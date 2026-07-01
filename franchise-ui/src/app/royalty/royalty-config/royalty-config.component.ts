import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormBuilder, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { RoyaltyStoreService } from '../royalty-store.service';

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
  form = this.fb.group({
    ratePercent: [5, [Validators.required, Validators.min(0), Validators.max(100)]]
  });

  constructor(
    private store: RoyaltyStoreService,
    private fb: FormBuilder,
    private snack: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.form.reset({ ratePercent: this.store.getConfig().ratePercent });
  }

  save(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const ratePercent = Number(this.form.getRawValue().ratePercent);
    this.store.setConfig({ ratePercent }).subscribe(() => {
      this.snack.open('Royalty rate saved', 'OK', { duration: 2200 });
    });
  }
}
