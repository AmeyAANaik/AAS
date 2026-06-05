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
import { ItemService } from '../../items/item.service';
import { VendorService } from '../../vendors/vendor.service';
import { OrderService } from '../order.service';
import { OrderPageComponent } from './order-page.component';

describe('OrderPageComponent', () => {
  let component: OrderPageComponent;
  let fixture: ComponentFixture<OrderPageComponent>;
  let orderService: jasmine.SpyObj<OrderService>;
  let vendorService: jasmine.SpyObj<VendorService>;
  let itemService: jasmine.SpyObj<ItemService>;

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
          customer: 'Sukarta Aundh',
          aas_vendor: '',
          aas_category: 'Bakery Inputs',
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
    vendorService.listVendors.and.returnValue(of([
      { name: 'VENDOR-1', supplier_name: 'Vendor A', category: 'Bakery Inputs' },
      { name: 'VENDOR-2', supplier_name: 'Vendor B', category: 'Beverages' }
    ]));
    itemService = jasmine.createSpyObj('ItemService', ['listItems']);
    itemService.listItems.and.returnValue(of([
      { name: 'ITEM-28', item_code: 'ITEM-28', item_name: 'NIRMA YELLOW POWDER 1KG', item_group: 'Bakery Inputs', stock_uom: 'Nos', aas_margin_percent: 7 }
    ]));

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
        { provide: ItemService, useValue: itemService },
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

  it('filters vendor assignment options by order category', () => {
    const order = component.orders[0];
    component.selectOrder(order);

    expect(component.assignableVendorOptions.map(vendor => vendor.id)).toEqual(['VENDOR-1']);
  });

  it('allows PDF management after vendor PDF is already received', () => {
    const order = {
      ...component.orders[0],
      status: 'VENDOR_PDF_RECEIVED' as const,
      raw: { ...component.orders[0].raw, aas_status: 'VENDOR_PDF_RECEIVED' }
    };
    component.selectOrder(order);

    expect(component.canManageVendorPdf).toBeTrue();
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
    component.transportChargeControl.setValue(0);
    component.billRefControl.setValue('VB-1');
    component.billDateControl.setValue(new Date('2024-01-10'));

    component.captureBill();

    expect(orderService.captureVendorBill).toHaveBeenCalledWith(order.name, {
      vendor_bill_total: 100,
      vendor_bill_ref: 'VB-1',
      vendor_bill_date: '2024-01-10',
      transport_charge: 0,
      allow_mismatch: false
    });
  });

  it('keeps GST percent when updating reviewed order items', () => {
    const order = {
      ...component.orders[0],
      status: 'VENDOR_PDF_RECEIVED' as const,
      raw: { ...component.orders[0].raw, aas_status: 'VENDOR_PDF_RECEIVED' }
    };
    orderService.getOrder.and.returnValue(of({
      data: {
        items: [
          {
            item_code: 'ITEM-1',
            item_name: 'Item 1',
            qty: 2,
            rate: 50,
            amount: 100,
            aas_margin_percent: 12,
            aas_gst_percent: 5
          }
        ]
      }
    }));
    orderService.updateOrderItems.and.returnValue(of({
      items: [
        {
          item_code: 'ITEM-1',
          item_name: 'Item 1',
          qty: 2,
          rate: 50,
          amount: 100,
          aas_margin_percent: 12,
          aas_gst_percent: 5
        }
      ]
    }));

    component.selectOrder(order);
    component.saveOrderLines();

    expect(orderService.updateOrderItems).toHaveBeenCalledWith(order.name, [
      jasmine.objectContaining({
        item_code: 'ITEM-1',
        aas_gst_percent: 5
      })
    ]);
  });

  it('saves cleaned order description without changing item name', () => {
    const order = {
      ...component.orders[0],
      status: 'VENDOR_PDF_RECEIVED' as const,
      raw: { ...component.orders[0].raw, aas_status: 'VENDOR_PDF_RECEIVED' }
    };
    orderService.getOrder.and.returnValue(of({
      data: {
        items: [
          {
            item_code: 'ITEM-1',
            item_name: 'ATTA',
            description: 'SFK ATTA',
            qty: 1,
            rate: 50,
            amount: 50,
            aas_margin_percent: 12,
            aas_gst_percent: 5
          }
        ]
      }
    }));

    component.selectOrder(order);
    component.orderLines[0].display_description = 'ATTA';
    component.saveOrderLines();

    expect(orderService.updateOrderItems).toHaveBeenCalledWith(order.name, [
      jasmine.objectContaining({
        item_code: 'ITEM-1',
        item_name: 'ATTA',
        display_description: 'ATTA'
      })
    ]);
  });

  it('applies bulk description cleanup tools to all visible lines', () => {
    component.orderLines = [
      {
        item_code: 'ITEM-1',
        item_name: 'SFK SAMRAT ATTA',
        display_description: 'SFK SAMRAT ATTA',
        qty: 1,
        rate: 10,
        amount: 10,
        aas_margin_percent: 7,
        aas_gst_percent: 5,
        mrpApplied: false
      },
      {
        item_code: 'ITEM-2',
        item_name: 'SFK MAIDA SAMRAT',
        display_description: 'SFK MAIDA SAMRAT',
        qty: 1,
        rate: 10,
        amount: 10,
        aas_margin_percent: 7,
        aas_gst_percent: 5,
        mrpApplied: false
      }
    ];

    component.toggleDescriptionCleanupToken('SFK');
    component.descriptionBulkRemoveText = 'SAMRAT';
    component.descriptionReplaceFrom = 'MAIDA';
    component.descriptionReplaceTo = 'FLOUR';

    component.applyDescriptionToolsToAll();

    expect(component.orderLines[0].display_description).toBe('ATTA');
    expect(component.orderLines[1].display_description).toBe('FLOUR');
    expect(component.activeDescriptionRuleLabels).toEqual([
      'Remove SFK',
      'Remove: SAMRAT',
      'Replace "MAIDA" with "FLOUR"'
    ]);
  });

  it('includes transport charge in bill validation and capture payload', () => {
    const order = {
      ...component.orders[0],
      status: 'VENDOR_PDF_RECEIVED' as const,
      billTotal: 115,
      transportCharge: 15,
      billRef: 'VB-2',
      billDate: new Date('2024-01-10'),
      raw: { ...component.orders[0].raw, aas_status: 'VENDOR_PDF_RECEIVED', aas_transport_charge: 15 }
    };
    orderService.getOrder.and.returnValue(of({
      data: {
        items: [
          { item_code: 'ITEM-1', item_name: 'Item 1', qty: 2, rate: 50, amount: 100, aas_margin_percent: 12 }
        ]
      }
    }));

    component.selectOrder(order);
    component.transportChargeControl.setValue(15);
    component.billTotalControl.setValue(115);
    component.billRefControl.setValue('VB-2');
    component.billDateControl.setValue(new Date('2024-01-10'));

    expect(component.expectedBillTotal).toBe(115);
    expect(component.billMatchesItems).toBeTrue();

    component.captureBill();

    expect(orderService.captureVendorBill).toHaveBeenCalledWith(order.name, {
      vendor_bill_total: 115,
      vendor_bill_ref: 'VB-2',
      vendor_bill_date: '2024-01-10',
      transport_charge: 15,
      allow_mismatch: false
    });
  });

  it('treats sub-1 bill difference as round off', () => {
    const order = {
      ...component.orders[0],
      status: 'VENDOR_PDF_RECEIVED' as const,
      billTotal: 100.7,
      transportCharge: 0,
      billRef: 'VB-RND',
      billDate: new Date('2024-01-10'),
      raw: { ...component.orders[0].raw, aas_status: 'VENDOR_PDF_RECEIVED', aas_transport_charge: 0 }
    };
    orderService.getOrder.and.returnValue(of({
      data: {
        items: [
          { item_code: 'ITEM-1', item_name: 'Item 1', qty: 2, rate: 50, amount: 100, aas_margin_percent: 12 }
        ]
      }
    }));

    component.selectOrder(order);
    component.billTotalControl.setValue(100.7);
    component.billRefControl.setValue('VB-RND');
    component.billDateControl.setValue(new Date('2024-01-10'));

    expect(component.billDiff).toBeCloseTo(0.7, 2);
    expect(component.billMatchesItems).toBeTrue();

    component.captureBill();

    expect(orderService.captureVendorBill).toHaveBeenCalledWith(order.name, {
      vendor_bill_total: 100.7,
      vendor_bill_ref: 'VB-RND',
      vendor_bill_date: '2024-01-10',
      transport_charge: 0,
      allow_mismatch: false
    });
  });

  it('includes GST in the expected bill total', () => {
    const order = {
      ...component.orders[0],
      status: 'VENDOR_PDF_RECEIVED' as const,
      billTotal: 105,
      transportCharge: 0,
      billRef: 'VB-GST',
      billDate: new Date('2024-01-10'),
      raw: { ...component.orders[0].raw, aas_status: 'VENDOR_PDF_RECEIVED', aas_transport_charge: 0 }
    };
    orderService.getOrder.and.returnValue(of({
      data: {
        items: [
          {
            item_code: 'ITEM-1',
            item_name: 'Item 1',
            qty: 2,
            rate: 50,
            amount: 100,
            aas_margin_percent: 12,
            aas_gst_percent: 5
          }
        ]
      }
    }));

    component.selectOrder(order);
    component.billTotalControl.setValue(105);

    expect(component.itemsSubtotal).toBe(100);
    expect(component.gstTotal).toBe(5);
    expect(component.expectedBillTotal).toBe(105);
    expect(component.billMatchesItems).toBeTrue();
  });

  it('allows capture as mismatched bill when there is no transport', () => {
    const order = {
      ...component.orders[0],
      status: 'VENDOR_PDF_RECEIVED' as const,
      billTotal: 110,
      transportCharge: 0,
      billRef: 'VB-3',
      billDate: new Date('2024-01-10'),
      raw: { ...component.orders[0].raw, aas_status: 'VENDOR_PDF_RECEIVED', aas_transport_charge: 0 }
    };
    orderService.getOrder.and.returnValue(of({
      data: {
        items: [
          { item_code: 'ITEM-1', item_name: 'Item 1', qty: 2, rate: 50, amount: 100, aas_margin_percent: 12 }
        ]
      }
    }));

    component.selectOrder(order);
    component.billTotalControl.setValue(110);
    component.billRefControl.setValue('VB-3');
    component.billDateControl.setValue(new Date('2024-01-10'));

    expect(component.billMatchesItems).toBeFalse();
    expect(component.canProceedAsMismatchBill).toBeTrue();

    component.mismatchOverrideControl.setValue(true);
    component.captureBill();

    expect(orderService.captureVendorBill).toHaveBeenCalledWith(order.name, {
      vendor_bill_total: 110,
      vendor_bill_ref: 'VB-3',
      vendor_bill_date: '2024-01-10',
      transport_charge: 0,
      allow_mismatch: true
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
      jasmine.objectContaining({
        item_code: 'ITEM-1',
        qty: 2,
        rate: 50,
        aas_margin_percent: 15
      })
    ]);
  });

  it('allows a manual recovery row with item name but no item code to be saved', () => {
    const order = component.orders[0];
    component.selectedOrder = order as any;
    component.orderLines = [
      {
        source_serial: 28,
        item_code: '',
        item_name: 'Manual recovery row',
        qty: 1,
        rate: 60,
        amount: 60,
        aas_margin_percent: 7,
        aas_vendor_rate: 60,
        aas_mrp: null,
        aas_gst_percent: 0,
        manual_entry: true,
        parse_note: 'Added manually because invoice row 28 was not parsed.',
        mrpApplied: false
      }
    ];

    component.saveOrderLines();

    expect(orderService.updateOrderItems).toHaveBeenCalledWith(order.name, [
      jasmine.objectContaining({
        item_code: '',
        item_name: 'Manual recovery row',
        manual_entry: true
      })
    ]);
  });

  it('defaults transport application to on when captured transport exists', () => {
    const order = {
      ...component.orders[0],
      status: 'VENDOR_BILL_CAPTURED' as const,
      transportCharge: 15,
      raw: { ...component.orders[0].raw, aas_status: 'VENDOR_BILL_CAPTURED', aas_transport_charge: 15 }
    };

    component.selectOrder(order);

    expect(component.applyTransportToInvoiceControl.value).toBeTrue();
    expect(component.shouldApplyTransportToInvoice).toBeTrue();
  });

  it('passes the transport toggle when creating the sell order', () => {
    const order = {
      ...component.orders[0],
      status: 'VENDOR_BILL_CAPTURED' as const,
      transportCharge: 15,
      raw: { ...component.orders[0].raw, aas_status: 'VENDOR_BILL_CAPTURED', aas_transport_charge: 15 }
    };
    component.selectOrder(order);
    component.sellPreview = {
      estimatedPrice: 107,
      itemsCount: 2,
      raw: { orderId: order.name, vendorBillTotal: 100, marginPercent: 7, sellAmount: 107, marginAmount: 7 }
    };

    component.applyTransportToInvoiceControl.setValue(true);
    component.createSellOrder();

    expect(orderService.createSellOrder).toHaveBeenCalledWith(order.name, {
      apply_transport_to_invoice: true
    });
  });

  it('prefills a manual recovery row with the missing invoice serial label', () => {
    component.pdfData = {
      completeness: {
        missingSerials: [28]
      }
    };

    component.addManualOrderLine();

    expect(component.orderLines.at(-1)).toEqual(
      jasmine.objectContaining({
        item_name: 'Missing invoice row 28',
        aas_margin_percent: 7,
        manual_entry: true,
        parse_note: 'Added manually because invoice row 28 was not parsed.'
      })
    );
  });

  it('treats a missing parser serial as resolved once a manual recovery row covers it', () => {
    component.pdfData = {
      completeness: {
        missingSerials: [28]
      }
    };

    expect(component.hasMissingParsedRows).toBeTrue();
    expect(component.unresolvedMissingParserSerials).toEqual([28]);

    component.addManualOrderLine();

    expect(component.unresolvedMissingParserSerials).toEqual([]);
    expect(component.hasMissingParsedRows).toBeFalse();
  });

  it('surfaces a strong validation message when parser serials are still missing', () => {
    component.pdfData = {
      completeness: {
        missingSerials: [67]
      }
    };
    component.orderLines = [
      {
        source_serial: 1,
        item_code: 'ITEM-1',
        item_name: 'Item 1',
        qty: 1,
        rate: 100,
        amount: 100,
        aas_margin_percent: 7,
        aas_vendor_rate: 100,
        aas_mrp: null,
        aas_gst_percent: 0,
        manual_entry: false,
        parse_note: null,
        mrpApplied: false
      }
    ];

    expect(component.billValidationMessage).toContain('Invoice parser missed serial row(s): 67');
    expect(component.isBillFormValid()).toBeFalse();
  });

  it('uses parser context to suggest the missing item name for a manual recovery row', () => {
    component.pdfData = {
      completeness: {
        missingSerials: [28],
        missingSerialContexts: [
          {
            serial: 28,
            parserContext: ['28 NIRMA YELLOW POWDER 1KG 34029011 60.000 18.00 60.17 3610.10']
          }
        ]
      }
    };

    component.addManualOrderLine();

    expect(component.orderLines.at(-1)).toEqual(
      jasmine.objectContaining({
        item_name: 'NIRMA YELLOW POWDER 1KG',
        item_code: 'ITEM-28',
        aas_margin_percent: 7,
        manual_entry: true
      })
    );
  });

  it('resolves the ERP item code from a unique partial manual item name', () => {
    const line = {
      source_serial: 28,
      item_code: '',
      item_name: 'nirma yellow',
      qty: 1,
      rate: 0,
      amount: 0,
      aas_margin_percent: 0,
      aas_vendor_rate: 0,
      aas_mrp: null,
      aas_gst_percent: null,
      manual_entry: true,
      parse_note: null,
      mrpApplied: false
    };

    component.recalcLine(line);

    expect(line.item_code).toBe('ITEM-28');
    expect(line.aas_margin_percent).toBe(0);
  });

  it('searches manual recovery item suggestions by partial item text', () => {
    expect(component.getManualItemMatches('yellow')).toEqual([
      jasmine.objectContaining({
        code: 'ITEM-28',
        name: 'NIRMA YELLOW POWDER 1KG'
      })
    ]);
  });

  it('uses extracted parser serials for the displayed row serial numbers', () => {
    component.pdfData = {
      orderItems: [
        { item_code: 'ITEM-1', item_name: 'Item 1', qty: 2, rate: 50, amount: 100, aas_margin_percent: 12 }
      ],
      completeness: {
        extractedSerials: [7]
      }
    };
    component.orderLines = (component.pdfData.orderItems ?? []).map((row: any) => ({
      source_serial: 7,
      item_code: row.item_code,
      item_name: row.item_name,
      qty: row.qty,
      rate: row.rate,
      amount: row.amount,
      aas_margin_percent: row.aas_margin_percent,
      aas_vendor_rate: row.rate,
      aas_mrp: null,
      aas_gst_percent: null,
      manual_entry: false,
      parse_note: null,
      mrpApplied: false
    }));

    expect(component.getOrderLineSerial(component.orderLines[0], 0)).toBe('7');
  });

  it('does not add GST twice when the parsed layout uses after-tax line values', () => {
    component.pdfData = {
      fieldMapping: {
        itemMappings: [
          { targetField: 'rate', sourceLabel: 'Rate After Tax' },
          { targetField: 'total', sourceLabel: 'Total Value After Tax' }
        ]
      }
    };
    component.orderLines = [
      {
        source_serial: 1,
        item_code: 'ITEM-1',
        item_name: 'Item 1',
        qty: 5,
        rate: 110,
        amount: 550,
        aas_margin_percent: 0,
        aas_vendor_rate: 110,
        aas_mrp: null,
        aas_gst_percent: 5,
        manual_entry: false,
        parse_note: null,
        mrpApplied: false
      }
    ];

    expect(component.gstIncludedInLineAmounts).toBeTrue();
    expect(component.gstTotal).toBe(0);
    expect(component.itemsTotal).toBe(550);
  });

  it('infers GST is already included when the entered bill total matches line totals more closely than subtotal plus GST', () => {
    component.billTotalControl.setValue(161916);
    component.transportChargeControl.setValue(0);
    component.orderLines = [
      {
        source_serial: 1,
        item_code: 'ITEM-1',
        item_name: 'Inclusive line item',
        qty: 1,
        rate: 161915.3,
        amount: 161915.3,
        aas_margin_percent: 0,
        aas_vendor_rate: 161915.3,
        aas_mrp: null,
        aas_gst_percent: 3.10838,
        manual_entry: false,
        parse_note: null,
        mrpApplied: false
      }
    ];

    expect(component.gstIncludedInLineAmounts).toBeTrue();
    expect(component.gstTotal).toBe(0);
    expect(component.expectedBillTotal).toBe(161915.3);
    expect(component.billDiff).toBeCloseTo(0.7, 1);
  });
});
