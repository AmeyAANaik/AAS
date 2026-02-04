import { Component, EventEmitter, Input, Output } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { finalize } from 'rxjs/operators';
import { BillsService } from '../bills.service';
import { OptionItem, PaymentPayload } from '../bills.model';

@Component({
  selector: 'app-payment-form',
  templateUrl: './payment-form.component.html',
  styleUrl: './payment-form.component.scss'
})
export class PaymentFormComponent {
  @Input() customers: OptionItem[] = [];
  @Input() defaultCompany = 'aas';
  @Output() created = new EventEmitter<void>();

  form: FormGroup = this.fb.group({
    customer: ['', Validators.required],
    company: [this.defaultCompany, Validators.required],
    amount: [0, [Validators.required, Validators.min(0.01)]],
    referenceNo: [''],
    referenceDate: ['']
  });

  statusMessage = '';
  isSubmitting = false;

  constructor(private fb: FormBuilder, private billsService: BillsService) {}

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue();
    const payload: PaymentPayload = {
      customer: String(value.customer ?? '').trim(),
      company: String(value.company ?? '').trim(),
      amount: Number(value.amount || 0),
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
      customer: '',
      company: this.defaultCompany,
      amount: 0,
      referenceNo: '',
      referenceDate: ''
    });
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
