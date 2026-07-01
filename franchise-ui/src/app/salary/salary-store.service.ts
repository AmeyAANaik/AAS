import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, of, throwError } from 'rxjs';
import { delay } from 'rxjs/operators';
import {
  Employee, EmployeeInput, EmployeeStatus, SalaryPayment, SalaryPaymentView
} from './salary.model';

const EMPLOYEES_KEY = 'franchise.salary.employees';
const PAYMENTS_KEY = 'franchise.salary.payments';
const LATENCY = 120;

interface SeedEmployee {
  name: string; role: string; monthlySalary: number; status: EmployeeStatus; joinedDate: string;
}

const SEED: SeedEmployee[] = [
  { name: 'Asha Patil', role: 'Store Manager', monthlySalary: 38000, status: 'Active', joinedDate: '2023-04-01' },
  { name: 'Rahul Deshmukh', role: 'Cashier', monthlySalary: 22000, status: 'Active', joinedDate: '2023-08-15' },
  { name: 'Priya Nair', role: 'Kitchen Staff', monthlySalary: 19000, status: 'Active', joinedDate: '2024-01-10' },
  { name: 'Imran Sheikh', role: 'Delivery', monthlySalary: 17000, status: 'Active', joinedDate: '2024-06-05' },
  { name: 'Sunita Rao', role: 'Helper', monthlySalary: 15000, status: 'Inactive', joinedDate: '2022-11-20' }
];

/**
 * In-browser mock salary backend. Employee master + a salary-payment ledger are
 * persisted to localStorage; every read/write returns an Observable (with a
 * small delay) so a real HTTP API can be dropped in later unchanged.
 */
@Injectable({ providedIn: 'root' })
export class SalaryStoreService {
  private employees: Employee[] = [];
  private payments: SalaryPayment[] = [];
  private readonly changed$ = new BehaviorSubject<void>(undefined);
  /** Emits whenever employees or payments change so views can refresh. */
  readonly changes$ = this.changed$.asObservable();

  constructor() {
    this.load();
  }

  private load(): void {
    const rawEmployees = this.read<Employee[]>(EMPLOYEES_KEY);
    const rawPayments = this.read<SalaryPayment[]>(PAYMENTS_KEY);
    if (rawEmployees && rawPayments) {
      this.employees = rawEmployees;
      this.payments = rawPayments;
      return;
    }
    this.seed();
  }

  private seed(): void {
    this.employees = [];
    this.payments = [];
    const today = this.today();
    SEED.forEach(s => {
      this.employees.push({
        id: this.id('emp'), name: s.name, role: s.role, monthlySalary: s.monthlySalary,
        status: s.status, joinedDate: s.joinedDate, createdAt: today
      });
    });

    const prev = this.prevMonth();
    const curr = this.currentMonth();
    // Previous month: all active employees paid. Current month: pending.
    this.employees.filter(e => e.status === 'Active').forEach(e => {
      this.payments.push({
        id: this.id('sal'), employeeId: e.id, month: prev, amount: e.monthlySalary,
        status: 'Paid', paidDate: this.lastDayOf(prev), remarks: 'Monthly salary', createdAt: today
      });
      this.payments.push({
        id: this.id('sal'), employeeId: e.id, month: curr, amount: e.monthlySalary,
        status: 'Pending', remarks: 'Monthly salary', createdAt: today
      });
    });
    this.persist();
  }

  private read<T>(key: string): T | null {
    try {
      const raw = localStorage.getItem(key);
      return raw ? (JSON.parse(raw) as T) : null;
    } catch {
      return null;
    }
  }

  private persist(): void {
    try {
      localStorage.setItem(EMPLOYEES_KEY, JSON.stringify(this.employees));
      localStorage.setItem(PAYMENTS_KEY, JSON.stringify(this.payments));
    } catch {
      /* ignore */
    }
    this.changed$.next();
  }

  private id(prefix: string): string {
    return `${prefix}_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 7)}`;
  }

  private today(): string {
    return new Date().toISOString().slice(0, 10);
  }

  private currentMonth(): string {
    return new Date().toISOString().slice(0, 7);
  }

  private prevMonth(): string {
    const d = new Date();
    d.setDate(1);
    d.setMonth(d.getMonth() - 1);
    return d.toISOString().slice(0, 7);
  }

  /** Last calendar day of a `yyyy-mm` period, as `yyyy-mm-dd`. */
  private lastDayOf(ym: string): string {
    const [y, m] = ym.split('-').map(Number);
    const d = new Date(y, m, 0); // day 0 of next month = last day of this one
    return `${ym}-${String(d.getDate()).padStart(2, '0')}`;
  }

