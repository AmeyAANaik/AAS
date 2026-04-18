import { Component, ElementRef, EventEmitter, Input, Output, ViewChild } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { finalize } from 'rxjs/operators';
import { formatUiError } from '../../shared/error-message.util';
import { AuthTokenService } from '../../shared/auth-token.service';
import { BillsService } from '../bills.service';
import { InvoiceOption, OptionItem, PaymentPayload } from '../bills.model';

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

@Component({
  selector: 'app-payment-form',
  templateUrl: './payment-form.component.html',
  styleUrl: './payment-form.component.scss'
})
export class PaymentFormComponent {
  @Input() customers: OptionItem[] = [];
  @Input() vendors: OptionItem[] = [];
  @Input() categories: OptionItem[] = [];
  @Input() invoices: InvoiceOption[] = [];
  @Input() defaultCompany = 'aas';
  @Output() created = new EventEmitter<void>();

  @ViewChild('paymentDetails') paymentDetails?: ElementRef<HTMLElement>;
  @ViewChild('amountInput') amountInput?: ElementRef<HTMLInputElement>;

  form: FormGroup = this.fb.group({
    partyType: ['Customer', Validators.required],
    customer: ['', Validators.required],
    company: [this.defaultCompany, Validators.required],
    categoryId: ['', Validators.required],
    amount: [0, [Validators.required, Validators.min(0.01)]],
    paymentDate: [new Date(), Validators.required],
    modeOfPayment: [''],
    evidence: ['']
  });

  statusMessage = '';
  isSubmitting = false;
  selectedDue = 0;
  underReviewAmount = 0;
  availableDueAmount = 0;
  hasLoadedDue = false;
  voucherFiles: File[] = [];
  readonly modeOfPaymentOptions = ['Cash', 'UPI Transfer', 'Check'];

  constructor(
    private fb: FormBuilder,
    private billsService: BillsService,
    private tokenStore: AuthTokenService
  ) {}

  get canRecordPayments(): boolean {
    const role = String(this.tokenStore.getRole() ?? '').trim().toLowerCase();
    return role === 'admin' || role === 'helper';
  }

  get partyLabel(): string {
    return this.isSupplierMode ? 'Vendor (Supplier)' : 'Branch (Customer)';
  }

  get modeLabel(): string {
    return this.isSupplierMode ? 'Vendor (Supplier)' : 'Branch (Customer)';
  }

  get partyOptions(): OptionItem[] {
    return this.isSupplierMode ? this.vendors : this.customers;
  }

  get isSupplierMode(): boolean {
    return String(this.form.get('partyType')?.value ?? '').toLowerCase() === 'supplier';
  }

  get selectedPartyName(): string {
    const partyId = String(this.form.get('customer')?.value ?? '').trim();
    const match = this.partyOptions.find(option => option.id === partyId);
    return match?.name || '';
  }

  get selectedCategoryName(): string {
    const categoryId = String(this.form.get('categoryId')?.value ?? '').trim();
    const match = this.categories.find(option => option.id === categoryId);
    return match?.name || '';
  }

  onPartyTypeChange(): void {
    const partyType = this.isSupplierMode ? 'Supplier' : 'Customer';
    this.form.patchValue({ partyType }, { emitEvent: false });
    this.resetDownstream({
      customer: '',
      categoryId: '',
      selectedDue: 0,
      underReviewAmount: 0,
      availableDueAmount: 0,
      hasLoadedDue: false
    });
  }

  onPartyChange(): void {
    const selectedPartyId = String(this.form.get('customer')?.value ?? '').trim();
    const selectedParty = this.partyOptions.find(party => party.id === selectedPartyId);
    const categoryId = this.isSupplierMode ? String(selectedParty?.categoryId ?? '').trim() : '';
    this.resetDownstream({
      customer: selectedPartyId,
      categoryId,
      selectedDue: 0,
      underReviewAmount: 0,
      availableDueAmount: 0,
      hasLoadedDue: false
    });
    if (!this.isSupplierMode) {
      this.syncCompanyFromCustomer();
    } else {
      this.form.patchValue({ company: this.defaultCompany }, { emitEvent: false });
    }
    if (categoryId) {
      this.onCategoryChange();
    }
  }

