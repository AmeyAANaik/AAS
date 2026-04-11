import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { AuthTokenService } from '../shared/auth-token.service';
import {
  VendorInvoiceTemplateProfilePreview,
} from './vendor.model';

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

  updateVendor(id: string, fields: Record<string, unknown>): Observable<unknown> {
    return this.http.put(`/api/vendors/${id}`, { fields }, { headers: this.authHeaders() });
  }

  deleteVendor(id: string): Observable<unknown> {
    return this.http.delete(`/api/vendors/${encodeURIComponent(id)}`, { headers: this.authHeaders() });
  }

  clearInvoiceTemplate(id: string): Observable<unknown> {
    return this.http.delete(`/api/vendors/${encodeURIComponent(id)}/invoice-template`, { headers: this.authHeaders() });
  }

  generateInvoiceTemplateProfile(
    id: string,
    file: File | null
  ): Observable<VendorInvoiceTemplateProfilePreview> {
    const formData = new FormData();
    if (file) {
      formData.append('file', file);
    }
    return this.http.post<VendorInvoiceTemplateProfilePreview>(
      `/api/vendors/${encodeURIComponent(id)}/invoice-template/generate-preview`,
      formData,
      { headers: this.authHeaders() }
    );
  }

  saveGeneratedInvoiceTemplateProfile(
    id: string,
    file: File | null,
    generatedTemplate: Record<string, unknown>
  ): Observable<{ profilePreview: VendorInvoiceTemplateProfilePreview }> {
    const formData = new FormData();
    if (file) {
      formData.append('file', file);
    }
    formData.append('generatedTemplate', JSON.stringify(generatedTemplate));
    return this.http.post<{ profilePreview: VendorInvoiceTemplateProfilePreview }>(
      `/api/vendors/${encodeURIComponent(id)}/invoice-template/generate-save`,
      formData,
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
