/**
 * A configurable payment mode (the "Daily Sales Master"). Replaces the old
 * hardcoded Cash/UPI/Card so franchises can add their own channels
 * (e.g. Swiggy, Zomato, Paytm) and enable/disable them over time.
 */
export interface PaymentMode {
  id: string;
  name: string;
  enabled: boolean;
  sortOrder: number;
  createdAt: string;
}

export interface PaymentModeInput {
  name: string;
  enabled: boolean;
}

/**
 * One collected payment within a daily sale. `modeName` is denormalised so old
 * entries still render correctly even if the master mode is later renamed or
 * removed. `attachmentName` is a mock upload — only the file name is stored.
 */
export interface PaymentLine {
  modeId: string;
  modeName: string;
  amount: number;
  remark?: string;
  attachmentName?: string;
}

export interface PaymentLineInput {
  modeId: string;
  modeName: string;
  amount: number;
  remark?: string;
  attachmentName?: string;
}

export interface SaleEntry {
  id: string;
  date: string;        // yyyy-mm-dd
  grossSales: number;
  gstAmount: number;
  discount: number;
  netSales: number;    // grossSales - discount
  payments: PaymentLine[];
  remarks?: string;
  createdAt: string;
}

export interface SaleInput {
  date: string;
  grossSales: number;
  gstAmount: number;
  discount: number;
  payments: PaymentLineInput[];
  remarks?: string;
}

/** Total collected across a sale's payment lines. */
export function paymentsTotal(payments: PaymentLine[] | PaymentLineInput[]): number {
  const total = (payments ?? []).reduce((sum, p) => sum + (Number(p.amount) || 0), 0);
  return Math.round(total * 100) / 100;
}
