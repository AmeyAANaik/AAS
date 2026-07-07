import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { MatTabsModule } from '@angular/material/tabs';
import { PageHeaderComponent } from '../shared/page-header/page-header.component';
import { SalesEntryComponent } from './sales-entry/sales-entry.component';
import { SalesReportsComponent } from './sales-reports/sales-reports.component';

@Component({
  selector: 'app-sales-page',
  standalone: true,
  imports: [CommonModule, MatTabsModule, PageHeaderComponent, SalesEntryComponent, SalesReportsComponent],
  template: `
    <div class="berry-page">
      <app-page-header
        kicker="Operations"
        title="Daily Sales"
        subtitle="Record the day's takings and review sales trends across days, months and years.">
      </app-page-header>

      <mat-tab-group class="sales-tabs" animationDuration="200ms">
        <mat-tab label="Entry">
          <div class="tab-body">
            <app-sales-entry></app-sales-entry>
          </div>
        </mat-tab>
        <mat-tab label="Reports">
          <div class="tab-body">
            <app-sales-reports></app-sales-reports>
          </div>
        </mat-tab>
      </mat-tab-group>
    </div>
  `,
  styles: [`
    .tab-body {
      padding-top: 24px;
    }
  `]
})
export class SalesPageComponent {}
