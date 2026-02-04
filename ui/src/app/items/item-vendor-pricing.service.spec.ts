import { ItemVendorPricingService } from './item-vendor-pricing.service';

describe('ItemVendorPricingService', () => {
  let service: ItemVendorPricingService;

  beforeEach(() => {
    service = new ItemVendorPricingService();
    localStorage.removeItem('aas_item_vendor_pricing');
  });

  it('calculates final rate using margin percent', () => {
    const finalRate = service.calculateFinalRate(100, 10);
    expect(finalRate).toBe(110);
  });

  it('returns zero final rate when original rate is zero', () => {
    const finalRate = service.calculateFinalRate(0, 15);
    expect(finalRate).toBe(0);
  });

  it('isolates vendor-specific pricing per item', () => {
    service.upsertPricing({
      itemId: 'ITEM-1',
      itemName: 'Item 1',
      vendorId: 'V1',
      vendorName: 'Vendor 1',
      originalRate: 50,
      marginPercent: 10,
      finalRate: service.calculateFinalRate(50, 10)
    });
    service.upsertPricing({
      itemId: 'ITEM-1',
      itemName: 'Item 1',
      vendorId: 'V2',
      vendorName: 'Vendor 2',
      originalRate: 60,
      marginPercent: 5,
      finalRate: service.calculateFinalRate(60, 5)
    });

    const entries = service.listPricing();
    const itemEntries = entries.filter(entry => entry.itemId === 'ITEM-1');
    expect(itemEntries.length).toBe(2);
    expect(itemEntries.find(entry => entry.vendorId === 'V1')?.finalRate).toBe(55);
    expect(itemEntries.find(entry => entry.vendorId === 'V2')?.finalRate).toBe(63);
  });

  it('supports multiple vendors per item', () => {
    service.upsertPricing({
      itemId: 'ITEM-2',
      itemName: 'Item 2',
      vendorId: 'V1',
      vendorName: 'Vendor 1',
      originalRate: 80,
      marginPercent: 20,
      finalRate: service.calculateFinalRate(80, 20)
    });
    service.upsertPricing({
      itemId: 'ITEM-2',
      itemName: 'Item 2',
      vendorId: 'V3',
      vendorName: 'Vendor 3',
      originalRate: 90,
      marginPercent: 0,
      finalRate: service.calculateFinalRate(90, 0)
    });

    const entries = service.listPricing().filter(entry => entry.itemId === 'ITEM-2');
    expect(entries.length).toBe(2);
  });
});
