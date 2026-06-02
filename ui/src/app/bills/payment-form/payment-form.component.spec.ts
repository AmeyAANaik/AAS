import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatButtonToggleModule } from '@angular/material/button-toggle';
import { MatCardModule } from '@angular/material/card';
import { MatDatepickerModule } from '@angular/material/datepicker';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatNativeDateModule } from '@angular/material/core';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { BillsService } from '../bills.service';
import { PaymentFormComponent } from './payment-form.component';

describe('PaymentFormComponent', () => {
  let component: PaymentFormComponent;
  let fixture: ComponentFixture<PaymentFormComponent>;
  let billsService: jasmine.SpyObj<BillsService>;

  beforeEach(async () => {
    billsService = jasmine.createSpyObj('BillsService', ['createPaymentWithAttachments', 'dueByCategory']);
    billsService.createPaymentWithAttachments.and.returnValue(of({ payment: { name: 'PAY-1', docstatus: 0, aas_payment_review_status: 'UNDER_REVIEW' }, files: [] }));
    billsService.dueByCategory.and.returnValue(of({ dueAmount: 250 }));

    await TestBed.configureTestingModule({
      declarations: [PaymentFormComponent],
      imports: [
        ReactiveFormsModule,
        MatButtonModule,
        MatButtonToggleModule,
        MatCardModule,
        MatDatepickerModule,
        MatFormFieldModule,
        MatInputModule,
        MatNativeDateModule,
        MatSelectModule,
        NoopAnimationsModule
      ],
      providers: [{ provide: BillsService, useValue: billsService }]
    }).compileComponents();

    fixture = TestBed.createComponent(PaymentFormComponent);
    component = fixture.componentInstance;
    component.defaultCompany = 'Shree Siddhivinayak Suppliers';
    component.customers = [{ id: 'SHOP-1', name: 'Sukarta Aundh', company: 'Shree Siddhivinayak Suppliers' }];
    component.categories = [
      { id: 'CAT-A', name: 'Bakery' },
      { id: 'CAT-B', name: 'Raw Material' }
    ];
    fixture.detectChanges();
  });

  it('creates the payment form component', () => {
    expect(component).toBeTruthy();
  });

  it('submits payment payload', () => {
    component.form.patchValue({ partyType: 'Customer', customer: 'SHOP-1', categoryId: 'CAT-A' });
    component.onCategoryChange();
    component.form.patchValue({
      amount: 250,
      paymentDate: new Date('2024-01-20T00:00:00.000Z'),
      modeOfPayment: 'Cash'
    });
    component.voucherFiles = [new File(['x'], 'evidence.png', { type: 'image/png' })];

    component.submit();

    expect(billsService.createPaymentWithAttachments).toHaveBeenCalled();
    const [payload, files] = billsService.createPaymentWithAttachments.calls.mostRecent().args as any[];
    expect(payload).toEqual({
      customer: 'SHOP-1',
      company: 'Shree Siddhivinayak Suppliers',
      amount: 250,
      paymentDate: '2024-01-20',
      modeOfPayment: 'Cash',
      partyType: 'Customer',
      categoryId: 'CAT-A'
    });
    expect(Array.isArray(files)).toBeTrue();
    expect(files.length).toBe(1);
  });

  it('loads due by category and pre-fills amount', () => {
    component.form.patchValue({ partyType: 'Customer', customer: 'SHOP-1', categoryId: 'CAT-A' });

    component.onCategoryChange();

    expect(billsService.dueByCategory).toHaveBeenCalledWith('Customer', 'SHOP-1', 'CAT-A');
    expect(component.hasLoadedDue).toBeTrue();
    expect(component.selectedDue).toBe(250);
    expect(component.form.get('amount')?.value).toBe(250);
  });

  it('resets downstream fields when category changes', () => {
    component.form.patchValue({ partyType: 'Customer', customer: 'SHOP-1', categoryId: 'CAT-A' });
    component.onCategoryChange();
    component.form.patchValue({
      modeOfPayment: 'Cash'
    });
    component.voucherFiles = [new File(['x'], 'evidence.png', { type: 'image/png' })];

    billsService.dueByCategory.calls.reset();
    billsService.dueByCategory.and.returnValue(of({ dueAmount: 100 }));
    const before = new Date();
    component.form.patchValue({ categoryId: 'CAT-B' });

    component.onCategoryChange();

    expect(billsService.dueByCategory).toHaveBeenCalledWith('Customer', 'SHOP-1', 'CAT-B');
    expect(component.hasLoadedDue).toBeTrue();
    expect(component.selectedDue).toBe(100);
    expect(component.form.get('amount')?.value).toBe(100);
    expect(component.form.get('modeOfPayment')?.value).toBe('');
    expect(component.voucherFiles.length).toBe(0);

    const paymentDate = component.form.get('paymentDate')?.value as Date;
    expect(paymentDate instanceof Date).toBeTrue();
    expect(paymentDate.getFullYear()).toBe(before.getFullYear());
    expect(paymentDate.getMonth()).toBe(before.getMonth());
    expect(paymentDate.getDate()).toBe(before.getDate());
  });
});
