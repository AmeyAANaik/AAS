import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDividerModule } from '@angular/material/divider';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { EmptyStateComponent } from '../shared/empty-state/empty-state.component';
import { PageHeaderComponent } from '../shared/page-header/page-header.component';

@Component({
  selector: 'app-reports-placeholder',
  standalone: true,
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatDividerModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    PageHeaderComponent,
    EmptyStateComponent
  ],
  templateUrl: './reports-placeholder.component.html',
  styleUrl: './reports-placeholder.component.css'
})
export class ReportsPlaceholderComponent {
  reportForm = new FormGroup({
    reportType: new FormControl('vendor-orders'),
    from: new FormControl(''),
    to: new FormControl('')
  });

  reportTypes = [
    { id: 'vendor-orders', label: 'Vendor Orders' },
    { id: 'vendor-billing', label: 'Vendor Billing' },
    { id: 'vendor-payments', label: 'Vendor Payments' },
    { id: 'shop-billing', label: 'Branch Billing' },
    { id: 'shop-payments', label: 'Branch Payments' },
    { id: 'shop-category', label: 'Branch Category Spend' }
  ];
}
