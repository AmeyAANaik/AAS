import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatCardModule } from '@angular/material/card';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { BranchFormComponent } from './branch-form.component';

describe('BranchFormComponent', () => {
  let component: BranchFormComponent;
  let fixture: ComponentFixture<BranchFormComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [BranchFormComponent],
      imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, MatCardModule, NoopAnimationsModule]
    }).compileComponents();

    fixture = TestBed.createComponent(BranchFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('marks form invalid when branch name is missing', () => {
    component.form.setValue({ branchName: '', location: '', whatsappGroupName: '', invoiceEmail: '', whatsappNumber: '', creditDays: 0 });
    expect(component.form.invalid).toBeTrue();
  });

  it('marks form valid when branch name is provided', () => {
    component.form.setValue({ branchName: 'Branch A', location: '', whatsappGroupName: '', invoiceEmail: 'billing@example.com', whatsappNumber: '+919405925917', creditDays: 0 });
    expect(component.form.valid).toBeTrue();
  });
});
