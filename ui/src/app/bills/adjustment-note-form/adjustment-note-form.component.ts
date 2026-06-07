import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { finalize } from 'rxjs/operators';
import { formatUiError } from '../../shared/error-message.util';
import { AuthTokenService } from '../../shared/auth-token.service';
import { AdjustmentNotePayload, InvoiceSummary, OptionItem, PaymentDueSummary } from '../bills.model';
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

type InvoiceOption = {
  id: string;
  label: string;
  categoryId: string;
  raw: InvoiceSummary;
};

@Component({
  selector: 'app-adjustment-note-form',
  templateUrl: './adjustment-note-form.component.html',
  styleUrl: './adjustment-note-form.component.scss'
})
export class AdjustmentNoteFormComponent {
  @Input() customers: OptionItem[] = [];
  @Input() vendors: OptionItem[] = [];
  @Input() categories: OptionItem[] = [];
  @Output() created = new EventEmitter<void>();

  form: FormGroup = this.fb.group({
    partyType: ['Customer', Validators.required],
    partyId: ['', Validators.required],
    categoryId: ['', Validators.required],
    invoiceId: [''],
    direction: ['GIVE', Validators.required],
    amount: [0, [Validators.required, Validators.min(0.01)]],
    noteDate: [new Date(), Validators.required],
    reason: [''],
    evidence: ['']
  });

  invoiceOptions: InvoiceOption[] = [];
  noteFiles: File[] = [];
  statusMessage = '';
  isLoadingInvoices = false;
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

  get selectedInvoiceLabel(): string {
    const invoiceId = String(this.form.get('invoiceId')?.value ?? '').trim();
    return this.invoiceOptions.find(option => option.id === invoiceId)?.label || '';
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
    this.form.patchValue({ partyType, partyId: '', categoryId: '', invoiceId: '' }, { emitEvent: false });
    this.invoiceOptions = [];
    this.resetDue();
  }

  onPartyChange(): void {
    this.form.patchValue({ invoiceId: '' }, { emitEvent: false });
    this.invoiceOptions = [];
    this.resetDue();
    this.loadInvoices();
  }

  onCategoryChange(): void {
    this.form.patchValue({ invoiceId: '' }, { emitEvent: false });
    this.invoiceOptions = [];
    this.resetDue();
    this.loadInvoices();
    this.loadDue();
  }

  onInvoiceChange(): void {
    this.statusMessage = '';
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
    const payload: AdjustmentNotePayload = {
      partyType: String(value.partyType ?? '').trim(),
      partyId: String(value.partyId ?? '').trim(),
      categoryId: String(value.categoryId ?? '').trim(),
      direction: String(value.direction ?? '').trim(),
      amount: Number(value.amount ?? 0),
      noteDate: formatApiDate(value.noteDate),
      reason: String(value.reason ?? '').trim() || undefined
    };
    const invoiceId = String(value.invoiceId ?? '').trim();
    if (invoiceId) {
      payload.invoiceId = invoiceId;
    }
    this.isSubmitting = true;
    this.statusMessage = `Submitting ${this.noteLabel.toLowerCase()} for admin review...`;
    this.billsService.createAdjustmentNoteWithAttachments(payload, this.noteFiles)
      .pipe(finalize(() => (this.isSubmitting = false)))
      .subscribe({
        next: result => {
          const count = Array.isArray(result?.files) ? result.files.length : 0;
          this.statusMessage = count > 0
            ? `${this.noteLabel} submitted for admin review with ${count} attachment(s).`
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
      invoiceId: '',
      direction: 'GIVE',
      amount: 0,
      noteDate: new Date(),
      reason: '',
      evidence: ''
    });
    this.invoiceOptions = [];
    this.noteFiles = [];
    this.resetDue();
    this.statusMessage = '';
  }

  private loadInvoices(): void {
    const partyType = this.isSupplierMode ? 'Supplier' : 'Customer';
    const partyId = String(this.form.get('partyId')?.value ?? '').trim();
    const categoryId = String(this.form.get('categoryId')?.value ?? '').trim();
    if (!partyId) {
      return;
    }
    this.isLoadingInvoices = true;
    this.statusMessage = 'Loading related invoices...';
    this.billsService.listInvoiceOptions(partyType, partyId)
      .pipe(finalize(() => (this.isLoadingInvoices = false)))
      .subscribe({
        next: invoices => {
          this.invoiceOptions = (invoices ?? []).map(invoice => {
            const id = String(invoice.name ?? '').trim();
            const date = String(invoice.posting_date ?? '').trim();
            const outstanding = Number(invoice.outstanding_amount ?? invoice.grand_total ?? 0);
            const invoiceCategoryId = String(invoice.aas_category ?? '').trim();
            return {
              id,
              label: `${id} • ${date || 'No date'} • ${invoiceCategoryId || 'No category'} • Due ₹${outstanding.toFixed(2)}`,
              categoryId: invoiceCategoryId,
              raw: invoice
            };
          }).filter(option => !!option.id)
            .filter(option => !categoryId || option.categoryId === categoryId);
          this.statusMessage = this.invoiceOptions.length
            ? ''
            : (categoryId
                ? 'No invoices found for the selected party and category.'
                : 'No related invoices found for the selected party.');
        },
        error: err => {
          this.invoiceOptions = [];
          this.statusMessage = formatUiError(err, 'Unable to load related invoices.');
        }
      });
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
