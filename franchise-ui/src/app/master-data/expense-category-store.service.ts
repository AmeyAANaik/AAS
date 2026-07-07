import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, of } from 'rxjs';
import { ExpenseCategoryInput, ExpenseCategoryRecord, ExpensePnlBucket } from './expense-category.model';

const STORAGE_KEY = 'franchise.expense-categories.v1';

export const SEED_EXPENSE_CATEGORIES: ExpenseCategoryRecord[] = [
  { id: 'expense-cat-electricity', code: 'ELECTRICITY', name: 'Electricity', status: 'Active', pnlBucket: 'electricity', sortOrder: 10, system: true },
  { id: 'expense-cat-rent', code: 'RENT', name: 'Rent', status: 'Active', pnlBucket: 'rent', sortOrder: 20, system: true },
  { id: 'expense-cat-gst', code: 'GST', name: 'GST', status: 'Active', pnlBucket: 'other', sortOrder: 30, system: true },
  { id: 'expense-cat-internet', code: 'INTERNET', name: 'Internet', status: 'Active', pnlBucket: 'other', sortOrder: 40, system: true },
  { id: 'expense-cat-marketing', code: 'MARKETING', name: 'Marketing', status: 'Active', pnlBucket: 'other', sortOrder: 50, system: true },
  { id: 'expense-cat-maintenance', code: 'MAINTENANCE', name: 'Maintenance', status: 'Active', pnlBucket: 'other', sortOrder: 60, system: true },
  { id: 'expense-cat-misc', code: 'MISC', name: 'Misc', status: 'Active', pnlBucket: 'other', sortOrder: 70, system: true }
];

@Injectable({ providedIn: 'root' })
export class ExpenseCategoryStoreService {
  private readonly categories$: BehaviorSubject<ExpenseCategoryRecord[]>;
  readonly changes$: Observable<ExpenseCategoryRecord[]>;

  constructor() {
    this.categories$ = new BehaviorSubject<ExpenseCategoryRecord[]>(this.load());
    this.changes$ = this.categories$.asObservable();
  }

  list(): Observable<ExpenseCategoryRecord[]> {
    return of(this.listSnapshot());
  }

  listSnapshot(): ExpenseCategoryRecord[] {
    return this.sorted(this.categories$.value).map(category => ({ ...category }));
  }

  activeSnapshot(): ExpenseCategoryRecord[] {
    return this.listSnapshot().filter(category => category.status === 'Active');
  }

  bucketFor(name: string): ExpensePnlBucket {
    const match = this.categories$.value.find(category => category.name.toLowerCase() === String(name).toLowerCase());
    return match?.pnlBucket ?? 'other';
  }

  create(input: ExpenseCategoryInput): Observable<ExpenseCategoryRecord> {
    const record: ExpenseCategoryRecord = {
      id: `expense-cat-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
      code: this.normalizeCode(input.code || input.name),
      name: input.name.trim(),
      status: input.status,
      pnlBucket: input.pnlBucket,
      sortOrder: this.categories$.value.length * 10 + 10,
      system: false
    };
    const next = [...this.categories$.value, record];
    this.persist(next);
    this.categories$.next(next);
    return of({ ...record });
  }

  update(id: string, input: ExpenseCategoryInput): Observable<ExpenseCategoryRecord | null> {
    let updated: ExpenseCategoryRecord | null = null;
    const next = this.categories$.value.map(category => {
      if (category.id !== id) {
        return category;
      }
      updated = {
        ...category,
        code: this.normalizeCode(input.code || input.name),
        name: input.name.trim(),
        status: input.status,
        pnlBucket: input.pnlBucket
      };
      return updated;
    });
    if (updated) {
      this.persist(next);
      this.categories$.next(next);
    }
    return of(updated);
  }

  setStatus(id: string, status: 'Active' | 'Inactive'): Observable<ExpenseCategoryRecord | null> {
    const current = this.categories$.value.find(category => category.id === id);
    if (!current) {
      return of(null);
    }
    return this.update(id, {
      name: current.name,
      code: current.code,
      status,
      pnlBucket: current.pnlBucket
    });
  }

  private load(): ExpenseCategoryRecord[] {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (raw) {
        return this.mergeSeeds(JSON.parse(raw) as ExpenseCategoryRecord[]);
      }
    } catch {
      /* ignore */
    }
    return SEED_EXPENSE_CATEGORIES.map(category => ({ ...category }));
  }

  private mergeSeeds(saved: ExpenseCategoryRecord[]): ExpenseCategoryRecord[] {
    const byId = new Map(saved.map(category => [category.id, category]));
    SEED_EXPENSE_CATEGORIES.forEach(seed => {
      if (!byId.has(seed.id)) {
        byId.set(seed.id, seed);
      }
    });
    return this.sorted([...byId.values()]);
  }

  private sorted(categories: ExpenseCategoryRecord[]): ExpenseCategoryRecord[] {
    return categories.slice().sort((a, b) => a.sortOrder - b.sortOrder || a.name.localeCompare(b.name));
  }

  private normalizeCode(value: string): string {
    return String(value || '')
      .trim()
      .toUpperCase()
      .replace(/[^A-Z0-9]+/g, '_')
      .replace(/^_+|_+$/g, '');
  }

  private persist(categories: ExpenseCategoryRecord[]): void {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(categories));
    } catch {
      /* ignore */
    }
  }
}