  onCategoryChange(): void {
    const partyType = this.isSupplierMode ? 'Supplier' : 'Customer';
    const partyId = String(this.form.get('customer')?.value ?? '').trim();
    const categoryId = String(this.form.get('categoryId')?.value ?? '').trim();
    if (!partyId || !categoryId) {
      this.resetDownstream({ selectedDue: 0, underReviewAmount: 0, availableDueAmount: 0, hasLoadedDue: false });
      return;
    }
    this.hasLoadedDue = false;
    this.statusMessage = 'Loading due amount...';
    this.billsService.dueByCategory(partyType, partyId, categoryId).subscribe({
      next: result => {
        const dueAmount = Number(result?.dueAmount ?? 0);
        const reservedAmount = Number((result as any)?.underReviewAmount ?? 0);
        const availableAmount = Number((result as any)?.availableDueAmount ?? dueAmount);
        const safeReserved = Number.isFinite(reservedAmount) ? reservedAmount : 0;
        const safeAvailable = Number.isFinite(availableAmount) ? availableAmount : 0;
        this.resetDownstream({
          selectedDue: Number.isFinite(dueAmount) ? dueAmount : 0,
          underReviewAmount: safeReserved,
          availableDueAmount: safeAvailable,
          amountPrefill: safeAvailable,
          hasLoadedDue: true
        });
        this.applyAmountLimitValidator();
        this.statusMessage = '';
        this.scrollToPaymentDetails();
      },
      error: err => {
        this.statusMessage = this.formatError(err, 'Unable to load due amount');
        this.resetDownstream({ selectedDue: 0, underReviewAmount: 0, availableDueAmount: 0, hasLoadedDue: false });
      }
    });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    if (!this.voucherFiles.length) {
      this.statusMessage = 'Please upload evidence before recording the payment.';
      return;
    }
    const value = this.form.getRawValue();
    const customer = String(value.customer ?? '').trim();
    const company = String(value.company ?? '').trim();
    const partyType = String(value.partyType ?? '').trim() || 'Customer';
    const payload: PaymentPayload = {
      customer,
      company,
      amount: Number(value.amount || 0),
      paymentDate: formatApiDate(value.paymentDate),
      modeOfPayment: String(value.modeOfPayment ?? '').trim() || undefined,
      partyType,
      categoryId: String(value.categoryId ?? '').trim() || undefined
    };
    this.isSubmitting = true;
    this.statusMessage = 'Recording payment...';
    this.billsService
      .createPaymentWithAttachments(payload, this.voucherFiles)
      .pipe(finalize(() => (this.isSubmitting = false)))
      .subscribe({
        next: result => {
          const payment = (result as { payment?: any })?.payment ?? {};
          const reviewStatus = String(payment?.aas_payment_review_status ?? '').trim();
          const docstatus = Number(payment?.docstatus ?? NaN);
          const isUnderReview = reviewStatus.toUpperCase() === 'UNDER_REVIEW' || docstatus === 0;
          const count = Array.isArray((result as any)?.files) ? (result as any).files.length : 0;
          if (isUnderReview) {
            this.statusMessage = count > 0
              ? `Payment submitted for admin review with ${count} attachment(s).`
              : 'Payment submitted for admin review.';
          } else {
            this.statusMessage = count > 0 ? `Payment recorded with ${count} attachment(s).` : 'Payment recorded.';
          }
          this.created.emit();
          this.reset();
        },
        error: err => {
          const errorCode = String((err as any)?.error?.errorCode ?? '').trim();
          if (errorCode === 'OVERPAYMENT_NOT_ALLOWED') {
            const dueAmount = Number((err as any)?.error?.dueAmount ?? this.selectedDue);
            const reserved = Number((err as any)?.error?.underReviewAmount ?? this.underReviewAmount);
            const available = Number((err as any)?.error?.availableDueAmount ?? this.availableDueAmount);
            this.statusMessage = `Amount exceeds available due. Due: ₹${dueAmount.toFixed(2)}, Reserved: ₹${reserved.toFixed(2)}, Available: ₹${available.toFixed(2)}.`;
          } else {
            this.statusMessage = this.formatError(err, 'Unable to record payment');
          }
        }
      });
  }

