import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { StatusPillComponent } from '../shared/status-pill/status-pill.component';
import { EmptyStateComponent } from '../shared/empty-state/empty-state.component';
import { PageHeaderComponent } from '../shared/page-header/page-header.component';
import { BillsPageComponent } from './bills-page/bills-page.component';
import { InvoiceCreateComponent } from './invoice-create/invoice-create.component';
import { PaymentFormComponent } from './payment-form/payment-form.component';
import { BillsRoutingModule } from './bills-routing.module';

@NgModule({
  declarations: [BillsPageComponent, InvoiceCreateComponent, PaymentFormComponent],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatExpansionModule,
    MatFormFieldModule,
    MatInputModule,
    MatSelectModule,
    MatTableModule,
    StatusPillComponent,
    PageHeaderComponent,
    EmptyStateComponent,
    BillsRoutingModule
  ]
})
export class BillsModule {}
