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
  finalBillMatch?: string;
  transportChargeAmount?: string;
  transportChargeMatch?: string;
  activationReady: boolean;
  ocrLineCount: number;
  previewItems: Array<Record<string, unknown>>;
}

export interface VendorInvoiceFieldMapping {
  targetField: string;
  sourceLabel: string;
}

export interface VendorInvoiceTemplateMappingConfig {
  itemMappings: VendorInvoiceFieldMapping[];
  summaryMappings: VendorInvoiceFieldMapping[];
}

export interface VendorInvoiceTemplateMappingPreview {
  vendorId: string;
  ocrLineCount: number;
  ocrPreview: string[];
  previewMetrics: {
    itemsDetected: number;
    itemFieldsMatched: number;
    totalRows: number;
    billAmount: string;
    transportCharge: string;
  };
  requiredFields: {
    items: string[];
    summary: string[];
  };
  parsedFields?: {
    items: string[];
    summary: string[];
  };
  mappedFields: {
    items: string[];
    summary: string[];
  };
  missingFields: {
    items: string[];
    summary: string[];
  };
  mapping: VendorInvoiceTemplateMappingConfig;
  mappingChecks: {
    items: Array<{ targetField: string; sourceLabel: string; matched: boolean; matchedLine: string }>;
    summary: Array<{ targetField: string; sourceLabel: string; matched: boolean; matchedLine: string }>;
  };
  previewItems?: Array<Record<string, unknown>>;
  mappingReady: boolean;
  nextStep: string;
}

export interface VendorInvoiceDetectedColumn {
  id?: string;
  value: string;
  label: string;
  targetField: string;
  required: boolean;
}

export interface VendorInvoiceTemplateAnalysis {
  vendorId: string;
  sample: {
    fileName?: string;
    fileUrl?: string;
  };
  ocrLineCount: number;
  ocrPreview: string[];
  previewMetrics: {
    itemsDetected: number;
    totalRows: number;
    billAmount: string;
    transportCharge: string;
  };
  requiredFields: {
    items: string[];
    summary: string[];
  };
  detectedColumns: {
    items: VendorInvoiceDetectedColumn[];
    summary: VendorInvoiceDetectedColumn[];
  };
  savedMapping: VendorInvoiceTemplateMappingConfig;
  suggestedMapping: VendorInvoiceTemplateMappingConfig;
  nextStep: string;
}

export interface VendorInvoiceTemplateProfile {
  id: string;
  label: string;
  vendorName: string;
  description: string;
  inputReader: string;
  supportedItemFields: string[];
  supportedSummaryFields: string[];
}

