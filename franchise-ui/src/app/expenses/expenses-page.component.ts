import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { MatTabsModule } from '@angular/material/tabs';
import { PageHeaderComponent } from '../shared/page-header/page-header.component';
import { ExpenseEntryComponent } from './expense-entry/expense-entry.component';
import { ExpenseReportsComponent } from './expense-reports/expense-reports.component';

@Component({
  selector: 'app-expenses-page',
  standalone: true,
  imports: [
    CommonModule, MatTabsModule, PageHeaderComponent,
    ExpenseEntryComponent, ExpenseReportsComponent
  ],
  template: `
    <div class="berry-page">
      <app-page-header
        kicker="Operations"
        title="Expense Management"
        subtitle="Record operating costs and review month- and year-wise expense reports."></app-page-header>

      <mat-tab-group animationDuration="0ms">
        <mat-tab label="Entry">
          <app-expense-entry></app-expense-entry>
        </mat-tab>
        <mat-tab label="Reports">
          <app-expense-reports></app-expense-reports>
        </mat-tab>
      </mat-tab-group>
    </div>
  `
})
export class ExpensesPageComponent {}
