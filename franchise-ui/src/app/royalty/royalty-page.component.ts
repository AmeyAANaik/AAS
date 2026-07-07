import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { MatTabsModule } from '@angular/material/tabs';
import { PageHeaderComponent } from '../shared/page-header/page-header.component';
import { RoyaltyListComponent } from './royalty-list/royalty-list.component';
import { RoyaltyReportsComponent } from './royalty-reports/royalty-reports.component';

@Component({
  selector: 'app-royalty-page',
  standalone: true,
  imports: [
    CommonModule, MatTabsModule, PageHeaderComponent,
    RoyaltyListComponent, RoyaltyReportsComponent
  ],
  template: `
    <div class="berry-page">
      <app-page-header
        kicker="Operations"
        title="Royalty Management"
        subtitle="Generate royalty as a percentage of monthly net sales, record payments and track outstanding dues.">
      </app-page-header>

      <mat-tab-group class="royalty-tabs" animationDuration="200ms">
        <mat-tab label="Royalty">
          <div class="tab-body">
            <app-royalty-list></app-royalty-list>
          </div>
        </mat-tab>
        <mat-tab label="Reports">
          <div class="tab-body">
            <app-royalty-reports></app-royalty-reports>
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
export class RoyaltyPageComponent {}
