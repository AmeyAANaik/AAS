import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { VendorFormComponent } from './vendor-form.component';

describe('VendorFormComponent', () => {
  let component: VendorFormComponent;
  let fixture: ComponentFixture<VendorFormComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [VendorFormComponent],
      imports: [
        ReactiveFormsModule,
        MatFormFieldModule,
        MatInputModule,
        MatSelectModule,
        MatButtonModule,
        MatCardModule,
        NoopAnimationsModule
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(VendorFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('marks form invalid when required fields are empty', () => {
    component.form.patchValue({ supplierName: '', priority: null, status: '' });
    expect(component.form.invalid).toBeTrue();
  });

  it('marks form valid with required fields', () => {
    component.form.patchValue({
      supplierName: 'Vendor A',
      priority: 1,
      status: 'Active',
      invoiceTemplateJson: '{"targetSchema":{"items":[]},"parser":{"version":1,"itemLineRegex":"(?<name>item)\\\\s+(?<item_id>1234)\\\\s+(?<qty>1)\\\\s+(?<rate>10)\\\\s+(?<gst>5)\\\\s+(?<total>10)"}}'
    });
    expect(component.form.valid).toBeTrue();
  });
});
