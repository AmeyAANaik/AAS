import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, of } from 'rxjs';
import { ProductCategoryInput, ProductCategoryRecord, ProductCategoryStatus } from './product-category.model';

const STORAGE_KEY = 'franchise.product-categories.v1';

export const SEED_PRODUCT_CATEGORIES: ProductCategoryRecord[] = [
  { id: 'cat-raw-material', name: 'Raw Material', code: 'RAW_MATERIAL', description: 'Core ingredients and dry goods used in production.', status: 'Active', system: true, sortOrder: 10 },
  { id: 'cat-dairy', name: 'Dairy', code: 'DAIRY', description: 'Milk, cheese, butter and other dairy inventory.', status: 'Active', system: true, sortOrder: 20 },
  { id: 'cat-vegetable', name: 'Vegetable', code: 'VEGETABLE', description: 'Fresh vegetables and produce.', status: 'Active', system: true, sortOrder: 30 },
  { id: 'cat-utility', name: 'Utility', code: 'UTILITY', description: 'Gas, packaging utilities and operational consumables.', status: 'Active', system: true, sortOrder: 40 }
];

@Injectable({ providedIn: 'root' })
export class ProductCategoryStoreService {
  private readonly categories$: BehaviorSubject<ProductCategoryRecord[]>;
  readonly changes$: Observable<ProductCategoryRecord[]>;

  constructor() {
    this.categories$ = new BehaviorSubject<ProductCategoryRecord[]>(this.load());
    this.changes$ = this.categories$.asObservable();
  }

  list(): Observable<ProductCategoryRecord[]> {
    return of(this.listSnapshot());
  }

  listSnapshot(): ProductCategoryRecord[] {
    return this.sorted(this.categories$.value).map(category => ({ ...category }));
  }

  activeSnapshot(): ProductCategoryRecord[] {
    return this.listSnapshot().filter(category => category.status === 'Active');
  }

  activeNamesSnapshot(): string[] {
    return this.activeSnapshot().map(category => category.name);
  }

  create(input: ProductCategoryInput): Observable<ProductCategoryRecord> {
    const record: ProductCategoryRecord = {
      id: `prod-cat-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
      ...this.normalize(input),
      system: false,
      sortOrder: this.categories$.value.length * 10 + 10
    };
    const next = [...this.categories$.value, record];
    this.persist(next);
    this.categories$.next(this.sorted(next));
    return of({ ...record });
  }

  update(id: string, input: ProductCategoryInput): Observable<ProductCategoryRecord | null> {
    let updated: ProductCategoryRecord | null = null;
    const next = this.categories$.value.map(category => {
      if (category.id !== id) {
        return category;
      }
      updated = {
        ...category,
        ...this.normalize(input)
      };
      return updated;
    });
    if (updated) {
      this.persist(next);
      this.categories$.next(this.sorted(next));
    }
    return of(updated);
  }

  setStatus(id: string, status: ProductCategoryStatus): Observable<ProductCategoryRecord | null> {
    const current = this.categories$.value.find(category => category.id === id);
    if (!current) {
      return of(null);
    }
    return this.update(id, {
      name: current.name,
      code: current.code,
      description: current.description,
      status
    });
  }

  private load(): ProductCategoryRecord[] {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (raw) {
        return this.mergeSeeds(JSON.parse(raw) as ProductCategoryRecord[]);
      }
    } catch {
      /* ignore */
    }
    return SEED_PRODUCT_CATEGORIES.map(category => ({ ...category }));
  }

  private mergeSeeds(saved: ProductCategoryRecord[]): ProductCategoryRecord[] {
    const byId = new Map(saved.map(category => [category.id, category]));
    SEED_PRODUCT_CATEGORIES.forEach(seed => {
      if (!byId.has(seed.id)) {
        byId.set(seed.id, seed);
      }
    });
    return this.sorted([...byId.values()].map(category => this.normalizeRecord(category)));
  }

  private sorted(categories: ProductCategoryRecord[]): ProductCategoryRecord[] {
    return categories.slice().sort((a, b) => a.sortOrder - b.sortOrder || a.name.localeCompare(b.name));
  }

  private normalize(input: ProductCategoryInput): ProductCategoryInput {
    return {
      name: input.name.trim(),
      code: input.code.trim().toUpperCase().replace(/\s+/g, '_'),
      description: input.description.trim(),
      status: input.status
    };
  }

  private normalizeRecord(category: ProductCategoryRecord): ProductCategoryRecord {
    return {
      ...category,
      description: category.description ?? '',
      status: category.status ?? 'Active',
      sortOrder: category.sortOrder ?? 999
    };
  }

  private persist(categories: ProductCategoryRecord[]): void {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(categories));
    } catch {
      /* ignore */
    }
  }
}
