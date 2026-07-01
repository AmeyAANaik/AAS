import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { Subscription } from 'rxjs';
import { EmptyStateComponent } from '../../shared/empty-state/empty-state.component';
import { StatusPillComponent } from '../../shared/status-pill/status-pill.component';
import { SalaryStoreService } from '../salary-store.service';
import { SalaryPaymentView } from '../salary.model';

@Component({
  selector: 'app-salary-entry',
  standalone: true,
  imports: [
    CommonModule, FormsModule, MatButtonModule, MatFormFieldModule, MatIconModule,
    MatInputModule, MatSnackBarModule, MatTableModule,
    EmptyStateComponent, StatusPillComponent
  ],
  templateUrl: './salary-entry.component.html',
  styleUrl: './salary-entry.component.css'
})
export class SalaryEntryComponent implements OnInit, OnDestroy {
  month = new Date().toISOString().slice(0, 7);
  payments: SalaryPaymentView[] = [];
  loading = false;

  readonly columns = ['employee', 'amount', 'status', 'paidDate', 'actions'];

  private sub?: Subscription;

  constructor(
    private store: SalaryStoreService,
    private snack: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.reload();
    this.sub = this.store.changes$.subscribe(() => this.reload());
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  get total(): number {
    return this.store.totalForMonth(this.month);
  }

  get paidCount(): number {
    return this.payments.filter(p => p.status === 'Paid').length;
  }

  get pendingCount(): number {
    return this.payments.filter(p => p.status === 'Pending').length;
  }

  reload(): void {
    if (!this.month) {
      this.payments = [];
      return;
    }
    this.loading = true;
    this.store.paymentsForMonth(this.month).subscribe(list => {
      this.payments = list;
      this.loading = false;
    });
  }

  generate(): void {
    if (!this.month) {
      return;
    }
    this.store.generateMonth(this.month).subscribe(list => {
      this.payments = list;
      this.snack.open('Salary entries generated for the month', 'OK', { duration: 2200 });
    });
  }

  markPaid(p: SalaryPaymentView): void {
    this.store.markPaid(p.id).subscribe(() => {
      this.snack.open(`${p.employeeName} marked paid`, 'OK', { duration: 2000 });
    });
  }
}
