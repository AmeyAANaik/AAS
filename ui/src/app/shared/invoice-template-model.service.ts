import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, shareReplay } from 'rxjs';

export interface InvoiceTemplateModelField {
  key: string;
  label: string;
  required: boolean;
  sourceAliases: string[];
}

export interface InvoiceTemplateModel {
  itemFields: InvoiceTemplateModelField[];
  summaryFields: InvoiceTemplateModelField[];
}

@Injectable({
  providedIn: 'root'
})
export class InvoiceTemplateModelService {
  private readonly model$ = this.http.get<InvoiceTemplateModel>('/api/vendors/template-model').pipe(shareReplay(1));

  constructor(private http: HttpClient) {}

  getModel(): Observable<InvoiceTemplateModel> {
    return this.model$;
  }
}
