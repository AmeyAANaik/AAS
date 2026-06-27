import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { PageHeaderComponent } from '../../shared/page-header/page-header.component';
import { MockAuthService, ManagedUser } from '../../auth/mock-auth.service';
import { FEATURES, FeatureKey, featuresForRole, roleLabel } from '../../core/rbac';

type Override = 'default' | 'allow' | 'deny';

interface FeatureRow {
  key: FeatureKey;
  label: string;
  group: string;
  inRole: boolean;
  state: Override;
}

@Component({
  selector: 'app-access-control',
  standalone: true,
  imports: [CommonModule, MatButtonModule, MatButtonToggleModule, MatIconModule, MatSnackBarModule, PageHeaderComponent],
  templateUrl: './access-control.component.html',
  styleUrl: './access-control.component.css'
})
export class AccessControlComponent {
  users: ManagedUser[] = [];
  selected: ManagedUser | null = null;
  rows: FeatureRow[] = [];
  groups: string[] = [];

  constructor(private auth: MockAuthService, private snack: MatSnackBar) {
    this.users = this.auth.listUsers();
    this.groups = [...new Set(FEATURES.map(f => f.group))];
    if (this.users.length) {
      this.select(this.users[0]);
    }
  }

  roleLabel = roleLabel;

  select(user: ManagedUser): void {
    this.selected = user;
    const roleFeatures = featuresForRole(user.role);
    this.rows = FEATURES.map(f => {
      const inRole = roleFeatures.includes(f.key);
      let state: Override = 'default';
      if (user.allow.includes(f.key)) {
        state = 'allow';
      } else if (user.deny.includes(f.key)) {
        state = 'deny';
      }
      return { key: f.key, label: f.label, group: f.group, inRole, state };
    });
  }

  rowsForGroup(group: string): FeatureRow[] {
    return this.rows.filter(r => r.group === group);
  }

  effective(row: FeatureRow): boolean {
    if (row.state === 'allow') {
      return true;
    }
    if (row.state === 'deny') {
      return false;
    }
    return row.inRole;
  }

  setState(row: FeatureRow, state: Override): void {
    row.state = state;
  }

  save(): void {
    if (!this.selected) {
      return;
    }
    const allow = this.rows.filter(r => r.state === 'allow').map(r => r.key);
    const deny = this.rows.filter(r => r.state === 'deny').map(r => r.key);
    this.auth.updateOverrides(this.selected.id, allow, deny);
    // Refresh the local copy so re-selecting reflects saved state.
    this.users = this.auth.listUsers();
    this.selected = this.users.find(u => u.id === this.selected!.id) ?? null;
    this.snack.open('Access updated', 'OK', { duration: 2200 });
  }
}
