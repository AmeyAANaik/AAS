import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDialog } from '@angular/material/dialog';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatStepperModule } from '@angular/material/stepper';
import { MatTableModule } from '@angular/material/table';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { VendorService } from '../../vendors/vendor.service';
import { OrderService } from '../order.service';
import { OrderPageComponent } from './order-page.component';

describe('OrderPageComponent', () => {
  let component: OrderPageComponent;
  let fixture: ComponentFixture<OrderPageComponent>;
  let orderService: jasmine.SpyObj<OrderService>;
  let vendorService: jasmine.SpyObj<VendorService>;

  beforeEach(async () => {
    orderService = jasmine.createSpyObj('OrderService', [
      'listOrders',
      'getOrder',
      'assignVendor',
      'updateStatus',
      'uploadVendorPdf',
      'updateOrderItems',
      'captureVendorBill',
      'getSellPreview',
      'createSellOrder'
    ]);
    orderService.listOrders.and.returnValue(
      of([
        {
          name: 'ORD-1',
          customer: 'Shop A',
          aas_vendor: '',
          aas_status: 'DRAFT',
          transaction_date: '2024-01-10',
          delivery_date: '2024-01-12',
          grand_total: 120
        },
        {
          name: 'ORD-2',
          customer: 'Shop B',
          aas_vendor: 'Vendor A',
          aas_status: 'SELL_ORDER_CREATED',
          transaction_date: '2024-01-08',
          delivery_date: '2024-01-09',
          grand_total: 90
        }
      ])
    );
    orderService.getOrder.and.returnValue(of({ data: { items: [] } }));
    orderService.assignVendor.and.returnValue(of({}));
    orderService.updateStatus.and.returnValue(of({}));
    orderService.uploadVendorPdf.and.returnValue(of({}));
    orderService.updateOrderItems.and.returnValue(of({ items: [] }));
    orderService.captureVendorBill.and.returnValue(of({}));
    orderService.getSellPreview.and.returnValue(
      of({ orderId: 'ORD-1', vendorBillTotal: 100, marginPercent: 7, sellAmount: 107, marginAmount: 7 })
    );
    orderService.createSellOrder.and.returnValue(of({}));

    vendorService = jasmine.createSpyObj('VendorService', ['listVendors']);
    vendorService.listVendors.and.returnValue(of([{ name: 'VENDOR-1', supplier_name: 'Vendor A' }]));

    await TestBed.configureTestingModule({
      declarations: [OrderPageComponent],
      imports: [
        ReactiveFormsModule,
        FormsModule,
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
        { provide: VendorService, useValue: vendorService },
        { provide: ActivatedRoute, useValue: { queryParamMap: of(convertToParamMap({})) } },
        {
          provide: MatDialog,
          useValue: {
            open: () => ({
              afterClosed: () => of(null)
            })
          }
        }
      ],
      schemas: [NO_ERRORS_SCHEMA]
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

  it('assigns vendor via order service', () => {
    const order = component.orders[0];
    component.selectOrder(order);
    component.vendorControl.setValue('VENDOR-1');

    component.assignVendor();

    expect(orderService.assignVendor).toHaveBeenCalledWith(order.name, 'VENDOR-1');
  });

  it('allows delete for vendor-pdf-received orders even when a purchase order is linked', () => {
    const order = {
      ...component.orders[0],
      status: 'VENDOR_PDF_RECEIVED' as const,
      raw: { ...component.orders[0].raw, aas_po: 'PO-1' }
    };

    expect(component.canDeleteOrder(order)).toBeTrue();
  });

  it('captures vendor bill without header margin', () => {
    const order = {
      ...component.orders[0],
      status: 'VENDOR_PDF_RECEIVED' as const,
      billTotal: 100,
      billRef: 'VB-1',
      billDate: new Date('2024-01-10'),
      raw: { ...component.orders[0].raw, aas_status: 'VENDOR_PDF_RECEIVED' }
    };
    orderService.getOrder.and.returnValue(of({
      data: {
        items: [
          { item_code: 'ITEM-1', item_name: 'Item 1', qty: 2, rate: 50, amount: 100, aas_margin_percent: 12 }
        ]
      }
    }));

    component.selectOrder(order);
    component.billTotalControl.setValue(100);
    component.billRefControl.setValue('VB-1');
    component.billDateControl.setValue(new Date('2024-01-10'));

    component.captureBill();

    expect(orderService.captureVendorBill).toHaveBeenCalledWith(order.name, {
      vendor_bill_total: 100,
      vendor_bill_ref: 'VB-1',
      vendor_bill_date: '2024-01-10'
    });
  });

  it('includes item margin in update order items payload', () => {
    const order = component.orders[0];
    orderService.getOrder.and.returnValue(of({
      data: {
        items: [
          { item_code: 'ITEM-1', item_name: 'Item 1', qty: 2, rate: 50, amount: 100, aas_margin_percent: 12 }
        ]
      }
    }));

    component.selectOrder(order);
    component.orderLines[0].aas_margin_percent = 15;

    component.saveOrderLines();

    expect(orderService.updateOrderItems).toHaveBeenCalledWith(order.name, [
      { item_code: 'ITEM-1', qty: 2, rate: 50, aas_margin_percent: 15 }
    ]);
  });
});
