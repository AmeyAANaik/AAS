import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { finalize, Subscription } from 'rxjs';
import { CategoryService } from '../categories/category.service';
import { PageHeaderComponent } from '../shared/page-header/page-header.component';
import { formatUiError } from '../shared/error-message.util';
import { UomService } from '../shared/uom.service';
import {
  ApproveMasterDataReviewRequest,
  MasterDataReviewDetail,
  MasterDataReviewListItem,
  MasterDataReviewSummary
} from './master-data-review.model';
import { MasterDataReviewService } from './master-data-review.service';

@Component({
  selector: 'app-master-data-review-page',
  standalone: true,
  imports: [CommonModule, FormsModule, MatButtonModule, MatCardModule, MatIconModule, PageHeaderComponent],
  templateUrl: './master-data-review-page.component.html',
  styleUrl: './master-data-review-page.component.scss'
})
export class MasterDataReviewPageComponent implements OnInit, OnDestroy {
  readonly statusFilters = [
    { label: 'Pending', value: 'PENDING_REVIEW' },
    { label: 'Approved', value: 'APPROVED' },
    { label: 'Merged', value: 'MERGED' },
    { label: 'Rejected', value: 'REJECTED' }
  ];

  items: MasterDataReviewListItem[] = [];
  filteredItems: MasterDataReviewListItem[] = [];
  selectedItem: MasterDataReviewDetail | null = null;
  summary: MasterDataReviewSummary = { pendingCount: 0, approvedCount: 0, defaultMarginCount: 0, totalCount: 0 };
  categories: string[] = [];
  uoms: string[] = [];
  selectedStatus = 'PENDING_REVIEW';
  searchTerm = '';
  isLoading = false;
  isDetailLoading = false;
  isSaving = false;
  statusMessage = '';
  errorMessage = '';
  readonly formModel: ApproveMasterDataReviewRequest = {
    item_name: '',
    item_group: '',
    stock_uom: '',
    aas_packaging_unit: '',
    aas_margin_percent: null,
    aas_vendor_hsn_code: '',
    aas_gst_percent: null,
    reviewNotes: '',
    applyToSourceOrder: false
  };
  readonly priorityFilter = 'PENDING_REVIEW';

  private readonly subscriptions = new Subscription();

  constructor(
    private readonly reviewService: MasterDataReviewService,
    private readonly categoryService: CategoryService,
    private readonly uomService: UomService
  ) {}

