import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup } from '@angular/forms';
import { MatDialog } from '@angular/material/dialog';
import { finalize } from 'rxjs/operators';
import { Branch } from '../../branches/branch.model';
import { BranchService } from '../../branches/branch.service';
import { CategoryService } from '../../categories/category.service';
import { ItemService } from '../../items/item.service';
import { OrderService } from '../../orders/order.service';
import { CompanyContextService } from '../../shared/company-context.service';
import { formatUiError } from '../../shared/error-message.util';
import { VendorService } from '../../vendors/vendor.service';
import { InvoiceDeliveryDialogComponent, InvoiceDeliveryDialogResult } from '../invoice-delivery-dialog/invoice-delivery-dialog.component';
import { BillsService } from '../bills.service';
import { InvoiceFilters, InvoiceOption, InvoiceSummary, InvoiceView, ItemOption, OptionItem } from '../bills.model';

@Component({
  selector: 'app-bills-page',
  templateUrl: './bills-page.component.html',
  styleUrl: './bills-page.component.scss'
})
export class BillsPageComponent implements OnInit {
  filtersForm: FormGroup = this.fb.group({
    partyType: ['Customer'],
    customer: [''],
    from: [null],
    to: [null]
  });

  invoices: InvoiceView[] = [];
  invoiceOptions: InvoiceOption[] = [];
  customers: OptionItem[] = [];
  vendors: OptionItem[] = [];
  categories: OptionItem[] = [];
  items: ItemOption[] = [];
  orders: OptionItem[] = [];
  currentCompanyId = '';
  private customerCompanies = new Map<string, string>();
  private branchDirectory = new Map<string, Branch>();
  summary = { total: 0, paid: 0, open: 0, totalAmount: 0 };
  statusMessage = '';
  isLoading = false;
  deletingInvoiceId = '';
  private actionFeedback = new Map<string, { tone: 'success' | 'error'; message: string }>();

  constructor(
    private fb: FormBuilder,
    private billsService: BillsService,
    private branchService: BranchService,
    private categoryService: CategoryService,
    private itemService: ItemService,
    private orderService: OrderService,
    private vendorService: VendorService,
    private companyContextService: CompanyContextService,
    private readonly dialog: MatDialog
  ) {
    const today = new Date();
    this.filtersForm.patchValue({ from: today, to: today });
  }

  ngOnInit(): void {
    this.loadCompanyContext();
    this.loadReferenceData();
    this.loadInvoices();
  }

  get isSupplierInvoiceMode(): boolean {
    return String(this.filtersForm.get('partyType')?.value ?? '').toLowerCase() === 'supplier';
  }

  get partyFilterLabel(): string {
    return this.isSupplierInvoiceMode ? 'Vendor' : 'Branch';
  }

  get partyFilterOptions(): OptionItem[] {
    return this.isSupplierInvoiceMode ? this.vendors : this.customers;
  }

  onInvoicePartyTypeChange(): void {
    const partyType = this.isSupplierInvoiceMode ? 'Supplier' : 'Customer';
    this.filtersForm.patchValue({ partyType, customer: '' }, { emitEvent: false });
  }

  loadReferenceData(): void {
    this.branchService.listBranches().subscribe({
      next: branches => {
        this.branchDirectory = this.buildBranchDirectory(branches ?? []);
        this.customers = (branches ?? []).map(branch => {
          const name = String(branch.customer_name ?? branch.name ?? '').trim();
          const id = String(branch.name ?? name);
          return {
            id,
            name: name || String(branch.name ?? ''),
            company: this.customerCompanies.get(id) || this.currentCompanyId || undefined
          };
        });
        this.invoices = this.invoices.map(invoice => this.withDeliveryConfig(invoice));
      }
    });

    this.itemService.listItems().subscribe({
      next: items => {
        this.items = (items ?? []).map(item => ({
          id: String(item.name ?? item.item_code ?? ''),
          name: String(item.item_name ?? item.name ?? ''),
          code: String(item.item_code ?? item.name ?? ''),
          unit: String(item.stock_uom ?? ''),
          category: String(item.item_group ?? '')
        }));
      }
    });

    this.orderService.listOrders({}).subscribe({
      next: orders => {
        this.customerCompanies.clear();
        (orders ?? []).forEach(order => {
          const customerId = String(order.customer ?? '').trim();
          const company = String(order.company ?? '').trim();
          if (customerId && company && !this.customerCompanies.has(customerId)) {
            this.customerCompanies.set(customerId, company);
          }
        });
        this.orders = (orders ?? []).map(order => {
          const name = String(order.name ?? '').trim();
          return { id: name, name, company: String(order.company ?? '').trim() || undefined };
        });
        this.customers = this.customers.map(customer => ({
          ...customer,
          company: customer.company || this.customerCompanies.get(customer.id) || this.currentCompanyId || undefined
        }));
      }
    });

    this.vendorService.listVendors().subscribe({
      next: vendors => {
        this.vendors = (vendors ?? []).map(vendor => {
          const id = String(vendor.name ?? '').trim();
          const name = String(vendor.supplier_name ?? vendor.name ?? '').trim();
          return {
            id,
            name: name || id,
            categoryId: String(vendor.aas_category ?? '').trim() || undefined
          };
        });
      }
    });

    this.categoryService.listCategories().subscribe({
      next: categories => {
        this.categories = (categories ?? []).map(category => {
          const id = String(category.name ?? '').trim();
          const name = String(category.item_group_name ?? category.name ?? '').trim();
          return { id, name: name || id };
        });
      }
    });
  }

