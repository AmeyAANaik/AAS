import { AfterViewInit, Component, OnInit, ViewChild } from '@angular/core';
import { MatPaginator, PageEvent } from '@angular/material/paginator';
import { MatSort } from '@angular/material/sort';
import { MatTableDataSource } from '@angular/material/table';
import { firstValueFrom } from 'rxjs';
import { finalize } from 'rxjs/operators';
import { Category } from '../../categories/category.model';
import { CategoryService } from '../../categories/category.service';
import { Vendor } from '../../vendors/vendor.model';
import { VendorService } from '../../vendors/vendor.service';
import { ItemMetadataService } from '../item-metadata.service';
import { ItemVendorPricingService } from '../item-vendor-pricing.service';
import { Item, ItemFormValue, ItemPage, ItemVendorPricingEntry, ItemView } from '../item.model';
import { ItemService } from '../item.service';

@Component({
  selector: 'app-item-list',
  templateUrl: './item-list.component.html',
  styleUrl: './item-list.component.scss'
})
export class ItemListComponent implements OnInit {
  displayedColumns: string[] = ['code', 'name', 'category', 'uom', 'packaging'];
  dataSource = new MatTableDataSource<ItemView>([]);
  // Convenience alias used by the vendor-pricing widget.
  items: ItemView[] = [];
  pricingItems: ItemView[] = [];
  categories: Category[] = [];
  vendors: Vendor[] = [];
  pricing: ItemVendorPricingEntry[] = [];
  isLoadingItems = false;
  isSaving = false;
  statusMessage = '';
  searchTerm = '';
  totalItems = 0;
  pageSize = 20;
  pageIndex = 0;
  pageSizeOptions = [20, 50, 100];
  sortActive = 'name';
  sortDirection: 'asc' | 'desc' = 'asc';

  @ViewChild(MatSort) sort?: MatSort;
  @ViewChild(MatPaginator) paginator?: MatPaginator;

  constructor(
    private itemService: ItemService,
    private vendorService: VendorService,
    private categoryService: CategoryService,
    private metadataService: ItemMetadataService,
    private pricingService: ItemVendorPricingService
  ) {}

  ngOnInit(): void {
    this.loadStaticReferenceData();
    this.loadItemsPage(1);
    this.loadAllItemsForPricing();
    this.loadPricing();
  }

  ngAfterViewInit(): void {
    if (this.sort) {
      this.dataSource.sort = this.sort;
      this.dataSource.sortData = data => data;
      this.dataSource.sort.active = 'name';
      this.dataSource.sort.direction = 'asc';
    }
  }

  loadStaticReferenceData(): void {
    Promise.all([
      this.vendorService.listVendors().toPromise(),
      this.categoryService.listCategories().toPromise()
    ])
      .then(([vendors, categories]) => {
        this.vendors = vendors ?? [];
        this.categories = (categories ?? []).map(category => ({
          ...category,
          name: category.name ?? category.item_group_name ?? ''
        }));
      })
      .catch(err => {
        this.statusMessage = this.formatError(err, 'Unable to load item data');
      })
  }

  loadItemsPage(page: number): void {
    this.isLoadingItems = true;
    this.itemService
      .listItemsPaged(page, this.pageSize, this.searchTerm, this.sortActive, this.sortDirection)
      .toPromise()
      .then((response?: ItemPage) => {
        const payload = response ?? { items: [], total: 0, page, size: this.pageSize };
        const mergedItems = this.metadataService.mergeMetadata((payload.items ?? []) as Item[]);
        const rows = mergedItems.map(item => this.toViewModel(item as Item & { packagingUnit?: string }));
        this.dataSource.data = rows;
        this.items = rows;
        this.totalItems = Number(payload.total ?? 0);
        this.pageIndex = Math.max((payload.page ?? page) - 1, 0);
      })
      .catch(err => {
        this.statusMessage = this.formatError(err, 'Unable to load item data');
      })
      .finally(() => {
        this.isLoadingItems = false;
      });
  }

  loadAllItemsForPricing(): void {
    const pageSize = 200;
    const load = async () => {
      const collected: ItemView[] = [];
      let page = 1;
      let total = 0;
      do {
        const response = await firstValueFrom(
          this.itemService.listItemsPaged(page, pageSize, '', 'name', 'asc')
        );
        const items = response?.items ?? [];
        total = Number(response?.total ?? 0);
        const mergedItems = this.metadataService.mergeMetadata(items as Item[]);
        const rows = mergedItems.map(item =>
          this.toViewModel(item as Item & { packagingUnit?: string })
        );
        collected.push(...rows);
        if (!items.length) {
          break;
        }
        page += 1;
      } while (total === 0 || collected.length < total);
      this.pricingItems = collected;
    };

    load().catch(err => {
      this.statusMessage = this.formatError(err, 'Unable to load item data');
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
          this.loadItemsPage(this.pageIndex + 1);
          this.loadAllItemsForPricing();
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

  applyFilter(value: string): void {
    this.searchTerm = value.trim();
    if (this.paginator) {
      this.paginator.firstPage();
    }
    this.loadItemsPage(1);
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

  onPageChange(event: PageEvent): void {
    this.pageIndex = event.pageIndex;
    this.pageSize = event.pageSize;
    this.loadItemsPage(this.pageIndex + 1);
  }

  onSortChange(sort: { active: string; direction: string }): void {
    this.sortActive = sort.active || 'name';
    this.sortDirection = sort.direction === 'desc' ? 'desc' : 'asc';
    if (this.paginator) {
      this.paginator.firstPage();
    }
    this.loadItemsPage(1);
  }
}
