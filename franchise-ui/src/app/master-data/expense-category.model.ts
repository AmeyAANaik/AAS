export type ExpenseCategoryStatus = 'Active' | 'Inactive';
export type ExpensePnlBucket = 'rent' | 'electricity' | 'other';

export interface ExpenseCategoryRecord {
  id: string;
  code: string;
  name: string;
  status: ExpenseCategoryStatus;
  pnlBucket: ExpensePnlBucket;
  sortOrder: number;
  system: boolean;
}

export interface ExpenseCategoryInput {
  name: string;
  code: string;
  status: ExpenseCategoryStatus;
  pnlBucket: ExpensePnlBucket;
}
