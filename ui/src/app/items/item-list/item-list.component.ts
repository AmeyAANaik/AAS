import { Component, OnInit } from '@angular/core';
import { finalize } from 'rxjs/operators';
import { Category } from '../../categories/category.model';
import { CategoryService } from '../../categories/category.service';
import { Vendor } from '../../vendors/vendor.model';
import { VendorService } from '../../vendors/vendor.service';
import { ItemMetadataService } from '../item-metadata.service';
import { ItemVendorPricingService } from '../item-vendor-pricing.service';
import { Item, ItemFormValue, ItemVendorPricingEntry, ItemView } from '../item.model';
import { ItemService } from '../item.service';

@Component({
  selector: 'app-item-list',
  templateUrl: './item-list.component.html',
  styleUrl: './item-list.component.scss'
})
export class ItemListComponent implements OnInit {
  displayedColumns: string[] = ['code', 'name', 'category', 'uom', 'packaging'];
  items: ItemView[] = [];
  categories: Category[] = [];
  vendors: Vendor[] = [];
  pricing: ItemVendorPricingEntry[] = [];
  isLoading = false;
  isSaving = false;
  statusMessage = '';

  constructor(
    private itemService: ItemService,
    private vendorService: VendorService,
    private categoryService: CategoryService,
    private metadataService: ItemMetadataService,
    private pricingService: ItemVendorPricingService
  ) {}

  ngOnInit(): void {
    this.loadReferenceData();
    this.loadPricing();
  }

  loadReferenceData(): void {
    this.isLoading = true;
    Promise.all([
      this.itemService.listItems().toPromise(),
      this.vendorService.listVendors().toPromise(),
      this.categoryService.listCategories().toPromise()
    ])
      .then(([items, vendors, categories]) => {
        const mergedItems = this.metadataService.mergeMetadata((items ?? []) as Item[]);
        this.items = mergedItems.map(item => this.toViewModel(item as Item & { packagingUnit?: string }));
        this.vendors = vendors ?? [];
        this.categories = (categories ?? []).map(category => ({
          ...category,
          name: category.name ?? category.item_group_name ?? ''
        }));
      })
      .catch(err => {
        this.statusMessage = this.formatError(err, 'Unable to load item data');
      })
      .finally(() => {
        this.isLoading = false;
      });
  }

  loadPricing(): void {
    this.pricing = this.pricingService.listPricing();
  }

  saveItem(formValue: ItemFormValue): void {
    this.isSaving = true;
    const payload = {
      item_code: formValue.itemCode.trim(),
      item_name: formValue.itemName.trim(),
      item_group: formValue.category || 'All Item Groups',
      stock_uom: formValue.measureUnit || 'Nos',
      aas_packaging_unit: formValue.packagingUnit || ''
    };
    this.itemService
      .createItem(payload)
      .pipe(finalize(() => (this.isSaving = false)))
      .subscribe({
        next: () => {
          this.metadataService.saveMetadata(payload.item_code, {
            packagingUnit: formValue.packagingUnit
          });
          this.statusMessage = 'Item saved.';
          this.loadReferenceData();
        },
        error: err => {
          this.statusMessage = this.formatError(err, 'Unable to save item');
        }
      });
  }

  savePricing(entry: ItemVendorPricingEntry): void {
    this.pricingService.upsertPricing(entry);
    this.loadPricing();
  }

  private toViewModel(item: Item & { packagingUnit?: string }): ItemView {
    const code = String(item.item_code ?? item.name ?? '').trim();
    return {
      id: code || String(item.name ?? ''),
      code: code || String(item.name ?? ''),
      name: String(item.item_name ?? item.name ?? '').trim(),
      category: String(item.item_group ?? ''),
      measureUnit: String(item.stock_uom ?? ''),
      packagingUnit: item.aas_packaging_unit ?? item.packagingUnit ?? '',
      raw: item
    };
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
