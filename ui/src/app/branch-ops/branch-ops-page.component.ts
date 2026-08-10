import { Component, OnInit } from '@angular/core';
import { FormControl, FormGroup } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize } from 'rxjs/operators';
import { formatUiError } from '../shared/error-message.util';
import { BranchOpsAgingBucketKey, BranchOpsAgingDetail, BranchOpsAgingRow, BranchOpsAgingSummary, BranchOpsCategorySummaryRow, BranchOpsDetail, BranchOpsLedgerEntry, BranchOpsOrderRow, BranchOpsRiskTier, BranchOpsSummaryRow, BranchOpsSummaryTotals } from './branch-ops.model';
import { BranchOpsAgingQuery, BranchOpsService, ExportFormat } from './branch-ops.service';

@Component({
  selector: 'app-branch-ops-page',
  templateUrl: './branch-ops-page.component.html',
  styleUrl: './branch-ops-page.component.scss'
})
export class BranchOpsPageComponent implements OnInit {
  private readonly hiddenCategoryLabels = new Set(['all item groups']);
  private readonly settledThreshold = 0.01;
  private readonly defaultLedgerDays = 7;
  readonly searchControl = new FormControl('', { nonNullable: true });
  readonly ledgerDateRangeForm = new FormGroup({
    from: new FormControl<Date | null>(null),
    to: new FormControl<Date | null>(null)
  });
  readonly summaryColumns = ['branch', 'pendingOrders', 'awaitingVendorAssignment', 'awaitingVendorResponse', 'inProgress', 'openReceivableAmount', 'lastActivity', 'location', 'ledgerBalance', 'actions'];
  readonly orderColumns = ['orderId', 'vendor', 'status', 'orderDate', 'parsedItems', 'vendorBillTotal', 'sellOrderTotal', 'invoiceId', 'actions'];
  readonly ledgerColumns = ['date', 'voucherType', 'voucherNo', 'reference', 'debit', 'credit', 'netChange', 'runningBalance'];
  readonly agingColumns = [
    'branch', 'notDue', 'd1_7', 'd8_15', 'd16_30', 'd30Plus',
    'overdueAmount', 'draftUnbilledAmount', 'ledgerBalance', 'oldestOverdueDays', 'onTimePaymentPct', 'riskTier'
  ];
  readonly agingInvoiceColumns = [
    'invoiceId', 'postingDate', 'dueDate', 'stage', 'status',
    'invoiceAmount', 'outstandingAmount', 'daysPastDue', 'bucketLabel'
  ];
  readonly agingAsOfControl = new FormControl<Date | null>(new Date());

  activeTabIndex = 0;
  aging: BranchOpsAgingSummary | null = null;
  branchAging: BranchOpsAgingDetail | null = null;
  isLoadingAging = false;
  agingErrorMessage = '';
  private agingLoaded = false;
  private appliedAsOf: string | undefined;

  totals: BranchOpsSummaryTotals = {
    totalBranches: 0,
    branchesWithPendingOrders: 0,
    totalPendingOrders: 0,
    awaitingVendorAssignment: 0,
    awaitingVendorResponse: 0,
    openReceivableAmount: 0
  };
  branches: BranchOpsSummaryRow[] = [];
  selectedBranch: BranchOpsDetail | null = null;
  selectedBranchOrders: BranchOpsOrderRow[] = [];
  ledger: BranchOpsLedgerEntry[] = [];
  ledgerOpeningBalance = 0;
  ledgerClosingBalance = 0;
  ledgerCategorySummary: BranchOpsCategorySummaryRow[] = [];
  selectedCategoryId = '';
  categoryLedger: BranchOpsLedgerEntry[] = [];
  categoryLedgerBalance = 0;
  private forceSelectTopCategory = false;
  private appliedLedgerRange: { from?: string; to?: string } = {};
  isLoadingCategoryLedger = false;
  isLoadingSummary = false;
  isLoadingDetail = false;
  errorMessage = '';

  constructor(
    private branchOpsService: BranchOpsService,
    private route: ActivatedRoute,
    private router: Router
  ) {}

