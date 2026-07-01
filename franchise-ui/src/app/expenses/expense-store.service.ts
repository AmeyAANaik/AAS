import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, of, throwError } from 'rxjs';
import { delay } from 'rxjs/operators';
import { EXPENSE_CATEGORIES, ExpenseCategory, ExpenseEntry, ExpenseInput } from './expense.model';

const ENTRIES_KEY = 'franchise.expenses.entries';
const LATENCY = 120;

interface SeedExpense {
  monthOffset: 0 | 1;      // 0 = current month, 1 = previous month
  day: number;
  category: ExpenseCategory;
  amount: number;
  remarks?: string;
  billFileName?: string;
}

const SEED: SeedExpense[] = [
  { monthOffset: 0, day: 2, category: 'Rent', amount: 35000, remarks: 'Shop rent', billFileName: 'rent-receipt.pdf' },
  { monthOffset: 0, day: 5, category: 'Electricity', amount: 6200, remarks: 'Monthly power bill', billFileName: 'electricity.jpg' },
  { monthOffset: 0, day: 8, category: 'Internet', amount: 1499, remarks: 'Broadband' },
  { monthOffset: 0, day: 12, category: 'Marketing', amount: 8500, remarks: 'Social media ads' },
  { monthOffset: 0, day: 18, category: 'Maintenance', amount: 2400, remarks: 'AC servicing', billFileName: 'ac-service.pdf' },
  { monthOffset: 0, day: 22, category: 'Misc', amount: 1200, remarks: 'Stationery' },
  { monthOffset: 1, day: 2, category: 'Rent', amount: 35000, remarks: 'Shop rent', billFileName: 'rent-receipt.pdf' },
  { monthOffset: 1, day: 6, category: 'Electricity', amount: 5800, remarks: 'Monthly power bill' },
  { monthOffset: 1, day: 10, category: 'GST', amount: 14200, remarks: 'Quarterly GST payment', billFileName: 'gst-challan.pdf' },
  { monthOffset: 1, day: 15, category: 'Internet', amount: 1499, remarks: 'Broadband' },
  { monthOffset: 1, day: 24, category: 'Marketing', amount: 4200, remarks: 'Flyers' }
];

/**
 * In-browser mock expenses backend. Entries are persisted to localStorage; all
 * reads/writes return Observables (with a small delay) so a real HTTP API can be
 * dropped in later unchanged. Synchronous aggregation helpers back the P&L module.
 */
@Injectable({ providedIn: 'root' })
export class ExpenseStoreService {
  private entries: ExpenseEntry[] = [];
  private readonly changed$ = new BehaviorSubject<void>(undefined);
  /** Emits whenever expenses change so views can refresh. */
  readonly changes$ = this.changed$.asObservable();

  constructor() {
    this.load();
  }

  private load(): void {
    const raw = this.read<ExpenseEntry[]>(ENTRIES_KEY);
    if (raw) {
      this.entries = raw;
      return;
    }
    this.seed();
  }

  private seed(): void {
    this.entries = [];
    const now = new Date();
    SEED.forEach(s => {
      const d = new Date(now.getFullYear(), now.getMonth() - s.monthOffset, s.day);
      const date = this.toIso(d);
      this.entries.push({
        id: this.id('exp'),
        date,
        category: s.category,
        amount: s.amount,
        remarks: s.remarks,
        billFileName: s.billFileName,
        createdAt: date + this.tieBreaker()
      });
    });
    this.persist();
  }

  private read<T>(key: string): T | null {
    try {
      const raw = localStorage.getItem(key);
      return raw ? (JSON.parse(raw) as T) : null;
    } catch {
      return null;
    }
  }

  private persist(): void {
    try {
      localStorage.setItem(ENTRIES_KEY, JSON.stringify(this.entries));
    } catch {
      /* ignore */
    }
    this.changed$.next();
  }

  private id(prefix: string): string {
    return `${prefix}_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 7)}`;
  }

  private today(): string {
    return new Date().toISOString().slice(0, 10);
  }

  private toIso(d: Date): string {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }

  private round(n: number): number {
    return Math.round(n * 100) / 100;
  }

  // createdAt is used for stable ordering; add sub-second uniqueness.
  private tieBreaker(): string {
    return 'T' + (performance.now() % 1000).toFixed(3);
  }

  private sorted(): ExpenseEntry[] {
    return this.entries
      .slice()
      .sort((a, b) => b.date.localeCompare(a.date) || b.createdAt.localeCompare(a.createdAt));
  }

  // ---- public API (Observable, mimics HTTP) ---------------------------------

  list(): Observable<ExpenseEntry[]> {
    return of(this.sorted()).pipe(delay(LATENCY));
  }

  listSnapshot(): ExpenseEntry[] {
    return this.sorted();
  }

  create(input: ExpenseInput): Observable<ExpenseEntry> {
    const date = input.date || this.today();
    const entry: ExpenseEntry = {
      id: this.id('exp'),
      date,
      category: input.category,
      amount: this.round(Math.max(0, input.amount)),
      remarks: input.remarks?.trim() || undefined,
      billFileName: input.billFileName?.trim() || undefined,
      createdAt: this.today() + this.tieBreaker()
    };
    this.entries.push(entry);
    this.persist();
    return of(entry).pipe(delay(LATENCY));
  }

  update(id: string, input: ExpenseInput): Observable<ExpenseEntry> {
    const entry = this.entries.find(e => e.id === id);
    if (!entry) {
      return throwError(() => new Error('Expense not found'));
    }
    entry.date = input.date || entry.date;
    entry.category = input.category;
    entry.amount = this.round(Math.max(0, input.amount));
    entry.remarks = input.remarks?.trim() || undefined;
    entry.billFileName = input.billFileName?.trim() || undefined;
    this.persist();
    return of(entry).pipe(delay(LATENCY));
  }

  remove(id: string): Observable<void> {
    this.entries = this.entries.filter(e => e.id !== id);
    this.persist();
    return of(undefined).pipe(delay(LATENCY));
  }

  // ---- derived helpers (synchronous, used by reports + P&L) -----------------

  /** Sum of expense amount where date starts with the `yyyy-mm` period. */
  totalForMonth(ym: string): number {
    const total = this.entries
      .filter(e => e.date.startsWith(ym))
      .reduce((sum, e) => sum + e.amount, 0);
    return this.round(total);
  }

  /**
   * Sum of expense amount for a single category within a `yyyy-mm` period.
   * `category` is typed as `string` on purpose to keep callers (e.g. P&L)
   * decoupled from the ExpenseCategory union.
   */
  categoryTotal(ym: string, category: string): number {
    const total = this.entries
      .filter(e => e.date.startsWith(ym) && e.category === category)
      .reduce((sum, e) => sum + e.amount, 0);
    return this.round(total);
  }

  /** Category-wise totals for a `yyyy-mm` period, sorted by total desc. */
  byCategory(ym: string): Array<{ category: ExpenseCategory; total: number }> {
    return EXPENSE_CATEGORIES
      .map(category => ({ category, total: this.categoryTotal(ym, category) }))
      .filter(row => row.total > 0)
      .sort((a, b) => b.total - a.total);
  }

  resetDemoData(): Observable<void> {
    this.seed();
    return of(undefined).pipe(delay(LATENCY));
  }
}
