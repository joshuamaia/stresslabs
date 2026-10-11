import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { ChangeDetectionStrategy, Component, computed, inject } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { MatButtonModule } from '@angular/material/button';
import { MatListModule } from '@angular/material/list';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatToolbarModule } from '@angular/material/toolbar';
import { MatTooltipModule } from '@angular/material/tooltip';
import { RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { map } from 'rxjs';
import { LoadingTracker } from '../core/http/interceptors';
import { LiveRunStore } from '../core/live/live-run.store';

interface NavItem {
  path: string;
  label: string;
  icon: string;
}

@Component({
  selector: 'app-shell',
  imports: [RouterOutlet, RouterLink, RouterLinkActive, MatSidenavModule, MatToolbarModule, MatListModule,
    MatButtonModule, MatProgressBarModule, MatTooltipModule],
  template: `
    <mat-sidenav-container class="container">
      <mat-sidenav #nav [mode]="handset() ? 'over' : 'side'" [opened]="!handset()" class="sidenav">
        <div class="brand">
          <span class="material-symbols-outlined logo">electric_bolt</span>
          <div>
            <strong>Stress Lab</strong>
            <small>API &amp; Rate Limit</small>
          </div>
        </div>
        <mat-nav-list>
          @for (item of navItems; track item.path) {
            <a mat-list-item [routerLink]="item.path" routerLinkActive="active"
               (click)="handset() && nav.close()">
              <span matListItemIcon class="material-symbols-outlined">{{ item.icon }}</span>
              <span matListItemTitle>{{ item.label }}</span>
            </a>
          }
        </mat-nav-list>
        <div class="footer muted">Ambiente local · MVP</div>
      </mat-sidenav>

      <mat-sidenav-content>
        <mat-toolbar class="toolbar">
          @if (handset()) {
            <button mat-icon-button (click)="nav.toggle()" aria-label="Abrir menu">
              <span class="material-symbols-outlined">menu</span>
            </button>
          }
          <span class="title">API Stress &amp; Rate Limit Lab</span>
          <span class="spacer"></span>
          <span class="live" [class.on]="store.isActive()" [matTooltip]="liveTooltip()">
            <span class="dot"></span>{{ store.isActive() ? 'Teste em execução' : 'Ocioso' }}
          </span>
        </mat-toolbar>
        <div class="loading">
          @if (loading.busy()) {
            <mat-progress-bar mode="indeterminate" />
          }
        </div>
        <main><router-outlet /></main>
      </mat-sidenav-content>
    </mat-sidenav-container>
  `,
  styles: `
    .container { height: 100vh; }
    .sidenav { width: 240px; border-right: 1px solid var(--border); display: flex; flex-direction: column; }
    .brand { display: flex; align-items: center; gap: 10px; padding: 20px 16px 16px; }
    .brand strong { display: block; font-size: 1rem; }
    .brand small { color: var(--muted); font-size: .75rem; }
    .logo { color: var(--warning); font-size: 30px; background: #2a2412; border-radius: 10px; padding: 6px; }
    a.active { background: rgba(57, 135, 229, .14); border-radius: 8px; }
    a.active .material-symbols-outlined { color: var(--info); }
    mat-nav-list a { margin: 2px 8px; width: auto; }
    .footer { margin-top: auto; padding: 16px; font-size: .75rem; }
    .toolbar { position: sticky; top: 0; z-index: 2; background: var(--bg); border-bottom: 1px solid var(--border); gap: 8px; }
    .title { font-size: 1rem; font-weight: 600; }
    .spacer { flex: 1; }
    .loading { height: 4px; }
    .live { display: inline-flex; align-items: center; gap: 8px; font-size: .8rem; color: var(--muted); }
    .dot { width: 8px; height: 8px; border-radius: 50%; background: var(--muted); }
    .live.on { color: var(--good); }
    .live.on .dot { background: var(--good); box-shadow: 0 0 0 0 rgba(12,163,12,.6); animation: ping 1.6s infinite; }
    @keyframes ping { 70% { box-shadow: 0 0 0 8px rgba(12,163,12,0); } 100% { box-shadow: 0 0 0 0 rgba(12,163,12,0); } }
    @media (max-width: 600px) { .title { font-size: .9rem; } .live { display: none; } }
  `,
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Shell {
  protected readonly store = inject(LiveRunStore);
  protected readonly loading = inject(LoadingTracker);
  protected readonly handset = toSignal(
    inject(BreakpointObserver).observe([Breakpoints.XSmall, Breakpoints.Small]).pipe(map(r => r.matches)),
    { initialValue: false });
  protected readonly liveTooltip = computed(() => `SSE: ${this.store.connection()}`);

  protected readonly navItems: NavItem[] = [
    { path: '/dashboard', label: 'Dashboard', icon: 'space_dashboard' },
    { path: '/load-test', label: 'Teste de carga', icon: 'rocket_launch' },
    { path: '/rate-limit', label: 'Rate Limiting', icon: 'speed' },
    { path: '/metrics', label: 'Infraestrutura', icon: 'memory' },
    { path: '/history', label: 'Histórico', icon: 'history' },
    { path: '/comparison', label: 'Comparação', icon: 'compare_arrows' },
  ];

  constructor() {
    this.store.init();
  }
}
