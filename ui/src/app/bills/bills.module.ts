import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatDialogModule } from '@angular/material/dialog';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatDividerModule } from '@angular/material/divider';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatNativeDateModule } from '@angular/material/core';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatTableModule } from '@angular/material/table';
import { MatTooltipModule } from '@angular/material/tooltip';
import { StatusPillComponent } from '../shared/status-pill/status-pill.component';
import { EmptyStateComponent } from '../shared/empty-state/empty-state.component';
import { PageHeaderComponent } from '../shared/page-header/page-header.component';
import { BillsPageComponent } from './bills-page/bills-page.component';
import { InvoiceDeliveryDialogComponent } from './invoice-delivery-dialog/invoice-delivery-dialog.component';
import { InvoiceCreateComponent } from './invoice-create/invoice-create.component';
import { PaymentFormComponent } from './payment-form/payment-form.component';
import { BillsRoutingModule } from './bills-routing.module';

@NgModule({
  declarations: [BillsPageComponent, InvoiceCreateComponent, PaymentFormComponent, InvoiceDeliveryDialogComponent],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatButtonToggleModule,
    MatCardModule,
    MatDialogModule,
    MatDatepickerModule,
    MatDividerModule,
    MatExpansionModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatNativeDateModule,
    MatSelectModule,
    MatSlideToggleModule,
    MatTableModule,
    MatTooltipModule,
    StatusPillComponent,
    PageHeaderComponent,
    EmptyStateComponent,
    BillsRoutingModule
  ]
})
export class BillsModule {}
