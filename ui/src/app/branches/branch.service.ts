import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable } from 'rxjs';
import { AuthTokenService } from '../shared/auth-token.service';
import { Branch } from './branch.model';

@Injectable({
  providedIn: 'root'
})
export class BranchService {
  constructor(private http: HttpClient, private tokenStore: AuthTokenService) {}

  listBranches(): Observable<any[]> {
    return this.http.get<Branch[]>('/api/shops', { headers: this.authHeaders() });
  }

  createBranch(fields: Record<string, unknown>): Observable<unknown> {
    return this.http.post('/api/shops', { fields }, { headers: this.authHeaders() });
  }

  updateBranch(branchId: string, fields: Record<string, unknown>): Observable<unknown> {
    return this.http.put(`/api/shops/${encodeURIComponent(branchId)}`, { fields }, { headers: this.authHeaders() });
  }

  private authHeaders(): HttpHeaders {
    const token = this.tokenStore.getToken();
    if (!token) {
      return new HttpHeaders();
    }
    return new HttpHeaders({ Authorization: `Bearer ${token}` });
  }
}
