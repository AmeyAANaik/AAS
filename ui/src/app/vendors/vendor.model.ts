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
  priority: number | null;
  status: VendorStatus;
  templateKey: string;
  templateHasJson: boolean;
  raw: Vendor;
}
