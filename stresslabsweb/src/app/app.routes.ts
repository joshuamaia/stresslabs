import { Routes } from '@angular/router';

export const routes: Routes = [
  { path: '', pathMatch: 'full', redirectTo: 'dashboard' },
  {
    path: 'dashboard', title: 'Dashboard · Stress Lab',
    loadComponent: () => import('./features/dashboard/dashboard').then(m => m.Dashboard),
  },
  {
    path: 'load-test', title: 'Teste de carga · Stress Lab',
    loadComponent: () => import('./features/load-test/load-test').then(m => m.LoadTest),
  },
  {
    path: 'rate-limit', title: 'Rate Limiting · Stress Lab',
    loadComponent: () => import('./features/rate-limit/rate-limit').then(m => m.RateLimit),
  },
  {
    path: 'metrics', title: 'Infraestrutura · Stress Lab',
    loadComponent: () => import('./features/metrics/metrics').then(m => m.Metrics),
  },
  {
    path: 'history', title: 'Histórico · Stress Lab',
    loadComponent: () => import('./features/history/history').then(m => m.History),
  },
  {
    path: 'history/:id', title: 'Execução · Stress Lab',
    loadComponent: () => import('./features/history/history-detail').then(m => m.HistoryDetail),
  },
  {
    path: 'comparison', title: 'Comparação · Stress Lab',
    loadComponent: () => import('./features/comparison/comparison').then(m => m.ComparisonPage),
  },
  { path: '**', redirectTo: 'dashboard' },
];
