import { Component, EventEmitter, Input, OnChanges, Output } from '@angular/core';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Vendor } from '../../vendors/vendor.model';
import { ItemVendorPricingEntry, ItemVendorPricingFormValue, ItemView } from '../item.model';
import { ItemVendorPricingService } from '../item-vendor-pricing.service';

@Component({
  selector: 'app-item-vendor-pricing',
  templateUrl: './item-vendor-pricing.component.html',
  styleUrl: './item-vendor-pricing.component.scss'
})
export class ItemVendorPricingComponent implements OnChanges {
  @Input() items: ItemView[] = [];
  @Input() vendors: Vendor[] = [];
  @Input() pricing: ItemVendorPricingEntry[] = [];
  @Output() pricingSaved = new EventEmitter<ItemVendorPricingEntry>();

  form: FormGroup = this.fb.group({
    itemId: ['', [Validators.required]],
    vendorId: ['', [Validators.required]],
    originalRate: [null, [Validators.required, Validators.min(0)]],
    marginPercent: [0, [Validators.required, Validators.min(0)]]
  });

  finalRate = 0;

  constructor(private fb: FormBuilder, private pricingService: ItemVendorPricingService) {
    this.form.valueChanges.subscribe(() => this.updateFinalRate());
  }

  ngOnChanges(): void {
    this.updateFinalRate();
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }
    const value = this.form.getRawValue() as ItemVendorPricingFormValue;
    const item = this.items.find(entry => entry.id === value.itemId);
    const vendor = this.activeVendors.find(entry => entry.name === value.vendorId || entry['supplier_name'] === value.vendorId);
    if (!item || !vendor) {
      return;
    }
    const originalRate = Number(value.originalRate) || 0;
    const marginPercent = Number(value.marginPercent) || 0;
    const entry: ItemVendorPricingEntry = {
      itemId: item.id,
      itemName: item.name,
      vendorId: String(vendor.name ?? vendor['supplier_name'] ?? ''),
      vendorName: String(vendor['supplier_name'] ?? vendor.name ?? ''),
      originalRate,
      marginPercent,
      finalRate: this.pricingService.calculateFinalRate(originalRate, marginPercent)
    };
    this.pricingSaved.emit(entry);
    this.clear();
  }

  clear(): void {
    this.form.reset({ itemId: '', vendorId: '', originalRate: null, marginPercent: 0 });
    this.finalRate = 0;
  }

  private updateFinalRate(): void {
    const originalRate = Number(this.form.get('originalRate')?.value) || 0;
    const marginPercent = Number(this.form.get('marginPercent')?.value) || 0;
    this.finalRate = this.pricingService.calculateFinalRate(originalRate, marginPercent);
  }

  get activeVendors(): Vendor[] {
    return this.vendors.filter(vendor => !this.isDisabled(vendor.disabled));
  }

  private isDisabled(value: unknown): boolean {
    if (value === null || value === undefined) {
      return false;
    }
    if (typeof value === 'boolean') {
      return value;
    }
    if (typeof value === 'number') {
      return value !== 0;
    }
    const text = String(value).trim().toLowerCase();
    if (!text) {
      return false;
    }
    return text === '1' || text === 'true' || text === 'yes';
  }
}
