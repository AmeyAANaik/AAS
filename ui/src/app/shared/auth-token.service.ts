import { Injectable } from '@angular/core';

const STORAGE_KEY = 'aas_auth_token';

@Injectable({
  providedIn: 'root'
})
export class AuthTokenService {
  getToken(): string | null {
    return localStorage.getItem(STORAGE_KEY);
  }

  setToken(token: string | null): void {
    if (!token) {
      localStorage.removeItem(STORAGE_KEY);
      return;
    }
    localStorage.setItem(STORAGE_KEY, token);
  }
}
