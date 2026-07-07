import { Routes } from '@angular/router';
import { featureGuard } from '../auth/feature.guard';

export const INVENTORY_ROUTES: Routes = [
  {
    path: 'products',
    redirectTo: '/master-data/products',
    pathMatch: 'full'
  },
  {
    path: 'purchases',
    canMatch: [featureGuard],
    data: { feature: 'inventory.purchase' },
    loadComponent: () => import('./purchase-entry/purchase-entry.component').then(m => m.PurchaseEntryComponent)
  },
  {
    path: 'consumption',
    canMatch: [featureGuard],
    data: { feature: 'inventory.consume' },
    loadComponent: () => import('./consumption-entry/consumption-entry.component').then(m => m.ConsumptionEntryComponent)
  },
  {
    path: 'reports',
    canMatch: [featureGuard],
    data: { feature: 'inventory.view' },
    loadComponent: () => import('./stock-reports/stock-reports.component').then(m => m.StockReportsComponent)
  },
  { path: '', redirectTo: 'products', pathMatch: 'full' }
];
