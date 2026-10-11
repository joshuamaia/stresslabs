import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { MetricSnapshot, TestResult } from '../../core/models';
import { concurrencyChart, latencyChart, rpsChart, statusBreakdown, statusChart } from '../chart/chart-options';
import { EChart } from '../chart/echart';

/** Gráficos 1 a 4 de uma execução, alimentados apenas por snapshots/resultados reais. */
@Component({
  selector: 'app-run-charts',
  imports: [EChart],
  template: `
    @if (snapshots().length === 0) {
      <div class="panel empty-state">
        <span class="material-symbols-outlined">insights</span>
        Aguardando métricas do gerador de carga…
      </div>
    } @else {
      <div class="charts-grid">
        <section class="panel">
          <h2>Requisições por segundo</h2>
          <p class="hint">Respostas recebidas/s vs taxa de envio configurada</p>
          <app-chart [options]="rps()" label="Throughput ao longo do teste" />
        </section>
        <section class="panel">
          <h2>Tempo de resposta</h2>
          <p class="hint">Média, p95 e p99 por janela de 1s</p>
          <app-chart [options]="latency()" label="Latência média, p95 e p99" />
        </section>
        <section class="panel">
          <h2>Status HTTP</h2>
          <p class="hint">Distribuição acumulada, com HTTP 429 destacado</p>
          <app-chart [options]="status()" [height]="240" label="Distribuição de status HTTP" />
        </section>
        <section class="panel">
          <h2>Concorrência</h2>
          <p class="hint">Requisições simultâneas em execução</p>
          <app-chart [options]="concurrency()" label="Requisições simultâneas" />
        </section>
      </div>
    }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RunCharts {
  readonly snapshots = input.required<MetricSnapshot[]>();
  readonly result = input<TestResult | null>(null);
  readonly concurrencyLimit = input<number | undefined>(undefined);

  protected readonly rps = computed(() => rpsChart(this.snapshots()));
  protected readonly latency = computed(() => latencyChart(this.snapshots()));
  protected readonly concurrency = computed(() => concurrencyChart(this.snapshots(), this.concurrencyLimit()));
  protected readonly status = computed(() =>
    statusChart(statusBreakdown(this.result() ?? this.snapshots().at(-1) ?? null)));
}
