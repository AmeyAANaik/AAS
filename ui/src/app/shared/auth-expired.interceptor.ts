import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';
import { AuthTokenService } from './auth-token.service';

function isSessionMissingError(err: HttpErrorResponse): boolean {
  const payload = err.error as any;
  const message = typeof payload === 'string' ? payload : payload?.error || payload?.message;
  return typeof message === 'string' && message.toLowerCase().includes('erpnext session not found');
}

export const authExpiredInterceptor: HttpInterceptorFn = (req, next) => {
  const tokenStore = inject(AuthTokenService);
  const router = inject(Router);

  return next(req).pipe(
    catchError((err: unknown) => {
      if (err instanceof HttpErrorResponse && err.status === 401) {
        // Typical case: MW restarted, ERP session cookie cache is gone but JWT is still in localStorage.
        // Clear token and force re-login so MW can re-establish the ERP session.
        if (isSessionMissingError(err) || req.url.startsWith('/api/')) {
          tokenStore.setToken(null);
          tokenStore.setRole(null);
          // Avoid navigation loops if we're already on login.
          if (router.url !== '/login') {
            router.navigate(['/login'], { queryParams: { returnUrl: router.url } });
          }
        }
      }
      return throwError(() => err);
    })
  );
};
