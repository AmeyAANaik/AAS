import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { RouterModule } from '@angular/router';
import { MockAuthService } from '../auth/mock-auth.service';
import { FeatureKey } from '../core/rbac';
import { PageHeaderComponent } from '../shared/page-header/page-header.component';

interface MasterCard {
  title: string;
  icon: string;
  route: string;
  feature: FeatureKey;
  description: string;
}

@Component({
  selector: 'app-master-data-page',
  standalone: true,
  imports: [CommonModule, RouterModule, PageHeaderComponent],
  template: `
    <div class="berry-page">
      <app-page-header
        kicker="Master Data"
        title="Master Data"
        subtitle="Setup records used by franchise operations, reporting and P&L.">
      </app-page-header>

      <section class="master-grid">
        <a *ngFor="let card of visibleCards" class="master-card" [routerLink]="card.route">
          <span class="material-icons master-icon" aria-hidden="true">{{ card.icon }}</span>
          <strong>{{ card.title }}</strong>
          <small>{{ card.description }}</small>
          <span class="material-icons master-arrow" aria-hidden="true">arrow_forward</span>
        </a>
      </section>
    </div>
  `,
  styles: [`
    .master-grid {
      display: grid;
      grid-template-columns: repeat(3, minmax(0, 1fr));
      gap: 16px;
    }

    .master-card {
      min-height: 154px;
      border: 1px solid var(--border);
      border-radius: var(--radius);
      background: var(--surface);
      color: var(--ink);
      text-decoration: none;
      padding: 18px;
      display: grid;
      gap: 10px;
      position: relative;
      align-content: start;
      transition: transform var(--transition-fast), box-shadow var(--transition-fast), border-color var(--transition-fast);
    }

    .master-card:hover {
      transform: translateY(-2px);
      box-shadow: var(--shadow-md);
      border-color: rgba(91, 107, 255, 0.3);
    }

    .master-icon {
      width: 42px;
      height: 42px;
      display: inline-grid;
      place-items: center;
      border-radius: 12px;
      color: #fff;
      background: linear-gradient(135deg, #5b6bff, #04a9f5);
      box-shadow: 0 10px 22px rgba(15, 23, 42, 0.1);
    }

    .master-card strong {
      font-size: 16px;
    }

    .master-card small {
      color: var(--muted);
      line-height: 1.45;
      padding-right: 28px;
    }

    .master-arrow {
      position: absolute;
      right: 16px;
      bottom: 16px;
      color: var(--accent);
      font-size: 20px;
    }

    @media (max-width: 980px) {
      .master-grid { grid-template-columns: repeat(2, minmax(0, 1fr)); }
    }

    @media (max-width: 640px) {
      .master-grid { grid-template-columns: minmax(0, 1fr); }
    }
  `]
})
export class MasterDataPageComponent {
  private readonly cards: MasterCard[] = [
    { title: 'Branches', icon: 'store', route: '/master-data/branches', feature: 'branches.manage', description: 'Franchise branch identity, address, manager and setup status.' },
    { title: 'Vendors', icon: 'local_shipping', route: '/master-data/vendors', feature: 'vendors.view', description: 'Supplier master, invoice template setup and activation.' },
    { title: 'Products', icon: 'inventory_2', route: '/master-data/products', feature: 'inventory.manage', description: 'Product names, units, minimum stock and opening stock.' },
    { title: 'Product Categories', icon: 'category', route: '/master-data/product-categories', feature: 'inventory.manage', description: 'Inventory category taxonomy used by products, purchases and consumption.' },
    { title: 'Employees', icon: 'badge', route: '/master-data/employees', feature: 'employees.manage', description: 'Staff records used by salary generation.' },
    { title: 'Payment Modes', icon: 'payments', route: '/master-data/payment-modes', feature: 'payment-modes.manage', description: 'Cash, UPI, card, delivery app and other sales channels.' },
    { title: 'Expense Categories', icon: 'receipt_long', route: '/master-data/expense-categories', feature: 'expense-categories.manage', description: 'Expense heads mapped into reporting and P&L buckets.' },
    { title: 'Royalty Setup', icon: 'percent', route: '/master-data/royalty-config', feature: 'royalty-config.manage', description: 'Royalty percentage used for future dues generation.' }
  ];

  constructor(private auth: MockAuthService) {}

  get visibleCards(): MasterCard[] {
    return this.cards.filter(card => this.auth.hasFeature(card.feature));
  }
}
