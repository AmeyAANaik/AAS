import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatTableModule } from '@angular/material/table';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { of } from 'rxjs';
import { BranchFormComponent } from '../branch-form/branch-form.component';
import { BranchService } from '../branch.service';
import { BranchListComponent } from './branch-list.component';

describe('BranchListComponent', () => {
  let component: BranchListComponent;
  let fixture: ComponentFixture<BranchListComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [BranchListComponent, BranchFormComponent],
      imports: [
        ReactiveFormsModule,
        MatButtonModule,
        MatCardModule,
        MatFormFieldModule,
        MatInputModule,
        MatTableModule,
        NoopAnimationsModule
      ],
      providers: [
        {
          provide: BranchService,
          useValue: { listBranches: () => of([]), createBranch: () => of({}), saveMetadata: () => undefined }
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(BranchListComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('creates the branch list component', () => {
    expect(component).toBeTruthy();
  });
});
