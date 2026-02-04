import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { EmptyStateComponent } from '../shared/empty-state/empty-state.component';
import { PageHeaderComponent } from '../shared/page-header/page-header.component';
import { StatusPillComponent } from '../shared/status-pill/status-pill.component';
import { VendorFormComponent } from './vendor-form/vendor-form.component';
import { VendorListComponent } from './vendor-list/vendor-list.component';
import { VendorsRoutingModule } from './vendors-routing.module';

@NgModule({
  declarations: [VendorListComponent, VendorFormComponent],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatTableModule,
    PageHeaderComponent,
    EmptyStateComponent,
    StatusPillComponent,
    VendorsRoutingModule
  ]
})
export class VendorsModule {}
