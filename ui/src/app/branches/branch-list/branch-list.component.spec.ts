import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatTableModule } from '@angular/material/table';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { RouterTestingModule } from '@angular/router/testing';
import { of } from 'rxjs';
import { EmptyStateComponent } from '../../shared/empty-state/empty-state.component';
import { PageHeaderComponent } from '../../shared/page-header/page-header.component';
import { BranchFormComponent } from '../branch-form/branch-form.component';
import { BranchService } from '../branch.service';
import { BranchListComponent } from './branch-list.component';
import { BranchFormValue } from '../branch.model';

describe('BranchListComponent', () => {
  let component: BranchListComponent;
  let fixture: ComponentFixture<BranchListComponent>;
  let branchService: jasmine.SpyObj<BranchService>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [BranchListComponent, BranchFormComponent],
      imports: [
        ReactiveFormsModule,
        RouterTestingModule,
        MatButtonModule,
        MatCardModule,
        MatFormFieldModule,
        MatIconModule,
        MatInputModule,
        MatTableModule,
        NoopAnimationsModule,
        EmptyStateComponent,
        PageHeaderComponent
      ],
      providers: [
        {
          provide: BranchService,
          useValue: jasmine.createSpyObj('BranchService', {
            listBranches: of([]),
            createBranch: of({}),
            updateBranch: of({})
          })
        }
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(BranchListComponent);
    component = fixture.componentInstance;
    branchService = TestBed.inject(BranchService) as jasmine.SpyObj<BranchService>;
    fixture.detectChanges();
  });

  it('creates the branch list component', () => {
    expect(component).toBeTruthy();
  });

  it('updates customer_name when editing an existing branch', () => {
    component.selectedBranch = {
      id: 'BR-000001',
      name: 'Old Name',
      location: '',
      whatsappGroupName: '',
      invoiceEmail: '',
      whatsappNumber: '',
      creditDays: 0,
      taxId: '',
      fssaiNo: '',
      raw: { name: 'BR-000001', customer_name: 'Old Name' }
    };

    const formValue: BranchFormValue = {
      branchName: 'New Name',
      location: 'Pune',
      whatsappGroupName: 'Ops',
      invoiceEmail: 'billing@example.com',
      whatsappNumber: '+919999999999',
      creditDays: 10,
      taxId: 'GSTIN',
      fssaiNo: 'FSSAI'
    };

    component.saveBranch(formValue);

    expect(branchService.updateBranch).toHaveBeenCalledWith(
      'BR-000001',
      jasmine.objectContaining({ customer_name: 'New Name' })
    );
  });
});
