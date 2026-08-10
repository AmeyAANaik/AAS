import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { AuthTokenService } from '../shared/auth-token.service';

export type AccessFeatureDefinition = {
  key: string;
  label: string;
  group: string;
  description: string;
};

export type AccessManagedUser = {
  id: string;
  name: string;
  full_name?: string;
  email?: string;
  role?: string;
  enabled?: number | boolean;
  allow_features?: string[];
  deny_features?: string[];
  default_features?: string[];
  features?: string[];
};

export type AccessOverview = {
  features: AccessFeatureDefinition[];
  users: AccessManagedUser[];
  /** Baseline features per role, so the create form can preview them before the user exists. */
  defaultsByRole?: Record<string, string[]>;
};

export type UserCreatePayload = {
  email: string;
  fullName: string;
  password: string;
  role: string;
  mobileNo?: string;
  location?: string;
  company?: string;
  /** Required for the vendor role. */
  supplier?: string;
  /** Required for the shop (branch) role. */
  customer?: string;
  allowFeatures?: string[];
  denyFeatures?: string[];
};

@Injectable({ providedIn: 'root' })
export class AccessControlService {
  constructor(
    private http: HttpClient,
    private tokenStore: AuthTokenService
  ) {}

  getOverview(): Observable<AccessOverview> {
    return this.http.get<AccessOverview>('/api/admin/access', { headers: this.authHeaders() });
  }

  updateUserAccess(userId: string, allowFeatures: string[], denyFeatures: string[]): Observable<AccessManagedUser> {
    return this.http.put<AccessManagedUser>(
      `/api/admin/access/users/${encodeURIComponent(userId)}`,
      { allowFeatures, denyFeatures },
      { headers: this.authHeaders() }
    );
  }

  createUser(payload: UserCreatePayload): Observable<AccessManagedUser> {
    return this.http.post<AccessManagedUser>('/api/admin/access/users', payload, {
      headers: this.authHeaders()
    });
  }

  setUserEnabled(userId: string, enabled: boolean): Observable<AccessManagedUser> {
    return this.http.put<AccessManagedUser>(
      `/api/admin/access/users/${encodeURIComponent(userId)}/enabled`,
      { enabled },
      { headers: this.authHeaders() }
    );
  }

  private authHeaders(): HttpHeaders {
    const token = this.tokenStore.getToken();
    return token ? new HttpHeaders({ Authorization: `Bearer ${token}` }) : new HttpHeaders();
  }
}
