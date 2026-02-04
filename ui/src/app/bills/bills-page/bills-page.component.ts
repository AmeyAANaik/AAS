import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup } from '@angular/forms';
import { finalize } from 'rxjs/operators';
import { BranchService } from '../../branches/branch.service';
import { ItemService } from '../../items/item.service';
import { OrderService } from '../../orders/order.service';
import { BillsService } from '../bills.service';
import { InvoiceFilters, InvoiceSummary, InvoiceView, ItemOption, OptionItem } from '../bills.model';

@Component({
  selector: 'app-bills-page',
  templateUrl: './bills-page.component.html',
  styleUrl: './bills-page.component.scss'
})
export class BillsPageComponent implements OnInit {
  filtersForm: FormGroup = this.fb.group({
    customer: [''],
    from: [''],
    to: ['']
  });

  invoices: InvoiceView[] = [];
  customers: OptionItem[] = [];
  items: ItemOption[] = [];
  orders: OptionItem[] = [];
  summary = { total: 0, paid: 0, open: 0, totalAmount: 0 };
  statusMessage = '';
  isLoading = false;

  constructor(
    private fb: FormBuilder,
    private billsService: BillsService,
    private branchService: BranchService,
    private itemService: ItemService,
    private orderService: OrderService
  ) {
    const today = this.formatDate(new Date());
    this.filtersForm.patchValue({ from: today, to: today });
  }

  ngOnInit(): void {
    this.loadReferenceData();
    this.loadInvoices();
  }

  loadReferenceData(): void {
    this.branchService.listBranches().subscribe({
      next: branches => {
        this.customers = (branches ?? []).map(branch => {
          const name = String(branch.customer_name ?? branch.name ?? '').trim();
          return { id: String(branch.name ?? name), name: name || String(branch.name ?? '') };
        });
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
        this.orders = (orders ?? []).map(order => {
          const name = String(order.name ?? '').trim();
          return { id: name, name };
        });
      }
    });
  }

  loadInvoices(): void {
    this.isLoading = true;
    const filters = this.filtersForm.getRawValue() as InvoiceFilters;
    this.billsService
      .listInvoices(filters)
      .pipe(finalize(() => (this.isLoading = false)))
      .subscribe({
        next: invoices => {
          this.invoices = (invoices ?? []).map(invoice => this.toViewModel(invoice));
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
    this.billsService.downloadInvoicePdf(invoice.id).subscribe({
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

  downloadCsv(): void {
    const filters = this.filtersForm.getRawValue() as InvoiceFilters;
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
    return {
      id: String(invoice.name ?? '').trim(),
      customer: String(invoice.customer ?? '').trim() || 'Unknown',
      date: String(invoice.posting_date ?? '').trim(),
      totalLabel: this.resolveTotalLabel(invoice.grand_total),
      status,
      statusTone: this.resolveStatusTone(status),
      raw: invoice
    };
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

  private buildSummary(invoices: InvoiceView[]): { total: number; paid: number; open: number; totalAmount: number } {
    const total = invoices.length;
    const paid = invoices.filter(invoice => invoice.status.toLowerCase() === 'paid').length;
    const open = invoices.filter(invoice => invoice.status.toLowerCase() !== 'paid').length;
    const totalAmount = invoices.reduce((sum, invoice) => sum + (Number(invoice.raw.grand_total) || 0), 0);
    return { total, paid, open, totalAmount };
  }

  private formatDate(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  private formatError(err: unknown, fallback: string): string {
    if (err instanceof Error) {
      return err.message;
    }
    if (typeof err === 'string') {
      return err;
    }
    return fallback;
  }
}
