# API Stress & Rate Limit Lab

Laboratório Full Stack para executar **testes de carga reais** e validar **Rate Limiting** em uma API REST,
acompanhando os resultados em tempo real em um dashboard de observabilidade.

Todas as métricas exibidas vêm de requisições HTTP efetivamente executadas — nada é simulado.

## Componentes

| Diretório | Serviço Docker | Stack | Papel |
|---|---|---|---|
| `stresslabsservice` | `api-backend` (:8080) | Java 25, Spring Boot 4.1, JPA, Flyway, Bucket4j, Actuator | API testada, Rate Limiting, orquestração, persistência, SSE |
| `stresslabsloadgen` | `load-generator` (interno) | Java 25, Virtual Threads, `java.net.http.HttpClient` | Gera a carga e mede latência/throughput |
| `stresslabsweb` | `web-dashboard` (:4200) | Angular 22, Signals, Material, ECharts, Nginx | Configuração, monitoramento, histórico e comparação |
| — | `postgres` (:5433 no host) | PostgreSQL 17 | Execuções, snapshots, resultados e configuração |

```
Navegador ──► Nginx (web-dashboard) ──/api/admin──► api-backend ──► PostgreSQL
                                                      │  ▲
                                   POST /api/runs ────┘  │ snapshots/resultados (token interno)
                                                      ▼  │
                                                load-generator ──HTTP real──► api-backend /api/target/**
```

O gerador roda em **container separado** da API, reduzindo a interferência nos resultados, e acessa o
backend pelo nome do serviço Docker (`http://api-backend:8080`).

## Como executar

Pré-requisito: Docker Desktop (ou Docker Engine + Compose v2).

```bash
docker compose up -d --build
```

- Dashboard: http://localhost:4200
- API: http://localhost:8080 (`/actuator/health`, `/api/target/health`)

Para parar: `docker compose down` (adicione `-v` para apagar o banco). Variáveis opcionais estão em
[.env.example](.env.example) — copie para `.env` para customizar portas, token interno e limites.

Nenhum teste de carga é executado automaticamente ao subir o ambiente.

### Desenvolvimento local (sem Docker para as aplicações)

```bash
docker compose up -d postgres
cd stresslabsservice && ./mvnw spring-boot:run                        # :8080
cd stresslabsloadgen && ./mvnw spring-boot:run                         # :8081
cd stresslabsweb && npm install && npm start                           # :4200 (proxy /api -> :8080)
```

### Testes automatizados

```bash
cd stresslabsservice && ./mvnw test                     # unitários + integração (Testcontainers/PostgreSQL, requer Docker)
cd stresslabsloadgen && ./mvnw test                     # unitários + integração com servidor HTTP real
cd stresslabsweb && npx ng test --watch=false           # Vitest
```

Cobertura principal: regras do Rate Limiting (incluindo consumo concorrente), HTTP 200/429 com headers,
controle de concorrência e de taxa de envio, cálculo de métricas e percentis, timeouts, falhas de conexão,
cancelamento, validação de parâmetros e allowlist, persistência de resultados, casos de uso e comparação.

## Reproduzindo os cenários

Todos pelo dashboard (**Teste de carga**), sem editar código:

1. **Sem Rate Limiting** — Endpoint *Listar produtos*, *Carga constante*, 3.000 requisições, concorrência 50,
   200 req/s, 30 s, Rate Limiting **desabilitado**.
2. **Com Rate Limiting** — mesma configuração com Rate Limiting **habilitado** (padrão: 100 req/min por IP).
   Como o gerador usa um único IP, a partir da 101ª requisição a API responde `HTTP 429`.
3. **Comparar** — em **Histórico**, marque as duas execuções e clique em *Comparar* (ou use o painel
   “Com vs. sem Rate Limiting” do dashboard).
4. **Teste de Rate Limiting** — o cenário exige o limite habilitado e mais requisições que a capacidade do bucket.
5. **Carga progressiva / pico** — use *Processamento pesado* para observar a latência crescer com a taxa.
6. Para buckets por cliente, escolha a política **HEADER** em *Rate Limiting* e informe `X-Client-Id` no teste.

