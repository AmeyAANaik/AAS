import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { BillsPageComponent } from './bills-page/bills-page.component';

const routes: Routes = [{ path: '', component: BillsPageComponent }];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class BillsRoutingModule {}
