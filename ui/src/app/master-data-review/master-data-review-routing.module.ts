import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { MasterDataReviewPageComponent } from './master-data-review-page.component';

const routes: Routes = [{ path: '', component: MasterDataReviewPageComponent }];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class MasterDataReviewRoutingModule {}
