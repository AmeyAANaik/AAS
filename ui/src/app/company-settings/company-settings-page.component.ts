import { Component, OnInit } from '@angular/core';
import { FormBuilder, Validators } from '@angular/forms';
import { finalize } from 'rxjs/operators';
import { CompanyContextService, CompanyIdentity, OpeningBalancePreview, OpeningBalanceApplyResult } from '../shared/company-context.service';
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
  private readonly maxImageBytes = 10 * 1024 * 1024;
  readonly form = this.fb.group({
    name: ['', Validators.required],
    abbr: ['', Validators.required],
    default_currency: ['', Validators.required],
    country: [''],
    default_letter_head: [''],
    tax_id: [''],
    bank_beneficiary_name: [''],
    bank_name: [''],
    bank_account_number: [''],
    bank_ifsc_code: [''],
    bank_branch: [''],
    invoice_email: ['', Validators.email],
    whatsapp_number: ['']
  });

  companyId = '';
  companyLogoUrl = '';
  companySignatureUrl = '';
  selectedLogoFileName = '';
  selectedSignatureFileName = '';
  branchName = '';
  branchLocation = '';
  isLoading = false;
  isSaving = false;
  isUploadingLogo = false;
  isUploadingSignature = false;
  isOpeningBalancePreviewing = false;
  isOpeningBalanceApplying = false;
  isLoadingAccess = false;
  isSavingAccess = false;
  message = '';
  errorMessage = '';
  openingBalanceMessage = '';
  openingBalanceError = '';
  openingBalanceCutoverDate = new Date().toISOString().slice(0, 10);
  openingBalanceFile: File | null = null;
  openingBalanceFileName = '';
  openingBalancePreview: OpeningBalancePreview | null = null;
  openingBalanceApplyResult: OpeningBalanceApplyResult | null = null;
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
      name: String(raw.name ?? '').trim(),
      abbr: String(raw.abbr ?? '').trim(),
      default_currency: String(raw.default_currency ?? '').trim(),
      country: String(raw.country ?? '').trim(),
      default_letter_head: String(raw.default_letter_head ?? '').trim(),
      tax_id: String(raw.tax_id ?? '').trim(),
      bank_beneficiary_name: String(raw.bank_beneficiary_name ?? '').trim(),
      bank_name: String(raw.bank_name ?? '').trim(),
      bank_account_number: String(raw.bank_account_number ?? '').trim(),
      bank_ifsc_code: String(raw.bank_ifsc_code ?? '').trim(),
      bank_branch: String(raw.bank_branch ?? '').trim(),
      invoice_email: String(raw.invoice_email ?? '').trim(),
      whatsapp_number: String(raw.whatsapp_number ?? '').trim()
    })
      .pipe(finalize(() => (this.isSaving = false)))
      .subscribe({
        next: company => {
          this.applyCompany(company);
          this.message = 'Company details updated.';
        },
        error: err => {
          const message = String(err?.error?.error ?? err?.error?.message ?? err?.message ?? '').trim();
          this.errorMessage = message || 'Unable to update company details.';
        }
      });
  }

  onLogoSelected(event: Event): void {
    const input = event.target as HTMLInputElement | null;
    const file = input?.files?.[0];
    if (!file || !this.companyId) {
      return;
    }
    if (!file.type.startsWith('image/')) {
      this.errorMessage = 'Please choose an image file for the company logo.';
      input.value = '';
      return;
    }
    if (file.size > this.maxImageBytes) {
      this.errorMessage = 'Logo file is too large. Please upload an image up to 10 MB.';
      input.value = '';
      return;
    }
    this.isUploadingLogo = true;
    this.errorMessage = '';
    this.message = '';
    this.selectedLogoFileName = file.name;
    this.companyContextService.uploadCompanyLogo(this.companyId, file)
      .pipe(finalize(() => {
        this.isUploadingLogo = false;
        if (input) {
          input.value = '';
        }
      }))
      .subscribe({
        next: company => {
          this.applyCompany(company);
          this.message = 'Company logo updated.';
        },
        error: () => {
          this.errorMessage = 'Unable to upload company logo.';
        }
      });
  }

  onSignatureSelected(event: Event): void {
    const input = event.target as HTMLInputElement | null;
    const file = input?.files?.[0];
    if (!file || !this.companyId) {
      return;
    }
    if (!file.type.startsWith('image/')) {
      this.errorMessage = 'Please choose an image file for the company signature.';
      input.value = '';
      return;
    }
    if (file.size > this.maxImageBytes) {
      this.errorMessage = 'Signature file is too large. Please upload an image up to 10 MB.';
      input.value = '';
      return;
    }
    this.isUploadingSignature = true;
    this.errorMessage = '';
    this.message = '';
    this.selectedSignatureFileName = file.name;
    this.companyContextService.uploadCompanySignature(this.companyId, file)
      .pipe(finalize(() => {
        this.isUploadingSignature = false;
        if (input) {
          input.value = '';
        }
      }))
      .subscribe({
        next: company => {
          this.applyCompany(company);
          this.message = 'Company signature updated.';
        },
        error: () => {
          this.errorMessage = 'Unable to upload company signature.';
        }
      });
  }

  onOpeningBalanceFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement | null;
    const file = input?.files?.[0];
    this.openingBalanceMessage = '';
    this.openingBalanceError = '';
    this.openingBalancePreview = null;
    this.openingBalanceApplyResult = null;
    if (!file) {
      return;
    }
    if (!file.name.toLowerCase().endsWith('.csv') && !file.type.includes('csv')) {
      this.openingBalanceError = 'Please choose a CSV file.';
      if (input) {
        input.value = '';
      }
      return;
    }
    this.openingBalanceFile = file;
    this.openingBalanceFileName = file.name;
    if (input) {
      input.value = '';
    }
  }

  downloadOpeningBalanceTemplate(): void {
    const template = [
      'record_type,account,debit,credit,cost_center,party_id,amount,bill_no,invoice_ref',
      'ACCOUNT,ACC-EXAMPLE-1,1000,0,Main - CC,,,,' ,
      'ACCOUNT,ACC-EXAMPLE-2,0,1000,Main - CC,,,,' ,
      'SUPPLIER,,,,,SUPPLIER-0001,25000,,',
      'CUSTOMER,,,,,CUSTOMER-0001,15000,,'
    ].join('\n');

    const blob = new Blob([template], { type: 'text/csv;charset=utf-8' });
    const url = URL.createObjectURL(blob);
    const a = document.createElement('a');
    a.href = url;
    a.download = 'aas-opening-balances-template.csv';
    a.click();
    URL.revokeObjectURL(url);
  }

  previewOpeningBalances(): void {
    if (!this.companyId || !this.openingBalanceFile || !this.openingBalanceCutoverDate) {
      this.openingBalanceError = 'Select a cutover date and CSV file first.';
      return;
    }
    this.isOpeningBalancePreviewing = true;
    this.openingBalanceError = '';
    this.openingBalanceMessage = '';
    this.openingBalancePreview = null;
    this.openingBalanceApplyResult = null;
    this.companyContextService.previewOpeningBalances(this.companyId, this.openingBalanceFile, this.openingBalanceCutoverDate)
      .pipe(finalize(() => (this.isOpeningBalancePreviewing = false)))
      .subscribe({
        next: preview => {
          this.openingBalancePreview = preview;
          if (preview.isValid) {
            this.openingBalanceMessage = 'Preview ready. Review totals and apply when ready.';
          } else {
            this.openingBalanceError = 'Preview found validation errors. Fix the CSV and preview again.';
          }
        },
        error: err => {
          const message = String(err?.error?.message ?? err?.message ?? '').trim();
          this.openingBalanceError = message || 'Unable to preview opening balances.';
        }
      });
  }

  applyOpeningBalances(): void {
    if (!this.companyId || !this.openingBalanceFile || !this.openingBalanceCutoverDate) {
      this.openingBalanceError = 'Select a cutover date and CSV file first.';
      return;
    }
    if (!this.openingBalancePreview?.isValid) {
      this.openingBalanceError = 'Run a valid preview before applying.';
      return;
    }
    this.isOpeningBalanceApplying = true;
    this.openingBalanceError = '';
    this.openingBalanceMessage = '';
    this.openingBalanceApplyResult = null;
    this.companyContextService.applyOpeningBalances(this.companyId, this.openingBalanceFile, this.openingBalanceCutoverDate)
      .pipe(finalize(() => (this.isOpeningBalanceApplying = false)))
      .subscribe({
        next: result => {
          this.openingBalanceApplyResult = result;
          this.openingBalanceMessage = 'Opening balances created in ERPNext as drafts.';
        },
        error: err => {
          const message = String(err?.error?.message ?? err?.message ?? '').trim();
          this.openingBalanceError = message || 'Unable to apply opening balances.';
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
    this.companySignatureUrl = company.signature_url ?? '';
    this.selectedLogoFileName = this.companyLogoUrl
      ? this.companyLogoUrl.split('/').pop()?.split('?')[0] ?? ''
      : '';
    this.selectedSignatureFileName = this.companySignatureUrl
      ? this.companySignatureUrl.split('/').pop()?.split('?')[0] ?? ''
      : '';
    this.form.patchValue({
      name: company.name ?? '',
      abbr: company.abbr ?? '',
      default_currency: company.default_currency ?? '',
      country: company.country ?? '',
      default_letter_head: company.default_letter_head ?? '',
      tax_id: company.tax_id ?? '',
      bank_beneficiary_name: company.bank_beneficiary_name ?? '',
      bank_name: company.bank_name ?? '',
      bank_account_number: company.bank_account_number ?? '',
      bank_ifsc_code: company.bank_ifsc_code ?? '',
      bank_branch: company.bank_branch ?? '',
      invoice_email: company.invoice_email ?? '',
      whatsapp_number: company.whatsapp_number ?? ''
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
