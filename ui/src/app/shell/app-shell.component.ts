import { CommonModule } from '@angular/common';
import { Component, OnDestroy } from '@angular/core';
import { NavigationEnd, Router, RouterModule } from '@angular/router';
import { filter, Subscription } from 'rxjs';
import { AuthTokenService } from '../shared/auth-token.service';
import { CompanyContextService } from '../shared/company-context.service';

type ShellRouteMeta = { breadcrumbs: string[] };

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
  private readonly routeMap: Record<string, ShellRouteMeta> = {
    '/admin/dashboard': { breadcrumbs: ['Home', 'Dashboard'] },
    '/vendor-ops': { breadcrumbs: ['Home', 'Vendor Operations'] },
    '/branch-ops': { breadcrumbs: ['Home', 'Branch Operations'] },
    '/orders': { breadcrumbs: ['Procure', 'Orders'] },
    '/stock': { breadcrumbs: ['Inventory', 'Stock'] },
    '/bills': { breadcrumbs: ['Finance', 'Bills / Invoices'] },
    '/vendors': { breadcrumbs: ['Master Data', 'Vendors'] },
    '/branches': { breadcrumbs: ['Master Data', 'Branches'] },
    '/company-settings': { breadcrumbs: ['Home', 'Company Settings'] },
    '/categories': { breadcrumbs: ['Master Data', 'Categories'] },
    '/items': { breadcrumbs: ['Master Data', 'Items'] },
    '/reports': { breadcrumbs: ['Reports'] }
  };
  private readonly subscription: Subscription;

  constructor(
    private router: Router,
    private tokenStore: AuthTokenService,
    private companyContextService: CompanyContextService
  ) {
    this.subscription = this.router.events
      .pipe(filter(event => event instanceof NavigationEnd))
      .subscribe(event => {
        const url = this.normalizeUrl((event as NavigationEnd).urlAfterRedirects);
        const meta = this.metaForUrl(url);
        this.breadcrumbs = meta.breadcrumbs.join(' / ');
      });
    this.loadCompanyContext();
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

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
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
