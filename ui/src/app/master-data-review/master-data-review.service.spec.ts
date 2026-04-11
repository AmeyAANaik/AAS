import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthTokenService } from '../shared/auth-token.service';
import { MasterDataReviewService } from './master-data-review.service';

describe('MasterDataReviewService', () => {
  let service: MasterDataReviewService;
  let httpMock: HttpTestingController;
  let tokenStore: AuthTokenService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [MasterDataReviewService, AuthTokenService]
    });

    service = TestBed.inject(MasterDataReviewService);
    httpMock = TestBed.inject(HttpTestingController);
    tokenStore = TestBed.inject(AuthTokenService);
    tokenStore.setToken('test-token');
  });

  afterEach(() => {
    httpMock.verify();
    tokenStore.setToken(null);
  });

  it('requests pending count with auth header', () => {
    service.getPendingCount().subscribe();

    const req = httpMock.expectOne('/api/master-data-review/count');
    expect(req.request.method).toBe('GET');
    expect(req.request.headers.get('Authorization')).toBe('Bearer test-token');
    req.flush({ pendingCount: 3 });
  });

  it('emits refresh event after approval', () => {
    let refreshed = 0;
    service.refresh$.subscribe(() => refreshed++);

    service.approve('ITEM-1', {
      item_name: 'Item 1',
      item_group: 'Grocery',
      stock_uom: 'Nos',
      aas_packaging_unit: '1 pack',
      aas_margin_percent: 7,
      aas_vendor_hsn_code: 'HSN-1',
      aas_gst_percent: 5,
      reviewNotes: 'Approved',
      applyToSourceOrder: true
    }).subscribe();

    const req = httpMock.expectOne('/api/master-data-review/items/ITEM-1/approve');
    expect(req.request.method).toBe('PUT');
    req.flush({ detail: { id: 'ITEM-1' }, pendingCount: 2 });

    expect(refreshed).toBe(1);
  });
});
