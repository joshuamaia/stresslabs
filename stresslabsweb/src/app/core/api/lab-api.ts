import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';
import {
  Comparison,
  Execution,
  ExecutionDetail,
  ExecutionSummary,
  InfrastructureMetrics,
  LoadTestOptions,
  MetricSnapshot,
  Page,
  RateLimitConfig,
  StartLoadTestRequest,
} from '../models';

export const API_BASE = '/api/admin';

/** Integração HTTP com o backend: testes de carga e métricas. */
@Injectable({ providedIn: 'root' })
export class LoadTestApi {
  private readonly http = inject(HttpClient);

  options(): Observable<LoadTestOptions> {
    return this.http.get<LoadTestOptions>(`${API_BASE}/load-tests/options`);
  }

  start(request: StartLoadTestRequest): Observable<Execution> {
    return this.http.post<Execution>(`${API_BASE}/load-tests`, request);
  }

  stop(id: string): Observable<Execution> {
    return this.http.post<Execution>(`${API_BASE}/load-tests/${id}/stop`, {});
  }

  get(id: string): Observable<Execution> {
    return this.http.get<Execution>(`${API_BASE}/load-tests/${id}`);
  }

  /** Retorna null quando não há execução ativa (HTTP 204). */
  active(): Observable<Execution | null> {
    return this.http.get<Execution | null>(`${API_BASE}/load-tests/active`, { observe: 'response' })
      .pipe(map(response => response.status === 204 ? null : response.body));
  }

  snapshots(id: string): Observable<MetricSnapshot[]> {
    return this.http.get<MetricSnapshot[]>(`${API_BASE}/metrics/executions/${id}/snapshots`);
  }

  streamUrl(id: string): string {
    return `${API_BASE}/load-tests/${id}/stream`;
  }
}

@Injectable({ providedIn: 'root' })
export class RateLimitApi {
  private readonly http = inject(HttpClient);

  get(): Observable<RateLimitConfig> {
    return this.http.get<RateLimitConfig>(`${API_BASE}/rate-limit`);
  }

  update(config: RateLimitConfig): Observable<RateLimitConfig> {
    return this.http.put<RateLimitConfig>(`${API_BASE}/rate-limit`, config);
  }

  resetBuckets(): Observable<void> {
    return this.http.post<void>(`${API_BASE}/rate-limit/reset`, {});
  }
}

@Injectable({ providedIn: 'root' })
export class HistoryApi {
  private readonly http = inject(HttpClient);

  list(page = 0, size = 20): Observable<Page<ExecutionSummary>> {
    const params = new HttpParams().set('page', page).set('size', size);
    return this.http.get<Page<ExecutionSummary>>(`${API_BASE}/history`, { params });
  }

  detail(id: string): Observable<ExecutionDetail> {
    return this.http.get<ExecutionDetail>(`${API_BASE}/history/${id}`);
  }

  compare(a: string, b: string): Observable<Comparison> {
    const params = new HttpParams().set('a', a).set('b', b);
    return this.http.get<Comparison>(`${API_BASE}/history/compare`, { params });
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`${API_BASE}/history/${id}`);
  }

  clear(): Observable<{ deleted: number }> {
    return this.http.delete<{ deleted: number }>(`${API_BASE}/history`);
  }
}

@Injectable({ providedIn: 'root' })
export class MetricsApi {
  private readonly http = inject(HttpClient);

  infrastructure(): Observable<InfrastructureMetrics> {
    return this.http.get<InfrastructureMetrics>(`${API_BASE}/metrics/infrastructure`);
  }
}
