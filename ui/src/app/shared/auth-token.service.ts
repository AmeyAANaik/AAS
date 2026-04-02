import { Injectable } from '@angular/core';

const STORAGE_KEY = 'aas_auth_token';
const ROLE_KEY = 'aas_auth_role';
const FEATURES_KEY = 'aas_auth_features';
const HOME_ROUTE_KEY = 'aas_auth_home_route';

@Injectable({
  providedIn: 'root'
})
export class AuthTokenService {
  getToken(): string | null {
    return localStorage.getItem(STORAGE_KEY);
  }

  getRole(): string | null {
    return localStorage.getItem(ROLE_KEY);
  }

  getFeatures(): string[] {
    const raw = localStorage.getItem(FEATURES_KEY);
    if (!raw) {
      return [];
    }
    try {
      const parsed = JSON.parse(raw);
      return Array.isArray(parsed) ? parsed.map(value => String(value)) : [];
    } catch {
      return [];
    }
  }

  getHomeRoute(): string | null {
    return localStorage.getItem(HOME_ROUTE_KEY);
  }

  setToken(token: string | null): void {
    if (!token) {
      localStorage.removeItem(STORAGE_KEY);
      localStorage.removeItem(ROLE_KEY);
      localStorage.removeItem(FEATURES_KEY);
      localStorage.removeItem(HOME_ROUTE_KEY);
      return;
    }
    localStorage.setItem(STORAGE_KEY, token);
  }

  setRole(role: string | null): void {
    if (!role) {
      localStorage.removeItem(ROLE_KEY);
      return;
    }
    localStorage.setItem(ROLE_KEY, role);
  }

  setFeatures(features: string[] | null): void {
    if (!features || !features.length) {
      localStorage.removeItem(FEATURES_KEY);
      return;
    }
    localStorage.setItem(FEATURES_KEY, JSON.stringify(features));
  }

  setHomeRoute(homeRoute: string | null): void {
    if (!homeRoute) {
      localStorage.removeItem(HOME_ROUTE_KEY);
      return;
    }
    localStorage.setItem(HOME_ROUTE_KEY, homeRoute);
  }
}
