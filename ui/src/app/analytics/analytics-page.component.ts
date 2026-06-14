import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatDividerModule } from '@angular/material/divider';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatNativeDateModule } from '@angular/material/core';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { NgChartsModule } from 'ng2-charts';
import { finalize } from 'rxjs/operators';
import type { ChartConfiguration, ChartType } from 'chart.js';
import { EmptyStateComponent } from '../shared/empty-state/empty-state.component';
import { PageHeaderComponent } from '../shared/page-header/page-header.component';
import { formatUiError } from '../shared/error-message.util';
import { AnalyticsColumn, AnalyticsKpi, AnalyticsQueryResponse, AnalyticsService } from './analytics.service';

interface DimensionOption { id: string; label: string; icon: string; }
interface MetricOption { id: string; label: string; }

const ALL_DIMENSIONS: DimensionOption[] = [
  { id: 'date',       label: 'Date',     icon: 'calendar_today' },
  { id: 'vendor',     label: 'Vendor',   icon: 'local_shipping' },
  { id: 'branch',     label: 'Branch',   icon: 'store' },
  { id: 'item_group', label: 'Category', icon: 'category' }
];

const ALL_METRICS: MetricOption[] = [
  { id: 'revenue',         label: 'Revenue' },
  { id: 'cost',            label: 'Cost' },
  { id: 'profit',          label: 'Profit' },
  { id: 'margin_pct',      label: 'Margin %' },
  { id: 'orders',          label: 'Orders' },
  { id: 'avg_order_value', label: 'Avg Order' }
];

@Component({
  selector: 'app-analytics-page',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatCardModule,
    MatChipsModule,
    MatDatepickerModule,
    MatDividerModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatNativeDateModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatTooltipModule,
    NgChartsModule,
    PageHeaderComponent,
    EmptyStateComponent
  ],
  templateUrl: './analytics-page.component.html',
  styleUrl: './analytics-page.component.css'
})
export class AnalyticsPageComponent implements OnInit {
  readonly allDimensions = ALL_DIMENSIONS;
  readonly allMetrics = ALL_METRICS;

  activeDimensions = new Set<string>(['date']);
  activeMetrics = new Set<string>(['revenue', 'profit', 'orders']);

  filterForm = new FormGroup({
    dateFrom: new FormControl<Date | null>(null),
    dateTo:   new FormControl<Date | null>(null),
    granularity: new FormControl<string>('day'),
    vendor:   new FormControl<string>(''),
    branch:   new FormControl<string>(''),
    itemGroup: new FormControl<string>('')
  });

  result: AnalyticsQueryResponse | null = null;
  isLoading = false;
  status = '';
  lastRunOk = false;

  chartType: ChartType = 'line';
  chartData: ChartConfiguration['data'] | null = null;
  chartOptions: ChartConfiguration['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { display: true } }
  };

  constructor(private analyticsService: AnalyticsService) {}

  ngOnInit(): void {
    const now = new Date();
    this.filterForm.reset({
      dateFrom: new Date(now.getFullYear(), now.getMonth(), 1),
      dateTo: now,
      granularity: 'day',
      vendor: '',
      branch: '',
      itemGroup: ''
    });
  }

  toggleDimension(id: string): void {
    if (this.activeDimensions.has(id)) {
      if (this.activeDimensions.size > 1) this.activeDimensions.delete(id);
    } else {
      this.activeDimensions.add(id);
    }
  }

  toggleMetric(id: string): void {
    if (this.activeMetrics.has(id)) {
      if (this.activeMetrics.size > 1) this.activeMetrics.delete(id);
    } else {
      this.activeMetrics.add(id);
    }
  }

  showGranularity(): boolean { return this.activeDimensions.has('date'); }

  run(): void {
    this.isLoading = true;
    this.status = '';
    this.analyticsService.query(this.buildRequest())
      .pipe(finalize(() => (this.isLoading = false)))
      .subscribe({
        next: res => {
          this.result = res;
          this.lastRunOk = true;
          this.status = `${res.rows.length} row(s) loaded.`;
          this.buildChart(res);
        },
        error: err => {
          this.result = null;
          this.lastRunOk = false;
          this.chartData = null;
          this.status = formatUiError(err, 'Failed to run analytics query.');
        }
      });
  }

  exportCsv(): void {
    this.analyticsService.export(this.buildRequest()).subscribe({
      next: blob => {
        const url = URL.createObjectURL(blob);
        const a = document.createElement('a');
        a.href = url;
        a.download = 'analytics-export.csv';
        a.click();
        URL.revokeObjectURL(url);
      },
      error: err => { this.status = formatUiError(err, 'Export failed.'); }
    });
  }

  clear(): void {
    this.ngOnInit();
    this.result = null;
    this.chartData = null;
    this.status = '';
    this.lastRunOk = false;
  }

  // ---- KPI helpers ----

  kpiIcon(kpi: AnalyticsKpi): string {
    if (kpi.id === 'revenue')         return 'trending_up';
    if (kpi.id === 'cost')            return 'receipt_long';
    if (kpi.id === 'profit')          return 'savings';
    if (kpi.id === 'margin_pct')      return 'percent';
    if (kpi.id === 'orders')          return 'shopping_cart';
    if (kpi.id === 'avg_order_value') return 'equalizer';
    return 'bar_chart';
  }

  kpiClass(kpi: AnalyticsKpi): string {
    if (kpi.id === 'profit' || kpi.id === 'margin_pct') {
      return kpi.value > 0 ? 'kpi-positive' : kpi.value < 0 ? 'kpi-negative' : '';
    }
    return '';
  }

