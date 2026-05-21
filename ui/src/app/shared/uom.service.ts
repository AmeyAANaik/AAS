import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { AuthTokenService } from './auth-token.service';

export type UomRow = {
  name?: string;
  uom_name?: string;
  must_be_whole_number?: number | boolean;
};

@Injectable({ providedIn: 'root' })
export class UomService {
  constructor(private http: HttpClient, private tokenStore: AuthTokenService) {}

  listUoms(refresh = false): Observable<UomRow[]> {
    const refreshParam = refresh ? '?refresh=1' : '';
    return this.http.get<UomRow[]>(`/api/uoms${refreshParam}`, { headers: this.authHeaders() });
  }

  private authHeaders(): HttpHeaders {
    const token = this.tokenStore.getToken();
    return token ? new HttpHeaders({ Authorization: `Bearer ${token}` }) : new HttpHeaders();
  }
}

