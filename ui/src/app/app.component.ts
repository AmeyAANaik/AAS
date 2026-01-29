import { CommonModule } from '@angular/common';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { Component } from '@angular/core';
import { FormsModule } from '@angular/forms';

type SimpleDoc = {
  name: string;
  [key: string]: unknown;
};

type Role = 'admin' | 'vendor' | 'shop' | 'helper';

type OrderSummary = {
  name: string;
  customer?: string;
  company?: string;
  transaction_date?: string;
  delivery_date?: string;
  aas_vendor?: string;
  aas_status?: string;
  status?: string;
  grand_total?: number;
  aas_cost_total?: number;
  aas_margin_total?: number;
  aas_margin_percent?: number;
};

@Component({
  selector: 'app-root',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './app.component.html',
  styleUrl: './app.component.css'
})
export class AppComponent {
  private readonly apiBase = '';
  loginUsername = '';
  loginPassword = '';
  token: string | null = null;
  loginStatus = '';
  isLoggedIn = false;
  isLoading = false;
  dataStatus = '';
  loggedInUser = '';
  currentRole: Role = 'admin';
  serverRole = '';

  items: SimpleDoc[] = [];
  vendors: SimpleDoc[] = [];
  categories: SimpleDoc[] = [];
  shops: SimpleDoc[] = [];
  orders: OrderSummary[] = [];

  orderCustomer = '';
  orderCompany = '';
  orderDate = '';
  orderDeliveryDate = '';
  orderItemCode = '';
  orderQty = 1;
  orderCostRate = 0;
  orderMarginPercent = 0;
  orderRate = 0;
  orderStatus = '';
  filterShop = '';
  filterVendor = '';
  filterStatus = '';
  filterFrom = '';
  filterTo = '';
  vendorView = '';
  shopView = '';
  reportMonth = '';
  vendorReportOrders: Array<{ vendor: string; shop: string; orders: number; total: number; cost_total?: number; margin_total?: number }> = [];
  vendorReportBilling: Array<{ vendor: string; total: number; cost_total?: number; margin_total?: number }> = [];
  vendorReportPayments: Array<{ vendor: string; total: number }> = [];
  shopReportBilling: Array<{ shop: string; total: number; cost_total?: number; margin_total?: number }> = [];
  shopReportPayments: Array<{ shop: string; total: number }> = [];
  shopReportCategory: Array<{ category: string; total: number; cost_total?: number; margin_total?: number }> = [];
  invoices: Array<{ name: string; customer: string; posting_date: string; grand_total: number; status: string }> = [];
  invoiceStatusMessage = '';
  selectedOrderId = '';
  selectedVendor = '';
  selectedStatus = 'Accepted';
  invoiceStatus = '';
  paymentAmount = 0;
  paymentStatus = '';

  newShopName = '';
  newVendorName = '';
  newCategoryName = '';
  newItemCode = '';
  newItemName = '';
  newItemGroup = '';
  newItemVendorRate = 0;
  newItemMarginPercent = 0;
  seedStatus = '';

  constructor(private http: HttpClient) {
    const today = this.formatDate(new Date());
    this.orderDate = today;
    this.orderDeliveryDate = today;
    this.filterFrom = today;
    this.filterTo = today;
    this.reportMonth = `${today.slice(0, 7)}`;
  }

  async login(): Promise<void> {
    this.loginStatus = 'Logging in...';
    this.token = null;
    this.isLoggedIn = false;
    this.createdOrders = [];
    try {
      const data = await this.http
        .post<{ accessToken: string }>(`/api/auth/login`, {
          username: this.loginUsername,
          password: this.loginPassword
        })
        .toPromise();
      this.token = data?.accessToken ?? null;
      if (!this.token) {
        this.loginStatus = 'Login failed: no token';
        return;
      }
      this.loginStatus = 'Login OK';
      this.isLoggedIn = true;
      this.loggedInUser = this.loginUsername;
      await this.loadProfile();
      await this.ensureSetup();
      await this.loadReferenceData();
      await this.loadOrders();
      await this.loadInvoices();
    } catch (err) {
      this.loginStatus = `Login failed: ${this.formatError(err)}`;
    }
  }

