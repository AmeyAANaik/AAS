import { CommonModule } from '@angular/common';
import { Component, OnDestroy } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { NavigationEnd, Router, RouterModule } from '@angular/router';
import { filter, Subscription } from 'rxjs';
import { MockAuthService } from '../auth/mock-auth.service';
import { BerryThemeService } from '../shared/services/berry-theme.service';
import { ModuleConfigService } from '../core/module-config.service';
import { FeatureKey, roleLabel } from '../core/rbac';
import { BranchStoreService } from '../master-data/branch-store.service';

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
  franchises: string[] = [];
  selectedFranchise = '';

  readonly allNavSections: ShellNavSection[] = [
    {
      title: 'Home',
      links: [{ label: 'Dashboard', icon: 'dashboard', route: '/dashboard', feature: 'dashboard.view' }]
    },
    {
      title: 'Master Data',
      links: [
        { label: 'Master Data', icon: 'storage', route: '/master-data', feature: 'master-data.view' },
        { label: 'Branches', icon: 'store', route: '/master-data/branches', feature: 'branches.manage' },
        { label: 'Vendors', icon: 'local_shipping', route: '/master-data/vendors', feature: 'vendors.view' },
        { label: 'Products', icon: 'inventory_2', route: '/master-data/products', feature: 'inventory.manage' },
        { label: 'Product Categories', icon: 'category', route: '/master-data/product-categories', feature: 'inventory.manage' },
        { label: 'Employees', icon: 'badge', route: '/master-data/employees', feature: 'employees.manage' },
        { label: 'Payment Modes', icon: 'payments', route: '/master-data/payment-modes', feature: 'payment-modes.manage' },
        { label: 'Expense Categories', icon: 'receipt_long', route: '/master-data/expense-categories', feature: 'expense-categories.manage' },
        { label: 'Royalty Setup', icon: 'percent', route: '/master-data/royalty-config', feature: 'royalty-config.manage' }
      ]
    },
    {
      title: 'Inventory',
      links: [
        { label: 'Purchases', icon: 'add_shopping_cart', route: '/inventory/purchases', feature: 'inventory.purchase' },
        { label: 'Consumption', icon: 'restaurant', route: '/inventory/consumption', feature: 'inventory.consume' },
        { label: 'Stock Reports', icon: 'assessment', route: '/inventory/reports', feature: 'inventory.view' }
      ]
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
  expandedSections = new Set<string>(['Home']);

  private readonly routeMap: Record<string, string[]> = {
    '/dashboard': ['Home', 'Dashboard'],
    '/master-data': ['Master Data'],
    '/master-data/branches': ['Master Data', 'Branches'],
    '/master-data/vendors': ['Master Data', 'Vendors'],
    '/master-data/products': ['Master Data', 'Products'],
    '/master-data/product-categories': ['Master Data', 'Product Categories'],
    '/master-data/employees': ['Master Data', 'Employees'],
    '/master-data/payment-modes': ['Master Data', 'Payment Modes'],
    '/master-data/expense-categories': ['Master Data', 'Expense Categories'],
    '/master-data/royalty-config': ['Master Data', 'Royalty Setup'],
    '/inventory/purchases': ['Inventory', 'Purchases'],
    '/inventory/consumption': ['Inventory', 'Consumption'],
    '/inventory/reports': ['Inventory', 'Stock Reports'],
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
  private readonly branchSub: Subscription;

  constructor(
    private router: Router,
    private auth: MockAuthService,
    private themeService: BerryThemeService,
    private moduleConfig: ModuleConfigService,
    private branchStore: BranchStoreService
  ) {
    this.routerSub = this.router.events
      .pipe(filter(event => event instanceof NavigationEnd))
      .subscribe(event => {
        const url = this.normalizeUrl((event as NavigationEnd).urlAfterRedirects);
        this.breadcrumbs = this.metaForUrl(url).join(' / ');
        this.expandSectionForUrl(url);
        this.isNavOpen = false;
        this.closeUserMenu();
      });

    this.currentTheme = this.themeService.getCurrentTheme();
    this.themeSub = this.themeService.theme$.subscribe(theme => (this.currentTheme = theme));

    this.authSub = this.auth.currentUser$.subscribe(() => this.refreshProfile());
    this.moduleSub = this.moduleConfig.changes$.subscribe(() => this.refreshProfile());
    this.branchSub = this.branchStore.changes$.subscribe(branches => {
      this.franchises = branches.map(branch => branch.name);
      if (this.isSuperAdmin && !this.franchises.includes(this.selectedFranchise)) {
        this.selectedFranchise = this.franchises[0] ?? '';
      }
    });
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
    this.franchises = this.branchStore.branchNamesSnapshot();
    this.selectedFranchise = this.isSuperAdmin ? this.franchises[0] : user.franchise;
    this.navSections = this.buildNavSections(this.auth.effectiveFeatures());
    this.expandSectionForUrl(this.normalizeUrl(this.router.url));
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
  toggleSection(title: string): void {
    if (this.expandedSections.has(title)) {
      this.expandedSections.delete(title);
    } else {
      this.expandedSections.add(title);
    }
  }
  isSectionExpanded(title: string): boolean { return this.expandedSections.has(title); }

  ngOnDestroy(): void {
    this.routerSub.unsubscribe();
    this.themeSub.unsubscribe();
    this.authSub.unsubscribe();
    this.moduleSub.unsubscribe();
    this.branchSub.unsubscribe();
  }

  private initialsFor(value: string, fallback: string): string {
    const parts = String(value ?? '').trim().split(/\s+/).filter(Boolean).slice(0, 2);
    return parts.length ? parts.map(p => p[0]?.toUpperCase() ?? '').join('') : fallback;
  }

  private expandSectionForUrl(url: string): void {
    const section = this.navSections.find(group =>
      group.links.some(link => url === link.route || url.startsWith(`${link.route}/`))
    );
    if (section) {
      this.expandedSections.add(section.title);
    }
  }
}
