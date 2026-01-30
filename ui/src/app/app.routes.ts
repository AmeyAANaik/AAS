import { Routes } from '@angular/router';
import { AppComponent } from './app.component';

export const routes: Routes = [
  { path: '', component: AppComponent },
  { path: 'admin', component: AppComponent },
  { path: 'vendor', component: AppComponent },
  { path: 'shop', component: AppComponent },
  { path: 'helper', component: AppComponent },
  { path: '**', redirectTo: '' }
];
