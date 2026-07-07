export type RoyaltyStatus = 'Pending' | 'Partial' | 'Paid';

export interface RoyaltyConfig {
  ratePercent: number;
  branchRates?: RoyaltyBranchRate[];
}

export interface RoyaltyBranchRate {
  branchName: string;
  ratePercent: number;
}

export interface RoyaltyEntry {
  id: string;
  branchName: string;
  month: string; // yyyy-mm
  netSalesBase: number;
  ratePercent: number;
  dueAmount: number;
  paidAmount: number;
  status: RoyaltyStatus;
  generatedDate: string;
  paidDate?: string;
  remarks?: string;
  createdAt: string;
}
