import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatAutocompleteModule } from '@angular/material/autocomplete';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatProgressBarModule } from '@angular/material/progress-bar';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { DatePickerFieldComponent } from '../../shared/date-picker-field/date-picker-field.component';
import { PageHeaderComponent } from '../../shared/page-header/page-header.component';
import { InventoryStoreService } from '../inventory-store.service';
import { ExtractedInvoiceLine, ProductStockView, PurchaseLineInput, StockTxn } from '../inventory.model';
import { VendorStoreService } from '../../vendors/vendor-store.service';
import { VendorView } from '../../vendors/vendor.model';

type PurchaseMode = 'upload' | 'select';

interface PurchaseItemOption {
  id: string;
  name: string;
  unit: string;
  category: string;
  rate: number;
  gstPercent: number;
  qty: number;
  selected: boolean;
}

interface ActiveLine {
  productId: string;
  name: string;
  qty: number;
  rate: number;
  gstPercent: number;
}

@Component({
  selector: 'app-purchase-entry',
  standalone: true,
  imports: [
    CommonModule, FormsModule, ReactiveFormsModule, MatAutocompleteModule, MatButtonModule, MatCheckboxModule,
    MatFormFieldModule, MatIconModule, MatInputModule, MatProgressBarModule, MatSelectModule,
    MatSnackBarModule, MatTableModule, DatePickerFieldComponent, PageHeaderComponent
  ],
  templateUrl: './purchase-entry.component.html',
  styleUrl: './purchase-entry.component.css'
})
export class PurchaseEntryComponent implements OnInit {
  mode: PurchaseMode = 'select';

  products: ProductStockView[] = [];
  options: PurchaseItemOption[] = [];
  categories: string[] = [];
  vendors: VendorView[] = [];
  itemSearchTerm = '';

  // Vendor (searchable)
  vendorQuery = '';
  selectedVendorId = '';
  selectedVendorName = '';

  // Upload mode
  fileName = '';
  isExtracting = false;
  extracted = false;
  uploadLines: ExtractedInvoiceLine[] = [];

  // Select-mode optional bill photo
  selectBillFileName = '';

  transportCharge = 0;
  statusMessage = '';

  recent: StockTxn[] = [];
  recentCategory = '';
  readonly recentColumns = ['date', 'product', 'category', 'qty', 'rate', 'gst', 'amount', 'ref'];

  private rateDrafts = new Map<string, string>();
  private gstDrafts = new Map<string, string>();
  private qtyDrafts = new Map<string, string>();

  form: FormGroup = this.fb.group({
    category: ['', Validators.required],
    billNo: ['', Validators.required],
    billDate: [new Date().toISOString().slice(0, 10), Validators.required]
  });

  constructor(
    private fb: FormBuilder,
    public store: InventoryStoreService,
    private vendorStore: VendorStoreService,
    private snack: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.loadProducts();
    this.categories = this.store.listCategories();
    this.vendors = this.vendorStore.listSnapshot().filter(v => v.status === 'Active');
    this.loadRecent();
  }

  private loadProducts(): void {
    this.products = this.store.listProductsSnapshot();
    this.options = this.products
      .slice()
      .sort((a, b) => a.name.localeCompare(b.name))
      .map(p => ({
        id: p.id, name: p.name, unit: p.unit, category: p.category ?? '',
        rate: p.lastRate, gstPercent: 0, qty: 1, selected: false
      }));
  }

  private loadRecent(): void {
    this.store.listTransactions().subscribe(txns => {
      this.recent = txns.filter(t => t.type === 'PURCHASE').slice(0, 20);
    });
  }

  setMode(mode: PurchaseMode): void {
    this.mode = mode;
    this.statusMessage = '';
  }

  get selectedCategory(): string {
    return String(this.form.get('category')?.value ?? '');
  }

  // ---- Category + vendor ----------------------------------------------------

  onCategoryChange(): void {
    // Reset vendor + product selections when the category scope changes.
    this.clearVendor();
    this.options = this.options.map(o => ({ ...o, selected: false }));
    this.itemSearchTerm = '';
  }

  get filteredVendors(): VendorView[] {
    const cat = this.selectedCategory;
    const q = this.vendorQuery.trim().toLowerCase();
    return this.vendors
      .filter(v => (cat ? v.category === cat : true))
      .filter(v => (q ? v.name.toLowerCase().includes(q) || v.code.toLowerCase().includes(q) : true));
  }

  onVendorInput(value: string): void {
    this.vendorQuery = value;
    // Typing invalidates a prior selection until an option is chosen.
    if (value !== this.selectedVendorName) {
      this.selectedVendorId = '';
    }
  }

