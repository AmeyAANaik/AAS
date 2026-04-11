import { Component, OnInit } from '@angular/core';
import { FormControl } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize } from 'rxjs/operators';
import { VendorOpsDetail, VendorOpsLedgerEntry, VendorOpsOrderRow, VendorOpsSummaryRow, VendorOpsSummaryTotals } from './vendor-ops.model';
import { VendorOpsService } from './vendor-ops.service';

@Component({
  selector: 'app-vendor-ops-page',
  templateUrl: './vendor-ops-page.component.html',
  styleUrl: './vendor-ops-page.component.scss'
})
export class VendorOpsPageComponent implements OnInit {
  private readonly settledThreshold = 0.01;
  readonly searchControl = new FormControl('', { nonNullable: true });
  readonly summaryColumns = ['vendor', 'pendingOrders', 'awaitingPdf', 'awaitingBillCapture', 'inProgress', 'pendingBillAmount', 'lastActivity', 'templateStatus', 'ledgerBalance', 'actions'];
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
  ledger: VendorOpsLedgerEntry[] = [];
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
    this.route.paramMap.subscribe(params => {
      const vendorId = params.get('vendorId');
      if (vendorId) {
        this.loadVendor(vendorId);
      } else {
        this.selectedVendor = null;
        this.selectedVendorOrders = [];
        this.ledger = [];
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

  downloadLedger(): void {
    const vendorId = this.selectedVendor?.vendor?.vendorId;
    if (!vendorId) {
      return;
    }
    this.vendorOpsService.downloadVendorLedger(vendorId).subscribe({
      next: blob => this.saveBlob(blob, `vendor-ledger-${this.toFileSegment(vendorId)}.csv`),
      error: () => {
        this.errorMessage = 'Unable to download vendor ledger.';
      }
    });
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

  private loadVendor(vendorId: string): void {
    this.isLoadingDetail = true;
    this.errorMessage = '';
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
    this.vendorOpsService.getVendorLedger(vendorId).subscribe({
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
