import { Location } from '@angular/common';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { VendorService } from '../../vendors/vendor.service';
import { OrderService } from '../order.service';
import { OrderCreateComponent } from './order-create.component';

describe('OrderCreateComponent', () => {
  let component: OrderCreateComponent;
  let fixture: ComponentFixture<OrderCreateComponent>;
  let orderService: jasmine.SpyObj<OrderService>;
  let vendorService: jasmine.SpyObj<VendorService>;
  let location: jasmine.SpyObj<Location>;

  beforeEach(async () => {
    orderService = jasmine.createSpyObj('OrderService', ['createOrderFromBranchImage', 'assignVendor', 'listBranches', 'listCompanies']);
    orderService.createOrderFromBranchImage.and.returnValue(of({ name: 'ORD-1' }));
    orderService.assignVendor.and.returnValue(of({}));
    orderService.listBranches.and.returnValue(of([{ name: 'SHOP-1', customer_name: 'Shop A' }]));
    orderService.listCompanies.and.returnValue(of([{ name: 'AAS' }]));

    vendorService = jasmine.createSpyObj('VendorService', ['listVendors']);
    vendorService.listVendors.and.returnValue(of([]));

    location = jasmine.createSpyObj('Location', ['back']);

    await TestBed.configureTestingModule({
      declarations: [OrderCreateComponent],
      imports: [
        ReactiveFormsModule,
        MatButtonModule,
        MatCardModule,
        MatFormFieldModule,
        MatIconModule,
        MatInputModule,
        MatSelectModule,
        NoopAnimationsModule
      ],
      providers: [
        { provide: OrderService, useValue: orderService },
        { provide: VendorService, useValue: vendorService },
        { provide: Location, useValue: location }
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
    expect(component.shops).toEqual([{ id: 'SHOP-1', name: 'Shop A' }]);
  });

  it('loads companies and defaults to AAS', () => {
    expect(orderService.listCompanies).toHaveBeenCalled();
    expect(component.companies).toEqual([{ id: 'AAS', name: 'AAS' }]);
    expect(component.detailsGroup.get('company')?.value).toBe('AAS');
  });

  it('marks form invalid when required fields are missing', () => {
    component.detailsGroup.patchValue({ customer: '', vendor: '', company: '', orderDate: '', deliveryDate: '' });
    expect(component.form.invalid).toBeTrue();
  });

  it('creates order from branch image and assigns vendor', () => {
    component.detailsGroup.patchValue({
      customer: 'SHOP-1',
      vendor: 'VENDOR-1',
      company: 'AAS',
      orderDate: '2024-01-10',
      deliveryDate: '2024-01-12'
    });
    component.onImageSelected({ target: { files: [new File(['x'], 'test.png')] } } as unknown as Event);

    component.submit();

    expect(orderService.createOrderFromBranchImage).toHaveBeenCalled();
    expect(orderService.assignVendor).toHaveBeenCalledWith('ORD-1', 'VENDOR-1');
  });
});
