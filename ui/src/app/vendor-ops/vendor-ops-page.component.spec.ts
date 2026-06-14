import { convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { VendorOpsPageComponent } from './vendor-ops-page.component';

describe('VendorOpsPageComponent', () => {
  let component: VendorOpsPageComponent;
  let vendorOpsService: jasmine.SpyObj<any>;

  beforeEach(() => {
    vendorOpsService = jasmine.createSpyObj('VendorOpsService', [
      'getSummary',
      'getVendorDetail',
      'getVendorOrders',
      'getVendorLedger',
      'getVendorLedgerByCategory',
      'downloadAllVendorLedgers',
      'downloadAllVendorsLedgerCategoriesSummary',
      'downloadVendorLedgerCategoriesSummary',
      'downloadVendorLedgerByCategory'
    ]);
    vendorOpsService.getSummary.and.returnValue(of({ totals: {}, vendors: [] }));
    vendorOpsService.getVendorOrders.and.returnValue(of([]));
    vendorOpsService.getVendorDetail.and.returnValue(of({
      vendor: { vendorId: 'VENDOR-1', vendorName: 'FreshHarvest', templateStatus: 'ready', lastActivity: '' },
      kpis: {},
      template: {},
      billing: { ledgerBalance: 0 },
      exceptions: {}
    }));
    vendorOpsService.getVendorLedgerByCategory.and.returnValue(of({
      openingBalance: 200,
      closingBalance: 100,
      balance: 100,
      entries: [{ voucherNo: 'PINV-1' }]
    }));

    component = new VendorOpsPageComponent(
      vendorOpsService,
      { paramMap: of(convertToParamMap({})) } as any,
      jasmine.createSpyObj('Router', ['navigate'])
    );
  });

  it('keeps settled vendor categories visible and uses overall ledger totals', () => {
    component.selectedVendor = {
      vendor: { vendorId: 'VENDOR-1', vendorName: 'FreshHarvest', templateStatus: 'ready', lastActivity: '' },
      kpis: {} as any,
      template: {} as any,
      billing: { ledgerBalance: 0 } as any,
      exceptions: {} as any
    };
    vendorOpsService.getVendorLedger.and.returnValue(of({
      openingBalance: 50,
      closingBalance: -0.12,
      balance: -0.12,
      entries: [{ voucherNo: 'LEDGER-1' }],
      categorySummary: [
        { category: 'Dairy', amount: 1000 },
        { category: 'Grocery', amount: -0.12 },
        { category: 'All Item Groups', amount: 2 }
      ]
    }));

    (component as any).reloadLedgers();

    expect(component.ledgerOpeningBalance).toBe(50);
    expect(component.ledgerClosingBalance).toBe(-0.12);
    expect(component.ledgerCategorySummary.map(row => row.category)).toEqual(['Dairy', 'Grocery']);
    expect(component.selectedCategoryId).toBe('Dairy');
    expect(component.ledgerScopeNote).toContain('Overall vendor balances');
    expect(component.displayAmount(-0.001)).toBe(0);
  });

  it('does not overwrite overall vendor totals when category drill-down loads', () => {
    component.selectedVendor = {
      vendor: { vendorId: 'VENDOR-1', vendorName: 'FreshHarvest', templateStatus: 'ready', lastActivity: '' },
      kpis: {} as any,
      template: {} as any,
      billing: { ledgerBalance: 0 } as any,
      exceptions: {} as any
    };
    component.ledgerOpeningBalance = 50;
    component.ledgerClosingBalance = 75;

    component.viewCategoryLedger('Grocery');

    expect(component.selectedCategoryId).toBe('Grocery');
    expect(component.categoryLedger.length).toBe(1);
    expect(component.ledgerOpeningBalance).toBe(50);
    expect(component.ledgerClosingBalance).toBe(75);
    expect(component.ledgerScopeNote).toContain('Category drill-down: Grocery');
  });

  it('uses category closing balance instead of activity amount for category selection', () => {
    component.selectedVendor = {
      vendor: { vendorId: 'VENDOR-1', vendorName: 'FreshHarvest', templateStatus: 'ready', lastActivity: '' },
      kpis: {} as any,
      template: {} as any,
      billing: { ledgerBalance: 0 } as any,
      exceptions: {} as any
    };
    vendorOpsService.getVendorLedger.and.returnValue(of({
      openingBalance: 0,
      closingBalance: 316930,
      balance: 316930,
      entries: [],
      categorySummary: [
        { category: 'Aamras', amount: 931165, balance: 316930 },
        { category: 'Grocery', amount: 500000, balance: 450000 }
      ]
    }));

    (component as any).reloadLedgers();

    expect(component.categorySummaryBalance(component.ledgerCategorySummary[0])).toBe(316930);
    expect(component.selectedCategoryId).toBe('Grocery');
  });

  it('defaults the vendor ledger range to seven days', () => {
    (component as any).applyDefaultLedgerRange();

    const from = component.ledgerDateRangeForm.get('from')?.value as Date;
    const to = component.ledgerDateRangeForm.get('to')?.value as Date;
    const millisPerDay = 24 * 60 * 60 * 1000;

    expect(Math.round((to.getTime() - from.getTime()) / millisPerDay)).toBe(7);
  });
});
