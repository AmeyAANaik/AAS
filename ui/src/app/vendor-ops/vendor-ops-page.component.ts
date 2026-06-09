import { Component, OnInit } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize } from 'rxjs/operators';
import { VendorOpsCategorySummaryRow, VendorOpsDetail, VendorOpsLedgerEntry, VendorOpsOrderRow, VendorOpsSummaryRow, VendorOpsSummaryTotals } from './vendor-ops.model';
import { VendorOpsService } from './vendor-ops.service';

@Component({
  selector: 'app-vendor-ops-page',
  templateUrl: './vendor-ops-page.component.html',
  styleUrl: './vendor-ops-page.component.scss'
})
export class VendorOpsPageComponent implements OnInit {
  private readonly hiddenCategoryLabels = new Set(['all item groups']);
  private readonly settledThreshold = 0.01;
  private readonly defaultLedgerDays = 7;
  readonly searchControl = new FormControl('', { nonNullable: true });
  readonly ledgerDateRangeForm = new FormGroup({
    from: new FormControl<Date | null>(null),
    to: new FormControl<Date | null>(null)
  });
  readonly summaryColumns = ['vendor', 'pendingOrders', 'inProgress', 'pendingBillAmount', 'lastActivity', 'templateStatus', 'ledgerBalance', 'actions'];
  readonly orderColumns = ['orderId', 'branch', 'status', 'orderDate', 'parsedItems', 'vendorBillTotal', 'billRef', 'poNumber', 'actions'];
  readonly ledgerColumns = ['date', 'voucherType', 'voucherNo', 'reference', 'debit', 'credit', 'netChange', 'runningBalance'];

  totals: VendorOpsSummaryTotals = {
    totalVendors: 0,
    vendorsWithPendingOrders: 0,
    totalPendingOrders: 0,
    awaitingPdf: 0,
    awaitingBillCapture: 0,
    totalPendingBillAmount: 0
  };
  vendors: VendorOpsSummaryRow[] = [];
  selectedVendor: VendorOpsDetail | null = null;
  selectedVendorOrders: VendorOpsOrderRow[] = [];
  ledgerOpeningBalance = 0;
  ledgerClosingBalance = 0;
  ledgerCategorySummary: VendorOpsCategorySummaryRow[] = [];
  selectedCategoryId = '';
  categoryLedger: VendorOpsLedgerEntry[] = [];
  categoryLedgerBalance = 0;
  private forceSelectTopCategory = false;
  private appliedLedgerRange: { from?: string; to?: string } = {};
  isLoadingCategoryLedger = false;
  isLoadingSummary = false;
  isLoadingDetail = false;
  errorMessage = '';

  constructor(
    private vendorOpsService: VendorOpsService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadSummary();
    this.ensureDefaultLedgerRange();
    this.route.paramMap.subscribe(params => {
      const vendorId = params.get('vendorId');
      if (vendorId) {
        this.loadVendor(vendorId);
      } else {
        this.selectedVendor = null;
        this.selectedVendorOrders = [];
        this.ledgerCategorySummary = [];
        this.clearCategoryLedger();
      }
    });
  }

  loadSummary(): void {
    this.isLoadingSummary = true;
    this.vendorOpsService.getSummary()
      .pipe(finalize(() => (this.isLoadingSummary = false)))
      .subscribe({
        next: response => {
          this.totals = response.totals;
          this.vendors = response.vendors ?? [];
        },
        error: () => this.errorMessage = 'Unable to load vendor operations summary.'
      });
  }

  viewVendor(vendorId: string): void {
    this.router.navigate(['/vendor-ops', vendorId]);
  }

  openOrder(orderId: string): void {
    this.router.navigate(['/orders'], { queryParams: { orderId } });
  }

  downloadAllLedgers(): void {
    this.vendorOpsService.downloadAllVendorLedgers().subscribe({
      next: blob => this.saveBlob(blob, 'vendor-ledger-all.csv'),
      error: () => {
        this.errorMessage = 'Unable to download all vendor ledgers.';
      }
    });
  }

  downloadAllCategoryLedgers(): void {
    this.vendorOpsService.downloadAllVendorsLedgerCategoriesSummary(this.appliedLedgerRange).subscribe({
      next: blob => this.saveBlob(blob, 'vendor-ledger-categories-all.csv'),
      error: () => {
        this.errorMessage = 'Unable to download vendor ledgers by category.';
      }
    });
  }

  downloadCategorySummary(): void {
    const vendorId = this.selectedVendor?.vendor?.vendorId;
    if (!vendorId) {
      return;
    }
    this.vendorOpsService.downloadVendorLedgerCategoriesSummary(vendorId, this.appliedLedgerRange).subscribe({
      next: blob => this.saveBlob(blob, `vendor-ledger-categories-${this.toFileSegment(vendorId)}.csv`),
      error: () => {
        this.errorMessage = 'Unable to download category ledger summary.';
      }
    });
  }

