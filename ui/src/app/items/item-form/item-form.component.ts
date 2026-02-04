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
  @Input() item: ItemView | null = null;
  @Input() categories: Category[] = [];
  @Input() isSaving = false;
  @Input() statusMessage = '';
  @Output() save = new EventEmitter<ItemFormValue>();
  @Output() reset = new EventEmitter<void>();

  form: FormGroup = this.fb.group({
    itemCode: ['', [Validators.required, Validators.maxLength(140)]],
    itemName: ['', [Validators.required, Validators.maxLength(140)]],
    category: ['', [Validators.required]],
    measureUnit: ['', [Validators.required, Validators.maxLength(64)]],
    packagingUnit: ['']
  });

  constructor(private fb: FormBuilder) {}

  ngOnChanges(): void {
    if (this.item) {
      this.form.patchValue({
        itemCode: this.item.code,
        itemName: this.item.name,
        category: this.item.category,
        measureUnit: this.item.measureUnit,
        packagingUnit: this.item.packagingUnit
      });
      this.form.get('itemCode')?.disable({ emitEvent: false });
      this.form.get('itemName')?.disable({ emitEvent: false });
      this.form.markAsPristine();
      return;
    }
    this.form.enable({ emitEvent: false });
    this.form.reset({
      itemCode: '',
      itemName: '',
      category: '',
      measureUnit: 'Nos',
      packagingUnit: ''
    });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.save.emit(this.form.getRawValue() as ItemFormValue);
  }

  clear(): void {
    this.form.enable({ emitEvent: false });
    this.form.reset({
      itemCode: '',
      itemName: '',
      category: '',
      measureUnit: 'Nos',
      packagingUnit: ''
    });
    this.reset.emit();
  }
}
