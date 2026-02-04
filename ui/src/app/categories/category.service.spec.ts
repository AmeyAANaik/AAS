import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthTokenService } from '../shared/auth-token.service';
import { CategoryService } from './category.service';

describe('CategoryService', () => {
  let service: CategoryService;
  let httpMock: HttpTestingController;
  let tokenStore: AuthTokenService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [CategoryService, AuthTokenService]
    });
    service = TestBed.inject(CategoryService);
    httpMock = TestBed.inject(HttpTestingController);
    tokenStore = TestBed.inject(AuthTokenService);
  });

  afterEach(() => {
    httpMock.verify();
    tokenStore.setToken(null);
  });

  it('invokes listCategories with auth header', () => {
    tokenStore.setToken('test-token');

    service.listCategories().subscribe();

    const req = httpMock.expectOne('/api/categories');
    expect(req.request.method).toBe('GET');
    expect(req.request.headers.get('Authorization')).toBe('Bearer test-token');
    req.flush([]);
  });
});
