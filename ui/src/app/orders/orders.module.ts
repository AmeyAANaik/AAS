import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatBadgeModule } from '@angular/material/badge';
import { MatChipsModule } from '@angular/material/chips';
import { MatDialogModule } from '@angular/material/dialog';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatNativeDateModule } from '@angular/material/core';
import { MatPaginatorModule } from '@angular/material/paginator';
import { MatProgressSpinnerModule } from '@angular/material/progress-spinner';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatSortModule } from '@angular/material/sort';
import { MatSnackBarModule } from '@angular/material/snack-bar';
import { MatSidenavModule } from '@angular/material/sidenav';
import { MatStepperModule } from '@angular/material/stepper';
import { MatTableModule } from '@angular/material/table';
import { StatusPillComponent } from '../shared/status-pill/status-pill.component';
import { EmptyStateComponent } from '../shared/empty-state/empty-state.component';
import { PageHeaderComponent } from '../shared/page-header/page-header.component';
import { OrderCreateComponent } from './order-create/order-create.component';
import { OrderPageComponent } from './order-page/order-page.component';
import { OrderAdvancedFiltersDialogComponent } from './order-page/order-advanced-filters-dialog.component';
import { OrderDeleteConfirmDialogComponent } from './order-page/order-delete-confirm-dialog.component';
import { OrdersRoutingModule } from './orders-routing.module';

@NgModule({
  declarations: [OrderPageComponent, OrderCreateComponent, OrderAdvancedFiltersDialogComponent, OrderDeleteConfirmDialogComponent],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatCheckboxModule,
    MatDatepickerModule,
    MatBadgeModule,
    MatChipsModule,
    MatDialogModule,
    MatExpansionModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatNativeDateModule,
    MatPaginatorModule,
    MatProgressSpinnerModule,
    MatSelectModule,
    MatSlideToggleModule,
    MatSortModule,
    MatSnackBarModule,
    MatSidenavModule,
    MatStepperModule,
    MatTableModule,
    StatusPillComponent,
    PageHeaderComponent,
    EmptyStateComponent,
    OrdersRoutingModule
  ]
})
export class OrdersModule {}
