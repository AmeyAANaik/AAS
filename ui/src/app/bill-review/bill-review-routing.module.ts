import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { BillReviewPageComponent } from './bill-review-page.component';

const routes: Routes = [{ path: '', component: BillReviewPageComponent }];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class BillReviewRoutingModule {}

