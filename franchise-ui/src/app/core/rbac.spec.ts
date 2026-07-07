import { homeRouteForFeatures } from './rbac';

describe('RBAC home routing', () => {
  it('prefers the four-module dashboard when dashboard access is available', () => {
    expect(homeRouteForFeatures(['master-data.view', 'dashboard.view'])).toBe('/dashboard');
  });

  it('falls back to the first available module when dashboard access is unavailable', () => {
    expect(homeRouteForFeatures(['master-data.view'])).toBe('/master-data');
    expect(homeRouteForFeatures(['inventory.view'])).toBe('/inventory/reports');
    expect(homeRouteForFeatures(['admin.modules'])).toBe('/admin/modules');
  });
});
