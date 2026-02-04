import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatTableModule } from '@angular/material/table';
import { EmptyStateComponent } from '../shared/empty-state/empty-state.component';
import { PageHeaderComponent } from '../shared/page-header/page-header.component';
import { BranchFormComponent } from './branch-form/branch-form.component';
import { BranchListComponent } from './branch-list/branch-list.component';
import { BranchesRoutingModule } from './branches-routing.module';

@NgModule({
  declarations: [BranchListComponent, BranchFormComponent],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    MatTableModule,
    PageHeaderComponent,
    EmptyStateComponent,
    BranchesRoutingModule
  ]
})
export class BranchesModule {}
