import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatTabsModule } from '@angular/material/tabs';
import { MatTableModule } from '@angular/material/table';
import { MatIconModule } from '@angular/material/icon';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatMenuModule } from '@angular/material/menu';
import { MatSelectModule } from '@angular/material/select';
import { PageHeaderComponent } from '../../shared/page-header/page-header.component';
import { ReportColumn, ReportDownloadService, ReportExportFormat } from '../../shared/report-download.service';
import { StatusPillComponent } from '../../shared/status-pill/status-pill.component';
import { InventoryStoreService } from '../inventory-store.service';
import { StockTxn } from '../inventory.model';

interface CurrentRow {
  name: string; category: string; unit: string; current: number; min: number; rate: number; value: number; low: boolean;
}
interface DailyRow { date: string; opening: number; purchased: number; consumed: number; closing: number; }
interface ConsumptionRow { date: string; product: string; qty: number; avgRate: number; value: number; reason: string; }
interface ProductConsumptionRow { rank: number; product: string; unit: string; totalConsumed: number; totalValue: number; }
interface CategorySummaryRow { category: string; items: number; value: number; lowCount: number; }
interface UnconsumedRow { name: string; category: string; unit: string; current: number; value: number; lastPurchase: string; low: boolean; }

@Component({
  selector: 'app-stock-reports',
  standalone: true,
  imports: [
    CommonModule, FormsModule, MatButtonModule, MatTabsModule, MatTableModule, MatIconModule,
    MatFormFieldModule, MatMenuModule, MatSelectModule, PageHeaderComponent, StatusPillComponent
  ],
  templateUrl: './stock-reports.component.html',
  styleUrl: './stock-reports.component.css'
})
export class StockReportsComponent implements OnInit {
  current: CurrentRow[] = [];
  daily: DailyRow[] = [];
  monthly: DailyRow[] = [];
  consumption: ConsumptionRow[] = [];
  productConsumption: ProductConsumptionRow[] = [];
  categorySummary: CategorySummaryRow[] = [];
  unconsumed: UnconsumedRow[] = [];

  categories: string[] = [];
  totalStockValue = 0;
  lowStockCount = 0;
  totalConsumptionValue = 0;

  // Current-stock filters
  filterCategory = '';
  filterStatus: 'all' | 'in' | 'low' = 'all';
  activeTabIndex = 0;

  readonly currentCols = ['name', 'category', 'unit', 'current', 'min', 'rate', 'value', 'status'];
  readonly dailyCols = ['date', 'opening', 'purchased', 'consumed', 'closing'];
  readonly consumptionCols = ['date', 'product', 'qty', 'avgRate', 'value', 'reason'];
  readonly productConsumptionCols = ['rank', 'product', 'totalConsumed', 'totalValue'];
  readonly categorySummaryCols = ['category', 'items', 'value', 'lowCount'];
  readonly unconsumedCols = ['name', 'category', 'unit', 'current', 'value', 'lastPurchase', 'status'];

  constructor(private store: InventoryStoreService, private downloads: ReportDownloadService) {}

  ngOnInit(): void {
    this.build();
  }

