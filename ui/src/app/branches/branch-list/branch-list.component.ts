import { Component, OnInit } from '@angular/core';
import { finalize } from 'rxjs/operators';
import { Branch, BranchFormValue, BranchView } from '../branch.model';
import { BranchService } from '../branch.service';

@Component({
  selector: 'app-branch-list',
  templateUrl: './branch-list.component.html',
  styleUrl: './branch-list.component.scss'
})
export class BranchListComponent implements OnInit {
  displayedColumns: string[] = ['name', 'location', 'whatsapp', 'actions'];
  branches: BranchView[] = [];
  selectedBranch: BranchView | null = null;
  isLoading = false;
  isSaving = false;
  statusMessage = '';

  constructor(private branchService: BranchService) {}

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
    this.statusMessage = 'Branch name edits are not available yet.';
  }

  clearSelection(): void {
    this.selectedBranch = null;
    this.statusMessage = '';
  }

  saveBranch(formValue: BranchFormValue): void {
    this.isSaving = true;
    if (this.selectedBranch) {
      this.branchService.saveMetadata(this.selectedBranch.id, {
        location: formValue.location,
        whatsappGroupName: formValue.whatsappGroupName
      });
      this.statusMessage = 'Branch details updated locally.';
      this.isSaving = false;
      this.loadBranches();
      return;
    }
    const payload = { customer_name: formValue.branchName.trim() };
    this.branchService
      .createBranch(payload)
      .pipe(finalize(() => (this.isSaving = false)))
      .subscribe({
        next: () => {
          this.branchService.saveMetadata(formValue.branchName.trim(), {
            location: formValue.location,
            whatsappGroupName: formValue.whatsappGroupName
          });
          this.statusMessage = 'Branch saved.';
          this.selectedBranch = null;
          this.loadBranches();
        },
        error: err => {
          this.statusMessage = this.formatError(err, 'Unable to save branch');
        }
      });
  }

  private toViewModel(branch: Branch & { location?: string; whatsappGroupName?: string }): BranchView {
    const name = String(branch.customer_name ?? branch.name ?? '').trim();
    return {
      id: String(branch.name ?? name),
      name: name || String(branch.name ?? ''),
      location: branch.location ?? '',
      whatsappGroupName: branch.whatsappGroupName ?? '',
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
