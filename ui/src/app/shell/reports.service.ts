import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { AuthTokenService } from '../shared/auth-token.service';

@Injectable({
  providedIn: 'root'
})
export class ReportsService {
  constructor(private http: HttpClient, private tokenStore: AuthTokenService) {}

  runReport(reportType: string, month: string): Observable<Array<Record<string, unknown>>> {
    const params = new HttpParams().set('month', month);
    return this.http.get<Array<Record<string, unknown>>>(`/api/reports/${reportType}`, {
      headers: this.authHeaders(),
      params
    });
  }

  exportReport(reportType: string, month: string): Observable<Blob> {
    const params = new HttpParams().set('month', month);
    return this.http.get(`/api/reports/${reportType}/export`, {
      headers: this.authHeaders(),
      params,
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
}
