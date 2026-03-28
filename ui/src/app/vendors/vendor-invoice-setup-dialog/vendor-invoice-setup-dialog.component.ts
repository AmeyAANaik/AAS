import { Component, Inject, OnInit } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { finalize } from 'rxjs/operators';
import { InvoiceTemplateModel, InvoiceTemplateModelField, InvoiceTemplateModelService } from '../../shared/invoice-template-model.service';
import {
  VendorInvoiceTemplateProfilePreview,
  VendorView,
  parseVendorTemplateProfile,
  parseVendorTemplateSampleFileName
} from '../vendor.model';
import { VendorService } from '../vendor.service';

export interface VendorInvoiceSetupDialogData {
  vendor: VendorView;
  invoiceTemplateModel: InvoiceTemplateModel | null;
  profilePreview: VendorInvoiceTemplateProfilePreview | null;
}

export interface VendorInvoiceSetupDialogResult {
  profilePreview: VendorInvoiceTemplateProfilePreview | null;
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
  activeStage: 'setup' | 'review' = 'setup';
  sampleFile: File | null = null;
  isWorking = false;
  isLoadingModel = false;
  errorMessage = '';
  infoMessage = '';
  profilePreview: VendorInvoiceTemplateProfilePreview | null;
  private loadedInvoiceTemplateModel: InvoiceTemplateModel | null = null;

  constructor(
    @Inject(MAT_DIALOG_DATA) public readonly data: VendorInvoiceSetupDialogData,
    private readonly dialogRef: MatDialogRef<VendorInvoiceSetupDialogComponent, VendorInvoiceSetupDialogResult>,
    private readonly vendorService: VendorService,
    private readonly invoiceTemplateModelService: InvoiceTemplateModelService
  ) {
    this.profilePreview = data.profilePreview;
    this.activeStage = this.profilePreview ? 'review' : 'setup';
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

  get requiredSummaryFields(): InvoiceTemplateModelField[] {
    return (this.invoiceTemplateModel.summaryFields ?? []).filter(field => field.required);
  }

  get samplePdfUrl(): string {
    const raw = (this.data.vendor.raw ?? {}) as Record<string, unknown>;
    return String(raw['invoice_template_sample_pdf'] ?? '').trim();
  }

  get savedTemplateProfile() {
    return parseVendorTemplateProfile(this.data.vendor.raw);
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

  get matchedFieldCount(): number {
    if (!this.profilePreview) {
      return this.savedTemplateProfile ? this.requiredFieldCount : 0;
    }
    return this.countMatches(this.profilePreview.parsedFields.items, this.requiredItemFields.map(field => field.key))
      + this.countMatches(this.profilePreview.parsedFields.summary, this.requiredSummaryFields.map(field => field.key));
  }

  get canPreview(): boolean {
    return this.hasUploadedSample
      && !this.isWorking
      && !this.isLoadingModel;
  }

  get canSave(): boolean {
    return !!(this.profilePreview?.saveAllowed ?? this.profilePreview?.profileReady)
      && !this.isWorking
      && !this.isLoadingModel;
  }

  get expectedItemCount(): number {
    return this.profilePreview?.completeness?.expectedItemCount ?? this.profilePreview?.previewMetrics?.expectedItems ?? 0;
  }

  get extractedItemCount(): number {
    return this.profilePreview?.completeness?.extractedItemCount ?? this.profilePreview?.previewMetrics?.itemsDetected ?? 0;
  }

  get itemCountMismatch(): boolean {
    return !!this.profilePreview
      && this.expectedItemCount > 0
      && this.extractedItemCount < this.expectedItemCount;
  }

  get missingSerialNumbers(): number[] {
    return this.profilePreview?.completeness?.missingSerials ?? [];
  }

  get missingSerialContexts(): Array<{ serial: number; parserContext: string[]; camelotContext: string[] }> {
    return this.profilePreview?.completeness?.missingSerialContexts ?? [];
  }

  get missingSerialSummary(): string {
    if (!this.missingSerialNumbers.length) {
      return '';
    }
    return this.missingSerialNumbers.join(', ');
  }

  get fieldMappingItemRows() {
    return this.profilePreview?.fieldMapping?.itemMappings ?? [];
  }

  get fieldMappingSummaryRows() {
    return this.profilePreview?.fieldMapping?.summaryMappings ?? [];
  }

  get validationStateLabel(): string {
    if (this.profilePreview) {
      return this.profilePreview.profileReady ? 'Preview validated' : 'Needs review';
    }
    if (this.savedTemplateProfile) {
      return 'Setup saved';
    }
    return 'Setup pending';
  }

  get validationStatusTone(): 'ready' | 'warning' | 'idle' {
    if (this.profilePreview) {
      return this.profilePreview.profileReady ? 'ready' : 'warning';
    }
    if (this.savedTemplateProfile) {
      return 'ready';
    }
    return 'idle';
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
        title: 'Analyze invoice',
        description: 'Build a vendor-specific native PDF layout mapping from this sample.',
        done: !!this.profilePreview
      },
      {
        step: '3',
        title: 'Review preview',
        description: 'Confirm extracted rows and totals before saving.',
        done: this.profilePreview?.profileReady === true
      }
    ];
  }

