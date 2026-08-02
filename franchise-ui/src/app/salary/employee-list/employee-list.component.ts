import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { Subscription } from 'rxjs';
import { DatePickerFieldComponent } from '../../shared/date-picker-field/date-picker-field.component';
import { EmptyStateComponent } from '../../shared/empty-state/empty-state.component';
import { StatusPillComponent } from '../../shared/status-pill/status-pill.component';
import { SalaryStoreService } from '../salary-store.service';
import { Employee, EmployeeInput, EmployeeStatus } from '../salary.model';

@Component({
  selector: 'app-employee-list',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatIconModule,
    MatInputModule, MatSelectModule, MatSnackBarModule, MatTableModule, MatTooltipModule,
    DatePickerFieldComponent, EmptyStateComponent, StatusPillComponent
  ],
  templateUrl: './employee-list.component.html',
  styleUrl: './employee-list.component.css'
})
export class EmployeeListComponent implements OnInit, OnDestroy {
  employees: Employee[] = [];
  loading = false;

  formOpen = false;
  mode: 'create' | 'edit' = 'create';
  editingId: string | null = null;

  readonly columns = ['name', 'role', 'salary', 'status', 'actions'];

  form = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(80)]],
    role: [''],
    monthlySalary: [0, [Validators.required, Validators.min(0)]],
    status: ['Active' as EmployeeStatus, Validators.required],
    joinedDate: [this.today(), Validators.required]
  });

  private sub?: Subscription;

  constructor(
    private store: SalaryStoreService,
    private fb: FormBuilder,
    private snack: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.reload();
    this.sub = this.store.changes$.subscribe(() => this.reload());
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  private today(): string {
    return new Date().toISOString().slice(0, 10);
  }

  reload(): void {
    this.loading = true;
    this.store.listEmployees().subscribe(list => {
      this.employees = list;
      this.loading = false;
    });
  }

  openCreate(): void {
    this.mode = 'create';
    this.editingId = null;
    this.form.reset({ name: '', role: '', monthlySalary: 0, status: 'Active', joinedDate: this.today() });
    this.formOpen = true;
  }

  openEdit(e: Employee): void {
    this.mode = 'edit';
    this.editingId = e.id;
    this.form.reset({
      name: e.name, role: e.role ?? '', monthlySalary: e.monthlySalary,
      status: e.status, joinedDate: e.joinedDate
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
    const payload: EmployeeInput = {
      name: v.name!, role: v.role ?? '', monthlySalary: Number(v.monthlySalary),
      status: v.status as EmployeeStatus, joinedDate: v.joinedDate!
    };
    const op = this.mode === 'create'
      ? this.store.createEmployee(payload)
      : this.store.updateEmployee(this.editingId!, payload);
    op.subscribe(() => {
      this.snack.open(this.mode === 'create' ? 'Employee added' : 'Employee updated', 'OK', { duration: 2200 });
      this.closeForm();
    });
  }

  toggleStatus(e: Employee): void {
    const next: EmployeeStatus = e.status === 'Active' ? 'Inactive' : 'Active';
    this.store.setEmployeeStatus(e.id, next).subscribe(() => {
      this.snack.open(`${e.name} set ${next}`, 'OK', { duration: 2000 });
    });
  }
}
