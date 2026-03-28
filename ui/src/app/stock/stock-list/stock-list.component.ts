import { Component, OnInit } from '@angular/core';
import { finalize } from 'rxjs/operators';
import { formatUiError } from '../../shared/error-message.util';
import { StockService } from '../stock.service';
import { StockItem, StockThresholdFormValue, StockView, VendorStockGroup } from '../stock.model';

@Component({
  selector: 'app-stock-list',
  templateUrl: './stock-list.component.html',
  styleUrl: './stock-list.component.scss'
})
export class StockListComponent implements OnInit {
  displayedColumns: string[] = ['item', 'quantity', 'threshold', 'status', 'actions'];
  stockItems: StockView[] = [];
  vendorGroups: VendorStockGroup[] = [];
  selectedItem: StockView | null = null;
  isLoading = false;
  isSaving = false;
  statusMessage = '';
  summary = { totalItems: 0, totalQuantity: 0, lowStock: 0, vendors: 0 };

  constructor(private stockService: StockService) {}

  ngOnInit(): void {
    this.loadStock();
  }

  loadStock(): void {
    this.isLoading = true;
    this.stockService
      .listStockItems()
      .pipe(finalize(() => (this.isLoading = false)))
      .subscribe({
        next: items => {
          this.stockItems = items.map(item => this.toViewModel(item));
          this.vendorGroups = this.buildVendorGroups(this.stockItems);
          this.summary = this.buildSummary(this.stockItems);
          this.syncSelection();
        },
        error: err => {
          this.statusMessage = this.formatError(err, 'Unable to load stock');
        }
      });
  }

  selectItem(item: StockView): void {
    this.selectedItem = item;
    this.statusMessage = '';
  }

  clearSelection(): void {
    this.selectedItem = null;
    this.statusMessage = '';
  }

  saveThreshold(formValue: StockThresholdFormValue): void {
    if (!formValue.itemId) {
      this.statusMessage = 'Select an item to set a threshold.';
      return;
    }
    this.isSaving = true;
    this.stockService.saveThreshold(formValue.itemId, formValue.threshold ?? null);
    this.statusMessage = 'Threshold saved locally.';
    this.isSaving = false;
    this.loadStock();
  }

  private syncSelection(): void {
    if (!this.selectedItem) {
      return;
    }
    const refreshed = this.stockItems.find(item => item.id === this.selectedItem?.id) ?? null;
    this.selectedItem = refreshed;
  }

  private buildSummary(items: StockView[]): { totalItems: number; totalQuantity: number; lowStock: number; vendors: number } {
    const totalItems = items.length;
    const totalQuantity = items.reduce((sum, item) => sum + (Number(item.quantity) || 0), 0);
    const lowStock = items.filter(item => item.isLow).length;
    const vendors = new Set(items.map(item => item.vendorName).filter(Boolean)).size;
    return { totalItems, totalQuantity, lowStock, vendors };
  }

  private toViewModel(item: StockItem): StockView {
    const id = String(item.name ?? item.item_code ?? item.item_name ?? '').trim();
    const name = String(item.item_name ?? item.name ?? item.item_code ?? '').trim();
    const code = String(item.item_code ?? item.name ?? '').trim();
    const vendorName = String(item['aas_vendor'] ?? '').trim() || 'Unassigned vendor';
    const quantity = this.parseQuantity(item);
    const threshold = this.parseThreshold(item);
    const isLow = this.isLowStock(quantity, threshold);
    return {
      id,
      name: name || id,
      code: code || id,
      vendorName,
      quantity,
      threshold,
      isLow,
      thresholdLabel: threshold === null ? 'Not set' : String(threshold),
      statusLabel: isLow ? 'Low' : 'OK',
      raw: item
    };
  }

  private buildVendorGroups(items: StockView[]): VendorStockGroup[] {
    const groups = new Map<string, VendorStockGroup>();
    for (const item of items) {
      const key = item.vendorName || 'Unassigned vendor';
      const current = groups.get(key) ?? {
        vendorName: key,
        itemCount: 0,
        totalQuantity: 0,
        itemNames: []
      };
      current.itemCount += 1;
      current.totalQuantity += Number(item.quantity) || 0;
      current.itemNames.push(item.name);
      groups.set(key, current);
    }
    return Array.from(groups.values())
      .map(group => ({
        ...group,
        itemNames: group.itemNames.sort((a, b) => a.localeCompare(b))
      }))
      .sort((a, b) => a.vendorName.localeCompare(b.vendorName));
  }

  private parseQuantity(item: StockItem): number {
    const candidates = [item.stock_qty, item.actual_qty, item.quantity, item.qty];
    for (const candidate of candidates) {
      const value = Number(candidate);
      if (Number.isFinite(value)) {
        return value;
      }
    }
    return 0;
  }

  private parseThreshold(item: StockItem): number | null {
    const value = Number((item as StockItem & { threshold?: number }).threshold);
    return Number.isFinite(value) ? value : null;
  }

  private isLowStock(quantity: number, threshold: number | null): boolean {
    if (threshold === null) {
      return false;
    }
    return quantity <= threshold;
  }

  private formatError(err: unknown, fallback: string): string {
    return formatUiError(err, fallback);
  }
}
