import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { AuthTokenService } from '../shared/auth-token.service';

@Injectable({
  providedIn: 'root'
})
export class ItemService {
  constructor(private http: HttpClient, private tokenStore: AuthTokenService) {}

  listItems(): Observable<any[]> {
    return this.http.get<any[]>('/api/items', { headers: this.authHeaders() });
  }

  createItem(fields: Record<string, unknown>): Observable<unknown> {
    return this.http.post('/api/items', { fields }, { headers: this.authHeaders() });
  }

  private authHeaders(): HttpHeaders {
    const token = this.tokenStore.getToken();
    if (!token) {
      return new HttpHeaders();
    }
    return new HttpHeaders({ Authorization: `Bearer ${token}` });
  }
}
