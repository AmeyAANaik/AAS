import { CommonModule } from '@angular/common';
import { Component } from '@angular/core';
import { MatSlideToggleModule } from '@angular/material/slide-toggle';
import { MatIconModule } from '@angular/material/icon';
import { MatSnackBar, MatSnackBarModule } from '@angular/material/snack-bar';
import { PageHeaderComponent } from '../../shared/page-header/page-header.component';
import { ModuleConfigService } from '../../core/module-config.service';
import { TOGGLEABLE_MODULES, ModuleDef } from '../../core/rbac';

@Component({
  selector: 'app-module-settings',
  standalone: true,
  imports: [CommonModule, MatSlideToggleModule, MatIconModule, MatSnackBarModule, PageHeaderComponent],
  templateUrl: './module-settings.component.html',
  styleUrl: './module-settings.component.css'
})
export class ModuleSettingsComponent {
  readonly modules: ModuleDef[] = TOGGLEABLE_MODULES;

  constructor(private moduleConfig: ModuleConfigService, private snack: MatSnackBar) {}

  isEnabled(key: string): boolean {
    return this.moduleConfig.isModuleEnabled(key);
  }

  toggle(module: ModuleDef, enabled: boolean): void {
    this.moduleConfig.setModuleEnabled(module.key, enabled);
    this.snack.open(`${module.label} ${enabled ? 'enabled' : 'disabled'}`, 'OK', { duration: 2000 });
  }
}
