import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatPaginatorModule, PageEvent } from '@angular/material/paginator';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Router, RouterLink } from '@angular/router';
import { HistoryApi } from '../../core/api/lab-api';
import { NotificationService } from '../../core/http/notification.service';
import { ExecutionSummary, SCENARIO_LABELS } from '../../core/models';
import { fmt, fmtDuration } from '../../shared/format';
import { StatusBadge } from '../../shared/status-badge/status-badge';

@Component({
  selector: 'app-history',
  imports: [MatTableModule, MatPaginatorModule, MatCheckboxModule, MatButtonModule, MatTooltipModule, RouterLink,
    StatusBadge, DatePipe],
  template: `
    <div class="page">
      <header class="page-header">
        <div>
          <h1>Histórico</h1>
          <p>Execuções persistidas no PostgreSQL. Selecione duas para comparar.</p>
        </div>
        <div class="actions">
          <button mat-flat-button [disabled]="selected().length !== 2" (click)="compare()">
            <span class="material-symbols-outlined">compare_arrows</span> Comparar ({{ selected().length }}/2)
          </button>
          <button mat-stroked-button (click)="history.reload()">
            <span class="material-symbols-outlined">refresh</span> Atualizar
          </button>
          <button mat-button class="danger" (click)="clearAll()">
            <span class="material-symbols-outlined">delete_sweep</span> Excluir finalizadas
          </button>
        </div>
      </header>

      <section class="panel table-wrap">
        @if (rows().length === 0) {
          <div class="empty-state">
            <span class="material-symbols-outlined">history</span> Nenhuma execução registrada.
          </div>
        } @else {
          <table mat-table [dataSource]="rows()">
            <ng-container matColumnDef="select">
              <th mat-header-cell *matHeaderCellDef></th>
              <td mat-cell *matCellDef="let row">
                <mat-checkbox [checked]="isSelected(row)" [disabled]="!row.result || (!isSelected(row) && selected().length >= 2)"
                              (change)="toggle(row)" [attr.aria-label]="'Selecionar ' + row.execution.name" />
              </td>
            </ng-container>
            <ng-container matColumnDef="name">
              <th mat-header-cell *matHeaderCellDef>Teste</th>
              <td mat-cell *matCellDef="let row">
                <a class="name" [routerLink]="['/history', row.execution.id]">{{ row.execution.name }}</a>
                <div class="muted small">{{ row.execution.createdAt | date: 'dd/MM/yyyy HH:mm:ss' }} ·
                  {{ scenarioLabel(row) }}</div>
              </td>
            </ng-container>
            <ng-container matColumnDef="endpoint">
              <th mat-header-cell *matHeaderCellDef>Endpoint</th>
              <td mat-cell *matCellDef="let row" class="mono small">{{ row.execution.method }} {{ row.execution.endpoint }}</td>
            </ng-container>
            <ng-container matColumnDef="rateLimit">
              <th mat-header-cell *matHeaderCellDef>Rate Limit</th>
              <td mat-cell *matCellDef="let row">
                <span class="rl" [class.on]="row.execution.rateLimitEnabled" [matTooltip]="row.execution.rateLimitSummary">
                  {{ row.execution.rateLimitEnabled ? 'Ativo' : 'Inativo' }}
                </span>
              </td>
            </ng-container>
            <ng-container matColumnDef="status">
              <th mat-header-cell *matHeaderCellDef>Status</th>
              <td mat-cell *matCellDef="let row"><app-status-badge [status]="row.execution.status" /></td>
            </ng-container>
            <ng-container matColumnDef="requests">
              <th mat-header-cell *matHeaderCellDef class="num">Requisições</th>
              <td mat-cell *matCellDef="let row" class="num mono">{{ f(row.result?.totalRequests) }}</td>
            </ng-container>
            <ng-container matColumnDef="rps">
              <th mat-header-cell *matHeaderCellDef class="num">RPS</th>
              <td mat-cell *matCellDef="let row" class="num mono">{{ f(row.result?.avgRps, 1) }}</td>
            </ng-container>
            <ng-container matColumnDef="p95">
              <th mat-header-cell *matHeaderCellDef class="num">p95 (ms)</th>
              <td mat-cell *matCellDef="let row" class="num mono">{{ f(row.result?.p95LatencyMs, 1) }}</td>
            </ng-container>
            <ng-container matColumnDef="success">
              <th mat-header-cell *matHeaderCellDef class="num">Sucesso</th>
              <td mat-cell *matCellDef="let row" class="num mono">{{ row.result ? f(row.result.successRate, 1) + '%' : '—' }}</td>
            </ng-container>
            <ng-container matColumnDef="blocked">
              <th mat-header-cell *matHeaderCellDef class="num">429</th>
              <td mat-cell *matCellDef="let row" class="num mono blocked">{{ f(row.result?.blockedCount) }}</td>
            </ng-container>
            <ng-container matColumnDef="duration">
              <th mat-header-cell *matHeaderCellDef class="num">Duração</th>
              <td mat-cell *matCellDef="let row" class="num mono">{{ d(row.result?.durationMs) }}</td>
            </ng-container>
            <ng-container matColumnDef="actions">
              <th mat-header-cell *matHeaderCellDef></th>
              <td mat-cell *matCellDef="let row">
                <a mat-icon-button [routerLink]="['/history', row.execution.id]" aria-label="Ver detalhes">
                  <span class="material-symbols-outlined">visibility</span>
                </a>
                <button mat-icon-button (click)="remove(row)" aria-label="Excluir"
                        [disabled]="row.execution.status === 'RUNNING' || row.execution.status === 'PENDING'">
                  <span class="material-symbols-outlined">delete</span>
                </button>
              </td>
            </ng-container>
            <tr mat-header-row *matHeaderRowDef="columns"></tr>
            <tr mat-row *matRowDef="let row; columns: columns" [class.selected]="isSelected(row)"></tr>
          </table>
        }
        <mat-paginator [length]="history.value()?.totalElements ?? 0" [pageIndex]="page()" [pageSize]="size()"
                       [pageSizeOptions]="[10, 20, 50]" (page)="onPage($event)" />
      </section>
    </div>
  `,
  styles: `
    .table-wrap { overflow-x: auto; padding: 0; }
    table { min-width: 1000px; }
    .name { color: var(--text); font-weight: 500; text-decoration: none; }
    .name:hover { color: var(--info); }
    .small { font-size: .78rem; }
    .num { text-align: right; }
    .blocked { color: var(--serious); }
    .rl { font-size: .78rem; padding: 2px 8px; border-radius: 999px; border: 1px solid var(--border); color: var(--muted); }
    .rl.on { color: var(--serious); border-color: var(--serious); }
    tr.selected { background: rgba(57, 135, 229, .1); }
    .danger { color: var(--critical); }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class History {
  private readonly api = inject(HistoryApi);
  private readonly router = inject(Router);
  private readonly notifications = inject(NotificationService);
  protected readonly scenarioLabels = SCENARIO_LABELS;
  protected readonly f = fmt;
  protected readonly d = fmtDuration;
  protected readonly columns = ['select', 'name', 'endpoint', 'rateLimit', 'status', 'requests', 'rps', 'p95',
    'success', 'blocked', 'duration', 'actions'];

  protected readonly page = signal(0);
  protected readonly size = signal(20);
  protected readonly selected = signal<string[]>([]);
  protected readonly history = rxResource({
    params: () => ({ page: this.page(), size: this.size() }),
    stream: ({ params }) => this.api.list(params.page, params.size),
  });
  protected readonly rows = computed(() => this.history.value()?.content ?? []);

  protected scenarioLabel(row: ExecutionSummary): string {
    return this.scenarioLabels[row.execution.scenario];
  }

  protected isSelected(row: ExecutionSummary): boolean {
    return this.selected().includes(row.execution.id);
  }

  protected toggle(row: ExecutionSummary): void {
    const id = row.execution.id;
    this.selected.update(list => list.includes(id) ? list.filter(x => x !== id) : [...list, id].slice(-2));
  }

  protected compare(): void {
    const [a, b] = this.selected();
    this.router.navigate(['/comparison'], { queryParams: { a, b } });
  }

  protected onPage(event: PageEvent): void {
    this.page.set(event.pageIndex);
    this.size.set(event.pageSize);
  }

  protected remove(row: ExecutionSummary): void {
    if (!confirm(`Excluir a execução "${row.execution.name}"?`)) {
      return;
    }
    this.api.delete(row.execution.id).subscribe(() => {
      this.selected.update(list => list.filter(id => id !== row.execution.id));
      this.history.reload();
    });
  }

  protected clearAll(): void {
    if (!confirm('Excluir todas as execuções finalizadas e suas métricas?')) {
      return;
    }
    this.api.clear().subscribe(result => {
      this.notifications.success(`${result.deleted} execuções excluídas`);
      this.selected.set([]);
      this.history.reload();
    });
  }
}
