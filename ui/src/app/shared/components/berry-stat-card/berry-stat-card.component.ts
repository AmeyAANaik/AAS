import { Component, Input } from '@angular/core';
import { CommonModule } from '@angular/common';
import { MatCardModule } from '@angular/material/card';
import { MatIconModule } from '@angular/material/icon';

export type CardType = 'primary' | 'success' | 'warning' | 'danger';

@Component({
  selector: 'app-berry-stat-card',
  standalone: true,
  imports: [CommonModule, MatCardModule, MatIconModule],
  template: `
    <mat-card class="stat-card" [class]="'stat-card-' + type">
      <div class="stat-top">
        <div class="stat-icon">
          <mat-icon>{{ icon }}</mat-icon>
        </div>
        <div class="stat-label">{{ label }}</div>
      </div>
      <div class="stat-value">{{ value }}</div>
      <div class="stat-change" [class.positive]="isPositive" [class.negative]="!isPositive">
        <span class="change-dot" aria-hidden="true"></span>
        <span>{{ changeText }}</span>
      </div>
    </mat-card>
  `,
  styleUrls: ['./berry-stat-card.component.css'],
})
export class BerryStatCardComponent {
  @Input() label: string = '';
  @Input() value: string = '';
  @Input() icon: string = '';
  @Input() type: CardType = 'primary';
  @Input() changeText: string = '';
  @Input() isPositive: boolean = true;
}
