import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatTableModule } from '@angular/material/table';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { BranchService } from '../../branches/branch.service';
import { ItemService } from '../../items/item.service';
import { OrderService } from '../../orders/order.service';
import { BillsService } from '../bills.service';
import { InvoiceCreateComponent } from '../invoice-create/invoice-create.component';
import { PaymentFormComponent } from '../payment-form/payment-form.component';
import { BillsPageComponent } from './bills-page.component';

describe('BillsPageComponent', () => {
  let component: BillsPageComponent;
  let fixture: ComponentFixture<BillsPageComponent>;
  let billsService: jasmine.SpyObj<BillsService>;

  beforeEach(async () => {
    billsService = jasmine.createSpyObj('BillsService', ['listInvoices', 'downloadInvoicePdf', 'exportInvoices']);
    billsService.listInvoices.and.returnValue(
      of([
        { name: 'INV-1', customer: 'Shop A', posting_date: '2024-01-10', grand_total: 120, status: 'Paid' },
        { name: 'INV-2', customer: 'Shop B', posting_date: '2024-01-11', grand_total: 80, status: 'Unpaid' }
      ])
    );
    billsService.downloadInvoicePdf.and.returnValue(of(new Blob()));
    billsService.exportInvoices.and.returnValue(of(new Blob()));

    await TestBed.configureTestingModule({
      declarations: [BillsPageComponent, InvoiceCreateComponent, PaymentFormComponent],
      imports: [
        ReactiveFormsModule,
        MatButtonModule,
        MatCardModule,
        MatExpansionModule,
        MatFormFieldModule,
        MatInputModule,
        MatSelectModule,
        MatTableModule,
        NoopAnimationsModule
      ],
      providers: [
        { provide: BillsService, useValue: billsService },
        { provide: BranchService, useValue: { listBranches: () => of([]) } },
        { provide: ItemService, useValue: { listItems: () => of([]) } },
        { provide: OrderService, useValue: { listOrders: () => of([]) } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(BillsPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('creates the bills page component', () => {
    expect(component).toBeTruthy();
  });

  it('loads invoices on init', () => {
    expect(billsService.listInvoices).toHaveBeenCalled();
  });

  it('classifies paid invoices for summary', () => {
    expect(component.summary.paid).toBe(1);
    expect(component.summary.open).toBe(1);
  });
});
