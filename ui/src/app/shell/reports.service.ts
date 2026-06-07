import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { AuthTokenService } from '../shared/auth-token.service';

export interface ReportsCatalogItem {
  id: string;
  label: string;
  description: string;
  path: string;
  supportsExport: boolean;
  supportedGroupBy?: string[];
  defaultGroupBy?: string;
  supportedCompareBy?: string[];
  defaultCompareBy?: string;
}

@Injectable({
  providedIn: 'root'
})
export class ReportsService {
  constructor(private http: HttpClient, private tokenStore: AuthTokenService) {}

  getCatalog(): Observable<ReportsCatalogItem[]> {
    return this.http.get<ReportsCatalogItem[]>(`/api/reports/catalog`, {
      headers: this.authHeaders()
    });
  }

  runReport(path: string, params: { from?: string; to?: string; groupBy?: string; compareBy?: string } = {}): Observable<Array<Record<string, unknown>>> {
    return this.http.get<Array<Record<string, unknown>>>(`/api/reports/${path}`, {
      headers: this.authHeaders(),
      params: this.queryParams(params)
    });
  }

  exportReport(path: string, params: { from?: string; to?: string; groupBy?: string; compareBy?: string } = {}): Observable<Blob> {
    return this.http.get(`/api/reports/${path}/export`, {
      headers: this.authHeaders(),
      params: this.queryParams(params),
      responseType: 'blob'
    });
  }

  private authHeaders(): HttpHeaders {
    const token = this.tokenStore.getToken();
    if (!token) {
      return new HttpHeaders();
    }
    return new HttpHeaders({ Authorization: `Bearer ${token}` });
  }

  private queryParams(input: { from?: string; to?: string; groupBy?: string; compareBy?: string }): Record<string, string> {
    const params: Record<string, string> = {};
    if (input.from) {
      params['from'] = input.from;
    }
    if (input.to) {
      params['to'] = input.to;
    }
    if (input.groupBy) {
      params['groupBy'] = input.groupBy;
    }
    if (input.compareBy) {
      params['compareBy'] = input.compareBy;
    }
    return params;
  }
}