Endpoints testáveis (allowlist fixa): `GET /api/target/health`, `GET /api/target/products`,
`GET /api/target/products/{id}` (ID aleatório entre 1 e 1000), `POST /api/target/products` e
`GET /api/target/processing?workUnits=N` (1–50 unidades de 5 mil hashes SHA-256, prazo rígido de 2 s).

## Decisões técnicas

**Arquitetura hexagonal por feature (backend).** Pacotes `targetapi`, `ratelimit`, `loadtest`, `metrics` e
`testhistory`, cada um com `domain` (sem dependências de framework), `application` (casos de uso e portas) e
`adapter.in`/`adapter.out` (REST, SSE, JPA, Bucket4j, Micrometer, cliente HTTP do gerador). DTOs ficam nos
adapters; entidades JPA nunca chegam ao domínio.

**Taxa de envio ≠ concorrência.** O gerador usa um acumulador de créditos que segue o perfil do cenário
(constante, rampa 10%→100%, pico de 20%→100% entre 40% e 60% da duração) e um `Semaphore` que limita as
requisições simultâneas. Cada requisição roda em uma Virtual Thread, mas nunca há mais que `concurrency` em
andamento. Se a API ficar lenta, a concorrência limita a taxa efetiva — e o gráfico mostra isso.

**Medição.** Latências são medidas com `System.nanoTime()` (relógio monotônico). Cada latência fica em um
array em memória (8 bytes/requisição, limitado pelo máximo de requisições), permitindo percentis **exatos**
(nearest-rank) por janela de 1 s e para o teste inteiro. Apenas snapshots por segundo e o resultado
consolidado são persistidos — nunca cada requisição individual.

**Classificação.** 2xx/3xx = sucesso; 429 = bloqueada; demais 4xx/5xx = erro HTTP; timeout de resposta,
falha/timeout de conexão e cancelamento são contados separadamente (cancelamentos não contam como erro).

**Rate Limiting.** Bucket4j com buckets em memória (thread-safe, lock-free), política persistida no banco e
mantida em `AtomicReference` — alterações valem na hora, sem reinício. O filtro atua apenas em
`/api/target/**` e responde 429 com `Retry-After`, `X-RateLimit-Limit` e `X-RateLimit-Remaining`. Cada teste
zera os buckets ao iniciar, tornando os cenários reproduzíveis. **Limitação conhecida:** com várias
instâncias da API seria necessário estado compartilhado (ex.: Bucket4j com backend JDBC/distribuído).

**Tempo real.** SSE por execução (`/api/admin/load-tests/{id}/stream`) com eventos `status`, `snapshot` e
`result`, heartbeat a cada 15 s e reconexão com backoff exponencial no frontend; ao reconectar, o estado é
ressincronizado via REST. O Nginx desativa o buffer nessa rota.

**Segurança (MVP local).** O usuário escolhe um endpoint de uma allowlist — nunca uma URL. O gerador valida
novamente caminho, origem e headers (defesa em profundidade), aplica limites de requisições, concorrência,
taxa, duração e timeout, e só aceita comandos com o token interno. Apenas uma execução ativa por vez
(trava em memória + índice único parcial no banco). Rotas `/api/admin/**` e `/api/internal/**` só aceitam
clientes de rede local/privada; o gerador não publica porta. **Não exponha este sistema publicamente sem
autenticação, autorização e controles adicionais.** Um watchdog marca como `FAILED` execuções sem resultado
após duração + 60 s, e o gerador é cancelado se o backend recusar seus snapshots.

**Observabilidade.** Actuator + Micrometer (CPU, heap, threads, requisições HTTP, pool Hikari) na página
*Infraestrutura*, separada das métricas do gerador. Prometheus/Grafana ficaram fora desta versão.

**Frontend.** Standalone components, Signals/`computed()`/`effect()`, `rxResource`, Reactive Forms, controle
de fluxo `@if/@for/@switch`, lazy loading por rota, interceptors de loading e de erros (ProblemDetail).
O SSR gerado inicialmente foi removido: é um dashboard de dados ao vivo, servido como SPA pelo Nginx.

**Versões.** Os projetos foram gerados com Spring Boot 4.1.1 e Java 25 (LTS); mantive essas versões em vez de
Spring Boot 3.x por serem as escolhidas na criação dos projetos.
