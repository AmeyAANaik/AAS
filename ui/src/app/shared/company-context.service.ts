import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, Subject } from 'rxjs';
import { tap } from 'rxjs/operators';
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
  signature_url?: string;
  bank_beneficiary_name?: string;
  bank_name?: string;
  bank_account_number?: string;
  bank_ifsc_code?: string;
  bank_branch?: string;
  invoice_email?: string;
  whatsapp_number?: string;
};

export type BranchIdentity = {
  id: string;
  name: string;
  location?: string;
  whatsapp_group?: string;
  invoice_email?: string;
  whatsapp_number?: string;
  credit_days?: number;
  logo_url?: string;
};

export type CompanyContext = {
  company: CompanyIdentity | null;
  branch: BranchIdentity | null;
  companies: Array<{ name: string; abbr?: string; default_currency?: string }>;
  branches: Array<{ name: string; customer_name?: string; aas_branch_location?: string }>;
};

export type OpeningBalancePreview = {
  companyId: string;
  cutoverDate: string;
  isValid: boolean;
  errors: Array<{ row: number; field: string; message: string }>;
  summary?: Record<string, unknown>;
  planned?: Record<string, unknown>;
};

export type OpeningBalanceApplyResult = {
  companyId: string;
  cutoverDate: string;
  created: Record<string, unknown>;
};

@Injectable({ providedIn: 'root' })
export class CompanyContextService {
  readonly contextChanged$ = new Subject<void>();

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
    ).pipe(tap(() => this.contextChanged$.next()));
  }

  updateShop(id: string, fields: Record<string, unknown>): Observable<unknown> {
    return this.http.put(
      `/api/shops/${encodeURIComponent(id)}`,
      { fields },
      { headers: this.authHeaders() }
    ).pipe(tap(() => this.contextChanged$.next()));
  }

  uploadCompanyLogo(id: string, file: File): Observable<CompanyIdentity> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<CompanyIdentity>(
      `/api/companies/${encodeURIComponent(id)}/logo`,
      formData,
      { headers: this.authHeaders() }
    ).pipe(tap(() => this.contextChanged$.next()));
  }

  uploadCompanySignature(id: string, file: File): Observable<CompanyIdentity> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<CompanyIdentity>(
      `/api/companies/${encodeURIComponent(id)}/signature`,
      formData,
      { headers: this.authHeaders() }
    ).pipe(tap(() => this.contextChanged$.next()));
  }

  previewOpeningBalances(companyId: string, file: File, cutoverDate: string): Observable<OpeningBalancePreview> {
    const formData = new FormData();
    formData.append('file', file, file.name);
    formData.append('cutoverDate', cutoverDate);
    return this.http.post<OpeningBalancePreview>(
      `/api/companies/${encodeURIComponent(companyId)}/opening-balances/preview`,
      formData,
      { headers: this.authHeaders() }
    );
  }

  applyOpeningBalances(companyId: string, file: File, cutoverDate: string): Observable<OpeningBalanceApplyResult> {
    const formData = new FormData();
    formData.append('file', file, file.name);
    formData.append('cutoverDate', cutoverDate);
    return this.http.post<OpeningBalanceApplyResult>(
      `/api/companies/${encodeURIComponent(companyId)}/opening-balances/apply`,
      formData,
      { headers: this.authHeaders() }
    );
  }

  private authHeaders(): HttpHeaders {
    const token = this.tokenStore.getToken();
    return token ? new HttpHeaders({ Authorization: `Bearer ${token}` }) : new HttpHeaders();
  }
}
