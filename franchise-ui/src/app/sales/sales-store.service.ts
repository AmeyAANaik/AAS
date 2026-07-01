import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, of, throwError } from 'rxjs';
import { delay } from 'rxjs/operators';
import {
  PaymentLine, PaymentMode, PaymentModeInput, SaleEntry, SaleInput
} from './sales.model';

const ENTRIES_KEY = 'franchise.sales.entries.v2';
const MODES_KEY = 'franchise.sales.payment-modes';
const LATENCY = 120;

interface SeedMode { name: string; enabled: boolean; }

/** Default payment modes — the franchise can add/disable more from the master. */
const SEED_MODES: SeedMode[] = [
  { name: 'Cash', enabled: true },
  { name: 'UPI', enabled: true },
  { name: 'Card', enabled: true },
  { name: 'Swiggy', enabled: false },
  { name: 'Zomato', enabled: false }
];

interface SeedSale {
  dayOffset: number; // days before "today" used for seeding
  grossSales: number;
  gstAmount: number;
  discount: number;
  cash: number;
  upi: number;
  card: number;
  remarks?: string;
}

/**
 * Seed entries spread across the current and previous month so the reports
 * view looks populated. The cash/upi/card amounts are mapped onto the seeded
 * payment-mode master.
 */
const SEED: SeedSale[] = [
  { dayOffset: 0, grossSales: 18500, gstAmount: 925, discount: 500, cash: 6000, upi: 9000, card: 3000, remarks: 'Weekend rush' },
  { dayOffset: 2, grossSales: 14200, gstAmount: 710, discount: 200, cash: 5000, upi: 6000, card: 3000 },
  { dayOffset: 5, grossSales: 21000, gstAmount: 1050, discount: 1000, cash: 7000, upi: 9000, card: 4000, remarks: 'Festive offer' },
  { dayOffset: 9, grossSales: 9800, gstAmount: 490, discount: 0, cash: 4000, upi: 4800, card: 1000 },
  { dayOffset: 14, grossSales: 16500, gstAmount: 825, discount: 300, cash: 5500, upi: 7700, card: 3000 },
  { dayOffset: 38, grossSales: 12300, gstAmount: 615, discount: 100, cash: 4200, upi: 5000, card: 3000, remarks: 'Prev month' },
  { dayOffset: 44, grossSales: 19700, gstAmount: 985, discount: 700, cash: 6500, upi: 8500, card: 4000, remarks: 'Prev month' },
  { dayOffset: 52, grossSales: 8700, gstAmount: 435, discount: 0, cash: 3500, upi: 4200, card: 1000, remarks: 'Prev month' }
];

/**
 * In-browser mock sales backend. Daily sale entries and the payment-mode master
 * are persisted to localStorage; all reads/writes return Observables (with a
 * small delay) so a real HTTP API can be dropped in later unchanged.
 */
@Injectable({ providedIn: 'root' })
export class SalesStoreService {
  private entries: SaleEntry[] = [];
  private modes: PaymentMode[] = [];
  private readonly changed$ = new BehaviorSubject<void>(undefined);
  /** Emits whenever sales or payment modes change so views can refresh. */
  readonly changes$ = this.changed$.asObservable();

  constructor() {
    this.load();
  }

  private load(): void {
    const rawModes = this.read<PaymentMode[]>(MODES_KEY);
    const rawEntries = this.read<SaleEntry[]>(ENTRIES_KEY);
    if (rawModes && rawEntries) {
      this.modes = rawModes;
      this.entries = rawEntries;
      return;
    }
    this.seed();
  }

