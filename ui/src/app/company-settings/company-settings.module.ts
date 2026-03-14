import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { PageHeaderComponent } from '../shared/page-header/page-header.component';
import { CompanySettingsPageComponent } from './company-settings-page.component';
import { CompanySettingsRoutingModule } from './company-settings-routing.module';

@NgModule({
  declarations: [CompanySettingsPageComponent],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    PageHeaderComponent,
    CompanySettingsRoutingModule
  ]
})
export class CompanySettingsModule {}