  downloadCategoryLedger(): void {
    const vendorId = this.selectedVendor?.vendor?.vendorId;
    const categoryId = this.selectedCategoryId;
    if (!vendorId || !categoryId) {
      return;
    }
    this.vendorOpsService.downloadVendorLedgerByCategory(vendorId, categoryId, this.appliedLedgerRange).subscribe({
      next: blob => this.saveBlob(blob, `vendor-ledger-${this.toFileSegment(vendorId)}-${this.toFileSegment(categoryId)}.csv`),
      error: () => {
        this.errorMessage = 'Unable to download category ledger.';
      }
    });
  }

  viewCategoryLedger(categoryId: string): void {
    const vendorId = this.selectedVendor?.vendor?.vendorId;
    const id = (categoryId || '').trim();
    if (!vendorId || !id || this.isLoadingCategoryLedger) {
      return;
    }
    this.selectedCategoryId = id;
    this.isLoadingCategoryLedger = true;
    this.vendorOpsService.getVendorLedgerByCategory(vendorId, id, this.appliedLedgerRange)
      .pipe(finalize(() => (this.isLoadingCategoryLedger = false)))
      .subscribe({
        next: response => {
          this.categoryLedger = response.entries ?? [];
          this.categoryLedgerBalance = Number(response.balance ?? 0) || 0;
        },
        error: () => {
          this.categoryLedger = [];
          this.categoryLedgerBalance = 0;
          this.errorMessage = 'Unable to load category ledger.';
        }
      });
  }

  clearCategoryLedger(): void {
    this.selectedCategoryId = '';
    this.categoryLedger = [];
    this.categoryLedgerBalance = 0;
  }

  applyLedgerDateRange(): void {
    const fromValue = this.ledgerDateRangeForm.get('from')?.value ?? null;
    const toValue = this.ledgerDateRangeForm.get('to')?.value ?? null;
    const nextRange: { from?: string; to?: string } = {
      from: this.toIsoDate(fromValue),
      to: this.toIsoDate(toValue)
    };
    if (!nextRange.from) {
      delete nextRange.from;
    }
    if (!nextRange.to) {
      delete nextRange.to;
    }
    this.appliedLedgerRange = nextRange;
    this.forceSelectTopCategory = true;
    this.reloadLedgers();
  }

  clearLedgerDateRange(): void {
    this.applyDefaultLedgerRange();
    this.forceSelectTopCategory = true;
    this.reloadLedgers();
  }

  get ledgerDelta(): number {
    return (Number(this.ledgerClosingBalance) || 0) - (Number(this.ledgerOpeningBalance) || 0);
  }

  private reloadLedgers(): void {
    const vendorId = this.selectedVendor?.vendor?.vendorId;
    if (!vendorId) {
      return;
    }
    this.vendorOpsService.getVendorLedger(vendorId, this.appliedLedgerRange).subscribe({
      next: response => {
        this.ledgerOpeningBalance = Number(response.openingBalance ?? 0) || 0;
        this.ledgerClosingBalance = Number(response.closingBalance ?? response.balance ?? 0) || 0;
        this.ledgerCategorySummary = (response.categorySummary ?? []).filter(row => {
          const category = String(row?.category ?? '').trim();
          if (!category) {
            return false;
          }
          if (this.hiddenCategoryLabels.has(category.toLowerCase())) {
            return false;
          }
          return true;
        });

        if (!this.ledgerCategorySummary.length) {
          this.clearCategoryLedger();
          this.ledgerOpeningBalance = 0;
          this.ledgerClosingBalance = 0;
          return;
        }

        const categoryIds = new Set(this.ledgerCategorySummary.map(row => row.category));
        const topCategory = [...this.ledgerCategorySummary]
          .sort((a, b) => (Number(b.amount) || 0) - (Number(a.amount) || 0))[0]?.category;

        const shouldSelectTop = this.forceSelectTopCategory || !this.selectedCategoryId || !categoryIds.has(this.selectedCategoryId);
        this.forceSelectTopCategory = false;

        if (shouldSelectTop && topCategory) {
          this.viewCategoryLedger(topCategory);
        } else if (this.selectedCategoryId) {
          this.viewCategoryLedger(this.selectedCategoryId);
        }
      },
      error: () => {
        this.ledgerOpeningBalance = 0;
        this.ledgerClosingBalance = 0;
        this.ledgerCategorySummary = [];
        this.clearCategoryLedger();
      }
    });
  }

