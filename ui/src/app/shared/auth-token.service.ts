import { Injectable } from '@angular/core';

const STORAGE_KEY = 'aas_auth_token';
const ROLE_KEY = 'aas_auth_role';

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

  setToken(token: string | null): void {
    if (!token) {
      localStorage.removeItem(STORAGE_KEY);
      localStorage.removeItem(ROLE_KEY);
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
}
