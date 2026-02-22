import { Component, EventEmitter, Input, OnChanges, OnDestroy, Output } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { finalize, map, switchMap } from 'rxjs/operators';
import { OrderCreateResult, OrderOption } from '../order.model';
import { OrderService } from '../order.service';

@Component({
  selector: 'app-order-create',
  templateUrl: './order-create.component.html',
  styleUrl: './order-create.component.scss'
})
export class OrderCreateComponent implements OnChanges, OnDestroy {
  @Input() shops: OrderOption[] = [];
  @Input() vendors: OrderOption[] = [];
  @Output() created = new EventEmitter<OrderCreateResult>();

  statusMessage = '';
  isSubmitting = false;
  imageFile: File | null = null;
  imagePreviewUrl = '';
  createdOrderId: string | null = null;

  detailsGroup: FormGroup = this.fb.group({
    customer: ['', Validators.required],
    vendor: ['', Validators.required],
    company: ['AAS Core', Validators.required],
    orderDate: ['', Validators.required],
    deliveryDate: ['', Validators.required]
  });

  imageGroup: FormGroup = this.fb.group({
    imageName: ['']
  });

  form: FormGroup = this.fb.group({
    details: this.detailsGroup,
    image: this.imageGroup
  });

  constructor(private fb: FormBuilder, private orderService: OrderService) {
    this.setTodayDefaults();
  }

  ngOnChanges(): void {
    if (!this.detailsGroup.get('company')?.value) {
      this.detailsGroup.patchValue({ company: 'AAS Core' });
    }
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    this.isSubmitting = true;
    this.statusMessage = 'Creating order...';
    this.createdOrderId = null;
    if (!this.imageFile) {
      this.isSubmitting = false;
      this.statusMessage = 'Upload a branch image before creating the order.';
      return;
    }
    const details = this.detailsGroup.getRawValue();
    const vendorId = String(details.vendor ?? '').trim();
    this.orderService
      .createOrderFromBranchImage(this.imageFile, {
        customer: String(details.customer ?? '').trim(),
        company: String(details.company ?? '').trim(),
        transaction_date: String(details.orderDate ?? ''),
        delivery_date: String(details.deliveryDate ?? '')
      })
      .pipe(
        map(response => {
          const id = this.extractOrderId(response);
          if (!id) {
            throw new Error('Order created but ID missing.');
          }
          return { id, response };
        }),
        switchMap(({ id, response }) => {
          this.statusMessage = 'Assigning vendor...';
          return this.orderService.assignVendor(id, vendorId).pipe(
            map(assignResponse => ({ id, response, assignResponse }))
          );
        }),
        finalize(() => (this.isSubmitting = false))
      )
      .subscribe({
        next: result => {
          const id = result?.id ?? '';
          if (id) {
            this.createdOrderId = id;
            this.statusMessage = `Order created and vendor assigned: ${id}`;
            this.created.emit({
              id,
              customer: String(details.customer ?? '').trim(),
              transactionDate: String(details.orderDate ?? '')
            });
          } else {
            this.statusMessage = 'Order created.';
          }
          this.resetForm(false);
        },
        error: err => {
          this.statusMessage = this.formatError(err, 'Unable to create order');
        }
      });
  }

  clear(): void {
    this.resetForm(true);
  }

  ngOnDestroy(): void {
    this.revokePreviewUrl();
  }

  get canSubmit(): boolean {
    return this.form.valid && !this.isSubmitting;
  }

  get imageSelected(): boolean {
    return Boolean(this.imageFile);
  }

  private setTodayDefaults(): void {
    const today = this.formatDate(new Date());
    this.detailsGroup.patchValue({ orderDate: today, deliveryDate: today });
  }

  private resetForm(clearCreated: boolean): void {
    this.detailsGroup.reset({
      customer: '',
      vendor: '',
      company: 'AAS Core',
      orderDate: this.formatDate(new Date()),
      deliveryDate: this.formatDate(new Date())
    });
    this.imageGroup.reset({ imageName: '' });
    this.imageFile = null;
    this.revokePreviewUrl();
    if (clearCreated) {
      this.statusMessage = '';
      this.createdOrderId = null;
    }
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
        <text x="100" y="270" font-size="18" font-family="Arial" fill="#374151">Vendor: ${
          this.detailsGroup.get('vendor')?.value || 'Unassigned'
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
