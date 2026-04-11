import { inject } from '@angular/core';
import { CanMatchFn, Route, Router, UrlSegment } from '@angular/router';
import { map } from 'rxjs/operators';
import { AuthTokenService } from '../shared/auth-token.service';
import { UserAccessService } from '../shared/user-access.service';

export const featureGuard: CanMatchFn = (route: Route, segments: UrlSegment[]) => {
  const tokenStore = inject(AuthTokenService);
  const router = inject(Router);
  const userAccess = inject(UserAccessService);
  const token = tokenStore.getToken();
  const requestedFeature = String(route.data?.['feature'] ?? '').trim();

  if (!token) {
    const url = '/' + segments.map(segment => segment.path).join('/');
    return router.createUrlTree(['/login'], { queryParams: { returnUrl: url || '/login' } });
  }

  if (!requestedFeature) {
    return true;
  }

  return userAccess.getProfile().pipe(
    map(profile => {
      const features = Array.isArray(profile.features) ? profile.features.map(value => String(value)) : tokenStore.getFeatures();
      if (features.includes(requestedFeature)) {
        return true;
      }
      const fallbackRoute = String(profile.homeRoute ?? tokenStore.getHomeRoute() ?? '/login').trim() || '/login';
      return router.createUrlTree([fallbackRoute]);
    })
  );
};
