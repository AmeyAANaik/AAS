import { CommonModule } from '@angular/common';
import { NgModule } from '@angular/core';
import { ReactiveFormsModule } from '@angular/forms';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { PageHeaderComponent } from '../shared/page-header/page-header.component';
import { UserSettingsPageComponent } from './user-settings-page.component';
import { UserSettingsRoutingModule } from './user-settings-routing.module';

@NgModule({
  declarations: [UserSettingsPageComponent],
  imports: [
    CommonModule,
    ReactiveFormsModule,
    MatButtonModule,
    MatCardModule,
    MatFormFieldModule,
    MatInputModule,
    PageHeaderComponent,
    UserSettingsRoutingModule
  ]
})
export class UserSettingsModule {}