  ngOnInit(): void {
    this.loadCategories();
    this.loadUoms();
    this.loadQueue();
    this.subscriptions.add(this.reviewService.refresh$.subscribe(() => this.loadQueue()));
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

  loadQueue(): void {
    this.isLoading = true;
    this.errorMessage = '';
    this.reviewService.listItems()
      .pipe(finalize(() => (this.isLoading = false)))
      .subscribe({
        next: items => {
          this.items = items ?? [];
          this.summary = this.buildSummary(this.items);
          this.refreshFilteredItems(false);
          const selectedId = this.selectedItem?.id ?? '';
          const nextSelected = this.filteredItems.find(item => item.id === selectedId)
            ?? this.filteredItems[0]
            ?? null;
          if (nextSelected) {
            this.selectItem(nextSelected.id);
          } else {
            this.selectedItem = null;
            this.resetForm();
          }
        },
        error: err => {
          this.errorMessage = formatUiError(err, 'Unable to load master data review queue.');
        }
      });
  }

  applyStatusFilter(status: string, reloadSelection = true): void {
    this.selectedStatus = status;
    this.refreshFilteredItems(reloadSelection);
  }

  applySearch(value: string): void {
    this.searchTerm = value;
    this.refreshFilteredItems(true);
  }

  selectItem(itemId: string): void {
    if (!itemId) {
      return;
    }
    this.isDetailLoading = true;
    this.reviewService.getDetail(itemId)
      .pipe(finalize(() => (this.isDetailLoading = false)))
      .subscribe({
        next: detail => {
          this.selectedItem = detail;
          this.patchForm(detail);
        },
        error: err => {
          this.errorMessage = formatUiError(err, 'Unable to load review details.');
        }
      });
  }

  approve(): void {
    if (!this.selectedItem || this.isSaving) {
      return;
    }
    this.isSaving = true;
    this.statusMessage = '';
    this.errorMessage = '';
    this.reviewService.approve(this.selectedItem.id, {
      ...this.formModel,
      item_name: this.formModel.item_name.trim(),
      item_group: this.formModel.item_group.trim(),
      stock_uom: this.formModel.stock_uom.trim(),
      aas_packaging_unit: this.formModel.aas_packaging_unit.trim(),
      aas_vendor_hsn_code: this.formModel.aas_vendor_hsn_code.trim(),
      reviewNotes: this.formModel.reviewNotes.trim()
    })
      .pipe(finalize(() => (this.isSaving = false)))
      .subscribe({
        next: response => {
          this.statusMessage = response?.detail?.sourceOrderId && this.formModel.applyToSourceOrder
            ? 'Item approved and source order margin updated when eligible.'
            : 'Item approved.';
          this.loadQueue();
        },
        error: err => {
          this.errorMessage = formatUiError(err, 'Unable to approve review item.');
        }
      });
  }

  get notificationMessage(): string {
    if (this.summary.pendingCount <= 0) {
      return '';
    }
    if (this.summary.defaultMarginCount > 0) {
      return `${this.summary.pendingCount} item${this.summary.pendingCount === 1 ? '' : 's'} need review. ${this.summary.defaultMarginCount} still use default margin.`;
    }
    return `${this.summary.pendingCount} item${this.summary.pendingCount === 1 ? '' : 's'} are waiting for admin approval.`;
  }

  get hasActiveSearch(): boolean {
    return this.searchTerm.trim().length > 0;
  }

  private loadCategories(): void {
    this.categoryService.listCategories().subscribe({
      next: categories => {
        this.categories = (categories ?? [])
          .map(category => String(category.name ?? category.item_group_name ?? '').trim())
          .filter(Boolean)
          .sort((left, right) => left.localeCompare(right));
      },
      error: () => {
        this.categories = [];
      }
    });
  }

  private loadUoms(): void {
    this.uomService.listUoms().subscribe({
      next: rows => {
        this.uoms = (rows ?? [])
          .map(row => String(row.uom_name ?? row.name ?? '').trim())
          .filter(Boolean)
          .sort((left, right) => left.localeCompare(right));
      },
      error: () => {
        this.uoms = [];
      }
    });
  }

  private patchForm(detail: MasterDataReviewDetail): void {
    this.formModel.item_name = detail.itemName ?? '';
    this.formModel.item_group = detail.category ?? '';
    this.formModel.stock_uom = detail.uom ?? '';
    this.formModel.aas_packaging_unit = detail.packagingUnit ?? '';
    this.formModel.aas_margin_percent = Number.isFinite(detail.marginPercent) ? detail.marginPercent : null;
    this.formModel.aas_vendor_hsn_code = detail.vendorHsnCode ?? '';
    this.formModel.aas_gst_percent = Number.isFinite(detail.gstPercent) ? detail.gstPercent : null;
    this.formModel.reviewNotes = detail.reviewNotes ?? '';
    this.formModel.applyToSourceOrder = false;
  }

  private resetForm(): void {
    this.formModel.item_name = '';
    this.formModel.item_group = '';
    this.formModel.stock_uom = '';
    this.formModel.aas_packaging_unit = '';
    this.formModel.aas_margin_percent = null;
    this.formModel.aas_vendor_hsn_code = '';
    this.formModel.aas_gst_percent = null;
    this.formModel.reviewNotes = '';
    this.formModel.applyToSourceOrder = false;
  }

  private refreshFilteredItems(reloadSelection: boolean): void {
    const query = this.normalizeSearch(this.searchTerm);
    this.filteredItems = this.items.filter(item => {
      if (item.reviewStatus !== this.selectedStatus) {
        return false;
      }
      if (!query) {
        return true;
      }
      const haystack = this.normalizeSearch([
        item.itemName,
        item.itemCode,
        item.category,
        item.sourceOrderId,
        item.sourceInvoiceRef,
        item.vendorHsnCode,
        item.createdBy
      ].join(' '));
      return haystack.includes(query);
    });

    if (!reloadSelection) {
      return;
    }
    const first = this.filteredItems[0];
    if (first) {
      this.selectItem(first.id);
    } else {
      this.selectedItem = null;
      this.resetForm();
    }
  }

  private normalizeSearch(value: string): string {
    return String(value ?? '').trim().toLowerCase();
  }

  private buildSummary(items: MasterDataReviewListItem[]): MasterDataReviewSummary {
    return {
      pendingCount: items.filter(item => item.reviewStatus === 'PENDING_REVIEW').length,
      approvedCount: items.filter(item => item.reviewStatus === 'APPROVED').length,
      defaultMarginCount: items.filter(item => item.defaultMarginUsed).length,
      totalCount: items.length
    };
  }
}
