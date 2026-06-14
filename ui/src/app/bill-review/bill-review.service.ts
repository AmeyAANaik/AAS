import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Subject } from 'rxjs';
import { tap } from 'rxjs/operators';
import { AuthTokenService } from '../shared/auth-token.service';
import { BillReviewAttachment, BillReviewDetail, BillReviewItemType, BillReviewListItem, ReviewDecisionRequest } from './bill-review.model';

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

  downloadAttachment(attachment: BillReviewAttachment) {
    const url = String(attachment?.url ?? '').trim();
    if (!url) {
      throw new Error('Attachment URL is missing.');
    }
    return this.http.get(this.normalizeAttachmentUrl(url), {
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

  private normalizeAttachmentUrl(url: string): string {
    if (url.startsWith('/api/files/') || url.startsWith('/api/private/files/')) {
      return encodeURI(url);
    }
    if (url.startsWith('//')) {
      const pathStart = url.indexOf('/', 2);
      const path = pathStart >= 0 ? url.slice(pathStart) : url;
      if (path.startsWith('/api/files/') || path.startsWith('/api/private/files/')) {
        return encodeURI(path);
      }
      if (path.startsWith('/files/') || path.startsWith('/private/files/')) {
        return encodeURI(`/api${path}`);
      }
    }
    try {
      const parsed = new URL(url, window.location.origin);
      if (parsed.pathname.startsWith('/api/files/') || parsed.pathname.startsWith('/api/private/files/')) {
        return encodeURI(`${parsed.pathname}${parsed.search}`);
      }
      if (parsed.pathname.startsWith('/files/') || parsed.pathname.startsWith('/private/files/')) {
        return encodeURI(`/api${parsed.pathname}${parsed.search}`);
      }
    } catch {
      return encodeURI(url);
    }
    return encodeURI(url);
  }
}
