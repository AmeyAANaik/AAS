import { Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { finalize } from 'rxjs/operators';
import { ItemOption, OrderCreatePayload, OrderCreateResult, OrderOption } from '../order.model';
import { OrderService } from '../order.service';

@Component({
  selector: 'app-order-create',
  templateUrl: './order-create.component.html',
  styleUrl: './order-create.component.scss'
})
export class OrderCreateComponent implements OnChanges {
  @Input() shops: OrderOption[] = [];
  @Input() items: ItemOption[] = [];
  @Output() created = new EventEmitter<OrderCreateResult>();

  statusMessage = '';
  isSubmitting = false;
  lineTotal = 0;
  pricingLabel = '';

  detailsGroup: FormGroup = this.fb.group({
    customer: ['', Validators.required],
    company: ['aas', Validators.required],
    orderDate: ['', Validators.required],
    deliveryDate: ['', Validators.required]
  });

  itemGroup: FormGroup = this.fb.group({
    itemId: ['', Validators.required],
    quantity: [1, [Validators.required, Validators.min(1)]],
    pricingVisible: [true],
    rate: [0, [Validators.min(0)]]
  });

  form: FormGroup = this.fb.group({
    details: this.detailsGroup,
    item: this.itemGroup
  });

  constructor(private fb: FormBuilder, private orderService: OrderService) {
    this.setTodayDefaults();
    this.registerPricingWatcher();
    this.registerLineTotalWatcher();
  }

  ngOnChanges(): void {
    if (!this.detailsGroup.get('company')?.value) {
      this.detailsGroup.patchValue({ company: 'aas' });
    }
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    const payload = this.buildPayload();
    this.isSubmitting = true;
    this.statusMessage = 'Creating order...';
    this.orderService
      .createOrder(payload)
      .pipe(finalize(() => (this.isSubmitting = false)))
      .subscribe({
        next: response => {
          const id = String((response as { name?: string; data?: { name?: string } })?.name ?? (response as any)?.data?.name ?? '').trim();
          this.statusMessage = id ? `Order created: ${id}` : 'Order created.';
          if (id) {
            this.created.emit({ id, customer: payload.customer });
          }
          this.resetForm();
        },
        error: err => {
          this.statusMessage = this.formatError(err, 'Unable to create order');
        }
      });
  }

  clear(): void {
    this.resetForm();
  }

  get selectedItem(): ItemOption | undefined {
    const itemId = String(this.itemGroup.get('itemId')?.value ?? '').trim();
    return this.items.find(item => item.code === itemId || item.id === itemId);
  }

  get pricingVisible(): boolean {
    return Boolean(this.itemGroup.get('pricingVisible')?.value);
  }

  get canSubmit(): boolean {
    return this.form.valid && !this.isSubmitting;
  }

  get pricingHint(): string {
    return this.pricingVisible ? 'Pricing visible' : 'Pricing pending';
  }

  private buildPayload(): OrderCreatePayload {
    const details = this.detailsGroup.getRawValue();
    const itemValues = this.itemGroup.getRawValue();
    const rate = this.pricingVisible ? Number(itemValues.rate || 0) : 0;
    return {
      customer: String(details.customer ?? '').trim(),
      company: String(details.company ?? '').trim(),
      transaction_date: String(details.orderDate ?? ''),
      delivery_date: String(details.deliveryDate ?? ''),
      items: [
        {
          item_code: String(itemValues.itemId ?? ''),
          qty: Number(itemValues.quantity || 0),
          rate
        }
      ]
    } as OrderCreatePayload;
  }

  private setTodayDefaults(): void {
    const today = this.formatDate(new Date());
    this.detailsGroup.patchValue({ orderDate: today, deliveryDate: today });
  }

  private registerPricingWatcher(): void {
    this.itemGroup.get('pricingVisible')?.valueChanges.subscribe(visible => {
      const rateControl = this.itemGroup.get('rate');
      if (!rateControl) {
        return;
      }
      if (visible) {
        rateControl.setValidators([Validators.required, Validators.min(0)]);
        if (rateControl.value === null) {
          rateControl.setValue(0);
        }
      } else {
        rateControl.clearValidators();
        rateControl.setValue(0, { emitEvent: false });
      }
      rateControl.updateValueAndValidity({ emitEvent: false });
      this.updateLineTotal();
    });
  }

  private registerLineTotalWatcher(): void {
    this.itemGroup.valueChanges.subscribe(() => this.updateLineTotal());
    this.updateLineTotal();
  }

  private updateLineTotal(): void {
    const quantity = Number(this.itemGroup.get('quantity')?.value || 0);
    const rate = this.pricingVisible ? Number(this.itemGroup.get('rate')?.value || 0) : 0;
    this.lineTotal = this.pricingVisible ? quantity * rate : 0;
    this.pricingLabel = this.pricingVisible ? 'Derived total' : 'Pricing pending';
  }

  private resetForm(): void {
    this.detailsGroup.reset({
      customer: '',
      company: 'aas',
      orderDate: this.formatDate(new Date()),
      deliveryDate: this.formatDate(new Date())
    });
    this.itemGroup.reset({
      itemId: '',
      quantity: 1,
      pricingVisible: true,
      rate: 0
    });
    this.statusMessage = '';
    this.lineTotal = 0;
    this.pricingLabel = '';
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
