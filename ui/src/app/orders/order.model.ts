export type OrderStatus = 'Accepted' | 'Preparing' | 'Ready' | 'Delivered' | 'Pending' | 'Unknown' | string;

export interface OrderSummary {
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
