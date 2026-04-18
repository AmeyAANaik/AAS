import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { finalize, Subscription } from 'rxjs';
import { PageHeaderComponent } from '../shared/page-header/page-header.component';
import { formatUiError } from '../shared/error-message.util';
import { BillReviewDetail, BillReviewListItem } from './bill-review.model';
import { BillReviewService } from './bill-review.service';

type BillReviewPartyGroup = {
  key: string;
  label: string;
  partyType: string;
  party: string;
  partyName: string;
  count: number;
  totalAmount: number;
  items: BillReviewListItem[];
};

@Component({
  selector: 'app-bill-review-page',
  standalone: true,
  imports: [CommonModule, FormsModule, MatButtonModule, MatCardModule, MatIconModule, PageHeaderComponent],
  templateUrl: './bill-review-page.component.html',
  styleUrl: './bill-review-page.component.scss'
})
export class BillReviewPageComponent implements OnInit, OnDestroy {
  readonly statusFilters = [
    { label: 'Under review', value: 'UNDER_REVIEW' },
    { label: 'Approved', value: 'APPROVED' },
    { label: 'Rejected', value: 'REJECTED' }
  ];
  readonly partyFilters = [
    { label: 'All', value: '' },
    { label: 'Branches', value: 'Customer' },
    { label: 'Vendors', value: 'Supplier' }
  ];

  pendingCount = 0;
  items: BillReviewListItem[] = [];
  selectedStatus = 'UNDER_REVIEW';
  selectedPartyType = '';
  selectedItem: BillReviewDetail | null = null;
  selectedPaymentId = '';
  notes = '';
  isLoading = false;
  isDetailLoading = false;
  isSaving = false;
  statusMessage = '';
  errorMessage = '';

  private readonly subscriptions = new Subscription();

  constructor(public readonly billReviewService: BillReviewService) {}

