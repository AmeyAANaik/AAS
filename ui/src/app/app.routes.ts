import { Routes } from '@angular/router';
import { authGuard } from './auth/auth.guard';
import { featureGuard } from './auth/feature.guard';
import { LoginComponent } from './auth/login/login.component';
import { AppShellComponent } from './shell/app-shell.component';

export const routes: Routes = [
  { path: '', redirectTo: 'login', pathMatch: 'full' },
  { path: 'login', component: LoginComponent },
  { path: 'admin', redirectTo: 'admin/dashboard', pathMatch: 'full' },
  {
    path: '',
    component: AppShellComponent,
    canMatch: [authGuard],
    children: [
      {
        path: 'admin/dashboard',
        loadChildren: () => import('./dashboard/dashboard.module').then(m => m.DashboardModule),
        canMatch: [featureGuard],
        data: { feature: 'dashboard.view' }
      },
      {
        path: 'vendor-ops',
        loadChildren: () => import('./vendor-ops/vendor-ops.module').then(m => m.VendorOpsModule),
        canMatch: [featureGuard],
        data: { feature: 'vendor_ops.view' }
      },
      {
        path: 'branch-ops',
        loadChildren: () => import('./branch-ops/branch-ops.module').then(m => m.BranchOpsModule),
        canMatch: [featureGuard],
        data: { feature: 'branch_ops.view' }
      },
      {
        path: 'master-data-review',
        loadChildren: () => import('./master-data-review/master-data-review.module').then(m => m.MasterDataReviewModule),
        canMatch: [featureGuard],
        data: { feature: 'master_data_review.view' }
      },
      {
        path: 'bill-review',
        loadChildren: () => import('./bill-review/bill-review.module').then(m => m.BillReviewModule),
        canMatch: [featureGuard],
        data: { feature: 'bill_review.view' }
      },
      {
        path: 'company-settings',
        loadChildren: () => import('./company-settings/company-settings.module').then(m => m.CompanySettingsModule),
        canMatch: [featureGuard],
        data: { feature: 'company_settings.view' }
      },
      {
        path: 'user-settings',
        loadChildren: () => import('./user-settings/user-settings.module').then(m => m.UserSettingsModule),
        canMatch: [featureGuard],
        data: { feature: 'user_settings.view' }
      },
      { path: 'orders', loadChildren: () => import('./orders/orders.module').then(m => m.OrdersModule), canMatch: [featureGuard], data: { feature: 'orders.view' } },
      { path: 'stock', loadChildren: () => import('./stock/stock.module').then(m => m.StockModule), canMatch: [featureGuard], data: { feature: 'stock.view' } },
      { path: 'bills', loadChildren: () => import('./bills/bills.module').then(m => m.BillsModule), canMatch: [featureGuard], data: { feature: 'bills.view' } },
      { path: 'vendors', loadChildren: () => import('./vendors/vendors.module').then(m => m.VendorsModule), canMatch: [featureGuard], data: { feature: 'master_data.view' } },
      { path: 'branches', loadChildren: () => import('./branches/branches.module').then(m => m.BranchesModule), canMatch: [featureGuard], data: { feature: 'master_data.view' } },
      { path: 'categories', loadChildren: () => import('./categories/categories.module').then(m => m.CategoriesModule), canMatch: [featureGuard], data: { feature: 'master_data.view' } },
      { path: 'items', loadChildren: () => import('./items/items.module').then(m => m.ItemsModule), canMatch: [featureGuard], data: { feature: 'items.manage' } },
      {
        path: 'reports',
        redirectTo: 'analytics',
        pathMatch: 'full'
      },
      {
        path: 'analytics',
        loadChildren: () => import('./analytics/analytics.module').then(m => m.AnalyticsModule),
        canMatch: [featureGuard],
        data: { feature: 'reports.view' }
      },
      { path: '', redirectTo: 'admin/dashboard', pathMatch: 'full' }
    ]
  },
  { path: '**', redirectTo: 'login' }
];
