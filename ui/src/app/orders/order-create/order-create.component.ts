import { Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output } from '@angular/core';
import { Location } from '@angular/common';
import { Router } from '@angular/router';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Subscription, from, of } from 'rxjs';
import { concatMap, finalize, map, switchMap, toArray } from 'rxjs/operators';
import { OrderCreateResult, OrderOption } from '../order.model';
import { OrderService } from '../order.service';
import { CategoryService } from '../../categories/category.service';
import { ItemService } from '../../items/item.service';
import { Item } from '../../items/item.model';

type CreateMode = 'images' | 'items';

interface CategoryOrderItemOption {
  id: string;
  code: string;
  name: string;
  category: string;
  unit: string;
  packagingUnit: string;
  marginPercent: number | null;
  selected: boolean;
  qty: number;
}

@Component({
  selector: 'app-order-create',
  templateUrl: './order-create.component.html',
  styleUrl: './order-create.component.scss'
})
export class OrderCreateComponent implements OnInit, OnChanges, OnDestroy {
  @Input() shops: OrderOption[] = [];
  @Output() created = new EventEmitter<OrderCreateResult>();

  statusMessage = '';
  isSubmitting = false;
  isShopsLoading = false;
  isCategoriesLoading = false;
  isCompaniesLoading = false;
  shopsError = '';
  categoriesError = '';
  companiesError = '';
  imageFiles: File[] = [];
  imagePreviewUrls: string[] = [];
  createdOrderId: string | null = null;
  companies: OrderOption[] = [];
  categories: OrderOption[] = [];
  createMode: CreateMode = 'images';
  allItems: CategoryOrderItemOption[] = [];
  categoryItems: CategoryOrderItemOption[] = [];
  itemSearchTerm = '';
  isItemsLoading = false;
  itemsError = '';
  private subscriptions = new Subscription();

  detailsGroup: FormGroup = this.fb.group({
    customer: ['', Validators.required],
    category: ['', Validators.required],
    company: ['', Validators.required],
    orderDate: ['', Validators.required],
    deliveryDate: ['', Validators.required]
  });

  imageGroup: FormGroup = this.fb.group({
    imageName: ['']
  });

  form: FormGroup = this.fb.group({
    details: this.detailsGroup,
    image: this.imageGroup
  });

  constructor(
    private fb: FormBuilder,
    private orderService: OrderService,
    private categoryService: CategoryService,
    private itemService: ItemService,
    private location: Location,
    private router: Router
  ) {
    this.setTodayDefaults();
  }

  ngOnInit(): void {
    this.loadCompanies();
    if (!this.shops?.length) {
      this.loadShops();
    }
    this.loadCategories();
    this.loadItems();
    const categorySub = this.detailsGroup.get('category')?.valueChanges.subscribe(value => {
      this.updateCategoryItems(String(value ?? ''));
    });
    if (categorySub) {
      this.subscriptions.add(categorySub);
    }
  }

  ngOnChanges(): void {
    // Categories are loaded internally for the create flow.
  }

