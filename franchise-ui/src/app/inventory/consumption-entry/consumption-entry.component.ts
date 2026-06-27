import { CommonModule } from '@angular/common';
import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCheckboxModule } from '@angular/material/checkbox';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { MatTableModule } from '@angular/material/table';
import { PageHeaderComponent } from '../../shared/page-header/page-header.component';
import { EmptyStateComponent } from '../../shared/empty-state/empty-state.component';
import { InventoryStoreService } from '../inventory-store.service';
import { ProductStockView, StockTxn } from '../inventory.model';

interface ConsumeOption {
  id: string;
  name: string;
  unit: string;
  category: string;
  available: number;
  qty: number;
  reason: string;
  selected: boolean;
}

@Component({
  selector: 'app-consumption-entry',
  standalone: true,
  imports: [
    CommonModule, ReactiveFormsModule, MatButtonModule, MatCheckboxModule, MatFormFieldModule,
    MatIconModule, MatInputModule, MatSelectModule, MatSnackBarModule, MatTableModule,
    PageHeaderComponent, EmptyStateComponent
  ],
  templateUrl: './consumption-entry.component.html',
  styleUrl: './consumption-entry.component.css'
})
export class ConsumptionEntryComponent implements OnInit {
  products: ProductStockView[] = [];
  categories: string[] = [];
  options: ConsumeOption[] = [];
  itemSearchTerm = '';
  statusMessage = '';

  recent: StockTxn[] = [];
  readonly recentColumns = ['date', 'product', 'category', 'qty', 'reason', 'actions'];

  // Edit state
  editingId: string | null = null;
  editForm: FormGroup = this.fb.group({
    qty: [0, [Validators.required, Validators.min(0.01)]],
    reason: [''],
    date: ['', Validators.required]
  });

  form: FormGroup = this.fb.group({
    category: ['', Validators.required],
    date: [new Date().toISOString().slice(0, 10), Validators.required]
  });

  constructor(
    private fb: FormBuilder,
    public store: InventoryStoreService,
    private snack: MatSnackBar
  ) {}

  ngOnInit(): void {
    this.categories = this.store.listCategories();
    this.loadProducts();
    this.loadRecent();
  }

  private loadProducts(): void {
    this.products = this.store.listProductsSnapshot();
    this.options = this.products
      .slice()
      .sort((a, b) => a.name.localeCompare(b.name))
      .map(p => ({
        id: p.id, name: p.name, unit: p.unit, category: p.category ?? '',
        available: p.currentStock, qty: 1, reason: '', selected: false
      }));
  }

  private loadRecent(): void {
    this.store.listTransactions().subscribe(txns => {
      this.recent = txns.filter(t => t.type === 'CONSUMPTION').slice(0, 12);
    });
  }

  get selectedCategory(): string {
    return String(this.form.get('category')?.value ?? '');
  }

  onCategoryChange(): void {
    this.options = this.options.map(o => ({ ...o, selected: false, qty: 1, reason: '' }));
    this.itemSearchTerm = '';
    this.statusMessage = '';
  }

  // ---- catalog --------------------------------------------------------------

  get filteredOptions(): ConsumeOption[] {
    const cat = this.selectedCategory;
    const term = this.itemSearchTerm.trim().toLowerCase();
    return this.options
      .filter(o => (cat ? o.category === cat : true))
      .filter(o => !term || o.name.toLowerCase().includes(term) || o.unit.toLowerCase().includes(term));
  }

  updateItemSearch(term: string): void {
    this.itemSearchTerm = term;
  }

  clearItemSearch(): void {
    this.itemSearchTerm = '';
  }

  get selectedOptions(): ConsumeOption[] {
    return this.options.filter(o => o.selected);
  }

  get selectedCount(): number {
    return this.selectedOptions.length;
  }

  toggleSelection(id: string, selected: boolean): void {
    this.options = this.options.map(o => o.id === id ? { ...o, selected } : o);
  }

  onQtyInput(id: string, raw: string): void {
    const n = Number(raw);
    const qty = Number.isFinite(n) && n > 0 ? Math.round(n * 100) / 100 : 0;
    this.options = this.options.map(o => o.id === id ? { ...o, qty, selected: true } : o);
  }

  onReasonInput(id: string, raw: string): void {
    this.options = this.options.map(o => o.id === id ? { ...o, reason: raw } : o);
  }

  exceedsStock(o: ConsumeOption): boolean {
    return o.selected && o.qty > o.available;
  }

  // ---- submit ---------------------------------------------------------------

  submit(): void {
    if (!this.selectedCategory) {
      this.form.markAllAsTouched();
      this.statusMessage = 'Select a category.';
      return;
    }
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      this.statusMessage = 'Select a consumption date.';
      return;
    }
    const lines = this.selectedOptions.filter(o => o.qty > 0);
    if (!lines.length) {
      this.statusMessage = 'Select at least one product and quantity.';
      return;
    }
    const overdrawn = lines.find(o => o.qty > o.available);
    if (overdrawn) {
      this.statusMessage = `Cannot consume ${overdrawn.qty} of ${overdrawn.name} — only ${overdrawn.available} available.`;
      return;
    }

    const v = this.form.getRawValue();
    this.store.recordConsumption({
      date: v.date,
      lines: lines.map(o => ({ productId: o.id, qty: o.qty, reason: o.reason }))
    }).subscribe({
      next: () => {
        this.snack.open('Consumption recorded — stock reduced', 'OK', { duration: 2600 });
        this.reset();
      },
      error: (err: Error) => {
        this.snack.open(err.message, 'Dismiss', { duration: 4000, panelClass: 'master-data-snackbar-error' });
      }
    });
  }

  private reset(): void {
    this.form.reset({ category: '', date: new Date().toISOString().slice(0, 10) });
    this.itemSearchTerm = '';
    this.statusMessage = '';
    this.loadProducts();
    this.loadRecent();
  }

  // ---- edit recorded consumption -------------------------------------------

  startEdit(txn: StockTxn): void {
    this.editingId = txn.id;
    this.editForm.reset({ qty: -txn.qty, reason: txn.ref ?? '', date: txn.date });
  }

  cancelEdit(): void {
    this.editingId = null;
  }

  saveEdit(): void {
    if (!this.editingId || this.editForm.invalid) {
      this.editForm.markAllAsTouched();
      return;
    }
    const v = this.editForm.getRawValue();
    this.store.updateConsumption(this.editingId, Number(v.qty), v.reason ?? '', v.date).subscribe({
      next: () => {
        this.snack.open('Consumption updated — stock adjusted', 'OK', { duration: 2400 });
        this.editingId = null;
        this.loadProducts();
        this.loadRecent();
      },
      error: (err: Error) => {
        this.snack.open(err.message, 'Dismiss', { duration: 4000, panelClass: 'master-data-snackbar-error' });
      }
    });
  }

  deleteEntry(txn: StockTxn): void {
    if (!confirm(`Delete consumption of ${-txn.qty} ${this.store.productName(txn.productId)}? Stock will be returned.`)) {
      return;
    }
    this.store.deleteConsumption(txn.id).subscribe(() => {
      this.snack.open('Consumption deleted — stock returned', 'OK', { duration: 2400 });
      if (this.editingId === txn.id) {
        this.editingId = null;
      }
      this.loadProducts();
      this.loadRecent();
    });
  }
}
