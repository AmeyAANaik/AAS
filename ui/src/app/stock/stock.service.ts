import { Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';
import { ItemService } from '../items/item.service';
import { StockItem, StockMetadata } from './stock.model';

const METADATA_KEY = 'aas_stock_thresholds';

@Injectable({
  providedIn: 'root'
})
export class StockService {
  constructor(private itemService: ItemService) {}

  listStockItems(): Observable<Array<StockItem & StockMetadata>> {
    return this.itemService.listItems().pipe(
      map(items => this.mergeMetadata(items ?? []))
    );
  }

  saveThreshold(itemId: string, threshold: number | null): void {
    if (!itemId) {
      return;
    }
    const store = this.readMetadata();
    store[itemId] = { threshold };
    localStorage.setItem(METADATA_KEY, JSON.stringify(store));
  }

  private mergeMetadata(items: StockItem[]): Array<StockItem & StockMetadata> {
    const store = this.readMetadata();
    return items.map(item => ({
      ...item,
      ...(store[String(item.name ?? '')] || {})
    }));
  }

  private readMetadata(): Record<string, StockMetadata> {
    const raw = localStorage.getItem(METADATA_KEY);
    if (!raw) {
      return {};
    }
    try {
      return JSON.parse(raw) as Record<string, StockMetadata>;
    } catch {
      return {};
    }
  }
}
