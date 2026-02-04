import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { BillsService } from '../bills.service';
import { InvoiceCreateComponent } from './invoice-create.component';

describe('InvoiceCreateComponent', () => {
  let component: InvoiceCreateComponent;
  let fixture: ComponentFixture<InvoiceCreateComponent>;
  let billsService: jasmine.SpyObj<BillsService>;

  beforeEach(async () => {
    billsService = jasmine.createSpyObj('BillsService', ['createInvoice', 'getOrderSnapshot']);
    billsService.createInvoice.and.returnValue(of({ name: 'INV-1' }));
    billsService.getOrderSnapshot.and.returnValue(
      of({ customer: 'Shop A', company: 'aas', items: [{ item_code: 'ITM-1', qty: 2, rate: 10 }] })
    );

    await TestBed.configureTestingModule({
      declarations: [InvoiceCreateComponent],
      imports: [
        ReactiveFormsModule,
        MatButtonModule,
        MatCardModule,
        MatFormFieldModule,
        MatInputModule,
        MatSelectModule,
        NoopAnimationsModule
      ],
      providers: [{ provide: BillsService, useValue: billsService }]
    }).compileComponents();

    fixture = TestBed.createComponent(InvoiceCreateComponent);
    component = fixture.componentInstance;
    component.customers = [{ id: 'SHOP-1', name: 'Shop A' }];
    component.items = [{ id: 'ITEM-1', name: 'Item A', code: 'ITM-1' }];
    component.orders = [{ id: 'ORD-1', name: 'ORD-1' }];
    fixture.detectChanges();
  });

  it('creates the invoice create component', () => {
    expect(component).toBeTruthy();
  });

  it('submits manual invoice payload', () => {
    component.setMode('manual');
    component.manualForm.patchValue({
      customer: 'SHOP-1',
      company: 'aas',
      itemCode: 'ITM-1',
      qty: 2,
      rate: 12
    });

    component.submit();

    expect(billsService.createInvoice).toHaveBeenCalledWith({
      customer: 'SHOP-1',
      company: 'aas',
      items: [{ item_code: 'ITM-1', qty: 2, rate: 12 }]
    });
  });

  it('loads order and submits invoice from order', () => {
    component.setMode('order');
    component.orderForm.patchValue({ orderId: 'ORD-1' });
    component.loadOrder();

    component.submit();

    expect(billsService.getOrderSnapshot).toHaveBeenCalledWith('ORD-1');
    expect(billsService.createInvoice).toHaveBeenCalledWith({
      customer: 'Shop A',
      company: 'aas',
      items: [{ item_code: 'ITM-1', qty: 2, rate: 10 }]
    });
  });
});
