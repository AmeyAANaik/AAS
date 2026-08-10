import { convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';
import { BranchOpsPageComponent } from './branch-ops-page.component';

const AGING_ROW = {
  branchId: 'BRANCH-1',
  branchName: 'Downtown Branch',
  location: 'Pune',
  creditDays: 7,
  submittedOutstanding: 52353,
  notDue: 0,
  d1_7: 0,
  d8_15: 0,
  d16_30: 2206,
  d30Plus: 50147,
  overdueAmount: 52353,
  openInvoiceCount: 3,
  overdueInvoiceCount: 3,
  oldestOverdueDays: 51,
  oldestOverdueInvoiceId: 'ACC-SINV-2026-00060',
  oldestOverdueDueDate: '2026-06-12',
  draftUnbilledAmount: 909186,
  draftInvoiceCount: 55,
  oldestDraftDays: 57,
  ledgerBalance: 720934,
  unappliedCredits: 240605,
  onTimePaymentPct: 0,
  onTimePaymentSample: 39,
  onTimePaymentDenominator: 39,
  onTimeCoveragePct: 100,
  onTimeReliable: true,
  dueDateMissingCount: 0,
  riskScore: 8,
  riskScoreMax: 9,
  riskPct: 88.89,
  riskTier: 'DEFAULTER',
  riskBasis: 'FULL',
  riskReasons: ['Oldest overdue 51 days']
};

const AGING_SUMMARY = {
  asOfDate: '2026-08-02',
  asOfDateIsHistorical: false,
  buckets: [],
  coverage: { settledSubmittedInvoices: 39, referencedSettledInvoices: 39, onTimeCoveragePct: 100, onTimeReliable: true },
  totals: {},
  collectionsRanking: ['BRANCH-2', 'BRANCH-1'],
  backlogRanking: ['BRANCH-1', 'BRANCH-2'],
  branches: [AGING_ROW, { ...AGING_ROW, branchId: 'BRANCH-2', branchName: 'Uptown Branch', draftUnbilledAmount: 100 }]
};

describe('BranchOpsPageComponent', () => {
  let component: BranchOpsPageComponent;
  let branchOpsService: jasmine.SpyObj<any>;
  let router: jasmine.SpyObj<any>;

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
      'downloadBranchLedgerByCategory',
      'getAgingSummary',
      'getBranchAging',
      'downloadAgingSummary',
      'downloadBranchAging'
    ]);
    branchOpsService.getAgingSummary.and.returnValue(of(AGING_SUMMARY));
    branchOpsService.getBranchAging.and.returnValue(of({
      asOfDate: '2026-08-02',
      branch: { branchId: 'BRANCH-1', branchName: 'Downtown Branch', location: '', creditDays: 7 },
      summary: AGING_ROW,
      reconciliation: { submittedOutstanding: 52353, draftUnbilled: 909186, unappliedCredits: 240605, ledgerBalance: 720934, balanced: true },
      coverage: {},
      invoices: []
    }));
    branchOpsService.downloadAgingSummary.and.returnValue(of(new Blob(['x'])));
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

    router = jasmine.createSpyObj('Router', ['navigate']);
    component = new BranchOpsPageComponent(
      branchOpsService,
      { paramMap: of(convertToParamMap({})), queryParamMap: of(convertToParamMap({})) } as any,
      router
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
        { category: 'Aamras', amount: 15700, balance: 15700 },
        { category: 'Grocery', amount: 150838, balance: 0 },
        { category: 'All Item Groups', amount: 5 }
      ]
    }));

    (component as any).reloadLedgers();

    expect(component.ledgerOpeningBalance).toBe(10);
    expect(component.ledgerClosingBalance).toBe(-0.12);
    expect(component.ledgerCategorySummary.map(row => row.category)).toEqual(['Aamras', 'Grocery']);
    expect(component.ledgerCategorySummary.find(row => row.category === 'Grocery')?.amount).toBe(150838);
    expect(component.selectedCategoryId).toBe('Grocery');
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

  describe('aging tab', () => {
    it('loads aging once when the tab is first opened', () => {
      component.onTabChange(1);
      component.onTabChange(0);
      component.onTabChange(1);

      expect(branchOpsService.getAgingSummary).toHaveBeenCalledTimes(1);
      expect(component.aging).toBe(AGING_SUMMARY as any);
    });

    it('reflects the tab in the url query params', () => {
      component.onTabChange(1);
      expect(router.navigate.calls.mostRecent().args[1].queryParams).toEqual({ tab: 'aging' });
      expect(router.navigate.calls.mostRecent().args[1].queryParamsHandling).toBe('merge');

      component.onTabChange(0);
      expect(router.navigate.calls.mostRecent().args[1].queryParams).toEqual({ tab: null });
    });

    it('orders the two rankings independently of each other', () => {
      component.onTabChange(1);

      expect(component.collectionsRisk.map(row => row.branchId)).toEqual(['BRANCH-2', 'BRANCH-1']);
      expect(component.billingBacklog.map(row => row.branchId)).toEqual(['BRANCH-1', 'BRANCH-2']);
    });

    it('maps bucket keys and risk tiers to presentation tones', () => {
      expect(component.bucketTone('notDue')).toBe('neutral');
      expect(component.bucketTone('d1_7')).toBe('info');
      expect(component.bucketTone('d8_15')).toBe('warn');
      expect(component.bucketTone('d16_30')).toBe('warn-strong');
      expect(component.bucketTone('d30Plus')).toBe('danger');

      expect(component.riskTone('GOOD')).toBe('success');
      expect(component.riskTone('WATCH')).toBe('warning');
      expect(component.riskTone('DEFAULTER')).toBe('danger');
      expect(component.riskLabel('DEFAULTER')).toBe('Defaulter');
    });

    it('marks an unreliable on-time percentage and dashes a missing one', () => {
      expect(component.onTimeDisplay({ onTimePaymentPct: 0, onTimeReliable: true } as any)).toBe('0%');
      expect(component.onTimeDisplay({ onTimePaymentPct: 67, onTimeReliable: false } as any)).toBe('67% *');
      expect(component.onTimeDisplay({ onTimePaymentPct: null, onTimeReliable: false } as any)).toBe('—');
    });

    it('passes the applied as-of date to the export', () => {
      component.agingAsOfControl.setValue(new Date(2026, 7, 2));
      component.applyAgingAsOf();
      component.downloadAging('xlsx');

      expect(branchOpsService.downloadAgingSummary).toHaveBeenCalledWith({ asOf: '2026-08-02' }, 'xlsx');
    });

    it('surfaces a load failure without throwing', () => {
      branchOpsService.getAgingSummary.and.returnValue(throwError(() => new Error('boom')));

      component.loadAging();

      expect(component.aging).toBeNull();
      expect(component.agingErrorMessage).toBeTruthy();
    });
  });
});
