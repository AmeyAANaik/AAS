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
import { VendorStoreService } from '../vendor-store.service';
import { VendorView } from '../vendor.model';
import { MockAuthService } from '../../auth/mock-auth.service';

@Component({
  selector: 'app-vendor-list',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatIconModule,
    MatInputModule, MatSelectModule, MatSnackBarModule, MatTableModule,
    PageHeaderComponent, EmptyStateComponent, StatusPillComponent
  ],
  templateUrl: './vendor-list.component.html',
  styleUrl: './vendor-list.component.css'
})
export class VendorListComponent implements OnInit {
  vendors: VendorView[] = [];
  loading = false;
  canManage = false;

  formOpen = false;
  mode: 'create' | 'edit' = 'create';
  editingId: string | null = null;

  form = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(80)]],
    code: ['', Validators.required],
    category: [''],
    phone: [''],
    status: ['Active', Validators.required],
    totalPurchased: [0, [Validators.min(0)]],
    totalPaid: [0, [Validators.min(0)]]
  });

  constructor(
    private store: VendorStoreService,
    private fb: FormBuilder,
    private snack: MatSnackBar,
    auth: MockAuthService
  ) {
    this.canManage = auth.hasFeature('vendors.manage');
  }

  get columns(): string[] {
    const base = ['name', 'category', 'phone', 'purchased', 'paid', 'outstanding', 'status'];
    return this.canManage ? [...base, 'actions'] : base;
  }

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loading = true;
    this.store.list().subscribe(v => {
      this.vendors = v;
      this.loading = false;
    });
  }

  openCreate(): void {
    this.mode = 'create';
    this.editingId = null;
    this.form.reset({ name: '', code: '', category: '', phone: '', status: 'Active', totalPurchased: 0, totalPaid: 0 });
    this.formOpen = true;
  }

  openEdit(v: VendorView): void {
    this.mode = 'edit';
    this.editingId = v.id;
    this.form.reset({
      name: v.name, code: v.code, category: v.category, phone: v.phone,
      status: v.status, totalPurchased: v.totalPurchased, totalPaid: v.totalPaid
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
    const payload = {
      name: v.name!, code: v.code!, category: v.category ?? '', phone: v.phone ?? '',
      status: v.status as any, totalPurchased: Number(v.totalPurchased), totalPaid: Number(v.totalPaid)
    };
    const op = this.mode === 'create'
      ? this.store.create(payload)
      : this.store.update(this.editingId!, payload);
    op.subscribe(() => {
      this.snack.open(this.mode === 'create' ? 'Vendor added' : 'Vendor updated', 'OK', { duration: 2200 });
      this.closeForm();
      this.reload();
    });
  }

  remove(v: VendorView): void {
    if (!confirm(`Delete vendor ${v.name}?`)) {
      return;
    }
    this.store.remove(v.id).subscribe(() => {
      this.snack.open('Vendor deleted', 'OK', { duration: 2200 });
      this.reload();
    });
  }
}
