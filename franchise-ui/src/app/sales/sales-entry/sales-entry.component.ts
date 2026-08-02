import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormArray, FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { Subscription } from 'rxjs';
import { DatePickerFieldComponent } from '../../shared/date-picker-field/date-picker-field.component';
import { EmptyStateComponent } from '../../shared/empty-state/empty-state.component';
import { PaymentLineInput, PaymentMode, SaleEntry, paymentsTotal } from '../sales.model';
import { SalesStoreService } from '../sales-store.service';

@Component({
  selector: 'app-sales-entry',
  standalone: true,
  imports: [
    CommonModule, FormsModule, ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatIconModule,
    MatInputModule, MatSelectModule, MatSnackBarModule, MatTableModule, DatePickerFieldComponent, EmptyStateComponent
  ],
  templateUrl: './sales-entry.component.html',
  styleUrl: './sales-entry.component.css'
})
export class SalesEntryComponent implements OnInit, OnDestroy {
  recent: SaleEntry[] = [];
  readonly recentColumns = ['date', 'gross', 'gst', 'discount', 'net', 'payments', 'remarks'];

  modes: PaymentMode[] = [];

  /** Tolerance (₹) before the payments-vs-net mismatch warning fires. */
  private readonly reconcileTolerance = 1;

  form: FormGroup = this.fb.group({
    date: [new Date().toISOString().slice(0, 10), Validators.required],
    grossSales: [0, [Validators.required, Validators.min(0)]],
    gstAmount: [0, [Validators.required, Validators.min(0)]],
    discount: [0, [Validators.required, Validators.min(0)]],
    payments: this.fb.array([])
  });

  // entry-level remark kept outside validation (optional)
  remarks = '';

  private sub?: Subscription;

  constructor(
    private fb: FormBuilder,
    private store: SalesStoreService,
    private snack: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.loadModes();
    this.loadRecent();
    this.sub = this.store.changes$.subscribe(() => {
      this.loadModes();
      this.loadRecent();
    });
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  get payments(): FormArray {
    return this.form.get('payments') as FormArray;
  }

  private loadModes(): void {
    this.modes = this.store.activePaymentModesSnapshot();
    if (this.payments.length === 0) {
      // Pre-populate one line per active mode so the common case is one tap.
      this.modes.forEach(m => this.payments.push(this.newPaymentLine(m.id)));
      if (this.payments.length === 0) {
        this.payments.push(this.newPaymentLine(''));
      }
    }
  }

  private newPaymentLine(modeId: string): FormGroup {
    return this.fb.group({
      modeId: [modeId, Validators.required],
      amount: [0, [Validators.required, Validators.min(0)]],
      remark: [''],
      attachmentName: ['']
    });
  }

  addPaymentLine(): void {
    const firstMode = this.modes[0]?.id ?? '';
    this.payments.push(this.newPaymentLine(firstMode));
  }

  removePaymentLine(index: number): void {
    this.payments.removeAt(index);
    if (this.payments.length === 0) {
      this.addPaymentLine();
    }
  }

  onAttachmentSelected(event: Event, index: number): void {
    const input = event.target as HTMLInputElement;
    const name = input.files?.[0]?.name ?? '';
    this.payments.at(index).get('attachmentName')?.setValue(name);
  }

  clearAttachment(index: number): void {
    this.payments.at(index).get('attachmentName')?.setValue('');
  }

  private loadRecent(): void {
    this.store.list().subscribe(entries => {
      this.recent = entries.slice(0, 15);
    });
  }

  private num(name: string): number {
    const v = Number(this.form.get(name)?.value);
    return Number.isFinite(v) ? v : 0;
  }

  get netSales(): number {
    return this.round(this.num('grossSales') - this.num('discount'));
  }

  get paymentTotal(): number {
    return paymentsTotal(this.collectPayments());
  }

  get reconcileDiff(): number {
    return this.round(this.paymentTotal - this.netSales);
  }

  get isReconciled(): boolean {
    return Math.abs(this.reconcileDiff) <= this.reconcileTolerance;
  }

  /** Display helper for the recent table (e.g. "Cash ₹6,000 · UPI ₹9,000"). */
  paymentSummary(entry: SaleEntry): string {
    if (!entry.payments.length) {
      return '—';
    }
    return entry.payments
      .map(p => `${p.modeName} ₹${p.amount.toLocaleString('en-IN')}`)
      .join(' · ');
  }

  modeName(modeId: string): string {
    return this.modes.find(m => m.id === modeId)?.name ?? '';
  }

  private collectPayments(): PaymentLineInput[] {
    return this.payments.controls
      .map(ctrl => {
        const modeId = String(ctrl.get('modeId')?.value ?? '');
        return {
          modeId,
          modeName: this.modeName(modeId),
          amount: Number(ctrl.get('amount')?.value) || 0,
          remark: String(ctrl.get('remark')?.value ?? ''),
          attachmentName: String(ctrl.get('attachmentName')?.value ?? '')
        } as PaymentLineInput;
      })
      .filter(p => p.modeId && p.amount > 0);
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.snack.open('Enter valid amounts (0 or more), a date and a payment mode per line.', 'OK', { duration: 2800 });
      return;
    }

    const payments = this.collectPayments();
    if (!payments.length) {
      this.snack.open('Add at least one payment line with an amount.', 'OK', { duration: 2800 });
      return;
    }

    if (!this.isReconciled) {
      const diff = this.reconcileDiff;
      const direction = diff > 0 ? 'more than' : 'less than';
      this.snack.open(
        `Payments (₹${this.paymentTotal}) are ${direction} net sales (₹${this.netSales}) by ₹${Math.abs(diff)} — saved anyway.`,
        'OK',
        { duration: 3600 }
      );
    }

    this.store.create({
      date: this.form.value.date,
      grossSales: this.num('grossSales'),
      gstAmount: this.num('gstAmount'),
      discount: this.num('discount'),
      payments,
      remarks: this.remarks
    }).subscribe(() => {
      if (this.isReconciled) {
        this.snack.open('Sale recorded', 'OK', { duration: 2400 });
      }
      this.reset();
    });
  }

  private reset(): void {
    this.form.reset({
      date: new Date().toISOString().slice(0, 10),
      grossSales: 0, gstAmount: 0, discount: 0
    });
    this.payments.clear();
    this.loadModes();
    this.remarks = '';
  }

  private round(n: number): number {
    return Math.round(n * 100) / 100;
  }
}