  async createOrder(): Promise<void> {
    if (!this.token) {
      this.orderStatus = 'Please login first.';
      return;
    }
    this.orderStatus = 'Creating order...';
    try {
      if (!this.orderRate) {
        this.applyMarginForSelectedItem();
        if (this.orderCostRate > 0) {
          this.orderRate = Number((this.orderCostRate * (1 + this.orderMarginPercent / 100)).toFixed(2));
        }
      }
      const body = await this.http
        .post<{ data?: { name?: string; customer?: string; status?: string; total?: number } }>(
          `/api/orders`,
          {
            fields: {
              customer: this.orderCustomer,
              company: this.orderCompany,
              transaction_date: this.orderDate,
              delivery_date: this.orderDeliveryDate,
              items: [
                {
                  item_code: this.orderItemCode,
                  qty: Number(this.orderQty),
                  rate: Number(this.orderRate),
                  aas_vendor_rate: Number(this.orderCostRate) || 0,
                  aas_margin_percent: Number(this.orderMarginPercent) || 0
                }
              ]
            }
          },
          { headers: this.authHeaders() }
        )
        .toPromise();
      const orderId = body?.data?.name ?? '';
      this.orderStatus = orderId ? `Order created: ${orderId}` : 'Order created';
      if (orderId) {
        this.selectedOrderId = orderId;
        this.paymentAmount = body?.data?.total ?? Number(this.orderQty) * Number(this.orderRate);
        await this.loadOrders();
      }
    } catch (err) {
      this.orderStatus = `Order failed: ${this.formatError(err)}`;
    }
  }

  private formatError(err: unknown): string {
    if (err instanceof Error) {
      return err.message;
    }
    return String(err);
  }

  private formatDate(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }

  private authHeaders(): HttpHeaders {
    return new HttpHeaders({ Authorization: `Bearer ${this.token}` });
  }

  async loadReferenceData(): Promise<void> {
    if (!this.token) {
      return;
    }
    this.isLoading = true;
    this.dataStatus = 'Loading data...';
    try {
      const [items, vendors, categories, shops] = await Promise.all([
        this.http.get<SimpleDoc[]>(`/api/items`, { headers: this.authHeaders() }).toPromise(),
        this.http.get<SimpleDoc[]>(`/api/vendors`, { headers: this.authHeaders() }).toPromise(),
        this.http.get<SimpleDoc[]>(`/api/categories`, { headers: this.authHeaders() }).toPromise(),
        this.http.get<SimpleDoc[]>(`/api/shops`, { headers: this.authHeaders() }).toPromise()
      ]);
      this.items = items ?? [];
      this.vendors = vendors ?? [];
      this.categories = categories ?? [];
      this.shops = shops ?? [];
      this.dataStatus = 'Data loaded';
    } catch (err) {
      this.dataStatus = `Load failed: ${this.formatError(err)}`;
    } finally {
      this.isLoading = false;
    }
  }

  private async loadProfile(): Promise<void> {
    if (!this.token) {
      return;
    }
    try {
      const profile = await this.http
        .get<{ full_name?: string; name?: string; role?: string }>(`/api/me`, { headers: this.authHeaders() })
        .toPromise();
      const fullName = profile?.full_name || profile?.name;
      if (fullName) {
        this.loggedInUser = fullName;
      }
      if (profile?.role) {
        this.serverRole = profile.role;
        this.currentRole = profile.role as Role;
      }
    } catch {
      // fallback to username
    }
  }

  get canSwitchRoles(): boolean {
    return this.serverRole === 'admin';
  }

  logout(): void {
    this.token = null;
    this.isLoggedIn = false;
    this.items = [];
    this.vendors = [];
    this.categories = [];
    this.shops = [];
    this.orders = [];
    this.loginStatus = '';
    this.orderStatus = '';
    this.loggedInUser = '';
  }

  private async ensureSetup(): Promise<void> {
    if (!this.token) {
      return;
    }
    try {
      await this.http.post(`/api/setup/ensure`, {}, { headers: this.authHeaders() }).toPromise();
    } catch {
      // ignore setup errors; user can still proceed with defaults
    }
  }

  async assignVendor(): Promise<void> {
    if (!this.token || !this.selectedOrderId || !this.selectedVendor) {
      this.orderStatus = 'Select order and vendor.';
      return;
    }
    try {
      await this.http
        .post(
          `/api/orders/${this.selectedOrderId}/assign-vendor`,
          { fields: { aas_vendor: this.selectedVendor } },
          { headers: this.authHeaders() }
        )
        .toPromise();
      await this.loadOrders();
      this.orderStatus = 'Vendor assigned.';
    } catch (err) {
      this.orderStatus = `Assign failed: ${this.formatError(err)}`;
    }
  }

