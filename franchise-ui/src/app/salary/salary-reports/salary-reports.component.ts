import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { Subscription } from 'rxjs';
import { EmptyStateComponent } from '../../shared/empty-state/empty-state.component';
import { SalaryStoreService } from '../salary-store.service';
import { SalaryPaymentView } from '../salary.model';

interface MonthRow {
  ym: string;
  label: string;
  total: number;
  paid: number;
  pending: number;
  count: number;
}

@Component({
  selector: 'app-salary-reports',
  standalone: true,
  imports: [
    CommonModule, FormsModule, MatFormFieldModule, MatIconModule, MatSelectModule, MatTableModule,
    EmptyStateComponent
  ],
  templateUrl: './salary-reports.component.html',
  styleUrl: './salary-reports.component.css'
})
export class SalaryReportsComponent implements OnInit, OnDestroy {
  year = new Date().getFullYear();
  years: number[] = [];
  rows: MonthRow[] = [];
  loading = false;

  readonly columns = ['month', 'count', 'paid', 'pending', 'total'];

  private payments: SalaryPaymentView[] = [];
  private sub?: Subscription;

  constructor(private store: SalaryStoreService) {}

  ngOnInit(): void {
    this.reload();
    this.sub = this.store.changes$.subscribe(() => this.reload());
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  get yearTotal(): number {
    return this.rows.reduce((sum, r) => sum + r.total, 0);
  }

  get yearPaid(): number {
    return this.rows.reduce((sum, r) => sum + r.paid, 0);
  }

  get yearPending(): number {
    return this.rows.reduce((sum, r) => sum + r.pending, 0);
  }

  get monthsWithData(): number {
    return this.rows.filter(r => r.count > 0).length;
  }

  reload(): void {
    this.loading = true;
    this.store.listPayments().subscribe(list => {
      this.payments = list;
      this.buildYears();
      this.build();
      this.loading = false;
    });
  }

  onYearChange(): void {
    this.build();
  }

  private buildYears(): void {
    const set = new Set<number>();
    this.payments.forEach(p => set.add(Number(p.month.slice(0, 4))));
    set.add(new Date().getFullYear());
    this.years = [...set].sort((a, b) => b - a);
    if (!this.years.includes(this.year)) {
      this.year = this.years[0];
    }
  }

  private build(): void {
    const rows: MonthRow[] = [];
    for (let m = 1; m <= 12; m++) {
      const ym = `${this.year}-${String(m).padStart(2, '0')}`;
      const forMonth = this.payments.filter(p => p.month === ym);
      const paid = forMonth.filter(p => p.status === 'Paid').reduce((s, p) => s + p.amount, 0);
      const pending = forMonth.filter(p => p.status === 'Pending').reduce((s, p) => s + p.amount, 0);
      rows.push({
        ym,
        label: this.monthLabel(m),
        total: this.store.totalForMonth(ym),
        paid,
        pending,
        count: forMonth.length
      });
    }
    this.rows = rows;
  }

  private monthLabel(m: number): string {
    return new Date(this.year, m - 1, 1).toLocaleString('en-IN', { month: 'long' });
  }
}
