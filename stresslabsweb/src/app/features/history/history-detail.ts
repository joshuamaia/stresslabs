import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { RouterLink } from '@angular/router';
import { HistoryApi } from '../../core/api/lab-api';
import { RunKpis } from '../../core/live/live-run.store';
import { SCENARIO_LABELS } from '../../core/models';
import { fmt, fmtDuration } from '../../shared/format';
import { RunCharts } from '../../shared/run-view/run-charts';
import { RunKpisView } from '../../shared/run-view/run-kpis';
import { StatusBadge } from '../../shared/status-badge/status-badge';

@Component({
  selector: 'app-history-detail',
  imports: [RunKpisView, RunCharts, StatusBadge, MatButtonModule, RouterLink, DatePipe],
  template: `
    <div class="page">
      <header class="page-header">
        <div>
          <a mat-button routerLink="/history"><span class="material-symbols-outlined">arrow_back</span> Histórico</a>
        </div>
      </header>
      @if (detail.value(); as d) {
        <section class="panel summary">
          <div class="title">
            <app-status-badge [status]="d.execution.status" />
            <h1>{{ d.execution.name }}</h1>
          </div>
          <dl>
            <div><dt>Cenário</dt><dd>{{ scenarioLabels[d.execution.scenario] }}</dd></div>
            <div><dt>Endpoint</dt><dd class="mono">{{ d.execution.method }} {{ d.execution.endpoint }}</dd></div>
            <div><dt>Requisições</dt><dd>{{ f(d.execution.totalRequests) }}</dd></div>
            <div><dt>Concorrência</dt><dd>{{ d.execution.concurrency }}</dd></div>
            <div><dt>Taxa alvo</dt><dd>{{ d.execution.targetRps }} req/s</dd></div>
            <div><dt>Duração máx.</dt><dd>{{ d.execution.durationSeconds }} s</dd></div>
            <div><dt>Timeout</dt><dd>{{ d.execution.timeoutMs }} ms</dd></div>
            <div><dt>Rate Limiting</dt><dd>{{ d.execution.rateLimitSummary }}</dd></div>
            <div><dt>Início</dt><dd>{{ d.execution.startedAt | date: 'dd/MM/yyyy HH:mm:ss' }}</dd></div>
            <div><dt>Término</dt><dd>{{ d.execution.finishedAt | date: 'dd/MM/yyyy HH:mm:ss' }}</dd></div>
            @if (d.result) {
              <div><dt>Tempo total</dt><dd>{{ dur(d.result.durationMs) }}</dd></div>
              <div><dt>Latência mín/máx</dt><dd>{{ f(d.result.minLatencyMs, 1) }} / {{ f(d.result.maxLatencyMs, 1) }} ms</dd></div>
              <div><dt>Timeouts</dt><dd>{{ f(d.result.timeoutCount) }}</dd></div>
              <div><dt>Falhas de conexão</dt><dd>{{ f(d.result.connectionFailures) }}</dd></div>
            }
          </dl>
          @if (d.execution.failureReason) {
            <p class="failure">{{ d.execution.failureReason }}</p>
          }
        </section>
        <app-run-kpis [kpis]="kpis()" />
        <div class="gap"></div>
        <app-run-charts [snapshots]="d.snapshots" [result]="d.result" [concurrencyLimit]="d.execution.concurrency" />
      }
    </div>
  `,
  styles: `
    .summary { margin-bottom: 16px; }
    .title { display: flex; align-items: center; gap: 12px; flex-wrap: wrap; }
    h1 { font-size: 1.3rem; margin: 0; }
    dl { display: grid; grid-template-columns: repeat(auto-fill, minmax(180px, 1fr)); gap: 12px 24px; margin: 16px 0 0; }
    dt { color: var(--muted); font-size: .75rem; }
    dd { margin: 2px 0 0; font-size: .9rem; }
    .failure { color: var(--critical); }
    .gap { height: 16px; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class HistoryDetail {
  readonly id = input.required<string>();
  private readonly api = inject(HistoryApi);
  protected readonly scenarioLabels = SCENARIO_LABELS;
  protected readonly f = fmt;
  protected readonly dur = fmtDuration;
  protected readonly detail = rxResource({
    params: () => this.id(),
    stream: ({ params }) => this.api.detail(params),
  });

  protected readonly kpis = computed<RunKpis | null>(() => {
    const r = this.detail.value()?.result;
    return r ? {
      totalRequests: r.totalRequests, rps: r.avgRps, avgLatencyMs: r.avgLatencyMs, p95LatencyMs: r.p95LatencyMs,
      p99LatencyMs: r.p99LatencyMs, successRate: r.successRate, blocked: r.blockedCount,
      httpErrors: r.httpErrorCount, final: true,
    } : null;
  });
}
