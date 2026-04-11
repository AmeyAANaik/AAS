import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { AuthTokenService } from './auth-token.service';

export type UserIdentity = {
  id: string;
  name: string;
  full_name?: string;
  email?: string;
  mobile_no?: string;
  location?: string;
  customer?: string;
  supplier?: string;
  company?: string;
  enabled?: number | boolean;
  user_image?: string;
  role?: string;
};

@Injectable({ providedIn: 'root' })
export class UserContextService {
  constructor(private http: HttpClient, private tokenStore: AuthTokenService) {}

  getCurrentUser(): Observable<UserIdentity> {
    return this.http.get<UserIdentity>('/api/me', { headers: this.authHeaders() });
  }

  updateCurrentUser(fields: Partial<UserIdentity>): Observable<UserIdentity> {
    return this.http.put<UserIdentity>(
      '/api/me',
      { fields },
      { headers: this.authHeaders() }
    );
  }

  getUser(id: string): Observable<UserIdentity> {
    return this.http.get<UserIdentity>(`/api/users/${encodeURIComponent(id)}`, { headers: this.authHeaders() });
  }

  updateUser(id: string, fields: Partial<UserIdentity>): Observable<UserIdentity> {
    return this.http.put<UserIdentity>(
      `/api/users/${encodeURIComponent(id)}`,
      { fields },
      { headers: this.authHeaders() }
    );
  }

  private authHeaders(): HttpHeaders {
    const token = this.tokenStore.getToken();
    return token ? new HttpHeaders({ Authorization: `Bearer ${token}` }) : new HttpHeaders();
  }
}
