import { convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { BranchOpsPageComponent } from './branch-ops-page.component';

describe('BranchOpsPageComponent', () => {
  let component: BranchOpsPageComponent;
  let branchOpsService: jasmine.SpyObj<any>;

  beforeEach(() => {
    branchOpsService = jasmine.createSpyObj('BranchOpsService', [
      'getSummary',
      'getBranchDetail',
      'getBranchOrders',
      'getBranchLedger',
      'getBranchLedgerByCategory',
      'downloadAllBranchLedgers',
      'downloadAllBranchesLedgerCategoriesSummary',
      'downloadBranchLedgerCategoriesSummary',
      'downloadBranchLedgerByCategory'
    ]);
    branchOpsService.getSummary.and.returnValue(of({ totals: {}, branches: [] }));
    branchOpsService.getBranchOrders.and.returnValue(of([]));
    branchOpsService.getBranchDetail.and.returnValue(of({
      branch: { branchId: 'BRANCH-1', branchName: 'Downtown Branch', location: '', creditDays: 0, lastActivity: '' },
      kpis: {},
      billing: { ledgerBalance: 0, openInvoices: 0 },
      exceptions: {}
    }));
    branchOpsService.getBranchLedgerByCategory.and.returnValue(of({
      openingBalance: 99,
      closingBalance: 88,
      balance: 0,
      entries: [{ voucherNo: 'SINV-1' }]
    }));

    component = new BranchOpsPageComponent(
      branchOpsService,
      { paramMap: of(convertToParamMap({})) } as any,
      jasmine.createSpyObj('Router', ['navigate'])
    );
  });

  it('keeps settled categories visible and uses overall ledger totals', () => {
    component.selectedBranch = {
      branch: { branchId: 'BRANCH-1', branchName: 'Downtown Branch', location: '', creditDays: 0, lastActivity: '' },
      kpis: {} as any,
      billing: { ledgerBalance: 0, openInvoices: 0 } as any,
      exceptions: {} as any
    };
    branchOpsService.getBranchLedger.and.returnValue(of({
      openingBalance: 10,
      closingBalance: -0.12,
      balance: -0.12,
      entries: [{ voucherNo: 'LEDGER-1' }],
      categorySummary: [
        { category: 'Aamras', amount: 15700 },
        { category: 'Grocery', amount: -0.12 },
        { category: 'All Item Groups', amount: 5 }
      ]
    }));

    (component as any).reloadLedgers();

    expect(component.ledgerOpeningBalance).toBe(10);
    expect(component.ledgerClosingBalance).toBe(-0.12);
    expect(component.ledgerCategorySummary.map(row => row.category)).toEqual(['Aamras', 'Grocery']);
    expect(component.selectedCategoryId).toBe('Aamras');
    expect(component.ledgerScopeNote).toContain('Overall branch balances');
    expect(component.displayAmount(-0.001)).toBe(0);
  });

  it('does not overwrite overall totals when category drill-down loads', () => {
    component.selectedBranch = {
      branch: { branchId: 'BRANCH-1', branchName: 'Downtown Branch', location: '', creditDays: 0, lastActivity: '' },
      kpis: {} as any,
      billing: { ledgerBalance: 0, openInvoices: 0 } as any,
      exceptions: {} as any
    };
    component.ledgerOpeningBalance = 10;
    component.ledgerClosingBalance = 15;

    component.viewCategoryLedger('Grocery');

    expect(component.selectedCategoryId).toBe('Grocery');
    expect(component.categoryLedger.length).toBe(1);
    expect(component.ledgerOpeningBalance).toBe(10);
    expect(component.ledgerClosingBalance).toBe(15);
    expect(component.ledgerScopeNote).toContain('Category drill-down: Grocery');
  });

  it('defaults the ledger range to seven days', () => {
    (component as any).applyDefaultLedgerRange();

    const from = component.ledgerDateRangeForm.get('from')?.value as Date;
    const to = component.ledgerDateRangeForm.get('to')?.value as Date;
    const millisPerDay = 24 * 60 * 60 * 1000;

    expect(Math.round((to.getTime() - from.getTime()) / millisPerDay)).toBe(7);
  });
});
