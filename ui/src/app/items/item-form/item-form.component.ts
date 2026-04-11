import { Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Category } from '../../categories/category.model';
import { ItemFormValue, ItemView } from '../item.model';

@Component({
  selector: 'app-item-form',
  templateUrl: './item-form.component.html',
  styleUrl: './item-form.component.scss'
})
export class ItemFormComponent implements OnChanges {
  private readonly defaultMarginPercent = 7;
  readonly measureUnits = ['Nos', 'KG', 'GMS', 'LTR', 'ML', 'PCS', 'BOX', 'PACK', 'BAG', 'DOZEN'];
  @Input() item: ItemView | null = null;
  @Input() categories: Category[] = [];
  @Input() initialCategory = '';
  @Input() lockCategory = false;
  @Input() resolvedVendorName = '';
  @Input() resolvedVendorCode = '';
  @Input() categoryCode = '';
  @Input() formVersion = 0;
  @Input() isSaving = false;
  @Input() statusMessage = '';
  @Output() save = new EventEmitter<ItemFormValue>();
  @Output() reset = new EventEmitter<void>();

  form: FormGroup = this.fb.group({
    vendorHsnCode: ['', [Validators.required, Validators.maxLength(140)]],
    itemName: ['', [Validators.required, Validators.maxLength(140)]],
    category: ['', [Validators.required]],
    measureUnit: ['', [Validators.required, Validators.maxLength(64)]],
    packagingUnit: [''],
    marginPercent: [this.defaultMarginPercent, [Validators.required, Validators.min(0), Validators.max(100)]]
  });

  constructor(private fb: FormBuilder) {}

  ngOnChanges(): void {
    if (this.item) {
      this.form.patchValue({
        vendorHsnCode: this.item.vendorHsnCode,
        itemName: this.item.name,
        category: this.item.category,
        measureUnit: this.item.measureUnit,
        packagingUnit: this.item.packagingUnit,
        marginPercent: this.resolveMarginPercent(this.item.marginPercent)
      });
      this.form.markAsPristine();
      return;
    }
    this.resetFormState();
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.save.emit(this.form.getRawValue() as ItemFormValue);
  }

  clear(): void {
    this.resetFormState();
    this.reset.emit();
  }

  private resetFormState(): void {
    this.form.reset({
      vendorHsnCode: '',
      itemName: '',
      category: this.initialCategory || '',
      measureUnit: 'Nos',
      packagingUnit: '',
      marginPercent: this.defaultMarginPercent
    });
    this.form.markAsUntouched();
    this.form.markAsPristine();
  }

  private resolveMarginPercent(value: number | null): number {
    if (value === null || value === undefined || value <= 0) {
      return this.defaultMarginPercent;
    }
    return value;
  }

}