  async updateStatus(): Promise<void> {
    if (!this.token || !this.selectedOrderId || !this.selectedStatus) {
      this.orderStatus = 'Select order and status.';
      return;
    }
    try {
      await this.http
        .post(
          `/api/orders/${this.selectedOrderId}/status`,
          { fields: { aas_status: this.selectedStatus } },
          { headers: this.authHeaders() }
        )
        .toPromise();
      await this.loadOrders();
      this.orderStatus = 'Status updated.';
    } catch (err) {
      this.orderStatus = `Status update failed: ${this.formatError(err)}`;
    }
  }

  async createInvoice(): Promise<void> {
    if (!this.token || !this.selectedOrderId) {
      this.invoiceStatus = 'Select an order.';
      return;
    }
    try {
      const order = await this.http
        .get<any>(`/api/orders/${this.selectedOrderId}`, { headers: this.authHeaders() })
        .toPromise();
      const items = (order?.items || []).map((item: any) => ({
        item_code: item.item_code,
        qty: item.qty,
        rate: item.rate
      }));
      await this.http
        .post(
          `/api/invoices`,
          {
            fields: {
              customer: order.customer,
              company: order.company,
              items
            }
          },
          { headers: this.authHeaders() }
        )
        .toPromise();
      this.invoiceStatus = 'Invoice created.';
    } catch (err) {
      this.invoiceStatus = `Invoice failed: ${this.formatError(err)}`;
    }
  }

  async createPayment(): Promise<void> {
    if (!this.token || !this.selectedOrderId) {
      this.paymentStatus = 'Select an order.';
      return;
    }
    try {
      const order = await this.http
        .get<any>(`/api/orders/${this.selectedOrderId}`, { headers: this.authHeaders() })
        .toPromise();
      await this.http
        .post(
          `/api/payments`,
          {
            customer: order.customer,
            company: order.company,
            amount: this.paymentAmount || order.grand_total || 0
          },
          { headers: this.authHeaders() }
        )
        .toPromise();
      this.paymentStatus = 'Payment recorded.';
    } catch (err) {
      this.paymentStatus = `Payment failed: ${this.formatError(err)}`;
    }
  }

  async createShop(): Promise<void> {
    if (!this.token || !this.newShopName) {
      this.seedStatus = 'Enter shop name.';
      return;
    }
    try {
      await this.http
        .post(
          `/api/shops`,
          { fields: { customer_name: this.newShopName } },
          { headers: this.authHeaders() }
        )
        .toPromise();
      this.seedStatus = 'Shop created.';
      this.newShopName = '';
      await this.loadReferenceData();
    } catch (err) {
      this.seedStatus = `Shop create failed: ${this.formatError(err)}`;
    }
  }

  async createVendor(): Promise<void> {
    if (!this.token || !this.newVendorName) {
      this.seedStatus = 'Enter vendor name.';
      return;
    }
    try {
      await this.http
        .post(
          `/api/vendors`,
          { fields: { supplier_name: this.newVendorName } },
          { headers: this.authHeaders() }
        )
        .toPromise();
      this.seedStatus = 'Vendor created.';
      this.newVendorName = '';
      await this.loadReferenceData();
    } catch (err) {
      this.seedStatus = `Vendor create failed: ${this.formatError(err)}`;
    }
  }

  async createCategory(): Promise<void> {
    if (!this.token || !this.newCategoryName) {
      this.seedStatus = 'Enter category name.';
      return;
    }
    try {
      await this.http
        .post(
          `/api/categories`,
          { fields: { item_group_name: this.newCategoryName } },
          { headers: this.authHeaders() }
        )
        .toPromise();
      this.seedStatus = 'Category created.';
      this.newCategoryName = '';
      await this.loadReferenceData();
    } catch (err) {
      this.seedStatus = `Category create failed: ${this.formatError(err)}`;
    }
  }

  async createItem(): Promise<void> {
    if (!this.token || !this.newItemCode || !this.newItemName) {
      this.seedStatus = 'Enter item code and name.';
      return;
    }
    try {
      await this.http
        .post(
          `/api/items`,
          {
            fields: {
              item_code: this.newItemCode,
              item_name: this.newItemName,
              item_group: this.newItemGroup || 'All Item Groups',
              aas_vendor_rate: Number(this.newItemVendorRate) || 0,
              aas_margin_percent: Number(this.newItemMarginPercent) || 0
            }
          },
          { headers: this.authHeaders() }
        )
        .toPromise();
      this.seedStatus = 'Item created.';
      this.newItemCode = '';
      this.newItemName = '';
      this.newItemGroup = '';
      this.newItemVendorRate = 0;
      this.newItemMarginPercent = 0;
      await this.loadReferenceData();
    } catch (err) {
      this.seedStatus = `Item create failed: ${this.formatError(err)}`;
    }
  }

