import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { BranchFormComponent } from './branch-form.component';

describe('BranchFormComponent', () => {
  let component: BranchFormComponent;
  let fixture: ComponentFixture<BranchFormComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [BranchFormComponent],
      imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, NoopAnimationsModule]
    }).compileComponents();

    fixture = TestBed.createComponent(BranchFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('marks form invalid when branch name is missing', () => {
    component.form.setValue({ branchName: '', location: '', whatsappGroupName: '' });
    expect(component.form.invalid).toBeTrue();
  });

  it('marks form valid when branch name is provided', () => {
    component.form.setValue({ branchName: 'Branch A', location: '', whatsappGroupName: '' });
    expect(component.form.valid).toBeTrue();
  });
});
