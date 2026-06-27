import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';
import { FeatureKey, Role, featuresForRole } from '../core/rbac';
import { ModuleConfigService } from '../core/module-config.service';

export interface ManagedUser {
  id: string;
  name: string;
  email: string;
  role: Role;
  franchise: string;
  allow: FeatureKey[];
  deny: FeatureKey[];
}

const SESSION_KEY = 'franchise.session.userId';
const OVERRIDES_KEY = 'franchise.users.overrides';

export const FRANCHISES = ['Sukhkarta — Aundh', 'Sukhkarta — Baner', 'Sukhkarta — Wakad'];

const SEED_USERS: ManagedUser[] = [
  { id: 'admin', name: 'Nilesh (Brand Owner)', email: 'admin@franchise.app', role: 'SUPER_ADMIN', franchise: 'All Franchises', allow: [], deny: [] },
  { id: 'owner', name: 'Sukhkarta Owner', email: 'owner@sukhkarta.app', role: 'FRANCHISE_OWNER', franchise: FRANCHISES[0], allow: [], deny: [] },
  { id: 'manager', name: 'Branch Manager', email: 'manager@sukhkarta.app', role: 'MANAGER', franchise: FRANCHISES[0], allow: [], deny: [] },
  { id: 'staff', name: 'Counter Staff', email: 'staff@sukhkarta.app', role: 'STAFF', franchise: FRANCHISES[0], allow: [], deny: [] }
];

/**
 * Mock authentication + user store. No real backend: a "login" simply selects
 * one of the seeded demo users (by role). Per-user allow/deny overrides are
 * persisted to localStorage and edited from the Access Control admin page.
 */
@Injectable({ providedIn: 'root' })
export class MockAuthService {
  private readonly users: ManagedUser[];
  private readonly current$: BehaviorSubject<ManagedUser | null>;
  readonly currentUser$: Observable<ManagedUser | null>;

  constructor(private moduleConfig: ModuleConfigService) {
    this.users = this.loadUsers();
    this.current$ = new BehaviorSubject<ManagedUser | null>(this.restoreSession());
    this.currentUser$ = this.current$.asObservable();
  }

  private loadUsers(): ManagedUser[] {
    const base = SEED_USERS.map(u => ({ ...u, allow: [...u.allow], deny: [...u.deny] }));
    try {
      const raw = localStorage.getItem(OVERRIDES_KEY);
      if (raw) {
        const saved = JSON.parse(raw) as Record<string, { allow: FeatureKey[]; deny: FeatureKey[] }>;
        base.forEach(u => {
          if (saved[u.id]) {
            u.allow = saved[u.id].allow ?? [];
            u.deny = saved[u.id].deny ?? [];
          }
        });
      }
    } catch {
      /* ignore */
    }
    return base;
  }

  private persistOverrides(): void {
    const map: Record<string, { allow: FeatureKey[]; deny: FeatureKey[] }> = {};
    this.users.forEach(u => (map[u.id] = { allow: u.allow, deny: u.deny }));
    try {
      localStorage.setItem(OVERRIDES_KEY, JSON.stringify(map));
    } catch {
      /* ignore */
    }
  }

  private restoreSession(): ManagedUser | null {
    const id = localStorage.getItem(SESSION_KEY);
    return id ? this.users.find(u => u.id === id) ?? null : null;
  }

  listUsers(): ManagedUser[] {
    return this.users.map(u => ({ ...u, allow: [...u.allow], deny: [...u.deny] }));
  }

  isAuthenticated(): boolean {
    return this.current$.value !== null;
  }

  currentUser(): ManagedUser | null {
    return this.current$.value;
  }

  loginAs(userId: string): ManagedUser | null {
    const user = this.users.find(u => u.id === userId) ?? null;
    if (user) {
      localStorage.setItem(SESSION_KEY, user.id);
      this.current$.next(user);
    }
    return user;
  }

  logout(): void {
    localStorage.removeItem(SESSION_KEY);
    this.current$.next(null);
  }

  /** Role defaults + allow − deny, then minus any features whose module is disabled. */
  effectiveFeaturesFor(user: ManagedUser): FeatureKey[] {
    const set = new Set<FeatureKey>(featuresForRole(user.role));
    user.allow.forEach(f => set.add(f));
    user.deny.forEach(f => set.delete(f));
    return [...set].filter(f => this.moduleConfig.isFeatureEnabled(f));
  }

  effectiveFeatures(): FeatureKey[] {
    const user = this.current$.value;
    return user ? this.effectiveFeaturesFor(user) : [];
  }

  hasFeature(feature: FeatureKey): boolean {
    return this.effectiveFeatures().includes(feature);
  }

  updateOverrides(userId: string, allow: FeatureKey[], deny: FeatureKey[]): void {
    const user = this.users.find(u => u.id === userId);
    if (!user) {
      return;
    }
    user.allow = [...allow];
    user.deny = [...deny];
    this.persistOverrides();
    // If editing the logged-in user, re-emit so the shell refreshes nav.
    if (this.current$.value?.id === userId) {
      this.current$.next(user);
    }
  }
}
