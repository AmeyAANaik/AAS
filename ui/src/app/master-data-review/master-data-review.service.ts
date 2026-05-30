import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Subject } from 'rxjs';
import { tap } from 'rxjs/operators';
import { AuthTokenService } from '../shared/auth-token.service';
import {
  ApproveMasterDataReviewRequest,
  MasterDataReviewDetail,
  MasterDataReviewListItem
} from './master-data-review.model';

@Injectable({
  providedIn: 'root'
})
export class MasterDataReviewService {
  private readonly refreshSubject = new Subject<void>();
  readonly refresh$ = this.refreshSubject.asObservable();

  constructor(
    private readonly http: HttpClient,
    private readonly tokenStore: AuthTokenService
  ) {}

  getPendingCount() {
    return this.http.get<{ pendingCount: number }>('/api/master-data-review/count', { headers: this.authHeaders() });
  }

  listItems(status = '') {
    let params = new HttpParams();
    if (status.trim()) {
      params = params.set('status', status.trim());
    }
    return this.http.get<MasterDataReviewListItem[]>('/api/master-data-review/items', {
      headers: this.authHeaders(),
      params
    });
  }

  getDetail(itemId: string) {
    return this.http.get<MasterDataReviewDetail>(`/api/master-data-review/items/${encodeURIComponent(itemId)}`, {
      headers: this.authHeaders()
    });
  }

  approve(itemId: string, payload: ApproveMasterDataReviewRequest) {
    return this.http.put<{ detail: MasterDataReviewDetail; pendingCount: number }>(
      `/api/master-data-review/items/${encodeURIComponent(itemId)}/approve`,
      payload,
      { headers: this.authHeaders() }
    ).pipe(
      tap(() => this.refreshSubject.next())
    );
  }

  reject(itemId: string, reviewNotes: string) {
    return this.http.put<{ detail: MasterDataReviewDetail; pendingCount: number }>(
      `/api/master-data-review/items/${encodeURIComponent(itemId)}/reject`,
      { reviewNotes },
      { headers: this.authHeaders() }
    ).pipe(
      tap(() => this.refreshSubject.next())
    );
  }

  notifyRefresh(): void {
    this.refreshSubject.next();
  }

  private authHeaders(): HttpHeaders {
    const token = this.tokenStore.getToken();
    return token ? new HttpHeaders({ Authorization: `Bearer ${token}` }) : new HttpHeaders();
  }
}
