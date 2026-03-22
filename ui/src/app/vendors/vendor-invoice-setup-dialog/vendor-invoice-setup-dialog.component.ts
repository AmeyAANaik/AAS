import { Component, Inject, OnInit } from '@angular/core';
import { FormBuilder, FormControl, FormGroup } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { finalize } from 'rxjs/operators';
import { InvoiceTemplateModel, InvoiceTemplateModelField, InvoiceTemplateModelService } from '../../shared/invoice-template-model.service';
import {
  VendorInvoiceDetectedColumn,
  VendorInvoiceTemplateAnalysis,
  VendorInvoiceTemplateMappingConfig,
  VendorInvoiceTemplateMappingPreview,
  VendorView,
  parseVendorTemplateMapping,
  parseVendorTemplateSampleFileName
} from '../vendor.model';
import { VendorService } from '../vendor.service';

export interface VendorInvoiceSetupDialogData {
  vendor: VendorView;
  invoiceTemplateModel: InvoiceTemplateModel | null;
  mappingPreview: VendorInvoiceTemplateMappingPreview | null;
}

export interface VendorInvoiceSetupDialogResult {
  mappingPreview: VendorInvoiceTemplateMappingPreview | null;
  refreshVendor: boolean;
  statusMessage?: string;
  invoiceTemplateModel?: InvoiceTemplateModel | null;
}

@Component({
  selector: 'app-vendor-invoice-setup-dialog',
  templateUrl: './vendor-invoice-setup-dialog.component.html',
  styleUrl: './vendor-invoice-setup-dialog.component.scss'
})
export class VendorInvoiceSetupDialogComponent implements OnInit {
  readonly mappingForm: FormGroup = this.fb.group({});

  activeStage: 'setup' | 'review' = 'setup';
  sampleFile: File | null = null;
  isWorking = false;
  isAnalyzing = false;
  isLoadingModel = false;
  errorMessage = '';
  infoMessage = '';
  analysis: VendorInvoiceTemplateAnalysis | null = null;
  mappingPreview: VendorInvoiceTemplateMappingPreview | null;
  private loadedInvoiceTemplateModel: InvoiceTemplateModel | null = null;

  constructor(
    @Inject(MAT_DIALOG_DATA) public readonly data: VendorInvoiceSetupDialogData,
    private readonly dialogRef: MatDialogRef<VendorInvoiceSetupDialogComponent, VendorInvoiceSetupDialogResult>,
    private readonly fb: FormBuilder,
    private readonly vendorService: VendorService,
    private readonly invoiceTemplateModelService: InvoiceTemplateModelService
  ) {
    this.mappingPreview = data.mappingPreview;
    this.activeStage = this.mappingPreview ? 'review' : 'setup';
  }

  ngOnInit(): void {
    this.loadModelIfNeeded();
  }

  get invoiceTemplateModel(): InvoiceTemplateModel {
    return this.loadedInvoiceTemplateModel ?? this.data.invoiceTemplateModel ?? {
      itemFields: [],
      summaryFields: [],
      requiredFields: { items: [], summary: [] }
    };
  }

  get requiredItemFields(): InvoiceTemplateModelField[] {
    return (this.invoiceTemplateModel.itemFields ?? []).filter(field => field.required);
  }

  get itemFields(): InvoiceTemplateModelField[] {
    return this.invoiceTemplateModel.itemFields ?? [];
  }

  get optionalItemFields(): InvoiceTemplateModelField[] {
    return (this.invoiceTemplateModel.itemFields ?? []).filter(field => !field.required);
  }

  get requiredSummaryFields(): InvoiceTemplateModelField[] {
    return (this.invoiceTemplateModel.summaryFields ?? []).filter(field => field.required);
  }

  get summaryFields(): InvoiceTemplateModelField[] {
    return this.invoiceTemplateModel.summaryFields ?? [];
  }

  get optionalSummaryFields(): InvoiceTemplateModelField[] {
    return (this.invoiceTemplateModel.summaryFields ?? []).filter(field => !field.required);
  }

  get samplePdfUrl(): string {
    const raw = (this.data.vendor.raw ?? {}) as Record<string, unknown>;
    return String(raw['invoice_template_sample_pdf'] ?? '').trim();
  }

