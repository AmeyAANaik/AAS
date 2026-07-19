import { Injectable } from '@angular/core';
import { BehaviorSubject, Observable, of } from 'rxjs';
import { BranchInput, BranchRecord } from './branch.model';

const STORAGE_KEY = 'franchise.branches.v1';

export const SEED_BRANCHES: BranchRecord[] = [
  {
    id: 'branch-aundh',
    name: 'Sukhkarta — Aundh',
    code: 'AUNDH',
    fssaiNumber: '11526000000001',
    gstNumber: '27ABCDE1234F1Z5',
    tanNumber: 'PNEA12345B',
    city: 'Pune',
    area: 'Aundh',
    address: 'Aundh, Pune',
    managerName: 'Branch Manager',
    phone: '9876543210',
    email: 'aundh@sukhkarta.app',
    contactNumber: '020-40000001',
    accountHolderName: 'Sukhkarta Aundh',
    bankName: 'HDFC Bank',
    bankAccountNumber: '501000000001',
    ifscCode: 'HDFC0000001',
    openingDate: '2026-01-01',
    status: 'Active'
  },
  {
    id: 'branch-baner',
    name: 'Sukhkarta — Baner',
    code: 'BANER',
    fssaiNumber: '11526000000002',
    gstNumber: '27ABCDE1234F1Z6',
    tanNumber: 'PNEA12346B',
    city: 'Pune',
    area: 'Baner',
    address: 'Baner, Pune',
    managerName: 'Branch Manager',
    phone: '9876543211',
    email: 'baner@sukhkarta.app',
    contactNumber: '020-40000002',
    accountHolderName: 'Sukhkarta Baner',
    bankName: 'HDFC Bank',
    bankAccountNumber: '501000000002',
    ifscCode: 'HDFC0000002',
    openingDate: '2026-01-01',
    status: 'Active'
  },
  {
    id: 'branch-wakad',
    name: 'Sukhkarta — Wakad',
    code: 'WAKAD',
    fssaiNumber: '11526000000003',
    gstNumber: '27ABCDE1234F1Z7',
    tanNumber: 'PNEA12347B',
    city: 'Pune',
    area: 'Wakad',
    address: 'Wakad, Pune',
    managerName: 'Branch Manager',
    phone: '9876543212',
    email: 'wakad@sukhkarta.app',
    contactNumber: '020-40000003',
    accountHolderName: 'Sukhkarta Wakad',
    bankName: 'HDFC Bank',
    bankAccountNumber: '501000000003',
    ifscCode: 'HDFC0000003',
    openingDate: '2026-01-01',
    status: 'Active'
  }
];

@Injectable({ providedIn: 'root' })
export class BranchStoreService {
  private readonly branches$: BehaviorSubject<BranchRecord[]>;
  readonly changes$: Observable<BranchRecord[]>;

  constructor() {
    this.branches$ = new BehaviorSubject<BranchRecord[]>(this.load());
    this.changes$ = this.branches$.asObservable();
  }

  list(): Observable<BranchRecord[]> {
    return of(this.listSnapshot());
  }

  listSnapshot(): BranchRecord[] {
    return this.branches$.value.map(branch => ({ ...branch }));
  }

  branchNamesSnapshot(): string[] {
    return this.branches$.value.map(branch => branch.name);
  }

  create(input: BranchInput): Observable<BranchRecord> {
    const branch: BranchRecord = {
      ...input,
      id: `branch-${Date.now()}-${Math.random().toString(36).slice(2, 7)}`,
      ...this.normalizeInput(input)
    };
    const next = [...this.branches$.value, branch];
    this.persist(next);
    this.branches$.next(next);
    return of({ ...branch });
  }

  update(id: string, input: BranchInput): Observable<BranchRecord | null> {
    let updated: BranchRecord | null = null;
    const next = this.branches$.value.map(branch => {
      if (branch.id !== id) {
        return branch;
      }
      updated = {
        ...branch,
        ...this.normalizeInput(input)
      };
      return updated;
    });
    if (updated) {
      this.persist(next);
      this.branches$.next(next);
    }
    return of(updated);
  }

  delete(id: string): Observable<void> {
    const next = this.branches$.value.filter(branch => branch.id !== id);
    this.persist(next);
    this.branches$.next(next);
    return of(void 0);
  }

  private load(): BranchRecord[] {
    try {
      const raw = localStorage.getItem(STORAGE_KEY);
      if (raw) {
        const saved = JSON.parse(raw) as BranchRecord[];
        return this.mergeSeeds(saved);
      }
    } catch {
      /* ignore */
    }
    return SEED_BRANCHES.map(branch => ({ ...branch }));
  }

  private mergeSeeds(saved: BranchRecord[]): BranchRecord[] {
    const byId = new Map(saved.map(branch => [branch.id, branch]));
    SEED_BRANCHES.forEach(seed => {
      if (!byId.has(seed.id)) {
        byId.set(seed.id, seed);
      }
    });
    return [...byId.values()].map(branch => this.normalizeRecord(branch));
  }

  private persist(branches: BranchRecord[]): void {
    try {
      localStorage.setItem(STORAGE_KEY, JSON.stringify(branches));
    } catch {
      /* ignore */
    }
  }

  private normalizeInput(input: BranchInput): BranchInput {
    return {
      name: input.name.trim(),
      code: input.code.trim().toUpperCase(),
      fssaiNumber: input.fssaiNumber.trim(),
      gstNumber: input.gstNumber.trim().toUpperCase(),
      tanNumber: input.tanNumber.trim().toUpperCase(),
      city: input.city.trim(),
      area: input.area.trim(),
      address: input.address.trim(),
      managerName: input.managerName.trim(),
      phone: input.phone.trim(),
      email: input.email.trim().toLowerCase(),
      contactNumber: input.contactNumber.trim(),
      accountHolderName: input.accountHolderName.trim(),
      bankName: input.bankName.trim(),
      bankAccountNumber: input.bankAccountNumber.trim(),
      ifscCode: input.ifscCode.trim().toUpperCase(),
      openingDate: input.openingDate,
      status: input.status
    };
  }

  private normalizeRecord(branch: BranchRecord): BranchRecord {
    const seed = SEED_BRANCHES.find(row => row.id === branch.id);
    return {
      ...branch,
      fssaiNumber: branch.fssaiNumber ?? seed?.fssaiNumber ?? '',
      gstNumber: branch.gstNumber ?? seed?.gstNumber ?? '',
      tanNumber: branch.tanNumber ?? seed?.tanNumber ?? '',
      email: branch.email ?? seed?.email ?? '',
      contactNumber: branch.contactNumber ?? seed?.contactNumber ?? branch.phone ?? '',
      accountHolderName: branch.accountHolderName ?? seed?.accountHolderName ?? branch.name ?? '',
      bankName: branch.bankName ?? seed?.bankName ?? '',
      bankAccountNumber: branch.bankAccountNumber ?? seed?.bankAccountNumber ?? '',
      ifscCode: branch.ifscCode ?? seed?.ifscCode ?? ''
    };
  }
}