  /** Autocomplete option chosen — resolve the vendor by name within the category. */
  onVendorSelected(name: string): void {
    const cat = this.selectedCategory;
    const vendor = this.vendors.find(v => v.name === name && (cat ? v.category === cat : true));
    if (vendor) {
      this.selectVendor(vendor);
    }
  }

  selectVendor(vendor: VendorView): void {
    this.selectedVendorId = vendor.id;
    this.selectedVendorName = vendor.name;
    this.vendorQuery = vendor.name;
  }

  private clearVendor(): void {
    this.selectedVendorId = '';
    this.selectedVendorName = '';
    this.vendorQuery = '';
  }

  // ---- Select mode: catalog -------------------------------------------------

  get filteredOptions(): PurchaseItemOption[] {
    const cat = this.selectedCategory;
    const term = this.itemSearchTerm.trim().toLowerCase();
    return this.options
      .filter(o => (cat ? o.category === cat : true))
      .filter(o => !term
        || o.name.toLowerCase().includes(term)
        || o.unit.toLowerCase().includes(term)
        || o.category.toLowerCase().includes(term));
  }

  updateItemSearch(term: string): void {
    this.itemSearchTerm = term;
  }

  clearItemSearch(): void {
    this.itemSearchTerm = '';
  }

  get selectedOptions(): PurchaseItemOption[] {
    return this.options.filter(o => o.selected);
  }

  get selectedCount(): number {
    return this.selectedOptions.length;
  }

  toggleSelection(id: string, selected: boolean): void {
    this.options = this.options.map(o => o.id === id ? { ...o, selected, qty: selected ? this.normalizeQty(o.qty) : 1 } : o);
  }

  displayQty(o: PurchaseItemOption): string { return this.qtyDrafts.get(o.id) ?? String(o.qty); }
  displayRate(o: PurchaseItemOption): string { return this.rateDrafts.get(o.id) ?? String(o.rate); }
  displayGst(o: PurchaseItemOption): string { return this.gstDrafts.get(o.id) ?? String(o.gstPercent); }

  onQtyInput(id: string, raw: string): void {
    this.qtyDrafts.set(id, raw);
    const n = this.asNumber(raw);
    if (n === null) { return; }
    this.options = this.options.map(o => o.id === id ? { ...o, qty: this.normalizeQty(n), selected: true } : o);
  }
  commitQtyDraft(id: string): void {
    this.qtyDrafts.set(id, String(this.options.find(o => o.id === id)?.qty ?? 1));
  }

  onRateInput(id: string, raw: string): void {
    this.rateDrafts.set(id, raw);
    const n = this.asNumber(raw);
    if (n === null) { return; }
    this.options = this.options.map(o => o.id === id ? { ...o, rate: Math.max(0, n), selected: true } : o);
  }
  commitRateDraft(id: string): void {
    this.rateDrafts.set(id, String(this.options.find(o => o.id === id)?.rate ?? 0));
  }

  onGstInput(id: string, raw: string): void {
    this.gstDrafts.set(id, raw);
    const n = this.asNumber(raw);
    if (n === null) { return; }
    this.options = this.options.map(o => o.id === id ? { ...o, gstPercent: Math.max(0, n), selected: true } : o);
  }
  commitGstDraft(id: string): void {
    this.gstDrafts.set(id, String(this.options.find(o => o.id === id)?.gstPercent ?? 0));
  }

  lineTotal(qty: number, rate: number, gstPercent: number): number {
    const base = qty * rate;
    return base + (base * gstPercent / 100);
  }

  onSelectBillImage(event: Event): void {
    const input = event.target as HTMLInputElement | null;
    const file = input?.files?.[0];
    if (file) {
      this.selectBillFileName = file.name;
    }
    if (input) { input.value = ''; }
  }

  clearSelectBillImage(): void {
    this.selectBillFileName = '';
  }

