import { Component, EventEmitter, Input, OnChanges, OnDestroy, Output } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { of } from 'rxjs';
import { finalize, map, switchMap } from 'rxjs/operators';
import { ItemOption, OrderCreatePayload, OrderCreateResult, OrderOption } from '../order.model';
import { OrderService } from '../order.service';

@Component({
  selector: 'app-order-create',
  templateUrl: './order-create.component.html',
  styleUrl: './order-create.component.scss'
})
export class OrderCreateComponent implements OnChanges, OnDestroy {
  @Input() shops: OrderOption[] = [];
  @Input() items: ItemOption[] = [];
  @Output() created = new EventEmitter<OrderCreateResult>();

  statusMessage = '';
  isSubmitting = false;
  lineTotal = 0;
  pricingLabel = '';
  imageFile: File | null = null;
  imagePreviewUrl = '';

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

  imageGroup: FormGroup = this.fb.group({
    imageName: ['']
  });

  form: FormGroup = this.fb.group({
    details: this.detailsGroup,
    item: this.itemGroup,
    image: this.imageGroup
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
      .pipe(
        map(response => {
          const id = this.extractOrderId(response);
          if (!id) {
            throw new Error('Order created but ID missing.');
          }
          return { id, response };
        }),
        switchMap(({ id, response }) => {
          if (!this.imageFile) {
            return of({ id, response, uploaded: false });
          }
          this.statusMessage = 'Uploading order image...';
          return this.orderService.uploadOrderImage(id, this.imageFile).pipe(
            map(uploadResponse => ({ id, response, uploaded: true, uploadResponse }))
          );
        }),
        finalize(() => (this.isSubmitting = false))
      )
      .subscribe({
        next: result => {
          const id = result?.id ?? '';
          this.statusMessage = id
            ? result?.uploaded
              ? `Order created and image uploaded: ${id}`
              : `Order created: ${id}`
            : 'Order created.';
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

  ngOnDestroy(): void {
    this.revokePreviewUrl();
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

  get imageSelected(): boolean {
    return Boolean(this.imageFile);
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
    this.imageGroup.reset({ imageName: '' });
    this.imageFile = null;
    this.revokePreviewUrl();
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

  onImageSelected(event: Event): void {
    const input = event.target as HTMLInputElement | null;
    const file = input?.files?.[0];
    if (!file) {
      this.clearImage();
      return;
    }
    this.setImage(file);
  }

  generateSampleImage(): void {
    const timestamp = this.formatDate(new Date());
    const svg = `
      <svg xmlns="http://www.w3.org/2000/svg" width="800" height="500">
        <defs>
          <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stop-color="#f4f6f9"/>
            <stop offset="100%" stop-color="#d8e2f0"/>
          </linearGradient>
        </defs>
        <rect width="800" height="500" fill="url(#bg)"/>
        <rect x="60" y="60" width="680" height="380" fill="#ffffff" stroke="#1f2937" stroke-width="2"/>
        <text x="100" y="140" font-size="28" font-family="Arial" fill="#111827">Branch Order Image</text>
        <text x="100" y="190" font-size="18" font-family="Arial" fill="#374151">Generated: ${timestamp}</text>
        <text x="100" y="230" font-size="18" font-family="Arial" fill="#374151">Branch: ${
          this.detailsGroup.get('customer')?.value || 'Unassigned'
        }</text>
        <text x="100" y="270" font-size="18" font-family="Arial" fill="#374151">Item: ${
          this.selectedItem?.name || 'Unselected'
        }</text>
        <text x="100" y="310" font-size="18" font-family="Arial" fill="#374151">Qty: ${
          this.itemGroup.get('quantity')?.value || 0
        }</text>
      </svg>
    `;
    const blob = new Blob([svg.trim()], { type: 'image/svg+xml' });
    const file = new File([blob], `order-${timestamp}.svg`, { type: 'image/svg+xml' });
    this.setImage(file);
  }

  clearImage(): void {
    this.imageFile = null;
    this.imageGroup.patchValue({ imageName: '' });
    this.revokePreviewUrl();
  }

  private setImage(file: File): void {
    this.imageFile = file;
    this.imageGroup.patchValue({ imageName: file.name });
    this.revokePreviewUrl();
    this.imagePreviewUrl = URL.createObjectURL(file);
  }

  private revokePreviewUrl(): void {
    if (this.imagePreviewUrl) {
      URL.revokeObjectURL(this.imagePreviewUrl);
      this.imagePreviewUrl = '';
    }
  }

  private extractOrderId(response: unknown): string {
    const anyResponse = response as { name?: string; data?: { name?: string } } | null;
    return String(anyResponse?.name ?? anyResponse?.data?.name ?? '').trim();
  }
}