  get savedSampleFileName(): string {
    const parsedName = parseVendorTemplateSampleFileName(this.data.vendor.raw);
    if (parsedName) {
      return parsedName;
    }
    const url = this.samplePdfUrl;
    if (!url) {
      return '';
    }
    const lastSegment = url.split('/').pop() ?? '';
    return decodeURIComponent(lastSegment);
  }

  get selectedSampleFileName(): string {
    return this.sampleFile?.name ?? '';
  }

  get hasUploadedSample(): boolean {
    return !!this.sampleFile || !!this.samplePdfUrl;
  }

  get requiredFieldCount(): number {
    return this.requiredItemFields.length + this.requiredSummaryFields.length;
  }

  get mappedFieldCount(): number {
    const request = this.buildMappingRequest();
    const requiredItems = new Set(this.requiredItemFields.map(field => field.key));
    const requiredSummary = new Set(this.requiredSummaryFields.map(field => field.key));
    return request.itemMappings.filter(mapping => requiredItems.has(mapping.targetField)).length
      + request.summaryMappings.filter(mapping => requiredSummary.has(mapping.targetField)).length;
  }

  get canAnalyze(): boolean {
    return this.hasUploadedSample && !this.isWorking && !this.isAnalyzing && !this.isLoadingModel;
  }

  get canPreview(): boolean {
    return !!this.analysis && this.mappedFieldCount === this.requiredFieldCount && !this.isWorking && !this.isAnalyzing && !this.isLoadingModel;
  }

  get canSave(): boolean {
    return !!this.mappingPreview?.mappingReady && !this.isWorking && !this.isAnalyzing && !this.isLoadingModel;
  }

  get validationStateLabel(): string {
    if (this.mappingPreview?.mappingReady) {
      return 'Preview validated';
    }
    if (this.analysis) {
      return 'Analysis ready';
    }
    if (parseVendorTemplateMapping(this.data.vendor.raw)) {
      return 'Setup saved';
    }
    return 'Setup pending';
  }

  get validationStatusTone(): 'ready' | 'warning' | 'idle' {
    if (this.mappingPreview?.mappingReady || parseVendorTemplateMapping(this.data.vendor.raw)) {
      return 'ready';
    }
    if (this.analysis) {
      return 'warning';
    }
    return 'idle';
  }

  get previewMetrics(): { itemsDetected: number; totalRows: number; billAmount: string; transportCharge: string } {
    return this.mappingPreview?.previewMetrics ?? this.analysis?.previewMetrics ?? {
      itemsDetected: 0,
      totalRows: 0,
      billAmount: '',
      transportCharge: ''
    };
  }

  get detectedItemCount(): number {
    return this.previewMetrics.itemsDetected;
  }

  get ocrRowsScanned(): number {
    return this.previewMetrics.totalRows;
  }

  get detectedBillAmount(): string {
    return this.previewMetrics.billAmount || 'Not found';
  }

  get detectedTransportCharge(): string {
    return this.previewMetrics.transportCharge || 'Not found';
  }

  get stepCards(): Array<{ step: string; title: string; description: string; done: boolean }> {
    return [
      {
        step: '1',
        title: 'Upload sample',
        description: 'Use one real invoice PDF from this vendor.',
        done: this.hasUploadedSample
      },
      {
        step: '2',
        title: 'Map required fields',
        description: 'Choose only from backend-detected invoice columns.',
        done: !!this.analysis && this.mappedFieldCount === this.requiredFieldCount
      },
      {
        step: '3',
        title: 'Review extracted data',
        description: 'Validate row count and summary values before saving.',
        done: this.mappingPreview?.mappingReady === true
      }
    ];
  }

  openSetupStage(): void {
    this.activeStage = 'setup';
  }

  openReviewStage(): void {
    if (this.mappingPreview) {
      this.activeStage = 'review';
    }
  }

  onSampleFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement | null;
    this.sampleFile = input?.files?.item(0) ?? null;
    if (input) {
      input.value = '';
    }
    this.analysis = null;
    this.mappingPreview = null;
    this.errorMessage = '';
    this.infoMessage = this.sampleFile
      ? `Selected sample PDF: ${this.sampleFile.name}. Analyze it to replace the currently saved sample.`
      : '';
    this.initializeMappingForm({ itemMappings: [], summaryMappings: [] });
  }

  analyzeSample(): void {
    if (!this.hasUploadedSample) {
      this.errorMessage = 'Upload a sample PDF or reuse the sample already attached to this vendor.';
      return;
    }
    this.isAnalyzing = true;
    this.errorMessage = '';
    this.infoMessage = '';
    this.vendorService.analyzeInvoiceTemplateSample(this.data.vendor.id, this.sampleFile)
      .pipe(finalize(() => (this.isAnalyzing = false)))
      .subscribe({
        next: analysis => {
          this.analysis = analysis;
          this.mappingPreview = null;
          this.initializeMappingForm(this.preferredMapping(analysis));
          this.activeStage = 'setup';
          this.infoMessage = 'Sample analyzed. Map the required business fields from the detected invoice columns.';
        },
        error: err => {
          this.errorMessage = this.extractMessage(err, 'Unable to analyze the sample invoice.');
        }
      });
  }

  previewMapping(): void {
    if (!this.analysis) {
      this.errorMessage = 'Analyze the sample PDF first.';
      return;
    }
    this.isWorking = true;
    this.errorMessage = '';
    this.infoMessage = '';
    this.vendorService.previewInvoiceTemplateMapping(this.data.vendor.id, this.sampleFile, this.buildMappingRequest())
      .pipe(finalize(() => (this.isWorking = false)))
      .subscribe({
        next: preview => {
          this.mappingPreview = preview;
          this.activeStage = 'review';
          this.infoMessage = preview.mappingReady
            ? 'Preview passed. The generated vendor template is ready to save.'
            : 'Preview completed. Review missing fields before saving.';
        },
        error: err => {
          this.errorMessage = this.extractMessage(err, 'Unable to preview the generated vendor template.');
        }
      });
  }

  clearTemplate(): void {
    this.isWorking = true;
    this.errorMessage = '';
    this.infoMessage = '';
    this.vendorService.clearInvoiceTemplate(this.data.vendor.id)
      .pipe(finalize(() => (this.isWorking = false)))
      .subscribe({
        next: () => {
          this.analysis = null;
          this.mappingPreview = null;
          this.sampleFile = null;
          this.initializeMappingForm({ itemMappings: [], summaryMappings: [] });
          this.dialogRef.close({
            mappingPreview: null,
            refreshVendor: true,
            statusMessage: 'Invoice extraction setup cleared.',
            invoiceTemplateModel: this.loadedInvoiceTemplateModel ?? this.data.invoiceTemplateModel
          });
        },
        error: err => {
          this.errorMessage = this.extractMessage(err, 'Unable to clear invoice extraction setup.');
        }
      });
  }

  apply(): void {
    if (!this.analysis) {
      this.errorMessage = 'Analyze the sample PDF before saving.';
      return;
    }
    this.isWorking = true;
    this.errorMessage = '';
    this.infoMessage = '';
    this.vendorService.saveInvoiceTemplateMapping(this.data.vendor.id, this.sampleFile, this.buildMappingRequest())
      .pipe(finalize(() => (this.isWorking = false)))
      .subscribe({
        next: response => {
          this.dialogRef.close({
            mappingPreview: response.mappingPreview ?? this.mappingPreview,
            refreshVendor: true,
            statusMessage: 'Vendor-specific invoice template saved.',
            invoiceTemplateModel: this.loadedInvoiceTemplateModel ?? this.data.invoiceTemplateModel
          });
        },
        error: err => {
          this.errorMessage = this.extractMessage(err, 'Unable to save invoice extraction setup.');
        }
      });
  }

  close(): void {
    this.dialogRef.close({
      mappingPreview: this.mappingPreview,
      refreshVendor: false,
      invoiceTemplateModel: this.loadedInvoiceTemplateModel ?? this.data.invoiceTemplateModel
    });
  }

  itemOptions(): VendorInvoiceDetectedColumn[] {
    return this.analysis?.detectedColumns.items ?? [];
  }

  summaryOptions(): VendorInvoiceDetectedColumn[] {
    return this.analysis?.detectedColumns.summary ?? [];
  }

  mappingControl(section: 'items' | 'summary', fieldKey: string): FormControl<string> {
    return this.mappingForm.get(`${section}.${fieldKey}`) as FormControl<string>;
  }

  controlForItem(fieldKey: string): FormControl<string> {
    return this.mappingControl('items', fieldKey);
  }

  controlForSummary(fieldKey: string): FormControl<string> {
    return this.mappingControl('summary', fieldKey);
  }

  trackStep(_: number, item: { step: string }): string {
    return item.step;
  }

  trackChecklist(_: number, item: { step: string }): string {
    return item.step;
  }

  trackColumn(_: number, item: VendorInvoiceDetectedColumn): string {
    return `${item.targetField}:${item.value}`;
  }

  trackOption(_: number, item: VendorInvoiceDetectedColumn): string {
    return `${item.targetField}:${item.value}`;
  }

  trackField(_: number, item: InvoiceTemplateModelField): string {
    return item.key;
  }

  private loadModelIfNeeded(): void {
    if (this.data.invoiceTemplateModel) {
      this.loadedInvoiceTemplateModel = this.data.invoiceTemplateModel;
      this.initializeFromSavedSetup();
      return;
    }
    this.isLoadingModel = true;
    this.invoiceTemplateModelService.fetchModel()
      .pipe(finalize(() => (this.isLoadingModel = false)))
      .subscribe({
        next: model => {
          this.loadedInvoiceTemplateModel = model;
          this.initializeFromSavedSetup();
        },
        error: () => {
          this.errorMessage = 'Unable to load required invoice fields from API.';
        }
      });
  }

  private initializeFromSavedSetup(): void {
    const savedMapping = parseVendorTemplateMapping(this.data.vendor.raw) ?? { itemMappings: [], summaryMappings: [] };
    this.initializeMappingForm(savedMapping);
    if (this.samplePdfUrl) {
      this.infoMessage = 'A sample PDF is already saved for this vendor. Select a new PDF and click Analyze sample PDF to replace it, or reuse the saved sample by clicking Analyze sample PDF now.';
    }
  }

  private preferredMapping(analysis: VendorInvoiceTemplateAnalysis): VendorInvoiceTemplateMappingConfig {
    const saved = analysis.savedMapping;
    if ((saved.itemMappings?.length ?? 0) > 0 || (saved.summaryMappings?.length ?? 0) > 0) {
      return saved;
    }
    return analysis.suggestedMapping;
  }

  private initializeMappingForm(mapping: VendorInvoiceTemplateMappingConfig): void {
    const itemGroup = this.fb.group({});
    const summaryGroup = this.fb.group({});
    const itemMap = new Map((mapping.itemMappings ?? []).map(entry => [entry.targetField, entry.sourceLabel]));
    const summaryMap = new Map((mapping.summaryMappings ?? []).map(entry => [entry.targetField, entry.sourceLabel]));

    [...this.requiredItemFields, ...this.optionalItemFields].forEach(field => {
      itemGroup.addControl(field.key, this.fb.nonNullable.control(itemMap.get(field.key) ?? ''));
    });
    [...this.requiredSummaryFields, ...this.optionalSummaryFields].forEach(field => {
      summaryGroup.addControl(field.key, this.fb.nonNullable.control(summaryMap.get(field.key) ?? ''));
    });

    this.mappingForm.setControl('items', itemGroup);
    this.mappingForm.setControl('summary', summaryGroup);
  }

  private buildMappingRequest(): VendorInvoiceTemplateMappingConfig {
    const itemMappings = [...this.requiredItemFields, ...this.optionalItemFields]
      .map(field => ({
        targetField: field.key,
        sourceLabel: String(this.mappingControl('items', field.key)?.value ?? '').trim()
      }))
      .filter(entry => entry.sourceLabel);
    const summaryMappings = [...this.requiredSummaryFields, ...this.optionalSummaryFields]
      .map(field => ({
        targetField: field.key,
        sourceLabel: String(this.mappingControl('summary', field.key)?.value ?? '').trim()
      }))
      .filter(entry => entry.sourceLabel);
    return { itemMappings, summaryMappings };
  }

  private extractMessage(err: unknown, fallback: string): string {
    if (typeof err === 'object' && err && 'error' in err) {
      const error = (err as { error?: { error?: string } }).error;
      if (typeof error?.error === 'string' && error.error.trim()) {
        return error.error;
      }
    }
    return fallback;
  }
}
