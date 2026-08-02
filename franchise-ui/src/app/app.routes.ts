import { Routes } from '@angular/router';
import { LoginComponent } from './auth/login/login.component';
import { AppShellComponent } from './shell/app-shell.component';
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
        loadComponent: () => import('./dashboard/franchise-dashboard.component').then(m => m.FranchiseDashboardComponent),
        canMatch: [featureGuard],
        data: {
          feature: 'dashboard.view'
        }
      },
      {
        path: 'master-data',
        canMatch: [featureGuard],
        data: { feature: 'master-data.view' },
        loadComponent: () => import('./master-data/master-data-page.component').then(m => m.MasterDataPageComponent)
      },
      {
        path: 'master-data/branches',
        canMatch: [featureGuard],
        data: { feature: 'branches.manage' },
        loadComponent: () => import('./master-data/branch-list/branch-list.component').then(m => m.BranchListComponent)
      },
      {
        path: 'master-data/vendors',
        canMatch: [featureGuard],
        data: { feature: 'vendors.view' },
        loadComponent: () => import('./vendors/vendor-list/vendor-list.component').then(m => m.VendorListComponent)
      },
      {
        path: 'master-data/products',
        canMatch: [featureGuard],
        data: { feature: 'inventory.manage' },
        loadComponent: () => import('./inventory/product-list/product-list.component').then(m => m.ProductListComponent)
      },
      {
        path: 'master-data/product-categories',
        canMatch: [featureGuard],
        data: { feature: 'inventory.manage' },
        loadComponent: () => import('./master-data/product-category-list/product-category-list.component').then(m => m.ProductCategoryListComponent)
      },
      {
        path: 'master-data/employees',
        canMatch: [featureGuard],
        data: { feature: 'employees.manage' },
        loadComponent: () => import('./master-data/master-wrapper-pages.component').then(m => m.EmployeeMasterPageComponent)
      },
      {
        path: 'master-data/payment-modes',
        canMatch: [featureGuard],
        data: { feature: 'payment-modes.manage' },
        loadComponent: () => import('./master-data/master-wrapper-pages.component').then(m => m.PaymentModeMasterPageComponent)
      },
      {
        path: 'master-data/expense-categories',
        canMatch: [featureGuard],
        data: { feature: 'expense-categories.manage' },
        loadComponent: () => import('./master-data/expense-category-list/expense-category-list.component').then(m => m.ExpenseCategoryListComponent)
      },
      {
        path: 'master-data/royalty-config',
        canMatch: [featureGuard],
        data: { feature: 'royalty-config.manage' },
        loadComponent: () => import('./master-data/master-wrapper-pages.component').then(m => m.RoyaltyConfigMasterPageComponent)
      },
      {
        path: 'inventory',
        canMatch: [authGuard],
        loadChildren: () => import('./inventory/inventory.routes').then(m => m.INVENTORY_ROUTES)
      },
      {
        path: 'vendors',
        redirectTo: 'master-data/vendors',
        pathMatch: 'full'
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
