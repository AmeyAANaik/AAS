import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, of, throwError } from 'rxjs';
import { delay } from 'rxjs/operators';
import {
  ConsumptionInput, ExtractedInvoice, Product, ProductStockView, PurchaseInput, StockTxn, Unit
} from './inventory.model';
import { ProductCategoryStoreService } from '../master-data/product-category-store.service';

const PRODUCTS_KEY = 'franchise.inventory.products';
const TXNS_KEY = 'franchise.inventory.txns';
const LATENCY = 120;

interface SeedProduct {
  name: string; unit: Unit; minStockLevel: number; openingStock: number; category: string; openingRate: number;
}

const SEED: SeedProduct[] = [
  { name: 'Oil', unit: 'LTR', minStockLevel: 20, openingStock: 100, category: 'Raw Material', openingRate: 140 },
  { name: 'Cheese', unit: 'KG', minStockLevel: 5, openingStock: 12, category: 'Dairy', openingRate: 420 },
  { name: 'Gas', unit: 'Bottle', minStockLevel: 2, openingStock: 4, category: 'Utility', openingRate: 1100 },
  { name: 'Flour', unit: 'KG', minStockLevel: 25, openingStock: 80, category: 'Raw Material', openingRate: 45 },
  { name: 'Tomato', unit: 'KG', minStockLevel: 10, openingStock: 30, category: 'Vegetable', openingRate: 38 },
  { name: 'Butter', unit: 'KG', minStockLevel: 5, openingStock: 8, category: 'Dairy', openingRate: 520 }
];

/**
 * In-browser mock inventory backend. Products + an append-only stock-txn ledger
 * are persisted to localStorage; all reads/writes return Observables (with a
 * small delay) so a real HTTP API can be dropped in later unchanged.
 */
@Injectable({ providedIn: 'root' })
export class InventoryStoreService {
  private products: Product[] = [];
  private txns: StockTxn[] = [];
  private readonly changed$ = new BehaviorSubject<void>(undefined);
  /** Emits whenever stock changes so views can refresh. */
  readonly changes$ = this.changed$.asObservable();

  constructor(private categoryStore: ProductCategoryStoreService) {
    this.load();
  }

  private load(): void {
    const rawProducts = this.read<Product[]>(PRODUCTS_KEY);
    const rawTxns = this.read<StockTxn[]>(TXNS_KEY);
    if (rawProducts && rawTxns) {
      this.products = rawProducts;
      this.txns = rawTxns;
      return;
    }
    this.seed();
  }

