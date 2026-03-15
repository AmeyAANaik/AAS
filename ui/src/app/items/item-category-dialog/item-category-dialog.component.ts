import { Component, Inject, OnInit } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { MAT_DIALOG_DATA, MatDialogRef } from '@angular/material/dialog';
import { finalize } from 'rxjs/operators';
import { Category } from '../../categories/category.model';
import { ItemMetadataService } from '../item-metadata.service';
import { ItemFormValue, ItemView } from '../item.model';
import { ItemService } from '../item.service';

export interface ItemCategoryDialogData {
  categories: Category[];
  vendors: Array<Record<string, unknown>>;
  items: ItemView[];
  initialCategory?: string;
}

@Component({
  selector: 'app-item-category-dialog',
  templateUrl: './item-category-dialog.component.html',
  styleUrl: './item-category-dialog.component.scss'
})
export class ItemCategoryDialogComponent implements OnInit {
  selectedCategory = '';
  selectedItem: ItemView | null = null;
  visibleItems: ItemView[] = [];
  sortedCategories: Category[] = [];
  isSaving = false;
  isDeleting = false;
  statusMessage = '';
  didChange = false;
  formVersion = 0;

  constructor(
    private readonly dialogRef: MatDialogRef<ItemCategoryDialogComponent, boolean>,
    @Inject(MAT_DIALOG_DATA) public readonly data: ItemCategoryDialogData,
    private readonly itemService: ItemService,
    private readonly metadataService: ItemMetadataService
  ) {}

  ngOnInit(): void {
    this.sortedCategories = [...this.data.categories].sort((left, right) =>
      String(left.name ?? left.item_group_name ?? '').localeCompare(String(right.name ?? right.item_group_name ?? ''))
    );
    const firstCategory = this.sortedCategories[0]?.name ?? '';
    this.selectedCategory = this.data.initialCategory?.trim() || firstCategory;
    this.refreshVisibleItems();
    this.bumpFormVersion();
  }

  onCategoryChange(categoryName: string): void {
    this.selectedCategory = categoryName;
    this.selectedItem = null;
    this.statusMessage = '';
    this.refreshVisibleItems();
    this.bumpFormVersion();
  }

  startCreate(): void {
    this.selectedItem = null;
    this.statusMessage = '';
    this.bumpFormVersion();
  }

  editItem(item: ItemView): void {
    this.selectedItem = { ...item, raw: { ...item.raw } };
    this.statusMessage = '';
    this.bumpFormVersion();
  }

  saveItem(formValue: ItemFormValue): void {
    if (!this.selectedCategory.trim()) {
      this.statusMessage = 'Select a category first.';
      return;
    }
    const vendor = this.selectedItemVendor ?? this.resolvedVendor;
    if (!vendor) {
      this.statusMessage = `No active vendor is configured for category "${this.selectedCategory}".`;
      return;
    }
    this.isSaving = true;
    const payload = {
      item_name: formValue.itemName.trim(),
      item_group: this.selectedCategory,
      stock_uom: formValue.measureUnit || 'Nos',
      aas_packaging_unit: formValue.packagingUnit || '',
      aas_margin_percent: formValue.marginPercent ?? 0,
      aas_vendor_hsn_code: formValue.vendorHsnCode.trim()
    };

    const request$ = this.selectedItem
      ? this.itemService.updateItem(this.selectedItem.id, {
          item_name: payload.item_name,
          stock_uom: payload.stock_uom,
          aas_packaging_unit: payload.aas_packaging_unit,
          aas_margin_percent: payload.aas_margin_percent
        })
      : this.itemService.createItem(payload);

    request$.pipe(finalize(() => (this.isSaving = false))).subscribe({
      next: response => {
        const nextItem = this.toItemView(response, payload, vendor);
        this.metadataService.saveMetadata(nextItem.code, { packagingUnit: payload.aas_packaging_unit });
        this.upsertItem(nextItem);
        this.selectedItem = null;
        this.didChange = true;
        this.statusMessage = 'Item saved.';
        this.refreshVisibleItems();
      },
      error: err => {
        this.statusMessage = this.formatError(err, 'Unable to save item');
      }
    });
  }

