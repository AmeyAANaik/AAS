import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Subject } from 'rxjs';
import { tap } from 'rxjs/operators';
import { AuthTokenService } from '../shared/auth-token.service';
import { BillReviewDetail, BillReviewItemType, BillReviewListItem, ReviewDecisionRequest } from './bill-review.model';

@Injectable({ providedIn: 'root' })
export class BillReviewService {
  private readonly refreshSubject = new Subject<void>();
  readonly refresh$ = this.refreshSubject.asObservable();

  constructor(
    private readonly http: HttpClient,
    private readonly tokenStore: AuthTokenService
  ) {}

  getPendingCount() {
    return this.http.get<{ pendingCount: number }>('/api/bill-review/count', { headers: this.authHeaders() });
  }

  listItems(status = 'UNDER_REVIEW', partyType = '') {
    let params = new HttpParams();
    if (status.trim()) {
      params = params.set('status', status.trim());
    }
    if (partyType.trim()) {
      params = params.set('partyType', partyType.trim());
    }
    return this.http.get<BillReviewListItem[]>('/api/bill-review/items', { headers: this.authHeaders(), params });
  }

  getDetail(itemType: BillReviewItemType, documentId: string) {
    return this.http.get<BillReviewDetail>(
      `/api/bill-review/items/${encodeURIComponent(itemType)}/${encodeURIComponent(documentId)}`,
      { headers: this.authHeaders() }
    );
  }

  approve(itemType: BillReviewItemType, documentId: string, payload: ReviewDecisionRequest) {
    return this.http.put<BillReviewDetail>(
      `/api/bill-review/items/${encodeURIComponent(itemType)}/${encodeURIComponent(documentId)}/approve`,
      payload ?? {},
      { headers: this.authHeaders() }
    )
      .pipe(tap(() => this.refreshSubject.next()));
  }

  reject(itemType: BillReviewItemType, documentId: string, payload: ReviewDecisionRequest) {
    return this.http.put<BillReviewDetail>(
      `/api/bill-review/items/${encodeURIComponent(itemType)}/${encodeURIComponent(documentId)}/reject`,
      payload ?? {},
      { headers: this.authHeaders() }
    )
      .pipe(tap(() => this.refreshSubject.next()));
  }

  downloadAdjustmentNotePdf(documentId: string) {
    return this.http.get(`/api/adjustment-notes/${encodeURIComponent(documentId)}/pdf`, {
      headers: this.authHeaders(),
      responseType: 'blob'
    });
  }

  notifyRefresh(): void {
    this.refreshSubject.next();
  }

  private authHeaders(): HttpHeaders {
    const token = this.tokenStore.getToken();
    return token ? new HttpHeaders({ Authorization: `Bearer ${token}` }) : new HttpHeaders();
  }
}
