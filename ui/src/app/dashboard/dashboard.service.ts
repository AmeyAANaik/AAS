import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, timeout } from 'rxjs';
import { AuthTokenService } from '../shared/auth-token.service';
import { DashboardSnapshot } from './dashboard.model';

@Injectable({
  providedIn: 'root'
})
export class DashboardService {
  constructor(private http: HttpClient, private tokenStore: AuthTokenService) {}

  getDashboardSnapshot(): Observable<DashboardSnapshot> {
    const today = new Date();
    const rangeStart = this.formatDate(new Date(today.getFullYear(), today.getMonth(), 1));
    const rangeEnd = this.formatDate(today);

    const headers = this.authHeaders();
    const params = new HttpParams().set('from', rangeStart).set('to', rangeEnd);

    // Metrics are computed server-side; the UI just renders the snapshot.
    return this.http
      .get<DashboardSnapshot>(`/api/dashboard/summary`, { headers, params })
      .pipe(timeout({ first: 15000 }));
  }

  private authHeaders(): HttpHeaders {
    const token = this.tokenStore.getToken();
    if (!token) {
      return new HttpHeaders();
    }
    return new HttpHeaders({ Authorization: `Bearer ${token}` });
  }

  private formatDate(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }
}