  reset(): void {
    this.form.reset({
      partyType: 'Customer',
      customer: '',
      company: this.defaultCompany,
      categoryId: '',
      amount: 0,
      paymentDate: new Date(),
      modeOfPayment: '',
      evidence: ''
    });
    this.selectedDue = 0;
    this.underReviewAmount = 0;
    this.availableDueAmount = 0;
    this.hasLoadedDue = false;
    this.statusMessage = '';
    this.voucherFiles = [];
  }

  get pendingBalance(): number | null {
    if (!this.hasLoadedDue) {
      return null;
    }
    if (!Number.isFinite(this.selectedDue) || this.selectedDue <= 0) {
      return this.selectedDue === 0 ? 0 : null;
    }
    return Number(this.selectedDue);
  }

  get balanceAfterPayment(): number | null {
    if (this.pendingBalance === null) {
      return null;
    }
    const amount = Number(this.form.get('amount')?.value ?? 0);
    return this.pendingBalance - amount;
  }

  get surplusAmount(): number | null {
    const balance = this.balanceAfterPayment;
    if (balance === null) {
      return null;
    }
    return balance < 0 ? Math.abs(balance) : 0;
  }

  get dueToneClass(): string {
    const due = this.pendingBalance;
    if (due === null) {
      return '';
    }
    return due > 0 ? 'due-open' : 'due-clear';
  }

  get companyDisplay(): string {
    return String(this.form.get('company')?.value ?? '').trim() || 'Select branch to lock company';
  }

  private scrollToPaymentDetails(): void {
    const target = this.paymentDetails?.nativeElement;
    if (!target) {
      return;
    }
    setTimeout(() => {
      try {
        target.scrollIntoView({ behavior: 'smooth', block: 'start' });
      } catch (ignored) {
        // Ignore scroll failures (e.g., in unit tests).
      }
      const input = this.amountInput?.nativeElement;
      if (input) {
        try {
          input.focus();
          input.select();
        } catch (ignored) {
          // Ignore focus failures (e.g., in unit tests).
        }
      }
    });
  }

  private resetDownstream(options: {
    customer?: string;
    categoryId?: string;
    selectedDue?: number;
    underReviewAmount?: number;
    availableDueAmount?: number;
    hasLoadedDue?: boolean;
    amountPrefill?: number;
  }): void {
    const today = new Date();
    if (options.customer !== undefined) {
      this.form.patchValue({ customer: options.customer }, { emitEvent: false });
    }
    if (options.categoryId !== undefined) {
      this.form.patchValue({ categoryId: options.categoryId }, { emitEvent: false });
    }
    if (options.selectedDue !== undefined) {
      this.selectedDue = options.selectedDue;
    }
    if (options.underReviewAmount !== undefined) {
      this.underReviewAmount = options.underReviewAmount;
    }
    if (options.availableDueAmount !== undefined) {
      this.availableDueAmount = options.availableDueAmount;
    }
    if (options.hasLoadedDue !== undefined) {
      this.hasLoadedDue = options.hasLoadedDue;
    }
    const amount = options.amountPrefill !== undefined ? options.amountPrefill : 0;
    this.form.patchValue(
      {
        amount,
        paymentDate: today,
        modeOfPayment: '',
        evidence: ''
      },
      { emitEvent: false }
    );
    this.applyAmountLimitValidator();
    this.voucherFiles = [];
  }

  onVoucherFilesSelected(event: Event): void {
    const input = event.target as HTMLInputElement | null;
    const files = input?.files ? Array.from(input.files) : [];
    this.voucherFiles = files;
  }

  private syncCompanyFromCustomer(): void {
    const customerId = String(this.form.get('customer')?.value ?? '').trim();
    const company = this.customers.find(customer => customer.id === customerId)?.company?.trim() || this.defaultCompany;
    this.form.patchValue({ company }, { emitEvent: false });
  }

  private formatError(err: unknown, fallback: string): string {
    return formatUiError(err, fallback);
  }

  private applyAmountLimitValidator(): void {
    const amountControl = this.form.get('amount');
    if (!amountControl) {
      return;
    }
    const maxAmount = Number.isFinite(this.availableDueAmount) ? this.availableDueAmount : 0;
    amountControl.setValidators([Validators.required, Validators.min(0.01), Validators.max(maxAmount)]);
    amountControl.updateValueAndValidity({ emitEvent: false });
  }
}
