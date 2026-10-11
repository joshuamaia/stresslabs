import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { catchError, EMPTY, switchMap, timer } from 'rxjs';
import { MetricsApi } from '../../core/api/lab-api';
import { InfrastructureMetrics } from '../../core/models';
import { timeSeriesChart } from '../../shared/chart/chart-options';
import { EChart } from '../../shared/chart/echart';
import { fmt, fmtBytes, fmtDuration } from '../../shared/format';
import { MetricCard, Tone } from '../../shared/metric-card/metric-card';

const POLL_MS = 3000;
const MAX_SAMPLES = 100;

/** Métricas de infraestrutura da API (Actuator/Micrometer), separadas das métricas do gerador. */
@Component({
  selector: 'app-metrics',
  imports: [MetricCard, EChart],
  template: `
    <div class="page">
      <header class="page-header">
        <div>
          <h1>Infraestrutura da API</h1>
          <p>JVM, threads, HTTP e pool de conexões coletados pelo Micrometer · atualização a cada {{ pollSeconds }}s
            enquanto esta página está aberta.</p>
        </div>
      </header>

      @if (current(); as m) {
        <div class="cards-grid">
          <app-metric-card label="CPU do processo" icon="memory" unit="%" [value]="pct(m.processCpuUsage)"
                           [tone]="cpuTone(m.processCpuUsage)" [hint]="'Sistema: ' + pct(m.systemCpuUsage) + '% · ' + m.availableProcessors + ' núcleos'" />
          <app-metric-card label="Heap JVM" icon="data_usage" [value]="bytes(m.heapUsedBytes)"
                           [hint]="'de ' + bytes(m.heapMaxBytes)" />
          <app-metric-card label="Non-heap" icon="stacks" [value]="bytes(m.nonHeapUsedBytes)" tone="neutral" />
          <app-metric-card label="Threads (plataforma)" icon="account_tree" [value]="f(m.liveThreads)"
                           [hint]="'Pico ' + f(m.peakThreads) + ' · daemon ' + f(m.daemonThreads)" />
          <app-metric-card label="Requisições HTTP" icon="swap_horiz" [value]="f(m.httpRequestCount)"
                           hint="Total atendido desde o início" />
          <app-metric-card label="Tempo médio HTTP" icon="timer" unit="ms" [value]="f(m.httpMeanLatencyMs, 2)"
                           [hint]="'Máx. recente ' + f(m.httpMaxLatencyMs, 1) + ' ms'" />
          <app-metric-card label="Conexões do banco" icon="database" [value]="f(m.dbActiveConnections) + ' ativas'"
                           [tone]="(m.dbPendingConnections ?? 0) > 0 ? 'warning' : 'info'"
                           [hint]="f(m.dbIdleConnections) + ' ociosas · ' + f(m.dbPendingConnections) + ' aguardando · máx ' + f(m.dbMaxConnections)" />
          <app-metric-card label="Uptime" icon="schedule" tone="neutral" [value]="duration(m.uptimeSeconds)" />
        </div>

        <div class="charts-grid charts">
          <section class="panel">
            <h2>CPU do processo</h2>
            <app-chart [options]="cpuChart()" [height]="220" label="CPU do processo ao longo do tempo" />
          </section>
          <section class="panel">
            <h2>Heap JVM</h2>
            <app-chart [options]="heapChart()" [height]="220" label="Heap usado ao longo do tempo" />
          </section>
          <section class="panel">
            <h2>Threads</h2>
            <app-chart [options]="threadsChart()" [height]="220" label="Threads vivas ao longo do tempo" />
          </section>
          <section class="panel">
            <h2>Conexões do banco</h2>
            <app-chart [options]="dbChart()" [height]="220" label="Conexões ativas e aguardando" />
          </section>
        </div>
      } @else {
        <div class="panel empty-state">
          <span class="material-symbols-outlined">hourglass_empty</span> Coletando métricas…
        </div>
      }
    </div>
  `,
  styles: `.charts { margin-top: 16px; }`,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Metrics {
  protected readonly pollSeconds = POLL_MS / 1000;
  protected readonly samples = signal<InfrastructureMetrics[]>([]);
  protected readonly current = computed(() => this.samples().at(-1) ?? null);
  protected readonly f = fmt;
  protected readonly bytes = fmtBytes;

  protected readonly cpuChart = computed(() => timeSeriesChart('%', [
    { name: 'Processo', points: this.points(m => m.processCpuUsage, 100) },
  ]));
  protected readonly heapChart = computed(() => timeSeriesChart('MB', [
    { name: 'Heap usado', points: this.points(m => m.heapUsedBytes, 1 / (1024 * 1024)) },
  ]));
  protected readonly threadsChart = computed(() => timeSeriesChart('threads', [
    { name: 'Vivas', points: this.points(m => m.liveThreads) },
  ]));
  protected readonly dbChart = computed(() => timeSeriesChart('conexões', [
    { name: 'Ativas', points: this.points(m => m.dbActiveConnections) },
    { name: 'Aguardando', points: this.points(m => m.dbPendingConnections) },
  ]));

  constructor() {
    const api = inject(MetricsApi);
    timer(0, POLL_MS).pipe(
      switchMap(() => api.infrastructure().pipe(catchError(() => EMPTY))),
      takeUntilDestroyed(),
    ).subscribe(sample => this.samples.update(list => [...list, sample].slice(-MAX_SAMPLES)));
  }

  private points(pick: (m: InfrastructureMetrics) => number | null, scale = 1): [number, number][] {
    return this.samples()
      .filter(m => pick(m) !== null)
      .map(m => [new Date(m.capturedAt).getTime(), Math.round(pick(m)! * scale * 100) / 100]);
  }

  protected pct(value: number | null): string {
    return value === null ? '—' : fmt(value * 100, 1);
  }

  protected cpuTone(value: number | null): Tone {
    if (value === null) {
      return 'neutral';
    }
    return value < 0.6 ? 'good' : value < 0.85 ? 'warning' : 'critical';
  }

  protected duration(seconds: number | null): string {
    return fmtDuration(seconds === null ? null : seconds * 1000);
  }
}
