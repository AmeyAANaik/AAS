import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { MatIconModule } from '@angular/material/icon';
import { MockAuthService, ManagedUser } from '../mock-auth.service';
import { homeRouteForFeatures, roleLabel } from '../../core/rbac';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, MatIconModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css'
})
export class LoginComponent {
  readonly users: ManagedUser[];
  selectedId: string;

  constructor(private auth: MockAuthService, private router: Router) {
    this.users = this.auth.listUsers();
    this.selectedId = this.auth.currentUser()?.id ?? this.users[0]?.id ?? '';
  }

  roleLabel(role: ManagedUser['role']): string {
    return roleLabel(role);
  }

  select(id: string): void {
    this.selectedId = id;
  }

  signIn(): void {
    const user = this.auth.loginAs(this.selectedId);
    if (user) {
      const route = homeRouteForFeatures(this.auth.effectiveFeaturesFor(user));
      this.router.navigateByUrl(route);
    }
  }
}
