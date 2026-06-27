import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import { delay } from 'rxjs/operators';
import { Vendor, VendorView } from './vendor.model';

const KEY = 'franchise.vendors.v2';
const LATENCY = 120;

// Vendor categories are aligned to the product taxonomy so a chosen category
// resolves to its supplying vendors (Raw Material / Dairy / Utility / Vegetable).
const SEED: Vendor[] = [
  { id: 'v1', name: 'Corner Kirana', code: 'VEN-001', category: 'Raw Material', phone: '98200 11111', status: 'Active', totalPurchased: 84500, totalPaid: 60000 },
  { id: 'v2', name: 'Sharma Dairy', code: 'VEN-002', category: 'Dairy', phone: '98200 22222', status: 'Active', totalPurchased: 52300, totalPaid: 52300 },
  { id: 'v3', name: 'Bharat Gas Agency', code: 'VEN-003', category: 'Utility', phone: '98200 33333', status: 'Active', totalPurchased: 33000, totalPaid: 22000 },
  { id: 'v4', name: 'Fresh Veggies Mandi', code: 'VEN-004', category: 'Vegetable', phone: '98200 44444', status: 'Active', totalPurchased: 18750, totalPaid: 15000 },
  { id: 'v5', name: 'Apex Wholesale', code: 'VEN-005', category: 'Raw Material', phone: '98200 55555', status: 'Active', totalPurchased: 41200, totalPaid: 30000 }
];

/** Mock vendor backend (localStorage). Mirrors the AAS vendor list UX. */
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
    return { ...v, outstanding: Math.max(0, Math.round((v.totalPurchased - v.totalPaid) * 100) / 100) };
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
}
