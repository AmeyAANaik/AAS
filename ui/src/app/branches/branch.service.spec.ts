import { HttpClientTestingModule, HttpTestingController } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { AuthTokenService } from '../shared/auth-token.service';
import { BranchService } from './branch.service';

describe('BranchService', () => {
  let service: BranchService;
  let httpMock: HttpTestingController;
  let tokenStore: AuthTokenService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [BranchService, AuthTokenService]
    });
    service = TestBed.inject(BranchService);
    httpMock = TestBed.inject(HttpTestingController);
    tokenStore = TestBed.inject(AuthTokenService);
  });

  afterEach(() => {
    httpMock.verify();
    tokenStore.setToken(null);
  });

  it('invokes listBranches with auth header', () => {
    tokenStore.setToken('test-token');

    service.listBranches().subscribe();

    const req = httpMock.expectOne('/api/shops');
    expect(req.request.method).toBe('GET');
    expect(req.request.headers.get('Authorization')).toBe('Bearer test-token');
    req.flush([]);
  });
});
