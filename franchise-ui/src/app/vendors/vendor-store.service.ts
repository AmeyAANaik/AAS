import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { delay } from 'rxjs/operators';
import {
  REQUIRED_ITEM_FIELDS,
  REQUIRED_SUMMARY_FIELDS,
  Vendor,
  VendorFieldMapping,
  VendorInvoiceTemplate,
  VendorView,
  emptyInvoiceTemplate
} from './vendor.model';

const KEY = 'franchise.vendors.v3';
const LATENCY = 120;
const ANALYZE_LATENCY = 700;

function seedVendor(partial: Partial<Vendor> & Pick<Vendor, 'id' | 'name' | 'code' | 'category' | 'phone'>): Vendor {
  return {
    supplierType: 'Company',
    gstin: '',
    pan: '',
    gstCategory: 'Registered Regular',
    foodLicenseNo: '',
    addressLine1: '',
    addressLine2: '',
    city: '',
    state: 'Maharashtra',
    country: 'India',
    pincode: '',
    email: '',
    defaultCurrency: 'INR',
    paymentTerms: 'Net 30',
    priority: 5,
    status: 'Active',
    totalPurchased: 0,
    totalPaid: 0,
    invoiceTemplate: null,
    ...partial
  };
}

// Vendor categories are aligned to the product taxonomy so a chosen category
// resolves to its supplying vendors (Raw Material / Dairy / Utility / Vegetable).
const SEED: Vendor[] = [
  seedVendor({
    id: 'v1', name: 'Corner Kirana', code: 'VEN-001', category: 'Raw Material', phone: '98200 11111',
    gstin: '27ABCDE1234F1Z5', pan: 'ABCDE1234F', addressLine1: '12 Market Road', city: 'Mumbai', pincode: '400001',
    email: 'orders@cornerkirana.in', totalPurchased: 84500, totalPaid: 60000,
    invoiceTemplate: validatedTemplate('corner-kirana-invoice.pdf', 14, 86040)
  }),
  seedVendor({
    id: 'v2', name: 'Sharma Dairy', code: 'VEN-002', category: 'Dairy', phone: '98200 22222',
    gstin: '27FGHIJ5678K1Z3', pan: 'FGHIJ5678K', addressLine1: '5 Milk Colony', city: 'Pune', pincode: '411001',
    totalPurchased: 52300, totalPaid: 52300,
    invoiceTemplate: validatedTemplate('sharma-dairy-bill.pdf', 6, 52300)
  }),
  seedVendor({
    id: 'v3', name: 'Bharat Gas Agency', code: 'VEN-003', category: 'Utility', phone: '98200 33333',
    addressLine1: 'Plot 9 Industrial Estate', city: 'Thane', pincode: '400604',
    status: 'Inactive', totalPurchased: 33000, totalPaid: 22000
  }),
  seedVendor({
    id: 'v4', name: 'Fresh Veggies Mandi', code: 'VEN-004', category: 'Vegetable', phone: '98200 44444',
    gstCategory: 'Unregistered', addressLine1: 'APMC Yard, Sector 19', city: 'Navi Mumbai', pincode: '400705',
    status: 'Inactive', totalPurchased: 18750, totalPaid: 15000
  }),
  seedVendor({
    id: 'v5', name: 'Apex Wholesale', code: 'VEN-005', category: 'Raw Material', phone: '98200 55555',
    gstin: '27KLMNO9012P1Z1', pan: 'KLMNO9012P', addressLine1: '88 Trade Centre', city: 'Mumbai', pincode: '400013',
    totalPurchased: 41200, totalPaid: 30000,
    invoiceTemplate: validatedTemplate('apex-wholesale-invoice.pdf', 22, 41980)
  })
];

function validatedTemplate(sampleFileName: string, itemsDetected: number, billAmount: number): VendorInvoiceTemplate {
  const built = buildMapping(true);
  return {
    configured: true,
    validated: true,
    engineLabel: 'Native layout mapping',
    sampleFileName,
    itemMappings: built.itemMappings,
    summaryMappings: built.summaryMappings,
    itemsDetected,
    totalRows: itemsDetected + 2,
    billAmount,
    validatedAt: new Date(Date.now() - 1000 * 60 * 60 * 24 * 30).toISOString()
  };
}

/** Build a field mapping. When `allMatched`, every required field resolves. */
function buildMapping(allMatched: boolean): Pick<VendorInvoiceTemplate, 'itemMappings' | 'summaryMappings'> {
  const itemMappings: VendorFieldMapping[] = REQUIRED_ITEM_FIELDS.map((target, i) => ({
    targetField: target,
    sourceLabel: allMatched || i < REQUIRED_ITEM_FIELDS.length - 1 ? defaultSourceLabel(target) : '',
    required: true,
    matched: allMatched || i < REQUIRED_ITEM_FIELDS.length - 1
  }));
  const summaryMappings: VendorFieldMapping[] = REQUIRED_SUMMARY_FIELDS.map(target => ({
    targetField: target,
    sourceLabel: defaultSourceLabel(target),
    required: true,
    matched: true
  }));
  return { itemMappings, summaryMappings };
}

