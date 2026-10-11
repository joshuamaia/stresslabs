import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, effect, inject, signal } from '@angular/core';
import { rxResource } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { RateLimitApi } from '../../core/api/lab-api';
import { NotificationService } from '../../core/http/notification.service';
import { ClientStrategy } from '../../core/models';
import { MetricCard } from '../../shared/metric-card/metric-card';

@Component({
  selector: 'app-rate-limit',
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatSlideToggleModule,
    MatButtonModule, MetricCard, DatePipe],
  template: `
    <div class="page">
      <header class="page-header">
        <div>
          <h1>Rate Limiting</h1>
          <p>Token bucket (Bucket4j) aplicado apenas a <span class="mono">/api/target/**</span>.
            Alterações valem imediatamente, sem reiniciar a API.</p>
        </div>
      </header>

      @if (config.value(); as current) {
        <div class="cards-grid top">
          <app-metric-card label="Estado" [value]="current.enabled ? 'Habilitado' : 'Desabilitado'"
                           [icon]="current.enabled ? 'shield' : 'shield_lock'" [tone]="current.enabled ? 'good' : 'neutral'" />
          <app-metric-card label="Capacidade" [value]="'' + current.capacity" unit="tokens" icon="inventory_2" />
          <app-metric-card label="Reposição" [value]="current.refillTokens + ' / ' + current.refillPeriodSeconds + 's'"
                           icon="autorenew" [hint]="rate(current.refillTokens, current.refillPeriodSeconds)" />
          <app-metric-card label="Identificação" [value]="current.clientStrategy" icon="badge"
                           [hint]="'Alterado ' + (current.updatedAt | date: 'dd/MM HH:mm:ss')" />
        </div>
      }

      <div class="layout">
        <form class="panel" [formGroup]="form" (ngSubmit)="save()">
          <h2>Configuração</h2>
          <mat-slide-toggle formControlName="enabled">Rate Limiting habilitado</mat-slide-toggle>
          <div class="row">
            <mat-form-field>
              <mat-label>Capacidade do bucket</mat-label>
              <input matInput type="number" formControlName="capacity">
              <mat-hint>Máximo de requisições em rajada</mat-hint>
            </mat-form-field>
            <mat-form-field>
              <mat-label>Tokens repostos</mat-label>
              <input matInput type="number" formControlName="refillTokens">
            </mat-form-field>
          </div>
          <div class="row">
            <mat-form-field>
              <mat-label>Intervalo de reposição</mat-label>
              <input matInput type="number" formControlName="refillPeriodSeconds">
              <span matTextSuffix>s</span>
            </mat-form-field>
            <mat-form-field>
              <mat-label>Identificação do cliente</mat-label>
              <mat-select formControlName="clientStrategy">
                <mat-option value="IP">Por IP de origem</mat-option>
                <mat-option value="HEADER">Por header X-Client-Id</mat-option>
                <mat-option value="GLOBAL">Global (bucket único)</mat-option>
              </mat-select>
            </mat-form-field>
          </div>
          <div class="actions">
            <button mat-flat-button type="submit" [disabled]="form.invalid || saving()">
              <span class="material-symbols-outlined">save</span> Salvar
            </button>
            <button mat-stroked-button type="button" (click)="reset()">
              <span class="material-symbols-outlined">restart_alt</span> Zerar buckets
            </button>
            <button mat-button type="button" (click)="applyDefaults()">Padrão (100/min por IP)</button>
          </div>
        </form>

        <section class="panel info">
          <h2>Como funciona</h2>
          <ul>
            <li>Cada cliente possui um bucket com <b>capacidade</b> tokens; cada requisição consome um.</li>
            <li>A cada <b>intervalo</b>, <b>tokens repostos</b> voltam ao bucket (reposição em intervalos).</li>
            <li>Sem tokens, a API responde <span class="mono http429">HTTP 429</span> com
              <span class="mono">Retry-After</span>, <span class="mono">X-RateLimit-Limit</span> e
              <span class="mono">X-RateLimit-Remaining</span>.</li>
            <li>Endpoints administrativos e de métricas nunca são limitados.</li>
            <li>Cada teste de carga zera os buckets ao iniciar, para cenários reproduzíveis.</li>
          </ul>
          <p class="muted">MVP: buckets em memória em uma única instância. Com várias instâncias seria necessário estado
            compartilhado (ex.: Bucket4j com backend JDBC ou cache distribuído).</p>
        </section>
      </div>
    </div>
  `,
  styles: `
    .top { margin-bottom: 16px; }
    .layout { display: grid; grid-template-columns: repeat(auto-fit, minmax(min(100%, 420px), 1fr)); gap: 16px; align-items: start; }
    form { display: flex; flex-direction: column; gap: 8px; }
    .row { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
    mat-form-field { width: 100%; }
    .info ul { padding-left: 18px; margin: 0 0 12px; color: var(--text-2); line-height: 1.7; font-size: .9rem; }
    .http429 { color: var(--serious); }
    @media (max-width: 480px) { .row { grid-template-columns: 1fr; } }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class RateLimit {
  private readonly api = inject(RateLimitApi);
  private readonly notifications = inject(NotificationService);
  protected readonly saving = signal(false);
  protected readonly config = rxResource({ stream: () => this.api.get() });

  protected readonly form = inject(NonNullableFormBuilder).group({
    enabled: [true],
    capacity: [100, [Validators.required, Validators.min(1), Validators.max(1_000_000)]],
    refillTokens: [100, [Validators.required, Validators.min(1), Validators.max(1_000_000)]],
    refillPeriodSeconds: [60, [Validators.required, Validators.min(1), Validators.max(3600)]],
    clientStrategy: ['IP' as ClientStrategy, Validators.required],
  });

  constructor() {
    effect(() => {
      const current = this.config.value();
      if (current) {
        this.form.patchValue(current);
      }
    });
  }

  protected rate(tokens: number, seconds: number): string {
    return `≈ ${(tokens / seconds).toLocaleString('pt-BR', { maximumFractionDigits: 2 })} req/s sustentadas`;
  }

  protected save(): void {
    this.saving.set(true);
    this.api.update(this.form.getRawValue()).subscribe({
      next: updated => {
        this.saving.set(false);
        this.config.set(updated);
        this.notifications.success('Configuração aplicada');
      },
      error: () => this.saving.set(false),
    });
  }

  protected reset(): void {
    this.api.resetBuckets().subscribe(() => this.notifications.success('Buckets zerados'));
  }

  protected applyDefaults(): void {
    this.form.setValue({ enabled: true, capacity: 100, refillTokens: 100, refillPeriodSeconds: 60, clientStrategy: 'IP' });
  }
}
