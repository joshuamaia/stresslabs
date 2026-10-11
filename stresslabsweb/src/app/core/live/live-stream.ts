import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { MetricSnapshot, StatusEvent, TestResult } from '../models';

export type LiveEvent =
  | { type: 'status'; data: StatusEvent }
  | { type: 'snapshot'; data: MetricSnapshot }
  | { type: 'result'; data: TestResult }
  | { type: 'connection'; data: ConnectionState };

export type ConnectionState = 'connecting' | 'open' | 'reconnecting' | 'closed';

const MAX_BACKOFF_MS = 15_000;

/**
 * Server-Sent Events com reconexão exponencial. O stream termina sozinho quando a execução
 * chega a um status final; ao reconectar, o backend reenvia o status atual para sincronização.
 */
@Injectable({ providedIn: 'root' })
export class LiveStream {
  connect(url: string): Observable<LiveEvent> {
    return new Observable<LiveEvent>(subscriber => {
      let source: EventSource | null = null;
      let retryTimer: ReturnType<typeof setTimeout> | undefined;
      let attempts = 0;
      let finished = false;

      const open = () => {
        subscriber.next({ type: 'connection', data: attempts === 0 ? 'connecting' : 'reconnecting' });
        source = new EventSource(url);
        source.onopen = () => {
          attempts = 0;
          subscriber.next({ type: 'connection', data: 'open' });
        };
        source.addEventListener('snapshot', event =>
          subscriber.next({ type: 'snapshot', data: JSON.parse((event as MessageEvent).data) }));
        source.addEventListener('result', event =>
          subscriber.next({ type: 'result', data: JSON.parse((event as MessageEvent).data) }));
        source.addEventListener('status', event => {
          const status: StatusEvent = JSON.parse((event as MessageEvent).data);
          subscriber.next({ type: 'status', data: status });
          if (status.status !== 'PENDING' && status.status !== 'RUNNING') {
            finished = true;
            close();
            subscriber.next({ type: 'connection', data: 'closed' });
            subscriber.complete();
          }
        });
        source.onerror = () => {
          // Controla a reconexão manualmente (backoff) em vez do retry automático do EventSource
          source?.close();
          if (finished) {
            return;
          }
          attempts++;
          const delay = Math.min(MAX_BACKOFF_MS, 500 * 2 ** Math.min(attempts, 5));
          subscriber.next({ type: 'connection', data: 'reconnecting' });
          retryTimer = setTimeout(open, delay);
        };
      };

      const close = () => {
        clearTimeout(retryTimer);
        source?.close();
        source = null;
      };

      open();
      return close;
    });
  }
}