  private build(): void {
    const products = this.store.listProductsSnapshot();
    const txns = this.store.transactionsSnapshot();
    this.categories = this.store.listCategories();

    // Current stock + closing value
    this.current = products.map(p => ({
      name: p.name, category: p.category ?? '—', unit: p.unit, current: p.currentStock,
      min: p.minStockLevel, rate: p.lastRate, value: p.stockValue, low: p.lowStock
    }));
    this.totalStockValue = this.round(this.current.reduce((s, r) => s + r.value, 0));
    this.lowStockCount = this.current.filter(r => r.low).length;

    // Category-wise stock summary
    const catMap = new Map<string, CategorySummaryRow>();
    this.current.forEach(r => {
      const row = catMap.get(r.category) ?? { category: r.category, items: 0, value: 0, lowCount: 0 };
      row.items += 1;
      row.value = this.round(row.value + r.value);
      row.lowCount += r.low ? 1 : 0;
      catMap.set(r.category, row);
    });
    this.categorySummary = [...catMap.values()].sort((a, b) => b.value - a.value);

    // Daily / monthly movement
    this.daily = this.aggregateByPeriod(txns, d => d);
    this.monthly = this.aggregateByPeriod(txns, d => d.slice(0, 7));

    // Running-average cost per consumption line
    const valuation = this.computeRunningAverage(txns);

    // Consumption report (line items) valued at running-average rate
    this.consumption = txns
      .filter(t => t.type === 'CONSUMPTION')
      .sort((a, b) => b.date.localeCompare(a.date) || b.createdAt.localeCompare(a.createdAt))
      .map(t => {
        const val = valuation.get(t.id) ?? { avg: 0, value: 0 };
        return { date: t.date, product: this.store.productName(t.productId), qty: -t.qty, avgRate: val.avg, value: val.value, reason: t.ref ?? '' };
      });
    this.totalConsumptionValue = this.round(this.consumption.reduce((s, r) => s + r.value, 0));

    // Product-wise consumption — ranked by most used
    const byProduct = new Map<string, { product: string; unit: string; totalConsumed: number; totalValue: number }>();
    txns.filter(t => t.type === 'CONSUMPTION').forEach(t => {
      const view = products.find(p => p.id === t.productId);
      const val = valuation.get(t.id) ?? { avg: 0, value: 0 };
      const existing = byProduct.get(t.productId) ?? { product: this.store.productName(t.productId), unit: view?.unit ?? '', totalConsumed: 0, totalValue: 0 };
      existing.totalConsumed += -t.qty;
      existing.totalValue = this.round(existing.totalValue + val.value);
      byProduct.set(t.productId, existing);
    });
    this.productConsumption = [...byProduct.values()]
      .sort((a, b) => b.totalConsumed - a.totalConsumed)
      .map((r, i) => ({ rank: i + 1, ...r }));

    // Unconsumed items — products that have never been consumed
    const consumedIds = new Set(txns.filter(t => t.type === 'CONSUMPTION').map(t => t.productId));
    const lastPurchaseByProduct = new Map<string, string>();
    txns.filter(t => t.type === 'PURCHASE' || t.type === 'OPENING').forEach(t => {
      const prev = lastPurchaseByProduct.get(t.productId);
      if (!prev || t.date > prev) {
        lastPurchaseByProduct.set(t.productId, t.date);
      }
    });
    this.unconsumed = products
      .filter(p => !consumedIds.has(p.id))
      .map(p => ({
        name: p.name, category: p.category ?? '—', unit: p.unit, current: p.currentStock,
        value: p.stockValue, lastPurchase: lastPurchaseByProduct.get(p.id) ?? '—', low: p.lowStock
      }))
      .sort((a, b) => b.value - a.value);
  }

  /** Moving-average cost: value each consumption at the average rate at that time. */
  private computeRunningAverage(txns: StockTxn[]): Map<string, { avg: number; value: number }> {
    const ordered = txns.slice().sort((a, b) => a.date.localeCompare(b.date) || a.createdAt.localeCompare(b.createdAt));
    const qtyOnHand = new Map<string, number>();
    const valueOnHand = new Map<string, number>();
    const out = new Map<string, { avg: number; value: number }>();
    ordered.forEach(t => {
      const q = qtyOnHand.get(t.productId) ?? 0;
      const v = valueOnHand.get(t.productId) ?? 0;
      if (t.qty >= 0) {
        qtyOnHand.set(t.productId, q + t.qty);
        valueOnHand.set(t.productId, v + t.qty * (t.rate ?? 0));
      } else {
        const consume = -t.qty;
        const avg = q > 0 ? v / q : (t.rate ?? 0);
        const value = this.round(consume * avg);
        out.set(t.id, { avg: this.round(avg), value });
        qtyOnHand.set(t.productId, Math.max(0, q - consume));
        valueOnHand.set(t.productId, Math.max(0, v - consume * avg));
      }
    });
    return out;
  }

  // ---- Current-stock filtering ----------------------------------------------

  get filteredCurrent(): CurrentRow[] {
    return this.current
      .filter(r => (this.filterCategory ? r.category === this.filterCategory : true))
      .filter(r => this.filterStatus === 'all' ? true : this.filterStatus === 'low' ? r.low : !r.low);
  }

  get activeReportHasRows(): boolean {
    return this.activeReport().rows.length > 0;
  }

  downloadActiveReport(format: ReportExportFormat): void {
    const report = this.activeReport();
    this.downloads.download({
      title: report.title,
      subtitle: 'Inventory stock ledger report',
      period: 'Current ledger',
      fileName: report.fileName,
      columns: report.columns,
      rows: report.rows,
      summary: [
        { label: 'Closing stock value', value: this.money(this.totalStockValue) },
        { label: 'Products tracked', value: this.current.length },
        { label: 'Low-stock items', value: this.lowStockCount },
        { label: 'Consumption value', value: this.money(this.totalConsumptionValue) }
      ]
    }, format);
  }

