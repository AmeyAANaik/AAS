import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { AuthTokenService } from '../shared/auth-token.service';
import { BranchOpsAnalytics, BranchOpsDetail, BranchOpsLedgerEntry, BranchOpsOrderRow, BranchOpsSummaryRow, BranchOpsSummaryTotals } from './branch-ops.model';

@Injectable({ providedIn: 'root' })
export class BranchOpsService {
  constructor(private http: HttpClient, private tokenStore: AuthTokenService) {}

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

  getBranchLedger(branchId: string): Observable<{ balance: number; entries: BranchOpsLedgerEntry[] }> {
    return this.http.get<{ balance: number; entries: BranchOpsLedgerEntry[] }>(`/api/branch-ops/${encodeURIComponent(branchId)}/ledger`, {
      headers: this.authHeaders()
    });
  }

  private authHeaders(): HttpHeaders {
    const token = this.tokenStore.getToken();
    return token ? new HttpHeaders({ Authorization: `Bearer ${token}` }) : new HttpHeaders();
  }
}
