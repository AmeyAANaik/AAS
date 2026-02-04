import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { forkJoin, map, Observable } from 'rxjs';
import { AuthTokenService } from '../shared/auth-token.service';
import {
  BillingRow,
  BillingSummary,
  DashboardSnapshot,
  InventoryItem,
  InvoiceSummary,
  OrderStatusRow,
  OrderSummary,
  SalesSummary,
  StockSnapshot
} from './dashboard.model';

@Injectable({
  providedIn: 'root'
})
export class DashboardService {
  constructor(private http: HttpClient, private tokenStore: AuthTokenService) {}

  getDashboardSnapshot(): Observable<DashboardSnapshot> {
    const today = new Date();
    const monthLabel = this.formatMonth(today);
    const rangeStart = this.formatDate(new Date(today.getFullYear(), today.getMonth(), 1));
    const rangeEnd = this.formatDate(today);

    const headers = this.authHeaders();
    const orderParams = new HttpParams().set('from', rangeStart).set('to', rangeEnd);
    const invoiceParams = new HttpParams().set('from', rangeStart).set('to', rangeEnd);
    const reportParams = new HttpParams().set('month', monthLabel);

    return forkJoin({
      orders: this.http.get<OrderSummary[]>(`/api/orders`, { headers, params: orderParams }),
      vendorBilling: this.http.get<BillingSummary[]>(`/api/reports/vendor-billing`, { headers, params: reportParams }),
      branchBilling: this.http.get<BillingSummary[]>(`/api/reports/shop-billing`, { headers, params: reportParams }),
      items: this.http.get<InventoryItem[]>(`/api/items`, { headers }),
      invoices: this.http.get<InvoiceSummary[]>(`/api/invoices`, { headers, params: invoiceParams })
    }).pipe(
      map(result => ({
        orderStatus: this.buildOrderStatus(result.orders ?? []),
        billsByVendor: this.buildBillingRows(result.vendorBilling ?? [], 'vendor'),
        billsByBranch: this.buildBillingRows(result.branchBilling ?? [], 'shop'),
        stockSnapshot: this.buildStockSnapshot(result.items ?? []),
        salesSummary: this.buildSalesSummary(result.invoices ?? [], rangeStart, rangeEnd),
        periodLabel: monthLabel
      }))
    );
  }

  private buildOrderStatus(orders: OrderSummary[]): OrderStatusRow[] {
    const counts = new Map<string, number>();
    for (const order of orders) {
      const status = (order.aas_status || order.status || 'Unknown').trim();
      counts.set(status, (counts.get(status) ?? 0) + 1);
    }
    return Array.from(counts.entries())
      .map(([status, count]) => ({ status, count }))
      .sort((a, b) => b.count - a.count);
  }

  private buildBillingRows(rows: BillingSummary[], key: 'vendor' | 'shop'): BillingRow[] {
    return rows
      .map(row => ({
        name: String(row[key] ?? 'Unknown'),
        total: Number(row.total) || 0
      }))
      .sort((a, b) => b.total - a.total);
  }

  private buildStockSnapshot(items: InventoryItem[]): StockSnapshot {
    let totalQuantity = 0;
    for (const item of items) {
      const quantity = Number(
        item.stock_qty ?? item.actual_qty ?? item.quantity ?? item.qty ?? 0
      );
      if (Number.isFinite(quantity)) {
        totalQuantity += quantity;
      }
    }
    return {
      totalItems: items.length,
      totalQuantity
    };
  }

  private buildSalesSummary(invoices: InvoiceSummary[], from: string, to: string): SalesSummary {
    const totalRevenue = invoices.reduce((sum, invoice) => sum + (Number(invoice.grand_total) || 0), 0);
    return {
      invoiceCount: invoices.length,
      totalRevenue,
      dateRangeLabel: `${from} to ${to}`
    };
  }

  private authHeaders(): HttpHeaders {
    const token = this.tokenStore.getToken();
    if (!token) {
      return new HttpHeaders();
    }
    return new HttpHeaders({ Authorization: `Bearer ${token}` });
  }

  private formatMonth(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    return `${year}-${month}`;
  }

  private formatDate(date: Date): string {
    const year = date.getFullYear();
    const month = String(date.getMonth() + 1).padStart(2, '0');
    const day = String(date.getDate()).padStart(2, '0');
    return `${year}-${month}-${day}`;
  }
}