  setRole(role: Role): void {
    this.currentRole = role;
    void this.loadOrders();
    if (role === 'admin' || role === 'shop') {
      void this.loadInvoices();
    }
    if (role === 'vendor' && this.vendorView) {
      void this.loadVendorReports();
    }
    if (role === 'shop' && this.shopView) {
      void this.loadShopReports();
    }
  }

  async loadOrders(): Promise<void> {
    if (!this.token) {
      return;
    }
    const params: Record<string, string> = {};
    if (this.filterShop) {
      params.customer = this.filterShop;
    }
    if (this.filterVendor) {
      params.vendor = this.filterVendor;
    }
    if (this.filterStatus) {
      params.status = this.filterStatus;
    }
    if (this.filterFrom) {
      params.from = this.filterFrom;
    }
    if (this.filterTo) {
      params.to = this.filterTo;
    }
    try {
      const orders = await this.http
        .get<OrderSummary[]>(`/api/orders`, { headers: this.authHeaders(), params })
        .toPromise();
      this.orders = orders ?? [];
    } catch (err) {
      this.dataStatus = `Orders load failed: ${this.formatError(err)}`;
    }
  }

  onItemSelected(): void {
    this.applyMarginForSelectedItem();
  }

  private applyMarginForSelectedItem(): void {
    const selected = this.items.find(item => item.name === this.orderItemCode);
    if (!selected) {
      return;
    }
    const vendorRate = Number(selected['aas_vendor_rate'] ?? 0);
    const marginPercent = Number(selected['aas_margin_percent'] ?? 0);
    this.orderCostRate = vendorRate;
    this.orderMarginPercent = marginPercent;
    if (vendorRate > 0) {
      this.orderRate = Number((vendorRate * (1 + marginPercent / 100)).toFixed(2));
    }
  }

  async loadVendorOrders(): Promise<void> {
    this.filterVendor = this.vendorView;
    this.filterShop = '';
    this.filterStatus = '';
    await this.loadOrders();
  }

  async loadShopOrders(): Promise<void> {
    this.filterShop = this.shopView;
    this.filterVendor = '';
    this.filterStatus = '';
    await this.loadOrders();
  }

  async loadVendorReports(): Promise<void> {
    if (!this.token) {
      return;
    }
    const params: Record<string, string> = {};
    if (this.vendorView) {
      params.vendor = this.vendorView;
    }
    params.month = this.reportMonth;
      const [orders, billing, payments] = await Promise.all([
        this.http
        .get<Array<{ vendor: string; shop: string; orders: number; total: number; cost_total?: number; margin_total?: number }>>(
          `/api/reports/vendor-orders`,
          { headers: this.authHeaders(), params }
        )
        .toPromise(),
      this.http
        .get<Array<{ vendor: string; total: number; cost_total?: number; margin_total?: number }>>(`/api/reports/vendor-billing`, {
          headers: this.authHeaders(),
          params
        })
        .toPromise(),
      this.http
        .get<Array<{ vendor: string; total: number }>>(`/api/reports/vendor-payments`, {
          headers: this.authHeaders(),
          params
        })
        .toPromise()
    ]);
    this.vendorReportOrders = orders ?? [];
    this.vendorReportBilling = billing ?? [];
    this.vendorReportPayments = payments ?? [];
  }

  async loadShopReports(): Promise<void> {
    if (!this.token) {
      return;
    }
    const params: Record<string, string> = {};
    if (this.shopView) {
      params.customer = this.shopView;
    }
    params.month = this.reportMonth;
      const [billing, payments, category] = await Promise.all([
        this.http
        .get<Array<{ shop: string; total: number; cost_total?: number; margin_total?: number }>>(`/api/reports/shop-billing`, {
          headers: this.authHeaders(),
          params
        })
        .toPromise(),
      this.http
        .get<Array<{ shop: string; total: number }>>(`/api/reports/shop-payments`, {
          headers: this.authHeaders(),
          params
        })
        .toPromise(),
      this.http
        .get<Array<{ category: string; total: number; cost_total?: number; margin_total?: number }>>(`/api/reports/shop-category`, {
          headers: this.authHeaders(),
          params
        })
        .toPromise()
    ]);
    this.shopReportBilling = billing ?? [];
    this.shopReportPayments = payments ?? [];
    this.shopReportCategory = category ?? [];
  }

