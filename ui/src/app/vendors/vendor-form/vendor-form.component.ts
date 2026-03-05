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
    invoiceTemplateJson: ['']
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
        invoiceTemplateJson: String(raw['invoice_template_json'] ?? '')
      });
      this.form.enable({ emitEvent: false });
      this.form.markAsPristine();
      return;
    }
    this.form.enable({ emitEvent: false });
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
      invoiceTemplateJson: ''
    });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const enabled = Boolean(this.form.get('invoiceTemplateEnabled')?.value);
    const json = String(this.form.get('invoiceTemplateJson')?.value ?? '').trim();
    if (enabled && json) {
      try {
        JSON.parse(json);
      } catch {
        this.form.get('invoiceTemplateJson')?.setErrors({ json: true });
        return;
      }
    }
    this.save.emit(this.form.getRawValue() as VendorFormValue);
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
      invoiceTemplateJson: ''
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
