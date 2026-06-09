import { FormBuilder } from '@angular/forms';
import { ItemFormComponent } from './item-form.component';

describe('ItemFormComponent', () => {
  let component: ItemFormComponent;

  beforeEach(() => {
    component = new ItemFormComponent(new FormBuilder());
  });

  it('prefills default item price when editing an item', () => {
    component.item = {
      id: 'ITEM-1',
      code: 'ITEM-1',
      name: 'Rice',
      category: 'Grocery',
      vendorId: 'SUP-1',
      vendorHsnCode: 'HSN-1',
      measureUnit: 'Kg',
      packagingUnit: 'Bag',
      defaultVendorRate: 42,
      marginPercent: 7,
      raw: {}
    };

    component.ngOnChanges();

    expect(component.form.get('defaultVendorRate')?.value).toBe(42);
  });

  it('emits default item price on save', () => {
    const saveSpy = spyOn(component.save, 'emit');
    component.form.setValue({
      vendorHsnCode: 'HSN-1',
      itemName: 'Rice',
      category: 'Grocery',
      measureUnit: 'Kg',
      packagingUnit: 'Bag',
      defaultVendorRate: 42,
      marginPercent: 7
    });

    component.submit();

    expect(saveSpy).toHaveBeenCalledWith(jasmine.objectContaining({ defaultVendorRate: 42 }));
  });
});
