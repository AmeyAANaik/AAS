import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatTableModule } from '@angular/material/table';
import { Subscription } from 'rxjs';
import { EmptyStateComponent } from '../../shared/empty-state/empty-state.component';
import { MonthPickerFieldComponent } from '../../shared/month-picker-field/month-picker-field.component';
import { ReportDownloadService, ReportExportFormat } from '../../shared/report-download.service';
import { SaleEntry } from '../sales.model';
import { SalesStoreService } from '../sales-store.service';

type ReportView = 'daily' | 'monthly' | 'yearly';

interface ReportRow {
  label: string;       // day / month / year
  gross: number;
  gst: number;
  discount: number;
  net: number;
  byMode: Record<string, number>;
}

interface Totals {
  gross: number;
  gst: number;
  discount: number;
  net: number;
  byMode: Record<string, number>;
}

@Component({
  selector: 'app-sales-reports',
  standalone: true,
  imports: [
    CommonModule, FormsModule, MatButtonToggleModule, MatFormFieldModule, MatIconModule,
    MatInputModule, MatMenuModule, MatTableModule, EmptyStateComponent, MonthPickerFieldComponent
  ],
  templateUrl: './sales-reports.component.html',
  styleUrl: './sales-reports.component.css'
})
export class SalesReportsComponent implements OnInit, OnDestroy {
  view: ReportView = 'daily';

  /** yyyy-mm for the month picker (drives daily + selects the year). */
  selectedMonth = new Date().toISOString().slice(0, 7);

  rows: ReportRow[] = [];
  totals: Totals = this.emptyTotals();

  /** Payment-mode names — drive the dynamic columns + payment KPI cards. */
  modeNames: string[] = [];
  columns: string[] = [];

  private entries: SaleEntry[] = [];
  private sub?: Subscription;

  constructor(private store: SalesStoreService, private downloads: ReportDownloadService) {}

  ngOnInit(): void {
    this.refreshData();
    this.sub = this.store.changes$.subscribe(() => this.refreshData());
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  private refreshData(): void {
    this.entries = this.store.listSnapshot();
    this.modeNames = this.store.knownPaymentModeNames();
    this.columns = ['label', 'gross', 'gst', 'discount', 'net', ...this.modeNames];
    this.rebuild();
  }

  get selectedYear(): string {
    return this.selectedMonth.slice(0, 4);
  }

  get scopeLabel(): string {
    switch (this.view) {
      case 'daily': return `Daily · ${this.selectedMonth}`;
      case 'monthly': return `Monthly · ${this.selectedYear}`;
      case 'yearly': return 'Yearly · all years';
    }
  }

  setView(view: ReportView): void {
    this.view = view;
    this.rebuild();
  }

  onMonthChange(value: string): void {
    if (value) {
      this.selectedMonth = value;
    }
    this.rebuild();
  }

  downloadReport(format: ReportExportFormat): void {
    const columns = [
      { key: 'label', label: this.view === 'daily' ? 'Date' : this.view === 'monthly' ? 'Month' : 'Year' },
      { key: 'gross', label: 'Gross sales', align: 'right' as const },
      { key: 'gst', label: 'GST', align: 'right' as const },
      { key: 'discount', label: 'Discount', align: 'right' as const },
      { key: 'net', label: 'Net sales', align: 'right' as const },
      ...this.modeNames.map(mode => ({ key: mode, label: mode, align: 'right' as const }))
    ];
    const rows = this.rows.map(row => ({
      label: row.label,
      gross: this.money(row.gross),
      gst: this.money(row.gst),
      discount: this.money(row.discount),
      net: this.money(row.net),
      ...Object.fromEntries(this.modeNames.map(mode => [mode, this.money(row.byMode[mode] ?? 0)]))
    }));
    this.downloads.download({
      title: 'Sales Report',
      subtitle: this.scopeLabel,
      period: this.view === 'yearly' ? 'All years' : this.selectedMonth,
      fileName: `sales-report-${this.view}-${this.view === 'yearly' ? 'all' : this.selectedMonth}`,
      columns,
      rows,
      summary: [
        { label: 'Gross sales', value: this.money(this.totals.gross) },
        { label: 'GST', value: this.money(this.totals.gst) },
        { label: 'Discount', value: this.money(this.totals.discount) },
        { label: 'Net sales', value: this.money(this.totals.net) }
      ]
    }, format);
  }

  private rebuild(): void {
    switch (this.view) {
      case 'daily':
        this.rows = this.buildRows(
          this.entries.filter(e => e.date.startsWith(this.selectedMonth)), e => e.date);
        break;
      case 'monthly':
        this.rows = this.buildRows(
          this.entries.filter(e => e.date.startsWith(this.selectedYear + '-')), e => e.date.slice(0, 7));
        break;
      case 'yearly':
        this.rows = this.buildRows(this.entries, e => e.date.slice(0, 4));
        break;
    }
    this.totals = this.sumTotals(this.rows);
  }

  private buildRows(entries: SaleEntry[], keyFn: (e: SaleEntry) => string): ReportRow[] {
    const map = new Map<string, ReportRow>();
    for (const e of entries) {
      const key = keyFn(e);
      const row = map.get(key) ?? {
        label: key, gross: 0, gst: 0, discount: 0, net: 0, byMode: {}
      };
      row.gross += e.grossSales;
      row.gst += e.gstAmount;
      row.discount += e.discount;
      row.net += e.netSales;
      for (const p of e.payments) {
        row.byMode[p.modeName] = (row.byMode[p.modeName] ?? 0) + p.amount;
      }
      map.set(key, row);
    }
    return [...map.entries()]
      .sort((a, b) => a[0].localeCompare(b[0]))
      .map(([, row]) => this.roundRow(row));
  }

  private sumTotals(rows: ReportRow[]): Totals {
    const totals = this.emptyTotals();
    for (const r of rows) {
      totals.gross += r.gross;
      totals.gst += r.gst;
      totals.discount += r.discount;
      totals.net += r.net;
      for (const mode of this.modeNames) {
        totals.byMode[mode] = (totals.byMode[mode] ?? 0) + (r.byMode[mode] ?? 0);
      }
    }
    totals.gross = this.r(totals.gross);
    totals.gst = this.r(totals.gst);
    totals.discount = this.r(totals.discount);
    totals.net = this.r(totals.net);
    for (const mode of this.modeNames) {
      totals.byMode[mode] = this.r(totals.byMode[mode] ?? 0);
    }
    return totals;
  }

  private roundRow(row: ReportRow): ReportRow {
    row.gross = this.r(row.gross);
    row.gst = this.r(row.gst);
    row.discount = this.r(row.discount);
    row.net = this.r(row.net);
    for (const k of Object.keys(row.byMode)) {
      row.byMode[k] = this.r(row.byMode[k]);
    }
    return row;
  }

  private emptyTotals(): Totals {
    const byMode: Record<string, number> = {};
    (this.modeNames ?? []).forEach(m => (byMode[m] = 0));
    return { gross: 0, gst: 0, discount: 0, net: 0, byMode };
  }

  private r(n: number): number {
    return Math.round(n * 100) / 100;
  }

  private money(value: number): string {
    return `Rs. ${value.toLocaleString('en-IN', { maximumFractionDigits: 2 })}`;
  }
}
