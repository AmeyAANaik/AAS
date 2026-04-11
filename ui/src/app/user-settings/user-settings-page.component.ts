import { Component, OnInit } from '@angular/core';
import { FormBuilder } from '@angular/forms';
import { finalize } from 'rxjs/operators';
import { UserContextService, UserIdentity } from '../shared/user-context.service';

@Component({
  selector: 'app-user-settings-page',
  templateUrl: './user-settings-page.component.html',
  styleUrl: './user-settings-page.component.scss'
})
export class UserSettingsPageComponent implements OnInit {
  readonly form = this.fb.group({
    name: [{ value: '', disabled: true }],
    full_name: [''],
    email: [{ value: '', disabled: true }],
    mobile_no: [''],
    location: [''],
    company: [''],
    customer: [''],
    supplier: ['']
  });

  userId = '';
  userImageUrl = '';
  userRole = '';
  isLoading = false;
  isSaving = false;
  message = '';
  errorMessage = '';

  constructor(
    private fb: FormBuilder,
    private userContextService: UserContextService
  ) {}

  ngOnInit(): void {
    this.loadUser();
  }

  save(): void {
    if (!this.userId) {
      return;
    }
    this.isSaving = true;
    this.errorMessage = '';
    this.message = '';
    const raw = this.form.getRawValue();
    this.userContextService.updateCurrentUser({
      full_name: String(raw.full_name ?? '').trim(),
      mobile_no: String(raw.mobile_no ?? '').trim(),
      location: String(raw.location ?? '').trim(),
      company: String(raw.company ?? '').trim(),
      customer: String(raw.customer ?? '').trim(),
      supplier: String(raw.supplier ?? '').trim()
    })
      .pipe(finalize(() => (this.isSaving = false)))
      .subscribe({
        next: user => {
          this.applyUser(user);
          this.message = 'User details updated.';
        },
        error: () => {
          this.errorMessage = 'Unable to update user details.';
        }
      });
  }

  private loadUser(): void {
    this.isLoading = true;
    this.userContextService.getCurrentUser()
      .pipe(finalize(() => (this.isLoading = false)))
      .subscribe({
        next: user => this.applyUser(user),
        error: () => {
          this.errorMessage = 'Unable to load user details.';
        }
      });
  }

  private applyUser(user: UserIdentity): void {
    this.userId = String(user.id ?? user.name ?? '').trim();
    this.userImageUrl = String(user.user_image ?? '').trim();
    this.userRole = String(user.role ?? '').trim();
    this.form.patchValue({
      name: user.name ?? '',
      full_name: user.full_name ?? '',
      email: user.email ?? '',
      mobile_no: user.mobile_no ?? '',
      location: user.location ?? '',
      company: user.company ?? '',
      customer: user.customer ?? '',
      supplier: user.supplier ?? ''
    });
  }
}
