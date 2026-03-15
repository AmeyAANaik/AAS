import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatIconModule } from '@angular/material/icon';
import { MatInputModule } from '@angular/material/input';
import { MatTableModule } from '@angular/material/table';
import { EmptyStateComponent } from '../shared/empty-state/empty-state.component';
import { PageHeaderComponent } from '../shared/page-header/page-header.component';
import { StatusPillComponent } from '../shared/status-pill/status-pill.component';
import { BranchOpsPageComponent } from './branch-ops-page.component';
import { BranchOpsRoutingModule } from './branch-ops-routing.module';

@NgModule({
  declarations: [BranchOpsPageComponent],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatIconModule,
    MatInputModule,
    MatTableModule,
    EmptyStateComponent,
    PageHeaderComponent,
    StatusPillComponent,
    BranchOpsRoutingModule
  ]
})
export class BranchOpsModule {}