  private activeReport(): { title: string; fileName: string; columns: ReportColumn[]; rows: Array<Record<string, unknown>> } {
    switch (this.activeTabIndex) {
      case 1:
        return {
          title: 'Stock Category Report',
          fileName: 'stock-category-report',
          columns: [
            { key: 'category', label: 'Category' },
            { key: 'items', label: 'Items', align: 'right' },
            { key: 'value', label: 'Stock value', align: 'right' },
            { key: 'lowCount', label: 'Low-stock', align: 'right' }
          ],
          rows: this.categorySummary.map(row => ({ ...row, value: this.money(row.value) }))
        };
      case 2:
        return {
          title: 'Daily Stock Report',
          fileName: 'daily-stock-report',
          columns: this.movementColumns('Date'),
          rows: this.daily.map(row => ({ ...row }))
        };
      case 3:
        return {
          title: 'Monthly Stock Report',
          fileName: 'monthly-stock-report',
          columns: this.movementColumns('Month'),
          rows: this.monthly.map(row => ({ ...row }))
        };
      case 4:
        return {
          title: 'Consumption Report',
          fileName: 'stock-consumption-report',
          columns: [
            { key: 'date', label: 'Date' },
            { key: 'product', label: 'Product' },
            { key: 'qty', label: 'Consumed', align: 'right' },
            { key: 'avgRate', label: 'Avg rate', align: 'right' },
            { key: 'value', label: 'Value', align: 'right' },
            { key: 'reason', label: 'Reason' }
          ],
          rows: this.consumption.map(row => ({ ...row, avgRate: this.money(row.avgRate), value: this.money(row.value) }))
        };
      case 5:
        return {
          title: 'Most Used Products Report',
          fileName: 'most-used-products-report',
          columns: [
            { key: 'rank', label: '#' },
            { key: 'product', label: 'Product' },
            { key: 'totalConsumed', label: 'Total consumed', align: 'right' },
            { key: 'totalValue', label: 'Total value', align: 'right' }
          ],
          rows: this.productConsumption.map(row => ({ ...row, totalConsumed: `${row.totalConsumed} ${row.unit}`, totalValue: this.money(row.totalValue) }))
        };
      case 6:
        return {
          title: 'Unconsumed Stock Report',
          fileName: 'unconsumed-stock-report',
          columns: [
            { key: 'name', label: 'Product' },
            { key: 'category', label: 'Category' },
            { key: 'unit', label: 'Unit' },
            { key: 'current', label: 'Current', align: 'right' },
            { key: 'value', label: 'Value', align: 'right' },
            { key: 'lastPurchase', label: 'Last purchase' },
            { key: 'status', label: 'Status' }
          ],
          rows: this.unconsumed.map(row => ({ ...row, value: this.money(row.value), status: row.low ? 'Low Stock' : 'In Stock' }))
        };
      default:
        return {
          title: 'Current Stock Report',
          fileName: 'current-stock-report',
          columns: [
            { key: 'name', label: 'Product' },
            { key: 'category', label: 'Category' },
            { key: 'unit', label: 'Unit' },
            { key: 'current', label: 'Current', align: 'right' },
            { key: 'min', label: 'Min', align: 'right' },
            { key: 'rate', label: 'Rate', align: 'right' },
            { key: 'value', label: 'Value', align: 'right' },
            { key: 'status', label: 'Status' }
          ],
          rows: this.filteredCurrent.map(row => ({
            ...row,
            rate: this.money(row.rate),
            value: this.money(row.value),
            status: row.low ? 'Low Stock' : 'In Stock'
          }))
        };
    }
  }

  private movementColumns(periodLabel: string): ReportColumn[] {
    return [
      { key: 'date', label: periodLabel },
      { key: 'opening', label: 'Opening', align: 'right' },
      { key: 'purchased', label: 'Purchased', align: 'right' },
      { key: 'consumed', label: 'Consumed', align: 'right' },
      { key: 'closing', label: 'Closing', align: 'right' }
    ];
  }

  private aggregateByPeriod(txns: StockTxn[], keyOf: (date: string) => string): DailyRow[] {
    const periods = [...new Set(txns.map(t => keyOf(t.date)))].sort();
    const rows: DailyRow[] = [];
    let runningClose = 0;
    const byPeriod = new Map<string, { purchased: number; consumed: number }>();
    txns.forEach(t => {
      const k = keyOf(t.date);
      const cur = byPeriod.get(k) ?? { purchased: 0, consumed: 0 };
      if (t.qty >= 0) {
        cur.purchased += t.qty;
      } else {
        cur.consumed += -t.qty;
      }
      byPeriod.set(k, cur);
    });
    periods.forEach(period => {
      const movement = byPeriod.get(period) ?? { purchased: 0, consumed: 0 };
      const opening = runningClose;
      const closing = opening + movement.purchased - movement.consumed;
      rows.push({ date: period, opening, purchased: movement.purchased, consumed: movement.consumed, closing });
      runningClose = closing;
    });
    return rows.reverse();
  }

  private round(n: number): number {
    return Math.round(n * 100) / 100;
  }

  private money(value: number): string {
    return `Rs. ${value.toLocaleString('en-IN', { maximumFractionDigits: 2 })}`;
  }
}
