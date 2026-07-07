import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { BranchStoreService } from '../../master-data/branch-store.service';
import { RoyaltyStoreService } from '../royalty-store.service';

@Component({
  selector: 'app-royalty-config',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, MatButtonModule, MatFormFieldModule,
    MatIconModule, MatInputModule, MatSelectModule, MatSnackBarModule
  ],
  templateUrl: './royalty-config.component.html',
  styleUrl: './royalty-config.component.css'
})
export class RoyaltyConfigComponent implements OnInit {
  branches: string[] = [];
  selectedBranch = '';
  branchRates: Array<{ branchName: string; ratePercent: number }> = [];

  form = this.fb.group({
    ratePercent: [5, [Validators.required, Validators.min(0), Validators.max(100)]]
  });

  constructor(
    private store: RoyaltyStoreService,
    private branchStore: BranchStoreService,
    private fb: FormBuilder,
    private snack: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.branches = this.branchStore.branchNamesSnapshot();
    this.selectedBranch = this.branches[0] ?? '';
    this.reloadRates();
  }

  onBranchChange(branchName: string): void {
    this.selectedBranch = branchName;
    this.form.reset({ ratePercent: this.store.rateForBranch(branchName) });
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const ratePercent = Number(this.form.getRawValue().ratePercent);
    this.store.setBranchRate(this.selectedBranch, ratePercent).subscribe(() => {
      this.snack.open('Branch royalty rate saved', 'OK', { duration: 2200 });
      this.reloadRates();
    });
  }

  private reloadRates(): void {
    this.branchRates = this.store.branchRatesSnapshot();
    this.form.reset({ ratePercent: this.store.rateForBranch(this.selectedBranch) });
  }
}
