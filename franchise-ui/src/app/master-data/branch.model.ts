export type BranchStatus = 'Active' | 'Setup Pending' | 'Inactive';

export interface BranchRecord {
  id: string;
  name: string;
  code: string;
  fssaiNumber: string;
  gstNumber: string;
  tanNumber: string;
  city: string;
  area: string;
  address: string;
  managerName: string;
  phone: string;
  email: string;
  contactNumber: string;
  accountHolderName: string;
  bankName: string;
  bankAccountNumber: string;
  ifscCode: string;
  openingDate: string;
  status: BranchStatus;
}

export interface BranchInput {
  name: string;
  code: string;
  fssaiNumber: string;
  gstNumber: string;
  tanNumber: string;
  city: string;
  area: string;
  address: string;
  managerName: string;
  phone: string;
  email: string;
  contactNumber: string;
  accountHolderName: string;
  bankName: string;
  bankAccountNumber: string;
  ifscCode: string;
  openingDate: string;
  status: BranchStatus;
}
