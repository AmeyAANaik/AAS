import { TestBed } from '@angular/core/testing';
import { SalesStoreService } from './sales-store.service';
import { SaleInput } from './sales.model';

const ENTRIES_KEY = 'franchise.sales.entries.v2';
const MODES_KEY = 'franchise.sales.payment-modes';

function input(partial: Partial<SaleInput>): SaleInput {
  return {
    date: '2026-06-10',
    grossSales: 1000,
    gstAmount: 50,
    discount: 100,
    payments: [
      { modeId: 'cash', modeName: 'Cash', amount: 400 },
      { modeId: 'upi', modeName: 'UPI', amount: 400 },
      { modeId: 'card', modeName: 'Card', amount: 100 },
    ],
    remarks: undefined,
    ...partial,
  };
}

describe('SalesStoreService', () => {
  let service: SalesStoreService;

  beforeEach(() => {
    localStorage.clear();
    // Pre-seed an empty entries array so the constructor's load() skips demo seeding.
    localStorage.setItem(MODES_KEY, JSON.stringify([]));
    localStorage.setItem(ENTRIES_KEY, JSON.stringify([]));
    TestBed.configureTestingModule({});
    service = TestBed.inject(SalesStoreService);
  });

  it('create() computes netSales = gross - discount', (done) => {
    service.create(input({ grossSales: 1000, discount: 100 })).subscribe((e) => {
      expect(e.netSales).toBe(900);
      done();
    });
  });

  it('netSalesForMonth sums only matching yyyy-mm', (done) => {
    service.create(input({ date: '2026-06-01', grossSales: 1000, discount: 100 })).subscribe(() => {
      service.create(input({ date: '2026-06-20', grossSales: 500, discount: 0 })).subscribe(() => {
        service.create(input({ date: '2026-05-15', grossSales: 9999, discount: 0 })).subscribe(() => {
          // June net: (1000-100) + (500-0) = 1400
          expect(service.netSalesForMonth('2026-06')).toBe(1400);
          expect(service.netSalesForMonth('2026-05')).toBe(9999);
          done();
        });
      });
    });
  });

  it('grossSalesForMonth sums only matching yyyy-mm', (done) => {
    service.create(input({ date: '2026-06-01', grossSales: 1000 })).subscribe(() => {
      service.create(input({ date: '2026-06-20', grossSales: 500 })).subscribe(() => {
        service.create(input({ date: '2026-07-01', grossSales: 200 })).subscribe(() => {
          expect(service.grossSalesForMonth('2026-06')).toBe(1500);
          expect(service.grossSalesForMonth('2026-07')).toBe(200);
          done();
        });
      });
    });
  });

  it('update() recomputes netSales and applies new values', (done) => {
    service.create(input({ grossSales: 1000, discount: 100 })).subscribe((created) => {
      service.update(created.id, input({ grossSales: 2000, discount: 300 })).subscribe((updated) => {
        expect(updated.netSales).toBe(1700);
        expect(updated.grossSales).toBe(2000);
        done();
      });
    });
  });

  it('remove() deletes the entry', (done) => {
    service.create(input({})).subscribe((created) => {
      service.remove(created.id).subscribe(() => {
        expect(service.listSnapshot().find((e) => e.id === created.id)).toBeUndefined();
        done();
      });
    });
  });

  it('persists to localStorage', (done) => {
    service.create(input({ date: '2026-06-10', grossSales: 1000, discount: 100 })).subscribe((created) => {
      const raw = JSON.parse(localStorage.getItem(ENTRIES_KEY) || '[]');
      expect(raw.some((e: any) => e.id === created.id && e.netSales === 900)).toBeTrue();
      done();
    });
  });
});
