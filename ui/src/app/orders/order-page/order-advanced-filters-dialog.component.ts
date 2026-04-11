import { Component, Inject } from '@angular/core';
import { FormControl } from '@angular/forms';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

export type OrderAdvancedFiltersDialogValue = { from: string; to: string };

@Component({
  selector: 'app-order-advanced-filters-dialog',
  templateUrl: './order-advanced-filters-dialog.component.html',
  styleUrl: './order-advanced-filters-dialog.component.scss'
})
export class OrderAdvancedFiltersDialogComponent {
  readonly fromControl = new FormControl<string>(this.data?.from ?? '', { nonNullable: true });
  readonly toControl = new FormControl<string>(this.data?.to ?? '', { nonNullable: true });

  constructor(
    private dialogRef: MatDialogRef<OrderAdvancedFiltersDialogComponent, OrderAdvancedFiltersDialogValue>,
    @Inject(MAT_DIALOG_DATA) public data: Partial<OrderAdvancedFiltersDialogValue> | null
  ) {}

  clear(): void {
    this.fromControl.setValue('');
    this.toControl.setValue('');
  }

  cancel(): void {
    this.dialogRef.close();
  }

  apply(): void {
    this.dialogRef.close({
      from: this.fromControl.value.trim(),
      to: this.toControl.value.trim()
    });
  }
}

