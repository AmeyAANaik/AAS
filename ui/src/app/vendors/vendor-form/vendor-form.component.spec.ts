import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { MatFormFieldModule } from '@angular/material/form-field';
import { MatInputModule } from '@angular/material/input';
import { MatSelectModule } from '@angular/material/select';
import { MatButtonModule } from '@angular/material/button';
import { MatCardModule } from '@angular/material/card';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { VendorFormComponent } from './vendor-form.component';

describe('VendorFormComponent', () => {
  let component: VendorFormComponent;
  let fixture: ComponentFixture<VendorFormComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      declarations: [VendorFormComponent],
      imports: [
        ReactiveFormsModule,
        MatFormFieldModule,
        MatInputModule,
        MatSelectModule,
        MatButtonModule,
        MatCardModule,
        NoopAnimationsModule
      ]
    }).compileComponents();

    fixture = TestBed.createComponent(VendorFormComponent);
    component = fixture.componentInstance;
    component.invoiceTemplateModel = {
      itemFields: [
        { key: 'item_name', label: 'Item name', required: true, sourceAliases: ['Item Description'] },
        { key: 'qty', label: 'Quantity', required: true, sourceAliases: ['Qty'] },
        { key: 'uom', label: 'UOM', required: false, sourceAliases: ['Unit'] }
      ],
      summaryFields: [
        { key: 'final_bill_amount', label: 'Final bill amount', required: true, sourceAliases: ['Grand Total'] }
      ],
      requiredFields: {
        items: ['item_name', 'qty'],
        summary: ['final_bill_amount']
      },
      workflow: {
        inputMode: 'native_layout_mapping',
        uiPolicy: 'Do not expose regex configuration in UI.'
      }
    };
    fixture.detectChanges();
  });

  it('marks form invalid when required fields are empty', () => {
    component.form.patchValue({ supplierName: '', priority: null, status: '' });
    expect(component.form.invalid).toBeTrue();
  });

  it('marks form valid with required fields', () => {
    component.form.patchValue({
      supplierName: 'Vendor A',
      vendorCode: 'VENDOR_A',
      category: 'Grocery',
      priority: 1,
      status: 'Inactive',
      invoiceTemplateJson: ''
    });
    expect(component.form.valid).toBeTrue();
  });

  it('shows the required field count from the invoice model', () => {
    expect(component.requiredFieldCount).toBe(3);
  });

  it('emits open setup event', () => {
    component.mode = 'edit';
    component.vendor = {
      id: 'SUP-0001',
      name: 'Vendor A',
      vendorCode: 'VENDOR_A',
      category: 'Grocery',
      priority: 1,
      status: 'Inactive',
      templateKey: '',
      templateHasJson: false,
      raw: {}
    };
    spyOn(component.openInvoiceSetup, 'emit');

    component.openSetupDialog();

    expect(component.openInvoiceSetup.emit).toHaveBeenCalled();
  });

  it('keeps vendor activation disabled until invoice setup is validated', () => {
    component.mode = 'edit';
    component.vendor = {
      id: 'SUP-0001',
      name: 'Vendor A',
      vendorCode: 'VENDOR_A',
      category: 'Grocery',
      priority: 1,
      status: 'Inactive',
      templateKey: '',
      templateHasJson: false,
      raw: {}
    };

    expect(component.canActivateVendor).toBeFalse();
    expect(component.canShowActivateAction).toBeFalse();
    expect(component.activationRequirementMessage).toContain('Validate and save');
  });

  it('allows vendor activation when a validated setup is present', () => {
    component.mode = 'edit';
    component.vendor = {
      id: 'SUP-0001',
      name: 'Vendor A',
      vendorCode: 'VENDOR_A',
      category: 'Grocery',
      priority: 1,
      status: 'Inactive',
      templateKey: '',
      templateHasJson: true,
      raw: {
        invoice_template_sample_pdf: 'http://localhost:8080/files/sample.pdf',
        invoice_template_json: JSON.stringify({
          kind: 'native_layout_mapping',
          profile: { id: 'vendor_a_native', label: 'Vendor A native layout' },
          fieldMapping: { itemMappings: [], summaryMappings: [] }
        })
      }
    };
    component.ngOnChanges({} as any);

    expect(component.canActivateVendor).toBeTrue();
    expect(component.canShowActivateAction).toBeTrue();
    expect(component.activationRequirementMessage).toContain('Use Activate vendor');
  });

  it('activates vendor through the dedicated action', () => {
    component.mode = 'edit';
    component.vendor = {
      id: 'SUP-0001',
      name: 'Vendor A',
      vendorCode: 'VENDOR_A',
      category: 'Grocery',
      priority: 1,
      status: 'Inactive',
      templateKey: '',
      templateHasJson: true,
      raw: {
        invoice_template_sample_pdf: 'http://localhost:8080/files/sample.pdf',
        invoice_template_json: JSON.stringify({
          kind: 'native_layout_mapping',
          profile: { id: 'vendor_a_native', label: 'Vendor A native layout' },
          fieldMapping: { itemMappings: [], summaryMappings: [] }
        })
      }
    };
    component.ngOnChanges({} as any);
    spyOn(component.save, 'emit');

    component.activateVendor();

    expect(component.form.get('status')?.value).toBe('Active');
    expect(component.save.emit).toHaveBeenCalled();
  });
});
