export type VendorStatus = 'Active' | 'Inactive';

export interface Vendor {
  name?: string;
  supplier_name?: string;
  disabled?: number | boolean;
  aas_priority?: number;
}

export interface VendorFormValue {
  supplierName: string;
  priority: number | null;
  status: VendorStatus;
}

export interface VendorView {
  id: string;
  name: string;
  priority: number | null;
  status: VendorStatus;
  raw: Vendor;
}
