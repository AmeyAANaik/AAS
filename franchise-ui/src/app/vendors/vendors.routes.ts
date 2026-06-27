import { Routes } from '@angular/router';

export const VENDORS_ROUTES: Routes = [
  {
    path: '',
    loadComponent: () => import('./vendor-list/vendor-list.component').then(m => m.VendorListComponent)
  }
];
