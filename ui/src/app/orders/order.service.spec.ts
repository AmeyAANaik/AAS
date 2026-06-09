import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthTokenService } from '../shared/auth-token.service';
import { OrderService } from './order.service';

describe('OrderService', () => {
  let service: OrderService;
  let httpMock: HttpTestingController;
  let tokenStore: jasmine.SpyObj<AuthTokenService>;

  beforeEach(() => {
    tokenStore = jasmine.createSpyObj('AuthTokenService', ['getToken']);
    tokenStore.getToken.and.returnValue('jwt-token');

    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [
        OrderService,
        { provide: AuthTokenService, useValue: tokenStore }
      ]
    });

    service = TestBed.inject(OrderService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('downloads ERP private file URLs through the authenticated proxy', () => {
    service.downloadFile('https://erp.example/private/files/branch_order.jpeg?download=1').subscribe();

    const request = httpMock.expectOne('/api/private/files/branch_order.jpeg?download=1');
    expect(request.request.method).toBe('GET');
    expect(request.request.headers.get('Authorization')).toBe('Bearer jwt-token');
    expect(request.request.responseType).toBe('blob');
    request.flush(new Blob(['image'], { type: 'image/jpeg' }));
  });
});
