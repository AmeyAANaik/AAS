import { CommonModule } from '@angular/common';
import { Component, Input, Optional, Self } from '@angular/core';
import { ControlValueAccessor, NgControl } from '@angular/forms';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';

@Component({
  selector: 'app-month-picker-field',
  standalone: true,
  imports: [CommonModule, MatDatepickerModule, MatFormFieldModule, MatInputModule],
  template: `
    <mat-form-field appearance="outline">
      <mat-label>{{ label }}</mat-label>
      <input
        matInput
        [matDatepicker]="picker"
        [value]="dateValue"
        [required]="required"
        [disabled]="disabled"
        (dateChange)="selectDate($event.value)"
        (blur)="onTouched()" />
      <mat-datepicker-toggle matIconSuffix [for]="picker"></mat-datepicker-toggle>
      <mat-datepicker #picker startView="multi-year"></mat-datepicker>
      <mat-error *ngIf="showError">{{ errorText }}</mat-error>
    </mat-form-field>
  `,
  styles: [`
    :host,
    mat-form-field {
      display: block;
      width: 100%;
    }
  `]
})
export class MonthPickerFieldComponent implements ControlValueAccessor {
  @Input() label = 'Month';
  @Input() required = false;
  @Input() errorText = 'Month is required.';

  dateValue: Date | null = null;
  disabled = false;

  private onChange: (value: string) => void = () => {};
  onTouched: () => void = () => {};

  constructor(@Optional() @Self() private ngControl: NgControl | null) {
    if (this.ngControl) {
      this.ngControl.valueAccessor = this;
    }
  }

  get showError(): boolean {
    const control = this.ngControl?.control;
    return !!control?.invalid && (control.touched || control.dirty);
  }

  writeValue(value: string | Date | null | undefined): void {
    this.dateValue = this.parseMonth(value);
  }

  registerOnChange(fn: (value: string) => void): void {
    this.onChange = fn;
  }

  registerOnTouched(fn: () => void): void {
    this.onTouched = fn;
  }

  setDisabledState(isDisabled: boolean): void {
    this.disabled = isDisabled;
  }

  selectDate(value: Date | null): void {
    this.dateValue = value ? new Date(value.getFullYear(), value.getMonth(), 1) : null;
    this.onChange(this.dateValue ? this.toMonthString(this.dateValue) : '');
    this.onTouched();
  }

  private parseMonth(value: string | Date | null | undefined): Date | null {
    if (value instanceof Date) {
      return Number.isNaN(value.getTime()) ? null : new Date(value.getFullYear(), value.getMonth(), 1);
    }
    if (!value) {
      return null;
    }
    const match = /^(\d{4})-(\d{2})(?:-\d{2})?$/.exec(value);
    if (!match) {
      return null;
    }
    return new Date(Number(match[1]), Number(match[2]) - 1, 1);
  }

  private toMonthString(value: Date): string {
    const year = value.getFullYear();
    const month = String(value.getMonth() + 1).padStart(2, '0');
    return `${year}-${month}`;
  }
}