  private seed(): void {
    const created = this.today();
    this.modes = SEED_MODES.map((m, i) => ({
      id: this.id('pm'), name: m.name, enabled: m.enabled, sortOrder: i, createdAt: created
    }));
    const byName = (name: string) => this.modes.find(m => m.name === name)!;

    this.entries = SEED.map(s => {
      const payments: PaymentLine[] = [
        { modeId: byName('Cash').id, modeName: 'Cash', amount: s.cash },
        { modeId: byName('UPI').id, modeName: 'UPI', amount: s.upi },
        { modeId: byName('Card').id, modeName: 'Card', amount: s.card }
      ].filter(p => p.amount > 0);
      return {
        id: this.id('sale'),
        date: this.dateMinusDays(s.dayOffset),
        grossSales: s.grossSales,
        gstAmount: s.gstAmount,
        discount: s.discount,
        netSales: this.round(s.grossSales - s.discount),
        payments,
        remarks: s.remarks,
        createdAt: created + this.tieBreaker()
      };
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
      localStorage.setItem(MODES_KEY, JSON.stringify(this.modes));
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

  private dateMinusDays(days: number): string {
    const d = new Date();
    d.setDate(d.getDate() - days);
    return d.toISOString().slice(0, 10);
  }

  private round(n: number): number {
    return Math.round(n * 100) / 100;
  }

  // createdAt is used for stable ordering; add sub-second uniqueness.
  private tieBreaker(): string {
    return 'T' + (performance.now() % 1000).toFixed(3);
  }

  private cleanPayments(input: SaleInput): PaymentLine[] {
    return (input.payments ?? [])
      .filter(p => p.modeId && (Number(p.amount) || 0) > 0)
      .map(p => ({
        modeId: p.modeId,
        modeName: p.modeName,
        amount: this.round(Number(p.amount) || 0),
        remark: p.remark?.trim() || undefined,
        attachmentName: p.attachmentName?.trim() || undefined
      }));
  }

  // ---- payment-mode master --------------------------------------------------

  listPaymentModes(): Observable<PaymentMode[]> {
    return of(this.sortedModes()).pipe(delay(LATENCY));
  }

  listPaymentModesSnapshot(): PaymentMode[] {
    return this.sortedModes();
  }

  /** Enabled modes only, in display order — drives the entry form. */
  activePaymentModesSnapshot(): PaymentMode[] {
    return this.sortedModes().filter(m => m.enabled);
  }

  createPaymentMode(input: PaymentModeInput): Observable<PaymentMode> {
    const name = input.name.trim();
    if (!name) {
      return throwError(() => new Error('Payment mode name is required.'));
    }
    if (this.modes.some(m => m.name.toLowerCase() === name.toLowerCase())) {
      return throwError(() => new Error(`Payment mode "${name}" already exists.`));
    }
    const mode: PaymentMode = {
      id: this.id('pm'), name, enabled: input.enabled,
      sortOrder: this.modes.length, createdAt: this.today()
    };
    this.modes.push(mode);
    this.persist();
    return of(mode).pipe(delay(LATENCY));
  }

  updatePaymentMode(id: string, input: PaymentModeInput): Observable<PaymentMode> {
    const mode = this.modes.find(m => m.id === id);
    if (!mode) {
      return throwError(() => new Error('Payment mode not found.'));
    }
    const name = input.name.trim();
    if (name && this.modes.some(m => m.id !== id && m.name.toLowerCase() === name.toLowerCase())) {
      return throwError(() => new Error(`Payment mode "${name}" already exists.`));
    }
    if (name) {
      mode.name = name;
    }
    mode.enabled = input.enabled;
    this.persist();
    return of(mode).pipe(delay(LATENCY));
  }

  setPaymentModeEnabled(id: string, enabled: boolean): Observable<PaymentMode> {
    const mode = this.modes.find(m => m.id === id);
    if (!mode) {
      return throwError(() => new Error('Payment mode not found.'));
    }
    mode.enabled = enabled;
    this.persist();
    return of(mode).pipe(delay(LATENCY));
  }

  removePaymentMode(id: string): Observable<void> {
    this.modes = this.modes.filter(m => m.id !== id);
    this.persist();
    return of(undefined).pipe(delay(LATENCY));
  }

  /** Mode names that appear across master + history — drives report columns. */
  knownPaymentModeNames(): string[] {
    const names = new Set<string>();
    this.sortedModes().forEach(m => names.add(m.name));
    this.entries.forEach(e => e.payments.forEach(p => names.add(p.modeName)));
    return [...names];
  }

  private sortedModes(): PaymentMode[] {
    return this.modes.slice().sort((a, b) => a.sortOrder - b.sortOrder || a.name.localeCompare(b.name));
  }

  // ---- sale entries (Observable, mimics HTTP) -------------------------------

  list(): Observable<SaleEntry[]> {
    return of(this.sortedDesc()).pipe(delay(LATENCY));
  }

  listSnapshot(): SaleEntry[] {
    return this.sortedDesc();
  }

  create(input: SaleInput): Observable<SaleEntry> {
    const created = this.today();
    const entry: SaleEntry = {
      id: this.id('sale'),
      date: input.date || created,
      grossSales: this.round(input.grossSales),
      gstAmount: this.round(input.gstAmount),
      discount: this.round(input.discount),
      netSales: this.round(input.grossSales - input.discount),
      payments: this.cleanPayments(input),
      remarks: input.remarks?.trim() || undefined,
      createdAt: created + this.tieBreaker()
    };
    this.entries.push(entry);
    this.persist();
    return of(entry).pipe(delay(LATENCY));
  }

  update(id: string, input: SaleInput): Observable<SaleEntry> {
    const entry = this.entries.find(e => e.id === id);
    if (!entry) {
      return throwError(() => new Error('Sale entry not found.'));
    }
    entry.date = input.date || entry.date;
    entry.grossSales = this.round(input.grossSales);
    entry.gstAmount = this.round(input.gstAmount);
    entry.discount = this.round(input.discount);
    entry.netSales = this.round(input.grossSales - input.discount);
    entry.payments = this.cleanPayments(input);
    entry.remarks = input.remarks?.trim() || undefined;
    this.persist();
    return of(entry).pipe(delay(LATENCY));
  }

  remove(id: string): Observable<void> {
    this.entries = this.entries.filter(e => e.id !== id);
    this.persist();
    return of(undefined).pipe(delay(LATENCY));
  }

  /** Sum of net sales where the date falls in the `yyyy-mm` period. */
  netSalesForMonth(ym: string): number {
    const total = this.entries
      .filter(e => e.date.startsWith(ym))
      .reduce((sum, e) => sum + e.netSales, 0);
    return this.round(total);
  }

  /** Sum of gross sales where the date falls in the `yyyy-mm` period. */
  grossSalesForMonth(ym: string): number {
    const total = this.entries
      .filter(e => e.date.startsWith(ym))
      .reduce((sum, e) => sum + e.grossSales, 0);
    return this.round(total);
  }

  resetDemoData(): Observable<void> {
    this.seed();
    return of(undefined).pipe(delay(LATENCY));
  }

  private sortedDesc(): SaleEntry[] {
    return this.entries
      .slice()
      .sort((a, b) => b.date.localeCompare(a.date) || b.createdAt.localeCompare(a.createdAt));
  }
}
