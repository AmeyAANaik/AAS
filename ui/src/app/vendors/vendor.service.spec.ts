import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthTokenService } from '../shared/auth-token.service';
import { VendorService } from './vendor.service';

describe('VendorService', () => {
  let service: VendorService;
  let httpMock: HttpTestingController;
  let tokenStore: AuthTokenService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [VendorService, AuthTokenService]
    });
    service = TestBed.inject(VendorService);
    httpMock = TestBed.inject(HttpTestingController);
    tokenStore = TestBed.inject(AuthTokenService);
  });

  afterEach(() => {
    httpMock.verify();
    tokenStore.setToken(null);
  });

  it('invokes listVendors with auth header', () => {
    tokenStore.setToken('test-token');

    service.listVendors().subscribe();

    const req = httpMock.expectOne('/api/vendors');
    expect(req.request.method).toBe('GET');
    expect(req.request.headers.get('Authorization')).toBe('Bearer test-token');
    req.flush([]);
  });
});
