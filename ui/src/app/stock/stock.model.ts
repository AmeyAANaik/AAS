export interface StockItem {
  name?: string;
  item_name?: string;
  item_code?: string;
  stock_qty?: number;
  actual_qty?: number;
  quantity?: number;
  qty?: number;
  [key: string]: unknown;
}

export interface StockMetadata {
  threshold?: number | null;
}

export interface StockView {
  id: string;
  name: string;
  code: string;
  vendorName: string;
  quantity: number;
  threshold: number | null;
  isLow: boolean;
  thresholdLabel: string;
  statusLabel: string;
  raw: StockItem;
}

export interface VendorStockGroup {
  vendorName: string;
  itemCount: number;
  totalQuantity: number;
  itemNames: string[];
}

export interface StockThresholdFormValue {
  itemId: string;
  threshold: number | null;
}
