import { Component } from '@angular/core';
import { MatTabsModule } from '@angular/material/tabs';
import { PageHeaderComponent } from '../shared/page-header/page-header.component';
import { SalaryEntryComponent } from './salary-entry/salary-entry.component';
import { SalaryReportsComponent } from './salary-reports/salary-reports.component';

@Component({
  selector: 'app-salary-page',
  standalone: true,
  imports: [
    MatTabsModule, PageHeaderComponent,
    SalaryEntryComponent, SalaryReportsComponent
  ],
  template: `
    <div class="berry-page">
      <app-page-header
        kicker="Operations"
        title="Salary Tracking"
        subtitle="Employee master, monthly salary generation and payment reports.">
      </app-page-header>

      <div class="berry-panel">
        <mat-tab-group animationDuration="0ms">
          <mat-tab label="Monthly Salary">
            <div class="tab-body"><app-salary-entry></app-salary-entry></div>
          </mat-tab>
          <mat-tab label="Reports">
            <div class="tab-body"><app-salary-reports></app-salary-reports></div>
          </mat-tab>
        </mat-tab-group>
      </div>
    </div>
  `,
  styles: [`
    .tab-body {
      padding: 20px 4px 4px;
    }
  `]
})
export class SalaryPageComponent {}
