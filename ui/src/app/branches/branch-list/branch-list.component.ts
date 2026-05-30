import { Component, OnInit } from '@angular/core';
import { FormControl } from '@angular/forms';
import { finalize } from 'rxjs/operators';
import { Branch, BranchFormValue, BranchView } from '../branch.model';
import { BranchService } from '../branch.service';
import { MasterDataToastService } from '../../shared/master-data-toast.service';
import { formatUiError } from '../../shared/error-message.util';

@Component({
  selector: 'app-branch-list',
  templateUrl: './branch-list.component.html',
  styleUrl: './branch-list.component.scss'
})
export class BranchListComponent implements OnInit {
  displayedColumns: string[] = ['name', 'location', 'contacts', 'creditDays', 'status', 'actions'];
  branches: BranchView[] = [];
  selectedBranch: BranchView | null = null;
  isFormOpen = false;
  isLoading = false;
  isSaving = false;
  isTogglingStatus = false;
  statusMessage = '';
  searchControl = new FormControl<string>('', { nonNullable: true });

  constructor(
    private branchService: BranchService,
    private toastService: MasterDataToastService
  ) {}

  ngOnInit(): void {
    this.loadBranches();
  }

  get filteredBranches(): BranchView[] {
    const term = this.searchControl.value.trim().toLowerCase();
    if (!term) {
      return this.branches;
    }
    return this.branches.filter(branch => {
      const haystack = [
        branch.name,
        branch.location,
        branch.invoiceEmail,
        branch.whatsappNumber,
        branch.whatsappGroupName
      ]
        .filter(Boolean)
        .join(' ')
        .toLowerCase();
      return haystack.includes(term);
    });
  }

  loadBranches(): void {
    this.isLoading = true;
    this.branchService
      .listBranches()
      .pipe(finalize(() => (this.isLoading = false)))
      .subscribe({
        next: branches => {
          this.branches = branches
            .map(branch => this.toViewModel(branch))
            .filter(branch => !branch.isDeleted);
        },
        error: err => {
          this.statusMessage = this.formatError(err, 'Unable to load branches');
        }
      });
  }

  selectBranch(branch: BranchView): void {
    this.selectedBranch = branch;
    this.isFormOpen = true;
    this.statusMessage = '';
  }

  openCreate(): void {
    this.selectedBranch = null;
    this.isFormOpen = true;
    this.statusMessage = '';
  }

  clearSelection(): void {
    this.selectedBranch = null;
    this.isFormOpen = false;
    this.statusMessage = '';
  }

  saveBranch(formValue: BranchFormValue): void {
    this.isSaving = true;
    if (this.selectedBranch) {
      const payload = {
        customer_name: formValue.branchName.trim(),
        aas_branch_location: formValue.location,
        aas_whatsapp_group_name: formValue.whatsappGroupName,
        aas_invoice_email: formValue.invoiceEmail,
        aas_whatsapp_number: formValue.whatsappNumber,
        aas_credit_days: formValue.creditDays ?? 0,
        tax_id: formValue.taxId?.trim() || '',
        aas_food_license_no: formValue.fssaiNo?.trim() || ''
      };
      this.branchService
        .updateBranch(this.selectedBranch.id, payload)
        .pipe(finalize(() => (this.isSaving = false)))
        .subscribe({
          next: () => {
            this.statusMessage = 'Branch details updated.';
            this.toastService.success(this.statusMessage);
            this.selectedBranch = null;
            this.isFormOpen = false;
            this.loadBranches();
          },
          error: err => {
            this.statusMessage = this.formatError(err, 'Unable to update branch');
            this.toastService.error(this.statusMessage);
          }
        });
      return;
    }
    const payload = {
      customer_name: formValue.branchName.trim(),
      aas_branch_location: formValue.location,
      aas_whatsapp_group_name: formValue.whatsappGroupName,
      aas_invoice_email: formValue.invoiceEmail,
      aas_whatsapp_number: formValue.whatsappNumber,
      aas_credit_days: formValue.creditDays ?? 0,
      tax_id: formValue.taxId?.trim() || '',
      aas_food_license_no: formValue.fssaiNo?.trim() || ''
    };
    this.branchService
      .createBranch(payload)
      .pipe(finalize(() => (this.isSaving = false)))
      .subscribe({
        next: () => {
          this.statusMessage = 'Branch saved.';
          this.toastService.success(this.statusMessage);
          this.selectedBranch = null;
          this.isFormOpen = false;
          this.loadBranches();
        },
        error: err => {
          this.statusMessage = this.formatError(err, 'Unable to save branch');
          this.toastService.error(this.statusMessage);
        }
    });
  }

  toggleBranchDisabled(branch: BranchView, disable: boolean): void {
    this.statusMessage = '';
    const actionLabel = disable ? 'disable' : 'reactivate';
    const ok = window.confirm(`Are you sure you want to ${actionLabel} “${branch.name}”?`);
    if (!ok) return;

    this.isTogglingStatus = true;
    const req$ = disable
      ? this.branchService.disableBranch(branch.id)
      : this.branchService.reactivateBranch(branch.id);

    req$
      .pipe(finalize(() => (this.isTogglingStatus = false)))
      .subscribe({
        next: () => {
          const message = disable ? 'Branch disabled.' : 'Branch reactivated.';
          this.toastService.success(message);
          this.loadBranches();
        },
        error: err => {
          const message = this.formatError(err, `Unable to ${actionLabel} branch`);
          this.toastService.error(message);
          this.statusMessage = message;
        }
      });
  }

  private toViewModel(branch: Branch & { location?: string; whatsappGroupName?: string }): BranchView {
    const name = String(branch.customer_name ?? branch.name ?? '').trim();
    const rawDeletedFlag = (branch as any).aas_is_deleted;
    const isDeleted =
      rawDeletedFlag === 1 ||
      rawDeletedFlag === true ||
      rawDeletedFlag === '1' ||
      String(rawDeletedFlag ?? '').trim().toLowerCase() === 'true';
    return {
      id: String(branch.name ?? name),
      name: name || String(branch.name ?? ''),
      location: branch.aas_branch_location ?? branch.location ?? '',
      whatsappGroupName: branch.aas_whatsapp_group_name ?? branch.whatsappGroupName ?? '',
      invoiceEmail: branch.aas_invoice_email ?? '',
      whatsappNumber: branch.aas_whatsapp_number ?? '',
      creditDays:
        typeof branch.aas_credit_days === 'number' ? branch.aas_credit_days : null,
      taxId: String(branch.tax_id ?? '').trim(),
      fssaiNo: String(branch.aas_food_license_no ?? '').trim(),
      disabled: Boolean(branch.disabled === 1 || branch.disabled === true),
      isDeleted,
      raw: branch
    };
  }

  private formatError(err: unknown, fallback: string): string {
    return formatUiError(err, fallback);
  }
}
