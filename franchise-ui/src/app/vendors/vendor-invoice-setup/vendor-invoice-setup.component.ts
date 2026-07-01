import { CommonModule } from '@angular/common';
import { Component, EventEmitter, Input, OnChanges, Output, SimpleChanges } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatIconModule } from '@angular/material/icon';
import {
  REQUIRED_ITEM_FIELDS,
  REQUIRED_SUMMARY_FIELDS,
  VendorFieldMapping,
  VendorInvoiceTemplate,
  VendorView
} from '../vendor.model';
import { VendorStoreService } from '../vendor-store.service';

type Stage = 'setup' | 'review';

/**
 * Mock of the production `ui/` "Invoice extraction setup" dialog, rendered as an
 * inline panel to match franchise-ui's UX. Two stages: upload + analyze a sample
 * invoice (Setup), then review the detected field mapping and validate it (Review).
 * A validated template is what lets the vendor be activated.
 */
@Component({
  selector: 'app-vendor-invoice-setup',
  standalone: true,
  imports: [CommonModule, MatButtonModule, MatIconModule],
  templateUrl: './vendor-invoice-setup.component.html',
  styleUrl: './vendor-invoice-setup.component.css'
})
export class VendorInvoiceSetupComponent implements OnChanges {
  /** Null in create mode — setup is only available after the vendor is saved. */
  @Input() vendorId: string | null = null;
  @Input() vendorName = '';
  @Input() template: VendorInvoiceTemplate | null = null;

  @Output() changed = new EventEmitter<VendorView>();

  readonly requiredCount = REQUIRED_ITEM_FIELDS.length + REQUIRED_SUMMARY_FIELDS.length;

  stage: Stage = 'setup';
  sampleFile: File | null = null;
  draft: VendorInvoiceTemplate | null = null;
  working = false;
  error = '';

  constructor(private store: VendorStoreService) {}

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['template'] || changes['vendorId']) {
      this.resetToSaved();
    }
  }

  private resetToSaved(): void {
    this.sampleFile = null;
    this.error = '';
    this.draft = this.template;
    this.stage = this.template?.validated ? 'review' : 'setup';
  }

  get canConfigure(): boolean {
    return !!this.vendorId;
  }

  get active(): VendorInvoiceTemplate | null {
    return this.draft ?? this.template;
  }

  get matchedCount(): number {
    const t = this.active;
    if (!t) {
      return 0;
    }
    return [...t.itemMappings, ...t.summaryMappings].filter(m => m.required && m.matched).length;
  }

  get mappingReady(): boolean {
    const t = this.active;
    return !!t && this.matchedCount === this.requiredCount;
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    this.sampleFile = input.files?.[0] ?? null;
    this.error = '';
  }

  analyze(): void {
    if (!this.sampleFile || !this.canConfigure) {
      this.error = 'Choose a sample invoice PDF to analyze.';
      return;
    }
    this.working = true;
    this.error = '';
    this.store.analyzeInvoiceSample(this.sampleFile.name).subscribe(template => {
      this.draft = template;
      this.stage = 'review';
      this.working = false;
    });
  }

  saveTemplate(): void {
    if (!this.vendorId || !this.draft || !this.mappingReady) {
      this.error = 'All required fields must be mapped before saving.';
      return;
    }
    this.working = true;
    this.store.saveInvoiceTemplate(this.vendorId, this.draft).subscribe(view => {
      this.working = false;
      this.changed.emit(view);
    });
  }

  removeTemplate(): void {
    if (!this.vendorId) {
      return;
    }
    this.working = true;
    this.store.removeInvoiceTemplate(this.vendorId).subscribe(view => {
      this.working = false;
      this.changed.emit(view);
    });
  }

  reanalyze(): void {
    this.draft = this.template?.validated ? this.template : null;
    this.sampleFile = null;
    this.stage = 'setup';
  }

  trackField(_: number, m: VendorFieldMapping): string {
    return m.targetField;
  }
}
