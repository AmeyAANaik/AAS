import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDividerModule } from '@angular/material/divider';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatNativeDateModule } from '@angular/material/core';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { NgChartsModule } from 'ng2-charts';
import { finalize } from 'rxjs/operators';
import { formatUiError } from '../shared/error-message.util';
import { EmptyStateComponent } from '../shared/empty-state/empty-state.component';
import { PageHeaderComponent } from '../shared/page-header/page-header.component';
import { ReportsCatalogItem, ReportsService } from './reports.service';
import type { ChartConfiguration, ChartType } from 'chart.js';

@Component({
  selector: 'app-reports-placeholder',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatDividerModule,
    MatFormFieldModule,
    MatDatepickerModule,
    MatNativeDateModule,
    MatIconModule,
    MatInputModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    NgChartsModule,
    PageHeaderComponent,
    EmptyStateComponent
  ],
  templateUrl: './reports-placeholder.component.html',
  styleUrl: './reports-placeholder.component.css'
})
export class ReportsPlaceholderComponent {
  catalog: ReportsCatalogItem[] = [];
  selectedReport: ReportsCatalogItem | null = null;

  rows: Array<Record<string, unknown>> = [];
  columns: string[] = [];
  totalsRow: Record<string, unknown> | null = null;
  isLoading = false;
  status = '';
  errorDetails: string[] = [];
  lastRunOk = false;

  chartType: ChartType = 'line';
  chartData: ChartConfiguration['data'] | null = null;
  chartOptions: ChartConfiguration['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { display: true } }
  };

  reportForm = new FormGroup({
    from: new FormControl<Date | null>(null),
    to: new FormControl<Date | null>(null),
    groupBy: new FormControl<string>('day')
  });

  constructor(private reportsService: ReportsService) {
    this.applyDefaultDates();
    this.loadCatalog();
  }

  private loadCatalog(): void {
    this.reportsService.getCatalog().subscribe({
      next: catalog => {
        this.catalog = catalog ?? [];
        this.selectedReport = this.catalog.length ? this.catalog[0] : null;
        this.applyGroupByDefaults();
      },
      error: err => {
        this.catalog = [];
        this.selectedReport = null;
        this.status = this.formatError(err, 'Failed to load report catalog.');
      }
    });
  }

  selectReport(report: ReportsCatalogItem): void {
    this.selectedReport = report;
    this.applyGroupByDefaults();
    this.status = '';
    this.errorDetails = [];
  }

  supportsGroupBy(): boolean {
    const supported = this.selectedReport?.supportedGroupBy ?? [];
    return Array.isArray(supported) && supported.length > 0;
  }

  run(): void {
    if (!this.selectedReport) {
      this.status = 'Select a report.';
      return;
    }
    const from = this.reportForm.get('from')?.value ?? null;
    const to = this.reportForm.get('to')?.value ?? null;
    const groupBy = this.supportsGroupBy() ? (this.reportForm.get('groupBy')?.value ?? 'day') : undefined;
    if (!this.isValidDateRange(from, to)) {
      this.status = 'From date must be on or before To date.';
      return;
    }
    this.isLoading = true;
    this.status = 'Running report...';
    this.errorDetails = [];
    this.reportsService
      .runReport(this.selectedReport.path, {
        from: this.formatDate(from),
        to: this.formatDate(to),
        groupBy: groupBy ? String(groupBy) : undefined
      })
      .pipe(finalize(() => (this.isLoading = false)))
      .subscribe({
        next: rows => {
          this.applyRows(rows ?? []);
          this.status = `Loaded ${this.rows.length} row(s) for ${this.selectedReport?.label ?? 'report'}.`;
          this.lastRunOk = true;
        },
        error: err => {
          this.resetResults();
          this.status = this.formatError(err, 'Failed to run report.');
          this.errorDetails = this.extractDetails(err);
          this.lastRunOk = false;
        }
      });
  }

