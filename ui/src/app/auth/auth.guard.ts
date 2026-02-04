import { inject } from '@angular/core';
import { CanMatchFn, Router } from '@angular/router';
import { AuthTokenService } from '../shared/auth-token.service';

export const authGuard: CanMatchFn = (route, segments) => {
  const tokenStore = inject(AuthTokenService);
  const router = inject(Router);
  const url = '/' + segments.map(s => s.path).join('/');
  if (tokenStore.getToken()) {
    return true;
  }
  return router.createUrlTree(['/login'], { queryParams: { returnUrl: url === '/' ? '/admin/dashboard' : url } });
};
