import { NO_ERRORS_SCHEMA } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { CategoryService } from '../categories/category.service';
import { UomService } from '../shared/uom.service';
import { MasterDataReviewPageComponent } from './master-data-review-page.component';
import { MasterDataReviewService } from './master-data-review.service';

describe('MasterDataReviewPageComponent', () => {
  let fixture: ComponentFixture<MasterDataReviewPageComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MasterDataReviewPageComponent],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
        {
          provide: MasterDataReviewService,
          useValue: {
            listItems: () => of([
              {
                id: 'ITEM-1',
                itemCode: 'ITEM-1',
                itemName: 'Item One',
                category: 'Grocery',
                uom: 'Nos',
                packagingUnit: '1 pack',
                marginPercent: 7,
                vendorId: 'VENDOR-1',
                vendorHsnCode: 'HSN-1',
                gstPercent: 5,
                reviewStatus: 'PENDING_REVIEW',
                sourceOrderId: 'SO-1',
                sourceInvoiceRef: 'invoice.pdf',
                createdAt: '2026-04-05 10:00:00',
                createdBy: 'admin@example.com',
                reviewNotes: '',
                defaultMarginUsed: true
              }
            ]),
            getDetail: () => of({
              id: 'ITEM-1',
              itemCode: 'ITEM-1',
              itemName: 'Item One',
              category: 'Grocery',
              uom: 'Nos',
              packagingUnit: '1 pack',
              marginPercent: 7,
              vendorId: 'VENDOR-1',
              vendorHsnCode: 'HSN-1',
              gstPercent: 5,
              reviewStatus: 'PENDING_REVIEW',
              sourceOrderId: 'SO-1',
              sourceInvoiceRef: 'invoice.pdf',
              createdAt: '2026-04-05 10:00:00',
              createdBy: 'admin@example.com',
              reviewNotes: '',
              defaultMarginUsed: true
            }),
            approve: () => of({ detail: { id: 'ITEM-1', sourceOrderId: 'SO-1' } }),
            refresh$: of()
          }
        },
        {
          provide: CategoryService,
          useValue: {
            listCategories: () => of([{ name: 'Grocery' }])
          }
        },
        {
          provide: UomService,
          useValue: {
            listUoms: () => of([{ name: 'Litre', uom_name: 'Litre' }])
          }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(MasterDataReviewPageComponent);
    fixture.detectChanges();
  });

  it('renders the review queue and summary', () => {
    const element = fixture.nativeElement as HTMLElement;
    const text = element.textContent ?? '';
    expect(text).toContain('Master Data Review');
    expect(text).toContain('Pending approval');
    expect(text).toContain('Item One');
    expect(text).toContain('Default margin used');
    expect(text).toContain('Admin notification');
    expect(text).toContain('need review');
    const searchInput = element.querySelector('.search-shell input') as HTMLInputElement | null;
    expect(searchInput?.placeholder).toContain('Search item, code, order, invoice, category, or HSN');
    const uomDatalist = element.querySelector('#review-uom-options') as HTMLDataListElement | null;
    expect(uomDatalist).toBeTruthy();
  });
});
