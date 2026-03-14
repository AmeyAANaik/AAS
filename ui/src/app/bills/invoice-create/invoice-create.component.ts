import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormArray, FormBuilder, FormControl, FormGroup, Validators } from '@angular/forms';
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
    company: ['', Validators.required],
    items: this.fb.array([this.createManualItemGroup()])
  });

  gstControl = new FormControl(true);

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

  addManualItem(): void {
    this.manualItems.push(this.createManualItemGroup());
  }

  removeManualItem(index: number): void {
    if (this.manualItems.length <= 1) {
      return;
    }
    this.manualItems.removeAt(index);
  }

  onManualCustomerChange(): void {
    const customerId = String(this.manualForm.get('customer')?.value ?? '').trim();
    const company = this.resolveCustomerCompany(customerId);
    this.manualForm.patchValue({ company }, { emitEvent: false });
  }

  get manualItems(): FormArray {
    return this.manualForm.get('items') as FormArray;
  }

  get manualItemGroups(): FormGroup[] {
    return this.manualItems.controls as FormGroup[];
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

  lineTotal(index: number): number {
    const group = this.manualItems.at(index) as FormGroup;
    const qty = Number(group.get('qty')?.value || 0);
    const rate = Number(group.get('rate')?.value || 0);
    return qty * rate;
  }

  get manualTotal(): number {
    return this.manualItems.controls.reduce((sum, _, index) => sum + this.lineTotal(index), 0);
  }

  get companyDisplay(): string {
    return String(this.manualForm.get('company')?.value ?? '').trim() || 'Select a branch to lock company';
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
      apply_gst: this.gstControl.value ?? true
    };
    this.createInvoice(payload, customer);
  }

  private submitManual(): void {
    if (this.manualForm.invalid) {
      this.manualForm.markAllAsTouched();
      return;
    }
    const formValue = this.manualForm.getRawValue();
    const company = String(formValue.company ?? '').trim();
    if (!company) {
      this.statusMessage = 'Select a branch with a mapped company.';
      return;
    }
    const items = (formValue.items ?? [])
      .map((item: { itemCode?: string; qty?: number; rate?: number }) => ({
        item_code: String(item.itemCode ?? '').trim(),
        qty: Number(item.qty ?? 0),
        rate: Number(item.rate ?? 0)
      }))
      .filter((item: { item_code: string; qty: number; rate: number }) => item.item_code && item.qty > 0);
    if (!items.length) {
      this.statusMessage = 'Add at least one invoice item.';
      return;
    }
    const payload: InvoiceCreatePayload = {
      customer: String(formValue.customer ?? '').trim(),
      company,
      items,
      apply_gst: this.gstControl.value ?? true
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
      company: '',
      items: []
    });
    this.manualItems.clear();
    this.manualItems.push(this.createManualItemGroup());
    this.orderSnapshot = null;
  }

  private createManualItemGroup(): FormGroup {
    return this.fb.group({
      itemCode: ['', Validators.required],
      qty: [1, [Validators.required, Validators.min(1)]],
      rate: [0, [Validators.required, Validators.min(0)]]
    });
  }

  private resolveCustomerCompany(customerId: string): string {
    return this.customers.find(customer => customer.id === customerId)?.company?.trim() ?? '';
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
