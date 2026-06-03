import { Location } from '@angular/common';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { CategoryService } from '../../categories/category.service';
import { ItemService } from '../../items/item.service';
import { VendorService } from '../../vendors/vendor.service';
import { OrderService } from '../order.service';
import { OrderCreateComponent } from './order-create.component';
import { CompanyContextService } from '../../shared/company-context.service';
import { ItemVendorPricingService } from '../../items/item-vendor-pricing.service';

describe('OrderCreateComponent', () => {
  let component: OrderCreateComponent;
  let fixture: ComponentFixture<OrderCreateComponent>;
  let orderService: jasmine.SpyObj<OrderService>;
  let categoryService: jasmine.SpyObj<CategoryService>;
  let itemService: jasmine.SpyObj<ItemService>;
  let vendorService: jasmine.SpyObj<VendorService>;
  let companyContextService: jasmine.SpyObj<CompanyContextService>;
  let itemVendorPricingService: jasmine.SpyObj<ItemVendorPricingService>;
  let location: jasmine.SpyObj<Location>;
  let router: jasmine.SpyObj<Router>;

  beforeEach(async () => {
    orderService = jasmine.createSpyObj('OrderService', [
      'createOrder',
      'createDirectOrderFromItems',
      'createOrderFromBranchImage',
      'captureVendorBill',
      'updateStatus',
      'uploadOrderImage',
      'listBranches'
    ]);
    orderService.createOrder.and.returnValue(of({ name: 'Shop_A_Grocery_20260315' }));
    orderService.createDirectOrderFromItems.and.returnValue(of({ order: { name: 'Shop_A_Grocery_20260315' } }));
    orderService.createOrderFromBranchImage.and.returnValue(of({ name: 'Shop_A_Grocery_20260315' }));
    orderService.captureVendorBill.and.returnValue(of({}));
    orderService.updateStatus.and.returnValue(of({}));
    orderService.uploadOrderImage.and.returnValue(of({}));
    orderService.listBranches.and.returnValue(of([{ name: 'SHOP-1', customer_name: 'Sukarta Aundh' }]));

    categoryService = jasmine.createSpyObj('CategoryService', ['listCategories']);
    categoryService.listCategories.and.returnValue(of([{ name: 'Grocery', item_group_name: 'Grocery' }]));

    itemService = jasmine.createSpyObj('ItemService', ['listItems']);
    itemService.listItems.and.returnValue(of([
      { name: 'ITEM-1', item_code: 'ITEM-1', item_name: 'Rice', item_group: 'Grocery', stock_uom: 'Nos', aas_vendor: 'SUP-1', aas_vendor_rate: 42, aas_gst_percent: 5 },
      { name: 'ITEM-1B', item_code: 'ITEM-1B', item_name: 'Dal', item_group: 'Grocery', stock_uom: 'Nos', aas_vendor: 'SUP-2', aas_vendor_rate: 50, aas_gst_percent: 12 },
      { name: 'ITEM-2', item_code: 'ITEM-2', item_name: 'Soap', item_group: 'Cleaning', stock_uom: 'Nos' }
    ]));

    vendorService = jasmine.createSpyObj('VendorService', ['listVendors']);
    vendorService.listVendors.and.returnValue(of([
      { name: 'SUP-1', supplier_name: 'Fresh Harvest', category: 'Grocery', disabled: 0 },
      { name: 'SUP-2', supplier_name: 'Daily Staples', category: 'Grocery', disabled: 0 },
      { name: 'SUP-3', supplier_name: 'CleanCo', category: 'Cleaning', disabled: 0 }
    ]));

    companyContextService = jasmine.createSpyObj('CompanyContextService', ['getContext']);
    companyContextService.getContext.and.returnValue(of({
      company: { id: 'Shree Siddhivinayak Suppliers', name: 'Shree Siddhivinayak Suppliers' },
      branch: null,
      companies: [{ name: 'Shree Siddhivinayak Suppliers' }],
      branches: []
    }));

    itemVendorPricingService = jasmine.createSpyObj('ItemVendorPricingService', ['listPricing']);
    itemVendorPricingService.listPricing.and.returnValue([]);

    location = jasmine.createSpyObj('Location', ['back']);
    router = jasmine.createSpyObj('Router', ['navigate']);
    router.navigate.and.returnValue(Promise.resolve(true));

    await TestBed.configureTestingModule({
      declarations: [OrderCreateComponent],
      imports: [
        ReactiveFormsModule,
        MatButtonModule,
        MatCardModule,
        MatCheckboxModule,
        MatFormFieldModule,
        MatIconModule,
        MatInputModule,
        MatSelectModule,
        MatSlideToggleModule,
        NoopAnimationsModule
      ],
      providers: [
        { provide: OrderService, useValue: orderService },
        { provide: CategoryService, useValue: categoryService },
        { provide: ItemService, useValue: itemService },
        { provide: VendorService, useValue: vendorService },
        { provide: CompanyContextService, useValue: companyContextService },
        { provide: ItemVendorPricingService, useValue: itemVendorPricingService },
        { provide: Location, useValue: location },
        { provide: Router, useValue: router }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(OrderCreateComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('creates the order create component', () => {
    expect(component).toBeTruthy();
  });

  it('loads branches into shops', () => {
    expect(orderService.listBranches).toHaveBeenCalled();
    expect(component.shops).toEqual([{ id: 'SHOP-1', name: 'Sukarta Aundh' }]);
  });

  it('loads categories and companies', () => {
    expect(categoryService.listCategories).toHaveBeenCalled();
    expect(companyContextService.getContext).toHaveBeenCalled();
    expect(vendorService.listVendors).toHaveBeenCalled();
    expect(component.categories).toEqual([{ id: 'Grocery', name: 'Grocery' }]);
    expect(component.selectedCompanyLabel).toBe('Shree Siddhivinayak Suppliers');
    expect(component.detailsGroup.get('company')?.value).toBe('Shree Siddhivinayak Suppliers');
  });

  it('shows all vendors and items for the selected category', () => {
    component.detailsGroup.patchValue({ category: 'Grocery' });

    expect(component.categoryVendors.map(vendor => vendor.name)).toEqual(['Daily Staples', 'Fresh Harvest']);
    expect(component.categoryItems).toEqual([]);
  });

  it('filters items by selected vendor and updates checkout totals when qty, rate, and gst change', () => {
    component.detailsGroup.patchValue({ category: 'Grocery', vendor: 'SUP-1' });

    expect(component.categoryItems.map(item => item.name)).toEqual(['Rice']);

    const item = component.categoryItems[0];
    component.toggleItemSelection(item.id, true);
    component.onQtyInput(item.id, '3');
    component.onRateInput(item.id, '100');
    component.onGstInput(item.id, '18');

    expect(component.selectedOrderItems.length).toBe(1);
    expect(component.selectedOrderItems[0].qty).toBe(3);
    expect(component.selectedOrderItems[0].rate).toBe(100);
    expect(component.selectedOrderItems[0].gstPercent).toBe(18);
    expect(component.vendorSubtotal).toBe(300);
    expect(component.gstTotal).toBe(54);
    expect(component.vendorInvoiceTotal).toBe(354);

    component.onTransportChargeInput('25.50');

    expect(component.transportCharge).toBe(25.5);
    expect(component.vendorInvoiceTotal).toBe(379.5);
  });

  it('includes transport charge in direct item-flow payload', () => {
    component.setCreateMode('items');
    component.detailsGroup.patchValue({
      customer: 'Sukarta Aundh',
      category: 'Grocery',
      vendor: 'SUP-1',
      company: 'Shree Siddhivinayak Suppliers',
      orderDate: '2026-06-04',
      deliveryDate: '2026-06-04'
    });
    const item = component.categoryItems[0];
    component.toggleItemSelection(item.id, true);
    component.onQtyInput(item.id, '2');
    component.onRateInput(item.id, '100');
    component.onGstInput(item.id, '5');
    component.onTransportChargeInput('30');

    component.submit();

    expect(orderService.createDirectOrderFromItems).toHaveBeenCalledWith(jasmine.objectContaining({
      transport_charge: 30
    }));
  });

  it('passes transport charge through fallback vendor bill capture', () => {
    orderService.createDirectOrderFromItems.and.returnValue(throwError(() => ({ status: 405 })));
    orderService.createOrder.and.returnValue(of({ name: 'SO-123' }));
    component.setCreateMode('items');
    component.detailsGroup.patchValue({
      customer: 'Sukarta Aundh',
      category: 'Grocery',
      vendor: 'SUP-1',
      company: 'Shree Siddhivinayak Suppliers',
      orderDate: '2026-06-04',
      deliveryDate: '2026-06-04'
    });
    const item = component.categoryItems[0];
    component.toggleItemSelection(item.id, true);
    component.onQtyInput(item.id, '1');
    component.onRateInput(item.id, '100');
    component.onGstInput(item.id, '5');
    component.onTransportChargeInput('15');

    component.submit();

    expect(orderService.createOrder).toHaveBeenCalledWith(jasmine.objectContaining({
      aas_transport_charge: 15
    }));
    expect(orderService.captureVendorBill).toHaveBeenCalledWith('SO-123', jasmine.objectContaining({
      transport_charge: 15
    }));
  });

  it('keeps the source switch working between upload images and select items', () => {
    expect(component.createMode).toBe('images');

    component.setCreateMode('items');
    expect(component.createMode).toBe('items');

    component.setCreateMode('images');
    expect(component.createMode).toBe('images');
  });
});
