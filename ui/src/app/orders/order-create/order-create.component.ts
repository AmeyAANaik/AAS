import { Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output } from '@angular/core';
import { Location } from '@angular/common';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Subscription } from 'rxjs';
import { finalize, map, switchMap } from 'rxjs/operators';
import { OrderCreateResult, OrderOption } from '../order.model';
import { OrderService } from '../order.service';
import { VendorService } from '../../vendors/vendor.service';

@Component({
  selector: 'app-order-create',
  templateUrl: './order-create.component.html',
  styleUrl: './order-create.component.scss'
})
export class OrderCreateComponent implements OnInit, OnChanges, OnDestroy {
  @Input() shops: OrderOption[] = [];
  @Input() vendors: OrderOption[] = [];
  @Output() created = new EventEmitter<OrderCreateResult>();

  statusMessage = '';
  isSubmitting = false;
  isShopsLoading = false;
  isVendorsLoading = false;
  isCompaniesLoading = false;
  shopsError = '';
  vendorsError = '';
  companiesError = '';
  imageFile: File | null = null;
  imagePreviewUrl = '';
  createdOrderId: string | null = null;
  companies: OrderOption[] = [];
  private subscriptions = new Subscription();

  detailsGroup: FormGroup = this.fb.group({
    customer: ['', Validators.required],
    vendor: ['', Validators.required],
    company: ['', Validators.required],
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

  constructor(
    private fb: FormBuilder,
    private orderService: OrderService,
    private vendorService: VendorService,
    private location: Location
  ) {
    this.setTodayDefaults();
  }

  ngOnInit(): void {
    this.loadCompanies();
    if (!this.shops?.length) {
      this.loadShops();
    }
    if (!this.vendors?.length) {
      this.loadVendors();
    }
  }

  ngOnChanges(): void {
    if (!this.vendors?.length && !this.isVendorsLoading) {
      this.loadVendors();
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
    let createdOrderId = '';
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
          createdOrderId = id;
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
          const fallback = createdOrderId
            ? `Order ${createdOrderId} was created, but vendor assignment failed.`
            : 'Unable to create order.';
          this.statusMessage = this.formatError(err, fallback);
        }
      });
  }

  clear(): void {
    this.resetForm(true);
  }

  goBack(): void {
    this.location.back();
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
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
      company: this.defaultCompanyId,
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
    const anyErr = err as { error?: unknown; message?: string } | null;
    const payload = anyErr?.error;
    if (payload && typeof payload === 'object') {
      const message = (payload as { message?: unknown; error?: unknown }).message
        ?? (payload as { message?: unknown; error?: unknown }).error;
      if (typeof message === 'string' && message.trim()) {
        return message.trim();
      }
    }
    if (err instanceof Error && err.message.trim()) {
      return err.message;
    }
    if (typeof err === 'string') {
      return err;
    }
    if (typeof anyErr?.message === 'string' && anyErr.message.trim()) {
      return anyErr.message.trim();
    }
    return fallback;
  }

  private loadCompanies(): void {
    this.isCompaniesLoading = true;
    this.companiesError = '';
    const sub = this.orderService
      .listCompanies()
      .pipe(finalize(() => (this.isCompaniesLoading = false)))
      .subscribe({
        next: companies => {
          this.companies = (companies ?? []).map(company => {
            const name = String(company?.name ?? '').trim();
            return { id: name, name };
          });
          this.detailsGroup.patchValue({ company: this.defaultCompanyId });
        },
        error: err => {
          this.companiesError = this.formatError(err, 'Unable to load companies');
          if (!this.detailsGroup.get('company')?.value) {
            this.detailsGroup.patchValue({ company: 'AAS' });
          }
        }
      });
    this.subscriptions.add(sub);
  }

  private loadShops(): void {
    this.isShopsLoading = true;
    this.shopsError = '';
    const sub = this.orderService
      .listBranches()
      .pipe(finalize(() => (this.isShopsLoading = false)))
      .subscribe({
        next: branches => {
          this.shops = (branches ?? []).map(branch => {
            const name = String(branch?.customer_name ?? branch?.name ?? '').trim();
            return { id: String(branch?.name ?? name), name: name || String(branch?.name ?? '') };
          });
        },
        error: err => {
          this.shopsError = this.formatError(err, 'Unable to load branches');
        }
      });
    this.subscriptions.add(sub);
  }

  private loadVendors(): void {
    this.isVendorsLoading = true;
    this.vendorsError = '';
    const sub = this.vendorService
      .listVendors()
      .pipe(finalize(() => (this.isVendorsLoading = false)))
      .subscribe({
        next: vendors => {
          this.vendors = (vendors ?? []).map(vendor => {
            const name = String(vendor?.supplier_name ?? vendor?.name ?? '').trim();
            return {
              id: String(vendor?.name ?? name),
              name: name || String(vendor?.name ?? ''),
              disabled: this.isDisabled(vendor?.disabled)
            };
          })
          .filter(vendor => !vendor.disabled)
          .map(({ id, name }) => ({ id, name }));
        },
        error: err => {
          this.vendorsError = this.formatError(err, 'Unable to load vendors');
        }
      });
    this.subscriptions.add(sub);
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

  get defaultCompanyId(): string {
    if (!this.companies.length) {
      return 'AAS';
    }
    return this.companies.find(company => company.id === 'AAS')?.id ?? this.companies[0].id;
  }

}
