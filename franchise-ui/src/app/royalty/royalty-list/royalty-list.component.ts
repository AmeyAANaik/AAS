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
import { RoyaltyEntry } from '../royalty.model';
import { RoyaltyStoreService } from '../royalty-store.service';

@Component({
  selector: 'app-royalty-list',
  standalone: true,
  imports: [
    CommonModule, FormsModule, MatButtonModule, MatFormFieldModule, MatIconModule,
    MatInputModule, MatSnackBarModule, MatTableModule,
    EmptyStateComponent, StatusPillComponent
  ],
  templateUrl: './royalty-list.component.html',
  styleUrl: './royalty-list.component.css'
})
export class RoyaltyListComponent implements OnInit, OnDestroy {
  entries: RoyaltyEntry[] = [];
  loading = false;

  /** yyyy-mm for the generate-month picker. */
  selectedMonth = new Date().toISOString().slice(0, 7);

  /** Preview of the figures for the selected month before generating. */
  ratePercent = 0;
  previewBase = 0;
  previewDue = 0;

  /** Inline "record payment" state. */
  payingId: string | null = null;
  payAmount: number | null = null;

  readonly columns = ['month', 'base', 'rate', 'due', 'paid', 'outstanding', 'status', 'actions'];

  private sub?: Subscription;

  constructor(private store: RoyaltyStoreService, private snack: MatSnackBar) {}

  ngOnInit(): void {
    this.ratePercent = this.store.getConfig().ratePercent;
    this.refreshPreview();
    this.reload();
    this.sub = this.store.changes$.subscribe(() => {
      this.ratePercent = this.store.getConfig().ratePercent;
      this.refreshPreview();
      this.reload();
    });
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  get totalDue(): number {
    return this.round(this.entries.reduce((s, e) => s + e.dueAmount, 0));
  }

  get totalPaid(): number {
    return this.round(this.entries.reduce((s, e) => s + e.paidAmount, 0));
  }

  get outstanding(): number {
    return this.store.outstandingTotal();
  }

  outstandingFor(e: RoyaltyEntry): number {
    return this.round(Math.max(0, e.dueAmount - e.paidAmount));
  }

  onMonthChange(value: string): void {
    if (value) {
      this.selectedMonth = value;
    }
    this.refreshPreview();
  }

  private refreshPreview(): void {
    this.previewDue = this.store.dueForMonth(this.selectedMonth);
    // Back out the base from due so the preview reflects current sales.
    this.previewBase = this.ratePercent > 0
      ? this.round(this.previewDue / (this.ratePercent / 100))
      : 0;
  }

  generate(): void {
    this.store.generateForMonth(this.selectedMonth).subscribe(entry => {
      this.snack.open(`Royalty generated for ${entry.month}`, 'OK', { duration: 2200 });
      this.reload();
    });
  }

  startPayment(e: RoyaltyEntry): void {
    this.payingId = e.id;
    this.payAmount = this.outstandingFor(e) || null;
  }

  cancelPayment(): void {
    this.payingId = null;
    this.payAmount = null;
  }

  confirmPayment(e: RoyaltyEntry): void {
    const amount = Number(this.payAmount);
    if (!amount || amount <= 0) {
      this.snack.open('Enter a payment amount greater than zero.', 'OK', { duration: 2600 });
      return;
    }
    this.store.recordPayment(e.id, amount).subscribe(() => {
      this.snack.open('Payment recorded', 'OK', { duration: 2200 });
      this.cancelPayment();
      this.reload();
    });
  }

  private reload(): void {
    this.loading = true;
    this.store.list().subscribe(list => {
      this.entries = list;
      this.loading = false;
    });
  }

  private round(n: number): number {
    return Math.round(n * 100) / 100;
  }
}
