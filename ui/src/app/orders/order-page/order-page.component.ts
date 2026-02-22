import { animate, style, transition, trigger } from '@angular/animations';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormControl } from '@angular/forms';
import { Subscription } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { VendorService } from '../../vendors/vendor.service';
import { OrderStatus, OrderSummary, SellPreview } from '../order.model';
import { OrderService } from '../order.service';

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
  items?: unknown[];
  [key: string]: unknown;
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
export class OrderPageComponent implements OnInit, OnDestroy {
  searchControl = new FormControl<string>('', { nonNullable: true });
  vendorControl = new FormControl<string | null>(null);

  billTotalControl = new FormControl<number | null>(null);
  billRefControl = new FormControl<string>('', { nonNullable: true });
  billDateControl = new FormControl<Date | null>(new Date());
  marginControl = new FormControl<number>(10, { nonNullable: true });

  orders: UiOrder[] = [];
  filteredOrders: UiOrder[] = [];
  selectedOrder: UiOrder | null = null;

  vendors: Record<string, string[]> = {};

  selectedFile: File | null = null;
  fileError = '';
  isUploading = false;
  pdfData: PdfParseResult | null = null;

  sellPreview: UiSellPreview | null = null;
  errorMessage = '';

  private subscriptions = new Subscription();

  constructor(
    private orderService: OrderService,
    private vendorService: VendorService
  ) {}

  ngOnInit(): void {
    this.loadVendors();
    this.loadOrders();
    this.subscriptions.add(
      this.searchControl.valueChanges.subscribe(() => this.applySearch())
    );
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
  }

  private loadVendors(): void {
    this.vendorService.listVendors().subscribe({
      next: vendors => {
        const names = (vendors ?? [])
          .map(vendor => String(vendor?.name ?? vendor?.supplier_name ?? '').trim())
          .filter(Boolean)
          .sort((a, b) => a.localeCompare(b));
        this.vendors = names.reduce<Record<string, string[]>>((acc, name) => {
          const key = name.slice(0, 1).toUpperCase();
          (acc[key] ||= []).push(name);
          return acc;
        }, {});
      }
    });
  }

  loadOrders(): void {
    this.errorMessage = '';
    this.orderService.listOrders({}).subscribe({
      next: orders => {
        this.orders = (orders ?? []).map(order => this.toUiOrder(order));
        this.applySearch();
        this.refreshSelection();
      },
      error: err => (this.errorMessage = this.formatError(err, 'Unable to load orders'))
    });
  }

  private applySearch(): void {
    const query = this.searchControl.value.trim().toLowerCase();
    if (!query) {
      this.filteredOrders = [...this.orders];
      return;
    }
    this.filteredOrders = this.orders.filter(order => {
      return (
        order.name.toLowerCase().includes(query) ||
        order.branch.toLowerCase().includes(query) ||
        order.vendor.toLowerCase().includes(query) ||
        String(order.status).toLowerCase().includes(query)
      );
    });
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

    this.billTotalControl.setValue(order.billTotal);
    this.billRefControl.setValue(order.billRef);
    this.billDateControl.setValue(order.billDate ?? new Date());
    this.marginControl.setValue(Number(order.raw.aas_margin_percent ?? 10));
  }

  closeManage(): void {
    this.selectedOrder = null;
    this.selectedFile = null;
    this.pdfData = null;
    this.sellPreview = null;
    this.errorMessage = '';
    this.fileError = '';
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
      .pipe(finalize(() => this.loadOrders()))
      .subscribe({
        next: () => {
          this.selectedOrder = { ...this.selectedOrder!, vendor: vendorId, status: 'VENDOR_ASSIGNED' };
        },
        error: err => (this.errorMessage = this.formatError(err, 'Unable to assign vendor'))
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
    this.orderService
      .uploadVendorPdf(this.selectedOrder.name, this.selectedFile)
      .pipe(finalize(() => (this.isUploading = false)))
      .subscribe({
        next: res => {
          this.pdfData = (res ?? null) as PdfParseResult | null;
          this.selectedFile = null;
          this.loadOrders();
        },
        error: err => {
          this.errorMessage = this.formatError(err, 'Unable to upload and parse vendor PDF');
        }
      });
  }

  isBillFormValid(): boolean {
    const total = Number(this.billTotalControl.value ?? 0);
    const ref = this.billRefControl.value.trim();
    const date = this.billDateControl.value;
    const margin = Number(this.marginControl.value ?? 0);
    return total > 0 && Boolean(ref) && Boolean(date) && Number.isFinite(margin) && margin >= 0;
  }

  captureBill(): void {
    if (!this.selectedOrder) {
      return;
    }
    if (!this.isBillFormValid()) {
      this.errorMessage = 'Fill in bill total, reference, date, and margin.';
      return;
    }
    const total = Number(this.billTotalControl.value ?? 0);
    const ref = this.billRefControl.value.trim();
    const date = this.billDateControl.value ?? new Date();
    const marginPercent = Number(this.marginControl.value ?? 10);
    this.errorMessage = '';
    this.orderService
      .captureVendorBill(this.selectedOrder.name, {
        vendor_bill_total: total,
        vendor_bill_ref: ref,
        vendor_bill_date: this.formatDate(date),
        margin_percent: marginPercent
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
    }
  }

  private toUiOrder(order: OrderSummary): UiOrder {
    const name = String(order.name ?? '').trim();
    const branch = String(order.customer ?? '').trim() || 'Unknown';
    const vendor = String(order.aas_vendor ?? '').trim();
    const status = this.normalizeStatus(order);

    const billTotal = order.aas_vendor_bill_total === undefined ? null : Number(order.aas_vendor_bill_total);
    const billRef = String(order.aas_vendor_bill_ref ?? '');
    const billDate = this.parseDate(order.aas_vendor_bill_date);

    return {
      name,
      branch,
      vendor,
      status,
      billTotal,
      billRef,
      billDate,
      raw: order
    };
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
