import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { AuthTokenService } from '../shared/auth-token.service';
import { OrderCreatePayload, OrderFilters, OrderItemPayload, OrderSummary, SellPreview, VendorBillPayload } from './order.model';

@Injectable({
  providedIn: 'root'
})
export class OrderService {
  constructor(private http: HttpClient, private tokenStore: AuthTokenService) {}

  listBranches(): Observable<any[]> {
    return this.http.get<any[]>('/api/shops', { headers: this.authHeaders() });
  }

  listCompanies(): Observable<any[]> {
    return this.http.get<any[]>('/api/companies', { headers: this.authHeaders() });
  }

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

  createOrderFromBranchImage(
    file: File,
    payload: { customer?: string; company: string; transaction_date?: string; delivery_date?: string }
  ): Observable<Record<string, unknown>> {
    const formData = new FormData();
    formData.append('file', file, file.name);
    if (payload.customer) {
      formData.append('customer', payload.customer);
    }
    if (payload.company) {
      formData.append('company', payload.company);
    }
    if (payload.transaction_date) {
      formData.append('transaction_date', payload.transaction_date);
    }
    if (payload.delivery_date) {
      formData.append('delivery_date', payload.delivery_date);
    }
    return this.http.post<Record<string, unknown>>('/api/orders/branch-image', formData, {
      headers: this.authHeaders()
    });
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

  uploadVendorPdf(orderId: string, file: File): Observable<Record<string, unknown>> {
    const formData = new FormData();
    formData.append('file', file, file.name);
    return this.http.post<Record<string, unknown>>(`/api/orders/${orderId}/vendor-pdf`, formData, {
      headers: this.authHeaders()
    });
  }

  captureVendorBill(orderId: string, payload: VendorBillPayload): Observable<Record<string, unknown>> {
    return this.http.post<Record<string, unknown>>(
      `/api/orders/${orderId}/vendor-bill`,
      { fields: payload },
      { headers: this.authHeaders() }
    );
  }

  deleteOrder(orderId: string): Observable<Record<string, unknown>> {
    return this.http.delete<Record<string, unknown>>(`/api/orders/${orderId}`, { headers: this.authHeaders() });
  }

  getSellPreview(orderId: string): Observable<SellPreview> {
    return this.http.get<SellPreview>(`/api/orders/${orderId}/sell-preview`, { headers: this.authHeaders() });
  }

  createSellOrder(orderId: string): Observable<Record<string, unknown>> {
    return this.http.post<Record<string, unknown>>(`/api/orders/${orderId}/sell-order`, {}, { headers: this.authHeaders() });
  }

  getOrder(orderId: string): Observable<Record<string, unknown>> {
    return this.http.get<Record<string, unknown>>(`/api/orders/${encodeURIComponent(orderId)}`, { headers: this.authHeaders() });
  }

  updateOrderItems(
    orderId: string,
    items: OrderItemPayload[]
  ): Observable<Record<string, unknown>> {
    return this.http.put<Record<string, unknown>>(
      `/api/orders/${encodeURIComponent(orderId)}/items`,
      { items },
      { headers: this.authHeaders() }
    );
  }

  private authHeaders(): HttpHeaders {
    const token = this.tokenStore.getToken();
    if (!token) {
      return new HttpHeaders();
    }
    return new HttpHeaders({ Authorization: `Bearer ${token}` });
  }
}
