import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { finalize } from 'rxjs/operators';
import { BranchService } from '../../branches/branch.service';
import { ItemService } from '../../items/item.service';
import { VendorService } from '../../vendors/vendor.service';
import {
  ItemOption,
  OrderFilters,
  OrderOption,
  OrderStatus,
  OrderSummary,
  OrderView,
  SellPreview
} from '../order.model';
import { OrderService } from '../order.service';

@Component({
  selector: 'app-order-page',
  templateUrl: './order-page.component.html',
  styleUrl: './order-page.component.scss'
})
export class OrderPageComponent implements OnInit {
  filtersForm: FormGroup = this.fb.group({
    customer: [''],
    vendor: [''],
    status: [''],
    from: [''],
    to: ['']
  });

  assignForm: FormGroup = this.fb.group({
    vendorId: ['']
  });

  statusForm: FormGroup = this.fb.group({
    status: ['DRAFT']
  });

  vendorBillForm: FormGroup = this.fb.group({
    vendorBillTotal: [null, [Validators.required, Validators.min(0.01)]],
    vendorBillRef: [''],
    vendorBillDate: [''],
    marginPercent: [10, [Validators.required, Validators.min(0)]]
  });

  orders: OrderView[] = [];
  shops: OrderOption[] = [];
  vendors: OrderOption[] = [];
  items: ItemOption[] = [];
  selectedOrder: OrderView | null = null;
  selectedVendorPdf: File | null = null;
  sellPreview: SellPreview | null = null;

  summary = { total: 0, pending: 0, inProgress: 0, completed: 0 };
  statusMessage = '';
  isLoading = false;
  isSaving = false;
  isPreviewLoading = false;

  readonly statuses: OrderStatus[] = [
    'DRAFT',
    'VENDOR_ASSIGNED',
    'VENDOR_PDF_RECEIVED',
    'VENDOR_BILL_CAPTURED',
    'SELL_ORDER_CREATED',
    'INVOICED'
  ];

  readonly timelineSteps: OrderStatus[] = [
    'DRAFT',
    'VENDOR_ASSIGNED',
    'VENDOR_PDF_RECEIVED',
    'VENDOR_BILL_CAPTURED',
    'SELL_ORDER_CREATED'
  ];

  constructor(
    private fb: FormBuilder,
    private orderService: OrderService,
    private branchService: BranchService,
    private vendorService: VendorService,
    private itemService: ItemService
  ) {
    const today = this.formatDate(new Date());
    this.filtersForm.patchValue({ from: today, to: today });
    this.vendorBillForm.patchValue({ vendorBillDate: today });
  }

  ngOnInit(): void {
    this.loadReferenceData();
    this.loadOrders();
  }

  loadReferenceData(): void {
    this.branchService.listBranches().subscribe({
      next: branches => {
        this.shops = (branches ?? []).map(branch => {
          const name = String(branch.customer_name ?? branch.name ?? '').trim();
          return { id: String(branch.name ?? name), name: name || String(branch.name ?? '') };
        });
      }
    });

    this.vendorService.listVendors().subscribe({
      next: vendors => {
        this.vendors = (vendors ?? []).map(vendor => {
          const name = String(vendor.supplier_name ?? vendor.name ?? '').trim();
          return { id: String(vendor.name ?? name), name: name || String(vendor.name ?? '') };
        });
      }
    });

    this.itemService.listItems().subscribe({
      next: items => {
        this.items = (items ?? []).map(item => ({
          id: String(item.name ?? item.item_code ?? ''),
          name: String(item.item_name ?? item.name ?? ''),
          code: String(item.item_code ?? item.name ?? ''),
          category: String(item.item_group ?? ''),
          unit: String(item.stock_uom ?? '')
        }));
      }
    });
  }

  loadOrders(): void {
    this.isLoading = true;
    const filters = this.filtersForm.getRawValue() as OrderFilters;
    this.orderService
      .listOrders(filters)
      .pipe(finalize(() => (this.isLoading = false)))
      .subscribe({
        next: orders => {
          this.orders = (orders ?? []).map(order => this.toViewModel(order));
          this.summary = this.buildSummary(this.orders);
          this.syncSelection();
        },
        error: err => {
          this.statusMessage = this.formatError(err, 'Unable to load orders');
        }
      });
  }

