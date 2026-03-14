import { Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { VendorFormValue, VendorTemplateValidation, VendorView } from '../vendor.model';
import { InvoiceTemplateModel } from '../../shared/invoice-template-model.service';
import { VendorService } from '../vendor.service';

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
  @Input() isValidatingTemplate = false;
  @Input() templateValidation: VendorTemplateValidation | null = null;
  @Input() invoiceTemplateModel: InvoiceTemplateModel | null = null;
  @Output() save = new EventEmitter<VendorFormValue>();
  @Output() reset = new EventEmitter<void>();
  @Output() clearTemplate = new EventEmitter<void>();
  @Output() validateTemplateSample = new EventEmitter<{ file: File; templateJson: string }>();

  form: FormGroup = this.fb.group({
    supplierName: ['', [Validators.required, Validators.maxLength(140)]],
    address: [''],
    phone: [''],
    gst: [''],
    pan: [''],
    foodLicenseNo: [''],
    priority: [null, [Validators.required, Validators.min(0), Validators.pattern(/^\d+$/)]],
    status: ['Inactive', [Validators.required]],
    invoiceTemplateJson: ['']
  });
  sampleFile: File | null = null;

  constructor(private fb: FormBuilder, private vendorService: VendorService) {}

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
      status: 'Inactive',
      invoiceTemplateJson: ''
    });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const status = String(this.form.get('status')?.value ?? 'Inactive');
    const json = String(this.form.get('invoiceTemplateJson')?.value ?? '').trim();
    if (status === 'Active' && !json) {
      this.form.get('invoiceTemplateJson')?.setErrors({ requiredForActive: true });
      return;
    }
    if (status === 'Active' && !this.hasValidatedSample) {
      this.form.get('invoiceTemplateJson')?.setErrors({ sampleRequiredForActive: true });
      return;
    }
    if (json) {
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

  onSampleFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement | null;
    const file = input?.files?.item(0) ?? null;
    this.sampleFile = file;
  }

  validateSample(): void {
    if (this.mode !== 'edit' || !this.vendor || !this.sampleFile) {
      return;
    }
    this.validateTemplateSample.emit({
      file: this.sampleFile,
      templateJson: String(this.form.get('invoiceTemplateJson')?.value ?? '')
    });
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
      status: 'Inactive',
      invoiceTemplateJson: ''
    });
    this.reset.emit();
  }

  get activeTemplateRequired(): boolean {
    return this.form.get('invoiceTemplateJson')?.hasError('requiredForActive') === true;
  }

  get sampleRequiredForActive(): boolean {
    return this.form.get('invoiceTemplateJson')?.hasError('sampleRequiredForActive') === true;
  }

  get hasValidatedSample(): boolean {
    if (this.templateValidation?.activationReady) {
      return true;
    }
    const raw = (this.vendor?.raw ?? {}) as Record<string, unknown>;
    return String(raw['invoice_template_sample_pdf'] ?? '').trim().length > 0;
  }

  get templateHint(): string {
    if (this.mode !== 'edit') {
      return 'Save the vendor first, then upload a sample invoice to validate the parser before activation.';
    }
    return 'Upload a sample invoice PDF to validate the template and confirm required item columns are extracted.';
  }

  get samplePdfUrl(): string {
    const raw = (this.vendor?.raw ?? {}) as Record<string, unknown>;
    return String(raw['invoice_template_sample_pdf'] ?? '').trim();
  }

  get samplePdfName(): string {
    const url = this.samplePdfUrl;
    if (!url) {
      return 'invoice_template.pdf';
    }
    const clean = url.split('?')[0];
    const parts = clean.split('/');
    return parts[parts.length - 1] || 'invoice_template.pdf';
  }

  openSamplePdf(): void {
    if (!this.vendor?.id) {
      return;
    }
    this.vendorService.downloadInvoiceTemplateSample(this.vendor.id).subscribe({
      next: blob => {
        const objectUrl = window.URL.createObjectURL(blob);
        window.open(objectUrl, '_blank', 'noopener,noreferrer');
        window.setTimeout(() => window.URL.revokeObjectURL(objectUrl), 60_000);
      }
    });
  }

  downloadSamplePdf(): void {
    if (!this.vendor?.id) {
      return;
    }
    this.vendorService.downloadInvoiceTemplateSample(this.vendor.id).subscribe({
      next: blob => {
        const objectUrl = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = objectUrl;
        link.download = this.samplePdfName;
        link.click();
        window.setTimeout(() => window.URL.revokeObjectURL(objectUrl), 60_000);
      }
    });
  }

  get itemFields() {
    return this.invoiceTemplateModel?.itemFields ?? [];
  }

  get summaryFields() {
    return this.invoiceTemplateModel?.summaryFields ?? [];
  }
}