  submit(): void {
    if (this.form.invalid) {
      this.form.markAllAsTouched();
      return;
    }

    if (this.createMode === 'items' && !this.selectedOrderItems.length) {
      this.statusMessage = 'Select at least one item from the chosen category before creating the order.';
      return;
    }

    this.isSubmitting = true;
    this.statusMessage = this.createMode === 'items'
      ? 'Creating order from selected items...'
      : 'Creating order...';
    this.createdOrderId = null;
    const details = this.detailsGroup.getRawValue();

    if (this.createMode === 'items') {
      this.orderService.createOrder({
        customer: String(details.customer ?? '').trim(),
        company: String(details.company ?? '').trim(),
        aas_category: String(details.category ?? '').trim(),
        transaction_date: String(details.orderDate ?? ''),
        delivery_date: String(details.deliveryDate ?? ''),
        items: this.selectedOrderItems.map(item => ({
          item_code: item.code,
          qty: item.qty,
          rate: 0
        }))
      })
      .pipe(finalize(() => (this.isSubmitting = false)))
      .subscribe({
        next: response => {
          const id = this.extractOrderId(response);
          const displayId = this.extractOrderDisplayId(response);
          if (id) {
            this.createdOrderId = id;
            this.statusMessage = `Order created from ${this.selectedOrderItems.length} selected item${this.selectedOrderItems.length === 1 ? '' : 's'}: ${id}`;
            this.created.emit({
              id,
              customer: String(details.customer ?? '').trim(),
              transactionDate: String(details.orderDate ?? '')
            });
            void this.router.navigate(['/orders'], {
              queryParams: { orderId: id, q: displayId || id }
            });
          } else {
            this.statusMessage = 'Order created.';
          }
          this.resetForm(false);
        },
        error: err => {
          this.statusMessage = this.formatError(err, 'Unable to create order from selected items.');
        }
      });
      return;
    }

    if (!this.imageFiles.length) {
      this.isSubmitting = false;
      this.statusMessage = 'Upload at least one branch image before creating the order.';
      return;
    }
    const [primaryImage, ...extraImages] = this.imageFiles;
    let createdOrderId = '';
    this.orderService
      .createOrderFromBranchImage(primaryImage, {
        customer: String(details.customer ?? '').trim(),
        company: String(details.company ?? '').trim(),
        category: String(details.category ?? '').trim(),
        transaction_date: String(details.orderDate ?? ''),
        delivery_date: String(details.deliveryDate ?? '')
      })
      .pipe(
        map(response => {
          const id = this.extractOrderId(response);
          if (!id) {
            throw new Error('Order created but ID missing.');
          }
          createdOrderId = id;
          return { id, response };
        }),
        switchMap(({ id, response }) => {
          if (!extraImages.length) {
            return of({ id, response, uploadedExtras: 0 });
          }
          this.statusMessage = `Uploading ${extraImages.length} additional image${extraImages.length === 1 ? '' : 's'}...`;
          return from(extraImages).pipe(
            concatMap(file => this.orderService.uploadOrderImage(id, file)),
            toArray(),
            map(results => ({ id, response, uploadedExtras: results.length }))
          );
        }),
        finalize(() => (this.isSubmitting = false))
      )
      .subscribe({
        next: result => {
          const id = result?.id ?? '';
          const displayId = this.extractOrderDisplayId(result?.response);
          if (id) {
            this.createdOrderId = id;
            this.statusMessage = `Order created with ${this.imageFiles.length} image${this.imageFiles.length === 1 ? '' : 's'} and top vendor assigned automatically: ${id}`;
            this.created.emit({
              id,
              customer: String(details.customer ?? '').trim(),
              transactionDate: String(details.orderDate ?? '')
            });
            void this.router.navigate(['/orders'], {
              queryParams: { orderId: id, q: displayId || id }
            });
          } else {
            this.statusMessage = 'Order created.';
          }
          this.resetForm(false);
        },
        error: err => {
          const fallback = createdOrderId
            ? `Order ${createdOrderId} was created, but follow-up processing failed.`
            : 'Unable to create order.';
          this.statusMessage = this.formatError(err, fallback);
        }
      });
  }

  clear(): void {
    this.resetForm(true);
  }

  goBack(): void {
    this.location.back();
  }

  ngOnDestroy(): void {
    this.subscriptions.unsubscribe();
    this.revokePreviewUrls();
  }

  get canSubmit(): boolean {
    const hasModeInput = this.createMode === 'images'
      ? this.imageSelected
      : this.selectedOrderItems.length > 0;
    return this.form.valid && hasModeInput && !this.isSubmitting;
  }

  get imageSelected(): boolean {
    return this.imageFiles.length > 0;
  }

  private setTodayDefaults(): void {
    const today = this.formatDate(new Date());
    this.detailsGroup.patchValue({ orderDate: today, deliveryDate: today });
  }

