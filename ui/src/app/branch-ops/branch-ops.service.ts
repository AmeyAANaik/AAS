import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { AuthTokenService } from '../shared/auth-token.service';
import { BranchOpsAnalytics, BranchOpsCategorySummaryRow, BranchOpsDetail, BranchOpsLedgerEntry, BranchOpsOrderRow, BranchOpsSummaryRow, BranchOpsSummaryTotals } from './branch-ops.model';

export type ExportFormat = 'csv' | 'xlsx' | 'pdf';

@Injectable({ providedIn: 'root' })
export class BranchOpsService {
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

  getSummary(): Observable<{ totals: BranchOpsSummaryTotals; branches: BranchOpsSummaryRow[] }> {
    return this.http.get<{ totals: BranchOpsSummaryTotals; branches: BranchOpsSummaryRow[] }>('/api/branch-ops/summary', {
      headers: this.authHeaders()
    });
  }

  getBranchDetail(branchId: string): Observable<BranchOpsDetail> {
    return this.http.get<BranchOpsDetail>(`/api/branch-ops/${encodeURIComponent(branchId)}`, {
      headers: this.authHeaders()
    });
  }

  getBranchOrders(branchId: string, filters?: { status?: string; vendor?: string; from?: string; to?: string }): Observable<BranchOpsOrderRow[]> {
    let params = new HttpParams();
    if (filters?.status) {
      params = params.set('status', filters.status);
    }
    if (filters?.vendor) {
      params = params.set('vendor', filters.vendor);
    }
    if (filters?.from) {
      params = params.set('from', filters.from);
    }
    if (filters?.to) {
      params = params.set('to', filters.to);
    }
    return this.http.get<BranchOpsOrderRow[]>(`/api/branch-ops/${encodeURIComponent(branchId)}/orders`, {
      headers: this.authHeaders(),
      params
    });
  }

  getBranchAnalytics(branchId: string): Observable<BranchOpsAnalytics> {
    return this.http.get<BranchOpsAnalytics>(`/api/branch-ops/${encodeURIComponent(branchId)}/analytics`, {
      headers: this.authHeaders()
    });
  }

  getBranchLedger(
    branchId: string,
    range?: { from?: string; to?: string }
  ): Observable<{ openingBalance?: number; closingBalance?: number; balance: number; entries: BranchOpsLedgerEntry[]; categorySummary: BranchOpsCategorySummaryRow[] }> {
    const params = this.withDateRange(new HttpParams(), range);
    return this.http.get<{ openingBalance?: number; closingBalance?: number; balance: number; entries: BranchOpsLedgerEntry[]; categorySummary: BranchOpsCategorySummaryRow[] }>(
      `/api/branch-ops/${encodeURIComponent(branchId)}/ledger`,
      { headers: this.authHeaders(), params }
    );
  }

  downloadAllBranchLedgers(format: ExportFormat = 'csv'): Observable<Blob> {
    return this.http.get('/api/branch-ops/ledger/export', {
      headers: this.authHeaders(),
      params: new HttpParams().set('format', format),
      responseType: 'blob'
    });
  }

  downloadBranchLedger(branchId: string, range?: { from?: string; to?: string }, format: ExportFormat = 'csv'): Observable<Blob> {
    let params = this.withDateRange(new HttpParams(), range);
    params = params.set('format', format);
    return this.http.get(`/api/branch-ops/${encodeURIComponent(branchId)}/ledger/export`, {
      headers: this.authHeaders(),
      params,
      responseType: 'blob'
    });
  }

  getBranchLedgerByCategory(
    branchId: string,
    categoryId: string,
    range?: { from?: string; to?: string }
  ): Observable<{ openingBalance?: number; closingBalance?: number; balance: number; entries: BranchOpsLedgerEntry[]; categoryId: string; categoryLabel: string }> {
    let params = new HttpParams().set('categoryId', categoryId);
    params = this.withDateRange(params, range);
    return this.http.get<{ openingBalance?: number; closingBalance?: number; balance: number; entries: BranchOpsLedgerEntry[]; categoryId: string; categoryLabel: string }>(
      `/api/branch-ops/${encodeURIComponent(branchId)}/ledger/category`,
      { headers: this.authHeaders(), params }
    );
  }

  downloadBranchLedgerByCategory(branchId: string, categoryId: string, range?: { from?: string; to?: string }, format: ExportFormat = 'csv'): Observable<Blob> {
    let params = new HttpParams().set('categoryId', categoryId);
    params = this.withDateRange(params, range);
    params = params.set('format', format);
    return this.http.get(`/api/branch-ops/${encodeURIComponent(branchId)}/ledger/category/export`, {
      headers: this.authHeaders(),
      params,
      responseType: 'blob'
    });
  }

  downloadBranchLedgerCategoriesSummary(branchId: string, range?: { from?: string; to?: string }, format: ExportFormat = 'csv'): Observable<Blob> {
    let params = this.withDateRange(new HttpParams(), range);
    params = params.set('format', format);
    return this.http.get(`/api/branch-ops/${encodeURIComponent(branchId)}/ledger/categories/export`, {
      headers: this.authHeaders(),
      params,
      responseType: 'blob'
    });
  }

  downloadAllBranchesLedgerCategoriesSummary(range?: { from?: string; to?: string }, format: ExportFormat = 'csv'): Observable<Blob> {
    let params = this.withDateRange(new HttpParams(), range);
    params = params.set('format', format);
    return this.http.get('/api/branch-ops/ledger/categories/export', {
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
