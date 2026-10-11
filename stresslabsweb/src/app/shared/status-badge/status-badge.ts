import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { STATUS_LABELS, TestStatus } from '../../core/models';

const ICONS: Record<TestStatus, string> = {
  PENDING: 'schedule',
  RUNNING: 'play_circle',
  COMPLETED: 'check_circle',
  STOPPED: 'stop_circle',
  FAILED: 'error',
};

@Component({
  selector: 'app-status-badge',
  template: `
    <span class="badge" [class]="'badge s-' + status().toLowerCase()">
      <span class="material-symbols-outlined" aria-hidden="true">{{ icon() }}</span>
      {{ label() }}
    </span>
  `,
  styles: `
    .badge {
      display: inline-flex; align-items: center; gap: 4px; padding: 2px 10px 2px 6px; border-radius: 999px;
      font-size: .78rem; font-weight: 500; border: 1px solid currentColor; white-space: nowrap;
    }
    .material-symbols-outlined { font-size: 16px; }
    .s-pending { color: var(--muted); }
    .s-running { color: var(--info); }
    .s-running .material-symbols-outlined { animation: pulse 1.4s ease-in-out infinite; }
    .s-completed { color: var(--good); }
    .s-stopped { color: var(--warning); }
    .s-failed { color: var(--critical); }
    @keyframes pulse { 50% { opacity: .35; } }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class StatusBadge {
  readonly status = input.required<TestStatus>();
  protected readonly icon = computed(() => ICONS[this.status()]);
  protected readonly label = computed(() => STATUS_LABELS[this.status()]);
}
