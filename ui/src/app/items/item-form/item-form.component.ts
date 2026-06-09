import { Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Observable } from 'rxjs';
import { map, startWith } from 'rxjs/operators';
import { Category } from '../../categories/category.model';
import { ItemFormValue, ItemView } from '../item.model';

@Component({
  selector: 'app-item-form',
  templateUrl: './item-form.component.html',
  styleUrl: './item-form.component.scss'
})
export class ItemFormComponent implements OnChanges {
  private readonly defaultMarginPercent = 7;
  readonly measureUnits: string[] = ['Nos', 'Kg', 'Gram', 'Litre', 'Ml', 'Pcs', 'Box', 'Pack', 'Bag', 'Dozen'];
  readonly filteredMeasureUnits$: Observable<string[]>;
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
    defaultVendorRate: [null, [Validators.min(0)]],
    marginPercent: [this.defaultMarginPercent, [Validators.required, Validators.min(0), Validators.max(100)]]
  });

  constructor(private fb: FormBuilder) {
    this.filteredMeasureUnits$ = this.form.get('measureUnit')!.valueChanges.pipe(
      startWith(this.form.get('measureUnit')!.value),
      map(value => this.filterMeasureUnits(value))
    );
  }

  ngOnChanges(): void {
    if (this.item) {
      this.form.patchValue({
        vendorHsnCode: this.item.vendorHsnCode,
        itemName: this.item.name,
        category: this.item.category,
        measureUnit: this.item.measureUnit,
        packagingUnit: this.item.packagingUnit,
        defaultVendorRate: this.resolveDefaultVendorRate(this.item.defaultVendorRate),
        marginPercent: this.resolveMarginPercent(this.item.marginPercent)
      });
      this.form.markAsPristine();
      return;
    }
    this.resetFormState();
  }

  submit(): void {
    this.normalizeMeasureUnitControl();
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

  normalizeMeasureUnitControl(): void {
    const raw = String(this.form.get('measureUnit')?.value ?? '');
    const normalized = this.normalizeMeasureUnit(raw);
    if (!normalized) {
      return;
    }
    if (!this.measureUnits.some(unit => unit.toLowerCase() === normalized.toLowerCase())) {
      this.measureUnits.push(normalized);
      this.measureUnits.sort((left, right) => left.localeCompare(right));
    }
    this.form.get('measureUnit')?.setValue(normalized, { emitEvent: false });
  }

  private filterMeasureUnits(value: unknown): string[] {
    const search = String(value ?? '').trim().toLowerCase();
    if (!search) {
      return [...this.measureUnits];
    }
    return this.measureUnits.filter(unit => unit.toLowerCase().includes(search));
  }

  private normalizeMeasureUnit(value: string): string {
    const normalized = (value ?? '').trim();
    if (!normalized) {
      return '';
    }
    const upper = normalized.toUpperCase();
    switch (upper) {
      case 'KG':
      case 'KGS':
      case 'KILOGRAM':
      case 'KILOGRAMS':
        return 'Kg';
      case 'GM':
      case 'GMS':
      case 'GRAM':
      case 'GRAMS':
        return 'Gram';
      case 'ML':
      case 'MILLILITRE':
      case 'MILLILITRES':
      case 'MILLILITER':
      case 'MILLILITERS':
        return 'Ml';
      case 'LTR':
      case 'LITRE':
      case 'LITRES':
      case 'LITER':
      case 'LITERS':
        return 'Litre';
      case 'PCS':
      case 'PC':
      case 'PIECE':
      case 'PIECES':
      case 'NOS':
      case 'NO':
      case 'NUMBER':
      case 'NUMBERS':
      case 'UNIT':
      case 'UNITS':
        return 'Nos';
      case 'TIN':
      case 'TINS':
        return 'Tin';
      case 'PACK':
      case 'PACKS':
      case 'PKT':
      case 'PKTS':
      case 'PACKET':
      case 'PACKETS':
        return 'Pack';
      default:
        return upper.substring(0, 1) + upper.substring(1).toLowerCase();
    }
  }

  private resetFormState(): void {
    this.form.reset({
      vendorHsnCode: '',
      itemName: '',
      category: this.initialCategory || '',
      measureUnit: 'Nos',
      packagingUnit: '',
      defaultVendorRate: null,
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

  private resolveDefaultVendorRate(value: number | null): number | null {
    if (value === null || value === undefined || value <= 0) {
      return null;
    }
    return value;
  }

}
