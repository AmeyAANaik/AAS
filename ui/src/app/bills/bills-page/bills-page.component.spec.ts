import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatDialog } from '@angular/material/dialog';
import { MatDividerModule } from '@angular/material/divider';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatExpansionModule } from '@angular/material/expansion';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatNativeDateModule } from '@angular/material/core';
import { MatSelectModule } from '@angular/material/select';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatTableModule } from '@angular/material/table';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { RouterTestingModule } from '@angular/router/testing';
import { of } from 'rxjs';
import { BranchService } from '../../branches/branch.service';
import { CategoryService } from '../../categories/category.service';
import { ItemService } from '../../items/item.service';
import { OrderService } from '../../orders/order.service';
import { EmptyStateComponent } from '../../shared/empty-state/empty-state.component';
import { PageHeaderComponent } from '../../shared/page-header/page-header.component';
import { StatusPillComponent } from '../../shared/status-pill/status-pill.component';
import { VendorService } from '../../vendors/vendor.service';
import { BillsService } from '../bills.service';
import { InvoiceCreateComponent } from '../invoice-create/invoice-create.component';
import { PaymentFormComponent } from '../payment-form/payment-form.component';
import { BillsPageComponent } from './bills-page.component';

describe('BillsPageComponent', () => {
  let component: BillsPageComponent;
  let fixture: ComponentFixture<BillsPageComponent>;
  let billsService: jasmine.SpyObj<BillsService>;

  beforeEach(async () => {
    billsService = jasmine.createSpyObj('BillsService', ['listInvoices', 'downloadInvoicePdf', 'exportInvoices', 'deleteInvoice']);
    billsService.listInvoices.and.returnValue(
      of([
        { name: 'INV-1', customer: 'Sukarta Aundh', posting_date: '2024-01-10', grand_total: 120, status: 'Paid' },
        { name: 'INV-2', customer: 'Shop B', posting_date: '2024-01-11', grand_total: 80, status: 'Unpaid' }
      ])
    );
    billsService.downloadInvoicePdf.and.returnValue(of(new Blob()));
    billsService.exportInvoices.and.returnValue(of(new Blob()));
    billsService.deleteInvoice.and.returnValue(of({}));

    await TestBed.configureTestingModule({
      declarations: [BillsPageComponent, InvoiceCreateComponent, PaymentFormComponent],
      imports: [
        ReactiveFormsModule,
        RouterTestingModule,
        MatButtonModule,
        MatButtonToggleModule,
        MatCardModule,
        MatDatepickerModule,
        MatDividerModule,
        MatExpansionModule,
        MatFormFieldModule,
        MatIconModule,
        MatInputModule,
        MatNativeDateModule,
        MatSelectModule,
        MatSlideToggleModule,
        MatTableModule,
        NoopAnimationsModule,
        EmptyStateComponent,
        PageHeaderComponent,
        StatusPillComponent
      ],
      providers: [
        { provide: BillsService, useValue: billsService },
        { provide: BranchService, useValue: { listBranches: () => of([{ name: 'Sukarta Aundh', customer_name: 'Sukarta Aundh', aas_invoice_email: 'billing@example.com', aas_whatsapp_number: '+919999999999' }]) } },
        { provide: VendorService, useValue: { listVendors: () => of([]) } },
        { provide: CategoryService, useValue: { listCategories: () => of([]) } },
        { provide: ItemService, useValue: { listItems: () => of([]) } },
        { provide: OrderService, useValue: { listOrders: () => of([]) } },
        { provide: MatDialog, useValue: { open: () => ({ afterClosed: () => of(undefined) }) } }
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

  it('only allows draft invoices to be deleted', () => {
    expect(component.canDelete(component.invoices[0])).toBeTrue();
    expect(component.canDelete({ ...component.invoices[0], id: '' })).toBeFalse();
  });

  it('enables send actions when branch delivery details exist', () => {
    expect(component.canSendEmail(component.invoices[0])).toBeTrue();
    expect(component.canSendWhatsApp(component.invoices[0])).toBeTrue();
  });
});
