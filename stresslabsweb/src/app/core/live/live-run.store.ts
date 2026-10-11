import { Injectable, computed, inject, signal } from '@angular/core';
import { Observable, Subscription, forkJoin, map, of, switchMap, tap } from 'rxjs';
import { HistoryApi, LoadTestApi } from '../api/lab-api';
import {
  ACTIVE_STATUSES,
  Execution,
  MetricSnapshot,
  StartLoadTestRequest,
  StatusEvent,
  TestResult,
} from '../models';
import { ConnectionState, LiveStream } from './live-stream';

export interface RunKpis {
  totalRequests: number;
  rps: number;
  avgLatencyMs: number;
  p95LatencyMs: number;
  p99LatencyMs: number;
  successRate: number;
  blocked: number;
  httpErrors: number;
  final: boolean;
}

/**
 * Estado da execução acompanhada (ativa ou a última). Signals para o estado,
 * RxJS para HTTP/SSE. Sem NgRx: um serviço simples basta.
 */
@Injectable({ providedIn: 'root' })
export class LiveRunStore {
  private readonly api = inject(LoadTestApi);
  private readonly history = inject(HistoryApi);
  private readonly stream = inject(LiveStream);
  private streamSubscription?: Subscription;
  private initialized = false;

  readonly execution = signal<Execution | null>(null);
  readonly snapshots = signal<MetricSnapshot[]>([]);
  readonly result = signal<TestResult | null>(null);
  readonly connection = signal<ConnectionState>('closed');

  readonly isActive = computed(() => {
    const execution = this.execution();
    return !!execution && ACTIVE_STATUSES.includes(execution.status);
  });
  readonly latest = computed(() => this.snapshots().at(-1) ?? null);
  readonly progress = computed(() => {
    const execution = this.execution();
    if (execution?.status === 'COMPLETED') {
      return 1;
    }
    return this.latest()?.progress ?? 0;
  });

  /** Durante a execução: valores da janela atual; após o término: consolidado do teste inteiro. */
  readonly kpis = computed<RunKpis | null>(() => {
    const result = this.result();
    if (result) {
      return {
        totalRequests: result.totalRequests,
        rps: result.avgRps,
        avgLatencyMs: result.avgLatencyMs,
        p95LatencyMs: result.p95LatencyMs,
        p99LatencyMs: result.p99LatencyMs,
        successRate: result.successRate,
        blocked: result.blockedCount,
        httpErrors: result.httpErrorCount,
        final: true,
      };
    }
    const snapshot = this.latest();
    if (!snapshot) {
      return null;
    }
    return {
      totalRequests: snapshot.sent,
      rps: snapshot.rps,
      avgLatencyMs: snapshot.avgLatencyMs,
      p95LatencyMs: snapshot.p95LatencyMs,
      p99LatencyMs: snapshot.p99LatencyMs,
      successRate: snapshot.sent === 0 ? 0 : (snapshot.success / snapshot.sent) * 100,
      blocked: snapshot.blocked,
      httpErrors: snapshot.errors - snapshot.timeouts - snapshot.connectionFailures,
      final: false,
    };
  });

  /** Carrega a execução ativa ou, na ausência, a mais recente do histórico. */
  init(): void {
    if (this.initialized) {
      return;
    }
    this.initialized = true;
    this.api.active().pipe(
      switchMap(active => active ? of(active)
        : this.history.list(0, 1).pipe(map(page => page.content[0]?.execution ?? null))),
    ).subscribe(execution => {
      if (execution) {
        this.track(execution);
      }
    });
  }

  start(request: StartLoadTestRequest): Observable<Execution> {
    return this.api.start(request).pipe(tap(execution => this.track(execution)));
  }

  stop(): Observable<Execution> | null {
    const execution = this.execution();
    return execution ? this.api.stop(execution.id).pipe(tap(updated => this.applyExecution(updated))) : null;
  }

  /** Limpa os resultados exibidos (não remove dados persistidos). */
  clear(): void {
    if (this.isActive()) {
      return;
    }
    this.streamSubscription?.unsubscribe();
    this.execution.set(null);
    this.snapshots.set([]);
    this.result.set(null);
    this.connection.set('closed');
  }

  track(execution: Execution): void {
    this.streamSubscription?.unsubscribe();
    this.execution.set(execution);
    this.snapshots.set([]);
    this.result.set(null);
    this.syncFromServer(execution.id);
    if (ACTIVE_STATUSES.includes(execution.status)) {
      this.openStream(execution.id);
    }
  }

  /** Recupera snapshots/resultado persistidos (início tardio da página, reconexões). */
  private syncFromServer(id: string): void {
    forkJoin({ snapshots: this.api.snapshots(id), detail: this.history.detail(id) }).subscribe(({ snapshots, detail }) => {
      if (this.execution()?.id !== id) {
        return;
      }
      this.mergeSnapshots(snapshots);
      if (detail.result) {
        this.result.set(detail.result);
      }
      this.applyExecution(detail.execution);
    });
  }

  private openStream(id: string): void {
    this.streamSubscription = this.stream.connect(this.api.streamUrl(id)).subscribe(event => {
      switch (event.type) {
        case 'connection':
          this.connection.set(event.data);
          break;
        case 'snapshot':
          this.mergeSnapshots([event.data]);
          break;
        case 'result':
          this.result.set(event.data);
          break;
        case 'status':
          this.applyStatus(event.data);
          break;
      }
    });
  }

  private applyStatus(event: StatusEvent): void {
    const current = this.execution();
    if (!current || current.id !== event.executionId) {
      return;
    }
    this.execution.set({ ...current, ...event, id: current.id });
    if (!ACTIVE_STATUSES.includes(event.status) && !this.result()) {
      this.syncFromServer(current.id);
    }
  }

  private applyExecution(updated: Execution): void {
    if (this.execution()?.id === updated.id) {
      this.execution.set(updated);
    }
  }

  private mergeSnapshots(incoming: MetricSnapshot[]): void {
    if (incoming.length === 0) {
      return;
    }
    this.snapshots.update(current => {
      const byElapsed = new Map(current.map(s => [s.elapsedMs, s]));
      incoming.forEach(s => byElapsed.set(s.elapsedMs, s));
      return [...byElapsed.values()].sort((a, b) => a.elapsedMs - b.elapsedMs);
    });
  }
}
