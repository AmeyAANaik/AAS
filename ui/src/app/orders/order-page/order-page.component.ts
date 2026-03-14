import { animate, style, transition, trigger } from '@angular/animations';
import { AfterViewInit, Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { FormControl } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Subscription } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { VendorService } from '../../vendors/vendor.service';
import { OrderItemPayload, OrderOption, OrderStatus, OrderSummary, SellPreview } from '../order.model';
import { OrderService } from '../order.service';
import { MatPaginator } from '@angular/material/paginator';
import { MatSort } from '@angular/material/sort';
import { MatTableDataSource } from '@angular/material/table';
import { OrderAdvancedFiltersDialogComponent, OrderAdvancedFiltersDialogValue } from './order-advanced-filters-dialog.component';
import { OrderDeleteConfirmDialogComponent, OrderDeleteConfirmDialogData } from './order-delete-confirm-dialog.component';

type UiOrderStatus =
  | 'DRAFT'
  | 'VENDOR_ASSIGNED'
  | 'VENDOR_PDF_RECEIVED'
  | 'VENDOR_BILL_CAPTURED'
  | 'SELL_ORDER_CREATED'
  | 'INVOICED'
  | (string & {});

interface UiOrder {
  name: string;
  status: UiOrderStatus;
  branch: string;
  vendor: string;
  currency: string;
  billTotal: number | null;
  billRef: string;
  billDate: Date | null;
  raw: OrderSummary;
}

interface UiSellPreview {
  estimatedPrice: number;
  itemsCount: number;
  raw: SellPreview;
}

interface PdfParseResult {
  fileName?: string;
  fileUrl?: string;
  items?: unknown[];
  orderItems?: Array<{ item_code?: string; item_name?: string; qty?: number; rate?: number; amount?: number; aas_margin_percent?: number }>;
  template?: { configured?: boolean; used?: boolean; key?: string };
  vendorBillTotal?: number;
  vendorBillRef?: string;
  vendorBillDate?: string;
  marginPercent?: number;
  [key: string]: unknown;
}

interface UiOrderLine {
  item_code: string;
  item_name: string;
  qty: number;
  rate: number;
  amount: number;
  aas_margin_percent: number;
}

@Component({
  selector: 'app-order-page',
  templateUrl: './order-page.component.html',
  styleUrl: './order-page.component.scss',
  animations: [
    trigger('slideIn', [
      transition(':enter', [
        style({ transform: 'translateX(20px)', opacity: 0 }),
        animate('180ms ease-out', style({ transform: 'translateX(0)', opacity: 1 }))
      ]),
      transition(':leave', [
        animate('140ms ease-in', style({ transform: 'translateX(20px)', opacity: 0 }))
      ])
    ])
  ]
})
export class OrderPageComponent implements OnInit, AfterViewInit, OnDestroy {
  searchControl = new FormControl<string>('', { nonNullable: true });
  vendorControl = new FormControl<string | null>(null);
  fromDateControl = new FormControl<string>('', { nonNullable: true });
  toDateControl = new FormControl<string>('', { nonNullable: true });

  appliedStatusFilters = new Set<UiOrderStatus>();
  appliedBranchFilters = new Set<string>();
  appliedVendorFilters = new Set<string>();

  draftStatusFilters = new Set<UiOrderStatus>();
  draftBranchFilters = new Set<string>();
  draftVendorFilters = new Set<string>();

  billTotalControl = new FormControl<number | null>(null);
  billRefControl = new FormControl<string>('', { nonNullable: true });
  billDateControl = new FormControl<Date | null>(new Date());

  orders: UiOrder[] = [];
  readonly displayedColumns: Array<'id' | 'branch' | 'vendor' | 'status' | 'date' | 'actions'> = [
    'id',
    'branch',
    'vendor',
    'status',
    'date',
    'actions'
  ];
  readonly dataSource = new MatTableDataSource<UiOrder>([]);
  selectedOrder: UiOrder | null = null;

  vendorOptions: OrderOption[] = [];
  isVendorsLoading = false;
  vendorsError = '';

  selectedFile: File | null = null;
  fileError = '';
  isUploading = false;
  pdfData: PdfParseResult | null = null;
  orderLines: UiOrderLine[] = [];
  isItemsSaving = false;

  sellPreview: UiSellPreview | null = null;
  errorMessage = '';
  isLoading = false;

  private subscriptions = new Subscription();
  private orderDetailsSeq = 0;
  private requestedOrderId: string | null = null;

  constructor(
    private readonly route: ActivatedRoute,
    private readonly dialog: MatDialog,
    private readonly vendorService: VendorService,
    private readonly orderService: OrderService,
    private readonly snackBar: MatSnackBar
  ) {}

