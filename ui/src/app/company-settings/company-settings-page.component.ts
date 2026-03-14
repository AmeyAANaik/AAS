import { Component, OnInit } from '@angular/core';
import { FormBuilder, Validators } from '@angular/forms';
import { finalize } from 'rxjs/operators';
import { CompanyContextService, CompanyIdentity } from '../shared/company-context.service';

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
  message = '';
  errorMessage = '';

  constructor(
    private fb: FormBuilder,
    private companyContextService: CompanyContextService
  ) {}

  ngOnInit(): void {
    this.loadContext();
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
}
