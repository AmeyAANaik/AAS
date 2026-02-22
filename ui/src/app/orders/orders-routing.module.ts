import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { OrderPageComponent } from './order-page/order-page.component';
import { OrderCreateComponent } from './order-create/order-create.component';

const routes: Routes = [
  { path: '', component: OrderPageComponent },
  { path: 'create', component: OrderCreateComponent }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class OrdersRoutingModule { }