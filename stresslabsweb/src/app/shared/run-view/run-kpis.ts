import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { RunKpis } from '../../core/live/live-run.store';
import { fmt } from '../format';
import { MetricCard, Tone } from '../metric-card/metric-card';

/** Os 8 cards principais da execução. */
@Component({
  selector: 'app-run-kpis',
  imports: [MetricCard],
  template: `
    <div class="cards-grid">
      <app-metric-card label="Total de requisições" icon="send" tone="info"
        [value]="f(k()?.totalRequests)" hint="Enviadas pelo gerador" />
      <app-metric-card label="Requisições por segundo" icon="speed" tone="info" unit="req/s"
        [value]="f(k()?.rps, 1)" [hint]="k()?.final ? 'Média do teste' : 'Janela atual'" />
      <app-metric-card label="Tempo médio" icon="timer" unit="ms" [tone]="latencyTone(k()?.avgLatencyMs)"
        [value]="f(k()?.avgLatencyMs, 1)" />
      <app-metric-card label="Latência p95" icon="avg_pace" unit="ms" [tone]="latencyTone(k()?.p95LatencyMs)"
        [value]="f(k()?.p95LatencyMs, 1)" />
      <app-metric-card label="Latência p99" icon="hourglass_top" unit="ms" [tone]="latencyTone(k()?.p99LatencyMs)"
        [value]="f(k()?.p99LatencyMs, 1)" />
      <app-metric-card label="Taxa de sucesso" icon="task_alt" unit="%" [tone]="successTone()"
        [value]="f(k()?.successRate, 1)" />
      <app-metric-card label="Bloqueadas (429)" icon="block" [tone]="(k()?.blocked ?? 0) > 0 ? 'serious' : 'neutral'"
        [value]="f(k()?.blocked)" hint="Rate Limiting" />
      <app-metric-card label="Erros HTTP" icon="error" [tone]="(k()?.httpErrors ?? 0) > 0 ? 'critical' : 'neutral'"
        [value]="f(k()?.httpErrors)" hint="4xx (exceto 429) e 5xx" />
    </div>
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RunKpisView {
  readonly k = input<RunKpis | null>(null, { alias: 'kpis' });

  protected readonly f = fmt;

  protected readonly successTone = computed<Tone>(() => {
    const rate = this.k()?.successRate;
    if (rate === undefined) {
      return 'neutral';
    }
    return rate >= 99 ? 'good' : rate >= 90 ? 'warning' : 'critical';
  });

  protected latencyTone(ms: number | undefined): Tone {
    if (ms === undefined) {
      return 'neutral';
    }
    return ms < 200 ? 'good' : ms < 1000 ? 'warning' : 'critical';
  }
}
