import { TestBed } from '@angular/core/testing';
import { ExpenseStoreService } from './expense-store.service';
import { ExpenseInput } from './expense.model';

const ENTRIES_KEY = 'franchise.expenses.entries';

function input(partial: Partial<ExpenseInput>): ExpenseInput {
  return {
    date: '2026-06-10',
    category: 'Misc',
    amount: 100,
    ...partial,
  } as ExpenseInput;
}

describe('ExpenseStoreService', () => {
  let service: ExpenseStoreService;

  beforeEach((done) => {
    localStorage.clear();
    localStorage.setItem(ENTRIES_KEY, JSON.stringify([]));
    TestBed.configureTestingModule({});
    service = TestBed.inject(ExpenseStoreService);

    // Seed a known set via the public API.
    const seeds: ExpenseInput[] = [
      input({ date: '2026-06-02', category: 'Rent', amount: 35000 }),
      input({ date: '2026-06-05', category: 'Electricity', amount: 6000 }),
      input({ date: '2026-06-08', category: 'Misc', amount: 1000 }),
      input({ date: '2026-06-12', category: 'Misc', amount: 500 }),
      input({ date: '2026-05-02', category: 'Rent', amount: 35000 }), // different month
    ];
    let i = 0;
    const next = () => {
      if (i >= seeds.length) {
        done();
        return;
      }
      service.create(seeds[i++]).subscribe(next);
    };
    next();
  });

  it('totalForMonth sums all expenses in the month only', () => {
    // June: 35000 + 6000 + 1000 + 500 = 42500
    expect(service.totalForMonth('2026-06')).toBe(42500);
    expect(service.totalForMonth('2026-05')).toBe(35000);
  });

  it('categoryTotal returns per-category sum for the month', () => {
    expect(service.categoryTotal('2026-06', 'Rent')).toBe(35000);
    expect(service.categoryTotal('2026-06', 'Electricity')).toBe(6000);
    expect(service.categoryTotal('2026-06', 'Misc')).toBe(1500);
    expect(service.categoryTotal('2026-06', 'Internet')).toBe(0);
  });

  it('byCategory returns nonzero categories sorted by total desc', () => {
    const rows = service.byCategory('2026-06');
    expect(rows.map((r) => r.category)).toEqual(['Rent', 'Electricity', 'Misc']);
    expect(rows.map((r) => r.total)).toEqual([35000, 6000, 1500]);
  });
});
