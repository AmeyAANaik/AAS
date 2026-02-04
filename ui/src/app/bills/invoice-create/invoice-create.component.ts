import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { finalize } from 'rxjs/operators';
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

  mode: 'order' | 'manual' = 'order';
  statusMessage = '';
  isSubmitting = false;
  isLoadingOrder = false;
  orderSnapshot: OrderSnapshot | null = null;

  orderForm: FormGroup = this.fb.group({
    orderId: ['', Validators.required]
  });

  manualForm: FormGroup = this.fb.group({
    customer: ['', Validators.required],
    company: ['aas', Validators.required],
    itemCode: ['', Validators.required],
    qty: [1, [Validators.required, Validators.min(1)]],
    rate: [0, [Validators.required, Validators.min(0)]]
  });

  constructor(private fb: FormBuilder, private billsService: BillsService) {}

  setMode(mode: 'order' | 'manual'): void {
    this.mode = mode;
    this.statusMessage = '';
  }

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
          this.statusMessage = order ? 'Order loaded.' : 'Order not found.';
        },
        error: err => {
          this.statusMessage = this.formatError(err, 'Unable to load order');
        }
      });
  }

  submit(): void {
    if (this.mode === 'order') {
      this.submitFromOrder();
      return;
    }
    this.submitManual();
  }

  private submitFromOrder(): void {
    if (!this.orderSnapshot) {
      this.statusMessage = 'Load an order to continue.';
      return;
    }
    const customer = String(this.orderSnapshot.customer ?? '').trim();
    const company = String(this.orderSnapshot.company ?? 'aas').trim();
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
      }))
    };
    this.createInvoice(payload, customer);
  }

  private submitManual(): void {
    if (this.manualForm.invalid) {
      this.manualForm.markAllAsTouched();
      return;
    }
    const formValue = this.manualForm.getRawValue();
    const payload: InvoiceCreatePayload = {
      customer: String(formValue.customer ?? '').trim(),
      company: String(formValue.company ?? 'aas').trim(),
      items: [
        {
          item_code: String(formValue.itemCode ?? ''),
          qty: Number(formValue.qty ?? 0),
          rate: Number(formValue.rate ?? 0)
        }
      ]
    };
    this.createInvoice(payload, payload.customer);
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
    this.manualForm.reset({
      customer: '',
      company: 'aas',
      itemCode: '',
      qty: 1,
      rate: 0
    });
    this.orderSnapshot = null;
  }

  get orderSummary(): string {
    if (!this.orderSnapshot) {
      return 'No order loaded.';
    }
    const itemCount = Array.isArray(this.orderSnapshot.items) ? this.orderSnapshot.items.length : 0;
    return `${this.orderSnapshot.customer ?? 'Unknown'} · ${itemCount} item(s)`;
  }

  get manualTotal(): number {
    const qty = Number(this.manualForm.get('qty')?.value || 0);
    const rate = Number(this.manualForm.get('rate')?.value || 0);
    return qty * rate;
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
