import { Component, OnInit } from '@angular/core';
import { FormBuilder, Validators } from '@angular/forms';
import { finalize } from 'rxjs/operators';
import { CompanyContextService, CompanyIdentity } from '../shared/company-context.service';
import {
  AccessControlService,
  AccessFeatureDefinition,
  AccessManagedUser
} from './access-control.service';

@Component({
  selector: 'app-company-settings-page',
  templateUrl: './company-settings-page.component.html',
  styleUrl: './company-settings-page.component.scss'
})
export class CompanySettingsPageComponent implements OnInit {
  readonly form = this.fb.group({
    name: [{ value: '', disabled: true }],
    abbr: ['', Validators.required],
    default_currency: ['', Validators.required],
    country: [''],
    default_letter_head: [''],
    tax_id: ['']
  });

  companyId = '';
  companyLogoUrl = '';
  branchName = '';
  branchLocation = '';
  isLoading = false;
  isSaving = false;
  isLoadingAccess = false;
  isSavingAccess = false;
  message = '';
  errorMessage = '';
  accessMessage = '';
  accessErrorMessage = '';
  featureCatalog: AccessFeatureDefinition[] = [];
  managedUsers: AccessManagedUser[] = [];
  selectedUserId = '';
  selectedUser: AccessManagedUser | null = null;
  allowOverrides = new Set<string>();
  denyOverrides = new Set<string>();

  constructor(
    private fb: FormBuilder,
    private companyContextService: CompanyContextService,
    private accessControlService: AccessControlService
  ) {}

  ngOnInit(): void {
    this.loadContext();
    this.loadAccessOverview();
  }

  save(): void {
    if (!this.companyId || this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.isSaving = true;
    this.errorMessage = '';
    this.message = '';
    const raw = this.form.getRawValue();
    this.companyContextService.updateCompany(this.companyId, {
      abbr: String(raw.abbr ?? '').trim(),
      default_currency: String(raw.default_currency ?? '').trim(),
      country: String(raw.country ?? '').trim(),
      default_letter_head: String(raw.default_letter_head ?? '').trim(),
      tax_id: String(raw.tax_id ?? '').trim()
    })
      .pipe(finalize(() => (this.isSaving = false)))
      .subscribe({
        next: company => {
          this.applyCompany(company);
          this.message = 'Company details updated.';
        },
        error: () => {
          this.errorMessage = 'Unable to update company details.';
        }
      });
  }

  private loadContext(): void {
    this.isLoading = true;
    this.companyContextService.getContext()
      .pipe(finalize(() => (this.isLoading = false)))
      .subscribe({
        next: context => {
          if (context.company) {
            this.applyCompany(context.company);
          }
          this.branchName = context.branch?.name ?? '';
          this.branchLocation = context.branch?.location ?? '';
        },
        error: () => {
          this.errorMessage = 'Unable to load company details.';
        }
      });
  }

  private applyCompany(company: CompanyIdentity): void {
    this.companyId = company.id;
    this.companyLogoUrl = company.logo_url ?? '';
    this.form.patchValue({
      name: company.name ?? '',
      abbr: company.abbr ?? '',
      default_currency: company.default_currency ?? '',
      country: company.country ?? '',
      default_letter_head: company.default_letter_head ?? '',
      tax_id: company.tax_id ?? ''
    });
  }

  get selectedRoleLabel(): string {
    return this.labelForRole(this.selectedUser?.role);
  }

  get selectedDefaultFeatures(): string[] {
    return Array.isArray(this.selectedUser?.default_features) ? [...this.selectedUser!.default_features!].sort() : [];
  }

  get featureGroups(): Array<{ name: string; features: AccessFeatureDefinition[] }> {
    const grouped = new Map<string, AccessFeatureDefinition[]>();
    for (const feature of this.featureCatalog) {
      const group = feature.group || 'Other';
      if (!grouped.has(group)) {
        grouped.set(group, []);
      }
      grouped.get(group)!.push(feature);
    }
    return Array.from(grouped.entries()).map(([name, features]) => ({ name, features }));
  }

  selectManagedUser(userId: string): void {
    this.selectedUserId = userId;
    this.selectedUser = this.managedUsers.find(user => user.id === userId) ?? null;
    this.allowOverrides = new Set(this.selectedUser?.allow_features ?? []);
    this.denyOverrides = new Set(this.selectedUser?.deny_features ?? []);
    this.accessMessage = '';
    this.accessErrorMessage = '';
  }

  overrideState(featureKey: string): 'default' | 'allow' | 'deny' {
    if (this.allowOverrides.has(featureKey)) {
      return 'allow';
    }
    if (this.denyOverrides.has(featureKey)) {
      return 'deny';
    }
    return 'default';
  }

  setOverride(featureKey: string, mode: 'default' | 'allow' | 'deny'): void {
    this.allowOverrides.delete(featureKey);
    this.denyOverrides.delete(featureKey);
    if (mode === 'allow') {
      this.allowOverrides.add(featureKey);
    }
    if (mode === 'deny') {
      this.denyOverrides.add(featureKey);
    }
  }

  isEffective(featureKey: string): boolean {
    if (this.allowOverrides.has(featureKey)) {
      return true;
    }
    if (this.denyOverrides.has(featureKey)) {
      return false;
    }
    return this.selectedDefaultFeatures.includes(featureKey);
  }

  saveAccess(): void {
    if (!this.selectedUser) {
      return;
    }
    this.isSavingAccess = true;
    this.accessMessage = '';
    this.accessErrorMessage = '';
    this.accessControlService.updateUserAccess(
      this.selectedUser.id,
      Array.from(this.allowOverrides),
      Array.from(this.denyOverrides)
    )
      .pipe(finalize(() => (this.isSavingAccess = false)))
      .subscribe({
        next: updatedUser => {
          this.managedUsers = this.managedUsers.map(user => user.id === updatedUser.id ? updatedUser : user);
          this.selectManagedUser(updatedUser.id);
          this.accessMessage = 'UI access overrides saved.';
        },
        error: () => {
          this.accessErrorMessage = 'Unable to save user access overrides.';
        }
      });
  }

  resetAccessOverrides(): void {
    if (!this.selectedUser) {
      return;
    }
    this.allowOverrides.clear();
    this.denyOverrides.clear();
  }

  private loadAccessOverview(): void {
    this.isLoadingAccess = true;
    this.accessControlService.getOverview()
      .pipe(finalize(() => (this.isLoadingAccess = false)))
      .subscribe({
        next: overview => {
          this.featureCatalog = Array.isArray(overview.features) ? overview.features : [];
          this.managedUsers = Array.isArray(overview.users) ? overview.users : [];
          if (!this.selectedUserId && this.managedUsers.length) {
            const firstNonAdmin = this.managedUsers.find(user => user.role !== 'admin') ?? this.managedUsers[0];
            this.selectManagedUser(firstNonAdmin.id);
          } else if (this.selectedUserId) {
            this.selectManagedUser(this.selectedUserId);
          }
        },
        error: () => {
          this.accessErrorMessage = 'Unable to load user access controls.';
        }
      });
  }

  labelForRole(role?: string): string {
    switch ((role ?? '').toLowerCase()) {
      case 'admin':
        return 'Admin';
      case 'helper':
        return 'Helper';
      case 'vendor':
        return 'Vendor';
      case 'shop':
        return 'Branch';
      default:
        return 'Unknown';
    }
  }
}
