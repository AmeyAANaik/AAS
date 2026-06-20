import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { finalize } from 'rxjs/operators';
import { formatUiError } from '../../shared/error-message.util';
import { AuthTokenService } from '../../shared/auth-token.service';
import { AdjustmentNotePayload, OptionItem, PaymentDueSummary } from '../bills.model';
import { BillsService } from '../bills.service';

function formatApiDate(value: unknown): string | undefined {
  if (!value) {
    return undefined;
  }
  if (value instanceof Date && !Number.isNaN(value.getTime())) {
    const year = value.getFullYear();
    const month = String(value.getMonth() + 1).padStart(2, '0');
    const day = String(value.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }
  const text = String(value).trim();
  return text || undefined;
}

type ItemOption = { id: string; name: string; code: string; group: string };

@Component({
  selector: 'app-adjustment-note-form',
  templateUrl: './adjustment-note-form.component.html',
  styleUrl: './adjustment-note-form.component.scss'
})
export class AdjustmentNoteFormComponent implements OnInit {
  @Input() customers: OptionItem[] = [];
  @Input() vendors: OptionItem[] = [];
  @Input() categories: OptionItem[] = [];
  @Output() created = new EventEmitter<void>();

  form: FormGroup = this.fb.group({
    partyType: ['Customer', Validators.required],
    partyId: ['', Validators.required],
    categoryId: ['', Validators.required],
    itemCode: ['', Validators.required],
    direction: ['GIVE', Validators.required],
    amount: [0, [Validators.required, Validators.min(0.01)]],
    noteDate: [new Date(), Validators.required],
    reason: [''],
    evidence: ['']
  });

  itemOptions: ItemOption[] = [];
  noteFiles: File[] = [];
  statusMessage = '';
  isLoadingItems = false;
  isLoadingDue = false;
  isSubmitting = false;
  selectedDue = 0;
  availableReducibleDue = 0;
  pendingPaymentApprovalAmount = 0;
  pendingAdjustmentAmount = 0;
  hasLoadedDue = false;

  constructor(
    private readonly fb: FormBuilder,
    private readonly billsService: BillsService,
    private readonly tokenStore: AuthTokenService
  ) {}

  ngOnInit(): void {
    this.loadItems();
  }

  get canCreateNotes(): boolean {
    const role = String(this.tokenStore.getRole() ?? '').trim().toLowerCase();
    return role === 'admin' || role === 'administrator' || role === 'helper';
  }

  get isSupplierMode(): boolean {
    return String(this.form.get('partyType')?.value ?? '').toLowerCase() === 'supplier';
  }

  get partyLabel(): string {
    return this.isSupplierMode ? 'Vendor (Supplier)' : 'Branch (Customer)';
  }

  get noteLabel(): string {
    return this.isGiveMode ? 'Credit note' : 'Debit note';
  }

  get directionLabel(): string {
    return this.isGiveMode ? 'Give' : 'Take';
  }

  get partyOptions(): OptionItem[] {
    return this.isSupplierMode ? this.vendors : this.customers;
  }

  get selectedPartyName(): string {
    const partyId = String(this.form.get('partyId')?.value ?? '').trim();
    const match = this.partyOptions.find(option => option.id === partyId);
    return match?.name || '';
  }

  get selectedCategoryName(): string {
    const categoryId = String(this.form.get('categoryId')?.value ?? '').trim();
    const match = this.categories.find(option => option.id === categoryId);
    return match?.name || '';
  }

  get selectedItemLabel(): string {
    const itemCode = String(this.form.get('itemCode')?.value ?? '').trim();
    const match = this.itemOptions.find(o => o.code === itemCode);
    if (!match) return '';
    return match.name ? `${match.name} (${match.code})` : match.code;
  }

  get isGiveMode(): boolean {
    return String(this.form.get('direction')?.value ?? '').toUpperCase() !== 'TAKE';
  }

  get dueImpact(): number {
    const amount = Number(this.form.get('amount')?.value ?? 0);
    if (!Number.isFinite(amount) || amount <= 0) {
      return 0;
    }
    if (this.isSupplierMode) {
      return this.isGiveMode ? amount : -amount;
    }
    return this.isGiveMode ? -amount : amount;
  }

  get dueAfterApproval(): number {
    return this.selectedDue + this.dueImpact;
  }

  get reducesDue(): boolean {
    return this.isSupplierMode ? !this.isGiveMode : this.isGiveMode;
  }

  get amountExceedsReducibleDue(): boolean {
    const amount = Number(this.form.get('amount')?.value ?? 0);
    if (!this.hasLoadedDue || !this.reducesDue || !Number.isFinite(amount) || amount <= 0) {
      return false;
    }
    return amount > this.availableReducibleDue + 0.0001;
  }

  onPartyTypeChange(): void {
    const partyType = this.isSupplierMode ? 'Supplier' : 'Customer';
    this.form.patchValue({ partyType, partyId: '', categoryId: '', itemCode: '' }, { emitEvent: false });
    this.resetDue();
  }

  onPartyChange(): void {
    this.form.patchValue({ itemCode: '' }, { emitEvent: false });
    this.resetDue();
  }

  onCategoryChange(): void {
    this.form.patchValue({ itemCode: '' }, { emitEvent: false });
    this.resetDue();
    this.loadDue();
  }

  onFilesSelected(event: Event): void {
    const target = event.target as HTMLInputElement | null;
    this.noteFiles = Array.from(target?.files ?? []);
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    if (!this.noteFiles.length) {
      this.statusMessage = 'Please upload evidence before submitting the note.';
      return;
    }
    if (this.amountExceedsReducibleDue) {
      this.statusMessage = `${this.noteLabel} cannot exceed the available due of ₹${this.availableReducibleDue.toFixed(2)}.`;
      return;
    }
    const value = this.form.getRawValue();
    const selectedItem = this.itemOptions.find(o => o.code === String(value.itemCode ?? '').trim());
    const payload: AdjustmentNotePayload = {
      partyType: String(value.partyType ?? '').trim(),
      partyId: String(value.partyId ?? '').trim(),
      categoryId: String(value.categoryId ?? '').trim(),
      itemCode: String(value.itemCode ?? '').trim(),
      itemName: selectedItem?.name || undefined,
      direction: String(value.direction ?? '').trim(),
      amount: Number(value.amount ?? 0),
      noteDate: formatApiDate(value.noteDate),
      reason: String(value.reason ?? '').trim() || undefined
    };
    this.isSubmitting = true;
    this.statusMessage = `Submitting ${this.noteLabel.toLowerCase()} for admin review...`;
    this.billsService.createAdjustmentNoteWithAttachments(payload, this.noteFiles)
      .pipe(finalize(() => (this.isSubmitting = false)))
      .subscribe({
        next: result => {
          const files = Array.isArray(result?.files) ? result.files : [];
          const pdfFile = files.find(f => (f.fileName ?? '').toLowerCase().endsWith('.pdf'));
          if (pdfFile?.fileUrl) {
            this.downloadPdfUrl(pdfFile.fileUrl);
          }
          const count = files.length;
          this.statusMessage = count > 0
            ? `${this.noteLabel} submitted for admin review with ${count} attachment(s). PDF downloaded.`
            : `${this.noteLabel} submitted for admin review.`;
          this.created.emit();
          this.reset();
        },
        error: err => {
          this.statusMessage = formatUiError(err, `Unable to submit ${this.noteLabel.toLowerCase()}.`);
        }
      });
  }

  reset(): void {
    this.form.reset({
      partyType: 'Customer',
      partyId: '',
      categoryId: '',
      itemCode: '',
      direction: 'GIVE',
      amount: 0,
      noteDate: new Date(),
      reason: '',
      evidence: ''
    });
    this.noteFiles = [];
    this.resetDue();
    this.statusMessage = '';
  }

  private loadItems(): void {
    this.isLoadingItems = true;
    this.billsService.listItemsForNote()
      .pipe(finalize(() => (this.isLoadingItems = false)))
      .subscribe({
        next: items => { this.itemOptions = items ?? []; },
        error: () => { this.itemOptions = []; }
      });
  }

  private downloadPdfUrl(fileUrl: string): void {
    try {
      const a = document.createElement('a');
      a.href = fileUrl;
      a.download = '';
      a.target = '_blank';
      document.body.appendChild(a);
      a.click();
      document.body.removeChild(a);
    } catch {
      // best-effort: PDF is still attached to the note in ERPNext
    }
  }

  private loadDue(): void {
    const partyType = this.isSupplierMode ? 'Supplier' : 'Customer';
    const partyId = String(this.form.get('partyId')?.value ?? '').trim();
    const categoryId = String(this.form.get('categoryId')?.value ?? '').trim();
    if (!partyId || !categoryId) {
      return;
    }
    this.isLoadingDue = true;
    this.statusMessage = 'Loading due impact...';
    this.billsService.dueByCategory(partyType, partyId, categoryId)
      .pipe(finalize(() => (this.isLoadingDue = false)))
      .subscribe({
        next: summary => {
          const safeSummary = summary as PaymentDueSummary;
          this.selectedDue = Number(safeSummary?.dueAmount ?? 0) || 0;
          this.pendingPaymentApprovalAmount = Number(safeSummary?.pendingAdminApprovalAmount ?? safeSummary?.underReviewAmount ?? 0) || 0;
          this.pendingAdjustmentAmount = Number(safeSummary?.pendingAdjustmentAmount ?? 0) || 0;
          const availableDueAmount = Number(safeSummary?.availableDueAmount ?? this.selectedDue - this.pendingPaymentApprovalAmount) || 0;
          this.availableReducibleDue = Math.max(0, availableDueAmount + Math.min(0, this.pendingAdjustmentAmount));
          this.hasLoadedDue = true;
          this.statusMessage = '';
        },
        error: err => {
          this.resetDue();
          this.statusMessage = formatUiError(err, 'Unable to load due impact.');
        }
      });
  }

  private resetDue(): void {
    this.selectedDue = 0;
    this.availableReducibleDue = 0;
    this.pendingPaymentApprovalAmount = 0;
    this.pendingAdjustmentAmount = 0;
    this.hasLoadedDue = false;
  }
}
