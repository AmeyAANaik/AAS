import { HttpClient, HttpHeaders, HttpParams } from '@angular/common/http';
import { Injectable } from '@angular/core';
import { forkJoin, map, Observable, timeout } from 'rxjs';
import { AuthTokenService } from '../shared/auth-token.service';
import {
  BillingRow,
  BillingSummary,
  DashboardSnapshot,
  InventoryItem,
  InvoiceSummary,
  OrderStatusRow,
  OrderSummary,
  RevenuePoint,
  SalesSummary,
  BranchOperationsSnapshot,
  StockSnapshot,
  VendorOperationsSnapshot
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
      vendorOps: this.http.get<{ totals?: VendorOperationsSnapshot }>(`/api/vendor-ops/summary`, { headers }),
      branchOps: this.http.get<{ totals?: BranchOperationsSnapshot }>(`/api/branch-ops/summary`, { headers }),
      items: this.http.get<InventoryItem[]>(`/api/items`, { headers }),
      invoices: this.http.get<InvoiceSummary[]>(`/api/invoices`, { headers, params: invoiceParams })
    }).pipe(
      timeout({ first: 15000 }),
      map(result => ({
        orderStatus: this.buildOrderStatus(result.orders ?? []),
        billsByVendor: this.buildBillingRows(result.vendorBilling ?? [], 'vendor'),
        billsByBranch: this.buildBillingRows(result.branchBilling ?? [], 'shop'),
        stockSnapshot: this.buildStockSnapshot(result.items ?? []),
        salesSummary: this.buildSalesSummary(result.invoices ?? [], rangeStart, rangeEnd),
        revenueSeries: this.buildRevenueSeries(result.invoices ?? [], rangeStart, rangeEnd),
        vendorOperations: this.buildVendorOperationsSummary(result.vendorOps?.totals),
        branchOperations: this.buildBranchOperationsSummary(result.branchOps?.totals),
        periodLabel: monthLabel
      }))
    );
  }

  private buildVendorOperationsSummary(totals?: VendorOperationsSnapshot): VendorOperationsSnapshot {
    return {
      totalVendors: Number(totals?.totalVendors) || 0,
      vendorsWithPendingOrders: Number(totals?.vendorsWithPendingOrders) || 0,
      totalPendingOrders: Number(totals?.totalPendingOrders) || 0,
      awaitingPdf: Number(totals?.awaitingPdf) || 0,
      awaitingBillCapture: Number(totals?.awaitingBillCapture) || 0,
      totalPendingBillAmount: Number(totals?.totalPendingBillAmount) || 0
    };
  }

  private buildBranchOperationsSummary(totals?: BranchOperationsSnapshot): BranchOperationsSnapshot {
    return {
      totalBranches: Number(totals?.totalBranches) || 0,
      branchesWithPendingOrders: Number(totals?.branchesWithPendingOrders) || 0,
      totalPendingOrders: Number(totals?.totalPendingOrders) || 0,
      awaitingVendorAssignment: Number(totals?.awaitingVendorAssignment) || 0,
      awaitingVendorResponse: Number(totals?.awaitingVendorResponse) || 0,
      openReceivableAmount: Number(totals?.openReceivableAmount) || 0
    };
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
    const revenueSeries = this.buildRevenueSeries(invoices, from, to);
    const totalRevenue = revenueSeries.reduce((sum, point) => sum + point.value, 0);
    const averageDailyRevenue = revenueSeries.length ? totalRevenue / revenueSeries.length : 0;
    const peakPoint = revenueSeries.reduce<RevenuePoint | null>((best, point) => {
      if (!best || point.value > best.value) {
        return point;
      }
      return best;
    }, null);
    return {
      invoiceCount: invoices.length,
      totalRevenue,
      dateRangeLabel: `${from} to ${to}`,
      averageDailyRevenue,
      peakRevenue: peakPoint?.value ?? 0,
      peakRevenueLabel: peakPoint?.label ?? `${from} to ${to}`
    };
  }

  private buildRevenueSeries(invoices: InvoiceSummary[], from: string, to: string): RevenuePoint[] {
    const totalsByDay = new Map<string, number>();
    for (const invoice of invoices) {
      const rawDate = String(invoice.posting_date ?? '').trim();
      if (!rawDate) {
        continue;
      }
      const day = rawDate.slice(0, 10);
      totalsByDay.set(day, (totalsByDay.get(day) ?? 0) + (Number(invoice.grand_total) || 0));
    }

    const points: RevenuePoint[] = [];
    let cursor = new Date(`${from}T00:00:00`);
    const end = new Date(`${to}T00:00:00`);
    while (cursor <= end) {
      const iso = this.formatDate(cursor);
      points.push({
        label: iso,
        shortLabel: this.formatShortDay(cursor),
        value: totalsByDay.get(iso) ?? 0
      });
      cursor = new Date(cursor.getFullYear(), cursor.getMonth(), cursor.getDate() + 1);
    }
    return points;
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

  private formatShortDay(date: Date): string {
    return `${String(date.getDate()).padStart(2, '0')} ${date.toLocaleString('en-US', { month: 'short' })}`;
  }
}
