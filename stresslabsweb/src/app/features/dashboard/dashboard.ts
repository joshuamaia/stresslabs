import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { RouterLink } from '@angular/router';
import { HistoryApi } from '../../core/api/lab-api';
import { LiveRunStore } from '../../core/live/live-run.store';
import { ExecutionSummary, SCENARIO_LABELS } from '../../core/models';
import { comparisonBarChart } from '../../shared/chart/chart-options';
import { EChart } from '../../shared/chart/echart';
import { fmt } from '../../shared/format';
import { RunCharts } from '../../shared/run-view/run-charts';
import { RunKpisView } from '../../shared/run-view/run-kpis';
import { StatusBadge } from '../../shared/status-badge/status-badge';

@Component({
  selector: 'app-dashboard',
  imports: [RunKpisView, RunCharts, StatusBadge, EChart, MatProgressBarModule, MatButtonModule, RouterLink, DatePipe],
  template: `
    <div class="page">
      <header class="page-header">
        <div>
          <h1>Dashboard</h1>
          <p>Métricas reais coletadas pelo gerador de carga, atualizadas via SSE.</p>
        </div>
        <a mat-flat-button routerLink="/load-test">
          <span class="material-symbols-outlined">rocket_launch</span> Novo teste
        </a>
      </header>

      @if (store.execution(); as execution) {
        <section class="panel run-header">
          <div class="run-title">
            <app-status-badge [status]="execution.status" />
            <h2>{{ execution.name }}</h2>
          </div>
          <div class="run-meta">
            <span><span class="material-symbols-outlined">category</span>{{ scenarioLabels[execution.scenario] }}</span>
            <span class="mono"><span class="material-symbols-outlined">link</span>{{ execution.method }} {{ execution.endpoint }}</span>
            <span><span class="material-symbols-outlined">tune</span>{{ execution.concurrency }} simultâneas · {{ execution.targetRps }} req/s alvo</span>
            <span [class.rl-on]="execution.rateLimitEnabled">
              <span class="material-symbols-outlined">{{ execution.rateLimitEnabled ? 'shield' : 'shield_lock' }}</span>
              Rate Limiting {{ execution.rateLimitEnabled ? 'habilitado' : 'desabilitado' }}
            </span>
            <span class="muted">{{ execution.createdAt | date: 'dd/MM/yyyy HH:mm:ss' }}</span>
          </div>
          <div class="progress">
            <mat-progress-bar [mode]="store.isActive() && store.progress() === 0 ? 'indeterminate' : 'determinate'"
                              [value]="store.progress() * 100" />
            <span class="mono">{{ fmt(store.progress() * 100) }}%</span>
          </div>
          @if (execution.failureReason) {
            <p class="failure"><span class="material-symbols-outlined">warning</span>{{ execution.failureReason }}</p>
          }
        </section>

        <app-run-kpis [kpis]="store.kpis()" />
        <div class="spacer"></div>
        <app-run-charts [snapshots]="store.snapshots()" [result]="store.result()"
                        [concurrencyLimit]="execution.concurrency" />
      } @else {
        <section class="panel empty-state">
          <span class="material-symbols-outlined">rocket_launch</span>
          <div>Nenhum teste executado ainda.</div>
          <a mat-stroked-button routerLink="/load-test">Configurar o primeiro teste</a>
        </section>
      }

      <section class="panel comparison">
        <h2>Com vs. sem Rate Limiting</h2>
        @if (pair(); as p) {
          <p class="hint">Últimas execuções concluídas de cada tipo · resultados observados, sem suposições</p>
          <div class="charts-grid">
            <app-chart [options]="latencyComparison()!" label="Latência com e sem Rate Limiting" />
            <app-chart [options]="outcomeComparison()!" label="Resultado das requisições com e sem Rate Limiting" />
          </div>
          <a mat-button [routerLink]="['/comparison']" [queryParams]="{ a: p.without.execution.id, b: p.with.execution.id }">
            Ver comparação detalhada
          </a>
        } @else {
          <p class="muted">Execute ao menos um teste concluído com e outro sem Rate Limiting para comparar.</p>
        }
      </section>
    </div>
  `,
  styles: `
    .run-header { margin-bottom: 16px; display: grid; gap: 12px; }
    .run-title { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
    .run-title h2 { margin: 0; font-size: 1.1rem; }
    .run-meta { display: flex; flex-wrap: wrap; gap: 8px 20px; color: var(--text-2); font-size: .85rem; }
    .run-meta span { display: inline-flex; align-items: center; gap: 4px; }
    .run-meta .material-symbols-outlined { font-size: 16px; color: var(--muted); }
    .rl-on, .rl-on .material-symbols-outlined { color: var(--serious) !important; }
    .progress { display: flex; align-items: center; gap: 12px; }
    .progress mat-progress-bar { flex: 1; }
    .failure { color: var(--critical); display: flex; gap: 6px; align-items: center; margin: 0; }
    .spacer { height: 16px; }
    .comparison { margin-top: 16px; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Dashboard {
  protected readonly store = inject(LiveRunStore);
  private readonly historyApi = inject(HistoryApi);
  protected readonly scenarioLabels = SCENARIO_LABELS;
  protected readonly fmt = fmt;

  /** Recarrega quando o status da execução acompanhada muda (ex.: ao terminar um teste). */
  private readonly history = rxResource({
    params: () => ({ status: this.store.execution()?.status }),
    stream: () => this.historyApi.list(0, 50),
  });

  protected readonly pair = computed(() => {
    const items = (this.history.value()?.content ?? []).filter(s => s.result && s.execution.status === 'COMPLETED');
    const withLimit = items.find(s => s.execution.rateLimitEnabled);
    const without = items.find(s => !s.execution.rateLimitEnabled);
    return withLimit && without ? { with: withLimit, without } : null;
  });

  protected readonly latencyComparison = computed(() => {
    const p = this.pair();
    if (!p) {
      return null;
    }
    const values = (s: ExecutionSummary) => [s.result!.avgLatencyMs, s.result!.p95LatencyMs, s.result!.p99LatencyMs];
    return comparisonBarChart(['Média', 'p95', 'p99'], 'ms', 'Sem Rate Limiting', values(p.without),
      'Com Rate Limiting', values(p.with));
  });

  protected readonly outcomeComparison = computed(() => {
    const p = this.pair();
    if (!p) {
      return null;
    }
    const values = (s: ExecutionSummary) => [s.result!.successCount, s.result!.blockedCount, s.result!.errorCount];
    return comparisonBarChart(['Sucesso', 'HTTP 429', 'Erros'], 'requisições', 'Sem Rate Limiting',
      values(p.without), 'Com Rate Limiting', values(p.with));
  });
}
