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
import { BranchRecord, BranchStatus } from '../branch.model';
import { BranchStoreService } from '../branch-store.service';

@Component({
  selector: 'app-branch-list',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatIconModule,
    MatInputModule, MatSelectModule, MatSnackBarModule, MatTableModule,
    PageHeaderComponent, EmptyStateComponent, StatusPillComponent
  ],
  templateUrl: './branch-list.component.html',
  styleUrl: './branch-list.component.css'
})
export class BranchListComponent implements OnInit {
  readonly statuses: BranchStatus[] = ['Active', 'Setup Pending', 'Inactive'];
  readonly columns = ['branch', 'location', 'contact', 'bank', 'openingDate', 'status', 'actions'];
  branches: BranchRecord[] = [];
  loading = false;

  formOpen = false;
  mode: 'create' | 'edit' = 'create';
  editingId: string | null = null;

  form = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(80)]],
    code: ['', [Validators.required, Validators.maxLength(12), Validators.pattern(/^[A-Za-z0-9_-]+$/)]],
    fssaiNumber: ['', [Validators.required, Validators.maxLength(20), Validators.pattern(/^[0-9]{14}$/)]],
    city: ['', [Validators.required, Validators.maxLength(50)]],
    area: ['', [Validators.required, Validators.maxLength(50)]],
    address: ['', [Validators.required, Validators.maxLength(160)]],
    managerName: ['', [Validators.required, Validators.maxLength(80)]],
    phone: ['', [Validators.required, Validators.pattern(/^[0-9+\-\s]{8,16}$/)]],
    email: ['', [Validators.required, Validators.email, Validators.maxLength(100)]],
    contactNumber: ['', [Validators.required, Validators.pattern(/^[0-9+\-\s]{8,16}$/)]],
    accountHolderName: ['', [Validators.required, Validators.maxLength(100)]],
    bankName: ['', [Validators.required, Validators.maxLength(80)]],
    bankAccountNumber: ['', [Validators.required, Validators.pattern(/^[0-9]{6,24}$/)]],
    ifscCode: ['', [Validators.required, Validators.pattern(/^[A-Za-z]{4}0[A-Za-z0-9]{6}$/)]],
    openingDate: [new Date().toISOString().slice(0, 10), Validators.required],
    status: ['Setup Pending' as BranchStatus, Validators.required]
  });

  constructor(
    private store: BranchStoreService,
    private fb: FormBuilder,
    private snack: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.reload();
  }

  reload(): void {
    this.loading = true;
    this.store.list().subscribe(branches => {
      this.branches = branches;
      this.loading = false;
    });
  }

  openCreate(): void {
    this.mode = 'create';
    this.editingId = null;
    this.form.reset({
      name: '',
      code: '',
      fssaiNumber: '',
      city: '',
      area: '',
      address: '',
      managerName: '',
      phone: '',
      email: '',
      contactNumber: '',
      accountHolderName: '',
      bankName: '',
      bankAccountNumber: '',
      ifscCode: '',
      openingDate: new Date().toISOString().slice(0, 10),
      status: 'Setup Pending'
    });
    this.formOpen = true;
  }

  openEdit(branch: BranchRecord): void {
    this.mode = 'edit';
    this.editingId = branch.id;
    this.form.reset({
      name: branch.name,
      code: branch.code,
      fssaiNumber: branch.fssaiNumber,
      city: branch.city,
      area: branch.area,
      address: branch.address,
      managerName: branch.managerName,
      phone: branch.phone,
      email: branch.email,
      contactNumber: branch.contactNumber,
      accountHolderName: branch.accountHolderName,
      bankName: branch.bankName,
      bankAccountNumber: branch.bankAccountNumber,
      ifscCode: branch.ifscCode,
      openingDate: branch.openingDate,
      status: branch.status
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
      name: v.name!,
      code: v.code!,
      fssaiNumber: v.fssaiNumber!,
      city: v.city!,
      area: v.area!,
      address: v.address!,
      managerName: v.managerName!,
      phone: v.phone!,
      email: v.email!,
      contactNumber: v.contactNumber!,
      accountHolderName: v.accountHolderName!,
      bankName: v.bankName!,
      bankAccountNumber: v.bankAccountNumber!,
      ifscCode: v.ifscCode!,
      openingDate: v.openingDate!,
      status: v.status as BranchStatus
    };

    if (this.mode === 'create') {
      this.store.create(payload).subscribe(() => {
        this.snack.open('Branch created', 'OK', { duration: 2200 });
        this.closeForm();
        this.reload();
      });
      return;
    }

    if (this.editingId) {
      this.store.update(this.editingId, payload).subscribe(() => {
        this.snack.open('Branch updated', 'OK', { duration: 2200 });
        this.closeForm();
        this.reload();
      });
    }
  }

  remove(branch: BranchRecord): void {
    if (!confirm(`Delete ${branch.name}? This removes it from the prototype branch selector.`)) {
      return;
    }
    this.store.delete(branch.id).subscribe(() => {
      this.snack.open('Branch deleted', 'OK', { duration: 2200 });
      this.reload();
    });
  }
}
