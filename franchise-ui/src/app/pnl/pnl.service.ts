import { inject, Injectable } from '@angular/core';
import { SalesStoreService } from '../sales/sales-store.service';
import { ExpenseStoreService } from '../expenses/expense-store.service';
import { SalaryStoreService } from '../salary/salary-store.service';
import { InventoryStoreService } from '../inventory/inventory-store.service';
import { RoyaltyStoreService } from '../royalty/royalty-store.service';
import { PnlDimension, PnlMetric, PnlPeriod } from './pnl.model';

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
    const rent = round(this.expenses.bucketTotal(ym, 'rent'));
    const electricity = round(this.expenses.bucketTotal(ym, 'electricity'));
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

  computeMetrics(period: PnlPeriod): PnlMetric[] {
    const revenue = period.revenue || 0;
    const totalCost = period.totalCost || 0;
    const basis = Math.max(revenue, totalCost, 1);
    const share = (amount: number) => round((Math.abs(amount) / basis) * 100);
    const marginTone = period.netProfit >= 0 ? 'good' : 'bad';

    return [
      {
        dimension: 'summary',
        metric: 'Revenue',
        amount: period.revenue,
        sharePercent: share(period.revenue),
        source: 'Daily sales entries',
        tone: 'neutral'
      },
      {
        dimension: 'summary',
        metric: 'Total cost',
        amount: period.totalCost,
        sharePercent: share(period.totalCost),
        source: 'Inventory, salary, expenses, royalty',
        tone: period.totalCost > period.revenue ? 'warn' : 'neutral'
      },
      {
        dimension: 'summary',
        metric: 'Net profit',
        amount: period.netProfit,
        sharePercent: revenue ? Math.abs(period.marginPercent) : 0,
        source: 'Revenue minus total cost',
        tone: marginTone
      },
      {
        dimension: 'revenue',
        metric: 'Net sales base',
        amount: period.revenue,
        sharePercent: share(period.revenue),
        source: 'Sales store',
        tone: 'neutral'
      },
      {
        dimension: 'cost',
        metric: 'Raw material consumption',
        amount: period.rawMaterial,
        sharePercent: share(period.rawMaterial),
        source: 'Stock consumption ledger',
        tone: 'neutral'
      },
      {
        dimension: 'cost',
        metric: 'Salary',
        amount: period.salary,
        sharePercent: share(period.salary),
        source: 'Salary payments',
        tone: period.salary > revenue * 0.6 && revenue > 0 ? 'warn' : 'neutral'
      },
      {
        dimension: 'cost',
        metric: 'Rent',
        amount: period.rent,
        sharePercent: share(period.rent),
        source: 'Expense category',
        tone: 'neutral'
      },
      {
        dimension: 'cost',
        metric: 'Electricity',
        amount: period.electricity,
        sharePercent: share(period.electricity),
        source: 'Expense category',
        tone: 'neutral'
      },
      {
        dimension: 'cost',
        metric: 'Royalty',
        amount: period.royalty,
        sharePercent: share(period.royalty),
        source: 'Royalty ledger or current rate',
        tone: 'neutral'
      },
      {
        dimension: 'cost',
        metric: 'Other expenses',
        amount: period.otherExpenses,
        sharePercent: share(period.otherExpenses),
        source: 'Expense categories excluding rent/electricity',
        tone: 'neutral'
      },
      {
        dimension: 'operations',
        metric: 'Cost coverage gap',
        amount: round(period.revenue - period.totalCost),
        sharePercent: revenue ? Math.abs(period.marginPercent) : 0,
        source: 'Aggregated module metrics',
        tone: marginTone
      }
    ];
  }

  metricsForDimension(period: PnlPeriod, dimension: PnlDimension): PnlMetric[] {
    const metrics = this.computeMetrics(period);
    return dimension === 'summary' ? metrics.filter(m => m.dimension === 'summary') : metrics.filter(m => m.dimension === dimension);
  }

  /** `2026-06` → `Jun 2026`. */
  private monthLabel(ym: string): string {
    const [y, m] = ym.split('-').map(Number);
    const name = MONTHS[(m - 1 + 12) % 12] ?? ym;
    return `${name} ${y}`;
  }
}
