import { TestBed } from '@angular/core/testing';
import { PnlService } from './pnl.service';
import { SalesStoreService } from '../sales/sales-store.service';
import { ExpenseStoreService } from '../expenses/expense-store.service';
import { SalaryStoreService } from '../salary/salary-store.service';
import { RoyaltyStoreService } from '../royalty/royalty-store.service';

const SALES_KEY = 'franchise.sales.entries.v2';
const MODES_KEY = 'franchise.sales.payment-modes';
const EXP_KEY = 'franchise.expenses.entries';
const SAL_EMP_KEY = 'franchise.salary.employees';
const SAL_PAY_KEY = 'franchise.salary.payments';
const ROY_CONFIG_KEY = 'franchise.royalty.config';
const ROY_ENTRIES_KEY = 'franchise.royalty.entries';
const INV_PRODUCTS_KEY = 'franchise.inventory.products';
const INV_TXNS_KEY = 'franchise.inventory.txns';

const YM = '2026-06';

describe('PnlService', () => {
  let pnl: PnlService;
  let sales: SalesStoreService;
  let expenses: ExpenseStoreService;
  let salary: SalaryStoreService;
  let royalty: RoyaltyStoreService;

  beforeEach((done) => {
    localStorage.clear();
    // Pre-seed every dependent store with empty data so no demo seeding runs.
    localStorage.setItem(MODES_KEY, JSON.stringify([]));
    localStorage.setItem(SALES_KEY, JSON.stringify([]));
    localStorage.setItem(EXP_KEY, JSON.stringify([]));
    localStorage.setItem(SAL_EMP_KEY, JSON.stringify([]));
    localStorage.setItem(SAL_PAY_KEY, JSON.stringify([]));
    localStorage.setItem(ROY_CONFIG_KEY, JSON.stringify({ ratePercent: 10 }));
    localStorage.setItem(ROY_ENTRIES_KEY, JSON.stringify([]));
    localStorage.setItem(INV_PRODUCTS_KEY, JSON.stringify([]));
    localStorage.setItem(INV_TXNS_KEY, JSON.stringify([]));

    TestBed.configureTestingModule({});
    sales = TestBed.inject(SalesStoreService);
    expenses = TestBed.inject(ExpenseStoreService);
    salary = TestBed.inject(SalaryStoreService);
    royalty = TestBed.inject(RoyaltyStoreService);
    pnl = TestBed.inject(PnlService);

    // One sale: net = 100000.
    sales.create({
      date: '2026-06-10', grossSales: 100000, gstAmount: 0, discount: 0,
      payments: [{ modeId: 'cash', modeName: 'Cash', amount: 100000 }],
    }).subscribe(() => {
      // One Rent, one Electricity, one Misc expense.
      expenses.create({ date: '2026-06-02', category: 'Rent', amount: 30000 }).subscribe(() => {
        expenses.create({ date: '2026-06-05', category: 'Electricity', amount: 5000 }).subscribe(() => {
          expenses.create({ date: '2026-06-08', category: 'Misc', amount: 2000 }).subscribe(() => {
            // One salary payment via an employee + generateMonth.
            salary.createEmployee({
              name: 'Worker', role: 'Staff', monthlySalary: 25000,
              status: 'Active', joinedDate: '2024-01-01',
            }).subscribe(() => {
              salary.generateMonth(YM).subscribe(() => done());
            });
          });
        });
      });
    });
  });

  it('computeMonth aggregates every line correctly', () => {
    const p = pnl.computeMonth(YM);

    expect(p.revenue).toBe(100000);
    expect(p.rent).toBe(30000);
    expect(p.electricity).toBe(5000);
    expect(p.salary).toBe(25000);
    // royalty = netSales(100000) * 10% = 10000
    expect(p.royalty).toBe(10000);
    expect(p.rawMaterial).toBe(0); // no inventory consumption

    // otherExpenses = totalForMonth - rent - electricity = (30000+5000+2000) - 30000 - 5000 = 2000
    expect(p.otherExpenses).toBe(2000);
    expect(p.otherExpenses).toBe(expenses.totalForMonth(YM) - p.rent - p.electricity);

    // totalCost = rawMaterial + salary + rent + electricity + royalty + otherExpenses
    const expectedTotalCost = 0 + 25000 + 30000 + 5000 + 10000 + 2000; // 72000
    expect(p.totalCost).toBe(expectedTotalCost);

    // netProfit = revenue - totalCost = 100000 - 72000 = 28000
    expect(p.netProfit).toBe(28000);

    // marginPercent = netProfit / revenue * 100 = 28
    expect(p.marginPercent).toBe(28);
  });
});
