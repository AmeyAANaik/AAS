import { HttpErrorResponse } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { FormControl } from '@angular/forms';
import { finalize } from 'rxjs/operators';
import { Category } from '../../categories/category.model';
import { CategoryService } from '../../categories/category.service';
import { Vendor, VendorFormValue, VendorTemplateValidation, VendorView } from '../vendor.model';
import { VendorService } from '../vendor.service';
import { InvoiceTemplateModel, InvoiceTemplateModelService } from '../../shared/invoice-template-model.service';
import { MasterDataToastService } from '../../shared/master-data-toast.service';

@Component({
  selector: 'app-vendor-list',
  templateUrl: './vendor-list.component.html',
  styleUrl: './vendor-list.component.scss'
})
export class VendorListComponent implements OnInit {
  displayedColumns: string[] = ['name', 'category', 'priority', 'template', 'status', 'actions'];
  vendors: VendorView[] = [];
  categories: Category[] = [];
  selectedVendor: VendorView | null = null;
  mode: 'create' | 'edit' = 'create';
  isFormOpen = false;
  searchControl = new FormControl('');
  isLoading = false;
  isSaving = false;
  isValidatingTemplate = false;
  statusMessage = '';
  templateValidation: VendorTemplateValidation | null = null;
  invoiceTemplateModel: InvoiceTemplateModel | null = null;

  constructor(
    private vendorService: VendorService,
    private categoryService: CategoryService,
    private invoiceTemplateModelService: InvoiceTemplateModelService,
    private toastService: MasterDataToastService
  ) {}

  ngOnInit(): void {
    this.invoiceTemplateModelService.getModel().subscribe({
      next: model => {
        this.invoiceTemplateModel = model;
      }
    });
    this.categoryService.listCategories().subscribe({
      next: categories => {
        this.categories = (categories ?? []).map(category => ({
          ...category,
          name: category.name ?? category.item_group_name ?? ''
        }));
      }
    });
    this.loadVendors();
  }

  loadVendors(): void {
    const selectedId = this.selectedVendor?.id ?? null;
    this.isLoading = true;
    this.vendorService
      .listVendors()
      .pipe(finalize(() => (this.isLoading = false)))
      .subscribe({
        next: vendors => {
          this.vendors = vendors.map(vendor => this.toViewModel(vendor));
          if (selectedId) {
            this.selectedVendor = this.vendors.find(vendor => vendor.id === selectedId) ?? this.selectedVendor;
          }
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
    this.templateValidation = null;
  }

  openCreate(): void {
    this.mode = 'create';
    this.selectedVendor = null;
    this.isFormOpen = true;
    this.statusMessage = '';
    this.templateValidation = null;
  }

  clearSelection(): void {
    this.clearSelectionInternal(true);
  }

  private clearSelectionInternal(clearStatus: boolean): void {
    this.selectedVendor = null;
    this.isFormOpen = false;
    this.mode = 'create';
    this.templateValidation = null;
    if (clearStatus) {
      this.statusMessage = '';
    }
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
        this.toastService.success(this.statusMessage);
        // Keep the status message visible after closing the form.
        this.clearSelectionInternal(false);
        this.loadVendors();
      },
      error: err => {
        this.statusMessage = this.formatError(err, 'Unable to save vendor');
        this.toastService.error(this.statusMessage);
      }
    });
  }

  validateTemplateSample(payload: { file: File; templateJson: string }): void {
    if (!this.selectedVendor) {
      this.statusMessage = 'Save the vendor first before validating a sample invoice.';
      return;
    }
    this.isValidatingTemplate = true;
    this.vendorService
      .uploadInvoiceTemplateSample(this.selectedVendor.id, payload.file, payload.templateJson)
      .pipe(finalize(() => (this.isValidatingTemplate = false)))
      .subscribe({
        next: response => {
          this.templateValidation = response.validation;
          this.statusMessage = response.validation.activationReady
            ? 'Template validation passed. Vendor can be activated.'
            : 'Template validation did not capture all required item columns.';
          this.loadVendors();
        },
        error: err => {
          this.statusMessage = this.formatError(err, 'Unable to validate template sample');
          this.templateValidation = null;
        }
      });
  }

  private toViewModel(vendor: Vendor): VendorView {
    const name = String(vendor['supplier_name'] ?? vendor.name ?? '').trim();
    const disabled = this.asFlag(vendor['disabled']);
    const priorityValue = vendor['aas_priority'];
    const priority = priorityValue === undefined || priorityValue === null ? null : Number(priorityValue);
    const templateHasJson = String(vendor['invoice_template_json'] ?? '').trim().length > 0;
    return {
      id: String(vendor.name ?? name),
      name: name || String(vendor.name ?? ''),
      vendorCode: String(vendor['vendor_code'] ?? '').trim(),
      category: String(vendor['category'] ?? '').trim(),
      priority,
      status: disabled ? 'Inactive' : 'Active',
      templateKey: '',
      templateHasJson,
      raw: vendor
    };
  }

  private toPayload(formValue: VendorFormValue): Record<string, unknown> {
    return {
      supplier_name: formValue.supplierName.trim(),
      vendor_code: formValue.vendorCode.trim(),
      category: formValue.category?.trim() || '',
      address: formValue.address?.trim() || '',
      phone: formValue.phone?.trim() || '',
      gst: formValue.gst?.trim() || '',
      pan: formValue.pan?.trim() || '',
      food_license_no: formValue.foodLicenseNo?.trim() || '',
      aas_priority: formValue.priority ?? 0,
      disabled: formValue.status === 'Inactive' ? 1 : 0,
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

  deleteVendor(vendor: VendorView, event?: Event): void {
    event?.stopPropagation();
    if (!vendor) {
      return;
    }
    if (vendor.status !== 'Inactive') {
      this.statusMessage = 'Only inactive vendors can be deleted.';
      return;
    }
    const confirmed = window.confirm(`Delete vendor "${vendor.name}"? This cannot be undone.`);
    if (!confirmed) {
      return;
    }
    this.isSaving = true;
    this.vendorService
      .deleteVendor(vendor.id)
      .pipe(finalize(() => (this.isSaving = false)))
      .subscribe({
        next: () => {
          if (this.selectedVendor?.id === vendor.id) {
            this.clearSelectionInternal(false);
          }
          this.statusMessage = 'Vendor deleted.';
          this.loadVendors();
        },
        error: err => {
          this.statusMessage = this.formatError(err, 'Unable to delete vendor');
        }
      });
  }

  get filteredVendors(): VendorView[] {
    const term = (this.searchControl.value ?? '').toString().toLowerCase().trim();
    if (!term) {
      return this.vendors;
    }
    return this.vendors.filter(vendor =>
      vendor.name.toLowerCase().includes(term)
        || vendor.vendorCode.toLowerCase().includes(term)
        || vendor.category.toLowerCase().includes(term));
  }

  get statusIsError(): boolean {
    const msg = (this.statusMessage ?? '').toLowerCase();
    if (!msg.trim()) {
      return false;
    }
    return msg.includes('unable') || msg.includes('error') || msg.includes('failed') || msg.includes('forbidden');
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
}
