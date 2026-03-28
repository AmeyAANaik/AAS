import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatTableModule } from '@angular/material/table';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { RouterTestingModule } from '@angular/router/testing';
import { of } from 'rxjs';
import { EmptyStateComponent } from '../../shared/empty-state/empty-state.component';
import { PageHeaderComponent } from '../../shared/page-header/page-header.component';
import { StatusPillComponent } from '../../shared/status-pill/status-pill.component';
import { StockThresholdFormComponent } from '../stock-threshold-form/stock-threshold-form.component';
import { StockService } from '../stock.service';
import { StockListComponent } from './stock-list.component';

describe('StockListComponent', () => {
  let component: StockListComponent;
  let fixture: ComponentFixture<StockListComponent>;
  let stockService: jasmine.SpyObj<StockService>;

  beforeEach(async () => {
    stockService = jasmine.createSpyObj('StockService', ['listStockItems', 'saveThreshold']);
    stockService.listStockItems.and.returnValue(
      of([
        {
          name: 'ITEM-1',
          item_name: 'Item One',
          item_code: 'ITM-1',
          aas_vendor: 'Vendor One',
          stock_qty: 2,
          threshold: 5
        },
        {
          name: 'ITEM-2',
          item_name: 'Item Two',
          item_code: 'ITM-2',
          aas_vendor: 'Vendor One',
          stock_qty: 8
        },
        {
          name: 'ITEM-3',
          item_name: 'Item Three',
          item_code: 'ITM-3',
          aas_vendor: 'Vendor Two',
          stock_qty: 4
        }
      ])
    );

    await TestBed.configureTestingModule({
      declarations: [StockListComponent, StockThresholdFormComponent],
      imports: [
        ReactiveFormsModule,
        RouterTestingModule,
        MatButtonModule,
        MatCardModule,
        MatFormFieldModule,
        MatInputModule,
        MatTableModule,
        NoopAnimationsModule,
        EmptyStateComponent,
        PageHeaderComponent,
        StatusPillComponent
      ],
      providers: [{ provide: StockService, useValue: stockService }]
    }).compileComponents();

    fixture = TestBed.createComponent(StockListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('creates the stock list component', () => {
    expect(component).toBeTruthy();
  });

  it('loads stock items on init', () => {
    expect(stockService.listStockItems).toHaveBeenCalled();
  });

  it('marks low stock when quantity is below threshold', () => {
    expect(component.stockItems[0].isLow).toBeTrue();
  });

  it('saves thresholds via the stock service', () => {
    component.saveThreshold({ itemId: 'ITEM-1', threshold: 3 });
    expect(stockService.saveThreshold).toHaveBeenCalledWith('ITEM-1', 3);
  });

  it('groups stock items vendor-wise', () => {
    expect(component.vendorGroups.length).toBe(2);
    expect(component.vendorGroups[0].vendorName).toBe('Vendor One');
    expect(component.vendorGroups[0].itemCount).toBe(2);
    expect(component.vendorGroups[0].itemNames).toContain('Item One');
    expect(component.vendorGroups[0].itemNames).toContain('Item Two');
  });
});
