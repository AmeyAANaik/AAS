export interface Item {
  name?: string;
  item_code?: string;
  item_name?: string;
  item_group?: string;
  stock_uom?: string;
  aas_packaging_unit?: string;
  aas_margin_percent?: number;
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
  marginPercent: number | null;
}

export interface ItemView {
  id: string;
  code: string;
  name: string;
  category: string;
  measureUnit: string;
  packagingUnit: string;
  marginPercent: number | null;
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

export interface ItemPage {
  items: Item[];
  total: number;
  page: number;
  size: number;
}
