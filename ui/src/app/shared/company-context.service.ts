import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { AuthTokenService } from './auth-token.service';

export type CompanyIdentity = {
  id: string;
  name: string;
  abbr?: string;
  default_currency?: string;
  country?: string;
  default_letter_head?: string;
  tax_id?: string;
  logo_url?: string;
};

export type BranchIdentity = {
  id: string;
  name: string;
  location?: string;
  whatsapp_group?: string;
  credit_days?: number;
  logo_url?: string;
};

export type CompanyContext = {
  company: CompanyIdentity | null;
  branch: BranchIdentity | null;
  companies: Array<{ name: string; abbr?: string; default_currency?: string }>;
  branches: Array<{ name: string; customer_name?: string; aas_branch_location?: string }>;
};

@Injectable({ providedIn: 'root' })
export class CompanyContextService {
  constructor(private http: HttpClient, private tokenStore: AuthTokenService) {}

  getContext(): Observable<CompanyContext> {
    return this.http.get<CompanyContext>('/api/company-context', { headers: this.authHeaders() });
  }

  getCompany(id: string): Observable<CompanyIdentity> {
    return this.http.get<CompanyIdentity>(`/api/companies/${encodeURIComponent(id)}`, { headers: this.authHeaders() });
  }

  updateCompany(id: string, fields: Partial<CompanyIdentity>): Observable<CompanyIdentity> {
    return this.http.put<CompanyIdentity>(
      `/api/companies/${encodeURIComponent(id)}`,
      { fields },
      { headers: this.authHeaders() }
    );
  }

  private authHeaders(): HttpHeaders {
    const token = this.tokenStore.getToken();
    return token ? new HttpHeaders({ Authorization: `Bearer ${token}` }) : new HttpHeaders();
  }
}
