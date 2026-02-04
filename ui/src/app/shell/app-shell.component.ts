import { CommonModule } from '@angular/common';
import { Component, OnDestroy } from '@angular/core';
import { NavigationEnd, Router, RouterModule } from '@angular/router';
import { filter, Subscription } from 'rxjs';
import { AuthTokenService } from '../shared/auth-token.service';

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
  private readonly routeMap: Record<string, ShellRouteMeta> = {
    '/admin/dashboard': { breadcrumbs: ['Home', 'Dashboard'] },
    '/orders': { breadcrumbs: ['Procure', 'Orders'] },
    '/stock': { breadcrumbs: ['Inventory', 'Stock'] },
    '/bills': { breadcrumbs: ['Finance', 'Bills / Invoices'] },
    '/vendors': { breadcrumbs: ['Master Data', 'Vendors'] },
    '/branches': { breadcrumbs: ['Master Data', 'Branches'] },
    '/categories': { breadcrumbs: ['Master Data', 'Categories'] },
    '/items': { breadcrumbs: ['Master Data', 'Items'] },
    '/reports': { breadcrumbs: ['Reports'] }
  };
  private readonly subscription: Subscription;

  constructor(private router: Router, private tokenStore: AuthTokenService) {
    this.subscription = this.router.events
      .pipe(filter(event => event instanceof NavigationEnd))
      .subscribe(event => {
        const url = (event as NavigationEnd).urlAfterRedirects.split('?')[0];
        const meta = this.routeMap[url] ?? this.routeMap['/admin/dashboard'];
        this.breadcrumbs = meta.breadcrumbs.join(' / ');
      });
  }

  logout(): void {
    this.tokenStore.setToken(null);
    this.router.navigateByUrl('/login');
  }

  ngOnDestroy(): void {
    this.subscription.unsubscribe();
  }
}
