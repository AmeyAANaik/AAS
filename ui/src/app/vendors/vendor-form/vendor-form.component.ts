import { Component, ElementRef, EventEmitter, Input, OnChanges, Output, SimpleChanges, ViewChild } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Category } from '../../categories/category.model';
import { VendorFormValue, VendorTemplateValidation, VendorView } from '../vendor.model';
import { InvoiceTemplateModel } from '../../shared/invoice-template-model.service';

@Component({
  selector: 'app-vendor-form',
  templateUrl: './vendor-form.component.html',
  styleUrl: './vendor-form.component.scss'
})
export class VendorFormComponent implements OnChanges {
  readonly templateExample = `{
  "parser": {
    "version": 1,
    "itemLineRegex": "^(?<name>.+?)\\\\s+(?<hsn>\\\\d{4,10})\\\\s+(?<qty>\\\\d+(?:\\\\.\\\\d+)?)\\\\s+(?<rate>\\\\d+(?:\\\\.\\\\d+)?)\\\\s+(?<amount>\\\\d+(?:\\\\.\\\\d+)?)$",
    "billDateRegex": "(?im)^dated\\\\s*(?<date>[^\\\\n\\\\r]+)$",
    "finalAmountRegex": "(?im)^total\\\\s+(?<amount>\\\\d+(?:,\\\\d{3})*(?:\\\\.\\\\d+)?)$",
    "transportChargeRegex": "(?im)^transport\\\\s+(?<amount>\\\\d+(?:,\\\\d{3})*(?:\\\\.\\\\d+)?)$"
  }
}`;

  @ViewChild('sampleInput') sampleInput?: ElementRef<HTMLInputElement>;
  @Input() vendor: VendorView | null = null;
  @Input() categories: Category[] = [];
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
    vendorCode: ['', [Validators.required, Validators.maxLength(140)]],
    category: ['', [Validators.required]],
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

  constructor(private fb: FormBuilder) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (this.vendor) {
      const raw = this.vendor.raw as Record<string, unknown>;
      this.form.patchValue({
        supplierName: this.vendor.name,
        vendorCode: String(raw['vendor_code'] ?? ''),
        category: String(raw['category'] ?? ''),
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
    } else {
      this.form.enable({ emitEvent: false });
      this.form.reset({
        supplierName: '',
        vendorCode: '',
        category: '',
        address: '',
        phone: '',
        gst: '',
        pan: '',
        foodLicenseNo: '',
        priority: null,
        status: 'Inactive',
        invoiceTemplateJson: ''
      });
      this.sampleFile = null;
    }

    if (changes['templateValidation']?.currentValue?.activationReady) {
      this.form.patchValue({ status: 'Active' }, { emitEvent: false });
    }
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
    this.sampleFile = null;
    this.resetSampleInput();
    this.clearTemplate.emit();
  }

  onSampleFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement | null;
    const file = input?.files?.item(0) ?? null;
    this.sampleFile = file;
  }

  triggerSampleUpload(): void {
    const input = this.sampleInput?.nativeElement;
    if (!input) {
      return;
    }
    input.value = '';
    input.click();
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
      vendorCode: '',
      category: '',
      address: '',
      phone: '',
      gst: '',
      pan: '',
      foodLicenseNo: '',
      priority: null,
      status: 'Inactive',
      invoiceTemplateJson: ''
    });
    this.resetSampleInput();
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

  get hasUploadedSample(): boolean {
    return !!this.sampleFile || !!this.samplePdfUrl;
  }

  get itemFields() {
    return this.invoiceTemplateModel?.itemFields ?? [];
  }

  get summaryFields() {
    return this.invoiceTemplateModel?.summaryFields ?? [];
  }

  private resetSampleInput(): void {
    const input = this.sampleInput?.nativeElement;
    if (input) {
      input.value = '';
    }
  }
}
