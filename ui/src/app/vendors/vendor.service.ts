import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { AuthTokenService } from '../shared/auth-token.service';
import { Vendor, VendorTemplateValidation } from './vendor.model';

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

  uploadInvoiceTemplateSample(id: string, file: File, templateJson: string): Observable<{ validation: VendorTemplateValidation }> {
    const formData = new FormData();
    formData.append('file', file);
    if (templateJson.trim()) {
      formData.append('templateJson', templateJson.trim());
    }
    return this.http.post<{ validation: VendorTemplateValidation }>(
      `/api/vendors/${encodeURIComponent(id)}/invoice-template/sample`,
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