  deleteItem(item: ItemView): void {
    if (this.isDeleting) {
      return;
    }
    const confirmed = window.confirm(`Soft delete item "${item.name}" (${item.code})? It will be hidden from the UI.`);
    if (!confirmed) {
      return;
    }
    this.isDeleting = true;
    this.itemService
      .deleteItem(item.id)
      .pipe(finalize(() => (this.isDeleting = false)))
      .subscribe({
        next: () => {
          this.data.items = this.data.items.filter(entry => entry.id !== item.id);
          if (this.selectedItem?.id === item.id) {
            this.selectedItem = null;
          }
          this.didChange = true;
          this.statusMessage = 'Item deleted.';
          this.refreshVisibleItems();
        },
        error: err => {
          this.statusMessage = this.formatError(err, 'Unable to delete item');
        }
      });
  }

  close(): void {
    this.dialogRef.close(this.didChange);
  }

  private refreshVisibleItems(): void {
    this.visibleItems = this.data.items
      .filter(item => item.category === this.selectedCategory)
      .sort((left, right) => left.name.localeCompare(right.name));
  }

  get resolvedVendor(): { id: string; name: string; code: string } | null {
    const candidates = (this.data.vendors ?? [])
      .filter(vendor => String(vendor['category'] ?? '').trim() === this.selectedCategory)
      .filter(vendor => !this.asFlag(vendor['disabled']))
      .sort((left, right) => {
        const priorityDiff = this.priorityOf(right['aas_priority']) - this.priorityOf(left['aas_priority']);
        if (priorityDiff !== 0) {
          return priorityDiff;
        }
        return String(left['supplier_name'] ?? left['name'] ?? '').localeCompare(String(right['supplier_name'] ?? right['name'] ?? ''));
      });
    const vendor = candidates[0];
    if (!vendor) {
      return null;
    }
    return {
      id: String(vendor['name'] ?? '').trim(),
      name: String(vendor['supplier_name'] ?? vendor['name'] ?? '').trim(),
      code: this.normalizeCodeSegment(String(vendor['vendor_code'] ?? vendor['supplier_name'] ?? vendor['name'] ?? ''))
    };
  }

  get categoryCode(): string {
    const category = this.sortedCategories.find(entry => entry.name === this.selectedCategory);
    return this.normalizeCodeSegment(String(category?.aas_category_code ?? category?.name ?? ''));
  }

  get resolvedVendorName(): string {
    const vendor = this.selectedItemVendor ?? this.resolvedVendor;
    if (!vendor) {
      return '';
    }
    return vendor.code ? `${vendor.name} (${vendor.code})` : vendor.name;
  }

