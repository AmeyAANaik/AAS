import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';

@Component({
  selector: 'app-berry-chart-card',
  standalone: true,
  imports: [CommonModule, MatCardModule],
  template: `
    <mat-card class="chart-card">
      <div class="chart-header">
        <h3 class="chart-title">{{ title }}</h3>
        <button class="chart-menu">⋮</button>
      </div>
      <ng-content></ng-content>
      <div class="chart-footer" *ngIf="stats && stats.length">
        <div class="chart-stat" *ngFor="let stat of stats">
          <div class="chart-stat-value">{{ stat.value }}</div>
          <div class="chart-stat-label">{{ stat.label }}</div>
        </div>
      </div>
    </mat-card>
  `,
  styleUrls: ['./berry-chart-card.component.css'],
})
export class BerryChartCardComponent {
  @Input() title: string = '';
  @Input() stats: Array<{ value: string; label: string }> = [];
}
