import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { AuthTokenService } from '../shared/auth-token.service';
import { ItemPage } from './item.model';

@Injectable({
  providedIn: 'root'
})
export class ItemService {
  constructor(private http: HttpClient, private tokenStore: AuthTokenService) {}

  listItems(): Observable<any[]> {
    return this.http.get<any[]>('/api/items', { headers: this.authHeaders() });
  }

  listItemsPaged(page: number, size: number, search: string, sort: string, dir: string): Observable<ItemPage> {
    const params = {
      page,
      size,
      search: search ?? '',
      sort: sort ?? '',
      dir: dir ?? ''
    };
    return this.http.get<ItemPage>('/api/items/paged', { headers: this.authHeaders(), params });
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
