import { Routes } from '@angular/router';
import { authGuard } from './auth/auth.guard';
import { LoginComponent } from './auth/login/login.component';
import { adminDashboardGuard } from './dashboard/admin-dashboard.guard';
import { AppShellComponent } from './shell/app-shell.component';
import { ReportsPlaceholderComponent } from './shell/reports-placeholder.component';

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
        canMatch: [adminDashboardGuard]
      },
      { path: 'orders', loadChildren: () => import('./orders/orders.module').then(m => m.OrdersModule) },
      { path: 'stock', loadChildren: () => import('./stock/stock.module').then(m => m.StockModule) },
      { path: 'bills', loadChildren: () => import('./bills/bills.module').then(m => m.BillsModule) },
      { path: 'vendors', loadChildren: () => import('./vendors/vendors.module').then(m => m.VendorsModule) },
      { path: 'branches', loadChildren: () => import('./branches/branches.module').then(m => m.BranchesModule) },
      { path: 'categories', loadChildren: () => import('./categories/categories.module').then(m => m.CategoriesModule) },
      { path: 'items', loadChildren: () => import('./items/items.module').then(m => m.ItemsModule) },
      { path: 'reports', component: ReportsPlaceholderComponent },
      { path: '', redirectTo: 'admin/dashboard', pathMatch: 'full' }
    ]
  },
  { path: '**', redirectTo: 'login' }
];
