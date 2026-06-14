import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { AuthTokenService } from '../shared/auth-token.service';
import { VendorOpsAnalytics, VendorOpsCategorySummaryRow, VendorOpsDetail, VendorOpsLedgerEntry, VendorOpsOrderRow, VendorOpsSummaryRow, VendorOpsSummaryTotals } from './vendor-ops.model';

export type ExportFormat = 'csv' | 'xlsx' | 'pdf';

@Injectable({ providedIn: 'root' })
export class VendorOpsService {
  constructor(private http: HttpClient, private tokenStore: AuthTokenService) {}

  private withDateRange(params: HttpParams, range?: { from?: string; to?: string }): HttpParams {
    let next = params;
    if (range?.from) {
      next = next.set('from', range.from);
    }
    if (range?.to) {
      next = next.set('to', range.to);
    }
    return next;
  }

  getSummary(): Observable<{ totals: VendorOpsSummaryTotals; vendors: VendorOpsSummaryRow[] }> {
    return this.http.get<{ totals: VendorOpsSummaryTotals; vendors: VendorOpsSummaryRow[] }>('/api/vendor-ops/summary', {
      headers: this.authHeaders()
    });
  }

  getVendorDetail(vendorId: string): Observable<VendorOpsDetail> {
    return this.http.get<VendorOpsDetail>(`/api/vendor-ops/${encodeURIComponent(vendorId)}`, {
      headers: this.authHeaders()
    });
  }

  getVendorOrders(vendorId: string, filters?: { status?: string; branch?: string; from?: string; to?: string }): Observable<VendorOpsOrderRow[]> {
    let params = new HttpParams();
    if (filters?.status) {
      params = params.set('status', filters.status);
    }
    if (filters?.branch) {
      params = params.set('branch', filters.branch);
    }
    if (filters?.from) {
      params = params.set('from', filters.from);
    }
    if (filters?.to) {
      params = params.set('to', filters.to);
    }
    return this.http.get<VendorOpsOrderRow[]>(`/api/vendor-ops/${encodeURIComponent(vendorId)}/orders`, {
      headers: this.authHeaders(),
      params
    });
  }

  getVendorAnalytics(vendorId: string): Observable<VendorOpsAnalytics> {
    return this.http.get<VendorOpsAnalytics>(`/api/vendor-ops/${encodeURIComponent(vendorId)}/analytics`, {
      headers: this.authHeaders()
    });
  }

  getVendorLedger(
    vendorId: string,
    range?: { from?: string; to?: string }
  ): Observable<{ openingBalance?: number; closingBalance?: number; balance: number; entries: VendorOpsLedgerEntry[]; categorySummary: VendorOpsCategorySummaryRow[] }> {
    const params = this.withDateRange(new HttpParams(), range);
    return this.http.get<{ openingBalance?: number; closingBalance?: number; balance: number; entries: VendorOpsLedgerEntry[]; categorySummary: VendorOpsCategorySummaryRow[] }>(
      `/api/vendor-ops/${encodeURIComponent(vendorId)}/ledger`,
      { headers: this.authHeaders(), params }
    );
  }

  downloadAllVendorLedgers(format: ExportFormat = 'csv'): Observable<Blob> {
    return this.http.get('/api/vendor-ops/ledger/export', {
      headers: this.authHeaders(),
      params: new HttpParams().set('format', format),
      responseType: 'blob'
    });
  }

  downloadVendorLedger(vendorId: string, range?: { from?: string; to?: string }, format: ExportFormat = 'csv'): Observable<Blob> {
    let params = this.withDateRange(new HttpParams(), range);
    params = params.set('format', format);
    return this.http.get(`/api/vendor-ops/${encodeURIComponent(vendorId)}/ledger/export`, {
      headers: this.authHeaders(),
      params,
      responseType: 'blob'
    });
  }

  getVendorLedgerByCategory(
    vendorId: string,
    categoryId: string,
    range?: { from?: string; to?: string }
  ): Observable<{ openingBalance?: number; closingBalance?: number; balance: number; entries: VendorOpsLedgerEntry[]; categoryId: string; categoryLabel: string }> {
    let params = new HttpParams().set('categoryId', categoryId);
    params = this.withDateRange(params, range);
    return this.http.get<{ openingBalance?: number; closingBalance?: number; balance: number; entries: VendorOpsLedgerEntry[]; categoryId: string; categoryLabel: string }>(
      `/api/vendor-ops/${encodeURIComponent(vendorId)}/ledger/category`,
      { headers: this.authHeaders(), params }
    );
  }

  downloadVendorLedgerByCategory(vendorId: string, categoryId: string, range?: { from?: string; to?: string }, format: ExportFormat = 'csv'): Observable<Blob> {
    let params = new HttpParams().set('categoryId', categoryId);
    params = this.withDateRange(params, range);
    params = params.set('format', format);
    return this.http.get(`/api/vendor-ops/${encodeURIComponent(vendorId)}/ledger/category/export`, {
      headers: this.authHeaders(),
      params,
      responseType: 'blob'
    });
  }

  downloadVendorLedgerCategoriesSummary(vendorId: string, range?: { from?: string; to?: string }, format: ExportFormat = 'csv'): Observable<Blob> {
    let params = this.withDateRange(new HttpParams(), range);
    params = params.set('format', format);
    return this.http.get(`/api/vendor-ops/${encodeURIComponent(vendorId)}/ledger/categories/export`, {
      headers: this.authHeaders(),
      params,
      responseType: 'blob'
    });
  }

  downloadAllVendorsLedgerCategoriesSummary(range?: { from?: string; to?: string }, format: ExportFormat = 'csv'): Observable<Blob> {
    let params = this.withDateRange(new HttpParams(), range);
    params = params.set('format', format);
    return this.http.get('/api/vendor-ops/ledger/categories/export', {
      headers: this.authHeaders(),
      params,
      responseType: 'blob'
    });
  }

  private authHeaders(): HttpHeaders {
    const token = this.tokenStore.getToken();
    return token ? new HttpHeaders({ Authorization: `Bearer ${token}` }) : new HttpHeaders();
  }
}