  private seed(): void {
    this.products = [];
    this.txns = [];
    const today = this.today();
    SEED.forEach((s, i) => {
      const id = this.id('prod');
      this.products.push({
        id, name: s.name, unit: s.unit, minStockLevel: s.minStockLevel,
        openingStock: s.openingStock, category: s.category, createdAt: today
      });
      this.txns.push({
        id: this.id('txn'), productId: id, type: 'OPENING', qty: s.openingStock,
        date: today, rate: s.openingRate, amount: s.openingStock * s.openingRate,
        ref: 'Opening stock', createdAt: today
      });
      void i;
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
      localStorage.setItem(PRODUCTS_KEY, JSON.stringify(this.products));
      localStorage.setItem(TXNS_KEY, JSON.stringify(this.txns));
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

  // ---- derived helpers (synchronous, used internally + by reports) ----------

  currentStock(productId: string): number {
    return this.txns.filter(t => t.productId === productId).reduce((sum, t) => sum + t.qty, 0);
  }

  private lastRate(productId: string): number {
    const purchases = this.txns
      .filter(t => t.productId === productId && (t.type === 'PURCHASE' || t.type === 'OPENING') && t.rate)
      .sort((a, b) => a.createdAt.localeCompare(b.createdAt));
    return purchases.length ? purchases[purchases.length - 1].rate ?? 0 : 0;
  }

  private toView(p: Product): ProductStockView {
    const currentStock = this.currentStock(p.id);
    const lastRate = this.lastRate(p.id);
    return {
      ...p,
      currentStock,
      lastRate,
      stockValue: Math.round(currentStock * lastRate * 100) / 100,
      lowStock: currentStock < p.minStockLevel
    };
  }

  // ---- public API (Observable, mimics HTTP) ---------------------------------

  listProducts(): Observable<ProductStockView[]> {
    const views = this.products
      .slice()
      .sort((a, b) => a.name.localeCompare(b.name))
      .map(p => this.toView(p));
    return of(views).pipe(delay(LATENCY));
  }

  listProductsSnapshot(): ProductStockView[] {
    return this.products.map(p => this.toView(p));
  }

  getProduct(id: string): Observable<ProductStockView | undefined> {
    const product = this.products.find(p => p.id === id);
    return of(product ? this.toView(product) : undefined).pipe(delay(LATENCY));
  }

  createProduct(input: {
    name: string; unit: Unit; minStockLevel: number; openingStock: number; openingRate: number; category?: string;
  }): Observable<ProductStockView> {
    const id = this.id('prod');
    const today = this.today();
    const product: Product = {
      id, name: input.name.trim(), unit: input.unit, minStockLevel: input.minStockLevel,
      openingStock: input.openingStock, category: input.category?.trim() || undefined, createdAt: today
    };
    this.products.push(product);
    if (input.openingStock > 0) {
      this.txns.push({
        id: this.id('txn'), productId: id, type: 'OPENING', qty: input.openingStock,
        date: today, rate: input.openingRate, amount: input.openingStock * input.openingRate,
        ref: 'Opening stock', createdAt: today
      });
    }
    this.persist();
    return of(this.toView(product)).pipe(delay(LATENCY));
  }

  /** Opening stock is immutable after creation — only metadata is editable. */
  updateProduct(id: string, patch: { name: string; unit: Unit; minStockLevel: number; category?: string }): Observable<ProductStockView> {
    const product = this.products.find(p => p.id === id);
    if (!product) {
      return throwError(() => new Error('Product not found'));
    }
    product.name = patch.name.trim();
    product.unit = patch.unit;
    product.minStockLevel = patch.minStockLevel;
    product.category = patch.category?.trim() || undefined;
    this.persist();
    return of(this.toView(product)).pipe(delay(LATENCY));
  }

  deleteProduct(id: string): Observable<void> {
    this.products = this.products.filter(p => p.id !== id);
    this.txns = this.txns.filter(t => t.productId !== id);
    this.persist();
    return of(undefined).pipe(delay(LATENCY));
  }

  recordPurchase(input: PurchaseInput): Observable<void> {
    const created = this.today();
    const ref = `${input.vendor} · ${input.billNo}`.trim();
    input.lines
      .filter(l => l.productId && l.qty > 0)
      .forEach(line => {
        this.txns.push({
          id: this.id('txn'), productId: line.productId, type: 'PURCHASE', qty: line.qty,
          date: input.billDate || created, rate: line.rate, amount: line.qty * (line.rate || 0),
          gstPercent: line.gstPercent ?? 0,
          ref, createdAt: created + this.tieBreaker()
        });
      });
    this.persist();
    return of(undefined).pipe(delay(LATENCY));
  }

  /** Edit a recorded consumption line; re-validates against available stock. */
  updateConsumption(txnId: string, qty: number, reason: string, date?: string): Observable<void> {
    const txn = this.txns.find(t => t.id === txnId && t.type === 'CONSUMPTION');
    if (!txn) {
      return throwError(() => new Error('Consumption entry not found.'));
    }
    // Stock available if this entry didn't exist (txn.qty is negative → add it back).
    const stockWithout = this.currentStock(txn.productId) - txn.qty;
    const next = Math.abs(qty);
    if (next <= 0) {
      return throwError(() => new Error('Quantity must be greater than zero.'));
    }
    if (next > stockWithout) {
      const name = this.productName(txn.productId);
      return throwError(() => new Error(`Cannot consume ${next} of ${name} — only ${stockWithout} available.`));
    }
    txn.qty = -next;
    txn.ref = reason?.trim() || 'Daily consumption';
    if (date) {
      txn.date = date;
    }
    this.persist();
    return of(undefined).pipe(delay(LATENCY));
  }

  /** Remove a recorded consumption line (returns its quantity to stock). */
  deleteConsumption(txnId: string): Observable<void> {
    this.txns = this.txns.filter(t => !(t.id === txnId && t.type === 'CONSUMPTION'));
    this.persist();
    return of(undefined).pipe(delay(LATENCY));
  }

  /**
   * Mock OCR extraction. Ignores file contents and returns a believable
   * vendor invoice, resolving product names against the current catalog.
   * Unmatched lines come back with productId='' so the UI prompts a mapping.
   */
  extractInvoice(file: File): Observable<ExtractedInvoice> {
    const catalog = this.listProductsSnapshot();
    const byName = (name: string) =>
      catalog.find(p => p.name.toLowerCase() === name.toLowerCase());

    // A single vendor's invoice — all lines share the vendor's category.
    const sample: Array<{ name: string; qty: number; rate: number; gstPercent: number }> = [
      { name: 'Oil', qty: 50, rate: 142, gstPercent: 5 },
      { name: 'Flour', qty: 80, rate: 46, gstPercent: 0 },
      { name: 'Rice', qty: 25, rate: 58, gstPercent: 5 } // intentionally unmatched → user maps it
    ];

    const lines = sample.map(s => {
      const match = byName(s.name);
      return {
        productId: match?.id ?? '',
        productName: s.name,
        qty: s.qty,
        rate: s.rate,
        gstPercent: s.gstPercent,
        matched: !!match
      };
    });

    const billDate = this.today();
    const stamp = Date.now().toString().slice(-5);
    const extracted: ExtractedInvoice = {
      vendor: 'Corner Kirana',
      category: 'Raw Material',
      billNo: `INV-${stamp}`,
      billDate,
      lines
    };
    void file;
    return of(extracted).pipe(delay(360));
  }

  recordConsumption(input: ConsumptionInput): Observable<void> {
    // Validate nothing goes negative.
    for (const line of input.lines.filter(l => l.productId && l.qty > 0)) {
      if (line.qty > this.currentStock(line.productId)) {
        const name = this.products.find(p => p.id === line.productId)?.name ?? 'item';
        return throwError(() => new Error(`Cannot consume ${line.qty} of ${name} — only ${this.currentStock(line.productId)} in stock.`));
      }
    }
    const created = this.today();
    input.lines
      .filter(l => l.productId && l.qty > 0)
      .forEach(line => {
        this.txns.push({
          id: this.id('txn'), productId: line.productId, type: 'CONSUMPTION', qty: -Math.abs(line.qty),
          date: input.date || created, ref: line.reason?.trim() || 'Daily consumption',
          createdAt: created + this.tieBreaker()
        });
      });
    this.persist();
    return of(undefined).pipe(delay(LATENCY));
  }

  listTransactions(): Observable<StockTxn[]> {
    const sorted = this.txns.slice().sort((a, b) => b.date.localeCompare(a.date) || b.createdAt.localeCompare(a.createdAt));
    return of(sorted).pipe(delay(LATENCY));
  }

  transactionsSnapshot(): StockTxn[] {
    return this.txns.slice();
  }

  productName(id: string): string {
    return this.products.find(p => p.id === id)?.name ?? id;
  }

  productCategory(id: string): string {
    return this.products.find(p => p.id === id)?.category ?? '';
  }

  /** Distinct, sorted product categories — drives the category dropdowns. */
  listCategories(): string[] {
    const set = new Set<string>();
    this.categoryStore.activeNamesSnapshot().forEach(category => {
      const c = category.trim();
      if (c) {
        set.add(c);
      }
    });
    this.products.forEach(p => {
      const c = (p.category ?? '').trim();
      if (c) {
        set.add(c);
      }
    });
    return [...set].sort((a, b) => a.localeCompare(b));
  }

  /**
   * Raw-material cost (COGS) consumed in a month, for the P&L module.
   * Consumption txns carry no rate, so each consumed quantity is valued at the
   * product's latest known rate. `ym` is a `yyyy-mm` period string.
   */
  consumptionValueForMonth(ym: string): number {
    const total = this.txns
      .filter(t => t.type === 'CONSUMPTION' && t.date.startsWith(ym))
      .reduce((sum, t) => sum + Math.abs(t.qty) * this.lastRate(t.productId), 0);
    return Math.round(total * 100) / 100;
  }

  resetDemoData(): Observable<void> {
    this.seed();
    return of(undefined).pipe(delay(LATENCY));
  }

  // createdAt is used for stable ordering; add sub-second uniqueness.
  private tieBreaker(): string {
    return 'T' + (performance.now() % 1000).toFixed(3);
  }
}
