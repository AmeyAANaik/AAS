import { CommonModule } from '@angular/common';
import { NO_ERRORS_SCHEMA } from '@angular/core';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { RouterTestingModule } from '@angular/router/testing';
import { of } from 'rxjs';
import { DashboardComponent } from './dashboard.component';
import { DashboardService } from './dashboard.service';
import { DashboardSnapshot } from './dashboard.model';

const snapshotStub: DashboardSnapshot = {
  orderStatus: [{ status: 'Pending', count: 2 }],
  billsByBranch: [{ name: 'Branch A', total: 1200 }],
  billsByVendor: [{ name: 'Vendor X', total: 900 }],
  stockSnapshot: { totalItems: 4, totalQuantity: 150 },
  salesSummary: {
    invoiceCount: 3,
    totalRevenue: 4500,
    dateRangeLabel: '2025-01-01 to 2025-01-31',
    averageDailyRevenue: 145.16,
    peakRevenue: 2200,
    peakRevenueLabel: '2025-01-08'
  },
  revenueSeries: [
    { label: '2025-01-01', shortLabel: '01 Jan', value: 1200 },
    { label: '2025-01-02', shortLabel: '02 Jan', value: 800 },
    { label: '2025-01-03', shortLabel: '03 Jan', value: 2500 }
  ],
  vendorOperations: {
    totalVendors: 4,
    vendorsWithPendingOrders: 2,
    totalPendingOrders: 6,
    awaitingPdf: 2,
    awaitingBillCapture: 3,
    totalPendingBillAmount: 15000
  },
  branchOperations: {
    totalBranches: 3,
    branchesWithPendingOrders: 2,
    totalPendingOrders: 5,
    awaitingVendorAssignment: 1,
    awaitingVendorResponse: 3,
    openReceivableAmount: 4200
  },
  periodLabel: '2025-01'
};

describe('DashboardComponent', () => {
  let fixture: ComponentFixture<DashboardComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [DashboardComponent],
      imports: [CommonModule, MatButtonModule, MatCardModule, MatTableModule, RouterTestingModule, NoopAnimationsModule],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        {
          provide: DashboardService,
          useValue: {
            getDashboardSnapshot: () => of(snapshotStub)
          }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(DashboardComponent);
    fixture.detectChanges();
  });

  it('should create', () => {
    const component = fixture.componentInstance;
    expect(component).toBeTruthy();
  });

  it('should render all dashboard widgets', () => {
    const compiled = fixture.nativeElement as HTMLElement;
    const text = compiled.textContent ?? '';

    expect(text).toContain('Order status summary');
    expect(text).toContain('Bills due per branch');
    expect(text).toContain('Bills due per vendor');
    expect(text).toContain('Vendor operations');
    expect(text).toContain('Branch operations');
    expect(text).toContain('Sales / revenue summary');
    expect(text).toContain('Sales / revenue trend');
    expect(text).toContain('Bills per branch');
    expect(text).not.toContain('Stock on hand');
  });
});
