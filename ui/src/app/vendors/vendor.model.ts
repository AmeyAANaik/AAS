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
  invoice_template_enabled?: number | boolean;
  invoice_template_key?: string;
  invoice_template_json?: string;
  invoice_template_sample_pdf?: string;
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
  invoiceTemplateEnabled: boolean;
  invoiceTemplateJson: string;
}

export interface VendorView {
  id: string;
  name: string;
  priority: number | null;
  status: VendorStatus;
  templateEnabled: boolean;
  templateKey: string;
  templateHasJson: boolean;
  raw: Vendor;
}
