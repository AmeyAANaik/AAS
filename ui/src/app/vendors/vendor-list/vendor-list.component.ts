import { Component, OnInit } from '@angular/core';
import { FormControl } from '@angular/forms';
import { finalize } from 'rxjs/operators';
import { Vendor, VendorFormValue, VendorView } from '../vendor.model';
import { VendorService } from '../vendor.service';

@Component({
  selector: 'app-vendor-list',
  templateUrl: './vendor-list.component.html',
  styleUrl: './vendor-list.component.scss'
})
export class VendorListComponent implements OnInit {
  displayedColumns: string[] = ['name', 'priority', 'status', 'actions'];
  vendors: VendorView[] = [];
  selectedVendor: VendorView | null = null;
  mode: 'create' | 'edit' = 'create';
  isFormOpen = false;
  searchControl = new FormControl('');
  isLoading = false;
  isSaving = false;
  statusMessage = '';

  constructor(private vendorService: VendorService) {}

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
    this.statusMessage = 'Editing vendors is not available yet.';
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
    this.vendorService
      .createVendor(payload)
      .pipe(finalize(() => (this.isSaving = false)))
      .subscribe({
        next: () => {
          this.statusMessage = 'Vendor saved.';
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
    return {
      id: String(vendor.name ?? name),
      name: name || String(vendor.name ?? ''),
      priority,
      status: disabled ? 'Inactive' : 'Active',
      raw: vendor
    };
  }

  private toPayload(formValue: VendorFormValue): Record<string, unknown> {
    return {
      supplier_name: formValue.supplierName.trim(),
      aas_priority: formValue.priority ?? 0,
      disabled: formValue.status === 'Inactive' ? 1 : 0
    };
  }

  get filteredVendors(): VendorView[] {
    const term = (this.searchControl.value ?? '').toString().toLowerCase().trim();
    if (!term) {
      return this.vendors;
    }
    return this.vendors.filter(vendor => vendor.name.toLowerCase().includes(term));
  }

  private formatError(err: unknown, fallback: string): string {
    if (err instanceof Error) {
      return err.message;
    }
    if (typeof err === 'string') {
      return err;
    }
    return fallback;
  }
}
