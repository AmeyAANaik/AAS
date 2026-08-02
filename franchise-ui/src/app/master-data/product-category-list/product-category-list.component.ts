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
import { ProductCategoryInput, ProductCategoryRecord, ProductCategoryStatus } from '../product-category.model';
import { ProductCategoryStoreService } from '../product-category-store.service';

@Component({
  selector: 'app-product-category-list',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatIconModule,
    MatInputModule, MatSelectModule, MatSnackBarModule, MatTableModule,
    PageHeaderComponent, StatusPillComponent
  ],
  templateUrl: './product-category-list.component.html',
  styleUrl: './product-category-list.component.css'
})
export class ProductCategoryListComponent implements OnInit {
  readonly columns = ['name', 'code', 'description', 'status', 'actions'];
  readonly statuses: ProductCategoryStatus[] = ['Active', 'Inactive'];

  categories: ProductCategoryRecord[] = [];
  formOpen = false;
  editingId: string | null = null;

  form = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(60)]],
    code: ['', [Validators.required, Validators.maxLength(24)]],
    description: ['', Validators.maxLength(140)],
    status: ['Active' as ProductCategoryStatus, Validators.required]
  });

  constructor(
    private store: ProductCategoryStoreService,
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
    this.form.reset({ name: '', code: '', description: '', status: 'Active' });
    this.formOpen = true;
  }

  openEdit(category: ProductCategoryRecord): void {
    this.editingId = category.id;
    this.form.reset({
      name: category.name,
      code: category.code,
      description: category.description,
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
    const payload: ProductCategoryInput = {
      name: v.name!,
      code: v.code!,
      description: v.description ?? '',
      status: v.status as ProductCategoryStatus
    };
    const op = this.editingId ? this.store.update(this.editingId, payload) : this.store.create(payload);
    op.subscribe(() => {
      this.snack.open(this.editingId ? 'Product category updated' : 'Product category created', 'OK', { duration: 2200 });
      this.closeForm();
      this.reload();
    });
  }

  toggle(category: ProductCategoryRecord): void {
    const next = category.status === 'Active' ? 'Inactive' : 'Active';
    this.store.setStatus(category.id, next).subscribe(() => {
      this.snack.open(`${category.name} set ${next}`, 'OK', { duration: 1800 });
      this.reload();
    });
  }
}
