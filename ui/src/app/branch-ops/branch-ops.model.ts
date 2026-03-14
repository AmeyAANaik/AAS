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
