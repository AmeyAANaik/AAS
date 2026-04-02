import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { shareReplay, tap } from 'rxjs/operators';
import { AuthTokenService } from './auth-token.service';

export type UserProfile = Record<string, unknown> & {
  role?: string;
  features?: string[];
  homeRoute?: string;
};

@Injectable({
  providedIn: 'root'
})
export class UserAccessService {
  private profileRequest$: Observable<UserProfile> | null = null;

  constructor(
    private http: HttpClient,
    private tokenStore: AuthTokenService
  ) {}

  getProfile(forceReload = false): Observable<UserProfile> {
    const token = this.tokenStore.getToken();
    if (!token) {
      return of({});
    }
    if (!forceReload && this.profileRequest$) {
      return this.profileRequest$;
    }
    const headers = new HttpHeaders({ Authorization: `Bearer ${token}` });
    this.profileRequest$ = this.http.get<UserProfile>('/api/me', { headers }).pipe(
      tap(profile => {
        this.tokenStore.setRole(String(profile.role ?? '').trim() || null);
        this.tokenStore.setFeatures(Array.isArray(profile.features) ? profile.features.map(value => String(value)) : []);
        this.tokenStore.setHomeRoute(String(profile.homeRoute ?? '').trim() || null);
      }),
      shareReplay(1)
    );
    return this.profileRequest$;
  }

  clearCache(): void {
    this.profileRequest$ = null;
  }

  hasFeature(feature: string): boolean {
    return this.tokenStore.getFeatures().includes(feature);
  }
}
