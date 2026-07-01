/**
 * Computed Profit & Loss figures for a single period (a month, or a whole-year
 * roll-up). The P&L module owns NO data of its own — every number here is
 * aggregated read-only from the sales, inventory, salary, expense and royalty
 * stores. All monetary values are rounded to 2 decimals.
 */
export interface PnlPeriod {
  /** `yyyy-mm` for a month, or `yyyy` for a year roll-up. */
  ym: string;
  /** Human-friendly label, e.g. `Jun 2026` or `2026`. */
  label: string;
  /** Net sales for the period. */
  revenue: number;
  /** Raw-material consumption value (COGS). */
  rawMaterial: number;
  /** Salary payouts. */
  salary: number;
  /** Rent expense. */
  rent: number;
  /** Electricity expense. */
  electricity: number;
  /** Franchise royalty due. */
  royalty: number;
  /** All other expenses (everything except Rent & Electricity). */
  otherExpenses: number;
  /** rawMaterial + salary + rent + electricity + royalty + otherExpenses. */
  totalCost: number;
  /** revenue − totalCost. */
  netProfit: number;
  /** netProfit / revenue * 100 (0 when revenue is 0). */
  marginPercent: number;
}

/** A single labelled line in the P&L breakdown table. */
export interface PnlLine {
  label: string;
  amount: number;
  /** `cost` rows are shown as deductions; `total` is the net-profit row. */
  kind: 'revenue' | 'cost' | 'total';
}
