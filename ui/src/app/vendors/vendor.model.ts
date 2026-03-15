export type VendorStatus = 'Active' | 'Inactive';

export interface Vendor {
  name?: string;
  supplier_name?: string;
  vendor_code?: string;
  disabled?: number | boolean;
  aas_priority?: number;
  category?: string;
  address?: string;
  phone?: string;
  gst?: string;
  pan?: string;
  food_license_no?: string;
  invoice_template_key?: string;
  invoice_template_json?: string;
  invoice_template_sample_pdf?: string;
}

export interface VendorFormValue {
  supplierName: string;
  vendorCode: string;
  category?: string;
  address?: string;
  phone?: string;
  gst?: string;
  pan?: string;
  foodLicenseNo?: string;
  priority: number | null;
  status: VendorStatus;
  invoiceTemplateJson: string;
}

export interface VendorTemplateValidation {
  configured: boolean;
  used: boolean;
  parserSource: string;
  detectedItems: number;
  requiredColumns: string[];
  parsedColumns: string[];
  missingColumns: string[];
  requiredSummaryFields: string[];
  parsedSummaryFields: string[];
  missingSummaryFields: string[];
  finalBillAmount: string;
  activationReady: boolean;
  ocrLineCount: number;
  previewItems: Array<Record<string, unknown>>;
}

export interface VendorView {
  id: string;
  name: string;
  vendorCode: string;
  category: string;
  priority: number | null;
  status: VendorStatus;
  templateKey: string;
  templateHasJson: boolean;
  raw: Vendor;
}
