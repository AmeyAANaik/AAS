import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { AuthTokenService } from '../shared/auth-token.service';
import { InvoiceCreatePayload, InvoiceFilters, InvoiceSummary, OrderSnapshot, PaymentPayload } from './bills.model';

@Injectable({
  providedIn: 'root'
})
export class BillsService {
  constructor(private http: HttpClient, private tokenStore: AuthTokenService) {}

  listInvoices(filters: InvoiceFilters): Observable<InvoiceSummary[]> {
    let params = new HttpParams();
    if (filters.customer) {
      params = params.set('customer', filters.customer);
    }
    if (filters.from) {
      params = params.set('from', filters.from);
    }
    if (filters.to) {
      params = params.set('to', filters.to);
    }
    return this.http.get<InvoiceSummary[]>('/api/invoices', { headers: this.authHeaders(), params });
  }

  createInvoice(payload: InvoiceCreatePayload): Observable<Record<string, unknown>> {
    return this.http.post<Record<string, unknown>>(
      '/api/invoices',
      { fields: payload },
      { headers: this.authHeaders() }
    );
  }

  getOrderSnapshot(orderId: string): Observable<OrderSnapshot> {
    return this.http.get<OrderSnapshot>(`/api/orders/${orderId}`, { headers: this.authHeaders() });
  }

  downloadInvoicePdf(invoiceId: string): Observable<Blob> {
    return this.http.get(`/api/invoices/${invoiceId}/pdf`, {
      headers: this.authHeaders(),
      responseType: 'blob'
    });
  }

  exportInvoices(filters: InvoiceFilters): Observable<Blob> {
    let params = new HttpParams();
    if (filters.customer) {
      params = params.set('customer', filters.customer);
    }
    if (filters.from) {
      params = params.set('from', filters.from);
    }
    if (filters.to) {
      params = params.set('to', filters.to);
    }
    return this.http.get('/api/invoices/export', {
      headers: this.authHeaders(),
      params,
      responseType: 'blob'
    });
  }

  createPayment(payload: PaymentPayload): Observable<Record<string, unknown>> {
    return this.http.post<Record<string, unknown>>('/api/payments', payload, { headers: this.authHeaders() });
  }

  private authHeaders(): HttpHeaders {
    const token = this.tokenStore.getToken();
    if (!token) {
      return new HttpHeaders();
    }
    return new HttpHeaders({ Authorization: `Bearer ${token}` });
  }
}
