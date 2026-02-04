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
import { PaymentFormComponent } from './payment-form.component';

describe('PaymentFormComponent', () => {
  let component: PaymentFormComponent;
  let fixture: ComponentFixture<PaymentFormComponent>;
  let billsService: jasmine.SpyObj<BillsService>;

  beforeEach(async () => {
    billsService = jasmine.createSpyObj('BillsService', ['createPayment']);
    billsService.createPayment.and.returnValue(of({}));

    await TestBed.configureTestingModule({
      declarations: [PaymentFormComponent],
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

    fixture = TestBed.createComponent(PaymentFormComponent);
    component = fixture.componentInstance;
    component.customers = [{ id: 'SHOP-1', name: 'Shop A' }];
    fixture.detectChanges();
  });

  it('creates the payment form component', () => {
    expect(component).toBeTruthy();
  });

  it('submits payment payload', () => {
    component.form.patchValue({
      customer: 'SHOP-1',
      company: 'aas',
      amount: 250,
      referenceNo: 'REF-1',
      referenceDate: '2024-01-20'
    });

    component.submit();

    expect(billsService.createPayment).toHaveBeenCalledWith({
      customer: 'SHOP-1',
      company: 'aas',
      amount: 250,
      referenceNo: 'REF-1',
      referenceDate: '2024-01-20'
    });
  });
});
