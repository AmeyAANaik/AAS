export type VendorStatus = 'Active' | 'Inactive';

export interface Vendor {
  name?: string;
  supplier_name?: string;
  disabled?: number | boolean;
  aas_priority?: number;
  address?: string;
  phone?: string;
  gst?: string;
  pan?: string;
  food_license_no?: string;
}

export interface VendorFormValue {
  supplierName: string;
  address?: string;
  phone?: string;
  gst?: string;
  pan?: string;
  foodLicenseNo?: string;
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
