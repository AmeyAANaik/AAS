import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup } from '@angular/forms';
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
  OrderView
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
    status: ['Accepted']
  });

  orders: OrderView[] = [];
  shops: OrderOption[] = [];
  vendors: OrderOption[] = [];
  items: ItemOption[] = [];
  selectedOrder: OrderView | null = null;
  summary = { total: 0, pending: 0, inProgress: 0, delivered: 0 };
  statusMessage = '';
  isLoading = false;
  isSaving = false;

  readonly statuses: OrderStatus[] = ['Accepted', 'Preparing', 'Ready', 'Delivered'];

  constructor(
    private fb: FormBuilder,
    private orderService: OrderService,
    private branchService: BranchService,
    private vendorService: VendorService,
    private itemService: ItemService
  ) {
    const today = this.formatDate(new Date());
    this.filtersForm.patchValue({ from: today, to: today });
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
    this.assignForm.patchValue({ vendorId: order.vendor || '' });
    this.statusForm.patchValue({ status: order.status || 'Accepted' });
    this.statusMessage = '';
  }

  clearSelection(): void {
    this.selectedOrder = null;
    this.assignForm.reset({ vendorId: '' });
    this.statusForm.reset({ status: 'Accepted' });
    this.statusMessage = '';
  }

  assignVendor(): void {
    if (!this.selectedOrder) {
      this.statusMessage = 'Select an order to assign a vendor.';
      return;
    }
    if (!this.canAssignVendor(this.selectedOrder)) {
      this.statusMessage = 'Vendor assignment is locked for this order.';
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
        error: err => {
          this.statusMessage = this.formatError(err, 'Unable to assign vendor');
        }
      });
  }

  updateStatus(): void {
    if (!this.selectedOrder) {
      this.statusMessage = 'Select an order to update status.';
      return;
    }
    if (!this.canUpdateStatus(this.selectedOrder)) {
      this.statusMessage = 'Status updates are locked for this order.';
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
        error: err => {
          this.statusMessage = this.formatError(err, 'Unable to update status');
        }
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

  canAssignVendor(order: OrderView): boolean {
    return !order.isFinal && !order.isVendorAssigned;
  }

  canUpdateStatus(order: OrderView): boolean {
    return !order.isFinal;
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
    const isFinal = status === 'Delivered';
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
    return status || 'Pending';
  }

  private resolveStatusTone(status: OrderStatus): 'neutral' | 'success' | 'warning' | 'info' {
    if (status === 'Delivered') {
      return 'success';
    }
    if (status === 'Ready') {
      return 'info';
    }
    if (status === 'Preparing') {
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

  private buildSummary(orders: OrderView[]): { total: number; pending: number; inProgress: number; delivered: number } {
    const total = orders.length;
    const delivered = orders.filter(order => order.status === 'Delivered').length;
    const inProgress = orders.filter(order => ['Accepted', 'Preparing', 'Ready'].includes(String(order.status))).length;
    const pending = orders.filter(order => order.status === 'Pending').length;
    return { total, pending, inProgress, delivered };
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
    return fallback;
  }
}
