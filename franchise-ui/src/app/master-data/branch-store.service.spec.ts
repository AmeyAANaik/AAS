import { TestBed } from '@angular/core/testing';
import { BranchStoreService } from './branch-store.service';
import { BranchInput } from './branch.model';

const STORAGE_KEY = 'franchise.branches.v1';

function input(partial: Partial<BranchInput> = {}): BranchInput {
  return {
    name: 'Sukhkarta — Kothrud',
    code: 'kothrud',
    fssaiNumber: '11526000000009',
    city: 'Pune',
    area: 'Kothrud',
    address: 'Kothrud, Pune',
    managerName: 'Demo Manager',
    phone: '9876543299',
    email: 'kothrud@sukhkarta.app',
    contactNumber: '020-40000009',
    accountHolderName: 'Sukhkarta Kothrud',
    bankName: 'ICICI Bank',
    bankAccountNumber: '123456789012',
    ifscCode: 'icic0000123',
    openingDate: '2026-07-07',
    status: 'Setup Pending',
    ...partial
  };
}

describe('BranchStoreService', () => {
  let service: BranchStoreService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({});
    service = TestBed.inject(BranchStoreService);
  });

  it('seeds the default franchise branches', () => {
    expect(service.branchNamesSnapshot()).toEqual([
      'Sukhkarta — Aundh',
      'Sukhkarta — Baner',
      'Sukhkarta — Wakad'
    ]);
  });

  it('creates a branch and exposes it to the selector list', (done) => {
    service.create(input()).subscribe(branch => {
      expect(branch.name).toBe('Sukhkarta — Kothrud');
      expect(branch.code).toBe('KOTHRUD');
      expect(branch.email).toBe('kothrud@sukhkarta.app');
      expect(branch.ifscCode).toBe('ICIC0000123');
      expect(service.branchNamesSnapshot()).toContain('Sukhkarta — Kothrud');

      const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '[]');
      expect(saved.some((row: { name: string }) => row.name === 'Sukhkarta — Kothrud')).toBeTrue();
      done();
    });
  });

  it('backfills new compliance and bank fields for older saved branches', () => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify([{
      id: 'branch-legacy',
      name: 'Legacy Branch',
      code: 'LEGACY',
      city: 'Pune',
      area: 'Camp',
      address: 'Camp, Pune',
      managerName: 'Legacy Manager',
      phone: '9876543200',
      openingDate: '2026-01-01',
      status: 'Active'
    }]));

    const fresh = new BranchStoreService();
    const legacy = fresh.listSnapshot().find(branch => branch.id === 'branch-legacy');

    expect(legacy?.fssaiNumber).toBe('');
    expect(legacy?.email).toBe('');
    expect(legacy?.contactNumber).toBe('9876543200');
    expect(legacy?.accountHolderName).toBe('Legacy Branch');
  });
});
