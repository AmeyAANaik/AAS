import { CommonModule } from '@angular/common';
import { Component, OnDestroy, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatTableModule } from '@angular/material/table';
import { Subscription } from 'rxjs';
import { EmptyStateComponent } from '../../shared/empty-state/empty-state.component';
import { StatusPillComponent } from '../../shared/status-pill/status-pill.component';
import { RoyaltyEntry, RoyaltyStatus } from '../royalty.model';
import { RoyaltyStoreService } from '../royalty-store.service';

type StatusFilter = 'All' | RoyaltyStatus;

@Component({
  selector: 'app-royalty-reports',
  standalone: true,
  imports: [
    CommonModule, FormsModule, MatButtonToggleModule, MatFormFieldModule, MatIconModule,
    MatInputModule, MatTableModule, EmptyStateComponent, StatusPillComponent
  ],
  templateUrl: './royalty-reports.component.html',
  styleUrl: './royalty-reports.component.css'
})
export class RoyaltyReportsComponent implements OnInit, OnDestroy {
  filter: StatusFilter = 'All';

  /** yyyy for the per-year monthly view. */
  selectedYear = new Date().toISOString().slice(0, 4);

  all: RoyaltyEntry[] = [];
  rows: RoyaltyEntry[] = [];

  readonly columns = ['month', 'base', 'rate', 'due', 'paid', 'outstanding', 'status'];

  private sub?: Subscription;

  constructor(private store: RoyaltyStoreService) {}

  ngOnInit(): void {
    this.all = this.store.listSnapshot();
    this.rebuild();
    this.sub = this.store.changes$.subscribe(() => {
      this.all = this.store.listSnapshot();
      this.rebuild();
    });
  }

  ngOnDestroy(): void {
    this.sub?.unsubscribe();
  }

  get years(): string[] {
    const set = new Set<string>(this.all.map(e => e.month.slice(0, 4)));
    set.add(this.selectedYear);
    return [...set].sort((a, b) => b.localeCompare(a));
  }

  // Totals across all entries, by status.
  get pendingTotal(): number {
    return this.sumOutstanding(this.all.filter(e => e.status !== 'Paid'));
  }

  get paidTotal(): number {
    return this.round(this.all.reduce((s, e) => s + e.paidAmount, 0));
  }

  get dueTotal(): number {
    return this.round(this.all.reduce((s, e) => s + e.dueAmount, 0));
  }

  get pendingCount(): number {
    return this.all.filter(e => e.status !== 'Paid').length;
  }

  outstandingFor(e: RoyaltyEntry): number {
    return this.round(Math.max(0, e.dueAmount - e.paidAmount));
  }

  setFilter(filter: StatusFilter): void {
    this.filter = filter;
    this.rebuild();
  }

  onYearChange(value: string): void {
    if (value) {
      this.selectedYear = value.slice(0, 4);
    }
    this.rebuild();
  }

  private rebuild(): void {
    let scoped = this.all.filter(e => e.month.startsWith(this.selectedYear));
    if (this.filter !== 'All') {
      scoped = scoped.filter(e => e.status === this.filter);
    }
    this.rows = scoped.sort((a, b) => a.month.localeCompare(b.month));
  }

  private sumOutstanding(entries: RoyaltyEntry[]): number {
    return this.round(entries.reduce((s, e) => s + Math.max(0, e.dueAmount - e.paidAmount), 0));
  }

  private round(n: number): number {
    return Math.round(n * 100) / 100;
  }
}
