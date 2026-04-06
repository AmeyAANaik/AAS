export interface MasterDataReviewSummary {
  pendingCount: number;
  approvedCount: number;
  defaultMarginCount: number;
  totalCount: number;
}

export interface MasterDataReviewListItem {
  id: string;
  itemCode: string;
  itemName: string;
  category: string;
  uom: string;
  packagingUnit: string;
  marginPercent: number;
  vendorId: string;
  vendorHsnCode: string;
  gstPercent: number;
  reviewStatus: 'PENDING_REVIEW' | 'APPROVED' | 'MERGED' | 'REJECTED' | string;
  sourceOrderId: string;
  sourceInvoiceRef: string;
  createdAt: string;
  createdBy: string;
  reviewNotes: string;
  defaultMarginUsed: boolean;
}

export interface MasterDataReviewDetail extends MasterDataReviewListItem {
  raw?: Record<string, unknown>;
}

export interface ApproveMasterDataReviewRequest {
  item_name: string;
  item_group: string;
  stock_uom: string;
  aas_packaging_unit: string;
  aas_margin_percent: number | null;
  aas_vendor_hsn_code: string;
  aas_gst_percent: number | null;
  reviewNotes: string;
  applyToSourceOrder: boolean;
}