  ngOnInit(): void {
    this.loadCount();
    this.loadQueue(true);
    this.subscriptions.add(this.billReviewService.refresh$.subscribe(() => {
      this.loadCount();
      this.loadQueue(false);
    }));
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

  applyStatus(status: string): void {
    this.selectedStatus = status;
    this.selectedItem = null;
    this.selectedPaymentId = '';
    this.notes = '';
    this.loadQueue(true);
  }

  applyPartyFilter(partyType: string): void {
    this.selectedPartyType = partyType;
    this.selectedItem = null;
    this.selectedPaymentId = '';
    this.notes = '';
    this.loadQueue(true);
  }

  selectPayment(paymentId: string): void {
    if (!paymentId) {
      return;
    }
    this.selectedPaymentId = paymentId;
    this.isDetailLoading = true;
    this.errorMessage = '';
    this.billReviewService.getDetail(paymentId)
      .pipe(finalize(() => (this.isDetailLoading = false)))
      .subscribe({
        next: detail => {
          this.selectedItem = detail;
          this.notes = String((detail.payment?.['aas_payment_review_notes'] ?? '') as string).trim();
        },
        error: err => {
          this.selectedItem = null;
          this.errorMessage = formatUiError(err, 'Unable to load payment details.');
        }
      });
  }

  approve(): void {
    if (!this.selectedPaymentId || this.isSaving) {
      return;
    }
    this.isSaving = true;
    this.statusMessage = '';
    this.errorMessage = '';
    this.billReviewService.approve(this.selectedPaymentId, { notes: this.notes.trim() || undefined })
      .pipe(finalize(() => (this.isSaving = false)))
      .subscribe({
        next: detail => {
          this.selectedItem = detail ?? null;
          const warnings = (detail as any)?.warnings as Array<{ code?: string; recordedDue?: any; currentDue?: any }> | undefined;
          const dueChanged = Array.isArray(warnings) && warnings.some(w => String(w?.code ?? '').toUpperCase() === 'DUE_CHANGED');
          if (dueChanged) {
            const first = warnings?.find(w => String(w?.code ?? '').toUpperCase() === 'DUE_CHANGED');
            const recorded = Number(first?.recordedDue ?? (detail as any)?.payment?.['aas_due_amount'] ?? 0);
            const current = Number(first?.currentDue ?? (detail as any)?.currentDueAmount ?? 0);
            this.statusMessage = `Payment approved (due changed: recorded ₹${recorded.toFixed(2)}, current ₹${current.toFixed(2)}).`;
          } else {
            this.statusMessage = 'Payment approved and submitted.';
          }
          this.loadCount();
          this.loadQueue(true);
        },
        error: err => {
          this.errorMessage = formatUiError(err, 'Unable to approve payment.');
        }
      });
  }

  reject(): void {
    if (!this.selectedPaymentId || this.isSaving) {
      return;
    }
    if (!this.notes.trim()) {
      this.errorMessage = 'Notes are required when rejecting a payment.';
      return;
    }
    this.isSaving = true;
    this.statusMessage = '';
    this.errorMessage = '';
    this.billReviewService.reject(this.selectedPaymentId, { notes: this.notes.trim() })
      .pipe(finalize(() => (this.isSaving = false)))
      .subscribe({
        next: () => {
          this.statusMessage = 'Payment rejected.';
          this.loadCount();
          this.loadQueue(true);
        },
        error: err => {
          this.errorMessage = formatUiError(err, 'Unable to reject payment.');
        }
      });
  }

  private loadQueue(resetSelection: boolean): void {
    this.isLoading = true;
    this.errorMessage = '';
    this.billReviewService.listPayments(this.selectedStatus, this.selectedPartyType)
      .pipe(finalize(() => (this.isLoading = false)))
      .subscribe({
        next: items => {
          this.items = items ?? [];
          if (!resetSelection && this.selectedPaymentId) {
            const stillExists = this.items.some(item => item.paymentId === this.selectedPaymentId);
            if (stillExists) {
              return;
            }
          }
          const nextSelected = this.items[0]?.paymentId ?? '';
          if (nextSelected) {
            this.selectPayment(nextSelected);
          } else {
            this.selectedItem = null;
            this.selectedPaymentId = '';
          }
        },
        error: err => {
          this.items = [];
          this.selectedItem = null;
          this.selectedPaymentId = '';
          this.errorMessage = formatUiError(err, 'Unable to load bill review queue.');
        }
      });
  }

  private loadCount(): void {
    this.billReviewService.getPendingCount().subscribe({
      next: result => {
        this.pendingCount = Number(result?.pendingCount ?? 0);
      },
      error: () => {
        this.pendingCount = 0;
      }
    });
  }

  get canDecide(): boolean {
    return this.selectedStatus === 'UNDER_REVIEW';
  }

  trackByPaymentId(_index: number, item: BillReviewListItem): string {
    return item.paymentId;
  }

  get payment(): Record<string, any> | null {
    return this.selectedItem?.payment ?? null;
  }

  get paymentAmount(): number {
    const payment = this.payment;
    if (!payment) {
      return 0;
    }
    const value = payment['paid_amount'] ?? payment['received_amount'] ?? 0;
    const number = Number(value);
    return Number.isFinite(number) ? number : 0;
  }

  get groupedItems(): BillReviewPartyGroup[] {
    const grouped = new Map<string, BillReviewPartyGroup>();
    for (const item of this.items ?? []) {
      const partyType = (item.partyType || '').trim();
      const party = (item.party || '').trim();
      const partyName = (item.partyName || item.party || '').trim();
      const key = `${partyType}::${party}`;
      const existing = grouped.get(key);
      if (existing) {
        existing.items.push(item);
        existing.count += 1;
        existing.totalAmount += Number(item.paidAmount ?? 0) || 0;
        continue;
      }
      const label = `${this.prettyPartyType(partyType)} · ${partyName || party || 'Unknown'}`;
      grouped.set(key, {
        key,
        label,
        partyType,
        party,
        partyName,
        count: 1,
        totalAmount: Number(item.paidAmount ?? 0) || 0,
        items: [item]
      });
    }
    return Array.from(grouped.values());
  }

  private prettyPartyType(partyType: string): string {
    const value = (partyType || '').toLowerCase();
    if (value === 'supplier' || value === 'vendor') {
      return 'Vendor (Supplier)';
    }
    if (value === 'customer' || value === 'branch') {
      return 'Branch (Customer)';
    }
    return partyType || 'Party';
  }
}
