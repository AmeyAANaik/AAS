import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { AuthTokenService } from '../shared/auth-token.service';
import { OrderCreatePayload, OrderFilters, OrderSummary } from './order.model';

@Injectable({
  providedIn: 'root'
})
export class OrderService {
  constructor(private http: HttpClient, private tokenStore: AuthTokenService) {}

  listOrders(filters: OrderFilters): Observable<OrderSummary[]> {
    const headers = this.authHeaders();
    let params = new HttpParams();
    if (filters.customer) {
      params = params.set('customer', filters.customer);
    }
    if (filters.vendor) {
      params = params.set('vendor', filters.vendor);
    }
    if (filters.status) {
      params = params.set('status', filters.status);
    }
    if (filters.from) {
      params = params.set('from', filters.from);
    }
    if (filters.to) {
      params = params.set('to', filters.to);
    }
    return this.http.get<OrderSummary[]>('/api/orders', { headers, params });
  }

  createOrder(payload: OrderCreatePayload): Observable<Record<string, unknown>> {
    return this.http.post<Record<string, unknown>>(
      '/api/orders',
      { fields: payload },
      { headers: this.authHeaders() }
    );
  }

  assignVendor(orderId: string, vendorId: string): Observable<unknown> {
    return this.http.post(
      `/api/orders/${orderId}/assign-vendor`,
      { fields: { aas_vendor: vendorId } },
      { headers: this.authHeaders() }
    );
  }

  updateStatus(orderId: string, status: string): Observable<unknown> {
    return this.http.post(
      `/api/orders/${orderId}/status`,
      { fields: { aas_status: status } },
      { headers: this.authHeaders() }
    );
  }

  uploadOrderImage(orderId: string, file: File): Observable<Record<string, unknown>> {
    const formData = new FormData();
    formData.append('file', file, file.name);
    return this.http.post<Record<string, unknown>>(`/api/orders/${orderId}/image`, formData, {
      headers: this.authHeaders()
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