  private resetForm(clearCreated: boolean): void {
    this.detailsGroup.reset({
      customer: '',
      category: '',
      company: this.defaultCompanyId,
      orderDate: this.formatDate(new Date()),
      deliveryDate: this.formatDate(new Date())
    });
    this.imageGroup.reset({ imageName: '' });
    this.imageFiles = [];
    this.itemSearchTerm = '';
    this.clearSelectedItems();
    this.revokePreviewUrls();
    if (clearCreated) {
      this.statusMessage = '';
      this.createdOrderId = null;
    }
  }

  private formatDate(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  private formatError(err: unknown, fallback: string): string {
    const anyErr = err as { error?: unknown; message?: string } | null;
    const payload = anyErr?.error;
    if (payload && typeof payload === 'object') {
      const message = (payload as { message?: unknown; error?: unknown }).message
        ?? (payload as { message?: unknown; error?: unknown }).error;
      if (typeof message === 'string' && message.trim()) {
        return message.trim();
      }
    }
    if (err instanceof Error && err.message.trim()) {
      return err.message;
    }
    if (typeof err === 'string') {
      return err;
    }
    if (typeof anyErr?.message === 'string' && anyErr.message.trim()) {
      return anyErr.message.trim();
    }
    return fallback;
  }

  private loadCompanies(): void {
    this.isCompaniesLoading = true;
    this.companiesError = '';
    const sub = this.orderService
      .listCompanies()
      .pipe(finalize(() => (this.isCompaniesLoading = false)))
      .subscribe({
        next: companies => {
          this.companies = (companies ?? []).map(company => {
            const name = String(company?.name ?? '').trim();
            return { id: name, name };
          });
          this.detailsGroup.patchValue({ company: this.defaultCompanyId });
        },
        error: err => {
          this.companiesError = this.formatError(err, 'Unable to load companies');
          if (!this.detailsGroup.get('company')?.value) {
            this.detailsGroup.patchValue({ company: 'AAS' });
          }
        }
      });
    this.subscriptions.add(sub);
  }

  private loadShops(): void {
    this.isShopsLoading = true;
    this.shopsError = '';
    const sub = this.orderService
      .listBranches()
      .pipe(finalize(() => (this.isShopsLoading = false)))
      .subscribe({
        next: branches => {
          this.shops = (branches ?? []).map(branch => {
            const name = String(branch?.customer_name ?? branch?.name ?? '').trim();
            return { id: String(branch?.name ?? name), name: name || String(branch?.name ?? '') };
          });
        },
        error: err => {
          this.shopsError = this.formatError(err, 'Unable to load branches');
        }
      });
    this.subscriptions.add(sub);
  }

  private loadCategories(): void {
    this.isCategoriesLoading = true;
    this.categoriesError = '';
    const sub = this.categoryService
      .listCategories()
      .pipe(finalize(() => (this.isCategoriesLoading = false)))
      .subscribe({
        next: categories => {
          this.categories = (categories ?? []).map(category => {
            const name = String(category?.item_group_name ?? category?.name ?? '').trim();
            return {
              id: String(category?.name ?? name),
              name: name || String(category?.name ?? ''),
              disabled: false
            };
          })
          .filter(category => !category.disabled)
          .map(({ id, name }) => ({ id, name }));
          this.updateCategoryItems(String(this.detailsGroup.get('category')?.value ?? ''));
        },
        error: err => {
          this.categoriesError = this.formatError(err, 'Unable to load categories');
        }
      });
    this.subscriptions.add(sub);
  }

  private loadItems(): void {
    this.isItemsLoading = true;
    this.itemsError = '';
    const sub = this.itemService
      .listItems()
      .pipe(finalize(() => (this.isItemsLoading = false)))
      .subscribe({
        next: items => {
          this.allItems = (items ?? []).map(item => this.mapItemOption(item));
          this.updateCategoryItems(String(this.detailsGroup.get('category')?.value ?? ''));
        },
        error: err => {
          this.itemsError = this.formatError(err, 'Unable to load items for category ordering.');
        }
      });
    this.subscriptions.add(sub);
  }

  setCreateMode(mode: CreateMode): void {
    this.createMode = mode;
    this.statusMessage = '';
    if (mode === 'items') {
      this.updateCategoryItems(String(this.detailsGroup.get('category')?.value ?? ''));
    }
  }

  toggleItemSelection(itemId: string, selected: boolean): void {
    this.categoryItems = this.categoryItems.map(item => item.id === itemId
      ? { ...item, selected, qty: selected ? Math.max(item.qty, 1) : 1 }
      : item);
    this.syncItemsToMasterList();
  }

  updateItemQty(itemId: string, rawValue: string | number): void {
    const qty = Math.max(1, Number(rawValue || 1));
    this.categoryItems = this.categoryItems.map(item => item.id === itemId
      ? { ...item, qty, selected: true }
      : item);
    this.syncItemsToMasterList();
  }

  get selectedOrderItems(): CategoryOrderItemOption[] {
    return this.categoryItems.filter(item => item.selected);
  }

  get selectedItemsCount(): number {
    return this.selectedOrderItems.length;
  }

  get totalSelectedQty(): number {
    return this.selectedOrderItems.reduce((sum, item) => sum + item.qty, 0);
  }

  get filteredCategoryItems(): CategoryOrderItemOption[] {
    const term = this.itemSearchTerm.trim().toLowerCase();
    if (!term) {
      return this.categoryItems;
    }
    return this.categoryItems.filter(item =>
      item.name.toLowerCase().includes(term)
      || item.code.toLowerCase().includes(term)
      || item.unit.toLowerCase().includes(term)
      || item.packagingUnit.toLowerCase().includes(term)
    );
  }

  updateItemSearch(term: string): void {
    this.itemSearchTerm = term;
  }

  clearItemSearch(): void {
    this.itemSearchTerm = '';
  }

  onImageSelected(event: Event): void {
    const input = event.target as HTMLInputElement | null;
    const files = input?.files ? Array.from(input.files) : [];
    if (!files.length) {
      this.clearImage();
      return;
    }
    const existingKeys = new Set(
      this.imageFiles.map(file => `${file.name}:${file.size}:${file.lastModified}`)
    );
    const appended = files.filter(file => {
      const key = `${file.name}:${file.size}:${file.lastModified}`;
      if (existingKeys.has(key)) {
        return false;
      }
      existingKeys.add(key);
      return true;
    });
    this.setImages([...this.imageFiles, ...appended]);
    if (input) {
      input.value = '';
    }
  }

  private isDisabled(value: unknown): boolean {
    if (value === null || value === undefined) {
      return false;
    }
    if (typeof value === 'boolean') {
      return value;
    }
    if (typeof value === 'number') {
      return value !== 0;
    }
    const text = String(value).trim().toLowerCase();
    if (!text) {
      return false;
    }
    return text === '1' || text === 'true' || text === 'yes';
  }

  generateSampleImage(): void {
    const timestamp = this.formatDate(new Date());
    const svg = `
      <svg xmlns="http://www.w3.org/2000/svg" width="800" height="500">
        <defs>
          <linearGradient id="bg" x1="0" y1="0" x2="1" y2="1">
            <stop offset="0%" stop-color="#f4f6f9"/>
            <stop offset="100%" stop-color="#d8e2f0"/>
          </linearGradient>
        </defs>
        <rect width="800" height="500" fill="url(#bg)"/>
        <rect x="60" y="60" width="680" height="380" fill="#ffffff" stroke="#1f2937" stroke-width="2"/>
        <text x="100" y="140" font-size="28" font-family="Arial" fill="#111827">Branch Order Image</text>
        <text x="100" y="190" font-size="18" font-family="Arial" fill="#374151">Generated: ${timestamp}</text>
        <text x="100" y="230" font-size="18" font-family="Arial" fill="#374151">Branch: ${
          this.detailsGroup.get('customer')?.value || 'Unassigned'
        }</text>
        <text x="100" y="270" font-size="18" font-family="Arial" fill="#374151">Category: ${
          this.detailsGroup.get('category')?.value || 'Unassigned'
        }</text>
      </svg>
    `;
    const blob = new Blob([svg.trim()], { type: 'image/svg+xml' });
    const file = new File([blob], `order-${timestamp}.svg`, { type: 'image/svg+xml' });
    this.setImages([file]);
  }

  clearImage(): void {
    this.imageFiles = [];
    this.imageGroup.patchValue({ imageName: '' });
    this.revokePreviewUrls();
  }

  removeImage(index: number): void {
    if (index < 0 || index >= this.imageFiles.length) {
      return;
    }
    this.imageFiles = this.imageFiles.filter((_, i) => i !== index);
    this.syncImageState();
  }

  private setImages(files: File[]): void {
    this.imageFiles = files;
    this.syncImageState();
  }

  private syncImageState(): void {
    this.imageGroup.patchValue({
      imageName: this.imageFiles.map(file => file.name).join(', ')
    });
    this.revokePreviewUrls();
    this.imagePreviewUrls = this.imageFiles.map(file => URL.createObjectURL(file));
  }

  private revokePreviewUrls(): void {
    this.imagePreviewUrls.forEach(url => URL.revokeObjectURL(url));
    this.imagePreviewUrls = [];
  }

  private extractOrderId(response: unknown): string {
    const anyResponse = response as { name?: string; data?: { name?: string } } | null;
    return String(anyResponse?.name ?? anyResponse?.data?.name ?? '').trim();
  }

  private extractOrderDisplayId(response: unknown): string {
    const anyResponse = response as { title?: string; data?: { title?: string } } | null;
    return String(anyResponse?.title ?? anyResponse?.data?.title ?? '').trim();
  }

  get defaultCompanyId(): string {
    if (!this.companies.length) {
      return 'AAS';
    }
    return this.companies.find(company => company.id === 'AAS')?.id ?? this.companies[0].id;
  }

  private mapItemOption(item: Item): CategoryOrderItemOption {
    const id = String(item.name ?? item.item_code ?? '').trim();
    return {
      id,
      code: String(item.item_code ?? '').trim(),
      name: String(item.item_name ?? item.item_code ?? '').trim(),
      category: String(item.item_group ?? '').trim(),
      unit: String(item.stock_uom ?? 'Nos').trim() || 'Nos',
      packagingUnit: String(item.aas_packaging_unit ?? '').trim(),
      marginPercent: this.asNumber(item.aas_margin_percent),
      selected: false,
      qty: 1
    };
  }

  private updateCategoryItems(categoryId: string): void {
    const normalized = String(categoryId ?? '').trim().toLowerCase();
    this.itemSearchTerm = '';
    this.categoryItems = this.allItems
      .filter(item => normalized ? item.category.toLowerCase() === normalized : false)
      .map(item => ({ ...item }))
      .sort((left, right) => left.name.localeCompare(right.name));
  }

  private syncItemsToMasterList(): void {
    const overrides = new Map(this.categoryItems.map(item => [item.id, item]));
    this.allItems = this.allItems.map(item => overrides.get(item.id) ?? item);
  }

  private clearSelectedItems(): void {
    this.allItems = this.allItems.map(item => ({ ...item, selected: false, qty: 1 }));
    this.updateCategoryItems(String(this.detailsGroup.get('category')?.value ?? ''));
  }

  private asNumber(value: unknown): number | null {
    if (value === null || value === undefined || value === '') {
      return null;
    }
    const numeric = Number(value);
    return Number.isFinite(numeric) ? numeric : null;
  }

}
