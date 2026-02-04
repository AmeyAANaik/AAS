import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { CategoryFormComponent } from './category-form.component';

describe('CategoryFormComponent', () => {
  let component: CategoryFormComponent;
  let fixture: ComponentFixture<CategoryFormComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [CategoryFormComponent],
      imports: [ReactiveFormsModule, MatFormFieldModule, MatInputModule, NoopAnimationsModule]
    }).compileComponents();

    fixture = TestBed.createComponent(CategoryFormComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('marks form invalid when category name is missing', () => {
    component.form.setValue({ categoryName: '' });
    expect(component.form.invalid).toBeTrue();
  });

  it('marks form valid when category name is provided', () => {
    component.form.setValue({ categoryName: 'Category A' });
    expect(component.form.valid).toBeTrue();
  });
});
