import { inject } from '@angular/core';
import { CanMatchFn, Route, Router, UrlSegment } from '@angular/router';
import { MockAuthService } from './mock-auth.service';
import { FeatureKey, homeRouteForFeatures } from '../core/rbac';

/**
 * Gate a route on its `data.feature`. Access requires the feature to be in the
 * user's effective set (role defaults ± overrides AND the owning module on).
 * Denied users are redirected to the first module they can reach.
 */
export const featureGuard: CanMatchFn = (route: Route, _segments: UrlSegment[]) => {
  const auth = inject(MockAuthService);
  const router = inject(Router);

  if (!auth.isAuthenticated()) {
    return router.parseUrl('/login');
  }

  const feature = route.data?.['feature'] as FeatureKey | undefined;
  const features = auth.effectiveFeatures();
  if (!feature || features.includes(feature)) {
    return true;
  }
  return router.parseUrl(homeRouteForFeatures(features));
};
