export interface VendorOpsSummaryTotals {
  totalVendors: number;
  vendorsWithPendingOrders: number;
  totalPendingOrders: number;
  awaitingPdf: number;
  awaitingBillCapture: number;
  totalPendingBillAmount: number;
}

export interface VendorOpsSummaryRow {
  vendorId: string;
  vendorName: string;
  pendingOrders: number;
  awaitingPdf: number;
  awaitingBillCapture: number;
  inProgress: number;
  pendingBillAmount: number;
  lastActivity: string;
  templateStatus: string;
  ledgerBalance: number;
  parseSuccessRate: number;
}

export interface VendorOpsDetail {
  vendor: {
    vendorId: string;
    vendorName: string;
    templateStatus: string;
    lastActivity: string;
  };
  kpis: {
    pendingOrders: number;
    awaitingPdf: number;
    awaitingBillCapture: number;
    totalVendorBillAmount: number;
    outstandingBalance: number;
    parseSuccessRate: number;
  };
  template: {
    status: string;
    hasTemplateJson: boolean;
    hasSamplePdf: boolean;
    active: boolean;
  };
  billing: {
    billsCaptured: number;
    unpaidPurchaseInvoices: number;
    outstandingAmount: number;
    ledgerBalance: number;
  };
  exceptions: {
    mismatchCount: number;
    parseFailureCount: number;
    missingTemplate: boolean;
    awaitingPdfTooLong: number;
  };
}

export interface VendorOpsOrderRow {
  orderId: string;
  branch: string;
  orderDate: string;
  deliveryDate: string;
  status: string;
  pdfUploaded: boolean;
  vendorPdf: string;
  parsedItems: number;
  vendorBillTotal: number;
  billRef: string;
  billDate: string;
  poNumber: string;
  purchaseInvoice: string;
  lastUpdated: string;
  hasMismatch: boolean;
  itemsTotal: number;
  sourceOrderMargin: number;
  assignmentToPdfHours: number;
  pdfToBillHours: number;
}

export interface VendorOpsLedgerEntry {
  date: string;
  voucherType: string;
  voucherNo: string;
  reference: string;
  debit: number;
  credit: number;
  netChange: number;
  runningBalance: number;
}

export interface VendorOpsAnalytics {
  vendorId: string;
  ordersByStatus: Array<{ status: string; count: number }>;
  billedAmountByBranch: Array<{ branch: string; total: number }>;
  topItemsByQty: Array<{ item: string; qty: number }>;
  topItemsByValue: Array<{ item: string; value: number }>;
  turnaround: {
    avgAssignmentToPdfHours: number;
    avgPdfToBillHours: number;
    parseSuccessRate: number;
    billCaptureRate: number;
  };
}
