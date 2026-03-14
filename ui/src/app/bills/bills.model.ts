export type InvoiceStatus = 'Paid' | 'Unpaid' | 'Overdue' | 'Draft' | string;

export interface InvoiceSummary {
  name?: string;
  customer?: string;
  company?: string;
  posting_date?: string;
  grand_total?: number;
  outstanding_amount?: number;
  status?: string;
}

export interface InvoiceView {
  id: string;
  customer: string;
  company: string;
  date: string;
  totalLabel: string;
  status: InvoiceStatus;
  statusTone: 'neutral' | 'success' | 'warning' | 'info';
  raw: InvoiceSummary;
}

export interface InvoiceFilters {
  customer?: string;
  from?: string;
  to?: string;
}

export interface InvoiceCreateLine {
  item_code: string;
  qty: number;
  rate: number;
}

export interface InvoiceCreatePayload {
  customer: string;
  company: string;
  items: InvoiceCreateLine[];
  apply_gst?: boolean;
}

export interface InvoiceCreateResult {
  id: string;
  customer: string;
}

export interface OrderSnapshot {
  name?: string;
  customer?: string;
  company?: string;
  items?: Array<{ item_code?: string; qty?: number; rate?: number }>;
  grand_total?: number;
  payment_schedule?: Array<{ due_date?: string; payment_amount?: number; outstanding?: number }>;
}

export interface PaymentPayload {
  customer: string;
  company: string;
  amount: number;
  invoiceId?: string;
  referenceNo?: string;
  referenceDate?: string;
}

export interface OptionItem {
  id: string;
  name: string;
  company?: string;
}

export interface InvoiceOption {
  id: string;
  name: string;
  customer: string;
  company: string;
  outstanding: number;
}

export interface ItemOption {
  id: string;
  name: string;
  code: string;
  unit?: string;
  category?: string;
}
