import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { Injectable, computed, inject, signal } from '@angular/core';
import { catchError, finalize, throwError } from 'rxjs';
import { NotificationService } from './notification.service';

/** Contador global de requisições em andamento (barra de progresso no topo). */
@Injectable({ providedIn: 'root' })
export class LoadingTracker {
  private readonly pending = signal(0);
  readonly busy = computed(() => this.pending() > 0);

  begin(): void {
    this.pending.update(count => count + 1);
  }

  end(): void {
    this.pending.update(count => Math.max(0, count - 1));
  }
}

export const loadingInterceptor: HttpInterceptorFn = (request, next) => {
  const tracker = inject(LoadingTracker);
  tracker.begin();
  return next(request).pipe(finalize(() => tracker.end()));
};

/** Tratamento centralizado de erros: extrai o ProblemDetail do backend e notifica o usuário. */
export const errorInterceptor: HttpInterceptorFn = (request, next) => {
  const notifications = inject(NotificationService);
  return next(request).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse) {
        notifications.error(describeHttpError(error));
      }
      return throwError(() => error);
    }),
  );
};

export function describeHttpError(error: HttpErrorResponse): string {
  if (error.status === 0) {
    return 'Backend inacessível. Verifique se a API está em execução.';
  }
  const body = error.error as { detail?: string; title?: string } | null;
  const detail = body?.detail ?? body?.title;
  return detail ? `${detail}` : `Erro HTTP ${error.status}`;
}
