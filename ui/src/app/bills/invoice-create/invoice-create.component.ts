import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormBuilder, FormControl, FormGroup, Validators } from '@angular/forms';
import { finalize } from 'rxjs/operators';
import { formatUiError } from '../../shared/error-message.util';
import { BillsService } from '../bills.service';
import {
  InvoiceCreatePayload,
  InvoiceCreateResult,
  ItemOption,
  OptionItem,
  OrderSnapshot
} from '../bills.model';

@Component({
  selector: 'app-invoice-create',
  templateUrl: './invoice-create.component.html',
  styleUrl: './invoice-create.component.scss'
})
export class InvoiceCreateComponent {
  @Input() customers: OptionItem[] = [];
  @Input() items: ItemOption[] = [];
  @Input() orders: OptionItem[] = [];
  @Output() created = new EventEmitter<InvoiceCreateResult>();

  statusMessage = '';
  isSubmitting = false;
  isLoadingOrder = false;
  orderSnapshot: OrderSnapshot | null = null;

  orderForm: FormGroup = this.fb.group({
    orderId: ['', Validators.required]
  });

  gstControl = new FormControl(true);
  roundingAdjustmentControl = new FormControl(0, { nonNullable: true });

  constructor(private fb: FormBuilder, private billsService: BillsService) {}

  loadOrder(): void {
    const orderId = String(this.orderForm.get('orderId')?.value ?? '').trim();
    if (!orderId) {
      this.statusMessage = 'Select an order to load.';
      return;
    }
    this.isLoadingOrder = true;
    this.statusMessage = 'Loading order details...';
    this.billsService
      .getOrderSnapshot(orderId)
      .pipe(finalize(() => (this.isLoadingOrder = false)))
      .subscribe({
        next: order => {
          this.orderSnapshot = order ?? null;
          this.roundingAdjustmentControl.setValue(Number(order?.aas_rounding_adjustment ?? 0) || 0, {
            emitEvent: false
          });
          this.statusMessage = order ? 'Order loaded.' : 'Order not found.';
        },
        error: err => {
          this.statusMessage = this.formatError(err, 'Unable to load order');
        }
      });
  }

  submit(): void {
    this.submitFromOrder();
  }

  get orderSummary(): string {
    if (!this.orderSnapshot) {
      return 'No order loaded.';
    }
    const itemCount = Array.isArray(this.orderSnapshot.items) ? this.orderSnapshot.items.length : 0;
    return `${this.orderSnapshot.customer ?? 'Unknown'} · ${itemCount} item(s)`;
  }

  get orderPayments(): Array<{ due_date?: string; payment_amount?: number; outstanding?: number }> {
    const schedule = this.orderSnapshot?.payment_schedule;
    if (!Array.isArray(schedule)) {
      return [];
    }
    return schedule.map(entry => ({
      due_date: entry?.due_date,
      payment_amount: Number(entry?.payment_amount ?? 0),
      outstanding: Number(entry?.outstanding ?? 0)
    }));
  }

  get orderOutstanding(): number | null {
    if (!this.orderSnapshot) {
      return null;
    }
    const payments = this.orderPayments;
    if (!payments.length) {
      return this.orderSnapshot.grand_total ?? null;
    }
    const outstanding = payments.reduce((sum, payment) => sum + (payment.outstanding ?? 0), 0);
    return Number.isFinite(outstanding) ? outstanding : null;
  }

  private submitFromOrder(): void {
    if (!this.orderSnapshot) {
      this.statusMessage = 'Load an order to continue.';
      return;
    }
    const customer = String(this.orderSnapshot.customer ?? '').trim();
    const company = String(this.orderSnapshot.company ?? '').trim();
    const items = Array.isArray(this.orderSnapshot.items) ? this.orderSnapshot.items : [];
    if (!customer || !company || !items.length) {
      this.statusMessage = 'Order is missing required invoice details.';
      return;
    }
    const payload: InvoiceCreatePayload = {
      customer,
      company,
      items: items.map(item => ({
        item_code: String(item.item_code ?? ''),
        qty: Number(item.qty ?? 0),
        rate: Number(item.rate ?? 0)
      })),
      apply_gst: this.gstControl.value ?? true,
      rounding_adjustment: this.roundingAdjustmentValue || undefined
    };
    this.createInvoice(payload, customer);
  }

  private createInvoice(payload: InvoiceCreatePayload, customer: string): void {
    this.isSubmitting = true;
    this.statusMessage = 'Creating invoice...';
    this.billsService
      .createInvoice(payload)
      .pipe(finalize(() => (this.isSubmitting = false)))
      .subscribe({
        next: response => {
          const id = String((response as { name?: string; data?: { name?: string } })?.name ?? (response as any)?.data?.name ?? '').trim();
          this.statusMessage = id ? `Invoice created: ${id}` : 'Invoice created.';
          if (id) {
            this.created.emit({ id, customer });
          }
          this.resetForms();
        },
        error: err => {
          this.statusMessage = this.formatError(err, 'Unable to create invoice');
        }
      });
  }

  resetForms(): void {
    this.orderForm.reset({ orderId: '' });
    this.orderSnapshot = null;
    this.roundingAdjustmentControl.setValue(0);
  }

  get roundingAdjustmentValue(): number {
    const value = Number(this.roundingAdjustmentControl.value ?? 0);
    return Number.isFinite(value) ? Math.round(value * 100) / 100 : 0;
  }

  private formatError(err: unknown, fallback: string): string {
    return formatUiError(err, fallback);
  }
}
