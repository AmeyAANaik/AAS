import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatTableModule } from '@angular/material/table';
import { EmptyStateComponent } from '../shared/empty-state/empty-state.component';
import { PageHeaderComponent } from '../shared/page-header/page-header.component';
import { StatusPillComponent } from '../shared/status-pill/status-pill.component';
import { VendorOpsPageComponent } from './vendor-ops-page.component';
import { VendorOpsRoutingModule } from './vendor-ops-routing.module';

@NgModule({
  declarations: [VendorOpsPageComponent],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatTableModule,
    PageHeaderComponent,
    EmptyStateComponent,
    StatusPillComponent,
    VendorOpsRoutingModule
  ]
})
export class VendorOpsModule {}
