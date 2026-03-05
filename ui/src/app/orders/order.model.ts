export type OrderStatus = 'Accepted' | 'Preparing' | 'Ready' | 'Delivered' | 'Pending' | 'Unknown' | string;

export interface OrderSummary {
  name?: string;
  customer?: string;
  company?: string;
  transaction_date?: string;
  delivery_date?: string;
  currency?: string;
  price_list_currency?: string;
  aas_vendor?: string;
  aas_vendor_pdf?: string;
  aas_status?: string;
  status?: string;
  grand_total?: number;
  aas_cost_total?: number;
  aas_margin_total?: number;
  aas_margin_percent?: number;
  aas_po?: string;
  aas_vendor_bill_total?: number;
  aas_vendor_bill_ref?: string;
  aas_vendor_bill_date?: string;
  aas_sell_order_total?: number;
  aas_so_branch?: string;
  aas_si_branch?: string;
}

export interface OrderView {
  id: string;
  customer: string;
  vendor: string;
  status: OrderStatus;
  statusLabel: string;
  statusTone: 'neutral' | 'success' | 'warning' | 'info';
  orderDate: string;
  deliveryDate: string;
  totalLabel: string;
  isFinal: boolean;
  isVendorAssigned: boolean;
  raw: OrderSummary;
}

export interface OrderFilters {
  customer?: string;
  vendor?: string;
  status?: string;
  from?: string;
  to?: string;
}

export interface OrderCreateLine {
  item_code: string;
  qty: number;
  rate: number;
}

export interface OrderCreatePayload {
  customer: string;
  company: string;
  transaction_date: string;
  delivery_date: string;
  items: OrderCreateLine[];
}

export interface OrderCreateResult {
  id: string;
  customer: string;
  transactionDate?: string;
}

export interface VendorBillPayload {
  vendor_bill_total: number;
  vendor_bill_ref?: string;
  vendor_bill_date?: string;
  margin_percent?: number;
}

export interface SellPreview {
  orderId: string;
  vendorBillTotal: number;
  marginPercent: number;
  sellAmount: number;
  marginAmount: number;
}

export interface OrderOption {
  id: string;
  name: string;
}

export interface ItemOption {
  id: string;
  name: string;
  code: string;
  category?: string;
  unit?: string;
}
