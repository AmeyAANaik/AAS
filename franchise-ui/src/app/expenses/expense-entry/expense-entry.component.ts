import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { Subscription } from 'rxjs';
import { EmptyStateComponent } from '../../shared/empty-state/empty-state.component';
import { PageHeaderComponent } from '../../shared/page-header/page-header.component';
import { ExpenseStoreService } from '../expense-store.service';
import { EXPENSE_CATEGORIES, ExpenseCategory, ExpenseEntry } from '../expense.model';

@Component({
  selector: 'app-expense-entry',
  standalone: true,
  imports: [
    CommonModule, FormsModule, ReactiveFormsModule, MatButtonModule, MatFormFieldModule,
    MatIconModule, MatInputModule, MatSelectModule, MatSnackBarModule, MatTableModule,
    EmptyStateComponent
  ],
  templateUrl: './expense-entry.component.html',
  styleUrl: './expense-entry.component.css'
})
export class ExpenseEntryComponent implements OnInit, OnDestroy {
  readonly categories = EXPENSE_CATEGORIES;
  readonly recentColumns = ['date', 'category', 'amount', 'remarks', 'bill'];

  recent: ExpenseEntry[] = [];
  billFileName = '';
  statusMessage = '';

  form: FormGroup = this.fb.group({
    date: [new Date().toISOString().slice(0, 10), Validators.required],
    category: ['' as ExpenseCategory | '', Validators.required],
    amount: [null as number | null, [Validators.required, Validators.min(0.01)]],
    remarks: ['']
  });

  private sub?: Subscription;

  constructor(
    private fb: FormBuilder,
    public store: ExpenseStoreService,
    private snack: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.loadRecent();
    this.sub = this.store.changes$.subscribe(() => this.loadRecent());
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  private loadRecent(): void {
    this.store.list().subscribe(entries => {
      this.recent = entries.slice(0, 20);
    });
  }

  onBillSelected(event: Event): void {
    const input = event.target as HTMLInputElement | null;
    const file = input?.files?.[0];
    if (file) {
      this.billFileName = file.name;
    }
    if (input) { input.value = ''; }
  }

  clearBill(): void {
    this.billFileName = '';
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.statusMessage = 'Enter a date, category and amount greater than zero.';
      return;
    }
    const v = this.form.getRawValue();
    this.store.create({
      date: v.date,
      category: v.category as ExpenseCategory,
      amount: Number(v.amount),
      remarks: v.remarks,
      billFileName: this.billFileName || undefined
    }).subscribe(() => {
      this.snack.open('Expense recorded', 'OK', { duration: 2600 });
      this.reset();
    });
  }

  private reset(): void {
    this.form.reset({
      date: new Date().toISOString().slice(0, 10),
      category: '',
      amount: null,
      remarks: ''
    });
    this.billFileName = '';
    this.statusMessage = '';
    this.loadRecent();
  }
}
