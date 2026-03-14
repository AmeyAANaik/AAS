import { NgModule } from '@angular/core';
import { RouterModule, Routes } from '@angular/router';
import { VendorOpsPageComponent } from './vendor-ops-page.component';

const routes: Routes = [
  { path: '', component: VendorOpsPageComponent },
  { path: ':vendorId', component: VendorOpsPageComponent }
];

@NgModule({
  imports: [RouterModule.forChild(routes)],
  exports: [RouterModule]
})
export class VendorOpsRoutingModule {}
