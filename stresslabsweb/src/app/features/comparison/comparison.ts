import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatSelectModule } from '@angular/material/select';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { HistoryApi } from '../../core/api/lab-api';
import { ComparisonMetric, ExecutionDetail, ExecutionSummary } from '../../core/models';
import { comparisonBarChart, overlayChart } from '../../shared/chart/chart-options';
import { EChart } from '../../shared/chart/echart';
import { fmt } from '../../shared/format';

@Component({
  selector: 'app-comparison',
  imports: [MatFormFieldModule, MatSelectModule, EChart],
  template: `
    <div class="page">
      <header class="page-header">
        <div>
          <h1>Comparação</h1>
          <p>Compare duas execuções (ex.: com e sem Rate Limiting). Os valores refletem apenas o que foi observado.</p>
        </div>
      </header>

      <section class="panel pickers">
        <mat-form-field>
          <mat-label>Teste A</mat-label>
          <mat-select [value]="a()" (valueChange)="select('a', $event)">
            @for (s of candidates(); track s.execution.id) {
              <mat-option [value]="s.execution.id">{{ optionLabel(s) }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
        <span class="material-symbols-outlined vs">compare_arrows</span>
        <mat-form-field>
          <mat-label>Teste B</mat-label>
          <mat-select [value]="b()" (valueChange)="select('b', $event)">
            @for (s of candidates(); track s.execution.id) {
              <mat-option [value]="s.execution.id" [disabled]="s.execution.id === a()">{{ optionLabel(s) }}</mat-option>
            }
          </mat-select>
        </mat-form-field>
      </section>

      @if (comparison.value(); as c) {
        <div class="legend">
          <span><i class="swatch a"></i>A · {{ describe(c.a) }}</span>
          <span><i class="swatch b"></i>B · {{ describe(c.b) }}</span>
        </div>
        <div class="charts-grid">
          <section class="panel">
            <h2>Latência</h2>
            <app-chart [options]="latencyBars()!" label="Latência média, p95 e p99 de A e B" />
          </section>
          <section class="panel">
            <h2>Resultado das requisições</h2>
            <app-chart [options]="outcomeBars()!" label="Sucesso, HTTP 429 e erros de A e B" />
          </section>
          <section class="panel">
            <h2>Throughput ao longo do tempo</h2>
            <app-chart [options]="rpsOverlay()!" label="RPS de A e B ao longo do tempo" />
          </section>
          <section class="panel">
            <h2>Latência p95 ao longo do tempo</h2>
            <app-chart [options]="p95Overlay()!" label="p95 de A e B ao longo do tempo" />
          </section>
        </div>

        <section class="panel table-panel">
          <h2>Diferenças (B − A)</h2>
          <div class="table-scroll">
            <table>
              <thead>
                <tr><th>Métrica</th><th class="num">A</th><th class="num">B</th><th class="num">Diferença</th><th class="num">Variação</th></tr>
              </thead>
              <tbody>
                @for (m of c.metrics; track m.key) {
                  <tr>
                    <td>{{ m.label }}</td>
                    <td class="num mono">{{ value(m, m.valueA) }}</td>
                    <td class="num mono">{{ value(m, m.valueB) }}</td>
                    <td class="num mono" [class]="'num mono ' + trend(m)">
                      @switch (trend(m)) {
                        @case ('better') { <span class="material-symbols-outlined" aria-label="melhor">trending_up</span> }
                        @case ('worse') { <span class="material-symbols-outlined" aria-label="pior">trending_down</span> }
                      }
                      {{ signed(m.difference) }}
                    </td>
                    <td class="num mono">{{ m.percentChange === null ? '—' : signed(m.percentChange) + '%' }}</td>
                  </tr>
                }
              </tbody>
            </table>
          </div>
          <p class="muted small">Setas indicam apenas o sentido desejável da métrica (ex.: menor latência).
            Bloqueios 429, tempo total e volume são neutros: dependem do objetivo do teste.</p>
        </section>
      } @else if (!a() || !b()) {
        <section class="panel empty-state">
          <span class="material-symbols-outlined">compare_arrows</span>
          Selecione duas execuções concluídas para comparar.
        </section>
      }
    </div>
  `,
  styles: `
    .pickers { display: flex; align-items: center; gap: 16px; flex-wrap: wrap; margin-bottom: 16px; }
    .pickers mat-form-field { flex: 1 1 280px; }
    .vs { color: var(--muted); }
    .legend { display: flex; flex-wrap: wrap; gap: 8px 24px; margin-bottom: 12px; color: var(--text-2); font-size: .85rem; }
    .swatch { display: inline-block; width: 12px; height: 4px; border-radius: 2px; margin-right: 6px; vertical-align: middle; }
    .swatch.a { background: var(--series-1); } .swatch.b { background: var(--series-2); }
    .table-panel { margin-top: 16px; }
    .table-scroll { overflow-x: auto; }
    table { width: 100%; border-collapse: collapse; font-size: .88rem; min-width: 560px; }
    th, td { padding: 10px 12px; border-bottom: 1px solid var(--border); text-align: left; }
    th { color: var(--muted); font-weight: 500; font-size: .78rem; }
    .num { text-align: right; }
    .better { color: var(--good); } .worse { color: var(--critical); }
    td .material-symbols-outlined { font-size: 16px; }
    .small { font-size: .78rem; }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ComparisonPage {
  readonly a = input<string | undefined>();
  readonly b = input<string | undefined>();
  private readonly api = inject(HistoryApi);
  private readonly router = inject(Router);

  private readonly history = rxResource({ stream: () => this.api.list(0, 100) });
  protected readonly candidates = computed(() => (this.history.value()?.content ?? []).filter(s => !!s.result));

  protected readonly comparison = rxResource({
    params: () => ({ a: this.a(), b: this.b() }),
    stream: ({ params }) => params.a && params.b && params.a !== params.b
      ? this.api.compare(params.a, params.b) : of(undefined),
  });

  protected readonly latencyBars = computed(() => {
    const c = this.comparison.value();
    if (!c) return null;
    const v = (d: ExecutionDetail) => [d.result!.avgLatencyMs, d.result!.p95LatencyMs, d.result!.p99LatencyMs];
    return comparisonBarChart(['Média', 'p95', 'p99'], 'ms', 'A', v(c.a), 'B', v(c.b));
  });
  protected readonly outcomeBars = computed(() => {
    const c = this.comparison.value();
    if (!c) return null;
    const v = (d: ExecutionDetail) => [d.result!.successCount, d.result!.blockedCount, d.result!.errorCount];
    return comparisonBarChart(['Sucesso', 'HTTP 429', 'Erros'], 'requisições', 'A', v(c.a), 'B', v(c.b));
  });
  protected readonly rpsOverlay = computed(() => {
    const c = this.comparison.value();
    return c ? overlayChart('req/s', 'A', c.a.snapshots, 'B', c.b.snapshots, s => s.rps) : null;
  });
  protected readonly p95Overlay = computed(() => {
    const c = this.comparison.value();
    return c ? overlayChart('ms', 'A', c.a.snapshots, 'B', c.b.snapshots, s => s.p95LatencyMs) : null;
  });

  protected select(slot: 'a' | 'b', id: string): void {
    this.router.navigate([], { queryParams: { [slot]: id }, queryParamsHandling: 'merge' });
  }

  protected optionLabel(s: ExecutionSummary): string {
    const date = new Date(s.execution.createdAt).toLocaleString('pt-BR');
    return `${s.execution.name} · RL ${s.execution.rateLimitEnabled ? 'on' : 'off'} · ${date}`;
  }

  protected describe(d: ExecutionDetail): string {
    return `${d.execution.name} — Rate Limiting ${d.execution.rateLimitEnabled ? 'habilitado' : 'desabilitado'}`;
  }

  protected value(m: ComparisonMetric, v: number): string {
    const digits = m.unit === 'ms' || m.unit === 'req/s' || m.unit === '%' ? 1 : 0;
    return `${fmt(v, digits)}${m.unit ? ' ' + m.unit : ''}`;
  }

  protected signed(v: number): string {
    return `${v > 0 ? '+' : ''}${fmt(v, Math.abs(v) < 100 ? 1 : 0)}`;
  }

  protected trend(m: ComparisonMetric): 'better' | 'worse' | '' {
    if (m.preference === 'NEUTRAL' || m.difference === 0) return '';
    const improved = m.preference === 'HIGHER_IS_BETTER' ? m.difference > 0 : m.difference < 0;
    return improved ? 'better' : 'worse';
  }
}
