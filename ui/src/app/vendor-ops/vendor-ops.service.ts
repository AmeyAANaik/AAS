import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { AuthTokenService } from '../shared/auth-token.service';
import { VendorOpsAnalytics, VendorOpsDetail, VendorOpsLedgerEntry, VendorOpsOrderRow, VendorOpsSummaryRow, VendorOpsSummaryTotals } from './vendor-ops.model';

@Injectable({ providedIn: 'root' })
export class VendorOpsService {
  constructor(private http: HttpClient, private tokenStore: AuthTokenService) {}

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

  getVendorLedger(vendorId: string): Observable<{ balance: number; entries: VendorOpsLedgerEntry[] }> {
    return this.http.get<{ balance: number; entries: VendorOpsLedgerEntry[] }>(`/api/vendor-ops/${encodeURIComponent(vendorId)}/ledger`, {
      headers: this.authHeaders()
    });
  }

  private authHeaders(): HttpHeaders {
    const token = this.tokenStore.getToken();
    return token ? new HttpHeaders({ Authorization: `Bearer ${token}` }) : new HttpHeaders();
  }
}