  private loadCompanyContext(): void {
    this.companyContextService.getContext().subscribe({
      next: context => {
        this.currentCompanyId = String(context?.company?.id ?? '').trim();
        if (!this.currentCompanyId) {
          return;
        }
        this.customers = this.customers.map(customer => ({
          ...customer,
          company: customer.company || this.currentCompanyId || undefined
        }));
      }
    });
  }

  loadInvoices(): void {
    this.isLoading = true;
    const raw = this.filtersForm.getRawValue() as { partyType?: string; customer?: string; from?: unknown; to?: unknown };
    const filters: InvoiceFilters = {
      partyType: raw.partyType || undefined,
      partyId: raw.customer || undefined,
      from: this.formatApiDate(raw.from),
      to: this.formatApiDate(raw.to)
    };
    this.billsService
      .listInvoices(filters)
      .pipe(finalize(() => (this.isLoading = false)))
      .subscribe({
        next: invoices => {
          this.invoices = (invoices ?? []).map(invoice => this.toViewModel(invoice));
          this.invoiceOptions = this.invoices
            .filter(invoice => invoice.status.toLowerCase() !== 'paid')
            .map(invoice => {
              const outstanding = Number(invoice.raw.outstanding_amount ?? invoice.raw.grand_total ?? 0);
            return {
              id: invoice.id,
              name: `${invoice.id} • ${invoice.customer} • Due ${outstanding.toFixed(2)}`,
              customer: invoice.customer,
              company: invoice.company,
              outstanding
            };
          });
          this.summary = this.buildSummary(this.invoices);
          this.statusMessage = '';
        },
        error: err => {
          this.statusMessage = this.formatError(err, 'Unable to load invoices');
        }
      });
  }

  downloadPdf(invoice: InvoiceView): void {
    if (!invoice?.id) {
      return;
    }
    this.billsService.downloadInvoicePdf(invoice.id, this.filtersForm.get('partyType')?.value || 'Customer').subscribe({
      next: blob => {
        if (!blob) {
          return;
        }
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = `${invoice.id}.pdf`;
        link.click();
        window.URL.revokeObjectURL(url);
      },
      error: err => {
        this.statusMessage = this.formatError(err, 'Unable to download invoice PDF');
      }
    });
  }

  deleteInvoice(invoice: InvoiceView): void {
    if (!invoice?.id || !this.canDelete(invoice)) {
      return;
    }
    const confirmed = window.confirm(`Delete invoice "${invoice.id}"? Only draft invoices can be removed.`);
    if (!confirmed) {
      return;
    }
    this.deletingInvoiceId = invoice.id;
    this.statusMessage = '';
    this.billsService
      .deleteInvoice(invoice.id)
      .pipe(finalize(() => (this.deletingInvoiceId = '')))
      .subscribe({
        next: () => {
          this.setActionFeedback(invoice.id, 'success', 'Invoice deleted.');
          this.statusMessage = 'Invoice deleted.';
          this.loadInvoices();
        },
        error: err => {
          const message = this.formatError(err, 'Unable to delete invoice');
          this.setActionFeedback(invoice.id, 'error', message);
          this.statusMessage = message;
        }
      });
  }

  openDeliveryDialog(invoice: InvoiceView, channel: 'email' | 'whatsapp'): void {
    if ((channel === 'email' && !invoice.emailConfigured) || (channel === 'whatsapp' && !invoice.whatsappConfigured)) {
      return;
    }
    const dialogRef = this.dialog.open<InvoiceDeliveryDialogComponent, { invoice: InvoiceView; channel: 'email' | 'whatsapp' }, InvoiceDeliveryDialogResult>(
      InvoiceDeliveryDialogComponent,
      {
        width: '640px',
        maxWidth: '92vw',
        data: { invoice, channel },
        autoFocus: false
      }
    );
    dialogRef.afterClosed().subscribe(result => {
      if (!result?.sent) {
        return;
      }
      this.setActionFeedback(invoice.id, 'success', result.message || `${channel === 'email' ? 'Email' : 'WhatsApp'} sent.`);
    });
  }

  downloadCsv(): void {
    const raw = this.filtersForm.getRawValue() as { partyType?: string; customer?: string; from?: unknown; to?: unknown };
    const filters: InvoiceFilters = {
      partyType: raw.partyType || undefined,
      partyId: raw.customer || undefined,
      from: this.formatApiDate(raw.from),
      to: this.formatApiDate(raw.to)
    };
    this.billsService.exportInvoices(filters).subscribe({
      next: blob => {
        if (!blob) {
          return;
        }
        const url = window.URL.createObjectURL(blob);
        const link = document.createElement('a');
        link.href = url;
        link.download = 'invoices.csv';
        link.click();
        window.URL.revokeObjectURL(url);
      },
      error: err => {
        this.statusMessage = this.formatError(err, 'Unable to export invoices');
      }
    });
  }

