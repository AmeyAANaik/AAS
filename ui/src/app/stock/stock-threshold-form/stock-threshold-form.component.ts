import { Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { StockThresholdFormValue, StockView } from '../stock.model';

@Component({
  selector: 'app-stock-threshold-form',
  templateUrl: './stock-threshold-form.component.html',
  styleUrl: './stock-threshold-form.component.scss'
})
export class StockThresholdFormComponent implements OnChanges {
  @Input() stockItem: StockView | null = null;
  @Input() isSaving = false;
  @Input() statusMessage = '';
  @Output() save = new EventEmitter<StockThresholdFormValue>();
  @Output() reset = new EventEmitter<void>();

  form: FormGroup = this.fb.group({
    itemName: [{ value: '', disabled: true }],
    threshold: [null, [Validators.required, Validators.min(0)]]
  });

  private currentItemId = '';

  constructor(private fb: FormBuilder) {}

  ngOnChanges(): void {
    if (this.stockItem) {
      this.currentItemId = this.stockItem.id;
      this.form.patchValue({
        itemName: this.stockItem.name,
        threshold: this.stockItem.threshold ?? null
      });
      this.form.markAsPristine();
      return;
    }
    this.currentItemId = '';
    this.form.reset({ itemName: '', threshold: null });
  }

  submit(): void {
    if (!this.currentItemId) {
      this.form.markAllAsTouched();
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const thresholdValue = this.form.get('threshold')?.value;
    this.save.emit({
      itemId: this.currentItemId,
      threshold: thresholdValue === null ? null : Number(thresholdValue)
    });
  }

  clear(): void {
    this.currentItemId = '';
    this.form.reset({ itemName: '', threshold: null });
    this.reset.emit();
  }
}
