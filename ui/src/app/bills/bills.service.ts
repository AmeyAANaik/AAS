import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { map } from 'rxjs/operators';
import { Observable } from 'rxjs';
import { AuthTokenService } from '../shared/auth-token.service';
import { AdjustmentNoteDocument, AdjustmentNotePayload, AdjustmentNoteResult, InvoiceCreatePayload, InvoiceDeliveryPreview, InvoiceDeliveryRequest, InvoiceFilters, InvoiceSummary, OrderSnapshot, PaymentDueSummary, PaymentPayload, UploadedFileInfo } from './bills.model';

@Injectable({
  providedIn: 'root'
})
export class BillsService {
  constructor(private http: HttpClient, private tokenStore: AuthTokenService) {}

  listInvoices(filters: InvoiceFilters): Observable<InvoiceSummary[]> {
    let params = new HttpParams();
    if (filters.partyType) {
      params = params.set('partyType', filters.partyType);
    }
    if (filters.partyId) {
      params = params.set('partyId', filters.partyId);
    }
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

  getOrderSnapshot(orderId: string): Observable<OrderSnapshot | null> {
    return this.http
      .get<{ data?: OrderSnapshot }>(`/api/orders/${orderId}`, { headers: this.authHeaders() })
      .pipe(map(response => response?.data ?? null));
  }

  downloadInvoicePdf(invoiceId: string, partyType?: string): Observable<Blob> {
    let params = new HttpParams();
    if (partyType) {
      params = params.set('partyType', partyType);
    }
    return this.http.get(`/api/invoices/${invoiceId}/pdf`, {
      headers: this.authHeaders(),
      params,
      responseType: 'blob'
    });
  }

  deleteInvoice(invoiceId: string): Observable<Record<string, unknown>> {
    return this.http.delete<Record<string, unknown>>(`/api/invoices/${invoiceId}`, {
      headers: this.authHeaders()
    });
  }

  getInvoiceDeliveryPreview(invoiceId: string): Observable<InvoiceDeliveryPreview> {
    return this.http.get<InvoiceDeliveryPreview>(`/api/invoices/${invoiceId}/delivery-preview`, {
      headers: this.authHeaders()
    });
  }

  sendInvoiceEmail(invoiceId: string, payload: InvoiceDeliveryRequest): Observable<Record<string, unknown>> {
    return this.http.post<Record<string, unknown>>(`/api/invoices/${invoiceId}/send-email`, payload, {
      headers: this.authHeaders()
    });
  }

  sendInvoiceWhatsApp(invoiceId: string, payload: InvoiceDeliveryRequest): Observable<Record<string, unknown>> {
    return this.http.post<Record<string, unknown>>(`/api/invoices/${invoiceId}/send-whatsapp`, payload, {
      headers: this.authHeaders()
    });
  }

  exportInvoices(filters: InvoiceFilters): Observable<Blob> {
    let params = new HttpParams();
    if (filters.partyType) {
      params = params.set('partyType', filters.partyType);
    }
    if (filters.partyId) {
      params = params.set('partyId', filters.partyId);
    }
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

  createPaymentWithAttachments(payload: PaymentPayload, files: File[]): Observable<{ payment: Record<string, unknown>; files: UploadedFileInfo[] }> {
    const form = new FormData();
    form.append('payment', new Blob([JSON.stringify(payload)], { type: 'application/json' }), 'payment.json');
    (files ?? []).forEach(file => form.append('files', file, file.name));
    return this.http.post<{ payment: Record<string, unknown>; files: UploadedFileInfo[] }>('/api/payments/with-attachments', form, {
      headers: this.authHeaders()
    });
  }

  createAdjustmentNoteWithAttachments(
    payload: AdjustmentNotePayload,
    files: File[]
  ): Observable<AdjustmentNoteResult> {
    const form = new FormData();
    form.append('note', new Blob([JSON.stringify(payload)], { type: 'application/json' }), 'note.json');
    (files ?? []).forEach(file => form.append('files', file, file.name));
    return this.http.post<AdjustmentNoteResult>('/api/adjustment-notes/with-attachments', form, {
      headers: this.authHeaders()
    });
  }

  listInvoiceOptions(partyType: string, partyId: string): Observable<InvoiceSummary[]> {
    return this.listInvoices({
      partyType,
      partyId
    });
  }

  uploadPaymentAttachments(paymentId: string, files: File[]): Observable<{ paymentId: string; files: UploadedFileInfo[] }> {
    const form = new FormData();
    (files ?? []).forEach(file => form.append('files', file, file.name));
    return this.http.post<{ paymentId: string; files: UploadedFileInfo[] }>(`/api/payments/${paymentId}/attachments`, form, {
      headers: this.authHeaders()
    });
  }

  dueByCategory(
    partyType: string,
    partyId: string,
    categoryId: string
  ): Observable<PaymentDueSummary> {
    const params = new HttpParams()
      .set('partyType', partyType)
      .set('partyId', partyId)
      .set('categoryId', categoryId);
    return this.http.get<PaymentDueSummary>('/api/payments/due-by-category', { headers: this.authHeaders(), params });
  }

  private authHeaders(): HttpHeaders {
    const token = this.tokenStore.getToken();
    if (!token) {
      return new HttpHeaders();
    }
    return new HttpHeaders({ Authorization: `Bearer ${token}` });
  }
}
