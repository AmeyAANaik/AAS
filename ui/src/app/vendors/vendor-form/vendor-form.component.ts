import { Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { VendorFormValue, VendorView } from '../vendor.model';

@Component({
  selector: 'app-vendor-form',
  templateUrl: './vendor-form.component.html',
  styleUrl: './vendor-form.component.scss'
})
export class VendorFormComponent implements OnChanges {
  @Input() vendor: VendorView | null = null;
  @Input() mode: 'create' | 'edit' = 'create';
  @Input() isSaving = false;
  @Input() statusMessage = '';
  @Output() save = new EventEmitter<VendorFormValue>();
  @Output() reset = new EventEmitter<void>();

  form: FormGroup = this.fb.group({
    supplierName: ['', [Validators.required, Validators.maxLength(140)]],
    address: [''],
    phone: [''],
    gst: [''],
    pan: [''],
    foodLicenseNo: [''],
    priority: [null, [Validators.required, Validators.min(0), Validators.pattern(/^\d+$/)]],
    status: ['Active', [Validators.required]]
  });

  constructor(private fb: FormBuilder) {}

  ngOnChanges(): void {
    if (this.vendor) {
      this.form.patchValue({
        supplierName: this.vendor.name,
        priority: this.vendor.priority,
        status: this.vendor.status
      });
      if (this.mode === 'edit') {
        this.form.disable({ emitEvent: false });
      }
      this.form.markAsPristine();
      return;
    }
    this.form.enable({ emitEvent: false });
    this.form.reset({
      supplierName: '',
      address: '',
      phone: '',
      gst: '',
      pan: '',
      foodLicenseNo: '',
      priority: null,
      status: 'Active'
    });
  }

  submit(): void {
    if (this.mode === 'edit') {
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.save.emit(this.form.getRawValue() as VendorFormValue);
  }

  clear(): void {
    this.form.reset({
      supplierName: '',
      address: '',
      phone: '',
      gst: '',
      pan: '',
      foodLicenseNo: '',
      priority: null,
      status: 'Active'
    });
    this.reset.emit();
  }
}