  ngOnInit(): void {
    this.loadSummary();
    this.ensureDefaultLedgerRange();
    this.route.queryParamMap.subscribe(params => {
      const wantsAging = params.get('tab') === 'aging';
      this.activeTabIndex = wantsAging ? 1 : 0;
      if (wantsAging) {
        this.ensureAgingLoaded();
      }
    });
    this.route.paramMap.subscribe(params => {
      const branchId = params.get('branchId');
      if (branchId) {
        this.loadBranch(branchId);
      } else {
        this.selectedBranch = null;
        this.selectedBranchOrders = [];
        this.ledger = [];
        this.ledgerCategorySummary = [];
        this.clearCategoryLedger();
      }
    });
  }

  loadSummary(): void {
    this.isLoadingSummary = true;
    this.branchOpsService.getSummary()
      .pipe(finalize(() => (this.isLoadingSummary = false)))
      .subscribe({
        next: response => {
          this.totals = response.totals;
          this.branches = response.branches ?? [];
        },
        error: () => this.errorMessage = 'Unable to load branch operations summary.'
      });
  }

  viewBranch(branchId: string): void {
    this.router.navigate(['/branch-ops', branchId], { queryParamsHandling: 'merge' });
  }

  onTabChange(index: number): void {
    this.activeTabIndex = index;
    this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { tab: index === 1 ? 'aging' : null },
      queryParamsHandling: 'merge',
      replaceUrl: true
    });
    if (index === 1) {
      this.ensureAgingLoaded();
    }
  }

  loadAging(): void {
    this.isLoadingAging = true;
    this.agingErrorMessage = '';
    this.branchOpsService.getAgingSummary(this.agingQuery)
      .pipe(finalize(() => (this.isLoadingAging = false)))
      .subscribe({
        next: response => {
          this.aging = response;
          this.agingLoaded = true;
        },
        error: err => {
          this.aging = null;
          this.agingErrorMessage = formatUiError(err, 'Unable to load the receivables aging report.');
        }
      });
    const branchId = this.selectedBranch?.branch?.branchId;
    if (branchId) {
      this.loadBranchAging(branchId);
    }
  }

  loadBranchAging(branchId: string): void {
    this.branchOpsService.getBranchAging(branchId, this.agingQuery).subscribe({
      next: detail => (this.branchAging = detail),
      error: err => {
        this.branchAging = null;
        this.agingErrorMessage = formatUiError(err, 'Unable to load branch aging detail.');
      }
    });
  }

  applyAgingAsOf(): void {
    this.appliedAsOf = this.toIsoDate(this.agingAsOfControl.value ?? null);
    this.loadAging();
  }

  resetAgingAsOf(): void {
    this.agingAsOfControl.setValue(new Date());
    this.appliedAsOf = undefined;
    this.loadAging();
  }

  downloadAging(format: ExportFormat = 'csv'): void {
    this.branchOpsService.downloadAgingSummary(this.agingQuery, format).subscribe({
      next: blob => this.saveBlob(blob, `branch-aging-all.${format}`),
      error: err => {
        this.agingErrorMessage = formatUiError(err, 'Unable to download the aging report.');
      }
    });
  }

  downloadBranchAging(format: ExportFormat = 'csv'): void {
    const branchId = this.branchAging?.branch?.branchId ?? this.selectedBranch?.branch?.branchId;
    if (!branchId) {
      return;
    }
    this.branchOpsService.downloadBranchAging(branchId, this.agingQuery, format).subscribe({
      next: blob => this.saveBlob(blob, `branch-aging-${this.toFileSegment(branchId)}.${format}`),
      error: err => {
        this.agingErrorMessage = formatUiError(err, 'Unable to download branch aging detail.');
      }
    });
  }

  /** Worst collections risk first — the branches chasing money is most urgent for. */
  get collectionsRisk(): BranchOpsAgingRow[] {
    return this.orderByRanking(this.aging?.collectionsRanking);
  }

  /** Largest unbilled backlog first — an internal billing gap, not a branch payment failure. */
  get billingBacklog(): BranchOpsAgingRow[] {
    return this.orderByRanking(this.aging?.backlogRanking);
  }

  bucketTone(key: BranchOpsAgingBucketKey): 'neutral' | 'info' | 'warn' | 'warn-strong' | 'danger' {
    switch (key) {
      case 'notDue': return 'neutral';
      case 'd1_7': return 'info';
      case 'd8_15': return 'warn';
      case 'd16_30': return 'warn-strong';
      default: return 'danger';
    }
  }

  riskTone(tier: BranchOpsRiskTier): 'success' | 'warning' | 'danger' {
    if (tier === 'DEFAULTER') {
      return 'danger';
    }
    return tier === 'WATCH' ? 'warning' : 'success';
  }

  riskLabel(tier: BranchOpsRiskTier): string {
    if (tier === 'DEFAULTER') {
      return 'Defaulter';
    }
    return tier === 'WATCH' ? 'Watch' : 'Good';
  }

  /** An asterisk marks a percentage computed from too few referenced payments to trust. */
  onTimeDisplay(row: BranchOpsAgingRow): string {
    if (row.onTimePaymentPct === null || row.onTimePaymentPct === undefined) {
      return '—';
    }
    const value = `${Math.round(row.onTimePaymentPct)}%`;
    return row.onTimeReliable ? value : `${value} *`;
  }

  private ensureAgingLoaded(): void {
    if (this.agingLoaded || this.isLoadingAging) {
      return;
    }
    this.loadAging();
  }

  private orderByRanking(ranking: string[] | undefined): BranchOpsAgingRow[] {
    const rows = this.aging?.branches ?? [];
    if (!ranking?.length) {
      return rows;
    }
    const byId = new Map(rows.map(row => [row.branchId, row]));
    return ranking.map(id => byId.get(id)).filter((row): row is BranchOpsAgingRow => !!row);
  }

  private get agingQuery(): BranchOpsAgingQuery {
    return this.appliedAsOf ? { asOf: this.appliedAsOf } : {};
  }

  openOrder(orderId: string): void {
    this.router.navigate(['/orders'], { queryParams: { orderId } });
  }

  downloadAllLedgers(format: ExportFormat = 'csv'): void {
    this.branchOpsService.downloadAllBranchLedgers(format).subscribe({
      next: blob => this.saveBlob(blob, `branch-ledger-all.${format}`),
      error: () => {
        this.errorMessage = 'Unable to download all branch ledgers.';
      }
    });
  }

  downloadAllCategoryLedgers(format: ExportFormat = 'csv'): void {
    this.branchOpsService.downloadAllBranchesLedgerCategoriesSummary(this.appliedLedgerRange, format).subscribe({
      next: blob => this.saveBlob(blob, `branch-ledger-categories-all.${format}`),
      error: () => {
        this.errorMessage = 'Unable to download branch ledgers by category.';
      }
    });
  }

  downloadLedger(format: ExportFormat = 'csv'): void {
    const branchId = this.selectedBranch?.branch?.branchId;
    if (!branchId) { return; }
    this.branchOpsService.downloadBranchLedger(branchId, this.appliedLedgerRange, format).subscribe({
      next: blob => this.saveBlob(blob, `branch-ledger-${this.toFileSegment(branchId)}.${format}`),
      error: () => { this.errorMessage = 'Unable to download branch ledger.'; }
    });
  }

  downloadCategorySummary(format: ExportFormat = 'csv'): void {
    const branchId = this.selectedBranch?.branch?.branchId;
    if (!branchId) {
      return;
    }
    this.branchOpsService.downloadBranchLedgerCategoriesSummary(branchId, this.appliedLedgerRange, format).subscribe({
      next: blob => this.saveBlob(blob, `branch-ledger-categories-${this.toFileSegment(branchId)}.${format}`),
      error: () => {
        this.errorMessage = 'Unable to download category ledger summary.';
      }
    });
  }

  downloadCategoryLedger(format: ExportFormat = 'csv'): void {
    const branchId = this.selectedBranch?.branch?.branchId;
    const categoryId = this.selectedCategoryId;
    if (!branchId || !categoryId) {
      return;
    }
    this.branchOpsService.downloadBranchLedgerByCategory(branchId, categoryId, this.appliedLedgerRange, format).subscribe({
      next: blob => this.saveBlob(blob, `branch-ledger-${this.toFileSegment(branchId)}-${this.toFileSegment(categoryId)}.${format}`),
      error: () => {
        this.errorMessage = 'Unable to download category ledger.';
      }
    });
  }

  viewCategoryLedger(categoryId: string): void {
    const branchId = this.selectedBranch?.branch?.branchId;
    const id = (categoryId || '').trim();
    if (!branchId || !id || this.isLoadingCategoryLedger) {
      return;
    }
    this.selectedCategoryId = id;
    this.isLoadingCategoryLedger = true;
    this.branchOpsService.getBranchLedgerByCategory(branchId, id, this.appliedLedgerRange)
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
    const branchId = this.selectedBranch?.branch?.branchId;
    if (!branchId) {
      return;
    }
    this.branchOpsService.getBranchLedger(branchId, this.appliedLedgerRange).subscribe({
      next: response => {
        this.ledger = response.entries ?? [];
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
        this.ledger = [];
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

  get filteredBranches(): BranchOpsSummaryRow[] {
    const term = this.searchControl.value.trim().toLowerCase();
    if (!term) {
      return this.branches;
    }
    return this.branches.filter(branch =>
      branch.branchName.toLowerCase().includes(term) ||
      branch.branchId.toLowerCase().includes(term)
    );
  }

  get selectedBranchSettlementState(): 'settled' | 'open' | 'overdue' {
    const detail = this.selectedBranch;
    if (!detail) {
      return 'open';
    }
    if ((detail.exceptions?.overdueInvoices ?? 0) > 0) {
      return 'overdue';
    }
    const balance = Math.abs(detail.billing?.ledgerBalance ?? 0);
    if (balance <= this.settledThreshold && (detail.billing?.openInvoices ?? 0) === 0) {
      return 'settled';
    }
    return 'open';
  }

  get selectedBranchSettlementLabel(): string {
    return this.toSettlementLabel(this.selectedBranchSettlementState);
  }

  get ledgerScopeNote(): string {
    if (this.selectedCategoryId) {
      return `Overall branch balances for the selected date range. Category drill-down: ${this.selectedCategoryId}.`;
    }
    return 'Overall branch balances for the selected date range.';
  }

  branchSummaryState(branch: BranchOpsSummaryRow): 'settled' | 'open' | 'overdue' {
    const balance = Math.abs(branch.ledgerBalance ?? 0);
    const receivable = Math.abs(branch.openReceivableAmount ?? 0);
    if (balance <= this.settledThreshold && receivable <= this.settledThreshold) {
      return 'settled';
    }
    return 'open';
  }

  branchSummaryLabel(branch: BranchOpsSummaryRow): string {
    return this.toSettlementLabel(this.branchSummaryState(branch));
  }

  isSelectedBranch(branchId: string): boolean {
    return this.selectedBranch?.branch?.branchId === branchId;
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

  private loadBranch(branchId: string): void {
    this.isLoadingDetail = true;
    this.errorMessage = '';
    this.ensureDefaultLedgerRange();
    this.branchOpsService.getBranchDetail(branchId).subscribe({
      next: detail => {
        this.selectedBranch = detail;
      },
      error: () => {
        this.errorMessage = 'Unable to load branch details.';
        this.selectedBranch = null;
      }
    });
    this.branchOpsService.getBranchOrders(branchId)
      .pipe(finalize(() => (this.isLoadingDetail = false)))
      .subscribe({
        next: orders => {
          this.selectedBranchOrders = orders ?? [];
        },
        error: () => {
          this.selectedBranchOrders = [];
          this.errorMessage = 'Unable to load branch orders.';
        }
      });
    this.clearCategoryLedger();
    this.forceSelectTopCategory = true;
    this.reloadLedgers();
    if (this.activeTabIndex === 1) {
      this.loadBranchAging(branchId);
    }
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
