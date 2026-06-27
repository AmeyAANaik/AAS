import { Routes } from '@angular/router';
import { LoginComponent } from './auth/login/login.component';
import { AppShellComponent } from './shell/app-shell.component';
import { ComingSoonComponent } from './shared/coming-soon/coming-soon.component';
import { NoAccessComponent } from './shared/no-access/no-access.component';
import { authGuard } from './auth/auth.guard';
import { featureGuard } from './auth/feature.guard';

export const routes: Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: 'login', component: LoginComponent },
  {
    path: '',
    component: AppShellComponent,
    canMatch: [authGuard],
    children: [
      {
        path: 'dashboard',
        component: ComingSoonComponent,
        canMatch: [featureGuard],
        data: {
          feature: 'dashboard.view',
          kicker: 'Home',
          title: 'Franchise Dashboard',
          description: "At-a-glance view of today's sales, stock value, outstanding and profit.",
          features: [
            "Today's Sales & Monthly Sales", 'Current Stock Value', 'Monthly Expenses',
            'Profit', 'Vendor Outstanding', 'Royalty Outstanding',
            'Charts: Sales Trend, Expense Breakdown, Stock Consumption, Profit Trend'
          ]
        }
      },
      {
        path: 'inventory',
        canMatch: [authGuard],
        loadChildren: () => import('./inventory/inventory.routes').then(m => m.INVENTORY_ROUTES)
      },
      {
        path: 'vendors',
        canMatch: [featureGuard],
        data: { feature: 'vendors.view' },
        loadChildren: () => import('./vendors/vendors.routes').then(m => m.VENDORS_ROUTES)
      },
      {
        path: 'sales',
        component: ComingSoonComponent,
        canMatch: [featureGuard],
        data: {
          feature: 'sales.view', kicker: 'Operations', title: 'Daily Sales',
          description: 'Manual POS (Petpooja) sales entry with payment-mode split.',
          features: ['Date, Gross Sales, GST, Discount, Net Sales', 'Cash / UPI / Card split', 'Daily, Monthly, Yearly sales reports']
        }
      },
      {
        path: 'expenses',
        component: ComingSoonComponent,
        canMatch: [featureGuard],
        data: {
          feature: 'expenses.view', kicker: 'Operations', title: 'Expense Management',
          description: 'Track operating expenses with bill attachments.',
          features: ['Electricity, Rent, GST, Internet, Marketing, Maintenance, Misc', 'Date, Amount, Remarks, Bill attachment', 'Monthly, Category-wise, Yearly reports']
        }
      },
      {
        path: 'salary',
        component: ComingSoonComponent,
        canMatch: [featureGuard],
        data: {
          feature: 'salary.view', kicker: 'Operations', title: 'Salary Tracking',
          description: 'Track employee salary expenses.',
          features: ['Add Employee', 'Employee status (Active/Inactive)', 'Monthly salary entry', 'Salary reports']
        }
      },
      {
        path: 'royalty',
        component: ComingSoonComponent,
        canMatch: [featureGuard],
        data: {
          feature: 'royalty.view', kicker: 'Operations', title: 'Royalty Management',
          description: 'Percentage-based royalty calculation and collection.',
          features: ['Monthly royalty calculation', 'Generate royalty due', 'Record & verify payment', 'Royalty ledger & outstanding', 'Franchise-wise / Pending / Paid reports']
        }
      },
      {
        path: 'pnl',
        component: ComingSoonComponent,
        canMatch: [featureGuard],
        data: {
          feature: 'pnl.view', kicker: 'Operations', title: 'Profit & Loss',
          description: 'Operational P&L across all modules.',
          features: ['Revenue − Raw Material − Salary − Rent − Electricity − Royalty − Other = Net Profit', 'Monthly / Yearly P&L', 'Franchise-wise & Consolidated P&L']
        }
      },
      {
        path: 'admin/modules',
        canMatch: [featureGuard],
        data: { feature: 'admin.modules' },
        loadComponent: () => import('./admin/module-settings/module-settings.component').then(m => m.ModuleSettingsComponent)
      },
      {
        path: 'admin/access',
        canMatch: [featureGuard],
        data: { feature: 'admin.access' },
        loadComponent: () => import('./admin/access-control/access-control.component').then(m => m.AccessControlComponent)
      },
      { path: 'no-access', component: NoAccessComponent },
      { path: '', redirectTo: 'dashboard', pathMatch: 'full' }
    ]
  },
  { path: '**', redirectTo: 'login' }
];
