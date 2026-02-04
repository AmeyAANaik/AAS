import { HttpClient, HttpHeaders } from '@angular/common/http';
import { inject } from '@angular/core';
import { CanMatchFn, Router } from '@angular/router';
import { catchError, map, of } from 'rxjs';
import { AuthTokenService } from '../shared/auth-token.service';

export const adminDashboardGuard: CanMatchFn = () => {
  const tokenStore = inject(AuthTokenService);
  const router = inject(Router);
  const token = tokenStore.getToken();

  if (!token) {
    return router.createUrlTree(['/login']);
  }

  const headers = new HttpHeaders({ Authorization: `Bearer ${token}` });
  return inject(HttpClient)
    .get<{ role?: string }>(`/api/me`, { headers })
    .pipe(
      map(profile => (profile?.role === 'admin' ? true : router.createUrlTree(['/login']))),
      catchError(() => of(router.createUrlTree(['/login'])))
    );
};
