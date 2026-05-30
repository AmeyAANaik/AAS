import { animate, style, transition, trigger } from '@angular/animations';
import { AfterViewInit, Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { FormControl } from '@angular/forms';
import { ActivatedRoute } from '@angular/router';
import { MatAutocompleteSelectedEvent } from '@angular/material/autocomplete';
import { MatDialog } from '@angular/material/dialog';
import { MatSnackBar } from '@angular/material/snack-bar';
import { Observable, of, Subscription } from 'rxjs';
import { finalize, switchMap } from 'rxjs/operators';
import { VendorService } from '../../vendors/vendor.service';
import { ItemService } from '../../items/item.service';
import { Item } from '../../items/item.model';
import { OrderBranchImage, OrderItemPayload, ItemOption, OrderOption, OrderStatus, OrderSummary, SellPreview } from '../order.model';
import { OrderService } from '../order.service';
import { MatPaginator } from '@angular/material/paginator';
import { MatSort } from '@angular/material/sort';
import { MatTableDataSource } from '@angular/material/table';
import { OrderAdvancedFiltersDialogComponent, OrderAdvancedFiltersDialogValue } from './order-advanced-filters-dialog.component';
import { OrderDeleteConfirmDialogComponent, OrderDeleteConfirmDialogData } from './order-delete-confirm-dialog.component';
import { OrderBranchImageGalleryDialogComponent } from './order-branch-image-gallery-dialog.component';
import { formatUiError } from '../../shared/error-message.util';

type UiOrderStatus =
  | 'DRAFT'
  | 'VENDOR_ASSIGNED'
  | 'VENDOR_PDF_RECEIVED'
  | 'VENDOR_BILL_CAPTURED'
  | 'SELL_ORDER_CREATED'
  | 'INVOICED'
  | (string & {});

const BRANCH_IMAGE_PLACEHOLDER_ITEM_CODE = 'AAS-SYSTEM-BRANCH-IMAGE';
const DEFAULT_ORDER_MARGIN_PERCENT = 7;
const ORDER_DESCRIPTION_CLEANUP_TOKENS = ['SFK', 'PRAVIN', 'AMBARI'] as const;

interface UiOrder {
  name: string;
  displayId: string;
  status: UiOrderStatus;
  branch: string;
  vendor: string;
  currency: string;
  billTotal: number | null;
  transportCharge: number;
  roundingAdjustment: number;
  billRef: string;
  billDate: Date | null;
  raw: OrderSummary;
}

interface VendorOption {
  id: string;
  name: string;
  category?: string;
}

interface UiSellPreview {
  estimatedPrice: number;
  itemsCount: number;
  raw: SellPreview;
}

interface PdfParseResult {
  fileName?: string;
  fileUrl?: string;
  fieldMapping?: {
    itemMappings?: Array<{ targetField?: string; sourceLabel?: string; present?: boolean; confidence?: string }>;
    summaryMappings?: Array<{ targetField?: string; sourceLabel?: string; present?: boolean; confidence?: string }>;
    notes?: string;
    generatorType?: string;
    generatorModel?: string;
  };
  items?: unknown[];
  orderItems?: Array<{
    item_code?: string;
    item_name?: string;
    qty?: number;
    rate?: number;
    amount?: number;
    aas_margin_percent?: number;
    aas_vendor_rate?: number;
    aas_rate_before_tax?: number;
    aas_rate_after_tax?: number;
    aas_mrp?: number;
    aas_gst_percent?: number;
    manual_entry?: boolean;
    parse_note?: string;
  }>;
  completeness?: {
    expectedItemCount?: number;
    extractedItemCount?: number;
    itemCountComplete?: boolean;
    expectedSerials?: number[];
    extractedSerials?: number[];
    missingSerials?: number[];
    missingSerialContexts?: Array<{ serial: number; parserContext?: string[]; camelotContext?: string[] }>;
  };
  template?: { configured?: boolean; used?: boolean; key?: string };
  vendorBillTotal?: number;
  vendorBillRef?: string;
  vendorBillDate?: string;
  transportCharge?: number;
  marginPercent?: number;
  [key: string]: unknown;
}

interface UiOrderLine {
  source_serial?: number | null;
  item_code: string;
  item_name: string;
  display_description?: string;
  qty: number;
  rate: number;
  amount: number;
  aas_margin_percent: number;
  aas_vendor_rate?: number | null;
  aas_rate_before_tax?: number | null;
  aas_rate_after_tax?: number | null;
  aas_mrp?: number | null;
  aas_gst_percent?: number | null;
  manual_entry?: boolean;
  parse_note?: string | null;
  mrpApplied: boolean;
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
  transportChargeControl = new FormControl<number>(0, { nonNullable: true });
  mismatchOverrideControl = new FormControl<boolean>(false, { nonNullable: true });
  applyTransportToInvoiceControl = new FormControl<boolean>(false, { nonNullable: true });
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

  vendorOptions: VendorOption[] = [];
  isVendorsLoading = false;
  vendorsError = '';
  itemOptions: ItemOption[] = [];
  isItemsLoading = false;
  itemsError = '';

  selectedFile: File | null = null;
  fileError = '';
  isUploading = false;
  pdfData: PdfParseResult | null = null;
  orderLines: UiOrderLine[] = [];
  isItemsSaving = false;
  selectedDescriptionCleanupTokens = new Set<string>();
  descriptionBulkRemoveText = '';
  descriptionReplaceFrom = '';
  descriptionReplaceTo = '';

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
    private readonly itemService: ItemService,
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
        return String(order.displayId ?? order.name ?? '');
      }
      return '';
    };

    this.loadVendors();
    this.loadItems();
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
      this.transportChargeControl.valueChanges.subscribe(() => this.updateBillMismatchError())
    );
    this.subscriptions.add(
      this.mismatchOverrideControl.valueChanges.subscribe(() => this.updateBillMismatchError())
    );
    this.subscriptions.add(
      this.route.queryParamMap.subscribe(params => {
        this.requestedOrderId = String(params.get('orderId') ?? '').trim() || null;
        const requestedQuery = String(params.get('q') ?? '').trim();
        if (requestedQuery && requestedQuery !== this.searchControl.value) {
          this.searchControl.setValue(requestedQuery);
        }
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
              category: String(vendor?.category ?? '').trim(),
              disabled: this.isDisabled(vendor?.disabled)
            };
          });
          this.vendorOptions = options
            .filter(option => !option.disabled)
            .filter(option => option.id.trim() && option.name.trim())
            .map(({ id, name, category }) => ({ id, name, category }))
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
      String(order.displayId ?? '').toLowerCase().includes(q) ||
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
    this.transportChargeControl.setValue(order.transportCharge ?? 0);
    this.mismatchOverrideControl.setValue(false);
    this.applyTransportToInvoiceControl.setValue((order.transportCharge ?? 0) > 0);
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
        this.selectedOrder = {
          ...this.selectedOrder,
          raw: {
            ...this.selectedOrder?.raw,
            ...data
          }
        };
        const items = Array.isArray(data?.items) ? data.items : [];
        this.orderLines = this.hydrateOrderLines(items);
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

  get assignableVendorOptions(): VendorOption[] {
    const category = this.normalizeCategory(String(this.selectedOrder?.raw?.aas_category ?? ''));
    if (!category) {
      return this.vendorOptions;
    }
    const categoryMatches = this.vendorOptions.filter(vendor =>
      this.normalizeCategory(vendor.category ?? '') === category
    );
    return categoryMatches.length ? categoryMatches : this.vendorOptions;
  }

  get selectedOrderCategory(): string {
    return String(this.selectedOrder?.raw?.aas_category ?? '').trim();
  }

  get canManageVendorPdf(): boolean {
    const status = this.selectedOrder?.status ?? 'DRAFT';
    return status === 'VENDOR_ASSIGNED'
      || status === 'VENDOR_PDF_RECEIVED'
      || status === 'VENDOR_BILL_CAPTURED'
      || status === 'SELL_ORDER_CREATED';
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
    return !!String(order?.name ?? '').trim();
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
        this.snackBar.open(`Order ${orderId} archived.`, 'Dismiss', { duration: 3000 });
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
            this.orderLines = this.hydrateOrderLines(lines, parsed?.completeness?.extractedSerials ?? []);
          }
          this.updateBillMismatchError();

          const vendorBillTotal = Number(parsed?.vendorBillTotal ?? 0);
          if (Number.isFinite(vendorBillTotal) && vendorBillTotal > 0) {
            this.billTotalControl.setValue(vendorBillTotal);
          }

          const transportCharge = Number(parsed?.transportCharge ?? 0);
          this.transportChargeControl.setValue(Number.isFinite(transportCharge) && transportCharge > 0 ? transportCharge : 0);

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

  addManualOrderLine(): void {
    const nextMissing = this.missingParserSerials[0];
    const suggestedName = this.suggestMissingItemName(nextMissing);
    const parseNote = nextMissing
      ? `Added manually because invoice row ${nextMissing} was not parsed.`
      : 'Added manually because an invoice row was not parsed.';
    this.orderLines = [
      ...this.orderLines,
      {
        source_serial: Number.isFinite(nextMissing) ? Number(nextMissing) : null,
        item_code: this.resolveItemCodeFromName(suggestedName),
        item_name: suggestedName,
        display_description: suggestedName,
        qty: 1,
        rate: 0,
        amount: 0,
        aas_margin_percent: this.resolveItemMarginPercent(suggestedName),
        aas_vendor_rate: 0,
        aas_rate_before_tax: null,
        aas_rate_after_tax: null,
        aas_mrp: null,
        aas_gst_percent: null,
        manual_entry: true,
        parse_note: parseNote,
        mrpApplied: false
      }
    ];
    this.updateBillMismatchError();
  }

  recalcLine(line: UiOrderLine): void {
    const qty = Number(line.qty ?? 0);
    const rate = Number(line.rate ?? 0);
    const margin = Number(line.aas_margin_percent ?? 0);
    const gst = Number(line.aas_gst_percent ?? 0);
    line.qty = Number.isFinite(qty) ? qty : 0;
    line.rate = Number.isFinite(rate) ? rate : 0;
    line.item_name = String(line.item_name ?? '').trim();
    if (line.manual_entry) {
      line.item_code = this.resolveItemCodeFromName(line.item_name);
      line.aas_margin_percent = Number.isFinite(margin) && margin > 0
        ? margin
        : this.resolveItemMarginPercent(line.item_name);
    } else {
      line.item_code = String(line.item_code ?? '').trim();
      line.aas_margin_percent = Number.isFinite(margin) && margin >= 0 ? margin : 0;
    }
    line.aas_gst_percent = Number.isFinite(gst) && gst >= 0 ? gst : 0;
    line.amount = Math.round(line.qty * line.rate * 100) / 100;
    line.aas_vendor_rate = line.rate;
    line.aas_rate_before_tax = this.deriveLineRateBeforeTax(line);
    line.aas_rate_after_tax = this.deriveLineRateAfterTax(line);
    line.mrpApplied = this.isMrpCapApplied(line);
    this.updateBillMismatchError();
  }

  adjustQty(line: UiOrderLine, delta: number): void {
    const next = Number(line.qty ?? 0) + delta;
    line.qty = Math.max(0, Math.round(next * 100) / 100);
    this.recalcLine(line);
  }

  get itemsSubtotal(): number {
    const sum = this.orderLines.reduce((acc, line) => acc + Number(line.amount ?? 0), 0);
    return Math.round(sum * 100) / 100;
  }

  get gstTotal(): number {
    if (this.gstIncludedInLineAmounts) {
      return 0;
    }
    const sum = this.rawGstOnTopTotal;
    return Math.round(sum * 100) / 100;
  }

  get itemsTotal(): number {
    return Math.round((this.itemsSubtotal + this.gstTotal) * 100) / 100;
  }

  get gstIncludedInLineAmounts(): boolean {
    const itemMappings = this.pdfData?.fieldMapping?.itemMappings ?? [];
    const labels = itemMappings
      .filter(mapping => mapping?.targetField === 'rate' || mapping?.targetField === 'total')
      .map(mapping => String(mapping?.sourceLabel ?? '').trim().toLowerCase())
      .filter(Boolean);
    const explicitInclusiveLabel = labels.some(label =>
      label.includes('after tax') ||
      label.includes('incl. of tax') ||
      label.includes('incl of tax') ||
      label.includes('inclusive')
    );
    if (explicitInclusiveLabel) {
      return true;
    }

    const referenceBillTotal = this.referenceBillTotalForGstInference;
    if (referenceBillTotal <= 0 || this.rawGstOnTopTotal <= 0) {
      return false;
    }

    const subtotalWithTransport = Math.round((this.itemsSubtotal + this.transportCharge) * 100) / 100;
    const totalWithExtraGst = Math.round((subtotalWithTransport + this.rawGstOnTopTotal) * 100) / 100;
    const diffToInclusive = Math.abs(referenceBillTotal - subtotalWithTransport);
    const diffToExclusive = Math.abs(referenceBillTotal - totalWithExtraGst);

    return diffToInclusive <= 1 && diffToInclusive + 0.01 < diffToExclusive;
  }

  get billTotal(): number {
    const v = Number(this.billTotalControl.value ?? 0);
    return Number.isFinite(v) ? v : 0;
  }

  get billDiff(): number {
    return Math.round((this.billTotal - this.expectedBillTotal) * 100) / 100;
  }

  get unappliedAdditionalSpend(): number {
    return this.billDiff > 0.5 ? this.billDiff : 0;
  }

  get transportCharge(): number {
    const v = Number(this.transportChargeControl.value ?? 0);
    return Number.isFinite(v) && v > 0 ? v : 0;
  }

  get showAdditionalSpendField(): boolean {
    return this.transportCharge > 0 || this.unappliedAdditionalSpend > 0;
  }

  get shouldApplyTransportToInvoice(): boolean {
    return this.applyTransportToInvoiceControl.value && this.invoiceTransportCharge > 0;
  }

  get invoiceTransportCharge(): number {
    const value = Number(this.selectedOrder?.transportCharge ?? 0);
    return Number.isFinite(value) && value > 0 ? value : 0;
  }

  get previewInvoiceTotal(): number {
    const base = Number(this.sellPreview?.estimatedPrice ?? 0);
    const transport = this.shouldApplyTransportToInvoice ? this.invoiceTransportCharge : 0;
    return Math.round((base + transport) * 100) / 100;
  }

  get previewItemsCount(): number {
    const base = Number(this.sellPreview?.itemsCount ?? 0);
    return base + (this.shouldApplyTransportToInvoice ? 1 : 0);
  }

  get canProceedAsMismatchBill(): boolean {
    return this.transportCharge <= 0 && !this.billMatchesItems;
  }

  get expectedBillTotal(): number {
    return Math.round((this.itemsTotal + this.transportCharge) * 100) / 100;
  }

  private get rawGstOnTopTotal(): number {
    return this.orderLines.reduce((acc, line) => {
      const amount = Number(line.amount ?? 0);
      const gstPercent = Number(line.aas_gst_percent ?? 0);
      if (!Number.isFinite(amount) || amount <= 0 || !Number.isFinite(gstPercent) || gstPercent <= 0) {
        return acc;
      }
      return acc + (amount * gstPercent) / 100;
    }, 0);
  }

  private get referenceBillTotalForGstInference(): number {
    const entered = Number(this.billTotalControl.value ?? 0);
    if (Number.isFinite(entered) && entered > 0) {
      return entered;
    }
    const uploaded = Number(this.pdfData?.vendorBillTotal ?? 0);
    if (Number.isFinite(uploaded) && uploaded > 0) {
      return uploaded;
    }
    const selected = Number(this.selectedOrder?.billTotal ?? 0);
    if (Number.isFinite(selected) && selected > 0) {
      return selected;
    }
    return 0;
  }

  get missingParserSerials(): number[] {
    return Array.isArray(this.pdfData?.completeness?.missingSerials)
      ? (this.pdfData?.completeness?.missingSerials ?? [])
      : [];
  }

  get unresolvedMissingParserSerials(): number[] {
    if (!this.missingParserSerials.length) {
      return [];
    }
    const covered = new Set<number>();
    for (const line of this.orderLines) {
      const explicit = Number(line.source_serial ?? NaN);
      if (Number.isFinite(explicit) && explicit > 0) {
        covered.add(Math.round(explicit));
        continue;
      }
      const fromNote = this.extractMissingSerialFromParseNote(line.parse_note);
      if (fromNote !== null) {
        covered.add(fromNote);
      }
    }
    return this.missingParserSerials.filter(serial => !covered.has(serial));
  }

  get hasMissingParsedRows(): boolean {
    return this.unresolvedMissingParserSerials.length > 0;
  }

  get parserMissingRowsSummary(): string {
    return this.unresolvedMissingParserSerials.join(', ');
  }

  getOrderLineSerial(line: UiOrderLine, index: number): string {
    const explicit = Number(line.source_serial ?? NaN);
    if (Number.isFinite(explicit) && explicit > 0) {
      return String(Math.round(explicit));
    }
    const fromNote = this.extractMissingSerialFromParseNote(line.parse_note);
    if (fromNote !== null) {
      return String(fromNote);
    }
    return String(index + 1);
  }

  getManualItemMatches(query: string): ItemOption[] {
    const term = String(query ?? '').trim().toLowerCase();
    if (!term) {
      return this.itemOptions.slice(0, 12);
    }
    return this.itemOptions
      .filter(option => {
        const haystack = [
          option.name,
          option.code,
          option.category ?? '',
          option.unit ?? ''
        ]
          .join(' ')
          .toLowerCase();
        return haystack.includes(term);
      })
      .slice(0, 12);
  }

  selectManualItem(line: UiOrderLine, event: MatAutocompleteSelectedEvent): void {
    const selectedName = String(event.option.value ?? '').trim();
    line.item_name = selectedName;
    line.item_code = this.resolveItemCodeFromName(selectedName);
    if (!Number.isFinite(Number(line.aas_margin_percent)) || Number(line.aas_margin_percent) <= 0) {
      line.aas_margin_percent = this.resolveItemMarginPercent(selectedName);
    }
    this.recalcLine(line);
  }

  private loadItems(): void {
    this.isItemsLoading = true;
    this.itemsError = '';
    const sub = this.itemService
      .listItems()
      .pipe(finalize(() => (this.isItemsLoading = false)))
      .subscribe({
        next: items => {
          this.itemOptions = (items ?? [])
            .map(item => this.mapItemOption(item))
            .filter(option => !!option.id && !!option.name)
            .sort((left, right) => left.name.localeCompare(right.name));
        },
        error: err => {
          this.itemsError = formatUiError(err, 'Unable to load item list for manual recovery.');
        }
      });
    this.subscriptions.add(sub);
  }

  private suggestMissingItemName(serial?: number): string {
    if (!Number.isFinite(serial)) {
      return 'Manual recovery row';
    }
    const serialNumber = Number(serial);
    const context = (this.pdfData?.completeness?.missingSerialContexts ?? []).find(entry => entry?.serial === serialNumber);
    const candidates = [
      ...(context?.parserContext ?? []),
      ...(context?.camelotContext ?? [])
    ]
      .map(line => this.extractItemNameFromMissingContext(line, serialNumber))
      .filter((value): value is string => !!value);
    return candidates[0] ?? `Missing invoice row ${serialNumber}`;
  }

  private extractItemNameFromMissingContext(line: string, serial: number): string | null {
    const text = String(line ?? '').replace(/\s+/g, ' ').trim();
    if (!text) {
      return null;
    }
    const withoutSerial = text.replace(new RegExp(`^${serial}\\s+`), '').trim();
    if (!withoutSerial) {
      return null;
    }
    if (withoutSerial.includes('|')) {
      const pipeHead = withoutSerial.split('|')[0]?.trim();
      if (pipeHead) {
        return pipeHead;
      }
    }
    const segments = withoutSerial.split(/\s{2,}/).map(segment => segment.trim()).filter(Boolean);
    if (segments.length > 1) {
      return segments[0];
    }
    const beforeNumericTail = withoutSerial.match(/^(.*?)(?=\s+\d[\d,.]*(?:\s+\d[\d,.%]*)*$)/);
    const candidate = (beforeNumericTail?.[1] ?? withoutSerial).trim();
    return candidate || null;
  }

  private mapItemOption(item: Item): ItemOption {
    const code = String(item.item_code ?? item.name ?? '').trim();
    const name = String(item.item_name ?? item.item_code ?? item.name ?? '').trim();
    const marginPercent = Number(item.aas_margin_percent ?? NaN);
    return {
      id: code,
      code,
      name,
      category: String(item.item_group ?? '').trim(),
      unit: String(item.stock_uom ?? '').trim(),
      marginPercent: Number.isFinite(marginPercent) && marginPercent > 0 ? marginPercent : null
    };
  }

  private resolveItemCodeFromName(name: string): string {
    return this.findItemOption(name)?.code ?? '';
  }

  private resolveItemMarginPercent(name: string): number {
    return this.findItemOption(name)?.marginPercent ?? DEFAULT_ORDER_MARGIN_PERCENT;
  }

  private findItemOption(name: string): ItemOption | null {
    const text = String(name ?? '').trim();
    if (!text) {
      return null;
    }
    const exact = this.itemOptions.find(option =>
      option.name.localeCompare(text, undefined, { sensitivity: 'accent' }) === 0 ||
      option.code.localeCompare(text, undefined, { sensitivity: 'accent' }) === 0
    );
    if (exact) {
      return exact;
    }
    const matches = this.getManualItemMatches(text);
    return matches.length === 1 ? matches[0] : null;
  }

  getLineRateBeforeTax(line: UiOrderLine): number | null {
    return this.deriveLineRateBeforeTax(line);
  }

  getLineRateAfterTax(line: UiOrderLine): number | null {
    return this.deriveLineRateAfterTax(line);
  }

  private deriveLineRateBeforeTax(line: UiOrderLine): number | null {
    const explicit = Number(line.aas_rate_before_tax ?? NaN);
    if (Number.isFinite(explicit) && explicit >= 0) {
      return explicit;
    }
    const baseRate = Number(line.rate ?? 0);
    if (!Number.isFinite(baseRate) || baseRate <= 0) {
      return null;
    }
    const gstPercent = Number(line.aas_gst_percent ?? 0);
    if (this.gstIncludedInLineAmounts && Number.isFinite(gstPercent) && gstPercent > 0) {
      return Math.round((baseRate / (1 + gstPercent / 100)) * 100) / 100;
    }
    return Math.round(baseRate * 100) / 100;
  }

  private deriveLineRateAfterTax(line: UiOrderLine): number | null {
    const explicit = Number(line.aas_rate_after_tax ?? NaN);
    if (Number.isFinite(explicit) && explicit >= 0) {
      return explicit;
    }
    const baseRate = Number(line.rate ?? 0);
    if (!Number.isFinite(baseRate) || baseRate <= 0) {
      return null;
    }
    const gstPercent = Number(line.aas_gst_percent ?? 0);
    if (this.gstIncludedInLineAmounts) {
      return Math.round(baseRate * 100) / 100;
    }
    if (Number.isFinite(gstPercent) && gstPercent > 0) {
      return Math.round((baseRate * (1 + gstPercent / 100)) * 100) / 100;
    }
    return Math.round(baseRate * 100) / 100;
  }

  private hydrateOrderLines(rows: any[], extractedSerials: number[] = []): UiOrderLine[] {
    const fallbackSerials = this.orderLines.map(line => Number(line.source_serial ?? NaN));
    const fallbackRatesBeforeTax = this.orderLines.map(line => Number(line.aas_rate_before_tax ?? NaN));
    const fallbackRatesAfterTax = this.orderLines.map(line => Number(line.aas_rate_after_tax ?? NaN));
    return (rows ?? [])
      .map((row: any, index: number) => {
        const line = this.toUiOrderLine(row);
        const explicitSerial = Number(extractedSerials[index] ?? NaN);
        const carriedSerial = Number(fallbackSerials[index] ?? NaN);
        line.source_serial = Number.isFinite(explicitSerial) && explicitSerial > 0
          ? explicitSerial
          : Number.isFinite(carriedSerial) && carriedSerial > 0
            ? carriedSerial
            : this.extractMissingSerialFromParseNote(line.parse_note);
        const carriedBeforeTax = Number(fallbackRatesBeforeTax[index] ?? NaN);
        const carriedAfterTax = Number(fallbackRatesAfterTax[index] ?? NaN);
        line.aas_rate_before_tax = line.aas_rate_before_tax ?? (Number.isFinite(carriedBeforeTax) ? carriedBeforeTax : null);
        line.aas_rate_after_tax = line.aas_rate_after_tax ?? (Number.isFinite(carriedAfterTax) ? carriedAfterTax : null);
        return line;
      })
      .filter((row: UiOrderLine) => row.item_code && row.item_code !== BRANCH_IMAGE_PLACEHOLDER_ITEM_CODE);
  }

  private extractMissingSerialFromParseNote(note: string | null | undefined): number | null {
    const text = String(note ?? '').trim();
    if (!text) {
      return null;
    }
    const match = text.match(/invoice row\s+(\d+)/i);
    if (!match) {
      return null;
    }
    const serial = Number(match[1]);
    return Number.isFinite(serial) && serial > 0 ? serial : null;
  }

  get billMatchesItems(): boolean {
    // Treat sub-1 differences as round-off.
    return Math.abs(this.billDiff) < 1;
  }

  get billValidationMessage(): string {
    if (!this.orderLines.length) {
      return '';
    }
    if (this.hasMissingParsedRows) {
      return `Invoice parser missed serial row(s): ${this.parserMissingRowsSummary}. Add a manual recovery row or re-upload before bill capture.`;
    }
    const total = this.billTotal;
    if (total <= 0) {
      return '';
    }
    if (this.billMatchesItems) {
      return '';
    }
    return `Bill total must match items total, GST, and transport or additional spend. Diff: ${this.billDiff.toFixed(2)}. Differences below 1.00 are saved as round off.`;
  }

  applyItemsTotalToBill(): void {
    this.billTotalControl.setValue(this.expectedBillTotal);
    this.updateBillMismatchError();
  }

  applyDiffAsAdditionalSpend(): void {
    if (this.unappliedAdditionalSpend <= 0) {
      return;
    }
    this.transportChargeControl.setValue(
      Math.round((this.transportCharge + this.unappliedAdditionalSpend) * 100) / 100
    );
    this.updateBillMismatchError();
  }

  saveOrderLines(): void {
    if (!this.selectedOrder) {
      return;
    }
    const payload = this.buildOrderItemsPayload();
    if (!payload) {
      return;
    }
    this.isItemsSaving = true;
    this.errorMessage = '';
    this.orderService
      .updateOrderItems(this.selectedOrder.name, payload)
      .pipe(finalize(() => (this.isItemsSaving = false)))
      .subscribe({
        next: res => {
          this.applySavedOrderLines(res, true);
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
      !this.hasMissingParsedRows &&
      total > 0 &&
      Boolean(ref) &&
      Boolean(date) &&
      (this.billMatchesItems || (this.canProceedAsMismatchBill && this.mismatchOverrideControl.value))
    );
  }

  captureBill(): void {
    if (!this.selectedOrder) {
      return;
    }
    if (this.hasMissingParsedRows) {
      this.errorMessage = this.billValidationMessage;
      return;
    }
    if (!this.billMatchesItems && !(this.canProceedAsMismatchBill && this.mismatchOverrideControl.value)) {
      this.errorMessage = this.billValidationMessage || 'Bill total must match items total plus transport or additional spend.';
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
        vendor_bill_date: this.formatDate(date),
        transport_charge: this.transportCharge,
        allow_mismatch: this.canProceedAsMismatchBill && this.mismatchOverrideControl.value
      })
      .pipe(finalize(() => this.loadOrders()))
      .subscribe({
        next: () => {
          this.selectedOrder = {
            ...this.selectedOrder!,
            status: 'VENDOR_BILL_CAPTURED',
            billTotal: total,
            transportCharge: this.transportCharge,
            billRef: ref,
            billDate: date
          };
          this.applyTransportToInvoiceControl.setValue(this.transportCharge > 0);
        },
        error: err => (this.errorMessage = this.formatError(err, 'Unable to capture vendor bill'))
      });
  }

  private updateBillMismatchError(): void {
    const ctrl = this.billTotalControl;
    const total = this.billTotal;
    const shouldValidate = this.orderLines.length > 0 && total > 0;
    const mismatch = shouldValidate
      && !this.billMatchesItems
      && !(this.canProceedAsMismatchBill && this.mismatchOverrideControl.value);
    const missingSequence = this.hasMissingParsedRows;

    const current = ctrl.errors ?? {};
    const nextErrors: Record<string, true> = {};
    if (mismatch) {
      nextErrors['mismatch'] = true;
    }
    if (missingSequence) {
      nextErrors['missingSequence'] = true;
    }
    if (Object.keys(nextErrors).length) {
      ctrl.setErrors({ ...current, ...nextErrors });
      return;
    }
    if (current['mismatch'] || current['missingSequence']) {
      const { mismatch: _ignored, missingSequence: _ignoredSequence, ...rest } = current as any;
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
    const saveRequest: Observable<Record<string, unknown> | null> = this.orderLines.length
      ? this.persistOrderLinesBeforeSellOrder()
      : of(null);
    saveRequest
      .pipe(
        switchMap(() =>
          this.orderService.createSellOrder(this.selectedOrder!.name, {
            apply_transport_to_invoice: this.shouldApplyTransportToInvoice
          })
        ),
        finalize(() => {
          this.isItemsSaving = false;
          this.loadOrders();
        })
      )
      .subscribe({
        next: () => {
          this.selectedOrder = { ...this.selectedOrder!, status: 'SELL_ORDER_CREATED' };
        },
        error: err => (this.errorMessage = this.formatError(err, 'Unable to create sell order'))
      });
  }

  replaceSellOrder(): void {
    if (!this.selectedOrder) {
      return;
    }
    this.errorMessage = '';
    this.isItemsSaving = true;
    this.orderService
      .replaceSellOrder(this.selectedOrder.name, {
        apply_transport_to_invoice: this.shouldApplyTransportToInvoice
      })
      .pipe(finalize(() => (this.isItemsSaving = false)))
      .subscribe({
        next: () => {
          this.selectedOrder = { ...this.selectedOrder!, status: 'SELL_ORDER_CREATED' };
          this.loadOrders();
        },
        error: err => (this.errorMessage = this.formatError(err, 'Unable to update sell order'))
      });
  }

  private persistOrderLinesBeforeSellOrder(): Observable<Record<string, unknown>> {
    if (!this.selectedOrder) {
      return of({});
    }
    const payload = this.buildOrderItemsPayload();
    if (!payload) {
      throw new Error(this.errorMessage || 'Unable to save review changes before creating sell order.');
    }
    this.isItemsSaving = true;
    return this.orderService.updateOrderItems(this.selectedOrder.name, payload).pipe(
      finalize(() => (this.isItemsSaving = false)),
      switchMap(res => {
        this.applySavedOrderLines(res, false);
        return of(res);
      })
    );
  }

  private buildOrderItemsPayload(): OrderItemPayload[] | null {
    if (!this.orderLines.length) {
      this.errorMessage = 'At least one item line is required.';
      return null;
    }
    const invalidCode = this.orderLines.find(line => {
      const itemCode = String(line.item_code ?? '').trim();
      if (itemCode) {
        return false;
      }
      return !line.manual_entry || !String(line.item_name ?? '').trim();
    });
    if (invalidCode) {
      this.errorMessage = 'Every row must include an item code or a manual recovery item name before saving.';
      return null;
    }
    const invalidMargin = this.orderLines.some(line => !Number.isFinite(Number(line.aas_margin_percent)) || Number(line.aas_margin_percent) < 0);
    if (invalidMargin) {
      this.errorMessage = 'Margin must be a non-negative number for every item.';
      return null;
    }
    const invalidMrp = this.orderLines.find(line => this.hasMrpViolation(line));
    if (invalidMrp) {
      this.errorMessage = `Vendor rate exceeds MRP for ${invalidMrp.item_name || invalidMrp.item_code}.`;
      return null;
    }
    return this.orderLines.map(line => ({
      item_code: line.item_code,
      item_name: String(line.item_name ?? '').trim() || undefined,
      display_description: String(line.display_description ?? '').trim() || undefined,
      qty: Number(line.qty ?? 0),
      rate: Number(line.rate ?? 0),
      aas_margin_percent: Number(line.aas_margin_percent ?? 0),
      aas_mrp: Number(line.aas_mrp ?? 0) || undefined,
      aas_gst_percent: Number(line.aas_gst_percent ?? 0) || undefined,
      manual_entry: !!line.manual_entry,
      parse_note: String(line.parse_note ?? '').trim() || undefined
    }));
  }

  private applySavedOrderLines(res: Record<string, unknown>, showSuccessMessage: boolean): void {
    const items = (res as any)?.items ?? [];
    if (Array.isArray(items) && items.length) {
      this.orderLines = this.hydrateOrderLines(items);
    }
    this.updateBillMismatchError();
    if (showSuccessMessage) {
      this.snackBar.open('Order review updated.', 'Dismiss', { duration: 2500 });
    }
    if (this.billTotalControl.value === null || this.billTotalControl.value === 0 || this.billMatchesItems) {
      this.applyItemsTotalToBill();
    }
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
      this.transportChargeControl.setValue(updated.transportCharge ?? 0);
      this.applyTransportToInvoiceControl.setValue((updated.transportCharge ?? 0) > 0);
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
    const displayId = String(order.title ?? '').trim() || name;
    const branch = String(order.customer ?? '').trim() || 'Unknown';
    const vendor = String(order.aas_vendor ?? '').trim();
    const status = this.normalizeStatus(order);
    const currency = this.resolveCurrency(order);

    const billTotal = order.aas_vendor_bill_total === undefined ? null : Number(order.aas_vendor_bill_total);
    const transportCharge = Number(order.aas_transport_charge ?? 0);
    const roundingAdjustment = Number(order.aas_rounding_adjustment ?? 0);
    const billRef = String(order.aas_vendor_bill_ref ?? '');
    const billDate = this.parseDate(order.aas_vendor_bill_date);

    return {
      name,
      displayId,
      branch,
      vendor,
      status,
      currency,
      billTotal,
      transportCharge: Number.isFinite(transportCharge) && transportCharge > 0 ? transportCharge : 0,
      roundingAdjustment: Number.isFinite(roundingAdjustment) ? Math.round(roundingAdjustment * 100) / 100 : 0,
      billRef,
      billDate,
      raw: order
    };
  }

  private normalizeCategory(value: string): string {
    return String(value ?? '').trim().toLowerCase();
  }

  private toUiOrderLine(row: any): UiOrderLine {
    const qty = Number(row?.qty ?? 0);
    const sourceRate = Number(row?.rate ?? 0);
    const vendorRate = Number(row?.aas_vendor_rate ?? sourceRate);
    const effectiveRate = Number.isFinite(vendorRate) && vendorRate > 0 ? vendorRate : sourceRate;
    const amount = Number(row?.amount ?? qty * effectiveRate);
    const margin = Number(row?.aas_margin_percent ?? 0);
    const normalizedVendorRate = Number.isFinite(vendorRate) && vendorRate > 0 ? vendorRate : effectiveRate;
    const rateBeforeTax = Number(row?.aas_rate_before_tax ?? NaN);
    const rateAfterTax = Number(row?.aas_rate_after_tax ?? NaN);
    const mrp = Number(row?.aas_mrp ?? 0);
    const gst = Number(row?.aas_gst_percent ?? 0);
    const description = String(row?.description ?? '').trim();
    const displayDescription = String(row?.display_description ?? this.stripAasLineMeta(description)).trim();
    const parseNote = String(row?.parse_note ?? this.extractParseNote(description)).trim() || null;
    const line: UiOrderLine = {
      source_serial: null,
      item_code: String(row?.item_code ?? '').trim(),
      item_name: String(row?.item_name ?? row?.item_code ?? '').trim(),
      display_description: displayDescription || String(row?.item_name ?? row?.item_code ?? '').trim(),
      qty: Number.isFinite(qty) ? qty : 0,
      rate: Number.isFinite(effectiveRate) ? effectiveRate : 0,
      amount: Number.isFinite(amount) ? amount : 0,
      aas_margin_percent: Number.isFinite(margin) && margin >= 0 ? margin : 0,
      aas_vendor_rate: Number.isFinite(normalizedVendorRate) && normalizedVendorRate > 0 ? normalizedVendorRate : null,
      aas_rate_before_tax: Number.isFinite(rateBeforeTax) && rateBeforeTax >= 0 ? rateBeforeTax : null,
      aas_rate_after_tax: Number.isFinite(rateAfterTax) && rateAfterTax >= 0 ? rateAfterTax : null,
      aas_mrp: Number.isFinite(mrp) && mrp > 0 ? mrp : null,
      aas_gst_percent: Number.isFinite(gst) && gst >= 0 ? gst : null,
      manual_entry: !!row?.manual_entry || description.includes('[AAS_MANUAL_ENTRY]'),
      parse_note: parseNote,
      mrpApplied: false
    };
    line.aas_rate_before_tax = this.deriveLineRateBeforeTax(line);
    line.aas_rate_after_tax = this.deriveLineRateAfterTax(line);
    line.mrpApplied = this.isMrpCapApplied(line);
    return line;
  }

  get availableDescriptionCleanupTokens(): readonly string[] {
    return ORDER_DESCRIPTION_CLEANUP_TOKENS;
  }

  get activeDescriptionRuleLabels(): string[] {
    const rules: string[] = [];
    this.selectedDescriptionCleanupTokens.forEach(token => rules.push(`Remove ${token}`));
    const bulkTerms = this.getBulkRemoveTerms();
    if (bulkTerms.length) {
      rules.push(`Remove: ${bulkTerms.join(', ')}`);
    }
    const replaceFrom = this.descriptionReplaceFrom.trim();
    if (replaceFrom) {
      rules.push(`Replace "${replaceFrom}" with "${this.descriptionReplaceTo.trim()}"`);
    }
    return rules;
  }

  isDescriptionCleanupTokenSelected(token: string): boolean {
    return this.selectedDescriptionCleanupTokens.has(token);
  }

  toggleDescriptionCleanupToken(token: string): void {
    if (this.selectedDescriptionCleanupTokens.has(token)) {
      this.selectedDescriptionCleanupTokens.delete(token);
      return;
    }
    this.selectedDescriptionCleanupTokens.add(token);
  }

  applyDescriptionToolsToAll(): void {
    const selectedTokens = Array.from(this.selectedDescriptionCleanupTokens);
    const bulkTerms = this.getBulkRemoveTerms();
    const replaceFrom = this.descriptionReplaceFrom.trim();
    const replaceTo = this.descriptionReplaceTo.trim();

    this.orderLines = this.orderLines.map(line => {
      let text = String(line.display_description ?? line.item_name ?? '').trim();
      selectedTokens.forEach(token => {
        text = this.removeTokenFromText(text, token);
      });
      bulkTerms.forEach(term => {
        text = this.removeTokenFromText(text, term);
      });
      if (replaceFrom) {
        text = text.replace(new RegExp(this.escapeRegExp(replaceFrom), 'gi'), replaceTo);
      }
      text = this.normalizeDescriptionText(text);
      return {
        ...line,
        display_description: text || line.item_name
      };
    });
  }

  applyTitleCaseToAllDescriptions(): void {
    this.orderLines = this.orderLines.map(line => {
      const text = this.toTitleCase(String(line.display_description ?? line.item_name ?? '').trim());
      return {
        ...line,
        display_description: text || line.item_name
      };
    });
  }

  private getBulkRemoveTerms(): string[] {
    return this.descriptionBulkRemoveText
      .split(/[,\n]/)
      .map(term => term.trim())
      .filter(Boolean);
  }

  private removeTokenFromText(text: string, token: string): string {
    const pattern = new RegExp(`(^|\\s+)${this.escapeRegExp(token)}(?=\\s+|$)`, 'gi');
    return String(text ?? '').replace(pattern, ' ');
  }

  private normalizeDescriptionText(text: string): string {
    return String(text ?? '')
      .replace(/\s{2,}/g, ' ')
      .replace(/\s+([,./-])/g, '$1')
      .trim();
  }

  private toTitleCase(text: string): string {
    return String(text ?? '')
      .toLowerCase()
      .replace(/\b([a-z])/g, (_, char: string) => char.toUpperCase())
      .trim();
  }

  private isMrpCapApplied(line: UiOrderLine): boolean {
    const mrp = Number(line.aas_mrp ?? 0);
    const vendorRate = Number(line.aas_vendor_rate ?? line.rate ?? 0);
    const margin = Number(line.aas_margin_percent ?? 0);
    if (!Number.isFinite(mrp) || mrp <= 0 || !Number.isFinite(vendorRate) || vendorRate <= 0) {
      return false;
    }
    const requestedSellRate = Math.round(vendorRate * (1 + Math.max(margin, 0) / 100) * 100) / 100;
    return requestedSellRate >= mrp && vendorRate < mrp && margin > 0;
  }

  private hasMrpViolation(line: UiOrderLine): boolean {
    const mrp = Number(line.aas_mrp ?? 0);
    const vendorRate = Number(line.aas_vendor_rate ?? line.rate ?? 0);
    return Number.isFinite(mrp) && mrp > 0 && Number.isFinite(vendorRate) && vendorRate > mrp;
  }

  get branchImages(): OrderBranchImage[] {
    const images = this.selectedOrder?.raw?.branch_images;
    return Array.isArray(images) ? images : [];
  }

  get vendorPdfUrl(): string {
    return String(this.selectedOrder?.raw?.aas_vendor_pdf ?? '').trim();
  }

  private extractParseNote(description: string): string {
    const match = description.match(/\[AAS_PARSE_NOTE\]\s*(.+)$/m);
    return match?.[1]?.trim() ?? '';
  }

  private stripAasLineMeta(description: string): string {
    return String(description ?? '')
      .split('\n')
      .map(line => line.trim())
      .filter(line => line && !line.startsWith('[AAS_MANUAL_ENTRY]') && !line.startsWith('[AAS_PARSE_NOTE]'))
      .join(' ')
      .trim();
  }

  private escapeRegExp(value: string): string {
    return value.replace(/[.*+?^${}()|[\]\\]/g, '\\$&');
  }

  get hasBranchImages(): boolean {
    return this.branchImages.length > 0;
  }

  get vendorPdfName(): string {
    return this.attachmentName(this.vendorPdfUrl, 'Vendor PDF');
  }

  openBranchImages(): void {
    if (!this.selectedOrder || !this.branchImages.length) {
      return;
    }
    this.dialog.open(OrderBranchImageGalleryDialogComponent, {
      width: '960px',
      maxWidth: '96vw',
      data: {
        orderId: this.selectedOrder.displayId,
        images: this.branchImages
      }
    });
  }

  openVendorPdf(): void {
    if (!this.selectedOrder || !this.vendorPdfUrl) {
      return;
    }
    this.orderService.downloadVendorPdfFile(this.selectedOrder.name).subscribe({
      next: blob => {
        const objectUrl = window.URL.createObjectURL(blob);
        window.open(objectUrl, '_blank', 'noopener,noreferrer');
        window.setTimeout(() => window.URL.revokeObjectURL(objectUrl), 60_000);
      },
      error: err => {
        this.errorMessage = this.formatError(err, 'Unable to open file');
      }
    });
  }

  downloadBranchImages(): void {
    if (!this.selectedOrder || !this.branchImages.length) {
      return;
    }
    this.orderService.downloadBranchImagesZip(this.selectedOrder.name).subscribe({
      next: blob => {
        this.saveBlob(blob, `${this.selectedOrder?.name ?? 'order'}-branch-images.zip`);
      },
      error: err => {
        this.errorMessage = this.formatError(err, 'Unable to download branch images');
      }
    });
  }

  downloadVendorPdf(): void {
    if (!this.selectedOrder || !this.vendorPdfUrl) {
      return;
    }
    this.orderService.downloadVendorPdfFile(this.selectedOrder.name).subscribe({
      next: blob => {
        this.saveBlob(blob, this.vendorPdfName);
      },
      error: err => {
        this.errorMessage = this.formatError(err, 'Unable to download file');
      }
    });
  }

  private saveBlob(blob: Blob, fileName: string): void {
    const objectUrl = window.URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = objectUrl;
    link.download = fileName;
    link.click();
    window.setTimeout(() => window.URL.revokeObjectURL(objectUrl), 60_000);
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

  private attachmentName(url: string, fallback: string): string {
    const value = String(url ?? '').trim();
    if (!value) {
      return fallback;
    }
    const clean = value.split('?')[0];
    const parts = clean.split('/');
    return parts[parts.length - 1] || fallback;
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
    return formatUiError(err, fallback);
  }
}