  selectOrder(order: OrderView): void {
    this.selectedOrder = order;
    this.assignForm.patchValue({ vendorId: order.raw.aas_vendor || '' });
    this.statusForm.patchValue({ status: order.status || 'DRAFT' });
    this.vendorBillForm.patchValue({
      vendorBillTotal: order.raw.aas_vendor_bill_total ?? null,
      vendorBillRef: order.raw.aas_vendor_bill_ref ?? '',
      vendorBillDate: order.raw.aas_vendor_bill_date ?? this.formatDate(new Date()),
      marginPercent: order.raw.aas_margin_percent ?? 10
    });
    this.selectedVendorPdf = null;
    this.sellPreview = null;
    this.statusMessage = '';
  }

  assignVendor(): void {
    if (!this.selectedOrder) {
      this.statusMessage = 'Select an order to assign a vendor.';
      return;
    }
    const vendorId = String(this.assignForm.get('vendorId')?.value ?? '').trim();
    if (!vendorId) {
      this.statusMessage = 'Select a vendor to assign.';
      return;
    }
    this.isSaving = true;
    this.orderService
      .assignVendor(this.selectedOrder.id, vendorId)
      .pipe(finalize(() => (this.isSaving = false)))
      .subscribe({
        next: () => {
          this.statusMessage = 'Vendor assigned.';
          this.loadOrders();
        },
        error: err => (this.statusMessage = this.formatError(err, 'Unable to assign vendor'))
      });
  }

  updateStatus(): void {
    if (!this.selectedOrder) {
      this.statusMessage = 'Select an order to update status.';
      return;
    }
    const status = String(this.statusForm.get('status')?.value ?? '').trim();
    if (!status) {
      this.statusMessage = 'Select a status.';
      return;
    }
    this.isSaving = true;
    this.orderService
      .updateStatus(this.selectedOrder.id, status)
      .pipe(finalize(() => (this.isSaving = false)))
      .subscribe({
        next: () => {
          this.statusMessage = 'Status updated.';
          this.loadOrders();
        },
        error: err => (this.statusMessage = this.formatError(err, 'Unable to update status'))
      });
  }

  onVendorPdfSelected(event: Event): void {
    const input = event.target as HTMLInputElement | null;
    this.selectedVendorPdf = input?.files?.[0] ?? null;
  }

  uploadVendorPdf(): void {
    if (!this.selectedOrder) {
      this.statusMessage = 'Select an order first.';
      return;
    }
    if (!this.selectedVendorPdf) {
      this.statusMessage = 'Select a vendor PDF file first.';
      return;
    }
    this.isSaving = true;
    this.orderService
      .uploadVendorPdf(this.selectedOrder.id, this.selectedVendorPdf)
      .pipe(finalize(() => (this.isSaving = false)))
      .subscribe({
        next: () => {
          this.statusMessage = 'Vendor PDF uploaded and parsed.';
          this.selectedVendorPdf = null;
          this.loadOrders();
        },
        error: err => (this.statusMessage = this.formatError(err, 'Unable to upload vendor PDF'))
      });
  }

  captureVendorBill(): void {
    if (!this.selectedOrder) {
      this.statusMessage = 'Select an order first.';
      return;
    }
    if (this.vendorBillForm.invalid) {
      this.vendorBillForm.markAllAsTouched();
      return;
    }
    const value = this.vendorBillForm.getRawValue();
    this.isSaving = true;
    this.orderService
      .captureVendorBill(this.selectedOrder.id, {
        vendor_bill_total: Number(value.vendorBillTotal),
        vendor_bill_ref: String(value.vendorBillRef ?? ''),
        vendor_bill_date: String(value.vendorBillDate ?? ''),
        margin_percent: Number(value.marginPercent ?? 10)
      })
      .pipe(finalize(() => (this.isSaving = false)))
      .subscribe({
        next: () => {
          this.statusMessage = 'Vendor bill captured.';
          this.loadOrders();
          this.loadSellPreview();
        },
        error: err => (this.statusMessage = this.formatError(err, 'Unable to capture vendor bill'))
      });
  }

  loadSellPreview(): void {
    if (!this.selectedOrder) {
      return;
    }
    this.isPreviewLoading = true;
    this.orderService
      .getSellPreview(this.selectedOrder.id)
      .pipe(finalize(() => (this.isPreviewLoading = false)))
      .subscribe({
        next: preview => {
          this.sellPreview = preview;
          this.statusMessage = 'Sell preview calculated.';
        },
        error: err => {
          this.sellPreview = null;
          this.statusMessage = this.formatError(err, 'Unable to calculate sell preview');
        }
      });
  }

