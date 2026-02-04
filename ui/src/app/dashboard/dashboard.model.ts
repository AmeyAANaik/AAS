export type OrderSummary = {
  name?: string;
  customer?: string;
  company?: string;
  transaction_date?: string;
  delivery_date?: string;
  aas_vendor?: string;
  aas_status?: string;
  status?: string;
  grand_total?: number;
  aas_cost_total?: number;
  aas_margin_total?: number;
  aas_margin_percent?: number;
};

export type BillingSummary = {
  vendor?: string;
  shop?: string;
  total: number;
  cost_total?: number;
  margin_total?: number;
};

export type InvoiceSummary = {
  name?: string;
  customer?: string;
  posting_date?: string;
  grand_total: number;
  status?: string;
};

export type InventoryItem = {
  name?: string;
  stock_qty?: number;
  actual_qty?: number;
  quantity?: number;
  qty?: number;
  [key: string]: unknown;
};

export type OrderStatusRow = {
  status: string;
  count: number;
};

export type BillingRow = {
  name: string;
  total: number;
};

export type StockSnapshot = {
  totalItems: number;
  totalQuantity: number;
};

export type SalesSummary = {
  invoiceCount: number;
  totalRevenue: number;
  dateRangeLabel: string;
};

export type DashboardSnapshot = {
  orderStatus: OrderStatusRow[];
  billsByBranch: BillingRow[];
  billsByVendor: BillingRow[];
  stockSnapshot: StockSnapshot;
  salesSummary: SalesSummary;
  periodLabel: string;
};
