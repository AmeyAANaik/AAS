import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCardModule } from '@angular/material/card';
import { MatChipsModule } from '@angular/material/chips';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatDividerModule } from '@angular/material/divider';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatNativeDateModule } from '@angular/material/core';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatTooltipModule } from '@angular/material/tooltip';
import { NgChartsModule } from 'ng2-charts';
import { Observable, forkJoin, of } from 'rxjs';
import { catchError, finalize, map, startWith } from 'rxjs/operators';
import type { ChartConfiguration, ChartType } from 'chart.js';
import { BranchService } from '../branches/branch.service';
import { CategoryService } from '../categories/category.service';
import { ItemService } from '../items/item.service';
import { EmptyStateComponent } from '../shared/empty-state/empty-state.component';
import { formatUiError } from '../shared/error-message.util';
import { PageHeaderComponent } from '../shared/page-header/page-header.component';
import { VendorService } from '../vendors/vendor.service';
import { AnalyticsColumn, AnalyticsKpi, AnalyticsQueryResponse, AnalyticsService, ExportFormat } from './analytics.service';

type ViewMode = 'explorer' | 'priceHistory' | 'gstReports';

interface DimensionOption { id: string; label: string; icon: string; }
interface MetricOption { id: string; label: string; }
interface FilterOption { label: string; }
interface GstReportOption { id: string; label: string; description: string; }

const ALL_DIMENSIONS: DimensionOption[] = [
  { id: 'date',       label: 'Date',     icon: 'calendar_today' },
  { id: 'vendor',     label: 'Vendor',   icon: 'local_shipping' },
  { id: 'branch',     label: 'Branch',   icon: 'store' },
  { id: 'item_group', label: 'Category', icon: 'category' },
  { id: 'item',       label: 'Item',     icon: 'inventory_2' }
];

const ALL_METRICS: MetricOption[] = [
  { id: 'revenue',            label: 'Revenue' },
  { id: 'cost',               label: 'Cost' },
  { id: 'profit',             label: 'Profit (incl GST)' },
  { id: 'profit_ex_gst',      label: 'Profit (excl GST)' },
  { id: 'margin_pct',         label: 'Margin % (incl GST)' },
  { id: 'margin_pct_ex_gst',  label: 'Margin % (excl GST)' },
  { id: 'orders',             label: 'Orders' },
  { id: 'quantity',           label: 'Quantity' },
  { id: 'avg_order_value',    label: 'Avg Order' }
];

const METRIC_COLOR: Record<string, string> = {
  revenue:            '#2563eb',
  cost:               '#9ca3af',
  profit:             '#16a34a',
  profit_ex_gst:      '#059669',
  margin_pct:         '#f59e0b',
  margin_pct_ex_gst:  '#d97706',
  orders:             '#8b5cf6',
  quantity:           '#06b6d4',
  avg_order_value:    '#ec4899'
};

const ROW_WARN_THRESHOLD = 500;

