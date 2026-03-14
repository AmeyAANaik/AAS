import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { finalize } from 'rxjs/operators';
import { BillsService } from '../bills.service';
import { InvoiceOption, OptionItem, PaymentPayload } from '../bills.model';

@Component({
  selector: 'app-payment-form',
  templateUrl: './payment-form.component.html',
  styleUrl: './payment-form.component.scss'
})
export class PaymentFormComponent {
  @Input() customers: OptionItem[] = [];
  @Input() invoices: InvoiceOption[] = [];
  @Input() defaultCompany = 'aas';
  @Output() created = new EventEmitter<void>();

  form: FormGroup = this.fb.group({
    invoiceId: [''],
    customer: ['', Validators.required],
    company: [this.defaultCompany, Validators.required],
    amount: [0, [Validators.required, Validators.min(0.01)]],
    referenceNo: [''],
    referenceDate: ['']
  });

  statusMessage = '';
  isSubmitting = false;
  selectedInvoice?: InvoiceOption;

  constructor(private fb: FormBuilder, private billsService: BillsService) {}

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const customer = this.selectedInvoice?.customer || String(value.customer ?? '').trim();
    const company = this.selectedInvoice?.company || String(value.company ?? '').trim();
    const payload: PaymentPayload = {
      customer,
      company,
      amount: Number(value.amount || 0),
      invoiceId: String(value.invoiceId ?? '').trim() || undefined,
      referenceNo: String(value.referenceNo ?? '').trim() || undefined,
      referenceDate: String(value.referenceDate ?? '').trim() || undefined
    };
    this.isSubmitting = true;
    this.statusMessage = 'Recording payment...';
    this.billsService
      .createPayment(payload)
      .pipe(finalize(() => (this.isSubmitting = false)))
      .subscribe({
        next: () => {
          this.statusMessage = 'Payment recorded.';
          this.created.emit();
          this.reset();
        },
        error: err => {
          this.statusMessage = this.formatError(err, 'Unable to record payment');
        }
      });
  }

  reset(): void {
    this.form.reset({
      invoiceId: '',
      customer: '',
      company: this.defaultCompany,
      amount: 0,
      referenceNo: '',
      referenceDate: ''
    });
    this.selectedInvoice = undefined;
  }

  onInvoiceChange(): void {
    const invoiceId = String(this.form.get('invoiceId')?.value ?? '').trim();
    if (!invoiceId) {
      this.selectedInvoice = undefined;
      this.syncCompanyFromCustomer();
      return;
    }
    const invoice = this.invoices.find(entry => entry.id === invoiceId);
    if (!invoice) {
      this.selectedInvoice = undefined;
      this.syncCompanyFromCustomer();
      return;
    }
    this.selectedInvoice = invoice;
    this.form.patchValue({ customer: invoice.customer, company: invoice.company });
    if (Number.isFinite(invoice.outstanding)) {
      this.form.patchValue({ amount: invoice.outstanding });
    }
  }

  onCustomerChange(): void {
    if (this.selectedInvoice) {
      return;
    }
    this.syncCompanyFromCustomer();
  }

  get pendingBalance(): number | null {
    if (!this.selectedInvoice) {
      return null;
    }
    return Number(this.selectedInvoice.outstanding || 0);
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

  get companyDisplay(): string {
    return String(this.form.get('company')?.value ?? '').trim() || 'Select branch or invoice to lock company';
  }

  private syncCompanyFromCustomer(): void {
    const customerId = String(this.form.get('customer')?.value ?? '').trim();
    const company = this.customers.find(customer => customer.id === customerId)?.company?.trim() || this.defaultCompany;
    this.form.patchValue({ company }, { emitEvent: false });
  }

  private formatError(err: unknown, fallback: string): string {
    if (err instanceof Error) {
      return err.message;
    }
    if (typeof err === 'string') {
      return err;
    }
    return fallback;
  }
}
