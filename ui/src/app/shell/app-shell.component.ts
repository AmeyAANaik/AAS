import { CommonModule } from '@angular/common';
import { Component, OnDestroy } from '@angular/core';
import { NavigationEnd, Router, RouterModule } from '@angular/router';
import { filter, Subscription } from 'rxjs';
import { AuthTokenService } from '../shared/auth-token.service';
import { CompanyContextService } from '../shared/company-context.service';
import { UserAccessService } from '../shared/user-access.service';
import { BerryThemeService } from '../shared/services/berry-theme.service';
import { MasterDataReviewService } from '../master-data-review/master-data-review.service';
import { BillReviewService } from '../bill-review/bill-review.service';

type ShellRouteMeta = { breadcrumbs: string[] };
type ShellNavLink = { label: string; icon: string; route: string; feature: string };
type ShellNavSection = { title: string; links: ShellNavLink[] };

@Component({
  selector: 'app-shell',
  standalone: true,
  imports: [CommonModule, RouterModule],
  templateUrl: './app-shell.component.html',
  styleUrl: './app-shell.component.css'
})
export class AppShellComponent implements OnDestroy {
  breadcrumbs = 'Home / Dashboard';
  companyName = 'SCM Console';
  companySubline = 'Supply chain workspace';
  companyAvatarText = 'SC';
  companyLogoUrl = '';
  branchName = 'No branch';
  branchLocation = '';
  branchAvatarText = 'BR';
  branchLogoUrl = '';
  userName = 'User';
  userRole = '';
  userEmail = '';
  userAvatarText = 'U';
  isUserMenuOpen = false;
  isNavOpen = false;
  currentTheme: 'light' | 'dark' = 'light';
  masterDataReviewCount = 0;
  billReviewCount = 0;
  readonly masterDataReviewFeature = 'master_data_review.view';
  readonly billReviewFeature = 'bill_review.view';
  readonly allNavSections: ShellNavSection[] = [
    {
      title: 'Home',
      links: [{ label: 'Dashboard', icon: 'dashboard', route: '/admin/dashboard', feature: 'dashboard.view' }]
    },
    {
      title: 'Procure',
      links: [{ label: 'Orders', icon: 'receipt_long', route: '/orders', feature: 'orders.view' }]
    },
    {
      title: 'Inventory',
      links: [{ label: 'Stock', icon: 'inventory_2', route: '/stock', feature: 'stock.view' }]
    },
    {
      title: 'Finance',
      links: [{ label: 'Bills / Invoices', icon: 'account_balance_wallet', route: '/bills', feature: 'bills.view' }]
    },
    {
      title: 'Master Data',
      links: [
        { label: 'Vendors', icon: 'local_shipping', route: '/vendors', feature: 'master_data.view' },
        { label: 'Branches', icon: 'hub', route: '/branches', feature: 'master_data.view' },
        { label: 'Categories', icon: 'category', route: '/categories', feature: 'master_data.view' },
        { label: 'Items', icon: 'inventory', route: '/items', feature: 'master_data.view' }
      ]
    },
    {
      title: 'Administration',
      links: [
        { label: 'Master Data Review', icon: 'notifications_active', route: '/master-data-review', feature: 'master_data_review.view' },
        { label: 'Bill Review', icon: 'fact_check', route: '/bill-review', feature: 'bill_review.view' }
      ]
    },
    {
      title: 'Reports',
      links: [{ label: 'Reports', icon: 'query_stats', route: '/reports', feature: 'reports.view' }]
    },
    {
      title: 'Operations',
      links: [
        { label: 'Vendor Operations', icon: 'local_shipping', route: '/vendor-ops', feature: 'vendor_ops.view' },
        { label: 'Branch Operations', icon: 'storefront', route: '/branch-ops', feature: 'branch_ops.view' }
      ]
    }
  ];
  navSections: ShellNavSection[] = [];
  private readonly routeMap: Record<string, ShellRouteMeta> = {
    '/admin/dashboard': { breadcrumbs: ['Home', 'Dashboard'] },
    '/vendor-ops': { breadcrumbs: ['Home', 'Vendor Operations'] },
    '/branch-ops': { breadcrumbs: ['Home', 'Branch Operations'] },
    '/orders': { breadcrumbs: ['Procure', 'Orders'] },
    '/stock': { breadcrumbs: ['Inventory', 'Stock'] },
    '/bills': { breadcrumbs: ['Finance', 'Bills / Invoices'] },
    '/user-settings': { breadcrumbs: ['Home', 'User Details'] },
    '/master-data-review': { breadcrumbs: ['Administration', 'Master Data Review'] },
    '/bill-review': { breadcrumbs: ['Administration', 'Bill Review'] },
    '/vendors': { breadcrumbs: ['Master Data', 'Vendors'] },
    '/branches': { breadcrumbs: ['Master Data', 'Branches'] },
    '/company-settings': { breadcrumbs: ['Home', 'Company Settings'] },
    '/categories': { breadcrumbs: ['Master Data', 'Categories'] },
    '/items': { breadcrumbs: ['Master Data', 'Items'] },
    '/reports': { breadcrumbs: ['Reports'] }
  };
  private readonly subscription: Subscription;
  private readonly themeSubscription: Subscription;
  private readonly masterDataReviewSubscription: Subscription;
  private readonly billReviewSubscription: Subscription;
  private readonly companyContextSubscription: Subscription;

