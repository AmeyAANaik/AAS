import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatTableModule } from '@angular/material/table';
import { StatusPillComponent } from '../shared/status-pill/status-pill.component';
import { EmptyStateComponent } from '../shared/empty-state/empty-state.component';
import { PageHeaderComponent } from '../shared/page-header/page-header.component';
import { StockListComponent } from './stock-list/stock-list.component';
import { StockThresholdFormComponent } from './stock-threshold-form/stock-threshold-form.component';
import { StockRoutingModule } from './stock-routing.module';

@NgModule({
  declarations: [StockListComponent, StockThresholdFormComponent],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatTableModule,
    StatusPillComponent,
    PageHeaderComponent,
    EmptyStateComponent,
    StockRoutingModule
  ]
})
export class StockModule {}
