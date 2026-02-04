import { CommonModule } from '@angular/common';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MatCardModule } from '@angular/material/card';
import { MatTableModule } from '@angular/material/table';
import { of } from 'rxjs';
import { DashboardComponent } from './dashboard.component';
import { DashboardService } from './dashboard.service';
import { DashboardSnapshot } from './dashboard.model';

const snapshotStub: DashboardSnapshot = {
  orderStatus: [{ status: 'Pending', count: 2 }],
  billsByBranch: [{ name: 'Branch A', total: 1200 }],
  billsByVendor: [{ name: 'Vendor X', total: 900 }],
  stockSnapshot: { totalItems: 4, totalQuantity: 150 },
  salesSummary: { invoiceCount: 3, totalRevenue: 4500, dateRangeLabel: '2025-01-01 to 2025-01-31' },
  periodLabel: '2025-01'
};

describe('DashboardComponent', () => {
  let fixture: ComponentFixture<DashboardComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [DashboardComponent],
      imports: [CommonModule, MatCardModule, MatTableModule, NoopAnimationsModule],
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
    const titles = Array.from(compiled.querySelectorAll('mat-card-title')).map(element => element.textContent?.trim());

    expect(titles).toContain('Order status summary');
    expect(titles).toContain('Bills due per branch');
    expect(titles).toContain('Bills due per vendor');
    expect(titles).toContain('Stock quantity snapshot');
    expect(titles).toContain('Sales / revenue summary');
  });
});
