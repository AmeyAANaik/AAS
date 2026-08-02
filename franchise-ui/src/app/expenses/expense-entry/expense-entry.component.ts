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
import { DatePickerFieldComponent } from '../../shared/date-picker-field/date-picker-field.component';
import { EmptyStateComponent } from '../../shared/empty-state/empty-state.component';
import { ExpenseStoreService } from '../expense-store.service';
import { ExpenseEntry } from '../expense.model';
import { ExpenseCategoryRecord } from '../../master-data/expense-category.model';
import { ExpenseCategoryStoreService } from '../../master-data/expense-category-store.service';

@Component({
  selector: 'app-expense-entry',
  standalone: true,
  imports: [
    CommonModule, FormsModule, ReactiveFormsModule, MatButtonModule, MatFormFieldModule,
    MatIconModule, MatInputModule, MatSelectModule, MatSnackBarModule, MatTableModule,
    DatePickerFieldComponent, EmptyStateComponent
  ],
  templateUrl: './expense-entry.component.html',
  styleUrl: './expense-entry.component.css'
})
export class ExpenseEntryComponent implements OnInit, OnDestroy {
  readonly recentColumns = ['date', 'category', 'amount', 'remarks', 'bill'];

  categories: ExpenseCategoryRecord[] = [];
  recent: ExpenseEntry[] = [];
  billFileName = '';
  statusMessage = '';

  form: FormGroup = this.fb.group({
    date: [new Date().toISOString().slice(0, 10), Validators.required],
    category: ['', Validators.required],
    amount: [null as number | null, [Validators.required, Validators.min(0.01)]],
    remarks: ['']
  });

  private sub?: Subscription;
  private categorySub?: Subscription;

  constructor(
    private fb: FormBuilder,
    public store: ExpenseStoreService,
    private categoryStore: ExpenseCategoryStoreService,
    private snack: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.loadRecent();
    this.loadCategories();
    this.sub = this.store.changes$.subscribe(() => this.loadRecent());
    this.categorySub = this.categoryStore.changes$.subscribe(() => this.loadCategories());
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
    this.categorySub?.unsubscribe();
  }

  private loadRecent(): void {
    this.store.list().subscribe(entries => {
      this.recent = entries.slice(0, 20);
    });
  }

  private loadCategories(): void {
    this.categories = this.categoryStore.activeSnapshot();
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
      category: v.category,
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
