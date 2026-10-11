import { ChangeDetectionStrategy, Component, computed, effect, inject, signal } from '@angular/core';
import { rxResource, toSignal } from '@angular/core/rxjs-interop';
import { NonNullableFormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { RouterLink } from '@angular/router';
import { LoadTestApi, RateLimitApi } from '../../core/api/lab-api';
import { NotificationService } from '../../core/http/notification.service';
import { LiveRunStore } from '../../core/live/live-run.store';
import { HttpMethod, LoadTestLimits, SCENARIO_LABELS, StartLoadTestRequest, TestScenario } from '../../core/models';
import { fmt } from '../../shared/format';
import { RunCharts } from '../../shared/run-view/run-charts';
import { RunKpisView } from '../../shared/run-view/run-kpis';
import { StatusBadge } from '../../shared/status-badge/status-badge';

const SCENARIO_HINTS: Record<TestScenario, string> = {
  CONSTANT: 'Mantém a taxa de envio configurada durante todo o teste.',
  RAMP_UP: 'Cresce linearmente de 10% a 100% da taxa configurada.',
  SPIKE: 'Base de 20%, pico de 100% entre 40% e 60% da duração.',
  RATE_LIMIT: 'Envia mais requisições que a capacidade do bucket para validar o HTTP 429.',
};

@Component({
  selector: 'app-load-test',
  imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatSelectModule, MatSlideToggleModule,
    MatButtonModule, MatProgressBarModule, RouterLink, RunKpisView, RunCharts, StatusBadge],
  template: `
    <div class="page">
      <header class="page-header">
        <div>
          <h1>Teste de carga</h1>
          <p>Requisições HTTP reais disparadas pelo gerador (Virtual Threads) contra endpoints autorizados.</p>
        </div>
      </header>

      <div class="layout">
        <form class="panel form" [formGroup]="form" (ngSubmit)="start()">
          <h2>Configuração</h2>

          <mat-form-field>
            <mat-label>Nome do teste</mat-label>
            <input matInput formControlName="name" placeholder="Opcional" maxlength="120">
          </mat-form-field>

          <mat-form-field>
            <mat-label>Endpoint</mat-label>
            <mat-select formControlName="targetKey">
              @for (target of options.value()?.targets ?? []; track target.key) {
                <mat-option [value]="target.key">{{ target.label }}</mat-option>
              }
            </mat-select>
            @if (selectedTarget(); as target) {
              <mat-hint class="mono">{{ target.method }} {{ target.path }}</mat-hint>
            }
          </mat-form-field>

          <div class="row">
            <mat-form-field>
              <mat-label>Método HTTP</mat-label>
              <mat-select formControlName="method">
                @for (method of methods; track method) {
                  <mat-option [value]="method" [disabled]="selectedTarget()?.method !== method">{{ method }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
            <mat-form-field>
              <mat-label>Tipo de teste</mat-label>
              <mat-select formControlName="scenario">
                @for (scenario of options.value()?.scenarios ?? []; track scenario) {
                  <mat-option [value]="scenario">{{ scenarioLabels[scenario] }}</mat-option>
                }
              </mat-select>
            </mat-form-field>
          </div>
          <p class="scenario-hint">{{ scenarioHint() }}</p>

          <div class="row">
            <mat-form-field>
              <mat-label>Quantidade de requisições</mat-label>
              <input matInput type="number" formControlName="totalRequests">
              <mat-hint>máx. {{ fmt(limits()?.maxRequests) }}</mat-hint>
            </mat-form-field>
            <mat-form-field>
              <mat-label>Concorrência</mat-label>
              <input matInput type="number" formControlName="concurrency">
              <mat-hint>simultâneas · máx. {{ limits()?.maxConcurrency }}</mat-hint>
            </mat-form-field>
          </div>
          <div class="row">
            <mat-form-field>
              <mat-label>Taxa de envio</mat-label>
              <input matInput type="number" formControlName="targetRps">
              <span matTextSuffix>req/s</span>
              <mat-hint>máx. {{ fmt(limits()?.maxRps) }}</mat-hint>
            </mat-form-field>
            <mat-form-field>
              <mat-label>Duração máxima</mat-label>
              <input matInput type="number" formControlName="durationSeconds">
              <span matTextSuffix>s</span>
              <mat-hint>máx. {{ limits()?.maxDurationSeconds }} s</mat-hint>
            </mat-form-field>
          </div>
          <div class="row">
            <mat-form-field>
              <mat-label>Timeout por requisição</mat-label>
              <input matInput type="number" formControlName="timeoutMs">
              <span matTextSuffix>ms</span>
            </mat-form-field>
            <mat-form-field>
              <mat-label>X-Client-Id (header)</mat-label>
              <input matInput formControlName="clientId" placeholder="Opcional">
              <mat-hint>Usado pela política HEADER</mat-hint>
            </mat-form-field>
          </div>

          @if (form.controls.method.value === 'POST') {
            <mat-form-field>
              <mat-label>Corpo JSON</mat-label>
              <textarea matInput formControlName="requestBody" rows="4" class="mono"></textarea>
            </mat-form-field>
          }

          <mat-slide-toggle formControlName="rateLimitEnabled">
            Rate Limiting {{ form.controls.rateLimitEnabled.value ? 'habilitado' : 'desabilitado' }}
          </mat-slide-toggle>
          @if (rateLimit.value(); as rl) {
            <p class="muted small">Política atual: capacidade {{ rl.capacity }}, reposição de {{ rl.refillTokens }}
              a cada {{ rl.refillPeriodSeconds }}s por {{ rl.clientStrategy }}.</p>
          }
          <p class="muted small">RPS é a taxa de envio; concorrência é o teto de requisições simultâneas.
            Se a API ficar lenta, a concorrência limita a taxa efetiva.</p>

          <div class="actions">
            <button mat-flat-button type="submit" [disabled]="form.invalid || store.isActive() || submitting()">
              <span class="material-symbols-outlined">play_arrow</span> Iniciar teste
            </button>
            <button mat-stroked-button type="button" class="stop" (click)="stop()" [disabled]="!store.isActive()">
              <span class="material-symbols-outlined">stop</span> Interromper
            </button>
            <button mat-button type="button" (click)="store.clear()" [disabled]="store.isActive() || !store.execution()">
              <span class="material-symbols-outlined">mop</span> Limpar resultados
            </button>
            <a mat-button routerLink="/history">
              <span class="material-symbols-outlined">history</span> Consultar histórico
            </a>
          </div>
        </form>

        <section class="live">
          @if (store.execution(); as execution) {
            <div class="panel status">
              <div class="status-row">
                <app-status-badge [status]="execution.status" />
                <strong>{{ execution.name }}</strong>
                <span class="muted conn">SSE: {{ connectionLabel() }}</span>
              </div>
              <div class="progress">
                <mat-progress-bar mode="determinate" [value]="store.progress() * 100" />
                <span class="mono">{{ fmt(store.progress() * 100) }}%</span>
              </div>
              @if (store.latest(); as s) {
                <div class="counters mono">
                  <span>enviadas <b>{{ fmt(s.sent) }}</b></span>
                  <span>concluídas <b>{{ fmt(s.completed) }}</b></span>
                  <span class="ok">sucesso <b>{{ fmt(s.success) }}</b></span>
                  <span class="blocked">429 <b>{{ fmt(s.blocked) }}</b></span>
                  <span class="err">timeouts <b>{{ fmt(s.timeouts) }}</b></span>
                  <span class="err">falhas conexão <b>{{ fmt(s.connectionFailures) }}</b></span>
                  <span>em andamento <b>{{ s.concurrency }}</b></span>
                </div>
              }
              @if (execution.failureReason) {
                <p class="failure">{{ execution.failureReason }}</p>
              }
            </div>
            <app-run-kpis [kpis]="store.kpis()" />
            <app-run-charts [snapshots]="store.snapshots()" [result]="store.result()"
                            [concurrencyLimit]="execution.concurrency" />
          } @else {
            <div class="panel empty-state">
              <span class="material-symbols-outlined">monitoring</span>
              Configure e inicie um teste para acompanhar os resultados em tempo real.
            </div>
          }
        </section>
      </div>
    </div>
  `,
  styles: `
    .layout { display: grid; grid-template-columns: minmax(300px, 420px) minmax(0, 1fr); gap: 16px; align-items: start; }
    .form { display: flex; flex-direction: column; gap: 4px; position: sticky; top: 80px; }
    .row { display: grid; grid-template-columns: 1fr 1fr; gap: 12px; }
    mat-form-field { width: 100%; }
    .scenario-hint { color: var(--text-2); font-size: .8rem; margin: -8px 0 12px; }
    .small { font-size: .78rem; margin: 6px 0; }
    .actions { margin-top: 12px; }
    .stop:not([disabled]) { color: var(--critical); border-color: var(--critical); }
    .live { display: grid; gap: 16px; min-width: 0; }
    .status { display: grid; gap: 10px; }
    .status-row { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
    .conn { margin-left: auto; font-size: .78rem; }
    .progress { display: flex; align-items: center; gap: 12px; }
    .progress mat-progress-bar { flex: 1; }
    .counters { display: flex; flex-wrap: wrap; gap: 6px 16px; font-size: .8rem; color: var(--text-2); }
    .counters b { color: var(--text); }
    .ok b { color: var(--good); } .blocked b { color: var(--serious); } .err b { color: var(--critical); }
    .failure { color: var(--critical); margin: 0; }
    @media (max-width: 1100px) { .layout { grid-template-columns: 1fr; } .form { position: static; } }
    @media (max-width: 480px) { .row { grid-template-columns: 1fr; } }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class LoadTest {
  protected readonly store = inject(LiveRunStore);
  private readonly api = inject(LoadTestApi);
  private readonly notifications = inject(NotificationService);
  private readonly fb = inject(NonNullableFormBuilder);
  protected readonly scenarioLabels = SCENARIO_LABELS;
  protected readonly methods: HttpMethod[] = ['GET', 'POST'];
  protected readonly fmt = fmt;
  protected readonly submitting = signal(false);

  protected readonly options = rxResource({ stream: () => this.api.options() });
  private readonly rateLimitApi = inject(RateLimitApi);
  protected readonly rateLimit = rxResource({ stream: () => this.rateLimitApi.get() });
  protected readonly limits = computed<LoadTestLimits | undefined>(() => this.options.value()?.limits);

  protected readonly form = this.fb.group({
    name: [''],
    targetKey: ['health', Validators.required],
    method: ['GET' as HttpMethod, Validators.required],
    scenario: ['CONSTANT' as TestScenario, Validators.required],
    totalRequests: [1000, [Validators.required, Validators.min(1)]],
    concurrency: [20, [Validators.required, Validators.min(1)]],
    targetRps: [100, [Validators.required, Validators.min(1)]],
    durationSeconds: [30, [Validators.required, Validators.min(1)]],
    timeoutMs: [2000, [Validators.required]],
    rateLimitEnabled: [false],
    clientId: ['', Validators.pattern(/^[A-Za-z0-9._:-]{0,64}$/)],
    requestBody: [''],
  });

  private readonly targetKey = toSignal(this.form.controls.targetKey.valueChanges, { initialValue: 'health' });
  private readonly scenario = toSignal(this.form.controls.scenario.valueChanges, { initialValue: 'CONSTANT' as TestScenario });
  protected readonly selectedTarget = computed(() =>
    this.options.value()?.targets.find(target => target.key === this.targetKey()));
  protected readonly scenarioHint = computed(() => SCENARIO_HINTS[this.scenario()]);
  protected readonly connectionLabel = computed(() => ({
    connecting: 'conectando', open: 'conectado', reconnecting: 'reconectando…', closed: 'encerrado',
  })[this.store.connection()]);

  constructor() {
    // Aplica os limites de segurança informados pelo backend aos validadores
    effect(() => {
      const limits = this.limits();
      if (!limits) {
        return;
      }
      const c = this.form.controls;
      c.totalRequests.setValidators([Validators.required, Validators.min(1), Validators.max(limits.maxRequests)]);
      c.concurrency.setValidators([Validators.required, Validators.min(1), Validators.max(limits.maxConcurrency)]);
      c.targetRps.setValidators([Validators.required, Validators.min(1), Validators.max(limits.maxRps)]);
      c.durationSeconds.setValidators([Validators.required, Validators.min(1), Validators.max(limits.maxDurationSeconds)]);
      c.timeoutMs.setValidators([Validators.required, Validators.min(limits.minTimeoutMs), Validators.max(limits.maxTimeoutMs)]);
      Object.values(c).forEach(control => control.updateValueAndValidity({ emitEvent: false }));
    });
    // O método acompanha o endpoint escolhido; POST recebe o corpo padrão
    effect(() => {
      const target = this.selectedTarget();
      if (target) {
        this.form.patchValue({ method: target.method, requestBody: target.defaultBody ?? '' });
      }
    });
    // Teste de Rate Limiting exige o limite habilitado e mais requisições que a capacidade
    effect(() => {
      if (this.scenario() === 'RATE_LIMIT') {
        const capacity = this.rateLimit.value()?.capacity ?? 100;
        const total = this.form.controls.totalRequests.value;
        this.form.patchValue({
          rateLimitEnabled: true,
          totalRequests: total > capacity ? total : capacity * 3,
        });
      }
    });
  }

  protected start(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const request: StartLoadTestRequest = {
      name: v.name.trim() || null,
      scenario: v.scenario,
      targetKey: v.targetKey,
      method: v.method,
      totalRequests: v.totalRequests,
      concurrency: v.concurrency,
      targetRps: v.targetRps,
      durationSeconds: v.durationSeconds,
      timeoutMs: v.timeoutMs,
      rateLimitEnabled: v.rateLimitEnabled,
      requestBody: v.method === 'POST' ? v.requestBody : null,
      headers: v.clientId ? { 'X-Client-Id': v.clientId } : null,
    };
    this.submitting.set(true);
    this.store.start(request).subscribe({
      next: execution => {
        this.submitting.set(false);
        this.notifications.success(`Teste "${execution.name}" iniciado`);
        this.rateLimit.reload();
      },
      error: () => this.submitting.set(false),
    });
  }

  protected stop(): void {
    this.store.stop()?.subscribe(() => this.notifications.success('Interrupção solicitada'));
  }
}
