import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatMenuModule } from '@angular/material/menu';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { Subscription } from 'rxjs';
import { EmptyStateComponent } from '../../shared/empty-state/empty-state.component';
import { MonthPickerFieldComponent } from '../../shared/month-picker-field/month-picker-field.component';
import { ReportDownloadService, ReportExportFormat } from '../../shared/report-download.service';
import { ExpenseStoreService } from '../expense-store.service';

interface CategoryRow {
  category: string;
  total: number;
  percent: number;
}

interface MonthlyRow {
  ym: string;
  label: string;
  total: number;
}

@Component({
  selector: 'app-expense-reports',
  standalone: true,
  imports: [
    CommonModule, FormsModule, MatButtonModule, MatFormFieldModule, MatIconModule, MatInputModule, MatMenuModule, MatSelectModule,
    MatTableModule, EmptyStateComponent, MonthPickerFieldComponent
  ],
  templateUrl: './expense-reports.component.html',
  styleUrl: './expense-reports.component.css'
})
export class ExpenseReportsComponent implements OnInit, OnDestroy {
  readonly categoryColumns = ['category', 'total', 'percent'];
  readonly monthlyColumns = ['label', 'total'];

  selectedMonth = new Date().toISOString().slice(0, 7); // yyyy-mm
  selectedYear = new Date().getFullYear();
  years: number[] = [];

  monthlyTotal = 0;
  categoryRows: CategoryRow[] = [];
  monthlyRows: MonthlyRow[] = [];
  yearlyTotal = 0;

  private sub?: Subscription;

  constructor(private store: ExpenseStoreService, private downloads: ReportDownloadService) {}

  ngOnInit(): void {
    const current = new Date().getFullYear();
    this.years = [current, current - 1, current - 2];
    this.refresh();
    this.sub = this.store.changes$.subscribe(() => this.refresh());
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  onMonthChange(value: string): void {
    if (value) {
      this.selectedMonth = value;
      const year = Number(value.slice(0, 4));
      if (Number.isFinite(year)) {
        this.selectedYear = year;
      }
    }
    this.refresh();
  }

  onYearChange(value: number): void {
    this.selectedYear = Number(value);
    this.refresh();
  }

  downloadMonthly(format: ReportExportFormat): void {
    this.downloads.download({
      title: 'Expense Category Report',
      subtitle: 'Category-wise expense breakup',
      period: this.selectedMonth,
      fileName: `expense-category-report-${this.selectedMonth}`,
      columns: [
        { key: 'category', label: 'Category' },
        { key: 'total', label: 'Total', align: 'right' },
        { key: 'percent', label: '% of month', align: 'right' }
      ],
      rows: this.categoryRows.map(row => ({
        category: row.category,
        total: this.money(row.total),
        percent: `${row.percent.toFixed(1)}%`
      })),
      summary: [
        { label: 'Monthly total', value: this.money(this.monthlyTotal) },
        { label: 'Categories', value: this.categoryRows.length },
        { label: 'Top category', value: this.topCategory }
      ]
    }, format);
  }

  downloadYearly(format: ReportExportFormat): void {
    this.downloads.download({
      title: 'Expense Monthly Totals',
      subtitle: 'Year-wise operating expense report',
      period: String(this.selectedYear),
      fileName: `expense-yearly-report-${this.selectedYear}`,
      columns: [
        { key: 'month', label: 'Month' },
        { key: 'total', label: 'Total', align: 'right' }
      ],
      rows: this.monthlyRows.map(row => ({ month: row.label, total: this.money(row.total) })),
      summary: [{ label: 'Year total', value: this.money(this.yearlyTotal) }]
    }, format);
  }

  private refresh(): void {
    this.buildMonthly();
    this.buildYearly();
  }

  private buildMonthly(): void {
    const ym = this.selectedMonth;
    this.monthlyTotal = this.store.totalForMonth(ym);
    const breakdown = this.store.byCategory(ym);
    this.categoryRows = breakdown.map(row => ({
      category: row.category,
      total: row.total,
      percent: this.monthlyTotal > 0 ? (row.total / this.monthlyTotal) * 100 : 0
    }));
  }

  private buildYearly(): void {
    const rows: MonthlyRow[] = [];
    let total = 0;
    for (let m = 0; m < 12; m++) {
      const ym = `${this.selectedYear}-${String(m + 1).padStart(2, '0')}`;
      const monthTotal = this.store.totalForMonth(ym);
      total += monthTotal;
      rows.push({ ym, label: this.monthLabel(m), total: monthTotal });
    }
    this.monthlyRows = rows;
    this.yearlyTotal = Math.round(total * 100) / 100;
  }

  get topCategory(): string {
    return this.categoryRows.length ? this.categoryRows[0].category : '—';
  }

  private monthLabel(monthIndex: number): string {
    const names = ['Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun', 'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'];
    return `${names[monthIndex]} ${this.selectedYear}`;
  }

  private money(value: number): string {
    return `Rs. ${value.toLocaleString('en-IN', { maximumFractionDigits: 2 })}`;
  }
}
