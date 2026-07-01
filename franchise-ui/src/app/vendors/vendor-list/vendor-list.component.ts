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
import { VendorInvoiceSetupComponent } from '../vendor-invoice-setup/vendor-invoice-setup.component';
import { VendorStoreService } from '../vendor-store.service';
import {
  GST_CATEGORIES,
  PAYMENT_TERMS,
  Vendor,
  VendorStatus,
  VendorView
} from '../vendor.model';
import { MockAuthService } from '../../auth/mock-auth.service';

@Component({
  selector: 'app-vendor-list',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatIconModule,
    MatInputModule, MatSelectModule, MatSnackBarModule, MatTableModule,
    PageHeaderComponent, EmptyStateComponent, StatusPillComponent, VendorInvoiceSetupComponent
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
  /** The vendor currently being edited (carries the saved invoice template). */
  editingVendor: VendorView | null = null;

  readonly gstCategories = GST_CATEGORIES;
  readonly paymentTerms = PAYMENT_TERMS;

  form = this.fb.group({
    // Details
    name: ['', [Validators.required, Validators.maxLength(80)]],
    code: ['', Validators.required],
    supplierType: ['Company', Validators.required],
    category: [''],
    // Tax & compliance
    gstin: ['', [Validators.maxLength(15)]],
    pan: ['', [Validators.maxLength(10)]],
    gstCategory: ['Registered Regular'],
    foodLicenseNo: [''],
    // Address & contact
    addressLine1: [''],
    addressLine2: [''],
    city: [''],
    state: ['Maharashtra'],
    country: ['India'],
    pincode: [''],
    email: ['', [Validators.email]],
    phone: [''],
    // Buying defaults
    defaultCurrency: ['INR'],
    paymentTerms: ['Net 30'],
    // Operations
    priority: [5 as number | null, [Validators.min(0)]],
    status: ['Inactive', Validators.required],
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
    const base = ['name', 'category', 'gstin', 'phone', 'purchased', 'paid', 'outstanding', 'invoice', 'status'];
    return this.canManage ? [...base, 'actions'] : base;
  }

  /** Active requires a validated invoice format (mirrors the ui/ activation gate). */
  get canActivate(): boolean {
    return !!this.editingVendor?.invoiceValidated;
  }

  get activationHint(): string {
    if (this.mode === 'create') {
      return 'Save the vendor first, then validate its invoice format to activate it.';
    }
    return this.canActivate
      ? 'Invoice format validated — this vendor can be activated.'
      : 'Validate the invoice format below before setting this vendor Active.';
  }

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loading = true;
    this.store.list().subscribe(v => {
      this.vendors = v;
      // Keep the edit panel's vendor reference fresh (e.g. after template save).
      if (this.editingId) {
        this.editingVendor = v.find(x => x.id === this.editingId) ?? this.editingVendor;
      }
      this.loading = false;
    });
  }

  openCreate(): void {
    this.mode = 'create';
    this.editingId = null;
    this.editingVendor = null;
    this.form.reset({
      name: '', code: '', supplierType: 'Company', category: '',
      gstin: '', pan: '', gstCategory: 'Registered Regular', foodLicenseNo: '',
      addressLine1: '', addressLine2: '', city: '', state: 'Maharashtra', country: 'India', pincode: '',
      email: '', phone: '',
      defaultCurrency: 'INR', paymentTerms: 'Net 30',
      priority: 5, status: 'Inactive', totalPurchased: 0, totalPaid: 0
    });
    this.formOpen = true;
  }

  openEdit(v: VendorView): void {
    this.mode = 'edit';
    this.editingId = v.id;
    this.editingVendor = v;
    this.form.reset({
      name: v.name, code: v.code, supplierType: v.supplierType, category: v.category,
      gstin: v.gstin, pan: v.pan, gstCategory: v.gstCategory, foodLicenseNo: v.foodLicenseNo,
      addressLine1: v.addressLine1, addressLine2: v.addressLine2, city: v.city, state: v.state,
      country: v.country, pincode: v.pincode, email: v.email, phone: v.phone,
      defaultCurrency: v.defaultCurrency, paymentTerms: v.paymentTerms,
      priority: v.priority, status: v.status, totalPurchased: v.totalPurchased, totalPaid: v.totalPaid
    });
    this.formOpen = true;
  }

  closeForm(): void {
    this.formOpen = false;
    this.editingId = null;
    this.editingVendor = null;
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const v = this.form.getRawValue();
    if (v.status === 'Active' && !this.canActivate) {
      this.form.get('status')?.setErrors({ activationBlocked: true });
      this.snack.open('Validate the invoice format before activating this vendor.', 'OK', { duration: 3000 });
      return;
    }
    const payload: Omit<Vendor, 'id'> = {
      name: v.name!, code: v.code!, supplierType: v.supplierType as Vendor['supplierType'],
      category: v.category ?? '', gstin: (v.gstin ?? '').toUpperCase(), pan: (v.pan ?? '').toUpperCase(),
      gstCategory: v.gstCategory ?? '', foodLicenseNo: v.foodLicenseNo ?? '',
      addressLine1: v.addressLine1 ?? '', addressLine2: v.addressLine2 ?? '', city: v.city ?? '',
      state: v.state ?? '', country: v.country ?? '', pincode: v.pincode ?? '',
      email: v.email ?? '', phone: v.phone ?? '',
      defaultCurrency: v.defaultCurrency ?? 'INR', paymentTerms: v.paymentTerms ?? '',
      priority: v.priority ?? null, status: v.status as VendorStatus,
      totalPurchased: Number(v.totalPurchased), totalPaid: Number(v.totalPaid),
      // Preserve the saved invoice template on edit; new vendors start without one.
      invoiceTemplate: this.editingVendor?.invoiceTemplate ?? null
    };
    const op = this.mode === 'create'
      ? this.store.create(payload)
      : this.store.update(this.editingId!, payload);
    op.subscribe(view => {
      this.snack.open(this.mode === 'create' ? 'Vendor added' : 'Vendor updated', 'OK', { duration: 2200 });
      // Stay on the new vendor so its invoice format can be configured immediately.
      if (this.mode === 'create') {
        this.mode = 'edit';
        this.editingId = view.id;
        this.editingVendor = view;
      } else {
        this.editingVendor = view;
      }
      this.reload();
    });
  }

  /** Invoice format saved/removed from the child component. */
  onInvoiceChanged(view: VendorView): void {
    this.editingVendor = view;
    this.form.patchValue({ status: view.status }, { emitEvent: false });
    this.snack.open(view.invoiceValidated ? 'Invoice format validated' : 'Invoice format removed', 'OK', { duration: 2200 });
    this.reload();
  }

  remove(v: VendorView): void {
    if (!confirm(`Delete vendor ${v.name}?`)) {
      return;
    }
    this.store.remove(v.id).subscribe(() => {
      this.snack.open('Vendor deleted', 'OK', { duration: 2200 });
      if (this.editingId === v.id) {
        this.closeForm();
      }
      this.reload();
    });
  }
}
