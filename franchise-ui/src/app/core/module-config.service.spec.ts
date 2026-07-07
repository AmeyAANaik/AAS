import { TestBed } from '@angular/core/testing';
import { ModuleConfigService } from './module-config.service';

const STORAGE_KEY = 'franchise.modules.enabled';

describe('ModuleConfigService', () => {
  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({});
  });

  it('does not let saved module settings disable the dashboard landing', () => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify({ dashboard: false, 'master-data': true }));

    const service = TestBed.inject(ModuleConfigService);

    expect(service.isFeatureEnabled('dashboard.view')).toBeTrue();
    expect(service.disabledFeatures().has('dashboard.view')).toBeFalse();
  });
});
