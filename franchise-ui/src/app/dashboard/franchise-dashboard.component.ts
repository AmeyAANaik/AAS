import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit, inject } from '@angular/core';
import { RouterModule } from '@angular/router';
import { merge, Subscription } from 'rxjs';
import { MockAuthService } from '../auth/mock-auth.service';
import { FeatureKey } from '../core/rbac';
import { ExpenseStoreService } from '../expenses/expense-store.service';
import { InventoryStoreService } from '../inventory/inventory-store.service';
import { PnlService } from '../pnl/pnl.service';
import { RoyaltyStoreService } from '../royalty/royalty-store.service';
import { SalesStoreService } from '../sales/sales-store.service';
import { PageHeaderComponent } from '../shared/page-header/page-header.component';
import { VendorStoreService } from '../vendors/vendor-store.service';

interface DashboardKpi {
  label: string;
  value: string;
  note: string;
  tone?: 'good' | 'warn' | 'bad';
}

interface DashboardCard {
  title: string;
  symbol: string;
  route: string;
  feature: FeatureKey;
  description: string;
  meta: string;
  tone: 'blue' | 'green' | 'amber' | 'rose' | 'violet' | 'slate';
}

@Component({
  selector: 'app-franchise-dashboard',
  standalone: true,
  imports: [CommonModule, RouterModule, PageHeaderComponent],
  template: `
    <div class="berry-page">
      <app-page-header
        kicker="Home"
        title="Franchise Dashboard"
        subtitle="Choose a module, then continue into the daily franchise workflow.">
      </app-page-header>

      <section class="berry-panel module-panel">
        <div class="berry-panel-header">
          <div>
            <div class="berry-panel-title">Modules</div>
            <div class="berry-panel-subtitle">Start with Master Data, Inventory, Operations or Administration.</div>
          </div>
        </div>
        <div class="workspace-grid">
          <a
            *ngFor="let card of visibleCards"
            class="workspace-card"
            [class]="'workspace-card ' + card.tone"
            [routerLink]="card.route">
            <span class="workspace-symbol material-icons" aria-hidden="true">{{ card.symbol }}</span>
            <span class="workspace-copy">
              <strong>{{ card.title }}</strong>
              <small>{{ card.description }}</small>
            </span>
            <span class="workspace-meta">{{ card.meta }}</span>
            <span class="workspace-arrow material-icons" aria-hidden="true">arrow_forward</span>
          </a>
        </div>
      </section>

      <div class="dashboard-grid">
        <div class="berry-kpi-card dashboard-kpi" *ngFor="let kpi of kpis" [class.good]="kpi.tone === 'good'" [class.warn]="kpi.tone === 'warn'" [class.bad]="kpi.tone === 'bad'">
          <div class="summary-label">{{ kpi.label }}</div>
          <div class="summary-value">{{ kpi.value }}</div>
          <div class="kpi-note">{{ kpi.note }}</div>
        </div>
      </div>

      <div class="dashboard-split">
        <section class="berry-panel">
          <div class="berry-panel-header">
            <div>
              <div class="berry-panel-title">Operations Snapshot</div>
              <div class="berry-panel-subtitle">Computed from the same module stores used by the entry screens.</div>
            </div>
          </div>
          <div class="metric-list">
            <div class="metric-row" *ngFor="let row of operations">
              <span>{{ row.label }}</span>
              <strong>{{ row.value }}</strong>
            </div>
          </div>
        </section>

        <section class="berry-panel">
          <div class="berry-panel-header">
            <div>
              <div class="berry-panel-title">Attention Needed</div>
              <div class="berry-panel-subtitle">A short business-owner queue for the current month.</div>
            </div>
          </div>
          <div class="attention-list">
            <div class="attention-row" *ngFor="let item of attention">
              <span class="dot" [class.warn]="item.tone === 'warn'" [class.bad]="item.tone === 'bad'"></span>
              <span>{{ item.label }}</span>
              <strong>{{ item.value }}</strong>
            </div>
          </div>
        </section>
      </div>
    </div>
  `,
  styles: [`
    .dashboard-grid {
      display: grid;
      grid-template-columns: repeat(4, minmax(0, 1fr));
      gap: 20px;
    }

    .dashboard-kpi {
      display: grid;
      gap: 8px;
      min-height: 122px;
    }

    .dashboard-kpi.good .summary-value { color: var(--color-success); }
    .dashboard-kpi.warn .summary-value { color: var(--color-warning); }
    .dashboard-kpi.bad .summary-value { color: var(--color-danger); }

    .kpi-note {
      color: var(--muted);
      font-size: 12px;
    }

    .dashboard-split {
      display: grid;
      grid-template-columns: 1fr 1fr;
      gap: 20px;
    }

    .workspace-grid {
      display: grid;
      grid-template-columns: repeat(4, minmax(0, 1fr));
      gap: 14px;
    }

    .workspace-card {
      min-height: 154px;
      padding: 16px;
      border-radius: 8px;
      border: 1px solid var(--border);
      background: var(--surface);
      color: var(--ink);
      text-decoration: none;
      display: grid;
      grid-template-rows: auto 1fr auto;
      gap: 12px;
      position: relative;
      overflow: hidden;
      transition: transform var(--transition-fast), box-shadow var(--transition-fast), border-color var(--transition-fast);
    }

    .workspace-card:hover {
      transform: translateY(-2px);
      box-shadow: var(--shadow-md);
      border-color: rgba(91, 107, 255, 0.28);
    }

    .workspace-symbol {
      width: 44px;
      height: 44px;
      border-radius: 14px;
      display: inline-grid;
      place-items: center;
      color: #fff;
      font-size: 24px;
      box-shadow: 0 10px 22px rgba(15, 23, 42, 0.1);
    }

    .workspace-card.blue .workspace-symbol { background: linear-gradient(135deg, #5b6bff, #04a9f5); }
    .workspace-card.green .workspace-symbol { background: linear-gradient(135deg, #21a67a, #69c78f); }
    .workspace-card.amber .workspace-symbol { background: linear-gradient(135deg, #d99014, #f5b84b); }
    .workspace-card.rose .workspace-symbol { background: linear-gradient(135deg, #eb4d6d, #f2829a); }
    .workspace-card.violet .workspace-symbol { background: linear-gradient(135deg, #7957d5, #9b82f0); }
    .workspace-card.slate .workspace-symbol { background: linear-gradient(135deg, #334155, #64748b); }

    .workspace-copy {
      display: grid;
      gap: 6px;
      align-content: start;
    }

    .workspace-copy strong {
      font-size: 16px;
      color: var(--ink);
    }

    .workspace-copy small,
    .workspace-meta {
      color: var(--muted);
      font-size: 12px;
      line-height: 1.45;
    }

    .workspace-meta {
      min-height: 28px;
      padding-right: 30px;
      font-weight: 700;
    }

    .workspace-arrow {
      position: absolute;
      right: 14px;
      bottom: 14px;
      color: var(--accent);
      font-size: 20px;
    }

    .metric-list,
    .attention-list {
      display: grid;
      gap: 10px;
    }

    .metric-row,
    .attention-row {
      display: flex;
      align-items: center;
      justify-content: space-between;
      gap: 12px;
      padding: 12px 0;
      border-bottom: 1px solid var(--border-subtle);
    }

    .metric-row:last-child,
    .attention-row:last-child {
      border-bottom: 0;
    }

    .dot {
      width: 10px;
      height: 10px;
      border-radius: 999px;
      background: var(--color-success);
      flex: 0 0 auto;
    }

    .dot.warn { background: var(--color-warning); }
    .dot.bad { background: var(--color-danger); }

    .attention-row span:nth-child(2) {
      flex: 1;
      min-width: 0;
    }

    @media (max-width: 1100px) {
      .dashboard-grid,
      .workspace-grid {
        grid-template-columns: repeat(2, minmax(0, 1fr));
      }
    }

    @media (max-width: 720px) {
      .dashboard-grid,
      .workspace-grid,
      .dashboard-split {
        grid-template-columns: minmax(0, 1fr);
      }

      .workspace-card {
        min-height: 132px;
      }
    }
  `]
})
export class FranchiseDashboardComponent implements OnInit, OnDestroy {
  private readonly auth = inject(MockAuthService);
  private readonly sales = inject(SalesStoreService);
  private readonly inventory = inject(InventoryStoreService);
  private readonly expenses = inject(ExpenseStoreService);
  private readonly royalty = inject(RoyaltyStoreService);
  private readonly vendors = inject(VendorStoreService);
  private readonly pnl = inject(PnlService);

