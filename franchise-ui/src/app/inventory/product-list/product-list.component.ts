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
import { EmptyStateComponent } from '../../shared/empty-state/empty-state.component';
import { StatusPillComponent } from '../../shared/status-pill/status-pill.component';
import { InventoryStoreService } from '../inventory-store.service';
import { ProductStockView, UNITS } from '../inventory.model';

@Component({
  selector: 'app-product-list',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatIconModule,
    MatInputModule, MatSelectModule, MatSnackBarModule, MatTableModule,
    PageHeaderComponent, EmptyStateComponent, StatusPillComponent
  ],
  templateUrl: './product-list.component.html',
  styleUrl: './product-list.component.css'
})
export class ProductListComponent implements OnInit {
  readonly units = UNITS;
  readonly columns = ['name', 'unit', 'min', 'current', 'value', 'status', 'actions'];
  products: ProductStockView[] = [];
  loading = false;

  formOpen = false;
  mode: 'create' | 'edit' = 'create';
  editingId: string | null = null;

  form = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(80)]],
    unit: ['KG', Validators.required],
    minStockLevel: [0, [Validators.required, Validators.min(0)]],
    category: [''],
    openingStock: [0, [Validators.required, Validators.min(0)]],
    openingRate: [0, [Validators.required, Validators.min(0)]]
  });

  constructor(
    private store: InventoryStoreService,
    private fb: FormBuilder,
    private snack: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loading = true;
    this.store.listProducts().subscribe(products => {
      this.products = products;
      this.loading = false;
    });
  }

  openCreate(): void {
    this.mode = 'create';
    this.editingId = null;
    this.form.reset({ name: '', unit: 'KG', minStockLevel: 0, category: '', openingStock: 0, openingRate: 0 });
    this.form.get('openingStock')?.enable();
    this.form.get('openingRate')?.enable();
    this.formOpen = true;
  }

  openEdit(product: ProductStockView): void {
    this.mode = 'edit';
    this.editingId = product.id;
    this.form.reset({
      name: product.name, unit: product.unit, minStockLevel: product.minStockLevel,
      category: product.category ?? '', openingStock: product.openingStock, openingRate: product.lastRate
    });
    // Opening stock is locked once the product exists.
    this.form.get('openingStock')?.disable();
    this.form.get('openingRate')?.disable();
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
    if (this.mode === 'create') {
      this.store.createProduct({
        name: v.name!, unit: v.unit as any, minStockLevel: Number(v.minStockLevel),
        openingStock: Number(v.openingStock), openingRate: Number(v.openingRate), category: v.category ?? ''
      }).subscribe(() => {
        this.snack.open('Product created', 'OK', { duration: 2200 });
        this.closeForm();
        this.reload();
      });
    } else if (this.editingId) {
      this.store.updateProduct(this.editingId, {
        name: v.name!, unit: v.unit as any, minStockLevel: Number(v.minStockLevel), category: v.category ?? ''
      }).subscribe(() => {
        this.snack.open('Product updated', 'OK', { duration: 2200 });
        this.closeForm();
        this.reload();
      });
    }
  }

  remove(product: ProductStockView): void {
    if (!confirm(`Delete ${product.name}? This removes its stock history too.`)) {
      return;
    }
    this.store.deleteProduct(product.id).subscribe(() => {
      this.snack.open('Product deleted', 'OK', { duration: 2200 });
      this.reload();
    });
  }
}
