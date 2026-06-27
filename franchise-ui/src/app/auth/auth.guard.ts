import { inject } from '@angular/core';
import { CanMatchFn, Router } from '@angular/router';
import { MockAuthService } from './mock-auth.service';

export const authGuard: CanMatchFn = () => {
  const auth = inject(MockAuthService);
  const router = inject(Router);
  if (auth.isAuthenticated()) {
    return true;
  }
  return router.parseUrl('/login');
};