  createSellOrder(): void {
    if (!this.selectedOrder) {
      return;
    }
    this.isSaving = true;
    this.orderService
      .createSellOrder(this.selectedOrder.id)
      .pipe(finalize(() => (this.isSaving = false)))
      .subscribe({
        next: () => {
          this.statusMessage = 'Sell order created.';
          this.loadOrders();
          this.loadSellPreview();
        },
        error: err => (this.statusMessage = this.formatError(err, 'Unable to create sell order'))
      });
  }

  onOrderCreated(): void {
    this.loadOrders();
  }

  getStatusPillClass(order: OrderView): string {
    if (order.statusTone === 'success') {
      return 'pill pill-success';
    }
    if (order.statusTone === 'warning') {
      return 'pill pill-warning';
    }
    if (order.statusTone === 'info') {
      return 'pill pill-info';
    }
    return 'pill pill-neutral';
  }

  isStepDone(step: OrderStatus): boolean {
    if (!this.selectedOrder) {
      return false;
    }
    return this.timelineIndex(this.selectedOrder.status) >= this.timelineIndex(step);
  }

  canCaptureBill(): boolean {
    if (!this.selectedOrder) {
      return false;
    }
    return ['VENDOR_ASSIGNED', 'VENDOR_PDF_RECEIVED'].includes(String(this.selectedOrder.status));
  }

  canCreateSellOrder(): boolean {
    if (!this.selectedOrder) {
      return false;
    }
    return String(this.selectedOrder.status) === 'VENDOR_BILL_CAPTURED';
  }

  private timelineIndex(status: OrderStatus): number {
    return this.timelineSteps.findIndex(step => step === status);
  }

  private syncSelection(): void {
    if (!this.selectedOrder) {
      return;
    }
    const refreshed = this.orders.find(order => order.id === this.selectedOrder?.id) ?? null;
    this.selectedOrder = refreshed;
  }

  private toViewModel(order: OrderSummary): OrderView {
    const id = String(order.name ?? '').trim();
    const customer = String(order.customer ?? '').trim();
    const vendor = String(order.aas_vendor ?? '').trim();
    const status = this.resolveStatus(order);
    const isFinal = ['SELL_ORDER_CREATED', 'INVOICED', 'Delivered'].includes(String(status));
    return {
      id,
      customer: customer || 'Unknown',
      vendor: vendor || 'Unassigned',
      status,
      statusLabel: status || 'Pending',
      statusTone: this.resolveStatusTone(status),
      orderDate: String(order.transaction_date ?? ''),
      deliveryDate: String(order.delivery_date ?? ''),
      totalLabel: this.resolveTotalLabel(order.grand_total),
      isFinal,
      isVendorAssigned: Boolean(vendor),
      raw: order
    };
  }

  private resolveStatus(order: OrderSummary): OrderStatus {
    const status = String(order.aas_status ?? order.status ?? '').trim();
    return status || 'DRAFT';
  }

  private resolveStatusTone(status: OrderStatus): 'neutral' | 'success' | 'warning' | 'info' {
    if (status === 'SELL_ORDER_CREATED' || status === 'INVOICED' || status === 'Delivered') {
      return 'success';
    }
    if (status === 'VENDOR_BILL_CAPTURED' || status === 'Ready') {
      return 'info';
    }
    if (status === 'VENDOR_PDF_RECEIVED' || status === 'Preparing') {
      return 'warning';
    }
    return 'neutral';
  }

  private resolveTotalLabel(total: number | undefined): string {
    if (total === null || total === undefined) {
      return 'Pending';
    }
    const value = Number(total);
    if (!Number.isFinite(value) || value <= 0) {
      return 'Pending';
    }
    return value.toFixed(2);
  }

  private buildSummary(orders: OrderView[]): { total: number; pending: number; inProgress: number; completed: number } {
    const total = orders.length;
    const completed = orders.filter(order => ['SELL_ORDER_CREATED', 'INVOICED'].includes(String(order.status))).length;
    const inProgress = orders.filter(order =>
      ['VENDOR_ASSIGNED', 'VENDOR_PDF_RECEIVED', 'VENDOR_BILL_CAPTURED'].includes(String(order.status))
    ).length;
    const pending = orders.filter(order => order.status === 'DRAFT').length;
    return { total, pending, inProgress, completed };
  }

  private formatDate(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  private formatError(err: unknown, fallback: string): string {
    if (err instanceof Error) {
      return err.message;
    }
    if (typeof err === 'string') {
      return err;
    }
    const status = (err as { error?: { message?: string } } | null)?.error?.message;
    if (typeof status === 'string' && status.trim()) {
      return status;
    }
    return fallback;
  }
}
