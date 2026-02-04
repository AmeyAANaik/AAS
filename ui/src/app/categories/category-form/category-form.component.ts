import { Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { CategoryFormValue, CategoryView } from '../category.model';

@Component({
  selector: 'app-category-form',
  templateUrl: './category-form.component.html',
  styleUrl: './category-form.component.scss'
})
export class CategoryFormComponent implements OnChanges {
  @Input() category: CategoryView | null = null;
  @Input() isSaving = false;
  @Input() statusMessage = '';
  @Output() save = new EventEmitter<CategoryFormValue>();
  @Output() reset = new EventEmitter<void>();

  form: FormGroup = this.fb.group({
    categoryName: ['', [Validators.required, Validators.maxLength(140)]]
  });

  constructor(private fb: FormBuilder) {}

  ngOnChanges(): void {
    if (this.category) {
      this.form.patchValue({ categoryName: this.category.name });
      this.form.get('categoryName')?.disable({ emitEvent: false });
      this.form.markAsPristine();
      return;
    }
    this.form.enable({ emitEvent: false });
    this.form.reset({ categoryName: '' });
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    this.save.emit(this.form.getRawValue() as CategoryFormValue);
  }

  clear(): void {
    this.form.enable({ emitEvent: false });
    this.form.reset({ categoryName: '' });
    this.reset.emit();
  }
}
