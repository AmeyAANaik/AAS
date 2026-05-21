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
    whatsappGroupName: [''],
    invoiceEmail: ['', [Validators.email]],
    whatsappNumber: [''],
    creditDays: [0, [Validators.min(0)]],
    taxId: [''],
    fssaiNo: ['']
  });

  constructor(private fb: FormBuilder) {}

  ngOnChanges(): void {
    if (this.branch) {
      this.form.patchValue({
        branchName: this.branch.name,
        location: this.branch.location,
        whatsappGroupName: this.branch.whatsappGroupName,
        invoiceEmail: this.branch.invoiceEmail,
        whatsappNumber: this.branch.whatsappNumber,
        creditDays: this.branch.creditDays ?? 0,
        taxId: this.branch.taxId,
        fssaiNo: this.branch.fssaiNo
      });
      this.form.markAsPristine();
      return;
    }
    this.form.enable({ emitEvent: false });
    this.form.reset({
      branchName: '',
      location: '',
      whatsappGroupName: '',
      invoiceEmail: '',
      whatsappNumber: '',
      creditDays: 0,
      taxId: '',
      fssaiNo: ''
    });
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
    this.form.reset({
      branchName: '',
      location: '',
      whatsappGroupName: '',
      invoiceEmail: '',
      whatsappNumber: '',
      creditDays: 0,
      taxId: '',
      fssaiNo: ''
    });
    this.reset.emit();
  }
}
