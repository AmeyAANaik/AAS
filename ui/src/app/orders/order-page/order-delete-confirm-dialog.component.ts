import { Component, Inject } from '@angular/core';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';

export interface OrderDeleteConfirmDialogData {
  orderId: string;
  purchaseOrderId?: string;
}

@Component({
  selector: 'app-order-delete-confirm-dialog',
  template: `
    <h2 mat-dialog-title>Delete order</h2>
    <mat-dialog-content>
      <p>Are you sure you want to delete order {{ data.orderId }}?</p>
      <p *ngIf="data.purchaseOrderId">
        The linked draft Purchase Order {{ data.purchaseOrderId }} will also be deleted automatically.
      </p>
    </mat-dialog-content>
    <mat-dialog-actions align="end">
      <button mat-button type="button" (click)="close(false)">Cancel</button>
      <button mat-raised-button color="warn" type="button" (click)="close(true)">Delete</button>
    </mat-dialog-actions>
  `
})
export class OrderDeleteConfirmDialogComponent {
  constructor(
    private readonly dialogRef: MatDialogRef<OrderDeleteConfirmDialogComponent, boolean>,
    @Inject(MAT_DIALOG_DATA) public readonly data: OrderDeleteConfirmDialogData
  ) {}

  close(result: boolean): void {
    this.dialogRef.close(result);
  }
}
