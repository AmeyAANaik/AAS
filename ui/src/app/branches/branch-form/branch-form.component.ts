import { Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { BranchFormValue, BranchView } from '../branch.model';

@Component({
  selector: 'app-branch-form',
  templateUrl: './branch-form.component.html',
  styleUrl: './branch-form.component.scss'
})
export class BranchFormComponent implements OnChanges {
  @Input() branch: BranchView | null = null;
  @Input() isSaving = false;
  @Input() statusMessage = '';
  @Output() save = new EventEmitter<BranchFormValue>();
  @Output() reset = new EventEmitter<void>();

  form: FormGroup = this.fb.group({
    branchName: ['', [Validators.required, Validators.maxLength(140)]],
    location: [''],
    whatsappGroupName: ['']
  });

  constructor(private fb: FormBuilder) {}

  ngOnChanges(): void {
    if (this.branch) {
      this.form.patchValue({
        branchName: this.branch.name,
        location: this.branch.location,
        whatsappGroupName: this.branch.whatsappGroupName
      });
      this.form.get('branchName')?.disable({ emitEvent: false });
      this.form.markAsPristine();
      return;
    }
    this.form.enable({ emitEvent: false });
    this.form.reset({ branchName: '', location: '', whatsappGroupName: '' });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.save.emit(this.form.getRawValue() as BranchFormValue);
  }

  clear(): void {
    this.form.enable({ emitEvent: false });
    this.form.reset({ branchName: '', location: '', whatsappGroupName: '' });
    this.reset.emit();
  }
}
