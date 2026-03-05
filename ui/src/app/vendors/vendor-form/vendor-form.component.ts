import { Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { VendorFormValue, VendorView } from '../vendor.model';

@Component({
  selector: 'app-vendor-form',
  templateUrl: './vendor-form.component.html',
  styleUrl: './vendor-form.component.scss'
})
export class VendorFormComponent implements OnChanges {
  @Input() vendor: VendorView | null = null;
  @Input() mode: 'create' | 'edit' = 'create';
  @Input() isSaving = false;
  @Input() statusMessage = '';
  @Input() canManageTemplates = true;
  @Output() save = new EventEmitter<VendorFormValue>();
  @Output() reset = new EventEmitter<void>();
  @Output() uploadTemplateSample = new EventEmitter<File>();
  @Output() clearTemplate = new EventEmitter<void>();

  form: FormGroup = this.fb.group({
    supplierName: ['', [Validators.required, Validators.maxLength(140)]],
    address: [''],
    phone: [''],
    gst: [''],
    pan: [''],
    foodLicenseNo: [''],
    priority: [null, [Validators.required, Validators.min(0), Validators.pattern(/^\d+$/)]],
    status: ['Active', [Validators.required]],
    invoiceTemplateEnabled: [false],
    invoiceTemplateKey: [{ value: '', disabled: true }]
  });

  constructor(private fb: FormBuilder) {}

  ngOnChanges(): void {
    if (this.vendor) {
      const raw = this.vendor.raw as Record<string, unknown>;
      this.form.patchValue({
        supplierName: this.vendor.name,
        address: String(raw['address'] ?? ''),
        phone: String(raw['phone'] ?? ''),
        gst: String(raw['gst'] ?? ''),
        pan: String(raw['pan'] ?? ''),
        foodLicenseNo: String(raw['food_license_no'] ?? ''),
        priority: this.vendor.priority,
        status: this.vendor.status,
        invoiceTemplateEnabled: this.asFlag(raw['invoice_template_enabled']),
        invoiceTemplateKey: String(raw['invoice_template_key'] ?? '')
      });
      this.form.enable({ emitEvent: false });
      // Keep template key read-only; it is set by uploading a sample PDF.
      this.form.get('invoiceTemplateKey')?.disable({ emitEvent: false });
      this.form.markAsPristine();
      return;
    }
    this.form.enable({ emitEvent: false });
    this.form.get('invoiceTemplateKey')?.disable({ emitEvent: false });
    this.form.reset({
      supplierName: '',
      address: '',
      phone: '',
      gst: '',
      pan: '',
      foodLicenseNo: '',
      priority: null,
      status: 'Active',
      invoiceTemplateEnabled: false,
      invoiceTemplateKey: ''
    });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.save.emit(this.form.getRawValue() as VendorFormValue);
  }

  onTemplateFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement | null;
    const file = input?.files?.[0];
    if (!file) {
      return;
    }
    this.uploadTemplateSample.emit(file);
    // Allow selecting the same file again.
    if (input) {
      input.value = '';
    }
  }

  clearInvoiceTemplate(): void {
    this.clearTemplate.emit();
  }

  clear(): void {
    this.form.reset({
      supplierName: '',
      address: '',
      phone: '',
      gst: '',
      pan: '',
      foodLicenseNo: '',
      priority: null,
      status: 'Active',
      invoiceTemplateEnabled: false,
      invoiceTemplateKey: ''
    });
    this.reset.emit();
  }

  private asFlag(value: unknown): boolean {
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
}