  async loadInvoices(): Promise<void> {
    if (!this.token) {
      return;
    }
    const params: Record<string, string> = {};
    if (this.shopView) {
      params.customer = this.shopView;
    }
    if (this.filterFrom) {
      params.from = this.filterFrom;
    }
    if (this.filterTo) {
      params.to = this.filterTo;
    }
    try {
      const invoices = await this.http
        .get<Array<{ name: string; customer: string; posting_date: string; grand_total: number; status: string }>>(
          `/api/invoices`,
          { headers: this.authHeaders(), params }
        )
        .toPromise();
      this.invoices = invoices ?? [];
      this.invoiceStatusMessage = '';
    } catch (err) {
      this.invoiceStatusMessage = `Invoice load failed: ${this.formatError(err)}`;
    }
  }

  async downloadInvoicePdf(invoiceId: string): Promise<void> {
    if (!this.token || !invoiceId) {
      return;
    }
    try {
      const blob = await this.http
        .get(`/api/invoices/${invoiceId}/pdf`, {
          headers: this.authHeaders(),
          responseType: 'blob'
        })
        .toPromise();
      if (!blob) {
        return;
      }
      const url = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = url;
      link.download = `${invoiceId}.pdf`;
      link.click();
      window.URL.revokeObjectURL(url);
    } catch (err) {
      this.invoiceStatusMessage = `Invoice download failed: ${this.formatError(err)}`;
    }
  }

  async downloadOrdersCsv(): Promise<void> {
    await this.downloadCsv(`/api/orders/export`, 'orders.csv', {
      customer: this.filterShop || '',
      vendor: this.filterVendor || '',
      status: this.filterStatus || '',
      from: this.filterFrom || '',
      to: this.filterTo || ''
    });
  }

  async downloadVendorOrdersCsv(): Promise<void> {
    await this.downloadCsv(`/api/reports/vendor-orders/export`, 'vendor-orders.csv', {
      vendor: this.vendorView || '',
      month: this.reportMonth || ''
    });
  }

  async downloadVendorBillingCsv(): Promise<void> {
    await this.downloadCsv(`/api/reports/vendor-billing/export`, 'vendor-billing.csv', {
      vendor: this.vendorView || '',
      month: this.reportMonth || ''
    });
  }

  async downloadVendorPaymentsCsv(): Promise<void> {
    await this.downloadCsv(`/api/reports/vendor-payments/export`, 'vendor-payments.csv', {
      vendor: this.vendorView || '',
      month: this.reportMonth || ''
    });
  }

  async downloadShopBillingCsv(): Promise<void> {
    await this.downloadCsv(`/api/reports/shop-billing/export`, 'shop-billing.csv', {
      customer: this.shopView || '',
      month: this.reportMonth || ''
    });
  }

  async downloadShopPaymentsCsv(): Promise<void> {
    await this.downloadCsv(`/api/reports/shop-payments/export`, 'shop-payments.csv', {
      customer: this.shopView || '',
      month: this.reportMonth || ''
    });
  }

  async downloadShopCategoryCsv(): Promise<void> {
    await this.downloadCsv(`/api/reports/shop-category/export`, 'shop-category.csv', {
      customer: this.shopView || '',
      month: this.reportMonth || ''
    });
  }

  async downloadInvoicesCsv(): Promise<void> {
    await this.downloadCsv(`/api/invoices/export`, 'invoices.csv', {
      customer: this.shopView || '',
      from: this.filterFrom || '',
      to: this.filterTo || ''
    });
  }

  private async downloadCsv(url: string, filename: string, params: Record<string, string>): Promise<void> {
    if (!this.token) {
      return;
    }
    try {
      const blob = await this.http
        .get(url, {
          headers: this.authHeaders(),
          params,
          responseType: 'blob'
        })
        .toPromise();
      if (!blob) {
        return;
      }
      const objectUrl = window.URL.createObjectURL(blob);
      const link = document.createElement('a');
      link.href = objectUrl;
      link.download = filename;
      link.click();
      window.URL.revokeObjectURL(objectUrl);
    } catch (err) {
      this.dataStatus = `CSV download failed: ${this.formatError(err)}`;
    }
  }
}
