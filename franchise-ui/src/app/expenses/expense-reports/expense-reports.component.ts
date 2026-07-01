import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { Subscription } from 'rxjs';
import { EmptyStateComponent } from '../../shared/empty-state/empty-state.component';
import { ExpenseStoreService } from '../expense-store.service';
import { ExpenseCategory } from '../expense.model';

interface CategoryRow {
  category: ExpenseCategory;
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
    CommonModule, FormsModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    MatTableModule, EmptyStateComponent
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

  constructor(private store: ExpenseStoreService) {}

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
}
