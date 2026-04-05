import { Component } from '@angular/core';
import { CommonModule } from '@angular/common';
import { RouterLink } from '@angular/router';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { Observable, catchError, map, of, startWith } from 'rxjs';
import { formatUiError } from '../shared/error-message.util';
import { AuthTokenService } from '../shared/auth-token.service';
import { DashboardService } from './dashboard.service';
import { BillingRow, DashboardSnapshot, RevenuePoint } from './dashboard.model';
import { BerryStatCardComponent } from '../shared/components/berry-stat-card/berry-stat-card.component';
import { BerryChartCardComponent } from '../shared/components/berry-chart-card/berry-chart-card.component';
import { PageHeaderComponent } from '../shared/page-header/page-header.component';

export type DashboardViewState = {
  snapshot: DashboardSnapshot | null;
  loading: boolean;
  error: string | null;
};

@Component({
  selector: 'app-dashboard',
  standalone: true,
  imports: [CommonModule, RouterLink, MatButtonModule, MatCardModule, MatIconModule, MatTableModule, BerryStatCardComponent, BerryChartCardComponent, PageHeaderComponent],
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

  revenuePath(points: RevenuePoint[]): string {
    const coords = this.revenueCoordinates(points);
    return coords.map((point, index) => `${index === 0 ? 'M' : 'L'} ${point.x} ${point.y}`).join(' ');
  }

  revenueAreaPath(points: RevenuePoint[]): string {
    const coords = this.revenueCoordinates(points);
    if (!coords.length) {
      return '';
    }
    const first = coords[0];
    const last = coords[coords.length - 1];
    return [
      `M ${first.x} 112`,
      `L ${first.x} ${first.y}`,
      ...coords.slice(1).map(point => `L ${point.x} ${point.y}`),
      `L ${last.x} 112`,
      'Z'
    ].join(' ');
  }

  revenueDots(points: RevenuePoint[]): Array<{ x: number; y: number; value: number; label: string }> {
    return this.revenueCoordinates(points).map((point, index) => ({
      ...point,
      value: points[index]?.value ?? 0,
      label: points[index]?.label ?? ''
    }));
  }

  branchBillingChart(rows: BillingRow[]): Array<BillingRow & { width: number }> {
    const topRows = rows.slice(0, 6);
    const max = Math.max(...topRows.map(row => row.total), 0);
    return topRows.map(row => ({
      ...row,
      width: max > 0 ? (row.total / max) * 100 : 0
    }));
  }

  orderStatusRows(rows: DashboardSnapshot['orderStatus']): Array<{ status: string; count: number; percent: number }> {
    const total = rows.reduce((sum, row) => sum + (row.count || 0), 0);
    return rows.map(row => ({
      ...row,
      percent: total > 0 ? (row.count / total) * 100 : 0
    }));
  }

  totalOrderCount(rows: DashboardSnapshot['orderStatus']): number {
    return rows.reduce((sum, row) => sum + (row.count || 0), 0);
  }

  topBillingRows(rows: BillingRow[], limit = 3): BillingRow[] {
    return rows.slice(0, limit);
  }

  formatCompactCurrency(value: number): string {
    const amount = Number(value) || 0;
    if (amount >= 10000000) {
      return `₹${(amount / 10000000).toFixed(2)}Cr`;
    }
    if (amount >= 100000) {
      return `₹${(amount / 100000).toFixed(2)}L`;
    }
    if (amount >= 1000) {
      return `₹${(amount / 1000).toFixed(1)}K`;
    }
    return `₹${amount.toFixed(0)}`;
  }

  trackByLabel(_index: number, row: { label: string }): string {
    return row.label;
  }

  private revenueCoordinates(points: RevenuePoint[]): Array<{ x: number; y: number }> {
    if (!points.length) {
      return [];
    }
    if (points.length === 1) {
      return [{ x: 12, y: 24 }];
    }
    const max = Math.max(...points.map(point => point.value), 0);
    return points.map((point, index) => {
      const x = 12 + (index * 296) / (points.length - 1);
      const y = max > 0 ? 112 - (point.value / max) * 88 : 112;
      return { x, y };
    });
  }
}
