import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { AuthTokenService } from '../shared/auth-token.service';
import { Vendor } from './vendor.model';

@Injectable({
  providedIn: 'root'
})
export class VendorService {
  constructor(private http: HttpClient, private tokenStore: AuthTokenService) {}

  listVendors(): Observable<any[]> {
    return this.http.get<any[]>('/api/vendors', { headers: this.authHeaders() });
  }

  createVendor(fields: Record<string, unknown>): Observable<unknown> {
    return this.http.post('/api/vendors', { fields }, { headers: this.authHeaders() });
  }

  private authHeaders(): HttpHeaders {
    const token = this.tokenStore.getToken();
    if (!token) {
      return new HttpHeaders();
    }
    return new HttpHeaders({ Authorization: `Bearer ${token}` });
  }
}
