import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatStepperModule } from '@angular/material/stepper';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { OrderService } from '../order.service';
import { OrderCreateComponent } from './order-create.component';

describe('OrderCreateComponent', () => {
  let component: OrderCreateComponent;
  let fixture: ComponentFixture<OrderCreateComponent>;
  let orderService: jasmine.SpyObj<OrderService>;

  beforeEach(async () => {
    orderService = jasmine.createSpyObj('OrderService', ['createOrder']);
    orderService.createOrder.and.returnValue(of({ name: 'ORD-1', customer: 'Shop A' }));

    await TestBed.configureTestingModule({
      declarations: [OrderCreateComponent],
      imports: [
        ReactiveFormsModule,
        MatButtonModule,
        MatCardModule,
        MatFormFieldModule,
        MatInputModule,
        MatSelectModule,
        MatSlideToggleModule,
        MatStepperModule,
        NoopAnimationsModule
      ],
      providers: [{ provide: OrderService, useValue: orderService }]
    }).compileComponents();

    fixture = TestBed.createComponent(OrderCreateComponent);
    component = fixture.componentInstance;
    component.shops = [{ id: 'SHOP-1', name: 'Shop A' }];
    component.items = [{ id: 'ITEM-1', name: 'Item A', code: 'ITM-1' }];
    fixture.detectChanges();
  });

  it('creates the order create component', () => {
    expect(component).toBeTruthy();
  });

  it('marks form invalid when required fields are missing', () => {
    component.detailsGroup.patchValue({ customer: '', company: '', orderDate: '', deliveryDate: '' });
    component.itemGroup.patchValue({ itemId: '', quantity: null });
    expect(component.form.invalid).toBeTrue();
  });

  it('submits an order with pricing when visible', () => {
    component.detailsGroup.patchValue({
      customer: 'SHOP-1',
      company: 'aas',
      orderDate: '2024-01-10',
      deliveryDate: '2024-01-12'
    });
    component.itemGroup.patchValue({ itemId: 'ITM-1', quantity: 2, pricingVisible: true, rate: 10 });

    component.submit();

    expect(orderService.createOrder).toHaveBeenCalledWith({
      customer: 'SHOP-1',
      company: 'aas',
      transaction_date: '2024-01-10',
      delivery_date: '2024-01-12',
      items: [{ item_code: 'ITM-1', qty: 2, rate: 10 }]
    });
  });

  it('forces rate to zero when pricing is hidden', () => {
    component.detailsGroup.patchValue({
      customer: 'SHOP-1',
      company: 'aas',
      orderDate: '2024-01-10',
      deliveryDate: '2024-01-12'
    });
    component.itemGroup.patchValue({ itemId: 'ITM-1', quantity: 2, pricingVisible: false, rate: 99 });

    component.submit();

    expect(orderService.createOrder).toHaveBeenCalledWith({
      customer: 'SHOP-1',
      company: 'aas',
      transaction_date: '2024-01-10',
      delivery_date: '2024-01-12',
      items: [{ item_code: 'ITM-1', qty: 2, rate: 0 }]
    });
  });
});
