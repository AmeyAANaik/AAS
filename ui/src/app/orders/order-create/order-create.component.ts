import { Component, EventEmitter, Input, OnChanges, OnDestroy, OnInit, Output } from '@angular/core';
import { Location } from '@angular/common';
import { Router } from '@angular/router';
import { FormBuilder, FormGroup, Validators } from '@angular/forms';
import { Subscription, from, of } from 'rxjs';
import { concatMap, finalize, map, switchMap, toArray } from 'rxjs/operators';
import { OrderCreateResult, OrderOption } from '../order.model';
import { OrderService } from '../order.service';
import { CategoryService } from '../../categories/category.service';
import { formatUiError } from '../../shared/error-message.util';
import { ItemService } from '../../items/item.service';
import { Item } from '../../items/item.model';
import { VendorService } from '../../vendors/vendor.service';
import { Vendor } from '../../vendors/vendor.model';

type CreateMode = 'images' | 'items';
const BRANCH_IMAGE_PLACEHOLDER_ITEM_CODE = 'AAS-SYSTEM-BRANCH-IMAGE';

interface CategoryOrderItemOption {
  id: string;
  code: string;
  name: string;
  category: string;
  unit: string;
  packagingUnit: string;
  rate: number;
  gstPercent: number;
  selected: boolean;
  qty: number;
}

interface CategoryVendorOption {
  id: string;
  name: string;
  category: string;
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
  allVendors: CategoryVendorOption[] = [];
  categoryItems: CategoryOrderItemOption[] = [];
  categoryVendors: CategoryVendorOption[] = [];
  itemSearchTerm = '';
  isItemsLoading = false;
  isVendorsLoading = false;
  itemsError = '';
  vendorsError = '';
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
    private vendorService: VendorService,
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
    this.loadVendors();
    const categorySub = this.detailsGroup.get('category')?.valueChanges.subscribe(value => {
      const categoryId = String(value ?? '');
      this.updateCategoryItems(categoryId);
      this.updateCategoryVendors(categoryId);
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
        items: [{
          item_code: BRANCH_IMAGE_PLACEHOLDER_ITEM_CODE,
          qty: 1,
          rate: 0
        }]
      })
      .pipe(finalize(() => (this.isSubmitting = false)))
      .subscribe({
        next: response => {
          const id = this.extractOrderId(response);
          const displayId = this.extractOrderDisplayId(response);
          if (id) {
            this.createdOrderId = id;
            this.statusMessage = `Order created. Continue in Manage Order to upload the vendor PDF and parse the item list: ${id}`;
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

  get noActiveBranchesAvailable(): boolean {
    return !this.isShopsLoading && !this.shopsError && this.shops.length === 0;
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
    return formatUiError(err, fallback);
  }

  private asFlag(value: unknown): boolean {
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
    return text === '1' || text === 'true' || text === 'yes';
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
            const disabled = this.asFlag(branch?.disabled);
            return { id: String(branch?.name ?? name), name: name || String(branch?.name ?? ''), disabled };
          })
          .filter(branch => !branch.disabled)
          .map(({ id, name }) => ({ id, name }));
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
          this.updateCategoryVendors(String(this.detailsGroup.get('category')?.value ?? ''));
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

  private loadVendors(): void {
    this.isVendorsLoading = true;
    this.vendorsError = '';
    const sub = this.vendorService
      .listVendors()
      .pipe(finalize(() => (this.isVendorsLoading = false)))
      .subscribe({
        next: vendors => {
          this.allVendors = (vendors ?? [])
            .map(vendor => this.mapVendorOption(vendor))
            .filter(vendor => !!vendor.category)
            .sort((left, right) => left.name.localeCompare(right.name));
          this.updateCategoryVendors(String(this.detailsGroup.get('category')?.value ?? ''));
        },
        error: err => {
          this.vendorsError = this.formatError(err, 'Unable to load vendors for this category.');
        }
      });
    this.subscriptions.add(sub);
  }

  setCreateMode(mode: CreateMode): void {
    this.createMode = mode;
    this.statusMessage = '';
    if (mode === 'items') {
      const categoryId = String(this.detailsGroup.get('category')?.value ?? '');
      this.updateCategoryItems(categoryId);
      this.updateCategoryVendors(categoryId);
    }
  }

  toggleItemSelection(itemId: string, selected: boolean): void {
    this.categoryItems = this.categoryItems.map(item => item.id === itemId
      ? { ...item, selected, qty: selected ? Math.max(item.qty, 1) : 1 }
      : item);
    this.syncItemsToMasterList();
  }

  get selectedOrderItems(): CategoryOrderItemOption[] {
    return this.categoryItems.filter(item => item.selected);
  }

  get selectedItemsCount(): number {
    return this.selectedOrderItems.length;
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
      rate: Math.max(0, this.asNumber(item.aas_vendor_rate) ?? 0),
      gstPercent: 0,
      selected: false,
      qty: 1
    };
  }

  private mapVendorOption(vendor: Vendor): CategoryVendorOption {
    const name = String(vendor.supplier_name ?? vendor.name ?? '').trim();
    return {
      id: String(vendor.name ?? name).trim(),
      name: name || String(vendor.name ?? '').trim(),
      category: String(vendor.category ?? '').trim()
    };
  }

  private updateCategoryItems(categoryId: string): void {
    const normalized = this.normalizeCategory(categoryId);
    this.itemSearchTerm = '';
    this.categoryItems = this.allItems
      .filter(item => normalized ? this.normalizeCategory(item.category) === normalized : false)
      .map(item => ({ ...item }))
      .sort((left, right) => left.name.localeCompare(right.name));
  }

  private updateCategoryVendors(categoryId: string): void {
    const normalized = this.normalizeCategory(categoryId);
    this.categoryVendors = this.allVendors
      .filter(vendor => normalized ? this.normalizeCategory(vendor.category) === normalized : false)
      .map(vendor => ({ ...vendor }))
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

  private normalizeCategory(value: unknown): string {
    return String(value ?? '').trim().toLowerCase();
  }

}
