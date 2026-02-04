export interface Item {
  name?: string;
  item_code?: string;
  item_name?: string;
  item_group?: string;
  stock_uom?: string;
}

export interface ItemMetadata {
  packagingUnit?: string;
}

export interface ItemFormValue {
  itemCode: string;
  itemName: string;
  category: string;
  measureUnit: string;
  packagingUnit: string;
}

export interface ItemView {
  id: string;
  code: string;
  name: string;
  category: string;
  measureUnit: string;
  packagingUnit: string;
  raw: Item;
}

export interface ItemVendorPricingEntry {
  itemId: string;
  itemName: string;
  vendorId: string;
  vendorName: string;
  originalRate: number;
  marginPercent: number;
  finalRate: number;
}

export interface ItemVendorPricingFormValue {
  itemId: string;
  vendorId: string;
  originalRate: number | null;
  marginPercent: number | null;
}
