import { AfterViewInit, Component, OnInit, ViewChild } from '@angular/core';
import { MatPaginator, PageEvent } from '@angular/material/paginator';
import { MatSort } from '@angular/material/sort';
import { MatTableDataSource } from '@angular/material/table';
import { finalize } from 'rxjs/operators';
import { Category } from '../../categories/category.model';
import { CategoryService } from '../../categories/category.service';
import { ItemMetadataService } from '../item-metadata.service';
import { Item, ItemFormValue, ItemPage, ItemView } from '../item.model';
import { ItemService } from '../item.service';

@Component({
  selector: 'app-item-list',
  templateUrl: './item-list.component.html',
  styleUrl: './item-list.component.scss'
})
export class ItemListComponent implements OnInit {
  displayedColumns: string[] = ['code', 'name', 'category', 'uom', 'packaging', 'margin', 'actions'];
  dataSource = new MatTableDataSource<ItemView>([]);
  items: ItemView[] = [];
  categories: Category[] = [];
  isLoadingItems = false;
  isSaving = false;
  isDeleting = false;
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
    private categoryService: CategoryService,
    private metadataService: ItemMetadataService
  ) {}

  ngOnInit(): void {
    this.loadStaticReferenceData();
    this.loadItemsPage(1);
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
    this.categoryService
      .listCategories()
      .toPromise()
      .then(categories => {
        this.categories = (categories ?? []).map(category => ({
          ...category,
          name: category.name ?? category.item_group_name ?? ''
        }));
      })
      .catch(err => {
        this.statusMessage = this.formatError(err, 'Unable to load item data');
      });
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

  saveItem(formValue: ItemFormValue): void {
    this.isSaving = true;
    const payload = {
      item_code: formValue.itemCode.trim(),
      item_name: formValue.itemName.trim(),
      item_group: formValue.category || 'All Item Groups',
      stock_uom: formValue.measureUnit || 'Nos',
      aas_packaging_unit: formValue.packagingUnit || '',
      aas_margin_percent: formValue.marginPercent ?? 0
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
        },
        error: err => {
          this.statusMessage = this.formatError(err, 'Unable to save item');
        }
      });
  }

  deleteItem(item: ItemView): void {
    const itemId = item.id?.trim();
    if (!itemId || this.isDeleting) {
      return;
    }
    const confirmed = window.confirm(`Soft delete item "${item.name}" (${item.code})? It will be hidden from the UI.`);
    if (!confirmed) {
      return;
    }
    this.isDeleting = true;
    this.itemService
      .deleteItem(itemId)
      .pipe(finalize(() => (this.isDeleting = false)))
      .subscribe({
        next: () => {
          this.statusMessage = 'Item deleted.';
          this.loadItemsPage(this.pageIndex + 1);
        },
        error: err => {
          this.statusMessage = this.formatError(err, 'Unable to delete item');
        }
      });
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
      marginPercent:
        typeof item.aas_margin_percent === 'number' ? item.aas_margin_percent : null,
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
