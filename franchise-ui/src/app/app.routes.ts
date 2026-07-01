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
        canMatch: [featureGuard],
        data: { feature: 'sales.view' },
        loadComponent: () => import('./sales/sales-page.component').then(m => m.SalesPageComponent)
      },
      {
        path: 'expenses',
        canMatch: [featureGuard],
        data: { feature: 'expenses.view' },
        loadComponent: () => import('./expenses/expenses-page.component').then(m => m.ExpensesPageComponent)
      },
      {
        path: 'salary',
        canMatch: [featureGuard],
        data: { feature: 'salary.view' },
        loadComponent: () => import('./salary/salary-page.component').then(m => m.SalaryPageComponent)
      },
      {
        path: 'royalty',
        canMatch: [featureGuard],
        data: { feature: 'royalty.view' },
        loadComponent: () => import('./royalty/royalty-page.component').then(m => m.RoyaltyPageComponent)
      },
      {
        path: 'pnl',
        canMatch: [featureGuard],
        data: { feature: 'pnl.view' },
        loadComponent: () => import('./pnl/pnl-page.component').then(m => m.PnlPageComponent)
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
