export type ExpenseCategory = string;

export interface ExpenseEntry {
  id: string;
  date: string;            // yyyy-mm-dd
  category: string;
  amount: number;
  remarks?: string;
  billFileName?: string;   // mock attachment: only the chosen file's name is stored
  createdAt: string;
}

export interface ExpenseInput {
  date: string;
  category: string;
  amount: number;
  remarks?: string;
  billFileName?: string;
}