  constructor(
    private router: Router,
    private tokenStore: AuthTokenService,
    private companyContextService: CompanyContextService,
    private userAccess: UserAccessService,
    private themeService: BerryThemeService,
    private masterDataReviewService: MasterDataReviewService,
    private billReviewService: BillReviewService
  ) {
    this.subscription = this.router.events
      .pipe(filter(event => event instanceof NavigationEnd))
      .subscribe(event => {
        const url = this.normalizeUrl((event as NavigationEnd).urlAfterRedirects);
        const meta = this.metaForUrl(url);
        this.breadcrumbs = meta.breadcrumbs.join(' / ');
        this.isNavOpen = false;
        this.closeUserMenu();
    });
    this.loadCompanyContext();
    this.companyContextSubscription = this.companyContextService.contextChanged$.subscribe(() => this.loadCompanyContext());
    this.loadUserProfile();
    this.currentTheme = this.themeService.getCurrentTheme();
    this.themeSubscription = this.themeService.theme$.subscribe(theme => {
      this.currentTheme = theme;
    });
    this.masterDataReviewSubscription = this.masterDataReviewService.refresh$.subscribe(() => {
      if (this.canAccess(this.masterDataReviewFeature)) {
        this.loadMasterDataReviewCount();
      }
    });
    this.billReviewSubscription = this.billReviewService.refresh$.subscribe(() => {
      if (this.canAccess(this.billReviewFeature)) {
        this.loadBillReviewCount();
      }
    });
  }

  private metaForUrl(url: string): ShellRouteMeta {
    const candidates = Object.keys(this.routeMap).sort((a, b) => b.length - a.length);
    const match = candidates.find(key => url === key || url.startsWith(`${key}/`));
    return this.routeMap[match ?? '/admin/dashboard'];
  }

  private normalizeUrl(url: string): string {
    const withoutQuery = url.split('?')[0];
    if (withoutQuery.length > 1 && withoutQuery.endsWith('/')) {
      return withoutQuery.slice(0, -1);
    }
    return withoutQuery;
  }

  logout(): void {
    this.tokenStore.setToken(null);
    this.router.navigateByUrl('/login');
  }

  toggleUserMenu(): void {
    this.isUserMenuOpen = !this.isUserMenuOpen;
  }

  closeUserMenu(): void {
    this.isUserMenuOpen = false;
  }

  toggleNav(): void {
    this.isNavOpen = !this.isNavOpen;
  }

  closeNav(): void {
    this.isNavOpen = false;
  }

  toggleTheme(): void {
    this.themeService.toggleTheme();
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
    this.themeSubscription.unsubscribe();
    this.masterDataReviewSubscription.unsubscribe();
    this.billReviewSubscription.unsubscribe();
    this.companyContextSubscription.unsubscribe();
  }