  private tableSort?: MatSort;
  @ViewChild(MatSort)
  set matSort(sort: MatSort | undefined) {
    this.tableSort = sort;
    if (sort) {
      this.dataSource.sort = sort;
    }
  }

  private tablePaginator?: MatPaginator;
  @ViewChild(MatPaginator)
  set matPaginator(paginator: MatPaginator | undefined) {
    this.tablePaginator = paginator;
    if (paginator) {
      this.dataSource.paginator = paginator;
    }
  }

  ngOnInit(): void {
    this.dataSource.filterPredicate = (order, filter) => this.matchesFilter(order, filter);
    this.dataSource.sortingDataAccessor = (order, property) => {
      if (property === 'date') {
        return this.toDayNumber(String(order.raw.transaction_date ?? '')) ?? 0;
      }
      if (property === 'status') {
        return String(order.status ?? '');
      }
      if (property === 'vendor') {
        return String(order.vendor ?? '');
      }
      if (property === 'branch') {
        return String(order.branch ?? '');
      }
      if (property === 'id') {
        return String(order.name ?? '');
      }
      return '';
    };

    this.loadVendors();
    this.loadOrders();
    this.subscriptions.add(
      this.searchControl.valueChanges.subscribe(() => this.applySearch())
    );
    this.subscriptions.add(
      this.fromDateControl.valueChanges.subscribe(() => this.applyDateRange())
    );
    this.subscriptions.add(
      this.toDateControl.valueChanges.subscribe(() => this.applyDateRange())
    );

    this.subscriptions.add(
      this.billTotalControl.valueChanges.subscribe(() => this.updateBillMismatchError())
    );
    this.subscriptions.add(
      this.route.queryParamMap.subscribe(params => {
        this.requestedOrderId = String(params.get('orderId') ?? '').trim() || null;
        this.selectRequestedOrder();
      })
    );
  }

