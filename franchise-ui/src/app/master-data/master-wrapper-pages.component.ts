import { Component } from '@angular/core';
import { PageHeaderComponent } from '../shared/page-header/page-header.component';
import { EmployeeListComponent } from '../salary/employee-list/employee-list.component';
import { PaymentModesComponent } from '../sales/payment-modes/payment-modes.component';
import { RoyaltyConfigComponent } from '../royalty/royalty-config/royalty-config.component';

@Component({
  selector: 'app-employee-master-page',
  standalone: true,
  imports: [PageHeaderComponent, EmployeeListComponent],
  template: `
    <div class="berry-page">
      <app-page-header kicker="Master Data" title="Employee Master" subtitle="Maintain staff records used for monthly salary generation."></app-page-header>
      <app-employee-list></app-employee-list>
    </div>
  `
})
export class EmployeeMasterPageComponent {}

@Component({
  selector: 'app-payment-mode-master-page',
  standalone: true,
  imports: [PageHeaderComponent, PaymentModesComponent],
  template: `
    <div class="berry-page">
      <app-page-header kicker="Master Data" title="Payment Mode Master" subtitle="Control payment channels available during daily sales entry."></app-page-header>
      <app-payment-modes></app-payment-modes>
    </div>
  `
})
export class PaymentModeMasterPageComponent {}

@Component({
  selector: 'app-royalty-config-master-page',
  standalone: true,
  imports: [PageHeaderComponent, RoyaltyConfigComponent],
  template: `
    <div class="berry-page">
      <app-page-header kicker="Master Data" title="Royalty Setup" subtitle="Set the royalty percentage used when generating future dues."></app-page-header>
      <app-royalty-config></app-royalty-config>
    </div>
  `
})
export class RoyaltyConfigMasterPageComponent {}
