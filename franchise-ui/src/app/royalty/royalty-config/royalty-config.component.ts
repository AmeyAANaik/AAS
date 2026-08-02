import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { BranchStoreService } from '../../master-data/branch-store.service';
import { RoyaltyStoreService } from '../royalty-store.service';

interface BranchRateRow {
  branchName: string;
  control: FormControl<number | null>;
  saving: boolean;
}

@Component({
  selector: 'app-royalty-config',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, MatButtonModule, MatFormFieldModule,
    MatIconModule, MatInputModule, MatSnackBarModule
  ],
  templateUrl: './royalty-config.component.html',
  styleUrl: './royalty-config.component.css'
})
export class RoyaltyConfigComponent implements OnInit {
  rows: BranchRateRow[] = [];

  constructor(
    private store: RoyaltyStoreService,
    private branchStore: BranchStoreService,
    private snack: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.reloadRates();
  }

  saveRow(row: BranchRateRow): void {
    if (row.control.invalid) {
      row.control.markAsTouched();
      return;
    }
    const ratePercent = Number(row.control.value);
    row.saving = true;
    this.store.setBranchRate(row.branchName, ratePercent).subscribe(() => {
      row.saving = false;
      row.control.markAsPristine();
      this.snack.open(`${row.branchName} royalty rate saved`, 'OK', { duration: 2200 });
    });
  }

  trackByBranch(_: number, row: BranchRateRow): string {
    return row.branchName;
  }

  private reloadRates(): void {
    this.rows = this.branchStore.branchNamesSnapshot().map(branchName => ({
      branchName,
      control: new FormControl<number | null>(this.store.rateForBranch(branchName), {
        validators: [Validators.required, Validators.min(0), Validators.max(100)]
      }),
      saving: false
    }));
  }
}
