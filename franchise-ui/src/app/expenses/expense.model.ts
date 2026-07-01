export type ExpenseCategory =
  | 'Electricity'
  | 'Rent'
  | 'GST'
  | 'Internet'
  | 'Marketing'
  | 'Maintenance'
  | 'Misc';

export const EXPENSE_CATEGORIES: ExpenseCategory[] = [
  'Electricity', 'Rent', 'GST', 'Internet', 'Marketing', 'Maintenance', 'Misc'
];

export interface ExpenseEntry {
  id: string;
  date: string;            // yyyy-mm-dd
  category: ExpenseCategory;
  amount: number;
  remarks?: string;
  billFileName?: string;   // mock attachment: only the chosen file's name is stored
  createdAt: string;
}

export interface ExpenseInput {
  date: string;
  category: ExpenseCategory;
  amount: number;
  remarks?: string;
  billFileName?: string;
}
