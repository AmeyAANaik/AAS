import { Component } from '@angular/core';
import { Observable, catchError, map, of, startWith } from 'rxjs';
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

  readonly vm$: Observable<DashboardViewState> = this.dashboardService.getDashboardSnapshot().pipe(
    map(snapshot => ({ snapshot, loading: false, error: null })),
    startWith({ snapshot: null, loading: true, error: null }),
    catchError(err => of({ snapshot: null, loading: false, error: this.formatError(err) }))
  );

  constructor(private dashboardService: DashboardService) {}

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
    if (err instanceof Error) {
      return err.message;
    }
    return String(err ?? 'Failed to load dashboard data');
  }
}