function defaultSourceLabel(targetField: string): string {
  const map: Record<string, string> = {
    'Item name': 'PARTICULARS',
    Quantity: 'QTY',
    Rate: 'RATE',
    Amount: 'AMOUNT',
    'Invoice number': 'INVOICE NO',
    'Invoice date': 'DATE',
    'Bill amount': 'GRAND TOTAL'
  };
  return map[targetField] ?? targetField.toUpperCase();
}

/** Mock vendor backend (localStorage). Mirrors the AAS vendor master UX. */
@Injectable({ providedIn: 'root' })
export class VendorStoreService {
  private vendors: Vendor[] = [];

  constructor() {
    const raw = this.read();
    this.vendors = raw ?? SEED.map(v => ({ ...v }));
    if (!raw) {
      this.persist();
    }
  }

  private read(): Vendor[] | null {
    try {
      const raw = localStorage.getItem(KEY);
      return raw ? (JSON.parse(raw) as Vendor[]) : null;
    } catch {
      return null;
    }
  }

  private persist(): void {
    try {
      localStorage.setItem(KEY, JSON.stringify(this.vendors));
    } catch {
      /* ignore */
    }
  }

  private toView(v: Vendor): VendorView {
    return {
      ...v,
      outstanding: Math.max(0, Math.round((v.totalPurchased - v.totalPaid) * 100) / 100),
      invoiceConfigured: !!v.invoiceTemplate?.configured,
      invoiceValidated: !!v.invoiceTemplate?.validated
    };
  }

  private id(): string {
    return `v_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 6)}`;
  }

  list(): Observable<VendorView[]> {
    return of(this.listSnapshot()).pipe(delay(LATENCY));
  }

  /** Synchronous view list (for forms that filter client-side). */
  listSnapshot(): VendorView[] {
    return this.vendors.slice().sort((a, b) => a.name.localeCompare(b.name)).map(v => this.toView(v));
  }

  create(input: Omit<Vendor, 'id'>): Observable<VendorView> {
    const vendor: Vendor = { ...input, id: this.id() };
    this.vendors.push(vendor);
    this.persist();
    return of(this.toView(vendor)).pipe(delay(LATENCY));
  }

  update(id: string, patch: Omit<Vendor, 'id'>): Observable<VendorView> {
    const vendor = this.vendors.find(v => v.id === id)!;
    Object.assign(vendor, patch);
    this.persist();
    return of(this.toView(vendor)).pipe(delay(LATENCY));
  }

  remove(id: string): Observable<void> {
    this.vendors = this.vendors.filter(v => v.id !== id);
    this.persist();
    return of(undefined).pipe(delay(LATENCY));
  }

  // --- Invoice format (mock of the ui/ OCR + mapping pipeline) ---

  /**
   * Simulates analyzing an uploaded sample invoice into a field mapping.
   * Returns an unsaved, not-yet-validated template the user can review.
   */
  analyzeInvoiceSample(sampleFileName: string): Observable<VendorInvoiceTemplate> {
    const built = buildMapping(true);
    const itemsDetected = 8 + Math.floor(Math.random() * 16);
    const template: VendorInvoiceTemplate = {
      ...emptyInvoiceTemplate(),
      configured: true,
      validated: false,
      engineLabel: 'Native layout mapping',
      sampleFileName,
      itemMappings: built.itemMappings,
      summaryMappings: built.summaryMappings,
      itemsDetected,
      totalRows: itemsDetected + 2,
      billAmount: Math.round((10000 + Math.random() * 80000) * 100) / 100
    };
    return of(template).pipe(delay(ANALYZE_LATENCY));
  }

  /** Persists a reviewed template against the vendor and marks it validated. */
  saveInvoiceTemplate(id: string, template: VendorInvoiceTemplate): Observable<VendorView> {
    const vendor = this.vendors.find(v => v.id === id)!;
    vendor.invoiceTemplate = {
      ...template,
      configured: true,
      validated: true,
      validatedAt: new Date().toISOString()
    };
    this.persist();
    return of(this.toView(vendor)).pipe(delay(LATENCY));
  }

  removeInvoiceTemplate(id: string): Observable<VendorView> {
    const vendor = this.vendors.find(v => v.id === id)!;
    vendor.invoiceTemplate = null;
    if (vendor.status === 'Active') {
      vendor.status = 'Inactive';
    }
    this.persist();
    return of(this.toView(vendor)).pipe(delay(LATENCY));
  }
}