  amountTone(value: unknown): 'pos' | 'neg' | 'zero' {
    const num = Number(value ?? 0);
    if (!Number.isFinite(num) || Math.abs(num) < 0.0001) {
      return 'zero';
    }
    return num > 0 ? 'pos' : 'neg';
  }

  get filteredVendors(): VendorOpsSummaryRow[] {
    const term = this.searchControl.value.trim().toLowerCase();
    if (!term) {
      return this.vendors;
    }
    return this.vendors.filter(vendor =>
      vendor.vendorName.toLowerCase().includes(term) ||
      vendor.vendorId.toLowerCase().includes(term)
    );
  }

  get selectedVendorSettlementState(): 'settled' | 'open' | 'overdue' {
    const detail = this.selectedVendor;
    if (!detail) {
      return 'open';
    }
    if ((detail.billing?.unpaidPurchaseInvoices ?? 0) > 0 || (detail.exceptions?.awaitingPdfTooLong ?? 0) > 0) {
      return 'overdue';
    }
    const balance = Math.abs(detail.billing?.ledgerBalance ?? 0);
    if (balance <= this.settledThreshold) {
      return 'settled';
    }
    return 'open';
  }

  get selectedVendorSettlementLabel(): string {
    return this.toSettlementLabel(this.selectedVendorSettlementState);
  }

  get ledgerScopeNote(): string {
    if (this.selectedCategoryId) {
      return `Overall vendor balances for the selected date range. Category drill-down: ${this.selectedCategoryId}.`;
    }
    return 'Overall vendor balances for the selected date range.';
  }

  vendorSummaryState(vendor: VendorOpsSummaryRow): 'settled' | 'open' | 'overdue' {
    const balance = Math.abs(vendor.ledgerBalance ?? 0);
    const pending = Math.abs(vendor.pendingBillAmount ?? 0);
    if (balance <= this.settledThreshold && pending <= this.settledThreshold) {
      return 'settled';
    }
    return 'open';
  }

  vendorSummaryLabel(vendor: VendorOpsSummaryRow): string {
    return this.toSettlementLabel(this.vendorSummaryState(vendor));
  }

  isSelectedVendor(vendorId: string): boolean {
    return this.selectedVendor?.vendor?.vendorId === vendorId;
  }

  balanceState(balance: number, state: 'settled' | 'open' | 'overdue'): 'settled' | 'open' | 'overdue' {
    if (state === 'overdue') {
      return 'overdue';
    }
    return Math.abs(balance ?? 0) <= this.settledThreshold ? 'settled' : state;
  }

  displayAmount(value: unknown): number {
    const amount = Number(value ?? 0);
    if (!Number.isFinite(amount) || Math.abs(amount) <= this.settledThreshold) {
      return 0;
    }
    return amount;
  }

  private loadVendor(vendorId: string): void {
    this.isLoadingDetail = true;
    this.errorMessage = '';
    this.ensureDefaultLedgerRange();
    this.vendorOpsService.getVendorDetail(vendorId).subscribe({
      next: detail => {
        this.selectedVendor = detail;
      },
      error: () => {
        this.errorMessage = 'Unable to load vendor details.';
        this.selectedVendor = null;
      }
      });
    this.vendorOpsService.getVendorOrders(vendorId)
      .pipe(finalize(() => (this.isLoadingDetail = false)))
      .subscribe({
        next: orders => {
          this.selectedVendorOrders = orders ?? [];
        },
        error: () => {
          this.errorMessage = 'Unable to load vendor orders.';
          this.selectedVendorOrders = [];
        }
      });
    this.clearCategoryLedger();
    this.forceSelectTopCategory = true;
    this.reloadLedgers();
  }

  private ensureDefaultLedgerRange(): void {
    if (this.appliedLedgerRange.from || this.appliedLedgerRange.to) {
      return;
    }
    this.applyDefaultLedgerRange();
  }

  private applyDefaultLedgerRange(): void {
    const today = new Date();
    const from = new Date(today);
    from.setDate(from.getDate() - this.defaultLedgerDays);
    this.ledgerDateRangeForm.setValue({ from, to: today });
    this.appliedLedgerRange = {
      from: this.toIsoDate(from),
      to: this.toIsoDate(today)
    };
  }

  private saveBlob(blob: Blob, fileName: string): void {
    const url = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = fileName;
    link.click();
    window.URL.revokeObjectURL(url);
  }

  private toFileSegment(value: string): string {
    return value.trim().replace(/[^a-zA-Z0-9._-]+/g, '_') || 'unknown';
  }

  private toIsoDate(date: Date | null): string | undefined {
    if (!date) {
      return undefined;
    }
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  private toSettlementLabel(state: 'settled' | 'open' | 'overdue'): string {
    if (state === 'settled') {
      return 'Settled';
    }
    if (state === 'overdue') {
      return 'Overdue';
    }
    return 'Open';
  }
}
