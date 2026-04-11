import { Component, OnInit } from '@angular/core';
import { FormControl } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize } from 'rxjs/operators';
import { BranchOpsDetail, BranchOpsLedgerEntry, BranchOpsOrderRow, BranchOpsSummaryRow, BranchOpsSummaryTotals } from './branch-ops.model';
import { BranchOpsService } from './branch-ops.service';

@Component({
  selector: 'app-branch-ops-page',
  templateUrl: './branch-ops-page.component.html',
  styleUrl: './branch-ops-page.component.scss'
})
export class BranchOpsPageComponent implements OnInit {
  private readonly settledThreshold = 0.01;
  readonly searchControl = new FormControl('', { nonNullable: true });
  readonly summaryColumns = ['branch', 'pendingOrders', 'awaitingVendorAssignment', 'awaitingVendorResponse', 'inProgress', 'openReceivableAmount', 'lastActivity', 'location', 'ledgerBalance', 'actions'];
  readonly orderColumns = ['orderId', 'vendor', 'status', 'orderDate', 'parsedItems', 'vendorBillTotal', 'sellOrderTotal', 'invoiceId', 'actions'];
  readonly ledgerColumns = ['date', 'voucherType', 'voucherNo', 'reference', 'debit', 'credit', 'netChange', 'runningBalance'];

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
    this.route.paramMap.subscribe(params => {
      const branchId = params.get('branchId');
      if (branchId) {
        this.loadBranch(branchId);
      } else {
        this.selectedBranch = null;
        this.selectedBranchOrders = [];
        this.ledger = [];
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
    this.router.navigate(['/branch-ops', branchId]);
  }

  openOrder(orderId: string): void {
    this.router.navigate(['/orders'], { queryParams: { orderId } });
  }

  downloadAllLedgers(): void {
    this.branchOpsService.downloadAllBranchLedgers().subscribe({
      next: blob => this.saveBlob(blob, 'branch-ledger-all.csv'),
      error: () => {
        this.errorMessage = 'Unable to download all branch ledgers.';
      }
    });
  }

  downloadLedger(): void {
    const branchId = this.selectedBranch?.branch?.branchId;
    if (!branchId) {
      return;
    }
    this.branchOpsService.downloadBranchLedger(branchId).subscribe({
      next: blob => this.saveBlob(blob, `branch-ledger-${this.toFileSegment(branchId)}.csv`),
      error: () => {
        this.errorMessage = 'Unable to download branch ledger.';
      }
    });
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

  private loadBranch(branchId: string): void {
    this.isLoadingDetail = true;
    this.errorMessage = '';
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
    this.branchOpsService.getBranchLedger(branchId).subscribe({
      next: response => {
        this.ledger = response.entries ?? [];
      },
      error: () => {
        this.ledger = [];
      }
    });
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