  kpis: DashboardKpi[] = [];
  visibleCards: DashboardCard[] = [];
  operations: Array<{ label: string; value: string }> = [];
  attention: Array<{ label: string; value: string; tone?: 'warn' | 'bad' }> = [];
  private sub?: Subscription;

  private readonly cards: DashboardCard[] = [
    {
      title: 'Master Data',
      symbol: 'storage',
      route: '/master-data',
      feature: 'master-data.view',
      description: 'Branches, vendors, products and setup records.',
      meta: 'Setup data center',
      tone: 'violet'
    },
    {
      title: 'Inventory',
      symbol: 'inventory_2',
      route: '/inventory/reports',
      feature: 'inventory.view',
      description: 'Stock value, consumption and low-stock visibility.',
      meta: 'Reports and stock health',
      tone: 'green'
    },
    {
      title: 'Operations',
      symbol: 'point_of_sale',
      route: '/sales',
      feature: 'sales.view',
      description: 'Daily sales, expenses, salary, royalty and P&L workflows.',
      meta: 'Daily controls and reports',
      tone: 'blue'
    },
    {
      title: 'Administration',
      symbol: 'admin_panel_settings',
      route: '/admin/modules',
      feature: 'admin.modules',
      description: 'Module enablement, feature access and user controls.',
      meta: 'Settings and access',
      tone: 'slate'
    }
  ];