  private loadCompanyContext(): void {
    if (!this.tokenStore.getToken()) {
      return;
    }
    this.companyContextService.getContext().subscribe({
      next: context => {
        const company = context.company;
        const branch = context.branch;
        this.companyName = company?.name?.trim() || 'SCM Console';
        this.companySubline = company?.default_currency?.trim() || 'Supply chain workspace';
        this.companyLogoUrl = company?.logo_url?.trim() || '';
        this.companyAvatarText = this.initialsFor(this.companyName, 'SC');
        this.branchName = branch?.name?.trim() || 'No branch';
        this.branchLocation = branch?.location?.trim() || '';
        this.branchLogoUrl = branch?.logo_url?.trim() || '';
        this.branchAvatarText = this.initialsFor(this.branchName, 'BR');
      },
      error: () => {
        this.companyName = 'SCM Console';
        this.companySubline = 'Supply chain workspace';
        this.branchName = 'No branch';
        this.branchLocation = '';
      }
    });
  }

  private loadUserProfile(): void {
    this.userAccess.getProfile(true).subscribe({
      next: profile => {
        this.userName = String(profile['full_name'] ?? profile['name'] ?? profile['email'] ?? 'User').trim() || 'User';
        this.userEmail = String(profile['email'] ?? '').trim();
        this.userRole = this.formatRole(String(profile['role'] ?? this.tokenStore.getRole() ?? '').trim());
        this.userAvatarText = this.initialsFor(this.userName, 'U');
        const features = Array.isArray(profile['features']) ? profile['features'].map(value => String(value)) : this.tokenStore.getFeatures();
        this.navSections = this.buildNavSections(features);
        this.loadMasterDataReviewCount(features);
        this.loadBillReviewCount(features);
      },
      error: () => {
        const role = this.formatRole(String(this.tokenStore.getRole() ?? '').trim());
        this.userRole = role;
        this.userAvatarText = this.initialsFor(this.userName, 'U');
        const features = this.tokenStore.getFeatures();
        this.navSections = this.buildNavSections(features);
        this.loadMasterDataReviewCount(features);
        this.loadBillReviewCount(features);
      }
    });
  }

  private formatRole(role: string): string {
    if (!role) {
      return '';
    }
    if (role.toLowerCase() === 'shop') {
      return 'Branch';
    }
    return role
      .split(/[_\s]+/)
      .filter(Boolean)
      .map(part => part[0]?.toUpperCase() + part.slice(1).toLowerCase())
      .join(' ');
  }

  private buildNavSections(features: string[]): ShellNavSection[] {
    return this.allNavSections
      .map(section => ({
        ...section,
        links: section.links.filter(link => features.includes(link.feature))
      }))
      .filter(section => section.links.length > 0);
  }

  canAccess(feature: string): boolean {
    return this.tokenStore.getFeatures().includes(feature);
  }

  private loadMasterDataReviewCount(features: string[] = this.tokenStore.getFeatures()): void {
    if (!features.includes(this.masterDataReviewFeature)) {
      this.masterDataReviewCount = 0;
      return;
    }
    this.masterDataReviewService.getPendingCount().subscribe({
      next: result => {
        this.masterDataReviewCount = Number(result?.pendingCount ?? 0);
      },
      error: () => {
        this.masterDataReviewCount = 0;
      }
    });
  }

  private loadBillReviewCount(features: string[] = this.tokenStore.getFeatures()): void {
    if (!features.includes(this.billReviewFeature)) {
      this.billReviewCount = 0;
      return;
    }
    this.billReviewService.getPendingCount().subscribe({
      next: result => {
        this.billReviewCount = Number(result?.pendingCount ?? 0);
      },
      error: () => {
        this.billReviewCount = 0;
      }
    });
  }

  get totalReviewCount(): number {
    return (Number(this.masterDataReviewCount) || 0) + (Number(this.billReviewCount) || 0);
  }

  get reviewsRoute(): string {
    return this.canAccess(this.billReviewFeature) ? '/bill-review' : '/master-data-review';
  }

  get canSeeReviewsChip(): boolean {
    return this.canAccess(this.masterDataReviewFeature) || this.canAccess(this.billReviewFeature);
  }

  private initialsFor(value: string, fallback: string): string {
    const parts = String(value ?? '')
      .trim()
      .split(/\s+/)
      .filter(Boolean)
      .slice(0, 2);
    if (!parts.length) {
      return fallback;
    }
    return parts.map(part => part[0]?.toUpperCase() ?? '').join('');
  }
}
