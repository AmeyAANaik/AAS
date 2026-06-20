export type InvoiceStatus = 'Paid' | 'Unpaid' | 'Overdue' | 'Draft' | string;

export interface InvoiceSummary {
  name?: string;
  customer?: string;
  supplier?: string;
  company?: string;
  posting_date?: string;
  grand_total?: number;
  outstanding_amount?: number;
  status?: string;
  aas_category?: string;
  [key: string]: unknown;
}

export interface InvoiceDeliveryChannelPreview {
  channel: 'email' | 'whatsapp' | string;
  recipient: string;
  configured: boolean;
  missingRecipient: boolean;
  hint: string;
}

export interface InvoiceDeliveryPreview {
  invoiceId: string;
  customer: string;
  customerDisplayName: string;
  postingDate: string;
  grandTotal: number;
  outstandingAmount: number;
  email: InvoiceDeliveryChannelPreview;
  whatsapp: InvoiceDeliveryChannelPreview;
  defaultEmailMessage: string;
  defaultWhatsAppMessage: string;
}

export interface InvoiceDeliveryRequest {
  recipient?: string;
  message?: string;
}

export interface InvoiceView {
  id: string;
  customer: string;
  company: string;
  date: string;
  totalLabel: string;
  status: InvoiceStatus;
  statusTone: 'neutral' | 'success' | 'warning' | 'info';
  emailConfigured: boolean;
  emailRecipient: string;
  whatsappConfigured: boolean;
  whatsappRecipient: string;
  raw: InvoiceSummary;
}

export interface InvoiceFilters {
  partyType?: 'Customer' | 'Supplier' | string;
  partyId?: string;
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
  rounding_adjustment?: number;
  aas_source_sales_order?: string;
  aas_category?: string;
  posting_date?: string;
}

export interface InvoiceCreateResult {
  id: string;
  customer: string;
}

export interface OrderSnapshot {
  name?: string;
  customer?: string;
  company?: string;
  aas_category?: string;
  transaction_date?: string;
  items?: Array<{ item_code?: string; qty?: number; rate?: number }>;
  grand_total?: number;
  aas_rounding_adjustment?: number;
  payment_schedule?: Array<{ due_date?: string; payment_amount?: number; outstanding?: number }>;
}

export interface PaymentPayload {
  customer: string;
  company: string;
  amount: number;
  invoiceId?: string;
  paymentDate?: string;
  modeOfPayment?: string;
  partyType?: 'Customer' | 'Supplier' | string;
  categoryId?: string;
}

export interface PaymentDueSummary {
  dueAmount: number;
  underReviewAmount?: number;
  pendingAdminApprovalAmount?: number;
  pendingAdjustmentAmount?: number;
  availableDueAmount?: number;
}

export interface PaymentPrefillSelection {
  partyType?: string;
  partyId?: string;
  categoryId?: string;
}

export interface OptionItem {
  id: string;
  name: string;
  company?: string;
  categoryId?: string;
}

export interface InvoiceOption {
  id: string;
  name: string;
  customer: string;
  company: string;
  outstanding: number;
}

export interface UploadedFileInfo {
  fileName: string;
  fileUrl?: string | null;
  fileId?: string | null;
}

export interface AdjustmentNotePayload {
  partyType: 'Customer' | 'Supplier' | string;
  partyId: string;
  categoryId: string;
  itemCode: string;
  itemName?: string;
  direction: 'GIVE' | 'TAKE' | string;
  amount: number;
  noteDate?: string;
  reason?: string;
}

export interface AdjustmentNoteDocument {
  name?: string;
  posting_date?: string;
  docstatus?: number;
  aas_adjustment_direction?: string;
  aas_adjustment_note_type?: string;
  aas_adjustment_party_type?: string;
  aas_adjustment_party?: string;
  aas_adjustment_amount?: number;
  aas_adjustment_reason?: string;
  aas_reference_invoice?: string;
  aas_adjustment_item_name?: string;
  aas_adjustment_review_status?: string;
  aas_due_amount?: number;
  aas_category?: string;
}

export interface AdjustmentNoteResult {
  note: AdjustmentNoteDocument;
  files: UploadedFileInfo[];
}

export interface ItemOption {
  id: string;
  name: string;
  code: string;
  unit?: string;
  category?: string;
}
