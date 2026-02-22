import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { OrderService } from '../order.service';
import { OrderCreateComponent } from './order-create.component';

describe('OrderCreateComponent', () => {
  let component: OrderCreateComponent;
  let fixture: ComponentFixture<OrderCreateComponent>;
  let orderService: jasmine.SpyObj<OrderService>;

  beforeEach(async () => {
    orderService = jasmine.createSpyObj('OrderService', ['createOrderFromBranchImage', 'assignVendor']);
    orderService.createOrderFromBranchImage.and.returnValue(of({ name: 'ORD-1' }));
    orderService.assignVendor.and.returnValue(of({}));

    await TestBed.configureTestingModule({
      declarations: [OrderCreateComponent],
      imports: [
        ReactiveFormsModule,
        MatButtonModule,
        MatCardModule,
        MatFormFieldModule,
        MatInputModule,
        MatSelectModule,
        NoopAnimationsModule
      ],
      providers: [{ provide: OrderService, useValue: orderService }]
    }).compileComponents();

    fixture = TestBed.createComponent(OrderCreateComponent);
    component = fixture.componentInstance;
    component.shops = [{ id: 'SHOP-1', name: 'Shop A' }];
    component.vendors = [{ id: 'VENDOR-1', name: 'Vendor A' }];
    fixture.detectChanges();
  });

  it('creates the order create component', () => {
    expect(component).toBeTruthy();
  });

  it('marks form invalid when required fields are missing', () => {
    component.detailsGroup.patchValue({ customer: '', vendor: '', company: '', orderDate: '', deliveryDate: '' });
    expect(component.form.invalid).toBeTrue();
  });

  it('creates order from branch image and assigns vendor', () => {
    component.detailsGroup.patchValue({
      customer: 'SHOP-1',
      vendor: 'VENDOR-1',
      company: 'AAS Core',
      orderDate: '2024-01-10',
      deliveryDate: '2024-01-12'
    });
    component.onImageSelected({ target: { files: [new File(['x'], 'test.png')] } } as unknown as Event);

    component.submit();

    expect(orderService.createOrderFromBranchImage).toHaveBeenCalled();
    expect(orderService.assignVendor).toHaveBeenCalledWith('ORD-1', 'VENDOR-1');
  });
});
