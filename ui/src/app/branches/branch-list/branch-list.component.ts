import { Component, OnInit } from '@angular/core';
import { finalize } from 'rxjs/operators';
import { Branch, BranchFormValue, BranchView } from '../branch.model';
import { BranchService } from '../branch.service';
import { MasterDataToastService } from '../../shared/master-data-toast.service';

@Component({
  selector: 'app-branch-list',
  templateUrl: './branch-list.component.html',
  styleUrl: './branch-list.component.scss'
})
export class BranchListComponent implements OnInit {
  displayedColumns: string[] = ['name', 'location', 'whatsapp', 'creditDays', 'actions'];
  branches: BranchView[] = [];
  selectedBranch: BranchView | null = null;
  isFormOpen = false;
  isLoading = false;
  isSaving = false;
  statusMessage = '';

  constructor(
    private branchService: BranchService,
    private toastService: MasterDataToastService
  ) {}

  ngOnInit(): void {
    this.loadBranches();
  }

  loadBranches(): void {
    this.isLoading = true;
    this.branchService
      .listBranches()
      .pipe(finalize(() => (this.isLoading = false)))
      .subscribe({
        next: branches => {
          this.branches = branches.map(branch => this.toViewModel(branch));
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
        aas_branch_location: formValue.location,
        aas_whatsapp_group_name: formValue.whatsappGroupName,
        aas_credit_days: formValue.creditDays ?? 0
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
      aas_credit_days: formValue.creditDays ?? 0
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

  private toViewModel(branch: Branch & { location?: string; whatsappGroupName?: string }): BranchView {
    const name = String(branch.customer_name ?? branch.name ?? '').trim();
    return {
      id: String(branch.name ?? name),
      name: name || String(branch.name ?? ''),
      location: branch.aas_branch_location ?? branch.location ?? '',
      whatsappGroupName: branch.aas_whatsapp_group_name ?? branch.whatsappGroupName ?? '',
      creditDays:
        typeof branch.aas_credit_days === 'number' ? branch.aas_credit_days : null,
      raw: branch
    };
  }

  private formatError(err: unknown, fallback: string): string {
    if (err instanceof Error) {
      return err.message;
    }
    if (typeof err === 'string') {
      return err;
    }
    return fallback;
  }
}
