import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { of, Subject } from 'rxjs';
import { AppShellComponent } from './app-shell.component';
import { AuthTokenService } from '../shared/auth-token.service';
import { CompanyContextService } from '../shared/company-context.service';
import { UserAccessService } from '../shared/user-access.service';
import { BerryThemeService } from '../shared/services/berry-theme.service';
import { MasterDataReviewService } from '../master-data-review/master-data-review.service';

describe('AppShellComponent', () => {
  let fixture: ComponentFixture<AppShellComponent>;
  let tokenStore: AuthTokenService;

  function configureShell(features: string[]) {
    const refresh$ = new Subject<void>();
    TestBed.configureTestingModule({
      imports: [AppShellComponent, RouterTestingModule],
      providers: [
        AuthTokenService,
        {
          provide: CompanyContextService,
          useValue: {
            getContext: () => of({
              company: { name: 'AAS', default_currency: 'INR' },
              branch: { name: 'Branch 1', location: 'Pune' }
            })
          }
        },
        {
          provide: UserAccessService,
          useValue: {
            getProfile: () => of({
              full_name: 'Admin User',
              role: 'admin',
              features,
              homeRoute: '/admin/dashboard'
            })
          }
        },
        {
          provide: BerryThemeService,
          useValue: {
            getCurrentTheme: () => 'light',
            theme$: of<'light' | 'dark'>('light'),
            toggleTheme: () => void 0
          }
        },
        {
          provide: MasterDataReviewService,
          useValue: {
            getPendingCount: () => of({ pendingCount: 4 }),
            refresh$
          }
        }
      ]
    });
    tokenStore = TestBed.inject(AuthTokenService);
    tokenStore.setToken('token');
    tokenStore.setFeatures(features);
    tokenStore.setRole('admin');
    fixture = TestBed.createComponent(AppShellComponent);
    fixture.detectChanges();
  }

  afterEach(() => {
    tokenStore?.setToken(null);
    TestBed.resetTestingModule();
  });

  it('shows the review bell for admins with the feature', () => {
    configureShell(['dashboard.view', 'master_data_review.view']);

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Reviews');
    expect(text).toContain('4');
  });

  it('hides the review bell when the feature is missing', () => {
    configureShell(['dashboard.view']);

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).not.toContain('Reviews');
  });
});
