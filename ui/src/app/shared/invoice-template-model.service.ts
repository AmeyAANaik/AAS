import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Observable, shareReplay } from 'rxjs';
import { AuthTokenService } from './auth-token.service';

export interface InvoiceTemplateModelField {
  key: string;
  label: string;
  required: boolean;
  sourceAliases: string[];
}

export interface InvoiceTemplateModel {
  itemFields: InvoiceTemplateModelField[];
  summaryFields: InvoiceTemplateModelField[];
  requiredFields?: {
    items: string[];
    summary: string[];
  };
  workflow?: {
    inputMode: string;
    uiPolicy: string;
  };
}

@Injectable({
  providedIn: 'root'
})
export class InvoiceTemplateModelService {
  private readonly model$ = this.http.get<InvoiceTemplateModel>('/api/vendors/template-model', {
    headers: this.authHeaders()
  }).pipe(shareReplay(1));

  constructor(private http: HttpClient, private tokenStore: AuthTokenService) {}

  getModel(): Observable<InvoiceTemplateModel> {
    return this.model$;
  }

  fetchModel(): Observable<InvoiceTemplateModel> {
    return this.http.get<InvoiceTemplateModel>('/api/vendors/template-model', {
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
