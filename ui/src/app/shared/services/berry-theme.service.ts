import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable } from 'rxjs';

@Injectable({
  providedIn: 'root',
})
export class BerryThemeService {
  private readonly THEME_STORAGE_KEY = 'berry-aas-theme';
  private themeSubject: BehaviorSubject<'light' | 'dark'>;
  public theme$: Observable<'light' | 'dark'>;

  constructor() {
    const savedTheme = this.getSavedTheme();
    this.themeSubject = new BehaviorSubject<'light' | 'dark'>(savedTheme);
    this.theme$ = this.themeSubject.asObservable();
    this.applyTheme(savedTheme);
  }

  /**
   * Get the currently applied theme
   */
  getCurrentTheme(): 'light' | 'dark' {
    return this.themeSubject.value;
  }

  /**
   * Toggle between light and dark theme
   */
  toggleTheme(): void {
    const newTheme = this.getCurrentTheme() === 'light' ? 'dark' : 'light';
    this.setTheme(newTheme);
  }

  /**
   * Set a specific theme
   */
  setTheme(theme: 'light' | 'dark'): void {
    this.applyTheme(theme);
    this.themeSubject.next(theme);
    this.saveTheme(theme);
  }

  /**
   * Apply theme to DOM
   */
  private applyTheme(theme: 'light' | 'dark'): void {
    const htmlElement = document.documentElement;
    if (theme === 'dark') {
      htmlElement.setAttribute('data-theme', 'dark');
    } else {
      htmlElement.removeAttribute('data-theme');
    }
  }

  /**
   * Get saved theme from localStorage
   */
  private getSavedTheme(): 'light' | 'dark' {
    try {
      const saved = localStorage.getItem(this.THEME_STORAGE_KEY);
      if (saved === 'dark' || saved === 'light') {
        return saved;
      }
    } catch (e) {
      console.warn('Failed to get theme from localStorage:', e);
    }

    // Check system preference
    if (window.matchMedia && window.matchMedia('(prefers-color-scheme: dark)').matches) {
      return 'dark';
    }

    return 'light';
  }

  /**
   * Save theme to localStorage
   */
  private saveTheme(theme: 'light' | 'dark'): void {
    try {
      localStorage.setItem(this.THEME_STORAGE_KEY, theme);
    } catch (e) {
      console.warn('Failed to save theme to localStorage:', e);
    }
  }
}
