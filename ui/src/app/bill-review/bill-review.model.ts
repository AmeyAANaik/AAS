export interface BillReviewListItem {
  paymentId: string;
  partyType: string;
  party: string;
  partyName: string;
  postingDate: string;
  paidAmount: number;
  receivedAmount: number;
  categoryId: string;
  dueAmount: number;
  modeOfPayment: string;
  referenceNo: string;
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
  payment: Record<string, any>;
  attachments: BillReviewAttachment[];
  warnings?: Array<{ code: string; recordedDue?: number; currentDue?: number }>;
  currentDueAmount?: number;
  currentAvailableDueAmount?: number;
}

export interface ReviewDecisionRequest {
  notes?: string;
}
