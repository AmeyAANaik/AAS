import { CommonModule } from '@angular/common';
import { Component, OnDestroy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NavigationEnd, Router, RouterModule } from '@angular/router';
import { filter, Subscription } from 'rxjs';
import { MockAuthService, FRANCHISES } from '../auth/mock-auth.service';
import { BerryThemeService } from '../shared/services/berry-theme.service';
import { ModuleConfigService } from '../core/module-config.service';
import { FeatureKey, roleLabel } from '../core/rbac';

type ShellNavLink = { label: string; icon: string; route: string; feature: FeatureKey };
type ShellNavSection = { title: string; links: ShellNavLink[] };

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [CommonModule, FormsModule, RouterModule],
  templateUrl: './app-shell.component.html',
  styleUrl: './app-shell.component.css'
})
export class AppShellComponent implements OnDestroy {
  breadcrumbs = 'Home / Dashboard';
  companyName = 'Franchise Console';
  companySubline = 'Restaurant Franchise Management';
  companyAvatarText = 'FC';
  userName = 'User';
  userRole = '';
  userEmail = '';
  userAvatarText = 'U';
  isUserMenuOpen = false;
  isNavOpen = false;
  currentTheme: 'light' | 'dark' = 'light';

  isSuperAdmin = false;
  franchises = FRANCHISES;
  selectedFranchise = '';

  readonly allNavSections: ShellNavSection[] = [
    {
      title: 'Home',
      links: [{ label: 'Dashboard', icon: 'dashboard', route: '/dashboard', feature: 'dashboard.view' }]
    },
    {
      title: 'Inventory',
      links: [
        { label: 'Products', icon: 'inventory_2', route: '/inventory/products', feature: 'inventory.manage' },
        { label: 'Purchases', icon: 'add_shopping_cart', route: '/inventory/purchases', feature: 'inventory.purchase' },
        { label: 'Consumption', icon: 'restaurant', route: '/inventory/consumption', feature: 'inventory.consume' },
        { label: 'Stock Reports', icon: 'assessment', route: '/inventory/reports', feature: 'inventory.view' }
      ]
    },
    {
      title: 'Vendors',
      links: [{ label: 'Vendors', icon: 'local_shipping', route: '/vendors', feature: 'vendors.view' }]
    },
    {
      title: 'Operations',
      links: [
        { label: 'Daily Sales', icon: 'point_of_sale', route: '/sales', feature: 'sales.view' },
        { label: 'Expenses', icon: 'account_balance_wallet', route: '/expenses', feature: 'expenses.view' },
        { label: 'Salary', icon: 'badge', route: '/salary', feature: 'salary.view' },
        { label: 'Royalty', icon: 'percent', route: '/royalty', feature: 'royalty.view' },
        { label: 'Profit & Loss', icon: 'trending_up', route: '/pnl', feature: 'pnl.view' }
      ]
    },
    {
      title: 'Administration',
      links: [
        { label: 'Module Settings', icon: 'tune', route: '/admin/modules', feature: 'admin.modules' },
        { label: 'Access Control', icon: 'admin_panel_settings', route: '/admin/access', feature: 'admin.access' }
      ]
    }
  ];
  navSections: ShellNavSection[] = [];

  private readonly routeMap: Record<string, string[]> = {
    '/dashboard': ['Home', 'Dashboard'],
    '/inventory/products': ['Inventory', 'Products'],
    '/inventory/purchases': ['Inventory', 'Purchases'],
    '/inventory/consumption': ['Inventory', 'Consumption'],
    '/inventory/reports': ['Inventory', 'Stock Reports'],
    '/vendors': ['Vendors', 'Vendors'],
    '/sales': ['Operations', 'Daily Sales'],
    '/expenses': ['Operations', 'Expenses'],
    '/salary': ['Operations', 'Salary'],
    '/royalty': ['Operations', 'Royalty'],
    '/pnl': ['Operations', 'Profit & Loss'],
    '/admin/modules': ['Administration', 'Module Settings'],
    '/admin/access': ['Administration', 'Access Control']
  };

  private readonly routerSub: Subscription;
  private readonly themeSub: Subscription;
  private readonly authSub: Subscription;
  private readonly moduleSub: Subscription;

  constructor(
    private router: Router,
    private auth: MockAuthService,
    private themeService: BerryThemeService,
    private moduleConfig: ModuleConfigService
  ) {
    this.routerSub = this.router.events
      .pipe(filter(event => event instanceof NavigationEnd))
      .subscribe(event => {
        const url = this.normalizeUrl((event as NavigationEnd).urlAfterRedirects);
        this.breadcrumbs = this.metaForUrl(url).join(' / ');
        this.isNavOpen = false;
        this.closeUserMenu();
      });

    this.currentTheme = this.themeService.getCurrentTheme();
    this.themeSub = this.themeService.theme$.subscribe(theme => (this.currentTheme = theme));

    this.authSub = this.auth.currentUser$.subscribe(() => this.refreshProfile());
    this.moduleSub = this.moduleConfig.changes$.subscribe(() => this.refreshProfile());
  }

  private refreshProfile(): void {
    const user = this.auth.currentUser();
    if (!user) {
      this.navSections = [];
      return;
    }
    this.userName = user.name;
    this.userEmail = user.email;
    this.userRole = roleLabel(user.role);
    this.userAvatarText = this.initialsFor(user.name, 'U');
    this.isSuperAdmin = user.role === 'SUPER_ADMIN';
    this.selectedFranchise = this.isSuperAdmin ? this.franchises[0] : user.franchise;
    this.navSections = this.buildNavSections(this.auth.effectiveFeatures());
  }

  private buildNavSections(features: FeatureKey[]): ShellNavSection[] {
    return this.allNavSections
      .map(section => ({ ...section, links: section.links.filter(link => features.includes(link.feature)) }))
      .filter(section => section.links.length > 0);
  }

  private metaForUrl(url: string): string[] {
    const candidates = Object.keys(this.routeMap).sort((a, b) => b.length - a.length);
    const match = candidates.find(key => url === key || url.startsWith(`${key}/`));
    return this.routeMap[match ?? '/dashboard'] ?? ['Home'];
  }

  private normalizeUrl(url: string): string {
    const withoutQuery = url.split('?')[0];
    return withoutQuery.length > 1 && withoutQuery.endsWith('/') ? withoutQuery.slice(0, -1) : withoutQuery;
  }

  logout(): void {
    this.auth.logout();
    this.router.navigateByUrl('/login');
  }

  toggleUserMenu(): void { this.isUserMenuOpen = !this.isUserMenuOpen; }
  closeUserMenu(): void { this.isUserMenuOpen = false; }
  toggleNav(): void { this.isNavOpen = !this.isNavOpen; }
  closeNav(): void { this.isNavOpen = false; }
  toggleTheme(): void { this.themeService.toggleTheme(); }

  ngOnDestroy(): void {
    this.routerSub.unsubscribe();
    this.themeSub.unsubscribe();
    this.authSub.unsubscribe();
    this.moduleSub.unsubscribe();
  }

  private initialsFor(value: string, fallback: string): string {
    const parts = String(value ?? '').trim().split(/\s+/).filter(Boolean).slice(0, 2);
    return parts.length ? parts.map(p => p[0]?.toUpperCase() ?? '').join('') : fallback;
  }
}
