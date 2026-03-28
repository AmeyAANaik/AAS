import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Category } from '../../categories/category.model';
import {
  VendorFormValue,
  VendorInvoiceTemplateProfilePreview,
  VendorView,
  parseVendorTemplateProfile,
  parseVendorTemplateSampleFileName
} from '../vendor.model';
import { InvoiceTemplateModel } from '../../shared/invoice-template-model.service';

@Component({
  selector: 'app-vendor-form',
  templateUrl: './vendor-form.component.html',
  styleUrl: './vendor-form.component.scss'
})
export class VendorFormComponent implements OnChanges {
  @Input() vendor: VendorView | null = null;
  @Input() categories: Category[] = [];
  @Input() mode: 'create' | 'edit' = 'create';
  @Input() isSaving = false;
  @Input() statusMessage = '';
  @Input() invoiceTemplateModel: InvoiceTemplateModel | null = null;
  @Input() profilePreview: VendorInvoiceTemplateProfilePreview | null = null;
  @Output() save = new EventEmitter<VendorFormValue>();
  @Output() reset = new EventEmitter<void>();
  @Output() openInvoiceSetup = new EventEmitter<void>();

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

  constructor(private fb: FormBuilder) {
    this.form.get('status')?.valueChanges.subscribe(() => this.syncStatusErrors());
  }

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
    }
    this.syncStatusErrors();
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const status = String(this.form.get('status')?.value ?? 'Inactive');
    if (status === 'Active' && this.mode === 'create') {
      this.form.get('status')?.setErrors({ activationPending: true });
      return;
    }
    if (status === 'Active' && !this.previewReady) {
      this.form.get('status')?.setErrors({ previewRequired: true });
      return;
    }
    this.save.emit(this.form.getRawValue() as VendorFormValue);
  }

  activateVendor(): void {
    if (!this.canShowActivateAction) {
      this.form.get('status')?.setErrors({ previewRequired: true });
      return;
    }
    this.form.patchValue({ status: 'Active' });
    this.submit();
  }

  openSetupDialog(): void {
    this.openInvoiceSetup.emit();
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
    this.reset.emit();
  }

  get activationPending(): boolean {
    return this.form.get('status')?.hasError('activationPending') === true;
  }

  get previewRequiredForActive(): boolean {
    return this.form.get('status')?.hasError('previewRequired') === true;
  }

  get previewReady(): boolean {
    return this.profilePreview?.profileReady === true || this.hasSavedTemplateSetup;
  }

  get canActivateVendor(): boolean {
    return this.mode === 'edit' && this.previewReady;
  }

  get canShowActivateAction(): boolean {
    return this.canActivateVendor && this.currentStatus !== 'Active';
  }

  get currentStatus(): 'Active' | 'Inactive' {
    return String(this.form.get('status')?.value ?? 'Inactive') === 'Active' ? 'Active' : 'Inactive';
  }

  get activationRequirementMessage(): string {
    if (this.mode !== 'edit') {
      return 'Save the vendor first, then complete invoice extraction setup before activating it.';
    }
    if (!this.previewReady) {
      return 'Validate and save the vendor invoice extraction setup before activating this vendor.';
    }
    if (this.currentStatus === 'Active') {
      return 'Vendor is active and invoice extraction setup is validated.';
    }
    return 'Invoice extraction setup is validated. Use Activate vendor to enable this vendor profile.';
  }

  get validationStateLabel(): string {
    if (this.profilePreview) {
      return this.profilePreview.profileReady ? 'Preview validated' : 'Needs review';
    }
    if (this.hasSavedTemplateSetup) {
      return 'Setup saved';
    }
    return 'Preview pending';
  }

  get validationStatusTone(): 'ready' | 'warning' | 'idle' {
    if (this.profilePreview) {
      return this.profilePreview.profileReady ? 'ready' : 'warning';
    }
    if (this.hasSavedTemplateSetup) {
      return 'ready';
    }
    return 'idle';
  }

  get templateHint(): string {
    if (this.mode !== 'edit') {
      return 'Save the vendor first, then open invoice extraction setup to upload a sample PDF and generate a native invoice mapping.';
    }
    if (!this.previewReady) {
      return 'Open invoice extraction setup to analyze a sample invoice, review the generated native mapping, and save a validated setup before activating this vendor.';
    }
    return 'Open invoice extraction setup to review or refresh the saved native invoice mapping before activation.';
  }

  get formSubtitle(): string {
    return this.mode === 'edit'
      ? 'Maintain vendor master data and verify the stored invoice mapping before activation.'
      : 'Capture vendor details first. Native invoice mapping becomes available after the vendor is created.';
  }

  get requiredFieldCount(): number {
    return (this.invoiceTemplateModel?.requiredFields?.items.length ?? this.requiredItemFields.length)
      + (this.invoiceTemplateModel?.requiredFields?.summary.length ?? this.requiredSummaryFields.length);
  }

  get mappedFieldCount(): number {
    const requiredItems = this.requiredItemFields.map(field => field.key);
    const requiredSummary = this.requiredSummaryFields.map(field => field.key);
    if (this.profilePreview) {
      return this.countMatchedRequiredFields(this.profilePreview.parsedFields.items, requiredItems)
        + this.countMatchedRequiredFields(this.profilePreview.parsedFields.summary, requiredSummary);
    }
    return this.hasSavedTemplateSetup ? this.requiredFieldCount : 0;
  }

  get previewActionLabel(): string {
    return this.hasSavedTemplateSetup || this.profilePreview ? 'Update setup view' : 'Open setup view';
  }

  get extractionSummaryLines(): string[] {
    return [
      `Required fields: ${this.requiredFieldCount}`,
      `Matched fields: ${this.mappedFieldCount}/${this.requiredFieldCount}`,
      `Sample PDF: ${this.samplePdfDisplayName}`,
      `Generated template: ${this.savedTemplateProfile?.label ?? this.validationStateLabel}`
    ];
  }

  get samplePdfUrl(): string {
    const raw = (this.vendor?.raw ?? {}) as Record<string, unknown>;
    return String(raw['invoice_template_sample_pdf'] ?? '').trim();
  }

  get hasUploadedSample(): boolean {
    return !!this.samplePdfUrl;
  }

  get samplePdfDisplayName(): string {
    if (!this.hasUploadedSample) {
      return 'Pending';
    }
    const parsedName = parseVendorTemplateSampleFileName(this.vendor?.raw);
    if (parsedName) {
      return parsedName;
    }
    const lastSegment = this.samplePdfUrl.split('/').pop() ?? '';
    return decodeURIComponent(lastSegment || 'Attached');
  }

  get savedTemplateProfile() {
    return parseVendorTemplateProfile(this.vendor?.raw);
  }

  private get hasSavedTemplateSetup(): boolean {
    return !!this.savedTemplateProfile && this.hasUploadedSample;
  }

  get requiredItemFields() {
    return (this.invoiceTemplateModel?.itemFields ?? []).filter(field => field.required);
  }

  get requiredSummaryFields() {
    return (this.invoiceTemplateModel?.summaryFields ?? []).filter(field => field.required);
  }

  private syncStatusErrors(): void {
    const control = this.form.get('status');
    if (!control) {
      return;
    }
    const current = { ...(control.errors ?? {}) };
    delete current['activationPending'];
    delete current['previewRequired'];
    const nextErrors = Object.keys(current).length ? current : null;
    control.setErrors(nextErrors);
  }

  private countMatchedRequiredFields(parsedFields: string[], requiredFields: string[]): number {
    const parsed = new Set(parsedFields ?? []);
    return requiredFields.filter(field => parsed.has(field)).length;
  }
}
