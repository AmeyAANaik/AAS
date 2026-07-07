import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { PageHeaderComponent } from '../../shared/page-header/page-header.component';
import { StatusPillComponent } from '../../shared/status-pill/status-pill.component';
import { ExpenseCategoryInput, ExpenseCategoryRecord, ExpenseCategoryStatus, ExpensePnlBucket } from '../expense-category.model';
import { ExpenseCategoryStoreService } from '../expense-category-store.service';

@Component({
  selector: 'app-expense-category-list',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatIconModule,
    MatInputModule, MatSelectModule, MatSnackBarModule, MatTableModule,
    PageHeaderComponent, StatusPillComponent
  ],
  templateUrl: './expense-category-list.component.html',
  styleUrl: './expense-category-list.component.css'
})
export class ExpenseCategoryListComponent implements OnInit {
  readonly columns = ['name', 'code', 'bucket', 'status', 'actions'];
  readonly statuses: ExpenseCategoryStatus[] = ['Active', 'Inactive'];
  readonly buckets: Array<{ value: ExpensePnlBucket; label: string }> = [
    { value: 'rent', label: 'Rent' },
    { value: 'electricity', label: 'Electricity' },
    { value: 'other', label: 'Other expenses' }
  ];

  categories: ExpenseCategoryRecord[] = [];
  formOpen = false;
  editingId: string | null = null;

  form = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(60)]],
    code: ['', [Validators.required, Validators.maxLength(24)]],
    pnlBucket: ['other' as ExpensePnlBucket, Validators.required],
    status: ['Active' as ExpenseCategoryStatus, Validators.required]
  });

  constructor(
    private store: ExpenseCategoryStoreService,
    private fb: FormBuilder,
    private snack: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.store.list().subscribe(categories => (this.categories = categories));
  }

  openCreate(): void {
    this.editingId = null;
    this.form.reset({ name: '', code: '', pnlBucket: 'other', status: 'Active' });
    this.formOpen = true;
  }

  openEdit(category: ExpenseCategoryRecord): void {
    this.editingId = category.id;
    this.form.reset({
      name: category.name,
      code: category.code,
      pnlBucket: category.pnlBucket,
      status: category.status
    });
    this.formOpen = true;
  }

  closeForm(): void {
    this.formOpen = false;
    this.editingId = null;
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    const payload: ExpenseCategoryInput = {
      name: v.name!,
      code: v.code!,
      pnlBucket: v.pnlBucket as ExpensePnlBucket,
      status: v.status as ExpenseCategoryStatus
    };
    const op = this.editingId ? this.store.update(this.editingId, payload) : this.store.create(payload);
    op.subscribe(() => {
      this.snack.open(this.editingId ? 'Expense category updated' : 'Expense category created', 'OK', { duration: 2200 });
      this.closeForm();
      this.reload();
    });
  }

  toggle(category: ExpenseCategoryRecord): void {
    const next = category.status === 'Active' ? 'Inactive' : 'Active';
    this.store.setStatus(category.id, next).subscribe(() => {
      this.snack.open(`${category.name} set ${next}`, 'OK', { duration: 1800 });
      this.reload();
    });
  }

  bucketLabel(value: ExpensePnlBucket): string {
    return this.buckets.find(bucket => bucket.value === value)?.label ?? value;
  }
}
