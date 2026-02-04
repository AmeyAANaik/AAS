import { Injectable } from '@angular/core';
import { ItemVendorPricingEntry } from './item.model';

const PRICING_KEY = 'aas_item_vendor_pricing';

@Injectable({
  providedIn: 'root'
})
export class ItemVendorPricingService {
  listPricing(): ItemVendorPricingEntry[] {
    const store = this.readStore();
    return Object.values(store);
  }

  upsertPricing(entry: ItemVendorPricingEntry): void {
    const store = this.readStore();
    const key = this.keyFor(entry.itemId, entry.vendorId);
    store[key] = entry;
    localStorage.setItem(PRICING_KEY, JSON.stringify(store));
  }

  calculateFinalRate(originalRate: number, marginPercent: number): number {
    if (!originalRate || originalRate <= 0) {
      return 0;
    }
    const margin = Number(marginPercent) || 0;
    return Number((originalRate * (1 + margin / 100)).toFixed(2));
  }

  private keyFor(itemId: string, vendorId: string): string {
    return `${itemId}::${vendorId}`;
  }

  private readStore(): Record<string, ItemVendorPricingEntry> {
    const raw = localStorage.getItem(PRICING_KEY);
    if (!raw) {
      return {};
    }
    try {
      return JSON.parse(raw) as Record<string, ItemVendorPricingEntry>;
    } catch {
      return {};
    }
  }
}
