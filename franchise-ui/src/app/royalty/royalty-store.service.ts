import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, of } from 'rxjs';
import { delay } from 'rxjs/operators';
import { SalesStoreService } from '../sales/sales-store.service';
import { RoyaltyConfig, RoyaltyEntry, RoyaltyStatus } from './royalty.model';

const CONFIG_KEY = 'franchise.royalty.config';
const ENTRIES_KEY = 'franchise.royalty.entries';
const LATENCY = 120;
const DEFAULT_RATE = 5;

/**
 * In-browser mock royalty backend. Royalty is a configurable percentage of
 * monthly net sales (sourced from SalesStoreService). Config + per-month
 * entries are persisted to localStorage; all reads/writes return Observables
 * (with a small delay) so a real HTTP API can be dropped in later unchanged.
 */
@Injectable({ providedIn: 'root' })
export class RoyaltyStoreService {
  private config: RoyaltyConfig = { ratePercent: DEFAULT_RATE };
  private entries: RoyaltyEntry[] = [];
  private readonly changed$ = new BehaviorSubject<void>(undefined);
  /** Emits whenever royalty config or entries change so views can refresh. */
  readonly changes$ = this.changed$.asObservable();

  constructor(private salesStore: SalesStoreService) {
    this.load();
  }

  private load(): void {
    const rawConfig = this.read<RoyaltyConfig>(CONFIG_KEY);
    const rawEntries = this.read<RoyaltyEntry[]>(ENTRIES_KEY);
    if (rawConfig && rawEntries) {
      this.config = rawConfig;
      this.entries = rawEntries;
      return;
    }
    this.seed();
  }

  private seed(): void {
    this.config = { ratePercent: DEFAULT_RATE };
    this.entries = [];

    const current = this.currentMonth();
    const previous = this.previousMonth();

    // Previous month — fully paid.
    const prevBase = this.salesStore.netSalesForMonth(previous);
    const prevDue = this.round(prevBase * this.config.ratePercent / 100);
    this.entries.push({
      id: this.id('roy'),
      month: previous,
      netSalesBase: prevBase,
      ratePercent: this.config.ratePercent,
      dueAmount: prevDue,
      paidAmount: prevDue,
      status: 'Paid',
      generatedDate: this.today(),
      paidDate: this.today(),
      remarks: 'Settled',
      createdAt: this.today() + this.tieBreaker()
    });

    // Current month — pending.
    const curBase = this.salesStore.netSalesForMonth(current);
    const curDue = this.round(curBase * this.config.ratePercent / 100);
    this.entries.push({
      id: this.id('roy'),
      month: current,
      netSalesBase: curBase,
      ratePercent: this.config.ratePercent,
      dueAmount: curDue,
      paidAmount: 0,
      status: 'Pending',
      generatedDate: this.today(),
      createdAt: this.today() + this.tieBreaker()
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
      localStorage.setItem(CONFIG_KEY, JSON.stringify(this.config));
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

  private currentMonth(): string {
    return new Date().toISOString().slice(0, 7);
  }

  private previousMonth(): string {
    const d = new Date();
    d.setDate(1);
    d.setMonth(d.getMonth() - 1);
    return d.toISOString().slice(0, 7);
  }

  private round(n: number): number {
    return Math.round(n * 100) / 100;
  }

  // createdAt is used for stable ordering; add sub-second uniqueness.
  private tieBreaker(): string {
    return 'T' + (performance.now() % 1000).toFixed(3);
  }

  private statusFor(dueAmount: number, paidAmount: number): RoyaltyStatus {
    if (paidAmount >= dueAmount && dueAmount > 0) {
      return 'Paid';
    }
    if (paidAmount >= dueAmount) {
      // Zero due (e.g. no sales) with any payment is treated as settled.
      return paidAmount > 0 ? 'Paid' : 'Pending';
    }
    return paidAmount > 0 ? 'Partial' : 'Pending';
  }

  // ---- public API (Observable, mimics HTTP) ---------------------------------

  getConfig(): RoyaltyConfig {
    return { ...this.config };
  }

  setConfig(cfg: RoyaltyConfig): Observable<RoyaltyConfig> {
    this.config = { ratePercent: this.round(cfg.ratePercent) };
    this.persist();
    return of(this.getConfig()).pipe(delay(LATENCY));
  }

  list(): Observable<RoyaltyEntry[]> {
    return of(this.sortedDesc()).pipe(delay(LATENCY));
  }

  listSnapshot(): RoyaltyEntry[] {
    return this.sortedDesc();
  }

  /**
   * Generate (or recompute, if it already exists) the royalty entry for the
   * given yyyy-mm month using the current net sales and configured rate.
   */
  generateForMonth(ym: string): Observable<RoyaltyEntry> {
    const netSalesBase = this.salesStore.netSalesForMonth(ym);
    const ratePercent = this.config.ratePercent;
    const dueAmount = this.round(netSalesBase * ratePercent / 100);

    const existing = this.entries.find(e => e.month === ym);
    if (existing) {
      existing.netSalesBase = netSalesBase;
      existing.ratePercent = ratePercent;
      existing.dueAmount = dueAmount;
      existing.generatedDate = this.today();
      existing.status = this.statusFor(dueAmount, existing.paidAmount);
      existing.paidDate = existing.status === 'Paid' ? (existing.paidDate ?? this.today()) : undefined;
      this.persist();
      return of(existing).pipe(delay(LATENCY));
    }

    const entry: RoyaltyEntry = {
      id: this.id('roy'),
      month: ym,
      netSalesBase,
      ratePercent,
      dueAmount,
      paidAmount: 0,
      status: 'Pending',
      generatedDate: this.today(),
      createdAt: this.today() + this.tieBreaker()
    };
    this.entries.push(entry);
    this.persist();
    return of(entry).pipe(delay(LATENCY));
  }

  recordPayment(id: string, amount: number): Observable<RoyaltyEntry> {
    const entry = this.entries.find(e => e.id === id);
    if (!entry) {
      return of(undefined as unknown as RoyaltyEntry).pipe(delay(LATENCY));
    }
    const add = Math.max(0, this.round(amount));
    entry.paidAmount = this.round(entry.paidAmount + add);
    entry.status = this.statusFor(entry.dueAmount, entry.paidAmount);
    entry.paidDate = entry.status === 'Paid' ? this.today() : undefined;
    this.persist();
    return of(entry).pipe(delay(LATENCY));
  }

  /** Sum of outstanding (due − paid) across all entries, floored at 0. */
  outstandingTotal(): number {
    const total = this.entries.reduce(
      (sum, e) => sum + Math.max(0, e.dueAmount - e.paidAmount),
      0
    );
    return this.round(total);
  }

  /**
   * Royalty due for a month. If an entry already exists it returns its locked-in
   * dueAmount, otherwise it computes from current net sales and rate. Used by P&L.
   */
  dueForMonth(ym: string): number {
    const existing = this.entries.find(e => e.month === ym);
    if (existing) {
      return existing.dueAmount;
    }
    return this.round(this.salesStore.netSalesForMonth(ym) * this.config.ratePercent / 100);
  }

  resetDemoData(): Observable<void> {
    this.seed();
    return of(undefined).pipe(delay(LATENCY));
  }

  private sortedDesc(): RoyaltyEntry[] {
    return this.entries
      .slice()
      .sort((a, b) => b.month.localeCompare(a.month) || b.createdAt.localeCompare(a.createdAt));
  }
}
