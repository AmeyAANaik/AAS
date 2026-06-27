export type Unit = 'KG' | 'LTR' | 'Packet' | 'Bottle' | 'Piece';

export const UNITS: Unit[] = ['KG', 'LTR', 'Packet', 'Bottle', 'Piece'];

export interface Product {
  id: string;
  name: string;
  unit: Unit;
  minStockLevel: number;
  openingStock: number;
  category?: string;
  createdAt: string;
}

export type TxnType = 'OPENING' | 'PURCHASE' | 'CONSUMPTION';

export interface StockTxn {
  id: string;
  productId: string;
  type: TxnType;
  /** Signed quantity: + for opening/purchase, − for consumption. */
  qty: number;
  date: string; // yyyy-mm-dd
  rate?: number;
  amount?: number;
  /** Informational only — GST does not affect stock quantity or value. */
  gstPercent?: number;
  /** Vendor + bill for purchases; consumption reason otherwise. */
  ref?: string;
  createdAt: string;
}

export interface ProductStockView extends Product {
  currentStock: number;
  lastRate: number;
  stockValue: number;
  lowStock: boolean;
}

export interface PurchaseLineInput {
  productId: string;
  qty: number;
  rate: number;
  gstPercent?: number;
}

export interface PurchaseInput {
  vendor: string;
  billNo: string;
  billDate: string;
  transport?: number;
  lines: PurchaseLineInput[];
}

export interface ExtractedInvoiceLine {
  productId: string;
  productName: string;
  qty: number;
  rate: number;
  gstPercent: number;
  matched: boolean;
}

export interface ExtractedInvoice {
  vendor: string;
  category: string;
  billNo: string;
  billDate: string;
  lines: ExtractedInvoiceLine[];
}

export interface ConsumptionLineInput {
  productId: string;
  qty: number;
  reason?: string;
}

export interface ConsumptionInput {
  date: string;
  lines: ConsumptionLineInput[];
}