  // ---- derived helpers -------------------------------------------------------

  private employeeName(id: string): string {
    return this.employees.find(e => e.id === id)?.name ?? id;
  }

  private toView(p: SalaryPayment): SalaryPaymentView {
    return { ...p, employeeName: this.employeeName(p.employeeId) };
  }

  // ---- employees (public API, Observable, mimics HTTP) -----------------------

  listEmployees(): Observable<Employee[]> {
    const list = this.employees.slice().sort((a, b) => a.name.localeCompare(b.name));
    return of(list).pipe(delay(LATENCY));
  }

  listEmployeesSnapshot(): Employee[] {
    return this.employees.slice();
  }

  createEmployee(input: EmployeeInput): Observable<Employee> {
    const employee: Employee = {
      id: this.id('emp'), name: input.name.trim(), role: input.role?.trim() || undefined,
      monthlySalary: Number(input.monthlySalary) || 0, status: input.status,
      joinedDate: input.joinedDate, createdAt: this.today()
    };
    this.employees.push(employee);
    this.persist();
    return of(employee).pipe(delay(LATENCY));
  }

  updateEmployee(id: string, input: EmployeeInput): Observable<Employee> {
    const employee = this.employees.find(e => e.id === id);
    if (!employee) {
      return throwError(() => new Error('Employee not found'));
    }
    employee.name = input.name.trim();
    employee.role = input.role?.trim() || undefined;
    employee.monthlySalary = Number(input.monthlySalary) || 0;
    employee.status = input.status;
    employee.joinedDate = input.joinedDate;
    this.persist();
    return of(employee).pipe(delay(LATENCY));
  }

  setEmployeeStatus(id: string, status: EmployeeStatus): Observable<Employee> {
    const employee = this.employees.find(e => e.id === id);
    if (!employee) {
      return throwError(() => new Error('Employee not found'));
    }
    employee.status = status;
    this.persist();
    return of(employee).pipe(delay(LATENCY));
  }

  // ---- payments --------------------------------------------------------------

  listPayments(): Observable<SalaryPaymentView[]> {
    const list = this.payments
      .slice()
      .sort((a, b) => b.month.localeCompare(a.month) || a.employeeId.localeCompare(b.employeeId))
      .map(p => this.toView(p));
    return of(list).pipe(delay(LATENCY));
  }

  paymentsForMonth(ym: string): Observable<SalaryPaymentView[]> {
    const list = this.payments
      .filter(p => p.month === ym)
      .map(p => this.toView(p))
      .sort((a, b) => a.employeeName.localeCompare(b.employeeName));
    return of(list).pipe(delay(LATENCY));
  }

  /**
   * Create a Pending payment for every ACTIVE employee that does not already
   * have a payment for `ym`. Returns the full set of payments for that month.
   */
  generateMonth(ym: string): Observable<SalaryPaymentView[]> {
    const created = this.today();
    this.employees
      .filter(e => e.status === 'Active')
      .filter(e => !this.payments.some(p => p.employeeId === e.id && p.month === ym))
      .forEach(e => {
        this.payments.push({
          id: this.id('sal'), employeeId: e.id, month: ym, amount: e.monthlySalary,
          status: 'Pending', remarks: 'Monthly salary', createdAt: created
        });
      });
    this.persist();
    const list = this.payments
      .filter(p => p.month === ym)
      .map(p => this.toView(p))
      .sort((a, b) => a.employeeName.localeCompare(b.employeeName));
    return of(list).pipe(delay(LATENCY));
  }

  markPaid(id: string): Observable<SalaryPaymentView> {
    const payment = this.payments.find(p => p.id === id);
    if (!payment) {
      return throwError(() => new Error('Payment not found'));
    }
    payment.status = 'Paid';
    payment.paidDate = this.today();
    this.persist();
    return of(this.toView(payment)).pipe(delay(LATENCY));
  }

  removePayment(id: string): Observable<void> {
    this.payments = this.payments.filter(p => p.id !== id);
    this.persist();
    return of(undefined).pipe(delay(LATENCY));
  }

  /** Sum of payment amounts for a month (all statuses) — used by the P&L module. */
  totalForMonth(ym: string): number {
    const total = this.payments
      .filter(p => p.month === ym)
      .reduce((sum, p) => sum + p.amount, 0);
    return Math.round(total * 100) / 100;
  }

  resetDemoData(): Observable<void> {
    this.seed();
    return of(undefined).pipe(delay(LATENCY));
  }
}
