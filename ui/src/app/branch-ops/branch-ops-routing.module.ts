import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { BranchOpsPageComponent } from './branch-ops-page.component';

const routes: Routes = [
  { path: '', component: BranchOpsPageComponent },
  { path: ':branchId', component: BranchOpsPageComponent }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class BranchOpsRoutingModule {}
