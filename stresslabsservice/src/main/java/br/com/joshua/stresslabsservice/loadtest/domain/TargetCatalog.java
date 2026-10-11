package br.com.joshua.stresslabsservice.loadtest.domain;

import java.util.List;
import java.util.Optional;

/** Lista fechada de destinos permitidos (allowlist). URLs arbitrárias nunca são aceitas. */
public final class TargetCatalog {

    public static final int SEEDED_PRODUCTS = 1000;

    private static final List<AllowedTarget> TARGETS = List.of(
            new AllowedTarget("health", "Health check", TargetHttpMethod.GET, "/api/target/health",
                    "Resposta simples, mede o overhead básico da API", null, 0),
            new AllowedTarget("products-list", "Listar produtos", TargetHttpMethod.GET,
                    "/api/target/products?page=0&size=20", "Consulta paginada no PostgreSQL", null, 0),
            new AllowedTarget("product-by-id", "Produto por ID", TargetHttpMethod.GET, "/api/target/products/{id}",
                    "Consulta por chave primária com ID aleatório (1.." + SEEDED_PRODUCTS + ")", null,
                    SEEDED_PRODUCTS),
            new AllowedTarget("product-create", "Cadastrar produto", TargetHttpMethod.POST, "/api/target/products",
                    "INSERT real no PostgreSQL",
                    "{\"name\":\"Produto carga\",\"category\":\"load-test\",\"price\":19.90,\"stock\":10}", 0),
            new AllowedTarget("processing-light", "Processamento leve", TargetHttpMethod.GET,
                    "/api/target/processing?workUnits=2", "CPU-bound: 10 mil hashes SHA-256", null, 0),
            new AllowedTarget("processing-heavy", "Processamento pesado", TargetHttpMethod.GET,
                    "/api/target/processing?workUnits=20", "CPU-bound: 100 mil hashes SHA-256", null, 0));

    private TargetCatalog() {
    }

    public static List<AllowedTarget> all() {
        return TARGETS;
    }

    public static Optional<AllowedTarget> find(String key) {
        return TARGETS.stream().filter(target -> target.key().equals(key)).findFirst();
    }
}
