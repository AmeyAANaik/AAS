import { Injectable } from '@angular/core';
import { ItemMetadata } from './item.model';

const METADATA_KEY = 'aas_item_metadata';

@Injectable({
  providedIn: 'root'
})
export class ItemMetadataService {
  readMetadata(itemId: string): ItemMetadata {
    const store = this.readStore();
    return store[itemId] ?? {};
  }

  saveMetadata(itemId: string, metadata: ItemMetadata): void {
    if (!itemId) {
      return;
    }
    const store = this.readStore();
    store[itemId] = metadata;
    localStorage.setItem(METADATA_KEY, JSON.stringify(store));
  }

  mergeMetadata(items: Array<{ name?: string; item_code?: string }>): Array<{ name?: string; item_code?: string } & ItemMetadata> {
    const store = this.readStore();
    return items.map(item => ({
      ...item,
      ...(store[String(item.item_code ?? item.name ?? '')] || {})
    }));
  }

  private readStore(): Record<string, ItemMetadata> {
    const raw = localStorage.getItem(METADATA_KEY);
    if (!raw) {
      return {};
    }
    try {
      return JSON.parse(raw) as Record<string, ItemMetadata>;
    } catch {
      return {};
    }
  }
}
