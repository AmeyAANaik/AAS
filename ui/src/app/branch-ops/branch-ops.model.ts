export interface BranchOpsSummaryTotals {
  totalBranches: number;
  branchesWithPendingOrders: number;
  totalPendingOrders: number;
  awaitingVendorAssignment: number;
  awaitingVendorResponse: number;
  openReceivableAmount: number;
}

export interface BranchOpsSummaryRow {
  branchId: string;
  branchName: string;
  pendingOrders: number;
  awaitingVendorAssignment: number;
  awaitingVendorResponse: number;
  inProgress: number;
  openReceivableAmount: number;
  lastActivity: string;
  location: string;
  ledgerBalance: number;
  paymentCollectionRate: number;
}

export interface BranchOpsDetail {
  branch: {
    branchId: string;
    branchName: string;
    location: string;
    creditDays: number;
    lastActivity: string;
  };
  kpis: {
    pendingOrders: number;
    awaitingVendorAssignment: number;
    awaitingVendorResponse: number;
    openReceivableAmount: number;
    invoicedAmount: number;
    paymentCollectionRate: number;
  };
  billing: {
    invoicesRaised: number;
    openInvoices: number;
    paymentsReceived: number;
    approvedAdjustmentNotes: number;
    ledgerBalance: number;
  };
  exceptions: {
    unassignedOrders: number;
    awaitingVendorPdf: number;
    awaitingBillCapture: number;
    overdueInvoices: number;
  };
}

export interface BranchOpsOrderRow {
  orderId: string;
  branch: string;
  vendor: string;
  orderDate: string;
  deliveryDate: string;
  status: string;
  pdfUploaded: boolean;
  vendorBillTotal: number;
  sellOrderTotal: number;
  invoiceId: string;
  parsedItems: number;
  poNumber: string;
  lastUpdated: string;
}

export interface BranchOpsLedgerEntry {
  date: string;
  voucherType: string;
  voucherNo: string;
  reference: string;
  debit: number;
  credit: number;
  netChange: number;
  runningBalance: number;
}

export interface BranchOpsCategorySummaryRow {
  category: string;
  amount: number;
  balance?: number;
}

export type BranchOpsRiskTier = 'GOOD' | 'WATCH' | 'DEFAULTER';

export type BranchOpsAgingBucketKey = 'notDue' | 'd1_7' | 'd8_15' | 'd16_30' | 'd30Plus';

export interface BranchOpsAgingBucket {
  key: BranchOpsAgingBucketKey;
  label: string;
}

export interface BranchOpsAgingCoverage {
  settledSubmittedInvoices: number;
  referencedSettledInvoices: number;
  onTimeCoveragePct: number;
  onTimeReliable: boolean;
  note?: string;
}

export interface BranchOpsAgingRow {
  branchId: string;
  branchName: string;
  location: string;
  creditDays: number;

  submittedOutstanding: number;
  notDue: number;
  d1_7: number;
  d8_15: number;
  d16_30: number;
  d30Plus: number;
  overdueAmount: number;
  openInvoiceCount: number;
  overdueInvoiceCount: number;
  oldestOverdueDays: number;
  oldestOverdueInvoiceId: string;
  oldestOverdueDueDate: string;

  draftUnbilledAmount: number;
  draftInvoiceCount: number;
  oldestDraftDays: number;

  ledgerBalance: number;
  unappliedCredits: number;

  onTimePaymentPct: number | null;
  onTimePaymentSample: number;
  onTimePaymentDenominator: number;
  onTimeCoveragePct: number;
  onTimeReliable: boolean;
  dueDateMissingCount: number;

  riskScore: number;
  riskScoreMax: number;
  riskPct: number;
  riskTier: BranchOpsRiskTier;
  riskBasis: 'FULL' | 'AGING_ONLY';
  riskReasons: string[];
}

export interface BranchOpsAgingTotals {
  branches: number;
  notDue: number;
  d1_7: number;
  d8_15: number;
  d16_30: number;
  d30Plus: number;
  submittedOutstanding: number;
  overdueAmount: number;
  draftUnbilledAmount: number;
  ledgerBalance: number;
  unappliedCredits: number;
  defaulterBranches: number;
  watchBranches: number;
  goodBranches: number;
}

export interface BranchOpsAgingSummary {
  asOfDate: string;
  asOfDateIsHistorical: boolean;
  buckets: BranchOpsAgingBucket[];
  coverage: BranchOpsAgingCoverage;
  totals: BranchOpsAgingTotals;
  collectionsRanking: string[];
  backlogRanking: string[];
  branches: BranchOpsAgingRow[];
}

export interface BranchOpsAgingReconciliation {
  submittedOutstanding: number;
  draftUnbilled: number;
  unappliedCredits: number;
  ledgerBalance: number;
  balanced: boolean;
}

export interface BranchOpsAgingInvoiceRow {
  invoiceId: string;
  stage: 'SUBMITTED' | 'DRAFT';
  docstatus: number;
  postingDate: string;
  dueDate: string;
  dueDateSource: 'ERP' | 'DERIVED';
  status: string;
  invoiceAmount: number;
  outstandingAmount: number;
  paidAmount: number;
  daysPastDue: number | null;
  bucket: BranchOpsAgingBucketKey | 'draft';
  bucketLabel: string;
  settlementDate: string;
  paidOnTime: boolean | null;
}

export interface BranchOpsAgingDetail {
  asOfDate: string;
  asOfDateIsHistorical: boolean;
  buckets: BranchOpsAgingBucket[];
  branch: {
    branchId: string;
    branchName: string;
    location: string;
    creditDays: number;
  };
  summary: BranchOpsAgingRow;
  reconciliation: BranchOpsAgingReconciliation;
  coverage: BranchOpsAgingCoverage;
  invoices: BranchOpsAgingInvoiceRow[];
}

export interface BranchOpsAnalytics {
  branchId: string;
  ordersByStatus: Array<{ status: string; count: number }>;
  billedAmountByVendor: Array<{ vendor: string; total: number }>;
  topItemsByQty: Array<{ item: string; qty: number }>;
  topItemsByValue: Array<{ item: string; value: number }>;
  turnaround: {
    avgOrderToInvoiceHours: number;
    paymentCollectionRate: number;
    avgInvoiceToPaymentHours: number;
  };
}
