import { TestBed } from '@angular/core/testing';
import { ExpenseCategoryStoreService } from './expense-category-store.service';

const STORAGE_KEY = 'franchise.expense-categories.v1';

describe('ExpenseCategoryStoreService', () => {
  let service: ExpenseCategoryStoreService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({});
    service = TestBed.inject(ExpenseCategoryStoreService);
  });

  it('seeds default categories with P&L buckets', () => {
    const categories = service.listSnapshot();
    expect(categories.map(c => c.name)).toContain('Rent');
    expect(categories.map(c => c.name)).toContain('Electricity');
    expect(service.bucketFor('Rent')).toBe('rent');
    expect(service.bucketFor('Electricity')).toBe('electricity');
    expect(service.bucketFor('Misc')).toBe('other');
  });

  it('creates a custom category and persists it', (done) => {
    service.create({ name: 'Repairs', code: 'repairs', status: 'Active', pnlBucket: 'other' }).subscribe(category => {
      expect(category.code).toBe('REPAIRS');
      expect(service.activeSnapshot().map(c => c.name)).toContain('Repairs');

      const saved = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '[]');
      expect(saved.some((row: { name: string }) => row.name === 'Repairs')).toBeTrue();
      done();
    });
  });
});
