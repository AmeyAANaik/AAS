import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDividerModule } from '@angular/material/divider';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { finalize } from 'rxjs/operators';
import { EmptyStateComponent } from '../shared/empty-state/empty-state.component';
import { PageHeaderComponent } from '../shared/page-header/page-header.component';
import { ReportsService } from './reports.service';

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
  rows: Array<Record<string, unknown>> = [];
  columns: string[] = [];
  isLoading = false;
  status = '';

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

  constructor(private reportsService: ReportsService) {}

  run(): void {
    const reportType = this.reportForm.get('reportType')?.value || 'vendor-orders';
    const month = this.resolveMonth();
    this.isLoading = true;
    this.status = 'Running report...';
    this.reportsService
      .runReport(String(reportType), month)
      .pipe(finalize(() => (this.isLoading = false)))
      .subscribe({
        next: data => {
          this.rows = data ?? [];
          this.columns = this.rows.length ? Object.keys(this.rows[0]) : [];
          this.status = `Loaded ${this.rows.length} row(s) for ${month}.`;
        },
        error: err => {
          this.rows = [];
          this.columns = [];
          this.status = this.formatError(err, 'Failed to run report.');
        }
      });
  }

  exportCsv(): void {
    const reportType = this.reportForm.get('reportType')?.value || 'vendor-orders';
    const month = this.resolveMonth();
    this.status = 'Exporting CSV...';
    this.reportsService.exportReport(String(reportType), month).subscribe({
      next: blob => {
        const url = URL.createObjectURL(blob);
        const anchor = document.createElement('a');
        anchor.href = url;
        anchor.download = `${reportType}-${month}.csv`;
        anchor.click();
        URL.revokeObjectURL(url);
        this.status = `CSV exported for ${month}.`;
      },
      error: err => {
        this.status = this.formatError(err, 'Failed to export CSV.');
      }
    });
  }

  formatCell(value: unknown): string {
    if (value === null || value === undefined) {
      return '-';
    }
    if (typeof value === 'number') {
      return Number.isFinite(value) ? value.toFixed(2) : '-';
    }
    return String(value);
  }

  private resolveMonth(): string {
    const from = this.reportForm.get('from')?.value;
    if (from && String(from).length >= 7) {
      return String(from).slice(0, 7);
    }
    const today = new Date();
    const year = today.getFullYear();
    const month = String(today.getMonth() + 1).padStart(2, '0');
    return `${year}-${month}`;
  }

  private formatError(err: unknown, fallback: string): string {
    if (typeof err === 'string') {
      return err;
    }
    if (err instanceof Error) {
      return err.message;
    }
    return fallback;
  }
}
