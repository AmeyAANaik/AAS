import { CommonModule } from '@angular/common';
import { HttpClient } from '@angular/common/http';
import { Component, OnInit } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, Router, RouterModule } from '@angular/router';
import { AuthTokenService } from '../../shared/auth-token.service';

@Component({
  selector: 'app-login',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './login.component.html',
  styleUrl: './login.component.css'
})
export class LoginComponent implements OnInit {
  username = '';
  password = '';
  status = '';
  isLoading = false;
  hasToken = false;
  showForm = true;
  returnUrl = '/admin/dashboard';

  constructor(
    private http: HttpClient,
    private tokenStore: AuthTokenService,
    private router: Router,
    private route: ActivatedRoute
  ) {}

  ngOnInit(): void {
    this.returnUrl = this.route.snapshot.queryParamMap.get('returnUrl') || '/admin/dashboard';
    this.hasToken = !!this.tokenStore.getToken();
    this.showForm = !this.hasToken;
  }

  async login(): Promise<void> {
    if (!this.username || !this.password) {
      this.status = 'Enter your username and password.';
      return;
    }
    this.isLoading = true;
    this.status = 'Signing in...';
    try {
      const data = await this.http
        .post<{ accessToken: string }>(`/api/auth/login`, {
          username: this.username,
          password: this.password
        })
        .toPromise();
      const token = data?.accessToken ?? null;
      if (!token) {
        this.status = 'Login failed: no token returned.';
        return;
      }
      this.tokenStore.setToken(token);
      this.status = '';
      this.router.navigateByUrl(this.returnUrl);
    } catch (err) {
      this.status = 'Login failed. Check your credentials and try again.';
    } finally {
      this.isLoading = false;
    }
  }

  continue(): void {
    this.router.navigateByUrl(this.returnUrl);
  }

  signInDifferent(): void {
    this.tokenStore.setToken(null);
    this.hasToken = false;
    this.showForm = true;
    this.status = '';
    this.username = '';
    this.password = '';
  }
}
