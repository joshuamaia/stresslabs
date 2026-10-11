import { ChangeDetectionStrategy, Component, input } from '@angular/core';

export type Tone = 'good' | 'warning' | 'serious' | 'critical' | 'info' | 'neutral';

@Component({
  selector: 'app-metric-card',
  template: `
    <div class="card" [class]="'tone-' + tone()">
      <div class="head">
        <span class="material-symbols-outlined icon" aria-hidden="true">{{ icon() }}</span>
        <span class="label">{{ label() }}</span>
      </div>
      <div class="value mono">
        {{ value() }}@if (unit()) {<span class="unit">{{ unit() }}</span>}
      </div>
      @if (hint()) {
        <div class="hint">{{ hint() }}</div>
      }
    </div>
  `,
  styles: `
    .card {
      position: relative; background: var(--surface); border: 1px solid var(--border);
      border-radius: var(--radius); padding: 14px 16px; overflow: hidden; height: 100%; box-sizing: border-box;
    }
    .card::before { content: ''; position: absolute; inset: 0 auto 0 0; width: 3px; background: var(--accent); }
    .head { display: flex; align-items: center; gap: 6px; color: var(--text-2); font-size: .8rem; }
    .icon { font-size: 18px; color: var(--accent); }
    .value { font-size: 1.6rem; font-weight: 600; margin-top: 8px; color: var(--text); letter-spacing: -.02em; }
    .unit { font-size: .85rem; color: var(--muted); margin-left: 4px; font-weight: 400; }
    .hint { font-size: .75rem; color: var(--muted); margin-top: 4px; }
    .tone-good { --accent: var(--good); }
    .tone-warning { --accent: var(--warning); }
    .tone-serious { --accent: var(--serious); }
    .tone-critical { --accent: var(--critical); }
    .tone-info { --accent: var(--info); }
    .tone-neutral { --accent: var(--muted); }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class MetricCard {
  readonly label = input.required<string>();
  readonly value = input.required<string>();
  readonly unit = input('');
  readonly icon = input('monitoring');
  readonly tone = input<Tone>('info');
  readonly hint = input('');
}
