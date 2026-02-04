import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatTableModule } from '@angular/material/table';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
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
          stock_qty: 2,
          threshold: 5
        }
      ])
    );

    await TestBed.configureTestingModule({
      declarations: [StockListComponent, StockThresholdFormComponent],
      imports: [
        ReactiveFormsModule,
        MatButtonModule,
        MatCardModule,
        MatFormFieldModule,
        MatInputModule,
        MatTableModule,
        NoopAnimationsModule
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
});
