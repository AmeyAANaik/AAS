import { Component, OnInit } from '@angular/core';
import { FormControl } from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { finalize } from 'rxjs/operators';
import { VendorOpsAnalytics, VendorOpsDetail, VendorOpsLedgerEntry, VendorOpsOrderRow, VendorOpsSummaryRow, VendorOpsSummaryTotals } from './vendor-ops.model';
import { VendorOpsService } from './vendor-ops.service';

@Component({
  selector: 'app-vendor-ops-page',
  templateUrl: './vendor-ops-page.component.html',
  styleUrl: './vendor-ops-page.component.scss'
})
export class VendorOpsPageComponent implements OnInit {
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
  selectedVendorAnalytics: VendorOpsAnalytics | null = null;
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
        this.selectedVendorAnalytics = null;
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
    this.vendorOpsService.getVendorAnalytics(vendorId).subscribe({
      next: analytics => {
        this.selectedVendorAnalytics = analytics;
      },
      error: () => {
        this.selectedVendorAnalytics = null;
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
}
