import { CommonModule } from '@angular/common';
import { Component, inject, OnDestroy, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { merge, Subscription } from 'rxjs';
import { PageHeaderComponent } from '../shared/page-header/page-header.component';
import { SalesStoreService } from '../sales/sales-store.service';
import { ExpenseStoreService } from '../expenses/expense-store.service';
import { SalaryStoreService } from '../salary/salary-store.service';
import { InventoryStoreService } from '../inventory/inventory-store.service';
import { PnlLine, PnlPeriod } from './pnl.model';
import { PnlService } from './pnl.service';
import { RoyaltyStoreService } from '../royalty/royalty-store.service';

type ViewMode = 'monthly' | 'yearly';

@Component({
  selector: 'app-pnl-page',
  standalone: true,
  imports: [
    CommonModule, FormsModule,
    MatButtonToggleModule, MatFormFieldModule, MatInputModule, MatSelectModule,
    PageHeaderComponent
  ],
  template: `
    <div class="berry-page">
      <app-page-header
        kicker="Operations"
        title="Profit & Loss"
        subtitle="Operational P&L across all modules.">
      </app-page-header>

      <!-- Controls -->
      <div class="berry-panel pnl-controls">
        <mat-button-toggle-group [value]="view" (change)="onViewChange($event.value)" aria-label="P&L view">
          <mat-button-toggle value="monthly">Monthly</mat-button-toggle>
          <mat-button-toggle value="yearly">Yearly</mat-button-toggle>
        </mat-button-toggle-group>

        <ng-container *ngIf="view === 'monthly'">
          <mat-form-field appearance="outline" class="control-field">
            <mat-label>Month</mat-label>
            <input matInput type="month" [(ngModel)]="month" (ngModelChange)="recompute()">
          </mat-form-field>
        </ng-container>

        <ng-container *ngIf="view === 'yearly'">
          <mat-form-field appearance="outline" class="control-field">
            <mat-label>Year</mat-label>
            <mat-select [(ngModel)]="year" (ngModelChange)="recompute()">
              <mat-option *ngFor="let y of years" [value]="y">{{ y }}</mat-option>
            </mat-select>
          </mat-form-field>
        </ng-container>
      </div>

      <!-- KPI cards -->
      <div class="berry-grid-4">
        <div class="berry-kpi-card">
          <span class="summary-label">Revenue</span>
          <span class="summary-value">₹ {{ scope.revenue | number:'1.0-2' }}</span>
        </div>
        <div class="berry-kpi-card">
          <span class="summary-label">Total Cost</span>
          <span class="summary-value">₹ {{ scope.totalCost | number:'1.0-2' }}</span>
        </div>
        <div class="berry-kpi-card">
          <span class="summary-label">Net Profit</span>
          <span class="summary-value" [class.profit]="scope.netProfit >= 0" [class.loss]="scope.netProfit < 0">
            ₹ {{ scope.netProfit | number:'1.0-2' }}
          </span>
        </div>
        <div class="berry-kpi-card">
          <span class="summary-label">Margin %</span>
          <span class="summary-value" [class.profit]="scope.netProfit >= 0" [class.loss]="scope.netProfit < 0">
            {{ scope.marginPercent | number:'1.0-1' }}%
          </span>
        </div>
      </div>

      <!-- Line-item breakdown -->
      <div class="berry-panel">
        <div class="berry-panel-header">
          <div>
            <div class="berry-panel-title">P&L Breakdown</div>
            <div class="berry-panel-subtitle">{{ scope.label }}</div>
          </div>
        </div>
        <div class="table-wrapper">
          <table class="pro-table">
            <thead>
              <tr>
                <th>Line item</th>
                <th class="num">Amount</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let line of breakdown"
                  [class.total-row]="line.kind === 'total'">
                <td>{{ line.label }}</td>
                <td class="num"
                    [class.profit]="line.kind === 'total' && scope.netProfit >= 0"
                    [class.loss]="line.kind === 'total' && scope.netProfit < 0">
                  ₹ {{ line.amount | number:'1.0-2' }}
                </td>
              </tr>
            </tbody>
          </table>
        </div>
      </div>

      <!-- Yearly month-by-month table -->
      <div class="berry-panel" *ngIf="view === 'yearly'">
        <div class="berry-panel-header">
          <div>
            <div class="berry-panel-title">Month-by-Month</div>
            <div class="berry-panel-subtitle">{{ year }}</div>
          </div>
        </div>
        <div class="table-wrapper">
          <table class="pro-table">
            <thead>
              <tr>
                <th>Month</th>
                <th class="num">Revenue</th>
                <th class="num">Total Cost</th>
                <th class="num">Net Profit</th>
                <th class="num">Margin %</th>
              </tr>
            </thead>
            <tbody>
              <tr *ngFor="let p of months">
                <td>{{ p.label }}</td>
                <td class="num">₹ {{ p.revenue | number:'1.0-2' }}</td>
                <td class="num">₹ {{ p.totalCost | number:'1.0-2' }}</td>
                <td class="num" [class.profit]="p.netProfit >= 0" [class.loss]="p.netProfit < 0">
                  ₹ {{ p.netProfit | number:'1.0-2' }}
                </td>
                <td class="num" [class.profit]="p.netProfit >= 0" [class.loss]="p.netProfit < 0">
                  {{ p.marginPercent | number:'1.0-1' }}%
                </td>
              </tr>
            </tbody>
            <tfoot>
              <tr class="total-row">
                <td>Total {{ year }}</td>
                <td class="num">₹ {{ yearTotal.revenue | number:'1.0-2' }}</td>
                <td class="num">₹ {{ yearTotal.totalCost | number:'1.0-2' }}</td>
                <td class="num" [class.profit]="yearTotal.netProfit >= 0" [class.loss]="yearTotal.netProfit < 0">
                  ₹ {{ yearTotal.netProfit | number:'1.0-2' }}
                </td>
                <td class="num" [class.profit]="yearTotal.netProfit >= 0" [class.loss]="yearTotal.netProfit < 0">
                  {{ yearTotal.marginPercent | number:'1.0-1' }}%
                </td>
              </tr>
            </tfoot>
          </table>
        </div>
      </div>
    </div>
  `,
  styles: [`
    .pnl-controls {
      display: flex;
      flex-wrap: wrap;
      align-items: center;
      gap: 16px;
    }
    .control-field {
      width: 200px;
      margin-bottom: -1.25em; /* trim Material's default hint gap */
    }
    .num {
      text-align: right;
      white-space: nowrap;
      font-variant-numeric: tabular-nums;
    }
    .total-row td {
      font-weight: 700;
      border-top: 2px solid var(--border, rgba(0, 0, 0, 0.12));
    }
    .profit { color: var(--color-success, #2c7a4b); }
    .loss { color: var(--color-danger, #b3472f); }
  `]
})
export class PnlPageComponent implements OnInit, OnDestroy {
  private readonly pnl = inject(PnlService);
  private readonly sales = inject(SalesStoreService);
  private readonly expenses = inject(ExpenseStoreService);
  private readonly salary = inject(SalaryStoreService);
  private readonly inventory = inject(InventoryStoreService);
  private readonly royalty = inject(RoyaltyStoreService);

  view: ViewMode = 'monthly';
  month = this.currentMonth();
  year = new Date().getFullYear();
  years = this.buildYears();

  scope!: PnlPeriod;
  breakdown: PnlLine[] = [];
  months: PnlPeriod[] = [];
  yearTotal!: PnlPeriod;

  private sub?: Subscription;

  ngOnInit(): void {
    this.recompute();
    // Recompute whenever any underlying module's data changes.
    this.sub = merge(
      this.sales.changes$,
      this.expenses.changes$,
      this.salary.changes$,
      this.inventory.changes$,
      this.royalty.changes$
    ).subscribe(() => this.recompute());
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  onViewChange(value: ViewMode): void {
    this.view = value;
    this.recompute();
  }

  recompute(): void {
    if (this.view === 'monthly') {
      this.scope = this.pnl.computeMonth(this.month);
      this.months = [];
    } else {
      this.months = this.pnl.computeYear(this.year);
      this.yearTotal = this.pnl.computeYearTotal(this.year);
      this.scope = this.yearTotal;
    }
    this.breakdown = this.toBreakdown(this.scope);
  }

  private toBreakdown(p: PnlPeriod): PnlLine[] {
    return [
      { label: 'Revenue', amount: p.revenue, kind: 'revenue' },
      { label: '− Raw Material', amount: p.rawMaterial, kind: 'cost' },
      { label: '− Salary', amount: p.salary, kind: 'cost' },
      { label: '− Rent', amount: p.rent, kind: 'cost' },
      { label: '− Electricity', amount: p.electricity, kind: 'cost' },
      { label: '− Royalty', amount: p.royalty, kind: 'cost' },
      { label: '− Other', amount: p.otherExpenses, kind: 'cost' },
      { label: '= Net Profit', amount: p.netProfit, kind: 'total' }
    ];
  }

  private currentMonth(): string {
    return new Date().toISOString().slice(0, 7);
  }

  private buildYears(): number[] {
    const current = new Date().getFullYear();
    const list: number[] = [];
    for (let y = current; y >= current - 4; y--) {
      list.push(y);
    }
    return list;
  }
}
