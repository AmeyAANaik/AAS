import { inject, Injectable } from '@angular/core';
import { SalesStoreService } from '../sales/sales-store.service';
import { ExpenseStoreService } from '../expenses/expense-store.service';
import { SalaryStoreService } from '../salary/salary-store.service';
import { InventoryStoreService } from '../inventory/inventory-store.service';
import { RoyaltyStoreService } from '../royalty/royalty-store.service';
import { PnlPeriod } from './pnl.model';

const MONTHS = [
  'Jan', 'Feb', 'Mar', 'Apr', 'May', 'Jun',
  'Jul', 'Aug', 'Sep', 'Oct', 'Nov', 'Dec'
];

function round(n: number): number {
  return Math.round(n * 100) / 100;
}

/**
 * Read-only P&L aggregation across every operational module. Owns no state:
 * each figure is computed on demand from the underlying stores.
 */
@Injectable({ providedIn: 'root' })
export class PnlService {
  private readonly sales = inject(SalesStoreService);
  private readonly expenses = inject(ExpenseStoreService);
  private readonly salary = inject(SalaryStoreService);
  private readonly inventory = inject(InventoryStoreService);
  private readonly royalty = inject(RoyaltyStoreService);

  /** Compute the full P&L for a single `yyyy-mm` month. */
  computeMonth(ym: string): PnlPeriod {
    const revenue = round(this.sales.netSalesForMonth(ym));
    const rawMaterial = round(this.inventory.consumptionValueForMonth(ym));
    const salary = round(this.salary.totalForMonth(ym));
    const rent = round(this.expenses.categoryTotal(ym, 'Rent'));
    const electricity = round(this.expenses.categoryTotal(ym, 'Electricity'));
    const royalty = round(this.royalty.dueForMonth(ym));
    const otherExpenses = round(this.expenses.totalForMonth(ym) - rent - electricity);

    const totalCost = round(rawMaterial + salary + rent + electricity + royalty + otherExpenses);
    const netProfit = round(revenue - totalCost);
    const marginPercent = revenue ? round((netProfit / revenue) * 100) : 0;

    return {
      ym,
      label: this.monthLabel(ym),
      revenue,
      rawMaterial,
      salary,
      rent,
      electricity,
      royalty,
      otherExpenses,
      totalCost,
      netProfit,
      marginPercent
    };
  }

  /** One PnlPeriod per month (Jan–Dec) of the given year. */
  computeYear(year: number): PnlPeriod[] {
    const periods: PnlPeriod[] = [];
    for (let m = 1; m <= 12; m++) {
      periods.push(this.computeMonth(`${year}-${String(m).padStart(2, '0')}`));
    }
    return periods;
  }

  /** Whole-year roll-up: the sum of all twelve months. */
  computeYearTotal(year: number): PnlPeriod {
    const months = this.computeYear(year);
    const sum = (pick: (p: PnlPeriod) => number) =>
      round(months.reduce((acc, p) => acc + pick(p), 0));

    const revenue = sum(p => p.revenue);
    const rawMaterial = sum(p => p.rawMaterial);
    const salary = sum(p => p.salary);
    const rent = sum(p => p.rent);
    const electricity = sum(p => p.electricity);
    const royalty = sum(p => p.royalty);
    const otherExpenses = sum(p => p.otherExpenses);

    const totalCost = round(rawMaterial + salary + rent + electricity + royalty + otherExpenses);
    const netProfit = round(revenue - totalCost);
    const marginPercent = revenue ? round((netProfit / revenue) * 100) : 0;

    return {
      ym: String(year),
      label: String(year),
      revenue,
      rawMaterial,
      salary,
      rent,
      electricity,
      royalty,
      otherExpenses,
      totalCost,
      netProfit,
      marginPercent
    };
  }

  /** `2026-06` → `Jun 2026`. */
  private monthLabel(ym: string): string {
    const [y, m] = ym.split('-').map(Number);
    const name = MONTHS[(m - 1 + 12) % 12] ?? ym;
    return `${name} ${y}`;
  }
}
