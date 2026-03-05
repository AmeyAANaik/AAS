import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { FormControl } from '@angular/forms';
import { finalize } from 'rxjs/operators';
import { Vendor, VendorFormValue, VendorView } from '../vendor.model';
import { VendorService } from '../vendor.service';
import { AuthTokenService } from '../../shared/auth-token.service';

@Component({
  selector: 'app-vendor-list',
  templateUrl: './vendor-list.component.html',
  styleUrl: './vendor-list.component.scss'
})
export class VendorListComponent implements OnInit {
  displayedColumns: string[] = ['name', 'priority', 'template', 'status', 'actions'];
  vendors: VendorView[] = [];
  selectedVendor: VendorView | null = null;
  mode: 'create' | 'edit' = 'create';
  isFormOpen = false;
  searchControl = new FormControl('');
  isLoading = false;
  isSaving = false;
  statusMessage = '';

  constructor(private vendorService: VendorService, private tokenStore: AuthTokenService) {}

  ngOnInit(): void {
    this.loadVendors();
  }

  loadVendors(): void {
    this.isLoading = true;
    this.vendorService
      .listVendors()
      .pipe(finalize(() => (this.isLoading = false)))
      .subscribe({
        next: vendors => {
          this.vendors = vendors.map(vendor => this.toViewModel(vendor));
        },
        error: err => {
          this.statusMessage = this.formatError(err, 'Unable to load vendors');
        }
      });
  }

  selectVendor(vendor: VendorView): void {
    this.selectedVendor = vendor;
    this.mode = 'edit';
    this.isFormOpen = true;
    this.statusMessage = '';
  }

  openCreate(): void {
    this.mode = 'create';
    this.selectedVendor = null;
    this.isFormOpen = true;
    this.statusMessage = '';
  }

  clearSelection(): void {
    this.selectedVendor = null;
    this.isFormOpen = false;
    this.mode = 'create';
    this.statusMessage = '';
  }

  saveVendor(formValue: VendorFormValue): void {
    this.isSaving = true;
    const payload = this.toPayload(formValue);
    const request$ =
      this.mode === 'edit' && this.selectedVendor
        ? this.vendorService.updateVendor(this.selectedVendor.id, payload)
        : this.vendorService.createVendor(payload);
    request$.pipe(finalize(() => (this.isSaving = false))).subscribe({
      next: () => {
        this.statusMessage = this.mode === 'edit' ? 'Vendor updated.' : 'Vendor saved.';
        this.clearSelection();
        this.loadVendors();
      },
      error: err => {
        this.statusMessage = this.formatError(err, 'Unable to save vendor');
      }
    });
  }

  private toViewModel(vendor: Vendor): VendorView {
    const name = String(vendor['supplier_name'] ?? vendor.name ?? '').trim();
    const disabled = Boolean(vendor['disabled']);
    const priorityValue = vendor['aas_priority'];
    const priority = typeof priorityValue === 'number' ? priorityValue : null;
    const templateEnabled = this.asFlag(vendor['invoice_template_enabled']);
    const templateKey = String(vendor['invoice_template_key'] ?? '').trim();
    const templateHasJson = String(vendor['invoice_template_json'] ?? '').trim().length > 0;
    return {
      id: String(vendor.name ?? name),
      name: name || String(vendor.name ?? ''),
      priority,
      status: disabled ? 'Inactive' : 'Active',
      templateEnabled,
      templateKey,
      templateHasJson,
      raw: vendor
    };
  }

  private toPayload(formValue: VendorFormValue): Record<string, unknown> {
    return {
      supplier_name: formValue.supplierName.trim(),
      address: formValue.address?.trim() || '',
      phone: formValue.phone?.trim() || '',
      gst: formValue.gst?.trim() || '',
      pan: formValue.pan?.trim() || '',
      food_license_no: formValue.foodLicenseNo?.trim() || '',
      aas_priority: formValue.priority ?? 0,
      disabled: formValue.status === 'Inactive' ? 1 : 0,
      invoice_template_enabled: formValue.invoiceTemplateEnabled ? 1 : 0,
      invoice_template_json: String(formValue.invoiceTemplateJson ?? '').trim()
    };
  }

  clearTemplate(): void {
    if (!this.selectedVendor) {
      this.statusMessage = 'Select a vendor before clearing the template.';
      return;
    }
    this.isSaving = true;
    this.vendorService
      .clearInvoiceTemplate(this.selectedVendor.id)
      .pipe(finalize(() => (this.isSaving = false)))
      .subscribe({
        next: () => {
          this.statusMessage = 'Template cleared.';
          this.loadVendors();
        },
        error: err => {
          this.statusMessage = this.formatError(err, 'Unable to clear template');
        }
      });
  }

  get filteredVendors(): VendorView[] {
    const term = (this.searchControl.value ?? '').toString().toLowerCase().trim();
    if (!term) {
      return this.vendors;
    }
    return this.vendors.filter(vendor => vendor.name.toLowerCase().includes(term));
  }

  private formatError(err: unknown, fallback: string): string {
    if (err instanceof HttpErrorResponse) {
      const payload: any = err.error;
      const message = typeof payload === 'string' ? payload : payload?.error || payload?.message;
      if (typeof message === 'string' && message.trim()) {
        return message.trim();
      }
      if (typeof err.message === 'string' && err.message.trim()) {
        return err.message.trim();
      }
      return fallback;
    }
    if (err instanceof Error) {
      return err.message;
    }
    if (typeof err === 'string') {
      return err;
    }
    return fallback;
  }

  private asFlag(value: unknown): boolean {
    if (value === null || value === undefined) {
      return false;
    }
    if (typeof value === 'boolean') {
      return value;
    }
    if (typeof value === 'number') {
      return value !== 0;
    }
    const text = String(value).trim().toLowerCase();
    if (!text) {
      return false;
    }
    return text === '1' || text === 'true' || text === 'yes';
  }

  get canManageTemplates(): boolean {
    // Server enforces ADMIN-only. This is just UI gating to avoid confusing 403s.
    const role = (this.tokenStore.getRole() ?? '').toLowerCase().trim();
    if (!role) {
      return true;
    }
    return role === 'admin';
  }
}