  onInvoiceCreated(): void {
    this.loadInvoices();
  }

  onPaymentCreated(): void {
    this.loadInvoices();
  }

  canDelete(invoice: InvoiceView): boolean {
    if (this.isSupplierInvoiceMode) {
      return false;
    }
    return !!invoice.id;
  }

  isDeleting(invoice: InvoiceView): boolean {
    return this.deletingInvoiceId === invoice.id;
  }

  canSendEmail(invoice: InvoiceView): boolean {
    return invoice.emailConfigured;
  }

  canSendWhatsApp(invoice: InvoiceView): boolean {
    return invoice.whatsappConfigured;
  }

  getActionFeedback(invoice: InvoiceView): { tone: 'success' | 'error'; message: string } | null {
    return this.actionFeedback.get(invoice.id) ?? null;
  }

  getStatusPillClass(invoice: InvoiceView): string {
    if (invoice.statusTone === 'success') {
      return 'pill pill-success';
    }
    if (invoice.statusTone === 'warning') {
      return 'pill pill-warning';
    }
    if (invoice.statusTone === 'info') {
      return 'pill pill-info';
    }
    return 'pill pill-neutral';
  }

  private toViewModel(invoice: InvoiceSummary): InvoiceView {
    const status = String(invoice.status ?? '').trim() || 'Pending';
    return this.withDeliveryConfig({
      id: String(invoice.name ?? '').trim(),
      customer: String(invoice.customer ?? '').trim() || 'Unknown',
      company: String(invoice.company ?? '').trim(),
      date: String(invoice.posting_date ?? '').trim(),
      totalLabel: this.resolveTotalLabel(invoice.grand_total),
      status,
      statusTone: this.resolveStatusTone(status),
      emailConfigured: false,
      emailRecipient: '',
      whatsappConfigured: false,
      whatsappRecipient: '',
      raw: invoice
    });
  }

  private resolveTotalLabel(total: number | undefined): string {
    if (total === null || total === undefined) {
      return 'Pending';
    }
    const value = Number(total);
    if (!Number.isFinite(value) || value <= 0) {
      return 'Pending';
    }
    return value.toFixed(2);
  }

  private resolveStatusTone(status: string): 'neutral' | 'success' | 'warning' | 'info' {
    if (status.toLowerCase() === 'paid') {
      return 'success';
    }
    if (status.toLowerCase() === 'overdue') {
      return 'warning';
    }
    if (status.toLowerCase() === 'draft') {
      return 'info';
    }
    return 'neutral';
  }

  private setActionFeedback(invoiceId: string, tone: 'success' | 'error', message: string): void {
    if (!invoiceId || !message) {
      return;
    }
    this.actionFeedback.set(invoiceId, { tone, message });
  }

  private buildBranchDirectory(branches: Branch[]): Map<string, Branch> {
    const directory = new Map<string, Branch>();
    for (const branch of branches) {
      const keys = [String(branch.name ?? '').trim(), String(branch.customer_name ?? '').trim()].filter(Boolean);
      for (const key of keys) {
        if (!directory.has(key)) {
          directory.set(key, branch);
        }
      }
    }
    return directory;
  }

  private withDeliveryConfig(invoice: InvoiceView): InvoiceView {
    const branch = this.branchDirectory.get(invoice.customer) ?? this.branchDirectory.get(String(invoice.raw.customer ?? '').trim());
    const emailRecipient = String(branch?.aas_invoice_email ?? '').trim();
    const whatsappRecipient = String(branch?.aas_whatsapp_number ?? branch?.aas_whatsapp_group_name ?? '').trim();
    return {
      ...invoice,
      emailConfigured: !!emailRecipient,
      emailRecipient,
      whatsappConfigured: !!whatsappRecipient,
      whatsappRecipient
    };
  }

  private buildSummary(invoices: InvoiceView[]): { total: number; paid: number; open: number; totalAmount: number } {
    const total = invoices.length;
    const paid = invoices.filter(invoice => invoice.status.toLowerCase() === 'paid').length;
    const open = invoices.filter(invoice => invoice.status.toLowerCase() !== 'paid').length;
    const totalAmount = invoices.reduce((sum, invoice) => sum + (Number(invoice.raw.grand_total) || 0), 0);
    return { total, paid, open, totalAmount };
  }

  private formatApiDate(value: unknown): string | undefined {
    if (!value) {
      return undefined;
    }
    if (value instanceof Date && !Number.isNaN(value.getTime())) {
      const year = value.getFullYear();
      const month = String(value.getMonth() + 1).padStart(2, '0');
      const day = String(value.getDate()).padStart(2, '0');
      return `${year}-${month}-${day}`;
    }
    const text = String(value).trim();
    return text || undefined;
  }

  private formatError(err: unknown, fallback: string): string {
    return formatUiError(err, fallback);
  }
}