  openSetupStage(): void {
    this.activeStage = 'setup';
  }

  openReviewStage(): void {
    if (this.profilePreview) {
      this.activeStage = 'review';
    }
  }

  onSampleFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement | null;
    this.sampleFile = input?.files?.item(0) ?? null;
    if (input) {
      input.value = '';
    }
    this.profilePreview = null;
    this.activeStage = 'setup';
    this.errorMessage = '';
    this.infoMessage = this.sampleFile
      ? `Selected sample PDF: ${this.sampleFile.name}. Analyze it to build a new native PDF layout mapping for this vendor.`
      : '';
  }

  analyzeInvoice(): void {
    if (!this.hasUploadedSample) {
      this.errorMessage = 'Upload a sample PDF or reuse the sample already attached to this vendor.';
      return;
    }
    this.isWorking = true;
    this.errorMessage = '';
    this.infoMessage = '';
    this.vendorService.generateInvoiceTemplateProfile(this.data.vendor.id, this.sampleFile)
      .pipe(finalize(() => (this.isWorking = false)))
      .subscribe({
        next: preview => {
          this.profilePreview = preview;
          this.activeStage = 'review';
          this.infoMessage = preview.profileReady
            ? 'Preview passed. The generated native PDF layout mapping is ready to save.'
            : preview.saveAllowed
              ? `Preview completed with warnings. ${this.itemCountMismatch
                  ? `The invoice shows ${this.expectedItemCount} serial-numbered rows and only ${this.extractedItemCount} were extracted. Missing serials: ${this.missingSerialSummary || 'review mismatch details below'}. `
                  : ''}You can save this setup after review.`
            : this.itemCountMismatch
              ? `Preview completed, but the invoice shows ${this.expectedItemCount} serial-numbered rows and only ${this.extractedItemCount} were extracted. Missing serials: ${this.missingSerialSummary || 'review mismatch details below'}.`
              : 'Preview completed. Review the detected row mapping and missing fields before saving.';
        },
        error: err => {
          this.errorMessage = this.extractMessage(err, 'Unable to analyze the invoice and build a native layout mapping.');
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
          this.profilePreview = null;
          this.sampleFile = null;
          this.dialogRef.close({
            profilePreview: null,
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
    if (!this.profilePreview?.generatedTemplate?.id) {
      this.errorMessage = 'Analyze the invoice successfully before saving.';
      return;
    }
    this.isWorking = true;
    this.errorMessage = '';
    this.infoMessage = '';
    this.vendorService.saveGeneratedInvoiceTemplateProfile(
      this.data.vendor.id,
      this.sampleFile,
      this.profilePreview.generatedTemplate
    )
      .pipe(finalize(() => (this.isWorking = false)))
      .subscribe({
        next: response => {
          this.dialogRef.close({
            profilePreview: response.profilePreview ?? this.profilePreview,
            refreshVendor: true,
            statusMessage: 'Vendor native invoice mapping saved.',
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
      profilePreview: this.profilePreview,
      refreshVendor: false,
      invoiceTemplateModel: this.loadedInvoiceTemplateModel ?? this.data.invoiceTemplateModel
    });
  }

  trackChecklist(_: number, item: { step: string }): string {
    return item.step;
  }

  trackField(_: number, item: InvoiceTemplateModelField): string {
    return item.key;
  }

  private loadModelIfNeeded(): void {
    if (this.data.invoiceTemplateModel) {
      this.loadedInvoiceTemplateModel = this.data.invoiceTemplateModel;
      return;
    }
    this.isLoadingModel = true;
    this.invoiceTemplateModelService.fetchModel()
      .pipe(finalize(() => (this.isLoadingModel = false)))
      .subscribe({
        next: model => {
          this.loadedInvoiceTemplateModel = model;
        },
        error: () => {
          this.errorMessage = 'Unable to load required invoice fields from API.';
        }
      });
  }

  private countMatches(parsedFields: string[], requiredFields: string[]): number {
    const parsed = new Set(parsedFields ?? []);
    return requiredFields.filter(field => parsed.has(field)).length;
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
