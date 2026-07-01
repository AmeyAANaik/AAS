import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { Subscription } from 'rxjs';
import { EmptyStateComponent } from '../../shared/empty-state/empty-state.component';
import { StatusPillComponent } from '../../shared/status-pill/status-pill.component';
import { PaymentMode } from '../sales.model';
import { SalesStoreService } from '../sales-store.service';

/**
 * The "Daily Sales Master" — manage which payment modes can be used when
 * recording a sale. Lives as a tab inside the Daily Sales page.
 */
@Component({
  selector: 'app-payment-modes',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, MatButtonModule, MatFormFieldModule, MatIconModule,
    MatInputModule, MatSlideToggleModule, MatSnackBarModule, MatTableModule,
    EmptyStateComponent, StatusPillComponent
  ],
  templateUrl: './payment-modes.component.html',
  styleUrl: './payment-modes.component.css'
})
export class PaymentModesComponent implements OnInit, OnDestroy {
  modes: PaymentMode[] = [];
  readonly columns = ['name', 'status', 'actions'];

  editingId: string | null = null;

  form = this.fb.group({
    name: ['', [Validators.required, Validators.maxLength(40)]],
    enabled: [true]
  });

  private sub?: Subscription;

  constructor(
    private fb: FormBuilder,
    private store: SalesStoreService,
    private snack: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.reload();
    this.sub = this.store.changes$.subscribe(() => this.reload());
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  private reload(): void {
    this.store.listPaymentModes().subscribe(modes => (this.modes = modes));
  }

  startCreate(): void {
    this.editingId = null;
    this.form.reset({ name: '', enabled: true });
  }

  startEdit(mode: PaymentMode): void {
    this.editingId = mode.id;
    this.form.reset({ name: mode.name, enabled: mode.enabled });
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = { name: this.form.value.name!.trim(), enabled: !!this.form.value.enabled };
    const op = this.editingId
      ? this.store.updatePaymentMode(this.editingId, value)
      : this.store.createPaymentMode(value);
    op.subscribe({
      next: () => {
        this.snack.open(this.editingId ? 'Payment mode updated' : 'Payment mode added', 'OK', { duration: 2200 });
        this.startCreate();
      },
      error: (err: Error) => this.snack.open(err.message, 'OK', { duration: 3200 })
    });
  }

  toggle(mode: PaymentMode): void {
    this.store.setPaymentModeEnabled(mode.id, !mode.enabled).subscribe(() => {
      this.snack.open(`${mode.name} ${mode.enabled ? 'disabled' : 'enabled'}`, 'OK', { duration: 1800 });
    });
  }

  remove(mode: PaymentMode): void {
    if (!confirm(`Delete payment mode "${mode.name}"? Past entries keep their recorded mode name.`)) {
      return;
    }
    this.store.removePaymentMode(mode.id).subscribe(() => {
      this.snack.open('Payment mode removed', 'OK', { duration: 2000 });
      if (this.editingId === mode.id) {
        this.startCreate();
      }
    });
  }
}
