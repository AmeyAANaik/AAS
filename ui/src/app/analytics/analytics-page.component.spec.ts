import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { BranchService } from '../branches/branch.service';
import { CategoryService } from '../categories/category.service';
import { ItemService } from '../items/item.service';
import { VendorService } from '../vendors/vendor.service';
import { AnalyticsService } from './analytics.service';
import { AnalyticsPageComponent } from './analytics-page.component';

describe('AnalyticsPageComponent', () => {
  let fixture: ComponentFixture<AnalyticsPageComponent>;
  let component: AnalyticsPageComponent;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AnalyticsPageComponent, NoopAnimationsModule],
      providers: [
        {
          provide: AnalyticsService,
          useValue: {
            query: () => of({ columns: [], rows: [], totalsRow: {}, kpis: [] }),
            itemPriceHistory: () => of({ columns: [], rows: [], totalsRow: {}, kpis: [] }),
            export: () => of(new Blob()),
            exportItemPriceHistory: () => of(new Blob())
          }
        },
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
});
