import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { VendorFormComponent } from './vendor-form.component';

describe('VendorFormComponent', () => {
  let component: VendorFormComponent;
  let fixture: ComponentFixture<VendorFormComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [VendorFormComponent],
      imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatSelectModule, NoopAnimationsModule]
    }).compileComponents();

    fixture = TestBed.createComponent(VendorFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('marks form invalid when required fields are empty', () => {
    component.form.setValue({ supplierName: '', priority: null, status: '' });
    expect(component.form.invalid).toBeTrue();
  });

  it('marks form valid with required fields', () => {
    component.form.setValue({ supplierName: 'Vendor A', priority: 1, status: 'Active' });
    expect(component.form.valid).toBeTrue();
  });
});
