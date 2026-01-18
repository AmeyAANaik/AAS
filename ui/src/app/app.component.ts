import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';

type Role = 'admin' | 'vendor' | 'shop' | 'helper';

type NavItem = {
  id: string;
  label: string;
};

type Kpi = {
  title: string;
  value: string;
  meta: string;
};

type OrderRow = {
  id: string;
  shop: string;
  vendor: string;
  status: string;
  statusTone: 'prep' | 'ready' | 'assign' | 'delivered';
  total: string;
  eta: string;
};

type ListItem = {
  label: string;
  value?: string;
  tone?: 'ok' | 'warn' | 'danger';
};

type ReportCard = {
  title: string;
  description: string;
  roles: Role[];
};

type SettingsCard = {
  title: string;
  description: string;
  roles: Role[];
};

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent {
  readonly roles: Role[] = ['admin', 'vendor', 'shop', 'helper'];
  currentRole: Role = 'admin';
  activeSection = 'overview';

  navItems: NavItem[] = [
    { id: 'overview', label: 'Overview' },
    { id: 'orders', label: 'Orders' },
    { id: 'inventory', label: 'Inventory' },
    { id: 'payments', label: 'Payments' },
    { id: 'reports', label: 'Reports' },
    { id: 'settings', label: 'Settings' }
  ];

  kpis: Kpi[] = [
    { title: 'Orders Today', value: '128', meta: '+12% vs yesterday' },
    { title: 'Vendors Active', value: '24', meta: '3 awaiting assignment' },
    { title: 'Inventory Alerts', value: '7', meta: 'Milk, Tomatoes, Oil' },
    { title: 'Payments Due', value: 'Rs 2.4L', meta: '5 vendors overdue' }
  ];

  orders: OrderRow[] = [
    {
      id: 'SO-2031',
      shop: 'Hotel Harbor',
      vendor: 'Freshly Foods',
      status: 'Preparing',
      statusTone: 'prep',
      total: 'Rs 18,450',
      eta: '45 min'
    },
    {
      id: 'SO-2030',
      shop: 'Central Bistro',
      vendor: 'Green Basket',
      status: 'Ready',
      statusTone: 'ready',
      total: 'Rs 7,820',
      eta: '15 min'
    },
    {
      id: 'SO-2029',
      shop: 'Redwood Cafe',
      vendor: 'Prime Poultry',
      status: 'Awaiting Vendor',
      statusTone: 'assign',
      total: 'Rs 5,150',
      eta: '--'
    }
  ];

  criticalItems: ListItem[] = [
    { label: 'Tomatoes', value: 'Low', tone: 'warn' },
    { label: 'Paneer', value: 'Low', tone: 'warn' },
    { label: 'Cooking Oil', value: 'Out', tone: 'danger' },
    { label: 'Rice', value: 'Stable', tone: 'ok' }
  ];

  categories: string[] = ['Vegetables', 'Dairy', 'Spices', 'Meat', 'Dry Goods', 'Beverages'];

  vendorPayments: ListItem[] = [
    { label: 'Green Basket', value: 'Rs 48,900' },
    { label: 'Prime Poultry', value: 'Rs 32,110' },
    { label: 'Freshly Foods', value: 'Rs 21,400' }
  ];

  shopInvoices: ListItem[] = [
    { label: 'Hotel Harbor', value: 'Paid', tone: 'ok' },
    { label: 'Central Bistro', value: 'Pending', tone: 'warn' },
    { label: 'Redwood Cafe', value: 'Overdue', tone: 'danger' }
  ];

  reports: ReportCard[] = [
    {
      title: 'Vendor billing',
      description: 'Monthly billing and payment summary by vendor.',
      roles: ['admin', 'vendor']
    },
    {
      title: 'Shop billing',
      description: 'Monthly billing and category-wise breakdown.',
      roles: ['admin', 'shop']
    },
    {
      title: 'Order volume',
      description: 'Orders per shop, vendor, and category.',
      roles: ['admin', 'vendor', 'shop']
    }
  ];

  settingsCards: SettingsCard[] = [
    {
      title: 'Vendors',
      description: 'Onboard, assign, and audit performance.',
      roles: ['admin']
    },
    {
      title: 'Shops',
      description: 'Control shop access and order limits.',
      roles: ['admin']
    },
    {
      title: 'Helpers',
      description: 'Delivery tracking and item updates.',
      roles: ['admin', 'helper']
    }
  ];

  constructor() {}

  roleLabel(role: Role): string {
    return role.charAt(0).toUpperCase() + role.slice(1);
  }

  setRole(role: Role): void {
    this.currentRole = role;
  }

  setActive(sectionId: string): void {
    this.activeSection = sectionId;
  }

  isRole(role: Role): boolean {
    return this.currentRole === role;
  }

  isRoleIn(roles: Role[]): boolean {
    return roles.includes(this.currentRole);
  }

  // Integration logic removed per request.
}