  private toItemView(
    response: unknown,
    fallback: { item_name: string; item_group: string; stock_uom: string; aas_packaging_unit: string; aas_margin_percent: number; aas_vendor_hsn_code: string },
    vendor: { id: string; name: string; code: string }
  ): ItemView {
    const saved = this.unwrapResource(response);
    const generatedCode = [vendor.code, this.categoryCode, this.normalizeCodeSegment(fallback.aas_vendor_hsn_code)]
      .filter(Boolean)
      .join('_');
    const code = String(saved['item_code'] ?? this.selectedItem?.code ?? generatedCode).trim();
    const name = String(saved['item_name'] ?? fallback.item_name).trim();
    const category = String(saved['item_group'] ?? fallback.item_group).trim();
    const measureUnit = String(saved['stock_uom'] ?? fallback.stock_uom).trim();
    const packagingUnit = String(saved['aas_packaging_unit'] ?? fallback.aas_packaging_unit).trim();
    const vendorHsnCode = String(saved['aas_vendor_hsn_code'] ?? this.selectedItem?.vendorHsnCode ?? fallback.aas_vendor_hsn_code).trim();
    return {
      id: String(saved['name'] ?? this.selectedItem?.id ?? code).trim() || code,
      code,
      name,
      category,
      vendorId: String(saved['aas_vendor'] ?? this.selectedItem?.vendorId ?? vendor.id).trim(),
      vendorHsnCode,
      measureUnit,
      packagingUnit,
      marginPercent: this.toNumber(saved['aas_margin_percent'], fallback.aas_margin_percent),
      raw: {
        item_code: code,
        item_name: name,
        item_group: category,
        stock_uom: measureUnit,
        aas_packaging_unit: packagingUnit,
        aas_margin_percent: this.toNumber(saved['aas_margin_percent'], fallback.aas_margin_percent),
        aas_vendor: String(saved['aas_vendor'] ?? this.selectedItem?.vendorId ?? vendor.id).trim(),
        aas_vendor_hsn_code: vendorHsnCode
      }
    };
  }

  private unwrapResource(response: unknown): Record<string, unknown> {
    if (!response || typeof response !== 'object') {
      return {};
    }
    const data = (response as Record<string, unknown>)['data'];
    if (data && typeof data === 'object') {
      return data as Record<string, unknown>;
    }
    return response as Record<string, unknown>;
  }

  private toNumber(value: unknown, fallback: number): number {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : fallback;
  }

  private priorityOf(value: unknown): number {
    const parsed = Number(value);
    return Number.isFinite(parsed) ? parsed : 0;
  }

  get selectedItemVendor(): { id: string; name: string; code: string } | null {
    const vendorId = this.selectedItem?.vendorId ?? '';
    if (!vendorId) {
      return null;
    }
    const vendor = (this.data.vendors ?? []).find(entry => String(entry['name'] ?? '').trim() === vendorId);
    if (!vendor) {
      return null;
    }
    return {
      id: String(vendor['name'] ?? '').trim(),
      name: String(vendor['supplier_name'] ?? vendor['name'] ?? '').trim(),
      code: this.normalizeCodeSegment(String(vendor['vendor_code'] ?? vendor['supplier_name'] ?? vendor['name'] ?? ''))
    };
  }

  private asFlag(value: unknown): boolean {
    if (typeof value === 'boolean') {
      return value;
    }
    if (typeof value === 'number') {
      return value !== 0;
    }
    const normalized = String(value ?? '').trim().toLowerCase();
    return normalized === '1' || normalized === 'true' || normalized === 'yes';
  }

  private normalizeCodeSegment(value: string): string {
    return value
      .trim()
      .toUpperCase()
      .replace(/[^A-Z0-9]+/g, '_')
      .replace(/_+/g, '_')
      .replace(/^_+|_+$/g, '');
  }

  private upsertItem(nextItem: ItemView): void {
    const index = this.data.items.findIndex(entry => entry.id === nextItem.id);
    if (index >= 0) {
      this.data.items = this.data.items.map(entry => (entry.id === nextItem.id ? nextItem : entry));
      return;
    }
    this.data.items = [...this.data.items, nextItem];
  }

  private formatError(err: unknown, fallback: string): string {
    if (err instanceof HttpErrorResponse) {
      const payload = err.error;
      if (typeof payload === 'string' && payload.trim()) {
        return payload;
      }
      if (payload && typeof payload === 'object') {
        const message = String((payload as Record<string, unknown>)['message'] ?? '').trim();
        if (message) {
          return message;
        }
        const error = String((payload as Record<string, unknown>)['error'] ?? '').trim();
        if (error) {
          return error;
        }
      }
      return err.message || fallback;
    }
    if (err instanceof Error) {
      return err.message;
    }
    if (typeof err === 'string') {
      return err;
    }
    return fallback;
  }

  private bumpFormVersion(): void {
    this.formVersion += 1;
  }
}
