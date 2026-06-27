export type VendorStatus = 'Active' | 'Inactive';

export interface Vendor {
  id: string;
  name: string;
  code: string;
  category: string;
  phone: string;
  status: VendorStatus;
  totalPurchased: number;
  totalPaid: number;
}

export interface VendorView extends Vendor {
  outstanding: number;
}
