import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of, Subject } from 'rxjs';
import { BranchService } from '../branches/branch.service';
import { CategoryService } from '../categories/category.service';
import { ItemService } from '../items/item.service';
import { VendorService } from '../vendors/vendor.service';
import { AnalyticsService } from './analytics.service';
import { AnalyticsPageComponent } from './analytics-page.component';

describe('AnalyticsPageComponent', () => {
  let fixture: ComponentFixture<AnalyticsPageComponent>;
  let component: AnalyticsPageComponent;
  let analyticsService: jasmine.SpyObj<AnalyticsService>;

  beforeEach(async () => {
    analyticsService = jasmine.createSpyObj<AnalyticsService>('AnalyticsService', [
      'query',
      'itemPriceHistory',
      'gstr1',
      'export',
      'exportItemPriceHistory',
      'exportGstr1'
    ]);
    analyticsService.query.and.returnValue(of({ columns: [], rows: [], totalsRow: {}, kpis: [], warnings: [] }));
    analyticsService.itemPriceHistory.and.returnValue(of({ columns: [], rows: [], totalsRow: {}, kpis: [], warnings: [] }));
    analyticsService.gstr1.and.returnValue(of({ columns: [], rows: [], totalsRow: {}, kpis: [], warnings: [] }));
    analyticsService.export.and.returnValue(of(new Blob()));
    analyticsService.exportItemPriceHistory.and.returnValue(of(new Blob()));
    analyticsService.exportGstr1.and.returnValue(of(new Blob()));

    await TestBed.configureTestingModule({
      imports: [AnalyticsPageComponent, NoopAnimationsModule],
      providers: [
        { provide: AnalyticsService, useValue: analyticsService },
        { provide: VendorService, useValue: { listVendors: () => of([]) } },
        { provide: BranchService, useValue: { listBranches: () => of([]) } },
        { provide: CategoryService, useValue: { listCategories: () => of([]) } },
        { provide: ItemService, useValue: { listItems: () => of([]) } }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(AnalyticsPageComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('shows only vendor, branch, and category filters in explorer mode', () => {
    const itemInput = fixture.nativeElement.querySelector('input[formcontrolname="item"]');

    expect(fixture.nativeElement.querySelector('input[formcontrolname="vendor"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('input[formcontrolname="branch"]')).not.toBeNull();
    expect(fixture.nativeElement.querySelector('input[formcontrolname="itemGroup"]')).not.toBeNull();
    expect(itemInput).toBeNull();
    expect(component.allDimensions.map(option => option.id)).toEqual(['date', 'vendor', 'branch', 'item_group']);
  });

  it('shows item filter only in price history mode', () => {
    component.setViewMode('priceHistory');
    fixture.detectChanges();

    expect(fixture.nativeElement.querySelector('input[formcontrolname="item"]')).not.toBeNull();
  });

  it('omits item filter from explorer requests', () => {
    component.filterForm.patchValue({
      vendor: 'Vendor A',
      branch: 'Branch A',
      itemGroup: 'Category A',
      item: 'ITEM-001'
    });

    const explorerRequest = (component as any).buildRequest();
    expect(explorerRequest.filters).toEqual({
      vendor: 'Vendor A',
      branch: 'Branch A',
      itemGroup: 'Category A'
    });

    component.setViewMode('priceHistory');
    component.filterForm.patchValue({ item: 'ITEM-001' });

    const historyRequest = (component as any).buildRequest();
    expect(historyRequest.filters.item).toBe('ITEM-001');
  });

  it('right-aligns analytics metric headers and cells', () => {
    component.result = {
      columns: [
        { id: 'date', label: 'Date', colType: 'DIMENSION' },
        { id: 'revenue', label: 'Revenue', colType: 'CURRENCY' },
        { id: 'orders', label: 'Orders', colType: 'NUMBER' }
      ],
      rows: [
        { date: '2026-07-04', revenue: 42599, orders: 6 },
        { date: '2026-07-05', revenue: 1000, orders: 1 }
      ],
      totalsRow: { date: 'Total', revenue: 43599, orders: 7 },
      kpis: [],
      warnings: []
    };
    fixture.detectChanges();

    const headers = fixture.nativeElement.querySelectorAll('.data-table thead th');
    const bodyCells = fixture.nativeElement.querySelectorAll('.data-table tbody tr:first-child td');
    const totalCells = fixture.nativeElement.querySelectorAll('.data-table tfoot td');

    expect(headers[0].classList).not.toContain('num-col');
    expect(headers[1].classList).toContain('num-col');
    expect(headers[1].classList).toContain('metric-col');
    expect(bodyCells[1].classList).toContain('num-col');
    expect(totalCells[1].classList).toContain('num-col');
  });

  it('keeps the latest GSTR result when an older explorer request finishes later', () => {
    const explorer$ = new Subject<any>();
    const gstr$ = new Subject<any>();
    analyticsService.query.and.returnValue(explorer$);
    analyticsService.gstr1.and.returnValue(gstr$);

    component.run();
    component.setViewMode('gstReports');

    gstr$.next({
      columns: [{ id: 'gstin', label: 'Receiver GSTIN/UIN * (Required)', colType: 'DIMENSION' }],
      rows: [{ gstin: '27ABCDE1234F1Z5' }],
      totalsRow: {},
      kpis: [],
      warnings: []
    });
    gstr$.complete();

    explorer$.next({
      columns: [{ id: 'date', label: 'Date', colType: 'DIMENSION' }],
      rows: [{ date: '2026-08-14' }],
      totalsRow: {},
      kpis: [],
      warnings: []
    });
    explorer$.complete();

    expect(component.tableColumns[0].label).toBe('Receiver GSTIN/UIN * (Required)');
    expect(component.tableRows[0]['gstin']).toBe('27ABCDE1234F1Z5');
  });
});
