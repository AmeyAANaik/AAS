import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';
import { MatTableModule } from '@angular/material/table';
import { PageHeaderComponent } from '../shared/page-header/page-header.component';
import { BerryStatCardComponent } from '../shared/components/berry-stat-card/berry-stat-card.component';
import { BerryChartCardComponent } from '../shared/components/berry-chart-card/berry-chart-card.component';
import { DashboardComponent } from './dashboard.component';
import { DashboardRoutingModule } from './dashboard-routing.module';

@NgModule({
  imports: [
    DashboardComponent,
    DashboardRoutingModule,
    MatButtonModule,
    MatCardModule,
    MatIconModule,
    MatTableModule,
    PageHeaderComponent,
    BerryStatCardComponent,
    BerryChartCardComponent
  ]
})
export class DashboardModule {}
