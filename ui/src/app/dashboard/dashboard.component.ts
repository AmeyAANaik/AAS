import { Component } from '@angular/core';
import { Observable, catchError, map, of, startWith } from 'rxjs';
import { formatUiError } from '../shared/error-message.util';
import { AuthTokenService } from '../shared/auth-token.service';
import { DashboardService } from './dashboard.service';
import { DashboardSnapshot } from './dashboard.model';

export type DashboardViewState = {
  snapshot: DashboardSnapshot | null;
  loading: boolean;
  error: string | null;
};

@Component({
  selector: 'app-dashboard',
  templateUrl: './dashboard.component.html',
  styleUrl: './dashboard.component.css'
})
export class DashboardComponent {
  readonly orderStatusColumns = ['status', 'count'];
  readonly billingColumns = ['name', 'total'];
  readonly title: string;
  readonly subtitlePrefix: string;

  readonly vm$: Observable<DashboardViewState> = this.dashboardService.getDashboardSnapshot().pipe(
    map(snapshot => ({ snapshot, loading: false, error: null })),
    startWith({ snapshot: null, loading: true, error: null }),
    catchError(err => of({ snapshot: null, loading: false, error: this.formatError(err) }))
  );

  constructor(
    private dashboardService: DashboardService,
    private tokenStore: AuthTokenService
  ) {
    const role = (this.tokenStore.getRole() ?? '').trim().toLowerCase();
    this.title = role === 'admin' ? 'Admin dashboard' : 'Operations dashboard';
    this.subtitlePrefix = role === 'admin'
      ? 'Read-only snapshot for '
      : 'Shared operational snapshot for ';
  }

  getStatusPillClass(status: string): string {
    const normalized = String(status ?? '').toLowerCase();
    if (normalized === 'delivered') {
      return 'pill pill-success';
    }
    if (normalized === 'ready') {
      return 'pill pill-info';
    }
    if (normalized === 'preparing') {
      return 'pill pill-warning';
    }
    return 'pill pill-neutral';
  }

  private formatError(err: unknown): string {
    return formatUiError(err, 'Failed to load dashboard data');
  }
}