export interface VendorInvoiceTemplateProfilePreview {
  vendorId: string;
  engineType: string;
  generation?: {
    method: string;
    model: string;
    summary: string;
  };
  templateProfile: {
    id: string;
    label: string;
    vendorName: string;
    description: string;
    supportedItemFields: string[];
    supportedSummaryFields: string[];
  };
  generatedTemplate?: {
    id: string;
    label: string;
    vendorName: string;
    description: string;
    inputReader: string;
    yaml: string;
    generatorType: string;
    generatorModel: string;
  };
  sample: {
    fileName?: string;
    fileUrl?: string;
  };
  ocrLineCount: number;
  ocrPreview: string[];
  parserLineCount?: number;
  parserPreview?: string[];
  previewMetrics: {
    itemsDetected: number;
    itemFieldsMatched: number;
    totalRows: number;
    billAmount: string;
    transportCharge: string;
    invoiceNumber?: string;
    invoiceDate?: string;
    expectedItems?: number;
    itemCountComplete?: boolean;
  };
  fieldMapping?: {
    itemMappings: Array<{
      targetField: string;
      sourceLabel: string;
      required: boolean;
      present: boolean;
      confidence: string;
    }>;
    summaryMappings: Array<{
      targetField: string;
      sourceLabel: string;
      required: boolean;
      present: boolean;
      confidence: string;
    }>;
    notes?: string;
    generatorType?: string;
    generatorModel?: string;
  };
  requiredFields: {
    items: string[];
    summary: string[];
  };
  parsedFields: {
    items: string[];
    summary: string[];
  };
  missingFields: {
    items: string[];
    summary: string[];
  };
  previewItems: Array<Record<string, unknown>>;
  completeness?: {
    expectedItemCount: number;
    extractedItemCount: number;
    itemCountComplete: boolean;
    expectedSerials: number[];
    extractedSerials?: number[];
    missingSerials?: number[];
    missingSerialContexts?: Array<{
      serial: number;
      parserContext: string[];
      camelotContext: string[];
    }>;
  };
  profileReady: boolean;
  saveAllowed?: boolean;
  nextStep: string;
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

export interface SavedVendorInvoiceTemplateProfile {
  id: string;
  label: string;
  engineType: string;
  validation?: {
    itemsDetected?: number;
    totalRows?: number;
    billAmount?: string | number;
    transportCharge?: string | number;
    sampleFileName?: string;
    validatedAt?: string;
  };
}

export function parseVendorTemplateMapping(vendor: Vendor | null | undefined): VendorInvoiceTemplateMappingConfig | null {
  const templateJson = String(vendor?.invoice_template_json ?? '').trim();
  if (!templateJson) {
    return null;
  }
  try {
    const parsed = JSON.parse(templateJson) as {
      mapping?: {
        itemMappings?: VendorInvoiceFieldMapping[];
        summaryMappings?: VendorInvoiceFieldMapping[];
      };
    };
    const itemMappings = Array.isArray(parsed.mapping?.itemMappings)
      ? parsed.mapping.itemMappings.filter(isValidMapping)
      : [];
    const summaryMappings = Array.isArray(parsed.mapping?.summaryMappings)
      ? parsed.mapping.summaryMappings.filter(isValidMapping)
      : [];
    if (!itemMappings.length && !summaryMappings.length) {
      return null;
    }
    return { itemMappings, summaryMappings };
  } catch {
    return null;
  }
}

export function parseVendorTemplateSampleFileName(vendor: Vendor | null | undefined): string {
  const templateJson = String(vendor?.invoice_template_json ?? '').trim();
  if (!templateJson) {
    return '';
  }
  try {
    const parsed = JSON.parse(templateJson) as {
      sample?: { fileName?: string };
      validation?: { sampleFileName?: string };
    };
    return String(parsed.sample?.fileName ?? parsed.validation?.sampleFileName ?? '').trim();
  } catch {
    return '';
  }
}

function isValidMapping(value: unknown): value is VendorInvoiceFieldMapping {
  if (!value || typeof value !== 'object') {
    return false;
  }
  const record = value as Record<string, unknown>;
  return typeof record['targetField'] === 'string' && typeof record['sourceLabel'] === 'string';
}

export function parseVendorTemplateProfile(vendor: Vendor | null | undefined): SavedVendorInvoiceTemplateProfile | null {
  const templateJson = String(vendor?.invoice_template_json ?? '').trim();
  if (!templateJson) {
    return null;
  }
  try {
    const parsed = JSON.parse(templateJson) as {
      kind?: string;
      engine?: { type?: string };
      profile?: { id?: string; label?: string };
      validation?: {
        itemsDetected?: number;
        totalRows?: number;
        billAmount?: string | number;
        transportCharge?: string | number;
        sampleFileName?: string;
        validatedAt?: string;
      };
    };
    if (String(parsed.kind ?? '').trim() !== 'native_layout_mapping' || !parsed.profile?.id) {
      return null;
    }
    return {
      id: String(parsed.profile.id ?? '').trim(),
      label: String(parsed.profile.label ?? parsed.profile.id ?? '').trim(),
      engineType: String(parsed.engine?.type ?? '').trim(),
      validation: parsed.validation
    };
  } catch {
    return null;
  }
}

export function countMappedRequiredFields(
  config: VendorInvoiceTemplateMappingConfig | null | undefined,
  requiredItems: string[],
  requiredSummary: string[]
): number {
  if (!config) {
    return 0;
  }
  const requiredItemKeys = new Set(requiredItems);
  const requiredSummaryKeys = new Set(requiredSummary);
  const itemCount = (config.itemMappings ?? [])
    .filter(mapping => requiredItemKeys.has(mapping.targetField) && mapping.sourceLabel.trim())
    .map(mapping => mapping.targetField)
    .filter((value, index, array) => array.indexOf(value) === index)
    .length;
  const summaryCount = (config.summaryMappings ?? [])
    .filter(mapping => requiredSummaryKeys.has(mapping.targetField) && mapping.sourceLabel.trim())
    .map(mapping => mapping.targetField)
    .filter((value, index, array) => array.indexOf(value) === index)
    .length;
  return itemCount + summaryCount;
}
