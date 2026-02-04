import { Component, OnInit } from '@angular/core';
import { finalize } from 'rxjs/operators';
import { StockService } from '../stock.service';
import { StockItem, StockThresholdFormValue, StockView } from '../stock.model';

@Component({
  selector: 'app-stock-list',
  templateUrl: './stock-list.component.html',
  styleUrl: './stock-list.component.scss'
})
export class StockListComponent implements OnInit {
  displayedColumns: string[] = ['item', 'quantity', 'threshold', 'status', 'actions'];
  stockItems: StockView[] = [];
  selectedItem: StockView | null = null;
  isLoading = false;
  isSaving = false;
  statusMessage = '';
  summary = { totalItems: 0, totalQuantity: 0, lowStock: 0 };

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

  private buildSummary(items: StockView[]): { totalItems: number; totalQuantity: number; lowStock: number } {
    const totalItems = items.length;
    const totalQuantity = items.reduce((sum, item) => sum + (Number(item.quantity) || 0), 0);
    const lowStock = items.filter(item => item.isLow).length;
    return { totalItems, totalQuantity, lowStock };
  }

  private toViewModel(item: StockItem): StockView {
    const id = String(item.name ?? item.item_code ?? item.item_name ?? '').trim();
    const name = String(item.item_name ?? item.name ?? item.item_code ?? '').trim();
    const code = String(item.item_code ?? item.name ?? '').trim();
    const quantity = this.parseQuantity(item);
    const threshold = this.parseThreshold(item);
    const isLow = this.isLowStock(quantity, threshold);
    return {
      id,
      name: name || id,
      code: code || id,
      quantity,
      threshold,
      isLow,
      thresholdLabel: threshold === null ? 'Not set' : String(threshold),
      statusLabel: isLow ? 'Low' : 'OK',
      raw: item
    };
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
    if (err instanceof Error) {
      return err.message;
    }
    if (typeof err === 'string') {
      return err;
    }
    return fallback;
  }
}