  ngOnInit(): void {
    this.refresh();
    this.sub = merge(
      this.sales.changes$,
      this.inventory.changes$,
      this.expenses.changes$,
      this.royalty.changes$
    ).subscribe(() => this.refresh());
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  private refresh(): void {
    this.visibleCards = this.cards.filter(card => this.auth.hasFeature(card.feature));
    const today = new Date().toISOString().slice(0, 10);
    const month = today.slice(0, 7);
    const products = this.inventory.listProductsSnapshot();
    const stockValue = products.reduce((sum, p) => sum + p.stockValue, 0);
    const lowStock = products.filter(p => p.lowStock).length;
    const monthlySales = this.sales.netSalesForMonth(month);
    const monthlyExpenses = this.expenses.totalForMonth(month);
    const royaltyOutstanding = this.royalty.outstandingTotal();
    const vendorOutstanding = this.vendors.listSnapshot().reduce((sum, v) => sum + v.outstanding, 0);
    const period = this.pnl.computeMonth(month);

    this.kpis = [
      { label: "Today's Sales", value: this.money(this.sales.netSalesForMonth(today)), note: 'Net sales recorded today' },
      { label: 'Monthly Sales', value: this.money(monthlySales), note: month },
      { label: 'Stock Value', value: this.money(stockValue), note: `${products.length} products tracked`, tone: lowStock ? 'warn' : 'good' },
      { label: 'Net Profit', value: this.money(period.netProfit), note: `${period.marginPercent}% margin`, tone: period.netProfit >= 0 ? 'good' : 'bad' }
    ];

    this.operations = [
      { label: 'Monthly expenses', value: this.money(monthlyExpenses) },
      { label: 'Vendor outstanding', value: this.money(vendorOutstanding) },
      { label: 'Royalty outstanding', value: this.money(royaltyOutstanding) },
      { label: 'Low-stock items', value: String(lowStock) }
    ];

    this.attention = [
      { label: 'P&L health', value: period.netProfit >= 0 ? 'Profitable' : 'Loss', tone: period.netProfit >= 0 ? undefined : 'bad' },
      { label: 'Stock alerts', value: lowStock ? `${lowStock} low` : 'None', tone: lowStock ? 'warn' : undefined },
      { label: 'Vendor dues', value: vendorOutstanding > 0 ? this.money(vendorOutstanding) : 'Clear', tone: vendorOutstanding > 0 ? 'warn' : undefined },
      { label: 'Royalty dues', value: royaltyOutstanding > 0 ? this.money(royaltyOutstanding) : 'Clear', tone: royaltyOutstanding > 0 ? 'warn' : undefined }
    ];
  }

  private money(value: number): string {
    return `₹ ${Math.round(value).toLocaleString('en-IN')}`;
  }
}
