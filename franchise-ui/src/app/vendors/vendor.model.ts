export type VendorStatus = 'Active' | 'Inactive';
export type SupplierType = 'Company' | 'Individual';

/**
 * One mapped column/field in a vendor's invoice format.
 * `targetField` is the AAS field we need; `sourceLabel` is the column/label
 * detected on the vendor's invoice; `matched` is whether the analyzer found it.
 */
export interface VendorFieldMapping {
  targetField: string;
  sourceLabel: string;
  required: boolean;
  matched: boolean;
}

/**
 * A vendor's invoice format (a.k.a. invoice extraction template).
 * Mirrors the production `ui/` flow: a sample invoice is analyzed into a field
 * mapping, then validated & saved before the vendor can be activated.
 */
export interface VendorInvoiceTemplate {
  configured: boolean;
  validated: boolean;
  engineLabel: string;
  sampleFileName: string;
  itemMappings: VendorFieldMapping[];
  summaryMappings: VendorFieldMapping[];
  itemsDetected: number;
  totalRows: number;
  billAmount: number;
  validatedAt?: string;
}

export interface Vendor {
  id: string;

  // --- Details (ERPNext Supplier core) ---
  name: string;            // supplier_name
  code: string;            // vendor_code / naming
  supplierType: SupplierType;
  category: string;        // supplier_group

  // --- Tax & compliance ---
  gstin: string;           // tax_id (GSTIN)
  pan: string;
  gstCategory: string;
  foodLicenseNo: string;   // AAS-specific (FSSAI)

  // --- Address & contact ---
  addressLine1: string;
  addressLine2: string;
  city: string;
  state: string;
  country: string;
  pincode: string;
  email: string;
  phone: string;

  // --- Buying defaults ---
  defaultCurrency: string;
  paymentTerms: string;

  // --- Operations ---
  priority: number | null;
  status: VendorStatus;
  totalPurchased: number;
  totalPaid: number;

  // --- Invoice format ---
  invoiceTemplate: VendorInvoiceTemplate | null;
}

export interface VendorView extends Vendor {
  outstanding: number;
  invoiceConfigured: boolean;
  invoiceValidated: boolean;
}

export const GST_CATEGORIES = [
  'Registered Regular',
  'Registered Composition',
  'Unregistered',
  'SEZ',
  'Overseas'
];

export const PAYMENT_TERMS = [
  'Net 0 (Immediate)',
  'Net 15',
  'Net 30',
  'Net 45',
  'Net 60'
];

/** Required AAS fields an invoice format must map for activation. */
export const REQUIRED_ITEM_FIELDS = ['Item name', 'Quantity', 'Rate', 'Amount'];
export const REQUIRED_SUMMARY_FIELDS = ['Invoice number', 'Invoice date', 'Bill amount'];

export function emptyInvoiceTemplate(): VendorInvoiceTemplate {
  return {
    configured: false,
    validated: false,
    engineLabel: '',
    sampleFileName: '',
    itemMappings: [],
    summaryMappings: [],
    itemsDetected: 0,
    totalRows: 0,
    billAmount: 0
  };
}
