export type EmployeeStatus = 'Active' | 'Inactive';
export type SalaryStatus = 'Paid' | 'Pending';

export interface Employee {
  id: string;
  name: string;
  role?: string;
  monthlySalary: number;
  status: EmployeeStatus;
  joinedDate: string; // yyyy-mm-dd
  createdAt: string;
}

export interface EmployeeInput {
  name: string;
  role?: string;
  monthlySalary: number;
  status: EmployeeStatus;
  joinedDate: string;
}

export interface SalaryPayment {
  id: string;
  employeeId: string;
  month: string; // yyyy-mm
  amount: number;
  status: SalaryStatus;
  paidDate?: string;
  remarks?: string;
  createdAt: string;
}

export interface SalaryPaymentView extends SalaryPayment {
  employeeName: string;
}