  formatKpi(kpi: AnalyticsKpi): string {
    if (kpi.valueType === 'CURRENCY') return '₹' + this.formatNum(kpi.value);
    if (kpi.valueType === 'PERCENT')  return this.formatNum(kpi.value) + '%';
    return this.formatNum(kpi.value);
  }

  // ---- Table helpers ----

  get tableColumns(): AnalyticsColumn[] { return this.result?.columns ?? []; }
  get tableRows(): Record<string, unknown>[] { return this.result?.rows ?? []; }
  get totalRow(): Record<string, unknown> { return this.result?.totalsRow ?? {}; }
  get kpis(): AnalyticsKpi[] { return this.result?.kpis ?? []; }

  isDimension(col: AnalyticsColumn): boolean { return col.colType === 'DIMENSION'; }
  isCurrency(col: AnalyticsColumn): boolean  { return col.colType === 'CURRENCY'; }
  isPercent(col: AnalyticsColumn): boolean   { return col.colType === 'PERCENT'; }
  isProfitCol(col: AnalyticsColumn): boolean { return col.id === 'profit' || col.id === 'margin_pct'; }

  formatCell(col: AnalyticsColumn, value: unknown): string {
    if (value === null || value === undefined || value === '') return '—';
    if (col.colType === 'CURRENCY') return '₹' + this.formatNum(Number(value));
    if (col.colType === 'PERCENT')  return this.formatNum(Number(value)) + '%';
    if (col.colType === 'NUMBER')   return String(Math.round(Number(value)));
    return String(value);
  }

  cellClass(col: AnalyticsColumn, value: unknown): string {
    if (!this.isProfitCol(col)) return '';
    const n = Number(value);
    return n > 0 ? 'cell-pos' : n < 0 ? 'cell-neg' : '';
  }

  canRun(): boolean { return !this.isLoading; }
  canExport(): boolean { return this.lastRunOk && !this.isLoading; }

  // ---- Private ----

  private buildRequest() {
    const v = this.filterForm.value;
    const filters: Record<string, string> = {};
    if (v.vendor)    filters['vendor']    = v.vendor;
    if (v.branch)    filters['branch']    = v.branch;
    if (v.itemGroup) filters['itemGroup'] = v.itemGroup;
    return {
      dateFrom:    this.fmtDate(v.dateFrom ?? null),
      dateTo:      this.fmtDate(v.dateTo ?? null),
      granularity: v.granularity ?? 'day',
      dimensions:  Array.from(this.activeDimensions),
      metrics:     Array.from(this.activeMetrics),
      filters
    };
  }

  private buildChart(res: AnalyticsQueryResponse): void {
    this.chartData = null;
    if (!res.rows.length) return;

    const hasDate = this.activeDimensions.has('date');

    if (hasDate) {
      this.buildLineChart(res);
    } else {
      this.buildBarChart(res);
    }
  }

  private buildLineChart(res: AnalyticsQueryResponse): void {
    const labels = res.rows.map(r => String(r['date'] ?? ''));
    const datasets: ChartConfiguration['data']['datasets'] = [];

    const colorMap: Record<string, string> = {
      revenue: '#2563eb',
      cost:    '#9ca3af',
      profit:  '#16a34a'
    };

    for (const met of this.activeMetrics) {
      if (!['revenue', 'cost', 'profit'].includes(met)) continue;
      const metCol = res.columns.find(c => c.id === met);
      if (!metCol) continue;
      const data = res.rows.map(r => Number(r[met] ?? 0));
      if (!data.some(v => v !== 0)) continue;
      datasets.push({
        label: metCol.label,
        data,
        borderColor: colorMap[met] ?? '#6366f1',
        backgroundColor: 'transparent',
        pointRadius: 3,
        tension: 0.3
      });
    }

    if (!datasets.length) return;

    this.chartType = 'line';
    this.chartOptions = {
      responsive: true,
      maintainAspectRatio: false,
      plugins: { legend: { display: true } },
      scales: { y: { beginAtZero: false } }
    };
    this.chartData = { labels, datasets };
  }

  private buildBarChart(res: AnalyticsQueryResponse): void {
    const dimCol = res.columns.find(c => c.colType === 'DIMENSION');
    if (!dimCol) return;
    const labels = res.rows.map(r => String(r[dimCol.id] ?? ''));

    const firstMetId = Array.from(this.activeMetrics)
      .find(m => ['revenue', 'profit', 'cost', 'orders'].includes(m));
    if (!firstMetId) return;
    const firstMetCol = res.columns.find(c => c.id === firstMetId);
    if (!firstMetCol) return;

    const data = res.rows.map(r => Number(r[firstMetId] ?? 0));
    this.chartType = 'bar';
    this.chartOptions = {
      responsive: true,
      maintainAspectRatio: false,
      indexAxis: 'y' as const,
      plugins: { legend: { display: false } },
      scales: { x: { beginAtZero: true } }
    };
    this.chartData = {
      labels,
      datasets: [{ label: firstMetCol.label, data, backgroundColor: '#6366f1', borderRadius: 6 }]
    };
  }

  private fmtDate(date: Date | null): string {
    if (!date) return '';
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
  }

  private formatNum(n: number): string {
    if (!Number.isFinite(n)) return '—';
    return n.toLocaleString('en-IN', { maximumFractionDigits: 2 });
  }
}
