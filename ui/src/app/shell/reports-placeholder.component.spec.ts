import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { ReportsPlaceholderComponent } from './reports-placeholder.component';
import { ReportsCatalogItem, ReportsService } from './reports.service';

describe('ReportsPlaceholderComponent', () => {
  let fixture: ComponentFixture<ReportsPlaceholderComponent>;
  let component: ReportsPlaceholderComponent;
  let reportsService: jasmine.SpyObj<ReportsService>;

  const catalog: ReportsCatalogItem[] = [
    {
      id: 'branch-sales-profit',
      label: 'Branchwise Sales & Profit',
      description: 'Sales, cost, and profit totals grouped by branch for a date range.',
      path: 'company/branch-sales-profit',
      supportsExport: true
    },
    {
      id: 'overall-sales-profit',
      label: 'Company Overall Sales & Profit',
      description: 'Overall sales, cost, and profit totals across all branches for a date range.',
      path: 'company/overall-sales-profit',
      supportsExport: true
    },
    {
      id: 'supplier-expenses',
      label: 'Supplier Expenses',
      description: 'Expenses grouped by supplier for a date range.',
      path: 'company/supplier-expenses',
      supportsExport: true
    },
    {
      id: 'branch-income',
      label: 'Branch Income',
      description: 'Income grouped by branch for a date range.',
      path: 'company/branch-income',
      supportsExport: true
    }
  ];

  beforeEach(() => {
    reportsService = jasmine.createSpyObj<ReportsService>('ReportsService', [
      'getCatalog',
      'runReport',
      'exportReport'
    ]);
    reportsService.getCatalog.and.returnValue(of(catalog));
    reportsService.runReport.and.returnValue(of([{ shop: 'Branch A', profit_total: -10, profit_percent: -5 }]));
    reportsService.exportReport.and.returnValue(of(new Blob(['a,b\n1,2'], { type: 'text/csv' })));

    TestBed.configureTestingModule({
      imports: [ReportsPlaceholderComponent, NoopAnimationsModule],
      providers: [{ provide: ReportsService, useValue: reportsService }]
    });
    fixture = TestBed.createComponent(ReportsPlaceholderComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('loads and renders the report catalog as cards', () => {
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(reportsService.getCatalog).toHaveBeenCalled();
    expect(text).toContain('Branchwise Sales & Profit');
    expect(text).toContain('Supplier Expenses');
  });

  it('runs the selected report with from/to params', () => {
    component.reportForm.patchValue({
      from: new Date('2026-04-01T00:00:00'),
      to: new Date('2026-04-10T00:00:00')
    });
    component.selectReport(catalog[0]);
    fixture.detectChanges();

    component.run();

    expect(reportsService.runReport).toHaveBeenCalled();
    const args = reportsService.runReport.calls.mostRecent().args;
    expect(args[0]).toBe('company/branch-sales-profit');
    expect(args[1]).toEqual({ from: '2026-04-01', to: '2026-04-10', groupBy: undefined });
  });

  it('shows a negative indicator for profit columns', () => {
    component.reportForm.patchValue({
      from: new Date('2026-04-01T00:00:00'),
      to: new Date('2026-04-10T00:00:00')
    });
    component.selectReport(catalog[0]);
    fixture.detectChanges();

    component.run();
    fixture.detectChanges();

    const el = fixture.nativeElement as HTMLElement;
    const profitCell = el.querySelector('.profit-cell.neg');
    expect(profitCell).withContext('profit cell should have neg class').not.toBeNull();
    expect(profitCell?.textContent ?? '').toContain('▼');
  });
});
