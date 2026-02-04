import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { AuthTokenService } from '../shared/auth-token.service';
import { Category } from './category.model';

@Injectable({
  providedIn: 'root'
})
export class CategoryService {
  constructor(private http: HttpClient, private tokenStore: AuthTokenService) {}

  listCategories(): Observable<any[]> {
    return this.http.get<any[]>('/api/categories', { headers: this.authHeaders() });
  }

  createCategory(fields: Record<string, unknown>): Observable<unknown> {
    return this.http.post('/api/categories', { fields }, { headers: this.authHeaders() });
  }

  private authHeaders(): HttpHeaders {
    const token = this.tokenStore.getToken();
    if (!token) {
      return new HttpHeaders();
    }
    return new HttpHeaders({ Authorization: `Bearer ${token}` });
  }
}
