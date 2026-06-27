import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { FeatureKey, TOGGLEABLE_MODULES, isAdminFeature } from './rbac';

const STORAGE_KEY = 'franchise.modules.enabled';

/**
 * Admin-configurable module switches (mock, persisted to localStorage).
 * A disabled module's feature keys are stripped from every user's effective
 * access. Admin features are never affected.
 */
@Injectable({ providedIn: 'root' })
export class ModuleConfigService {
  private readonly state$: BehaviorSubject<Record<string, boolean>>;
  readonly changes$: Observable<Record<string, boolean>>;

  constructor() {
    this.state$ = new BehaviorSubject<Record<string, boolean>>(this.load());
    this.changes$ = this.state$.asObservable();
  }

  private load(): Record<string, boolean> {
    const defaults: Record<string, boolean> = {};
    TOGGLEABLE_MODULES.forEach(m => (defaults[m.key] = true));
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (raw) {
        return { ...defaults, ...JSON.parse(raw) };
      }
    } catch {
      /* ignore */
    }
    return defaults;
  }

  private persist(state: Record<string, boolean>): void {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
    } catch {
      /* ignore */
    }
  }

  snapshot(): Record<string, boolean> {
    return { ...this.state$.value };
  }

  isModuleEnabled(key: string): boolean {
    return this.state$.value[key] !== false;
  }

  setModuleEnabled(key: string, enabled: boolean): void {
    const next = { ...this.state$.value, [key]: enabled };
    this.persist(next);
    this.state$.next(next);
  }

  /** Feature keys that are currently disabled because their module is off. */
  disabledFeatures(): Set<FeatureKey> {
    const disabled = new Set<FeatureKey>();
    TOGGLEABLE_MODULES.forEach(m => {
      if (!this.isModuleEnabled(m.key)) {
        m.features.forEach(f => disabled.add(f));
      }
    });
    return disabled;
  }

  /** True when the feature's owning module is enabled (admin features always on). */
  isFeatureEnabled(feature: FeatureKey): boolean {
    if (isAdminFeature(feature)) {
      return true;
    }
    return !this.disabledFeatures().has(feature);
  }
}
