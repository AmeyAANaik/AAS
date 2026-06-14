import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { AuthTokenService } from '../shared/auth-token.service';

export interface AnalyticsColumn {
  id: string;
  label: string;
  colType: 'DIMENSION' | 'CURRENCY' | 'PERCENT' | 'NUMBER';
}

export interface AnalyticsKpi {
  id: string;
  label: string;
  value: number;
  valueType: 'CURRENCY' | 'PERCENT' | 'NUMBER';
}

export interface AnalyticsQueryRequest {
  dateFrom: string;
  dateTo: string;
  granularity: string;
  dimensions: string[];
  metrics: string[];
  filters: Record<string, string>;
}

export interface AnalyticsQueryResponse {
  columns: AnalyticsColumn[];
  rows: Record<string, unknown>[];
  totalsRow: Record<string, unknown>;
  kpis: AnalyticsKpi[];
}

@Injectable({ providedIn: 'root' })
export class AnalyticsService {
  constructor(private http: HttpClient, private tokenStore: AuthTokenService) {}

  query(req: AnalyticsQueryRequest): Observable<AnalyticsQueryResponse> {
    return this.http.post<AnalyticsQueryResponse>('/api/analytics/query', req, {
      headers: this.authHeaders()
    });
  }

  export(req: AnalyticsQueryRequest): Observable<Blob> {
    return this.http.post('/api/analytics/query/export', req, {
      headers: this.authHeaders(),
      responseType: 'blob'
    });
  }

  private authHeaders(): HttpHeaders {
    const token = this.tokenStore.getToken();
    return token ? new HttpHeaders({ Authorization: `Bearer ${token}` }) : new HttpHeaders();
  }
}
