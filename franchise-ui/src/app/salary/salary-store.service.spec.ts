import { TestBed } from '@angular/core/testing';
import { SalaryStoreService } from './salary-store.service';
import { EmployeeInput } from './salary.model';

const EMPLOYEES_KEY = 'franchise.salary.employees';
const PAYMENTS_KEY = 'franchise.salary.payments';
const YM = '2026-06';

function emp(partial: Partial<EmployeeInput>): EmployeeInput {
  return {
    name: 'Test',
    role: 'Staff',
    monthlySalary: 10000,
    status: 'Active',
    joinedDate: '2024-01-01',
    ...partial,
  };
}

describe('SalaryStoreService', () => {
  let service: SalaryStoreService;

  beforeEach((done) => {
    localStorage.clear();
    // Pre-seed empty arrays so load() skips demo seeding (needs BOTH keys present).
    localStorage.setItem(EMPLOYEES_KEY, JSON.stringify([]));
    localStorage.setItem(PAYMENTS_KEY, JSON.stringify([]));
    TestBed.configureTestingModule({});
    service = TestBed.inject(SalaryStoreService);

    const seeds: EmployeeInput[] = [
      emp({ name: 'Active One', monthlySalary: 20000, status: 'Active' }),
      emp({ name: 'Active Two', monthlySalary: 30000, status: 'Active' }),
      emp({ name: 'Inactive One', monthlySalary: 15000, status: 'Inactive' }),
    ];
    let i = 0;
    const next = () => {
      if (i >= seeds.length) {
        done();
        return;
      }
      service.createEmployee(seeds[i++]).subscribe(next);
    };
    next();
  });

  it('generateMonth creates one Pending payment per ACTIVE employee (not Inactive)', (done) => {
    service.generateMonth(YM).subscribe((list) => {
      expect(list.length).toBe(2);
      expect(list.every((p) => p.status === 'Pending')).toBeTrue();
      expect(list.find((p) => p.employeeName === 'Inactive One')).toBeUndefined();
      done();
    });
  });

  it('generateMonth is idempotent (no duplicates on re-run)', (done) => {
    service.generateMonth(YM).subscribe(() => {
      service.generateMonth(YM).subscribe((list) => {
        expect(list.length).toBe(2);
        done();
      });
    });
  });

  it('markPaid flips status to Paid and sets paidDate', (done) => {
    service.generateMonth(YM).subscribe((list) => {
      const target = list[0];
      service.markPaid(target.id).subscribe((paid) => {
        expect(paid.status).toBe('Paid');
        expect(paid.paidDate).toBeTruthy();
        done();
      });
    });
  });

  it('totalForMonth sums all payments for the month', (done) => {
    service.generateMonth(YM).subscribe(() => {
      // 20000 + 30000 = 50000
      expect(service.totalForMonth(YM)).toBe(50000);
      done();
    });
  });
});
