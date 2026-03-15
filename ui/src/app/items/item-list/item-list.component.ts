import { Component, OnInit } from '@angular/core';
import { MatDialog } from '@angular/material/dialog';
import { Category } from '../../categories/category.model';
import { CategoryService } from '../../categories/category.service';
import { VendorService } from '../../vendors/vendor.service';
import { ItemCategoryDialogComponent } from '../item-category-dialog/item-category-dialog.component';
import { ItemMetadataService } from '../item-metadata.service';
import { Item, ItemCategorySummaryView, ItemView } from '../item.model';
import { ItemService } from '../item.service';

@Component({
  selector: 'app-item-list',
  templateUrl: './item-list.component.html',
  styleUrl: './item-list.component.scss'
})
export class ItemListComponent implements OnInit {
  displayedColumns: string[] = ['category', 'count', 'actions'];
  items: ItemView[] = [];
  categoryRows: ItemCategorySummaryView[] = [];
  categories: Category[] = [];
  vendors: Array<Record<string, unknown>> = [];
  isLoadingItems = false;
  statusMessage = '';
  searchTerm = '';

  constructor(
    private readonly itemService: ItemService,
    private readonly categoryService: CategoryService,
    private readonly vendorService: VendorService,
    private readonly metadataService: ItemMetadataService,
    private readonly dialog: MatDialog
  ) {}

  ngOnInit(): void {
    this.loadPageData();
  }

  loadPageData(): void {
    this.isLoadingItems = true;
    Promise.all([
      this.categoryService.listCategories().toPromise(),
      this.itemService.listItems().toPromise(),
      this.vendorService.listVendors().toPromise()
    ])
      .then(([categories, items, vendors]) => {
        this.categories = (categories ?? []).map(category => ({
          ...category,
          name: category.name ?? category.item_group_name ?? ''
        }));
        this.vendors = (vendors ?? []) as Array<Record<string, unknown>>;
        const mergedItems = this.metadataService.mergeMetadata((items ?? []) as Item[]);
        this.items = mergedItems.map(item => this.toViewModel(item as Item & { packagingUnit?: string }));
        this.refreshCategoryRows();
      })
      .catch(err => {
        this.statusMessage = this.formatError(err, 'Unable to load item data');
      })
      .finally(() => {
        this.isLoadingItems = false;
      });
  }

  applyFilter(value: string): void {
    this.searchTerm = value.trim();
    this.refreshCategoryRows();
  }

  openCreate(): void {
    this.openCategoryDialog(this.firstCreatableCategory);
  }

  openCategoryDialog(categoryName = ''): void {
    const dialogRef = this.dialog.open(ItemCategoryDialogComponent, {
      width: '1100px',
      maxWidth: '95vw',
      data: {
        categories: this.categories,
        vendors: this.vendors.map(vendor => ({ ...vendor })),
        items: this.items.map(item => ({ ...item })),
        initialCategory: categoryName
      }
    });
    dialogRef.afterClosed().subscribe(didChange => {
      if (didChange) {
        this.statusMessage = 'Items updated.';
        this.loadPageData();
      }
    });
  }

  private toViewModel(item: Item & { packagingUnit?: string }): ItemView {
    const code = String(item.item_code ?? item.name ?? '').trim();
    return {
      id: code || String(item.name ?? ''),
      code: code || String(item.name ?? ''),
      name: String(item.item_name ?? item.name ?? '').trim(),
      category: String(item.item_group ?? ''),
      vendorId: String(item.aas_vendor ?? '').trim(),
      vendorHsnCode: String(item.aas_vendor_hsn_code ?? '').trim(),
      measureUnit: String(item.stock_uom ?? ''),
      packagingUnit: item.aas_packaging_unit ?? item.packagingUnit ?? '',
      marginPercent: typeof item.aas_margin_percent === 'number' ? item.aas_margin_percent : null,
      raw: item
    };
  }

  private refreshCategoryRows(): void {
    const counts = new Map<string, number>();
    for (const item of this.items) {
      const key = item.category || 'Uncategorized';
      counts.set(key, (counts.get(key) ?? 0) + 1);
    }

    const categoryNames = new Set<string>();
    for (const category of this.categories) {
      const name = String(category.name ?? category.item_group_name ?? '').trim();
      if (name) {
        categoryNames.add(name);
      }
    }
    for (const item of this.items) {
      if (item.category) {
        categoryNames.add(item.category);
      }
    }

    const search = this.searchTerm.trim().toLowerCase();
    this.categoryRows = [...categoryNames]
      .map(name => ({
        id: name,
        name,
        itemCount: counts.get(name) ?? 0
      }))
      .filter(row => !search || row.name.toLowerCase().includes(search))
      .sort((left, right) => left.name.localeCompare(right.name));
  }

  private get firstCreatableCategory(): string {
    const activeVendorCategories = new Set(
      this.vendors
        .filter(vendor => !this.asFlag(vendor['disabled']))
        .map(vendor => String(vendor['category'] ?? '').trim())
        .filter(Boolean)
    );
    return this.categoryRows.find(row => activeVendorCategories.has(row.name))?.name ?? this.categoryRows[0]?.name ?? '';
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
}
