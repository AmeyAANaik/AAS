import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, map } from 'rxjs';
import { AuthTokenService } from '../shared/auth-token.service';
import { Branch, BranchMetadata } from './branch.model';

const METADATA_KEY = 'aas_branch_metadata';

@Injectable({
  providedIn: 'root'
})
export class BranchService {
  constructor(private http: HttpClient, private tokenStore: AuthTokenService) {}

  listBranches(): Observable<any[]> {
    return this.http.get<Branch[]>('/api/shops', { headers: this.authHeaders() }).pipe(
      map(branches => this.mergeMetadata(branches ?? []) as any[])
    );
  }

  createBranch(fields: Record<string, unknown>): Observable<unknown> {
    return this.http.post('/api/shops', { fields }, { headers: this.authHeaders() });
  }

  saveMetadata(branchId: string, metadata: BranchMetadata): void {
    if (!branchId) {
      return;
    }
    const store = this.readMetadata();
    store[branchId] = metadata;
    localStorage.setItem(METADATA_KEY, JSON.stringify(store));
  }

  private mergeMetadata(branches: Branch[]): Array<Branch & BranchMetadata> {
    const store = this.readMetadata();
    return branches.map(branch => ({
      ...branch,
      ...(store[String(branch.name ?? branch.customer_name ?? '')] || {})
    }));
  }

  private readMetadata(): Record<string, BranchMetadata> {
    const raw = localStorage.getItem(METADATA_KEY);
    if (!raw) {
      return {};
    }
    try {
      return JSON.parse(raw) as Record<string, BranchMetadata>;
    } catch {
      return {};
    }
  }

  private authHeaders(): HttpHeaders {
    const token = this.tokenStore.getToken();
    if (!token) {
      return new HttpHeaders();
    }
    return new HttpHeaders({ Authorization: `Bearer ${token}` });
  }
}
