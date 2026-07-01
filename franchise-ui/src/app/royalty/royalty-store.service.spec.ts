import { TestBed } from '@angular/core/testing';
import { RoyaltyStoreService } from './royalty-store.service';
import { SalesStoreService } from '../sales/sales-store.service';
import { SaleInput } from '../sales/sales.model';

const SALES_KEY = 'franchise.sales.entries';
const ROY_CONFIG_KEY = 'franchise.royalty.config';
const ROY_ENTRIES_KEY = 'franchise.royalty.entries';
const YM = '2026-06';

function sale(partial: Partial<SaleInput>): SaleInput {
  return {
    date: '2026-06-10',
    grossSales: 1000,
    gstAmount: 0,
    discount: 0,
    cash: 1000,
    upi: 0,
    card: 0,
    ...partial,
  };
}

describe('RoyaltyStoreService', () => {
  let royalty: RoyaltyStoreService;
  let sales: SalesStoreService;

  beforeEach((done) => {
    localStorage.clear();
    localStorage.setItem(SALES_KEY, JSON.stringify([]));
    localStorage.setItem(ROY_CONFIG_KEY, JSON.stringify({ ratePercent: 10 }));
    localStorage.setItem(ROY_ENTRIES_KEY, JSON.stringify([]));
    TestBed.configureTestingModule({});

    sales = TestBed.inject(SalesStoreService);
    royalty = TestBed.inject(RoyaltyStoreService);

    // Seed a deterministic June net-sales total of 10000.
    sales.create(sale({ date: '2026-06-05', grossSales: 6000, discount: 0 })).subscribe(() => {
      sales.create(sale({ date: '2026-06-15', grossSales: 5000, discount: 1000 })).subscribe(() => {
        // net = 6000 + (5000-1000) = 10000
        done();
      });
    });
  });

  it('generateForMonth sets dueAmount = round(netSalesBase * rate/100)', (done) => {
    expect(sales.netSalesForMonth(YM)).toBe(10000);
    royalty.generateForMonth(YM).subscribe((entry) => {
      expect(entry.netSalesBase).toBe(10000);
      expect(entry.ratePercent).toBe(10);
      expect(entry.dueAmount).toBe(1000); // 10000 * 10 / 100
      expect(entry.status).toBe('Pending');
      done();
    });
  });

  it('recordPayment transitions Pending -> Partial -> Paid', (done) => {
    royalty.generateForMonth(YM).subscribe((entry) => {
      royalty.recordPayment(entry.id, 400).subscribe((partial) => {
        expect(partial.status).toBe('Partial');
        expect(partial.paidAmount).toBe(400);
        royalty.recordPayment(entry.id, 600).subscribe((paid) => {
          expect(paid.paidAmount).toBe(1000);
          expect(paid.status).toBe('Paid');
          expect(paid.paidDate).toBeTruthy();
          done();
        });
      });
    });
  });

  it('dueForMonth returns the locked-in due for an existing entry', (done) => {
    royalty.generateForMonth(YM).subscribe(() => {
      expect(royalty.dueForMonth(YM)).toBe(1000);
      done();
    });
  });

  it('dueForMonth computes from sales+rate when no entry exists', () => {
    // No entry generated for this month yet.
    expect(royalty.dueForMonth(YM)).toBe(1000);
  });

  it('outstandingTotal reflects due minus paid across entries', (done) => {
    royalty.generateForMonth(YM).subscribe((entry) => {
      expect(royalty.outstandingTotal()).toBe(1000);
      royalty.recordPayment(entry.id, 400).subscribe(() => {
        expect(royalty.outstandingTotal()).toBe(600);
        done();
      });
    });
  });
});
