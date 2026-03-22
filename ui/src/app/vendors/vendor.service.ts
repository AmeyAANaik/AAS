import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, catchError, throwError } from 'rxjs';
import { AuthTokenService } from '../shared/auth-token.service';
import {
  VendorInvoiceFieldMapping,
  VendorInvoiceTemplateAnalysis,
  VendorInvoiceTemplateMappingPreview,
  VendorInvoiceTemplateProfile,
  VendorInvoiceTemplateProfilePreview,
  VendorTemplateValidation
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

  uploadInvoiceTemplateSample(
    id: string,
    file: File,
    templateJson: string
  ): Observable<{ validation: VendorTemplateValidation; file?: { fileUrl?: string } }> {
    const formData = new FormData();
    formData.append('file', file);
    if (templateJson.trim()) {
      formData.append('templateJson', templateJson.trim());
    }
    return this.http.post<{ validation: VendorTemplateValidation; file?: { fileUrl?: string } }>(
      `/api/vendors/${encodeURIComponent(id)}/invoice-template/sample`,
      formData,
      { headers: this.authHeaders() }
    );
  }

  analyzeInvoiceTemplateSample(
    id: string,
    file: File | null
  ): Observable<VendorInvoiceTemplateAnalysis> {
    const formData = new FormData();
    if (file) {
      formData.append('file', file);
    }
    return this.http.post<VendorInvoiceTemplateAnalysis>(
      `/api/vendors/${encodeURIComponent(id)}/invoice-template/analyze`,
      formData,
      { headers: this.authHeaders() }
    );
  }

  previewInvoiceTemplateMapping(
    id: string,
    file: File | null,
    mapping: { itemMappings: VendorInvoiceFieldMapping[]; summaryMappings: VendorInvoiceFieldMapping[] }
  ): Observable<VendorInvoiceTemplateMappingPreview> {
    const formData = new FormData();
    if (file) {
      formData.append('file', file);
    }
    formData.append('mapping', JSON.stringify(mapping));
    const primaryUrl = `/api/vendors/${encodeURIComponent(id)}/invoice-template/mapping/preview`;
    const fallbackUrl = `/api/vendors/${encodeURIComponent(id)}/invoice-template/preview`;
    return this.http.post<VendorInvoiceTemplateMappingPreview>(
      primaryUrl,
      formData,
      { headers: this.authHeaders() }
    ).pipe(
      catchError(err => {
        if (err?.status !== 404) {
          return throwError(() => err);
        }
        return this.http.post<VendorInvoiceTemplateMappingPreview>(
          fallbackUrl,
          formData,
          { headers: this.authHeaders() }
        );
      })
    );
  }

  saveInvoiceTemplateMapping(
    id: string,
    file: File | null,
    mapping: { itemMappings: VendorInvoiceFieldMapping[]; summaryMappings: VendorInvoiceFieldMapping[] }
  ): Observable<{ mappingPreview: VendorInvoiceTemplateMappingPreview }> {
    const formData = new FormData();
    if (file) {
      formData.append('file', file);
    }
    formData.append('mapping', JSON.stringify(mapping));
    return this.http.post<{ mappingPreview: VendorInvoiceTemplateMappingPreview }>(
      `/api/vendors/${encodeURIComponent(id)}/invoice-template/mapping/save`,
      formData,
      { headers: this.authHeaders() }
    );
  }

  listInvoiceTemplateProfiles(): Observable<{ profiles: VendorInvoiceTemplateProfile[] }> {
    return this.http.get<{ profiles: VendorInvoiceTemplateProfile[] }>(
      '/api/vendors/invoice-template-profiles',
      { headers: this.authHeaders() }
    );
  }

  previewInvoiceTemplateProfile(
    id: string,
    file: File | null,
    templateProfileId: string
  ): Observable<VendorInvoiceTemplateProfilePreview> {
    const formData = new FormData();
    if (file) {
      formData.append('file', file);
    }
    formData.append('templateProfileId', templateProfileId);
    return this.http.post<VendorInvoiceTemplateProfilePreview>(
      `/api/vendors/${encodeURIComponent(id)}/invoice-template/profile/preview`,
      formData,
      { headers: this.authHeaders() }
    );
  }

  saveInvoiceTemplateProfile(
    id: string,
    file: File | null,
    templateProfileId: string
  ): Observable<{ profilePreview: VendorInvoiceTemplateProfilePreview }> {
    const formData = new FormData();
    if (file) {
      formData.append('file', file);
    }
    formData.append('templateProfileId', templateProfileId);
    return this.http.post<{ profilePreview: VendorInvoiceTemplateProfilePreview }>(
      `/api/vendors/${encodeURIComponent(id)}/invoice-template/profile/save`,
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