  ngAfterViewInit(): void {
    // MatSort/MatPaginator are assigned via @ViewChild setters (table is under *ngIf).
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

  private loadVendors(): void {
    this.isVendorsLoading = true;
    this.vendorsError = '';
    const sub = this.vendorService
      .listVendors()
      .pipe(finalize(() => (this.isVendorsLoading = false)))
      .subscribe({
        next: vendors => {
          const options = (vendors ?? []).map(vendor => {
            const name = String(vendor?.supplier_name ?? vendor?.name ?? '').trim();
            return {
              id: String(vendor?.name ?? name),
              name: name || String(vendor?.name ?? ''),
              disabled: this.isDisabled(vendor?.disabled)
            };
          });
          this.vendorOptions = options
            .filter(option => !option.disabled)
            .filter(option => option.id.trim() && option.name.trim())
            .map(({ id, name }) => ({ id, name }))
            .sort((a, b) => a.name.localeCompare(b.name));
        },
        error: err => {
          this.vendorsError = this.formatError(err, 'Unable to load vendors');
          this.vendorOptions = [];
        }
      });
    this.subscriptions.add(sub);
  }

  loadOrders(): void {
    this.errorMessage = '';
    this.isLoading = true;
    this.orderService.listOrders({})
      .pipe(finalize(() => (this.isLoading = false)))
      .subscribe({
      next: orders => {
        this.orders = (orders ?? []).map(order => this.toUiOrder(order));
        this.dataSource.data = this.orders;
        this.updateTableFilter();
        this.refreshSelection();
        this.selectRequestedOrder();
      },
      error: err => (this.errorMessage = this.formatError(err, 'Unable to load orders'))
    });
  }

  private applySearch(): void {
    this.updateTableFilter();
    if (this.dataSource.paginator) {
      this.dataSource.paginator.firstPage();
    }
  }

  private applyDateRange(): void {
    this.updateTableFilter();
    this.dataSource.paginator?.firstPage();
  }

  clearDateRange(): void {
    this.fromDateControl.setValue('');
    this.toDateControl.setValue('');
    this.updateTableFilter();
    this.dataSource.paginator?.firstPage();
  }

  private isDisabled(value: unknown): boolean {
    if (value === null || value === undefined) {
      return false;
    }
    if (typeof value === 'boolean') {
      return value;
    }
    if (typeof value === 'number') {
      return value !== 0;
    }
    const text = String(value).trim().toLowerCase();
    if (!text) {
      return false;
    }
    return text === '1' || text === 'true' || text === 'yes';
  }

  openAdvancedFilters(): void {
    const dialogRef = this.dialog.open<OrderAdvancedFiltersDialogComponent, OrderAdvancedFiltersDialogValue, OrderAdvancedFiltersDialogValue>(
      OrderAdvancedFiltersDialogComponent,
      {
        width: '420px',
        data: { from: this.fromDateControl.value, to: this.toDateControl.value }
      }
    );
    this.subscriptions.add(
      dialogRef.afterClosed().subscribe(result => {
        if (!result) {
          return;
        }
        this.fromDateControl.setValue(result.from ?? '');
        this.toDateControl.setValue(result.to ?? '');
        this.updateTableFilter();
        this.dataSource.paginator?.firstPage();
      })
    );
  }

  openFilters(): void {
    this.draftStatusFilters = new Set(this.appliedStatusFilters);
    this.draftBranchFilters = new Set(this.appliedBranchFilters);
    this.draftVendorFilters = new Set(this.appliedVendorFilters);
  }

  applyFilters(): void {
    this.appliedStatusFilters = new Set(this.draftStatusFilters);
    this.appliedBranchFilters = new Set(this.draftBranchFilters);
    this.appliedVendorFilters = new Set(this.draftVendorFilters);
    this.updateTableFilter();
    this.dataSource.paginator?.firstPage();
  }

  clearAllDraftFilters(): void {
    this.draftStatusFilters.clear();
    this.draftBranchFilters.clear();
    this.draftVendorFilters.clear();
  }

  clearAllFilters(): void {
    this.appliedStatusFilters.clear();
    this.appliedBranchFilters.clear();
    this.appliedVendorFilters.clear();
    this.searchControl.setValue('');
    this.fromDateControl.setValue('');
    this.toDateControl.setValue('');
    this.updateTableFilter();
    this.dataSource.paginator?.firstPage();
  }

  toggleDraftStatus(status: UiOrderStatus): void {
    if (this.draftStatusFilters.has(status)) {
      this.draftStatusFilters.delete(status);
    } else {
      this.draftStatusFilters.add(status);
    }
  }

  toggleDraftBranch(branch: string): void {
    if (this.draftBranchFilters.has(branch)) {
      this.draftBranchFilters.delete(branch);
    } else {
      this.draftBranchFilters.add(branch);
    }
  }

  toggleDraftVendor(vendor: string): void {
    if (this.draftVendorFilters.has(vendor)) {
      this.draftVendorFilters.delete(vendor);
    } else {
      this.draftVendorFilters.add(vendor);
    }
  }

  removeAppliedFilter(kind: 'status' | 'branch' | 'vendor', value: string): void {
    if (kind === 'status') {
      this.appliedStatusFilters.delete(value as UiOrderStatus);
      this.draftStatusFilters.delete(value as UiOrderStatus);
    } else if (kind === 'branch') {
      this.appliedBranchFilters.delete(value);
      this.draftBranchFilters.delete(value);
    } else {
      this.appliedVendorFilters.delete(value);
      this.draftVendorFilters.delete(value);
    }
    this.updateTableFilter();
    this.dataSource.paginator?.firstPage();
  }

  get availableStatuses(): UiOrderStatus[] {
    const base: UiOrderStatus[] = [
      'DRAFT',
      'VENDOR_ASSIGNED',
      'VENDOR_PDF_RECEIVED',
      'VENDOR_BILL_CAPTURED',
      'SELL_ORDER_CREATED',
      'INVOICED'
    ];
    const seen = new Set<string>();
    const extra: UiOrderStatus[] = [];
    for (const order of this.orders) {
      const status = String(order.status ?? '').trim();
      if (!status) {
        continue;
      }
      if (base.includes(status as UiOrderStatus)) {
        continue;
      }
      if (!seen.has(status)) {
        seen.add(status);
        extra.push(status as UiOrderStatus);
      }
    }
    return [...base, ...extra];
  }

  get availableBranches(): string[] {
    const branches = Array.from(new Set(this.orders.map(order => order.branch).filter(Boolean)));
    return branches.sort((a, b) => a.localeCompare(b));
  }

  get availableVendors(): string[] {
    const vendors = Array.from(new Set(this.orders.map(order => order.vendor).filter(Boolean)));
    return vendors.sort((a, b) => a.localeCompare(b));
  }

  get filterSummary(): string {
    const parts: string[] = [];
    if (this.appliedStatusFilters.size) {
      parts.push(`Status: ${Array.from(this.appliedStatusFilters).map(s => this.getStatusLabel(s)).join(', ')}`);
    }
    if (this.appliedBranchFilters.size) {
      parts.push(`Branch: ${Array.from(this.appliedBranchFilters).join(', ')}`);
    }
    if (this.appliedVendorFilters.size) {
      parts.push(`Vendor: ${Array.from(this.appliedVendorFilters).join(', ')}`);
    }
    const from = this.fromDateControl.value.trim();
    const to = this.toDateControl.value.trim();
    if (from || to) {
      parts.push(`Date: ${from || '…'} → ${to || '…'}`);
    }
    const query = this.searchControl.value.trim();
    if (query) {
      parts.push(`Search: \"${query}\"`);
    }
    return parts.length ? parts.join(' • ') : 'No filters applied';
  }

  get activeFilterCount(): number {
    const from = this.fromDateControl.value.trim();
    const to = this.toDateControl.value.trim();
    return (
      this.appliedStatusFilters.size +
      this.appliedBranchFilters.size +
      this.appliedVendorFilters.size +
      (from ? 1 : 0) +
      (to ? 1 : 0)
    );
  }

  get selectedFilterChips(): Array<{ kind: 'status' | 'branch' | 'vendor'; label: string; value: string }> {
    const chips: Array<{ kind: 'status' | 'branch' | 'vendor'; label: string; value: string }> = [];
    for (const status of Array.from(this.appliedStatusFilters)) {
      chips.push({ kind: 'status', value: String(status), label: this.getStatusLabel(status) });
    }
    for (const branch of Array.from(this.appliedBranchFilters)) {
      chips.push({ kind: 'branch', value: branch, label: branch });
    }
    for (const vendor of Array.from(this.appliedVendorFilters)) {
      chips.push({ kind: 'vendor', value: vendor, label: vendor });
    }
    return chips;
  }

  private updateTableFilter(): void {
    const payload = {
      q: this.searchControl.value.trim().toLowerCase(),
      statuses: Array.from(this.appliedStatusFilters),
      branches: Array.from(this.appliedBranchFilters),
      vendors: Array.from(this.appliedVendorFilters),
      from: this.fromDateControl.value.trim(),
      to: this.toDateControl.value.trim()
    };
    this.dataSource.filter = JSON.stringify(payload);
  }

  private matchesFilter(order: UiOrder, rawFilter: string): boolean {
    let filter: {
      q?: string;
      statuses?: string[];
      branches?: string[];
      vendors?: string[];
      from?: string;
      to?: string;
    } = {};
    const trimmed = String(rawFilter ?? '').trim();
    if (trimmed) {
      try {
        filter = JSON.parse(trimmed) as typeof filter;
      } catch {
        filter = { q: trimmed };
      }
    }

    const statuses = (filter.statuses ?? []).map(String);
    if (statuses.length && !statuses.includes(String(order.status))) {
      return false;
    }
    const branches = (filter.branches ?? []).map(String);
    if (branches.length && !branches.includes(String(order.branch))) {
      return false;
    }
    const vendors = (filter.vendors ?? []).map(String);
    if (vendors.length && !vendors.includes(String(order.vendor))) {
      return false;
    }

    const orderDay = this.toDayNumber(String(order.raw.transaction_date ?? ''));
    const fromDay = this.toDayNumber(String(filter.from ?? ''));
    const toDay = this.toDayNumber(String(filter.to ?? ''));
    if ((fromDay !== null || toDay !== null) && orderDay === null) {
      return false;
    }
    if (fromDay !== null && orderDay !== null && orderDay < fromDay) {
      return false;
    }
    if (toDay !== null && orderDay !== null && orderDay > toDay) {
      return false;
    }

    const q = String(filter.q ?? '').trim().toLowerCase();
    if (!q) {
      return true;
    }
    const date = String(order.raw.transaction_date ?? '');
    return (
      String(order.name ?? '').toLowerCase().includes(q) ||
      String(order.branch ?? '').toLowerCase().includes(q) ||
      String(order.vendor ?? '').toLowerCase().includes(q) ||
      String(order.status ?? '').toLowerCase().includes(q) ||
      date.toLowerCase().includes(q)
    );
  }

  private toDayNumber(value: string): number | null {
    const text = String(value ?? '').trim();
    if (!text) {
      return null;
    }
    const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(text);
    if (!match) {
      return null;
    }
    const year = Number(match[1]);
    const month = Number(match[2]);
    const day = Number(match[3]);
    if (!Number.isFinite(year) || !Number.isFinite(month) || !Number.isFinite(day)) {
      return null;
    }
    return year * 10000 + month * 100 + day;
  }

  selectOrder(order: UiOrder): void {
    this.errorMessage = '';
    this.fileError = '';
    this.selectedOrder = order;
    this.vendorControl.setValue(order.vendor || null);
    this.selectedFile = null;
    this.pdfData = null;
    const fileUrl = String(order.raw.aas_vendor_pdf ?? '').trim();
    if (fileUrl) {
      const parts = fileUrl.split('/');
      const fileName = parts[parts.length - 1] || 'Vendor PDF';
      this.pdfData = { fileName, fileUrl };
    }
    this.sellPreview = null;
    this.orderLines = [];

    this.billTotalControl.setValue(order.billTotal);
    this.billRefControl.setValue(order.billRef);
    this.billDateControl.setValue(order.billDate ?? new Date());
    const seq = ++this.orderDetailsSeq;
    this.orderService.getOrder(order.name).subscribe({
      next: res => {
        // Guard against late/stale responses overwriting newer data (e.g., after PDF upload).
        if (seq !== this.orderDetailsSeq || this.selectedOrder?.name !== order.name) {
          return;
        }
        const data = (res as any)?.data ?? res;
        const items = Array.isArray(data?.items) ? data.items : [];
        this.orderLines = items.map((row: any) => this.toUiOrderLine(row)).filter((row: UiOrderLine) => row.item_code);
      },
      error: () => {
        // Non-blocking: user can still proceed with upload/capture steps.
        if (seq === this.orderDetailsSeq) {
          this.orderLines = [];
        }
      }
    });
  }

  closeManage(): void {
    this.selectedOrder = null;
    this.selectedFile = null;
    this.pdfData = null;
    this.sellPreview = null;
    this.errorMessage = '';
    this.fileError = '';
    this.orderLines = [];
    this.isItemsSaving = false;
  }

  getStatusLabel(status: UiOrderStatus): string {
    switch (status) {
      case 'DRAFT':
        return 'Pending';
      case 'VENDOR_ASSIGNED':
        return 'Vendor assigned';
      case 'VENDOR_PDF_RECEIVED':
        return 'PDF received';
      case 'VENDOR_BILL_CAPTURED':
        return 'Bill captured';
      case 'SELL_ORDER_CREATED':
        return 'Sell order created';
      case 'INVOICED':
        return 'Invoiced';
      default:
        return String(status || 'Pending');
    }
  }

  isStepActive(step: 1 | 2 | 3 | 4): boolean {
    const status = this.selectedOrder?.status ?? 'DRAFT';
    if (step === 1) {
      return status === 'DRAFT';
    }
    if (step === 2) {
      return status === 'VENDOR_ASSIGNED';
    }
    if (step === 3) {
      return status === 'VENDOR_PDF_RECEIVED';
    }
    return status === 'VENDOR_BILL_CAPTURED';
  }

  isStepCompleted(step: 1 | 2 | 3 | 4): boolean {
    const status = this.selectedOrder?.status ?? 'DRAFT';
    const index = this.statusIndex(status);
    return index >= step;
  }

  assignVendor(): void {
    if (!this.selectedOrder) {
      return;
    }
    const vendorId = String(this.vendorControl.value ?? '').trim();
    if (!vendorId) {
      this.errorMessage = 'Select a vendor to assign.';
      return;
    }
    this.errorMessage = '';
    this.orderService
      .assignVendor(this.selectedOrder.name, vendorId)
      .subscribe({
        next: () => {
          this.selectedOrder = { ...this.selectedOrder!, vendor: vendorId, status: 'VENDOR_ASSIGNED' };
          this.loadOrders();
        },
        error: err => (this.errorMessage = this.formatError(err, 'Unable to assign vendor'))
      });
  }

  canDeleteOrder(order: UiOrder): boolean {
    const status = (order?.status ?? 'DRAFT') as UiOrderStatus;
    return status === 'DRAFT' || status === 'VENDOR_ASSIGNED' || status === 'VENDOR_PDF_RECEIVED';
  }

  confirmDeleteOrder(order: UiOrder): void {
    const orderId = String(order?.name ?? '').trim();
    if (!orderId) {
      return;
    }
    if (!this.canDeleteOrder(order)) {
      this.snackBar.open('This order cannot be deleted in its current status.', 'Dismiss', { duration: 4500 });
      return;
    }
    const dialogRef = this.dialog.open<
      OrderDeleteConfirmDialogComponent,
      OrderDeleteConfirmDialogData,
      boolean
    >(OrderDeleteConfirmDialogComponent, {
      width: '420px',
      data: { orderId, purchaseOrderId: String(order?.raw?.aas_po ?? '').trim() || undefined }
    });

    this.subscriptions.add(
      dialogRef.afterClosed().subscribe(confirmed => {
        if (!confirmed) {
          return;
        }
        this.deleteOrder(orderId);
      })
    );
  }

  private deleteOrder(orderId: string): void {
    this.errorMessage = '';
    this.orderService.deleteOrder(orderId).subscribe({
      next: () => {
        this.orders = this.orders.filter(o => o.name !== orderId);
        this.dataSource.data = this.orders;
        this.updateTableFilter();
        this.snackBar.open(`Order ${orderId} deleted.`, 'Dismiss', { duration: 3000 });
      },
      error: err => {
        this.errorMessage = this.formatError(err, `Unable to delete order ${orderId}`);
      }
    });
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement | null;
    this.selectedFile = input?.files?.[0] ?? null;
    this.fileError = '';
  }

  clearFile(): void {
    this.selectedFile = null;
    this.fileError = '';
  }

  async uploadAndParsePDF(): Promise<void> {
    if (!this.selectedOrder) {
      return;
    }
    if (!this.selectedFile) {
      this.fileError = 'Select a PDF file first.';
      return;
    }
    const isPdf =
      this.selectedFile.type === 'application/pdf' ||
      this.selectedFile.name.toLowerCase().endsWith('.pdf');
    if (!isPdf) {
      this.fileError = 'File must be a PDF.';
      return;
    }
    const header = new Uint8Array(await this.selectedFile.slice(0, 4).arrayBuffer());
    const magic = String.fromCharCode(...header);
    if (magic !== '%PDF') {
      this.fileError = 'Invalid PDF file. Please upload a real PDF export.';
      return;
    }
    this.errorMessage = '';
    this.fileError = '';
    this.isUploading = true;
    // Invalidate any in-flight order detail requests so they can't overwrite parsed lines.
    this.orderDetailsSeq++;
    this.orderService
      .uploadVendorPdf(this.selectedOrder.name, this.selectedFile)
      .pipe(finalize(() => (this.isUploading = false)))
      .subscribe({
        next: res => {
          const parsed = (res ?? null) as PdfParseResult | null;
          this.pdfData = parsed;
          const lines = parsed?.orderItems ?? [];
          if (Array.isArray(lines) && lines.length) {
            this.orderLines = lines.map(line => this.toUiOrderLine(line)).filter(row => row.item_code);
          }
          this.updateBillMismatchError();

          const vendorBillTotal = Number(parsed?.vendorBillTotal ?? 0);
          if (Number.isFinite(vendorBillTotal) && vendorBillTotal > 0) {
            this.billTotalControl.setValue(vendorBillTotal);
          }

          const vendorBillRef = String(parsed?.vendorBillRef ?? '').trim();
          if (vendorBillRef) {
            this.billRefControl.setValue(vendorBillRef);
          }

          const vendorBillDateText = String(parsed?.vendorBillDate ?? '').trim();
          if (vendorBillDateText) {
            const parsedDate = this.parseDate(vendorBillDateText);
            if (parsedDate) {
              this.billDateControl.setValue(parsedDate);
            }
          }

          this.selectedFile = null;
          this.updateBillMismatchError();
          this.loadOrders();
        },
        error: err => {
          this.errorMessage = this.formatError(err, 'Unable to upload and parse vendor PDF');
        }
      });
  }

  removeOrderLine(index: number): void {
    if (index < 0 || index >= this.orderLines.length) {
      return;
    }
    this.orderLines = this.orderLines.filter((_, i) => i !== index);
    this.updateBillMismatchError();
  }

  recalcLine(line: UiOrderLine): void {
    const qty = Number(line.qty ?? 0);
    const rate = Number(line.rate ?? 0);
    const margin = Number(line.aas_margin_percent ?? 0);
    line.qty = Number.isFinite(qty) ? qty : 0;
    line.rate = Number.isFinite(rate) ? rate : 0;
    line.aas_margin_percent = Number.isFinite(margin) && margin >= 0 ? margin : 0;
    line.amount = Math.round(line.qty * line.rate * 100) / 100;
    this.updateBillMismatchError();
  }

  adjustQty(line: UiOrderLine, delta: number): void {
    const next = Number(line.qty ?? 0) + delta;
    line.qty = Math.max(0, Math.round(next * 100) / 100);
    this.recalcLine(line);
  }

  get itemsTotal(): number {
    const sum = this.orderLines.reduce((acc, line) => acc + Number(line.amount ?? 0), 0);
    return Math.round(sum * 100) / 100;
  }

  get billTotal(): number {
    const v = Number(this.billTotalControl.value ?? 0);
    return Number.isFinite(v) ? v : 0;
  }

  get billDiff(): number {
    return Math.round((this.billTotal - this.itemsTotal) * 100) / 100;
  }

  get billMatchesItems(): boolean {
    // Treat small rounding differences as match.
    return Math.abs(this.billDiff) <= 0.5;
  }

  get billValidationMessage(): string {
    if (!this.orderLines.length) {
      return '';
    }
    const total = this.billTotal;
    if (total <= 0) {
      return '';
    }
    if (this.billMatchesItems) {
      return '';
    }
    return `Bill total must match items total. Diff: ${this.billDiff.toFixed(2)}`;
  }

  applyItemsTotalToBill(): void {
    this.billTotalControl.setValue(this.itemsTotal);
    this.updateBillMismatchError();
  }

  saveOrderLines(): void {
    if (!this.selectedOrder) {
      return;
    }
    if (!this.orderLines.length) {
      this.errorMessage = 'At least one item line is required.';
      return;
    }
    const invalidMargin = this.orderLines.some(line => !Number.isFinite(Number(line.aas_margin_percent)) || Number(line.aas_margin_percent) < 0);
    if (invalidMargin) {
      this.errorMessage = 'Margin must be a non-negative number for every item.';
      return;
    }
    const payload: OrderItemPayload[] = this.orderLines.map(line => ({
      item_code: line.item_code,
      qty: Number(line.qty ?? 0),
      rate: Number(line.rate ?? 0),
      aas_margin_percent: Number(line.aas_margin_percent ?? 0)
    }));
    this.isItemsSaving = true;
    this.errorMessage = '';
    this.orderService
      .updateOrderItems(this.selectedOrder.name, payload)
      .pipe(finalize(() => (this.isItemsSaving = false)))
      .subscribe({
        next: res => {
          const items = (res as any)?.items ?? [];
          if (Array.isArray(items) && items.length) {
            this.orderLines = items.map((row: any) => this.toUiOrderLine(row)).filter((row: UiOrderLine) => row.item_code);
          }
          this.updateBillMismatchError();
          this.snackBar.open('Order items updated.', 'Dismiss', { duration: 2500 });
          // Keep bill total aligned unless user explicitly changed it after editing.
          if (this.billTotalControl.value === null || this.billTotalControl.value === 0 || this.billMatchesItems) {
            this.applyItemsTotalToBill();
          }
          this.loadOrders();
        },
        error: err => {
          this.errorMessage = this.formatError(err, 'Unable to update order items');
        }
      });
  }

  isBillFormValid(): boolean {
    const total = Number(this.billTotalControl.value ?? 0);
    const ref = this.billRefControl.value.trim();
    const date = this.billDateControl.value;
    return (
      total > 0 &&
      Boolean(ref) &&
      Boolean(date) &&
      this.billMatchesItems
    );
  }

  captureBill(): void {
    if (!this.selectedOrder) {
      return;
    }
    if (!this.billMatchesItems) {
      this.errorMessage = this.billValidationMessage || 'Bill total must match items total.';
      return;
    }
    if (!this.isBillFormValid()) {
      this.errorMessage = 'Fill in bill total, reference, and date.';
      return;
    }
    const total = Number(this.billTotalControl.value ?? 0);
    const ref = this.billRefControl.value.trim();
    const date = this.billDateControl.value ?? new Date();
    this.errorMessage = '';
    this.orderService
      .captureVendorBill(this.selectedOrder.name, {
        vendor_bill_total: total,
        vendor_bill_ref: ref,
        vendor_bill_date: this.formatDate(date)
      })
      .pipe(finalize(() => this.loadOrders()))
      .subscribe({
        next: () => {
          this.selectedOrder = {
            ...this.selectedOrder!,
            status: 'VENDOR_BILL_CAPTURED',
            billTotal: total,
            billRef: ref,
            billDate: date
          };
        },
        error: err => (this.errorMessage = this.formatError(err, 'Unable to capture vendor bill'))
      });
  }

  private updateBillMismatchError(): void {
    const ctrl = this.billTotalControl;
    const total = this.billTotal;
    const shouldValidate = this.orderLines.length > 0 && total > 0;
    const mismatch = shouldValidate && !this.billMatchesItems;

    const current = ctrl.errors ?? {};
    if (mismatch) {
      if (!current['mismatch']) {
        ctrl.setErrors({ ...current, mismatch: true });
      }
      return;
    }
    if (current['mismatch']) {
      const { mismatch: _ignored, ...rest } = current as any;
      ctrl.setErrors(Object.keys(rest).length ? rest : null);
    }
  }

  calculatePreview(): void {
    if (!this.selectedOrder) {
      return;
    }
    this.errorMessage = '';
    this.orderService.getSellPreview(this.selectedOrder.name).subscribe({
      next: preview => {
        this.sellPreview = {
          raw: preview,
          estimatedPrice: Number((preview as { sellAmount?: number }).sellAmount ?? 0),
          itemsCount: Number((this.pdfData?.items?.length ?? 0))
        };
      },
      error: err => (this.errorMessage = this.formatError(err, 'Unable to calculate preview'))
    });
  }

  createSellOrder(): void {
    if (!this.selectedOrder) {
      return;
    }
    this.errorMessage = '';
    this.orderService
      .createSellOrder(this.selectedOrder.name)
      .pipe(finalize(() => this.loadOrders()))
      .subscribe({
        next: () => {
          this.selectedOrder = { ...this.selectedOrder!, status: 'SELL_ORDER_CREATED' };
        },
        error: err => (this.errorMessage = this.formatError(err, 'Unable to create sell order'))
      });
  }

  private refreshSelection(): void {
    if (!this.selectedOrder) {
      return;
    }
    const updated = this.orders.find(order => order.name === this.selectedOrder?.name) ?? null;
    if (updated) {
      this.selectedOrder = updated;
      this.vendorControl.setValue(updated.vendor || null);
      this.billTotalControl.setValue(updated.billTotal);
      this.billRefControl.setValue(updated.billRef);
      this.billDateControl.setValue(updated.billDate ?? new Date());
    }
  }

  private selectRequestedOrder(): void {
    if (!this.requestedOrderId) {
      return;
    }
    const match = this.orders.find(order => order.name === this.requestedOrderId);
    if (!match) {
      return;
    }
    this.selectOrder(match);
    this.requestedOrderId = null;
  }

  private toUiOrder(order: OrderSummary): UiOrder {
    const name = String(order.name ?? '').trim();
    const branch = String(order.customer ?? '').trim() || 'Unknown';
    const vendor = String(order.aas_vendor ?? '').trim();
    const status = this.normalizeStatus(order);
    const currency = this.resolveCurrency(order);

    const billTotal = order.aas_vendor_bill_total === undefined ? null : Number(order.aas_vendor_bill_total);
    const billRef = String(order.aas_vendor_bill_ref ?? '');
    const billDate = this.parseDate(order.aas_vendor_bill_date);

    return {
      name,
      branch,
      vendor,
      status,
      currency,
      billTotal,
      billRef,
      billDate,
      raw: order
    };
  }

  private toUiOrderLine(row: any): UiOrderLine {
    const qty = Number(row?.qty ?? 0);
    const rate = Number(row?.rate ?? 0);
    const amount = Number(row?.amount ?? qty * rate);
    const margin = Number(row?.aas_margin_percent ?? 0);
    return {
      item_code: String(row?.item_code ?? '').trim(),
      item_name: String(row?.item_name ?? row?.item_code ?? '').trim(),
      qty: Number.isFinite(qty) ? qty : 0,
      rate: Number.isFinite(rate) ? rate : 0,
      amount: Number.isFinite(amount) ? amount : 0,
      aas_margin_percent: Number.isFinite(margin) && margin >= 0 ? margin : 0
    };
  }

  private resolveCurrency(order: OrderSummary): string {
    const currency = String(order.currency ?? '').trim();
    if (currency) {
      return currency;
    }
    const priceListCurrency = String(order.price_list_currency ?? '').trim();
    if (priceListCurrency) {
      return priceListCurrency;
    }
    // Fall back to the expected ERPNext default in this project.
    return 'INR';
  }

  private normalizeStatus(order: OrderSummary): UiOrderStatus {
    const aas = String(order.aas_status ?? '').trim();
    if (aas) {
      return aas as UiOrderStatus;
    }
    const fallback = String(order.status ?? '').trim().toLowerCase();
    if (fallback === 'draft') {
      return 'DRAFT';
    }
    return 'DRAFT';
  }

  private statusIndex(status: UiOrderStatus): number {
    switch (status) {
      case 'DRAFT':
        return 1;
      case 'VENDOR_ASSIGNED':
        return 2;
      case 'VENDOR_PDF_RECEIVED':
        return 3;
      case 'VENDOR_BILL_CAPTURED':
        return 4;
      case 'SELL_ORDER_CREATED':
      case 'INVOICED':
        return 5;
      default:
        return 1;
    }
  }

  private parseDate(value: unknown): Date | null {
    const text = String(value ?? '').trim();
    if (!text) {
      return null;
    }
    const parsed = new Date(text);
    return Number.isNaN(parsed.getTime()) ? null : parsed;
  }

  private formatDate(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  private formatError(err: unknown, fallback: string): string {
    const message = (err as { error?: { message?: string } } | null)?.error?.message;
    if (typeof message === 'string' && message.trim()) {
      return message;
    }
    if (err instanceof Error) {
      return err.message;
    }
    if (typeof err === 'string') {
      return err;
    }
    return fallback;
  }
}
