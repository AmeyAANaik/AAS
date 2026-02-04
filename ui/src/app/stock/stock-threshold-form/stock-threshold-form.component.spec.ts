import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { StockThresholdFormComponent } from './stock-threshold-form.component';
import { StockView } from '../stock.model';

describe('StockThresholdFormComponent', () => {
  let component: StockThresholdFormComponent;
  let fixture: ComponentFixture<StockThresholdFormComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [StockThresholdFormComponent],
      imports: [
        ReactiveFormsModule,
        MatButtonModule,
        MatCardModule,
        MatFormFieldModule,
        MatInputModule,
        NoopAnimationsModule
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(StockThresholdFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('creates the stock threshold form component', () => {
    expect(component).toBeTruthy();
  });

  it('marks form invalid when threshold is missing', () => {
    const stockItem: StockView = {
      id: 'ITEM-1',
      name: 'Item 1',
      code: 'I1',
      quantity: 10,
      threshold: 5,
      isLow: false,
      thresholdLabel: '5',
      statusLabel: 'OK',
      raw: {}
    };
    component.stockItem = stockItem;
    component.ngOnChanges();
    component.form.patchValue({ threshold: null });
    expect(component.form.invalid).toBeTrue();
  });

  it('marks form invalid when threshold is negative', () => {
    const stockItem: StockView = {
      id: 'ITEM-2',
      name: 'Item 2',
      code: 'I2',
      quantity: 4,
      threshold: 2,
      isLow: false,
      thresholdLabel: '2',
      statusLabel: 'OK',
      raw: {}
    };
    component.stockItem = stockItem;
    component.ngOnChanges();
    component.form.patchValue({ threshold: -1 });
    expect(component.form.invalid).toBeTrue();
  });

  it('marks form valid when threshold is provided', () => {
    const stockItem: StockView = {
      id: 'ITEM-3',
      name: 'Item 3',
      code: 'I3',
      quantity: 7,
      threshold: 3,
      isLow: false,
      thresholdLabel: '3',
      statusLabel: 'OK',
      raw: {}
    };
    component.stockItem = stockItem;
    component.ngOnChanges();
    component.form.patchValue({ threshold: 4 });
    expect(component.form.valid).toBeTrue();
  });
});
