export type RoyaltyStatus = 'Pending' | 'Partial' | 'Paid';

export interface RoyaltyConfig {
  ratePercent: number;
}

export interface RoyaltyEntry {
  id: string;
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
