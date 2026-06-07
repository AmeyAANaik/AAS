import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { RouterLink } from '@angular/router';
import { finalize, Subscription } from 'rxjs';
import { PageHeaderComponent } from '../shared/page-header/page-header.component';
import { formatUiError } from '../shared/error-message.util';
import { BillReviewDetail, BillReviewItemType, BillReviewListItem } from './bill-review.model';
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
  imports: [CommonModule, FormsModule, MatButtonModule, MatCardModule, MatIconModule, RouterLink, PageHeaderComponent],
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
  selectedDocumentId = '';
  selectedItemType: BillReviewItemType = 'PAYMENT';
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
    this.clearSelection();
    this.loadQueue(true);
  }

  applyPartyFilter(partyType: string): void {
    this.selectedPartyType = partyType;
    this.clearSelection();
    this.loadQueue(true);
  }

  selectItem(item: BillReviewListItem): void {
    if (!item?.documentId) {
      return;
    }
    this.selectedDocumentId = item.documentId;
    this.selectedItemType = item.itemType;
    this.isDetailLoading = true;
    this.errorMessage = '';
    this.billReviewService.getDetail(item.itemType, item.documentId)
      .pipe(finalize(() => (this.isDetailLoading = false)))
      .subscribe({
        next: detail => {
          this.selectedItem = detail;
          this.notes = this.existingNotes(detail);
        },
        error: err => {
          this.selectedItem = null;
          this.errorMessage = formatUiError(err, 'Unable to load review details.');
        }
      });
  }

  approve(): void {
    if (!this.selectedDocumentId || this.isSaving) {
      return;
    }
    this.isSaving = true;
    this.statusMessage = '';
    this.errorMessage = '';
    this.billReviewService.approve(this.selectedItemType, this.selectedDocumentId, { notes: this.notes.trim() || undefined })
      .pipe(finalize(() => (this.isSaving = false)))
      .subscribe({
        next: detail => {
          this.selectedItem = detail ?? null;
          this.statusMessage = this.approvalMessage(detail);
          this.loadCount();
          this.loadQueue(true);
        },
        error: err => {
          this.errorMessage = formatUiError(err, 'Unable to approve review item.');
        }
      });
  }

  reject(): void {
    if (!this.selectedDocumentId || this.isSaving) {
      return;
    }
    if (!this.notes.trim()) {
      this.errorMessage = `Notes are required when rejecting a ${this.selectedLabel.toLowerCase()}.`;
      return;
    }
    this.isSaving = true;
    this.statusMessage = '';
    this.errorMessage = '';
    this.billReviewService.reject(this.selectedItemType, this.selectedDocumentId, { notes: this.notes.trim() })
      .pipe(finalize(() => (this.isSaving = false)))
      .subscribe({
        next: () => {
          this.statusMessage = `${this.selectedLabel} rejected.`;
          this.loadCount();
          this.loadQueue(true);
        },
        error: err => {
          this.errorMessage = formatUiError(err, 'Unable to reject review item.');
        }
      });
  }

  get canDecide(): boolean {
    return this.selectedStatus === 'UNDER_REVIEW';
  }

  get document(): Record<string, any> | null {
    return this.selectedItem?.document ?? this.selectedItem?.payment ?? null;
  }

  get selectedLabel(): string {
    return this.itemTypeLabel(this.selectedItem?.itemType || this.selectedItemType);
  }

  get itemAmount(): number {
    const document = this.document;
    if (!document) {
      return 0;
    }
    const type = (this.selectedItem?.itemType || this.selectedItemType || '').toUpperCase();
    if (type === 'PAYMENT') {
      const value = document['paid_amount'] ?? document['received_amount'] ?? 0;
      return Number(value) || 0;
    }
    return Number(document['aas_adjustment_amount'] ?? 0) || 0;
  }

  get canCaptureReceipt(): boolean {
    if (this.selectedItemType === 'PAYMENT') {
      return false;
    }
    const partyType = String(this.document?.['aas_adjustment_party_type'] ?? '').trim().toLowerCase();
    const categoryId = String(this.document?.['aas_category'] ?? '').trim();
    const partyId = String(this.document?.['aas_adjustment_party'] ?? '').trim();
    return partyType === 'customer' && !!partyId && !!categoryId;
  }

  get receiptQueryParams(): Record<string, string> {
    return {
      partyType: String(this.document?.['aas_adjustment_party_type'] ?? 'Customer').trim() || 'Customer',
      partyId: String(this.document?.['aas_adjustment_party'] ?? '').trim(),
      categoryId: String(this.document?.['aas_category'] ?? '').trim()
    };
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
        existing.totalAmount += Number(item.amount ?? 0) || 0;
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
        totalAmount: Number(item.amount ?? 0) || 0,
        items: [item]
      });
    }
    return Array.from(grouped.values());
  }

  trackByDocumentId(_index: number, item: BillReviewListItem): string {
    return `${item.itemType}:${item.documentId}`;
  }

  itemTypeLabel(itemType: BillReviewItemType): string {
    const normalized = String(itemType ?? '').toUpperCase();
    if (normalized === 'DEBIT_NOTE') {
      return 'Debit note';
    }
    if (normalized === 'CREDIT_NOTE') {
      return 'Credit note';
    }
    return 'Payment';
  }

  private loadQueue(resetSelection: boolean): void {
    this.isLoading = true;
    this.errorMessage = '';
    this.billReviewService.listItems(this.selectedStatus, this.selectedPartyType)
      .pipe(finalize(() => (this.isLoading = false)))
      .subscribe({
        next: items => {
          this.items = items ?? [];
          if (!resetSelection && this.selectedDocumentId) {
            const stillExists = this.items.some(item => item.documentId === this.selectedDocumentId && item.itemType === this.selectedItemType);
            if (stillExists) {
              return;
            }
          }
          const nextSelected = this.items[0];
          if (nextSelected) {
            this.selectItem(nextSelected);
          } else {
            this.clearSelection();
          }
        },
        error: err => {
          this.items = [];
          this.clearSelection();
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

  private clearSelection(): void {
    this.selectedItem = null;
    this.selectedDocumentId = '';
    this.selectedItemType = 'PAYMENT';
    this.notes = '';
  }

  private approvalMessage(detail: BillReviewDetail | null): string {
    const label = this.itemTypeLabel(detail?.itemType || this.selectedItemType);
    const warnings = detail?.warnings as Array<{ code?: string; recordedDue?: any; currentDue?: any }> | undefined;
    const dueChanged = Array.isArray(warnings) && warnings.some(w => String(w?.code ?? '').toUpperCase() === 'DUE_CHANGED');
    if (dueChanged) {
      const first = warnings?.find(w => String(w?.code ?? '').toUpperCase() === 'DUE_CHANGED');
      const recorded = Number(first?.recordedDue ?? detail?.document?.['aas_due_amount'] ?? 0);
      const current = Number(first?.currentDue ?? detail?.currentDueAmount ?? 0);
      return `${label} approved (due changed: recorded ₹${recorded.toFixed(2)}, current ₹${current.toFixed(2)}).`;
    }
    return label === 'Payment' ? 'Payment approved and submitted.' : `${label} approved and posted.`;
  }

  private existingNotes(detail: BillReviewDetail | null): string {
    const document = detail?.document ?? detail?.payment ?? {};
    const type = String(detail?.itemType ?? '').toUpperCase();
    if (type === 'PAYMENT') {
      return String(document?.['aas_payment_review_notes'] ?? '').trim();
    }
    return String(document?.['aas_adjustment_review_notes'] ?? '').trim();
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
