import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatStepperModule } from '@angular/material/stepper';
import { MatTableModule } from '@angular/material/table';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { BranchService } from '../../branches/branch.service';
import { ItemService } from '../../items/item.service';
import { VendorService } from '../../vendors/vendor.service';
import { OrderService } from '../order.service';
import { OrderCreateComponent } from '../order-create/order-create.component';
import { OrderPageComponent } from './order-page.component';

describe('OrderPageComponent', () => {
  let component: OrderPageComponent;
  let fixture: ComponentFixture<OrderPageComponent>;
  let orderService: jasmine.SpyObj<OrderService>;

  beforeEach(async () => {
    orderService = jasmine.createSpyObj('OrderService', ['listOrders', 'assignVendor', 'updateStatus']);
    orderService.listOrders.and.returnValue(
      of([
        {
          name: 'ORD-1',
          customer: 'Shop A',
          aas_vendor: '',
          aas_status: 'Accepted',
          transaction_date: '2024-01-10',
          delivery_date: '2024-01-12',
          grand_total: 120
        },
        {
          name: 'ORD-2',
          customer: 'Shop B',
          aas_vendor: 'Vendor A',
          aas_status: 'Delivered',
          transaction_date: '2024-01-08',
          delivery_date: '2024-01-09',
          grand_total: 90
        }
      ])
    );
    orderService.assignVendor.and.returnValue(of({}));
    orderService.updateStatus.and.returnValue(of({}));

    await TestBed.configureTestingModule({
      declarations: [OrderPageComponent, OrderCreateComponent],
      imports: [
        ReactiveFormsModule,
        MatButtonModule,
        MatCardModule,
        MatExpansionModule,
        MatFormFieldModule,
        MatInputModule,
        MatSelectModule,
        MatSlideToggleModule,
        MatStepperModule,
        MatTableModule,
        NoopAnimationsModule
      ],
      providers: [
        { provide: OrderService, useValue: orderService },
        { provide: BranchService, useValue: { listBranches: () => of([]) } },
        { provide: VendorService, useValue: { listVendors: () => of([]) } },
        { provide: ItemService, useValue: { listItems: () => of([]) } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(OrderPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('creates the order page component', () => {
    expect(component).toBeTruthy();
  });

  it('loads orders on init', () => {
    expect(orderService.listOrders).toHaveBeenCalled();
  });

  it('prevents vendor assignment when order is delivered', () => {
    const delivered = component.orders.find(order => order.id === 'ORD-2');
    expect(delivered).toBeTruthy();
    expect(component.canAssignVendor(delivered!)).toBeFalse();
  });

  it('prevents status updates when order is delivered', () => {
    const delivered = component.orders.find(order => order.id === 'ORD-2');
    expect(delivered).toBeTruthy();
    expect(component.canUpdateStatus(delivered!)).toBeFalse();
  });

  it('assigns vendor via order service', () => {
    const order = component.orders[0];
    component.selectOrder(order);
    component.assignForm.patchValue({ vendorId: 'VENDOR-1' });

    component.assignVendor();

    expect(orderService.assignVendor).toHaveBeenCalledWith(order.id, 'VENDOR-1');
  });
});