  exportCsv(): void {
    if (!this.selectedReport) {
      this.status = 'Select a report.';
      return;
    }
    const from = this.reportForm.get('from')?.value ?? null;
    const to = this.reportForm.get('to')?.value ?? null;
    const groupBy = this.supportsGroupBy() ? (this.reportForm.get('groupBy')?.value ?? 'day') : undefined;
    if (!this.isValidDateRange(from, to)) {
      this.status = 'From date must be on or before To date.';
      return;
    }
    this.status = 'Exporting CSV...';
    this.errorDetails = [];
    this.reportsService
      .exportReport(this.selectedReport.path, {
        from: this.formatDate(from),
        to: this.formatDate(to),
        groupBy: groupBy ? String(groupBy) : undefined
      })
      .subscribe({
        next: blob => {
          const url = URL.createObjectURL(blob);
          const anchor = document.createElement('a');
          anchor.href = url;
          anchor.download = `${this.safeFilename(this.selectedReport?.id ?? 'report')}.csv`;
          anchor.click();
          URL.revokeObjectURL(url);
          this.status = `CSV exported for ${this.selectedReport?.label ?? 'report'}.`;
        },
        error: err => {
          this.status = this.formatError(err, 'Failed to export CSV.');
          this.errorDetails = this.extractDetails(err);
        }
      });
  }

  clear(): void {
    this.applyDefaultDates();
    this.resetResults();
    this.status = '';
    this.errorDetails = [];
    this.lastRunOk = false;
  }

  formatCell(value: unknown): string {
    if (value === null || value === undefined) {
      return '-';
    }
    if (typeof value === 'number') {
      return Number.isFinite(value) ? value.toFixed(2) : '-';
    }
    return String(value);
  }

  isProfitColumn(key: string): boolean {
    return key === 'profit_total' || key === 'profit_percent';
  }

  signClass(value: unknown): 'pos' | 'neg' | 'zero' {
    const n = this.asNumber(value);
    if (n > 0) {
      return 'pos';
    }
    if (n < 0) {
      return 'neg';
    }
    return 'zero';
  }

  signIcon(value: unknown): string {
    const n = this.asNumber(value);
    if (n > 0) {
      return '▲';
    }
    if (n < 0) {
      return '▼';
    }
    return '';
  }

  formatHeader(key: string): string {
    return key
      .replace(/_/g, ' ')
      .replace(/\b\w/g, char => char.toUpperCase());
  }

  canApply(): boolean {
    return Boolean(this.selectedReport) && !this.isLoading;
  }

  canExport(): boolean {
    return Boolean(this.selectedReport) && this.lastRunOk && !this.isLoading;
  }

  private applyDefaultDates(): void {
    const now = new Date();
    this.reportForm.reset({
      from: this.firstDayOfMonth(now),
      to: now,
      groupBy: 'day'
    });
  }

  private firstDayOfMonth(date: Date): Date {
    return new Date(date.getFullYear(), date.getMonth(), 1);
  }

  private isValidDateRange(from: Date | null | undefined, to: Date | null | undefined): boolean {
    if (!from || !to) {
      return false;
    }
    return this.formatDate(from) <= this.formatDate(to);
  }

  private applyRows(rows: Array<Record<string, unknown>>): void {
    this.rows = rows ?? [];
    const firstRow = this.rows.length ? this.rows[0] : null;
    this.columns = firstRow ? Object.keys(firstRow) : [];
    this.totalsRow = this.buildTotalsRow();
    this.buildChart();
  }

  private resetResults(): void {
    this.rows = [];
    this.columns = [];
    this.totalsRow = null;
    this.chartData = null;
  }

