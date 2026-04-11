import { Injectable } from '@angular/core';
import { MatSnackBar } from '@angular/material/snack-bar';

@Injectable({ providedIn: 'root' })
export class MasterDataToastService {
  constructor(private readonly snackBar: MatSnackBar) {}

  success(message: string): void {
    this.snackBar.open(message, 'Close', {
      duration: 2600,
      horizontalPosition: 'right',
      verticalPosition: 'top',
      panelClass: ['master-data-snackbar', 'master-data-snackbar-success']
    });
  }

  error(message: string): void {
    this.snackBar.open(message, 'Close', {
      duration: 3600,
      horizontalPosition: 'right',
      verticalPosition: 'top',
      panelClass: ['master-data-snackbar', 'master-data-snackbar-error']
    });
  }
}