  // ---- Upload mode ----------------------------------------------------------

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement | null;
    const file = input?.files?.[0];
    if (!file) { return; }
    this.fileName = file.name;
    this.isExtracting = true;
    this.extracted = false;
    this.statusMessage = '';
    this.store.extractInvoice(file).subscribe(invoice => {
      this.form.patchValue({ category: invoice.category, billNo: invoice.billNo, billDate: invoice.billDate });
      const vendor = this.vendors.find(v => v.name.toLowerCase() === invoice.vendor.toLowerCase());
      if (vendor) {
        this.selectVendor(vendor);
      } else {
        this.clearVendor();
        this.vendorQuery = invoice.vendor;
      }
      this.uploadLines = invoice.lines.map(l => ({ ...l }));
      this.isExtracting = false;
      this.extracted = true;
    });
    if (input) { input.value = ''; }
  }

  clearUpload(): void {
    this.fileName = '';
    this.extracted = false;
    this.uploadLines = [];
  }

  onUploadField(line: ExtractedInvoiceLine, field: 'qty' | 'rate' | 'gstPercent', raw: string): void {
    const n = this.asNumber(raw);
    if (n === null) { return; }
    if (field === 'qty') { line.qty = this.normalizeQty(n); }
    else { line[field] = Math.max(0, n); }
  }

  onUploadProduct(line: ExtractedInvoiceLine, productId: string): void {
    line.productId = productId;
    line.matched = !!productId;
  }

  get unmatchedCount(): number {
    return this.uploadLines.filter(l => !l.productId).length;
  }

  // ---- Shared totals --------------------------------------------------------

  private activeLines(): ActiveLine[] {
    if (this.mode === 'select') {
      return this.selectedOptions.map(o => ({ productId: o.id, name: o.name, qty: o.qty, rate: o.rate, gstPercent: o.gstPercent }));
    }
    return this.uploadLines
      .filter(l => l.qty > 0)
      .map(l => ({ productId: l.productId, name: l.productName, qty: l.qty, rate: l.rate, gstPercent: l.gstPercent }));
  }

  get subtotal(): number {
    return this.round(this.activeLines().reduce((s, l) => s + l.qty * l.rate, 0));
  }

  get gstTotal(): number {
    return this.round(this.activeLines().reduce((s, l) => s + (l.qty * l.rate * l.gstPercent / 100), 0));
  }

  onTransportInput(raw: string): void {
    const n = this.asNumber(raw);
    this.transportCharge = n && n > 0 ? this.round(n) : 0;
  }

  get finalBill(): number {
    return this.round(this.subtotal + this.gstTotal + this.transportCharge);
  }

  get activeLineCount(): number {
    return this.activeLines().length;
  }

  // ---- Recent purchases filter ----------------------------------------------

  get filteredRecent(): StockTxn[] {
    if (!this.recentCategory) {
      return this.recent.slice(0, 10);
    }
    return this.recent.filter(t => this.store.productCategory(t.productId) === this.recentCategory).slice(0, 10);
  }

  setRecentCategory(category: string): void {
    this.recentCategory = category;
  }

  // ---- Submit ---------------------------------------------------------------

  submit(): void {
    if (!this.selectedCategory) {
      this.form.markAllAsTouched();
      this.statusMessage = 'Select a category.';
      return;
    }
    if (!this.selectedVendorId) {
      this.statusMessage = 'Select a vendor from the list.';
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.statusMessage = 'Enter the bill number and bill date.';
      return;
    }
    const lines = this.activeLines();
    if (!lines.length) {
      this.statusMessage = this.mode === 'select'
        ? 'Select at least one product to purchase.'
        : 'Upload a vendor invoice to extract line items.';
      return;
    }
    const unmapped = lines.find(l => !l.productId);
    if (unmapped) {
      this.statusMessage = `Map "${unmapped.name}" to a product before confirming.`;
      return;
    }
    const badRate = lines.find(l => l.rate <= 0);
    if (badRate) {
      this.statusMessage = `Enter a rate for ${badRate.name}.`;
      return;
    }

    const v = this.form.getRawValue();
    const billRef = this.mode === 'select' && this.selectBillFileName ? ` (bill: ${this.selectBillFileName})` : '';
    const payloadLines: PurchaseLineInput[] = lines.map(l => ({
      productId: l.productId, qty: l.qty, rate: l.rate, gstPercent: l.gstPercent
    }));
    this.store.recordPurchase({
      vendor: this.selectedVendorName + billRef, billNo: v.billNo, billDate: v.billDate,
      transport: this.transportCharge, lines: payloadLines
    }).subscribe(() => {
      this.snack.open('Purchase recorded — stock increased', 'OK', { duration: 2600 });
      this.reset();
    });
  }

  private reset(): void {
    this.form.reset({ category: '', billNo: '', billDate: new Date().toISOString().slice(0, 10) });
    this.clearVendor();
    this.transportCharge = 0;
    this.itemSearchTerm = '';
    this.statusMessage = '';
    this.selectBillFileName = '';
    this.rateDrafts.clear();
    this.gstDrafts.clear();
    this.qtyDrafts.clear();
    this.clearUpload();
    this.loadProducts();
    this.loadRecent();
  }

  // ---- helpers --------------------------------------------------------------

  private asNumber(value: unknown): number | null {
    if (value === null || value === undefined || value === '') { return null; }
    const n = Number(value);
    return Number.isFinite(n) ? n : null;
  }

  private normalizeQty(value: unknown): number {
    const n = this.asNumber(value);
    if (n === null || n <= 0) { return 0.01; }
    return Math.round(n * 100) / 100;
  }

  private round(n: number): number {
    return Math.round(n * 100) / 100;
  }
}