  private formatDate(date: Date | null): string {
    if (!date) {
      return '';
    }
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  private extractDetails(err: unknown): string[] {
    const anyErr = err as any;
    const payload = anyErr?.error;
    if (payload && typeof payload === 'object' && Array.isArray(payload.details)) {
      return payload.details.map((d: any) => String(d));
    }
    return [];
  }

  private safeFilename(value: string): string {
    const text = String(value ?? '').trim().toLowerCase();
    if (!text) {
      return 'report';
    }
    return text
      .replace(/[^a-z0-9._-]+/g, '-')
      .replace(/-+/g, '-')
      .replace(/^-/, '')
      .replace(/-$/, '');
  }

  private formatError(err: unknown, fallback: string): string {
    return formatUiError(err, fallback);
  }

  private applyGroupByDefaults(): void {
    if (!this.supportsGroupBy()) {
      this.reportForm.patchValue({ groupBy: 'day' }, { emitEvent: false });
      return;
    }
    const supported = this.selectedReport?.supportedGroupBy ?? [];
    const preferred = String(this.selectedReport?.defaultGroupBy ?? '').trim().toLowerCase() || 'day';
    const next = supported.includes(preferred) ? preferred : supported[0] ?? 'day';
    this.reportForm.patchValue({ groupBy: next }, { emitEvent: false });
  }

  private asNumber(value: unknown): number {
    if (typeof value === 'number') {
      return Number.isFinite(value) ? value : 0;
    }
    const text = String(value ?? '').trim();
    if (!text) {
      return 0;
    }
    const cleaned = text.replace(/[,₹\s]/g, '');
    const parsed = Number(cleaned);
    return Number.isFinite(parsed) ? parsed : 0;
  }

  private cssVar(name: string, fallback: string): string {
    try {
      if (typeof window === 'undefined') {
        return fallback;
      }
      const value = getComputedStyle(document.documentElement).getPropertyValue(name).trim();
      return value || fallback;
    } catch {
      return fallback;
    }
  }

  private buildChart(): void {
    this.chartData = null;
    if (!this.rows.length) {
      return;
    }
    if (!this.columns.includes('period_start')) {
      return;
    }
    const labels = this.rows.map(r => String(r['period_start'] ?? ''));
    if (!labels.length) {
      return;
    }
    const sales = this.rows.map(r => this.asNumber(r['sales_total']));
    const profit = this.rows.map(r => this.asNumber(r['profit_total']));
    const hasSales = sales.some(v => v !== 0);
    const hasProfit = profit.some(v => v !== 0);
    if (!hasSales && !hasProfit) {
      return;
    }
    const success = this.cssVar('--success', '#16a34a');
    const danger = this.cssVar('--danger', '#dc2626');
    const neutral = '#2563eb';
    const profitPointColors = profit.map(v => (v > 0 ? success : v < 0 ? danger : '#9ca3af'));
    this.chartData = {
      labels,
      datasets: [
        ...(hasSales
          ? [
              {
                label: 'Sales',
                data: sales,
                borderColor: neutral,
                backgroundColor: 'transparent',
                pointRadius: 2
              }
            ]
          : []),
        ...(hasProfit
          ? [
              {
                label: 'Profit',
                data: profit,
                borderColor: success,
                backgroundColor: 'transparent',
                pointBackgroundColor: profitPointColors,
                pointBorderColor: profitPointColors,
                pointRadius: 3
              }
            ]
          : [])
      ]
    } as ChartConfiguration['data'];
  }

  private buildTotalsRow(): Record<string, unknown> | null {
    if (!this.rows.length || !this.columns.length) {
      return null;
    }
    const totals: Record<string, unknown> = {};
    const sumByColumn: Record<string, number> = {};
    let labelSet = false;
    for (const column of this.columns) {
      if (column === 'period_start' || column === 'period_end') {
        if (!labelSet) {
          totals[column] = 'Total';
          labelSet = true;
        } else {
          totals[column] = '';
        }
        continue;
      }
      if (column.endsWith('_percent')) {
        totals[column] = '';
        continue;
      }
      const values = this.rows.map(r => r[column]);
      const numericValues = values
        .map(v => this.asNumber(v))
        .filter(v => Number.isFinite(v));
      const hasAnyNumeric = numericValues.some(v => v !== 0) || values.some(v => typeof v === 'number');
      if (!hasAnyNumeric) {
        totals[column] = '';
        continue;
      }
      const sum = numericValues.reduce((acc, cur) => acc + cur, 0);
      totals[column] = sum;
      sumByColumn[column] = sum;
    }

    if (this.columns.includes('profit_percent')) {
      const salesTotal = sumByColumn['sales_total'];
      const profitTotal = sumByColumn['profit_total'];
      if (Number.isFinite(salesTotal) && salesTotal !== 0 && Number.isFinite(profitTotal)) {
        totals['profit_percent'] = (profitTotal / salesTotal) * 100.0;
      } else {
        totals['profit_percent'] = '';
      }
    }

    return totals;
  }
}
