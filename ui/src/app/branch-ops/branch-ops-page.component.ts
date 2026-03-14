import { Component, OnInit } from '@angular/core';
import { FormControl } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize } from 'rxjs/operators';
import { BranchOpsAnalytics, BranchOpsDetail, BranchOpsLedgerEntry, BranchOpsOrderRow, BranchOpsSummaryRow, BranchOpsSummaryTotals } from './branch-ops.model';
import { BranchOpsService } from './branch-ops.service';

@Component({
  selector: 'app-branch-ops-page',
  templateUrl: './branch-ops-page.component.html',
  styleUrl: './branch-ops-page.component.scss'
})
export class BranchOpsPageComponent implements OnInit {
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
  selectedBranchAnalytics: BranchOpsAnalytics | null = null;
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
        this.selectedBranchAnalytics = null;
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
    this.branchOpsService.getBranchAnalytics(branchId).subscribe({
      next: analytics => {
        this.selectedBranchAnalytics = analytics;
      },
      error: () => {
        this.selectedBranchAnalytics = null;
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
}