@Component({
  selector: 'app-analytics-page',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatAutocompleteModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatCardModule,
    MatChipsModule,
    MatDatepickerModule,
    MatDividerModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatMenuModule,
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
  readonly allDimensions = ALL_DIMENSIONS.filter(option => option.id !== 'item');
  readonly allMetrics = ALL_METRICS;
  readonly viewModes: Array<{ id: ViewMode; label: string }> = [
    { id: 'explorer', label: 'Analytics Explorer' },
    { id: 'priceHistory', label: 'Item Price History' },
    { id: 'gstReports', label: 'GSTR-1 Reports' }
  ];
  readonly gstReports: GstReportOption[] = [
    { id: 'b2b', label: 'B2B', description: 'Registered customer invoice lines.' },
    { id: 'b2c', label: 'B2C', description: 'Unregistered customer outward supplies.' },
    { id: 'cdnr', label: 'CDNR', description: 'Registered customer credit/debit notes.' },
    { id: 'hsn', label: 'HSN', description: 'HSN-wise outward supply summary.' },
    { id: 'docs', label: 'DOCS', description: 'Documents issued during the period.' }
  ];

  viewMode: ViewMode = 'explorer';
  selectedGstReport = 'b2b';
  activeDimensions = new Set<string>(['date']);
  activeMetrics = new Set<string>(['revenue', 'cost', 'profit', 'profit_ex_gst', 'orders']);

  vendorOptions: FilterOption[] = [];
  branchOptions: FilterOption[] = [];
  categoryOptions: FilterOption[] = [];
  itemOptions: FilterOption[] = [];

  filterForm = new FormGroup({
    dateFrom: new FormControl<Date | null>(null),
    dateTo: new FormControl<Date | null>(null),
    granularity: new FormControl<string>('day'),
    vendor: new FormControl<string>(''),
    branch: new FormControl<string>(''),
    itemGroup: new FormControl<string>(''),
    item: new FormControl<string>('')
  });

  result: AnalyticsQueryResponse | null = null;
  isLoading = false;
  isLoadingOptions = false;
  status = '';
  lastRunOk = false;
  backendWarnings: string[] = [];
  rowCountWarning = '';

  chartType: ChartType = 'line';
  chartData: ChartConfiguration['data'] | null = null;
  chartOptions: ChartConfiguration['options'] = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { display: true } }
  };
  private runSequence = 0;

  readonly filteredVendors$: Observable<FilterOption[]> = this.buildFilteredOptions('vendor', () => this.vendorOptions);
  readonly filteredBranches$: Observable<FilterOption[]> = this.buildFilteredOptions('branch', () => this.branchOptions);
  readonly filteredCategories$: Observable<FilterOption[]> = this.buildFilteredOptions('itemGroup', () => this.categoryOptions);
  readonly filteredItems$: Observable<FilterOption[]> = this.buildFilteredOptions('item', () => this.itemOptions);

  constructor(
    private readonly analyticsService: AnalyticsService,
    private readonly vendorService: VendorService,
    private readonly branchService: BranchService,
    private readonly categoryService: CategoryService,
    private readonly itemService: ItemService
  ) {}

  ngOnInit(): void {
    const now = new Date();
    this.filterForm.reset({
      dateFrom: new Date(now.getFullYear(), now.getMonth(), 1),
      dateTo: now,
      granularity: 'day',
      vendor: '',
      branch: '',
      itemGroup: '',
      item: ''
    });
    this.loadFilterOptions();
    this.run();
  }

  setViewMode(mode: ViewMode): void {
    this.viewMode = mode;
    if (mode === 'explorer' || mode === 'gstReports') {
      this.filterForm.controls.item.setValue('');
      this.activeDimensions.delete('item');
    }
    this.result = null;
    this.chartData = null;
    this.status = '';
    this.lastRunOk = false;
    this.backendWarnings = [];
    this.rowCountWarning = '';
    this.run();
  }

  selectGstReport(reportId: string): void {
    this.selectedGstReport = reportId;
    this.result = null;
    this.chartData = null;
    this.status = '';
    this.lastRunOk = false;
    this.backendWarnings = [];
    this.rowCountWarning = '';
    this.run();
  }

  toggleDimension(id: string): void {
    if (this.activeDimensions.has(id)) {
      if (this.activeDimensions.size > 1) {
        this.activeDimensions.delete(id);
      }
      return;
    }
    this.activeDimensions.add(id);
  }

  toggleMetric(id: string): void {
    if (this.activeMetrics.has(id)) {
      if (this.activeMetrics.size > 1) {
        this.activeMetrics.delete(id);
      }
      return;
    }
    this.activeMetrics.add(id);
  }

  showGranularity(): boolean {
    return this.viewMode === 'explorer' && this.activeDimensions.has('date');
  }

  // --- Date presets ---
  setPreset(preset: 'today' | '7d' | '30d' | 'thisMonth' | 'lastMonth' | 'thisQuarter' | 'ytd'): void {
    const now = new Date();
    let from: Date;
    let to: Date = now;

    switch (preset) {
      case 'today':
        from = new Date(now.getFullYear(), now.getMonth(), now.getDate());
        to = from;
        break;
      case '7d':
        from = new Date(now);
        from.setDate(from.getDate() - 6);
        break;
      case '30d':
        from = new Date(now);
        from.setDate(from.getDate() - 29);
        break;
      case 'thisMonth':
        from = new Date(now.getFullYear(), now.getMonth(), 1);
        break;
      case 'lastMonth': {
        const lm = new Date(now.getFullYear(), now.getMonth() - 1, 1);
        from = lm;
        to = new Date(now.getFullYear(), now.getMonth(), 0);
        break;
      }
      case 'thisQuarter': {
        const qStart = Math.floor(now.getMonth() / 3) * 3;
        from = new Date(now.getFullYear(), qStart, 1);
        break;
      }
      case 'ytd':
        from = new Date(now.getFullYear(), 0, 1);
        break;
      default:
        from = new Date(now.getFullYear(), now.getMonth(), 1);
    }

    this.filterForm.patchValue({ dateFrom: from, dateTo: to });
  }

  run(): void {

    const sequence = ++this.runSequence;
    this.isLoading = true;
    this.status = '';
    this.backendWarnings = [];
    this.rowCountWarning = '';

    const request = this.buildRequest();
    const query$ = this.viewMode === 'priceHistory'
      ? this.analyticsService.itemPriceHistory(request)
      : this.viewMode === 'gstReports'
        ? this.analyticsService.gstr1(request)
        : this.analyticsService.query(request);

    query$
      .pipe(finalize(() => {
        if (sequence === this.runSequence) {
          this.isLoading = false;
        }
      }))
      .subscribe({
        next: res => {
          if (sequence !== this.runSequence) return;
          this.result = res;
          this.lastRunOk = true;
          this.status = `${res.rows.length} row(s) loaded.`;
          this.backendWarnings = res.warnings ?? [];
          if (res.rows.length > ROW_WARN_THRESHOLD) {
            this.rowCountWarning = `${res.rows.length} rows returned — consider narrowing the date range or adding more filters.`;
          }
          this.buildChart(res);
        },
        error: err => {
          if (sequence !== this.runSequence) return;
          this.result = null;
          this.lastRunOk = false;
          this.chartData = null;
          this.status = formatUiError(err, this.viewMode === 'priceHistory'
            ? 'Failed to load item price history.'
            : this.viewMode === 'gstReports'
              ? 'Failed to run GSTR-1 report.'
            : 'Failed to run analytics query.');
        }
      });
  }

  exportAs(format: ExportFormat): void {
    const request = this.buildRequest();
    const export$ = this.viewMode === 'priceHistory'
      ? this.analyticsService.exportItemPriceHistory(request, format)
      : this.viewMode === 'gstReports'
        ? this.analyticsService.exportGstr1(request, format)
        : this.analyticsService.export(request, format);

    const ext = format === 'xlsx' ? 'xlsx' : format === 'pdf' ? 'pdf' : 'csv';
    const base = this.viewMode === 'priceHistory'
      ? 'analytics-item-price-history'
      : this.viewMode === 'gstReports'
        ? `gstr1-${this.selectedGstReport}`
        : 'analytics-export';

    export$.subscribe({
      next: blob => {
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = `${base}.${ext}`;
        anchor.click();
        URL.revokeObjectURL(url);
      },
      error: err => {
        this.status = formatUiError(err, 'Export failed.');
      }
    });
  }

  clear(): void {
    this.runSequence++;
    const now = new Date();
    this.filterForm.reset({
      dateFrom: new Date(now.getFullYear(), now.getMonth(), 1),
      dateTo: now,
      granularity: 'day',
      vendor: '',
      branch: '',
      itemGroup: '',
      item: ''
    });
    this.activeDimensions = new Set<string>(['date']);
    this.activeMetrics = new Set<string>(['revenue', 'cost', 'profit', 'orders']);
    this.result = null;
    this.chartData = null;
    this.status = '';
    this.lastRunOk = false;
    this.viewMode = 'explorer';
    this.selectedGstReport = 'b2b';
    this.backendWarnings = [];
    this.rowCountWarning = '';
  }

  kpiIcon(kpi: AnalyticsKpi): string {
    if (kpi.id === 'revenue') return 'trending_up';
    if (kpi.id === 'cost') return 'receipt_long';
    if (kpi.id === 'profit' || kpi.id === 'profit_ex_gst') return 'savings';
    if (kpi.id === 'margin_pct' || kpi.id === 'margin_pct_ex_gst') return 'percent';
    if (kpi.id === 'orders') return 'shopping_cart';
    if (kpi.id === 'avg_order_value') return 'equalizer';
    if (kpi.id === 'price_points') return 'timeline';
    if (kpi.id === 'items') return 'inventory_2';
    if (kpi.id === 'avg_price') return 'price_change';
    if (kpi.id === 'quantity') return 'scale';
    if (kpi.id === 'rows') return 'table_rows';
    if (kpi.id === 'taxable') return 'receipt';
    return 'bar_chart';
  }

  kpiClass(kpi: AnalyticsKpi): string {
    if (kpi.id === 'profit' || kpi.id === 'profit_ex_gst' || kpi.id === 'margin_pct' || kpi.id === 'margin_pct_ex_gst') {
      return kpi.value > 0 ? 'kpi-positive' : kpi.value < 0 ? 'kpi-negative' : '';
    }
    return '';
  }

  formatKpi(kpi: AnalyticsKpi): string {
    if (kpi.valueType === 'CURRENCY') return '₹' + this.formatNum(kpi.value);
    if (kpi.valueType === 'PERCENT') return this.formatNum(kpi.value) + '%';
    return this.formatNum(kpi.value);
  }

  get tableColumns(): AnalyticsColumn[] { return this.result?.columns ?? []; }
  get tableRows(): Record<string, unknown>[] { return this.result?.rows ?? []; }
  get totalRow(): Record<string, unknown> { return this.result?.totalsRow ?? {}; }
  get kpis(): AnalyticsKpi[] { return this.result?.kpis ?? []; }
  get selectedGstReportLabel(): string {
    return this.gstReports.find(report => report.id === this.selectedGstReport)?.label ?? 'GSTR-1';
  }
  get emptyResultMessage(): string {
    if (this.viewMode !== 'gstReports') return 'No rows returned for the selected filters.';
    if (this.selectedGstReport === 'b2b') {
      return 'No B2B rows found. Registered customer invoices need GSTIN or Tax ID on the Customer master.';
    }
    if (this.selectedGstReport === 'cdnr') {
      return 'No CDNR rows found. Approved customer credit or debit notes are required for this report.';
    }
    return `No ${this.selectedGstReportLabel} rows found for the selected period.`;
  }

  isDimension(col: AnalyticsColumn): boolean { return col.colType === 'DIMENSION'; }
  isCurrency(col: AnalyticsColumn): boolean { return col.colType === 'CURRENCY'; }
  isPercent(col: AnalyticsColumn): boolean { return col.colType === 'PERCENT'; }
  isProfitCol(col: AnalyticsColumn): boolean {
    return col.id === 'profit' || col.id === 'profit_ex_gst' || col.id === 'margin_pct' || col.id === 'margin_pct_ex_gst';
  }

  formatCell(col: AnalyticsColumn, value: unknown): string {
    if (value === null || value === undefined || value === '') return '—';
    if (col.colType === 'CURRENCY') return '₹' + this.formatNum(Number(value));
    if (col.colType === 'PERCENT') return this.formatNum(Number(value)) + '%';
    if (this.viewMode === 'gstReports' && col.colType === 'NUMBER') return this.formatNum(Number(value));
    if (col.colType === 'NUMBER') return String(Math.round(Number(value)));
    return String(value);
  }

  cellClass(col: AnalyticsColumn, value: unknown): string {
    if (!this.isProfitCol(col)) return '';
    const n = Number(value);
    return n > 0 ? 'cell-pos' : n < 0 ? 'cell-neg' : '';
  }

  canRun(): boolean {
    return !this.isLoading;
  }

  canExport(): boolean {
    return this.lastRunOk && !this.isLoading;
  }

  private loadFilterOptions(): void {
    this.isLoadingOptions = true;
    forkJoin({
      vendors: this.vendorService.listVendors().pipe(catchError(() => of([]))),
      branches: this.branchService.listBranches().pipe(catchError(() => of([]))),
      categories: this.categoryService.listCategories().pipe(catchError(() => of([]))),
      items: this.itemService.listItems().pipe(catchError(() => of([])))
    })
      .pipe(finalize(() => (this.isLoadingOptions = false)))
      .subscribe(({ vendors, branches, categories, items }) => {
        this.vendorOptions = this.uniqueOptions((vendors ?? []).map((row: any) => String(row?.supplier_name ?? row?.name ?? '').trim()));
        this.branchOptions = this.uniqueOptions((branches ?? []).map((row: any) => String(row?.customer_name ?? row?.name ?? '').trim()));
        this.categoryOptions = this.uniqueOptions((categories ?? []).map((row: any) => String(row?.item_group_name ?? row?.name ?? '').trim()));
        this.itemOptions = this.uniqueOptions((items ?? []).map((row: any) => {
          const code = String(row?.item_code ?? row?.name ?? '').trim();
          const name = String(row?.item_name ?? '').trim();
          return code && name ? `${code} - ${name}` : code || name;
        }));
      });
  }

  private uniqueOptions(labels: string[]): FilterOption[] {
    const seen = new Set<string>();
    return labels
      .map(label => label.trim())
      .filter(label => {
        if (!label || seen.has(label.toLowerCase())) {
          return false;
        }
        seen.add(label.toLowerCase());
        return true;
      })
      .sort((a, b) => a.localeCompare(b))
      .map(label => ({ label }));
  }

  private buildFilteredOptions(
    controlName: 'vendor' | 'branch' | 'itemGroup' | 'item',
    source: () => FilterOption[]
  ): Observable<FilterOption[]> {
    return this.filterForm.controls[controlName].valueChanges.pipe(
      startWith(this.filterForm.controls[controlName].value ?? ''),
      map(value => this.filterOptions(source(), String(value ?? '')))
    );
  }

  private filterOptions(options: FilterOption[], rawValue: string): FilterOption[] {
    const query = rawValue.trim().toLowerCase();
    if (!query) {
      return options.slice(0, 100);
    }
    return options.filter(option => option.label.toLowerCase().includes(query)).slice(0, 100);
  }

  private buildRequest() {
    const v = this.filterForm.value;
    const filters: Record<string, string> = {};
    if (this.viewMode === 'gstReports') {
      filters['gstReport'] = this.selectedGstReport;
    } else {
      if (v.vendor) filters['vendor'] = v.vendor;
      if (v.branch) filters['branch'] = v.branch;
      if (v.itemGroup) filters['itemGroup'] = v.itemGroup;
      if (v.item && this.viewMode === 'priceHistory') filters['item'] = v.item;
    }
    return {
      dateFrom: this.fmtDate(v.dateFrom ?? null),
      dateTo: this.fmtDate(v.dateTo ?? null),
      granularity: v.granularity ?? 'day',
      dimensions: Array.from(this.activeDimensions),
      metrics: Array.from(this.activeMetrics),
      filters
    };
  }

  private buildChart(res: AnalyticsQueryResponse): void {
    this.chartData = null;
    if (!res.rows.length) return;
    if (this.viewMode === 'gstReports') return;
    if (this.viewMode === 'priceHistory') {
      this.buildPriceHistoryChart(res);
      return;
    }
    if (this.activeDimensions.has('date')) {
      this.buildLineChart(res);
      return;
    }
    this.buildBarChart(res);
  }

  private buildLineChart(res: AnalyticsQueryResponse): void {
    const labels = res.rows.map(r => String(r['date'] ?? ''));
    const datasets: ChartConfiguration['data']['datasets'] = [];

    // Currency metrics go on the primary Y axis; count/percent on a secondary axis
    const secondaryMetrics = new Set(['orders', 'quantity', 'margin_pct', 'margin_pct_ex_gst']);

    for (const met of this.activeMetrics) {
      const metCol = res.columns.find(c => c.id === met);
      if (!metCol) continue;
      const data = res.rows.map(r => Number(r[met] ?? 0));
      if (!data.some(v => v !== 0)) continue;
      datasets.push({
        label: metCol.label,
        data,
        borderColor: METRIC_COLOR[met] ?? '#6366f1',
        backgroundColor: 'transparent',
        pointRadius: 3,
        tension: 0.3,
        yAxisID: secondaryMetrics.has(met) ? 'y1' : 'y'
      });
    }

    if (!datasets.length) return;

    const hasSecondary = datasets.some(d => (d as any).yAxisID === 'y1');
    this.chartType = 'line';
    this.chartOptions = {
      responsive: true,
      maintainAspectRatio: false,
      plugins: { legend: { display: true } },
      scales: {
        y: { beginAtZero: false, position: 'left' },
        ...(hasSecondary ? {
          y1: { beginAtZero: true, position: 'right', grid: { drawOnChartArea: false } }
        } : {})
      }
    };
    this.chartData = { labels, datasets };
  }

  private buildPriceHistoryChart(res: AnalyticsQueryResponse): void {
    const labels = Array.from(new Set(res.rows.map(row => String(row['date'] ?? ''))));
    const itemKeys = Array.from(new Set(res.rows.map(row => String(row['item_code'] || row['item_name'] || 'Item'))));
    const palette = ['#2563eb', '#7c3aed', '#16a34a', '#ea580c', '#dc2626', '#0f766e'];

    const datasets = itemKeys.map((itemKey, index) => {
      const series = labels.map(label => {
        const row = res.rows.find(entry => String(entry['date'] ?? '') === label
          && String(entry['item_code'] || entry['item_name'] || 'Item') === itemKey);
        return Number(row?.['price'] ?? 0);
      });
      return {
        label: itemKey,
        data: series,
        borderColor: palette[index % palette.length],
        backgroundColor: 'transparent',
        pointRadius: 3,
        tension: 0.25
      };
    });

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
    const showableOrder = ['revenue', 'profit', 'profit_ex_gst', 'cost', 'orders', 'quantity', 'avg_order_value', 'margin_pct', 'margin_pct_ex_gst'];
    const barPalette = ['#6366f1', '#16a34a', '#9ca3af', '#f59e0b', '#06b6d4', '#ec4899'];

    const datasets = Array.from(this.activeMetrics)
      .filter(m => showableOrder.includes(m))
      .sort((a, b) => showableOrder.indexOf(a) - showableOrder.indexOf(b))
      .slice(0, 3)
      .map((met, i) => {
        const metCol = res.columns.find(c => c.id === met);
        if (!metCol) return null;
        return {
          label: metCol.label,
          data: res.rows.map(r => Number(r[met] ?? 0)),
          backgroundColor: METRIC_COLOR[met] ?? barPalette[i],
          borderRadius: 4
        };
      })
      .filter((d): d is NonNullable<typeof d> => d !== null);

    if (!datasets.length) return;

    this.chartType = 'bar';
    this.chartOptions = {
      responsive: true,
      maintainAspectRatio: false,
      indexAxis: 'y' as const,
      plugins: { legend: { display: datasets.length > 1 } },
      scales: { x: { beginAtZero: true } }
    };
    this.chartData = { labels, datasets };
  }

  private fmtDate(date: Date | null): string {
    if (!date) return '';
    return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`;
  }

  private formatNum(n: number): string {
    if (!Number.isFinite(n)) return '—';
    return n.toLocaleString('en-IN', { maximumFractionDigits: 2 });
  }

  private hasText(value: string | null | undefined): boolean {
    return !!value && value.trim().length > 0;
  }
}
