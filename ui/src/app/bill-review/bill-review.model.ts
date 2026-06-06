export type BillReviewItemType = 'PAYMENT' | 'CREDIT_NOTE' | 'DEBIT_NOTE' | string;

export interface BillReviewListItem {
  itemType: BillReviewItemType;
  documentId: string;
  documentLabel: string;
  paymentId?: string;
  partyType: string;
  party: string;
  partyName: string;
  postingDate: string;
  amount: number;
  paidAmount?: number;
  receivedAmount?: number;
  categoryId: string;
  dueAmount: number;
  modeOfPayment?: string;
  direction?: string;
  referenceNo?: string;
  referenceInvoice?: string;
  reason?: string;
  docstatus: number;
  createdAt: string;
  createdBy: string;
  reviewStatus: string;
  reviewedAt: string;
  reviewedBy: string;
}

export interface BillReviewAttachment {
  id: string;
  name: string;
  url: string;
  isPrivate: boolean;
  createdAt: string;
}

export interface BillReviewDetail {
  itemType: BillReviewItemType;
  documentId: string;
  document: Record<string, any>;
  payment?: Record<string, any>;
  attachments: BillReviewAttachment[];
  warnings?: Array<{ code: string; recordedDue?: number; currentDue?: number }>;
  currentDueAmount?: number;
  currentAvailableDueAmount?: number;
  expectedDueAfterApproval?: number;
  signedImpact?: number;
}

export interface ReviewDecisionRequest {
  notes?: string;
}
