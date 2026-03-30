package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.meta.VendorFieldRegistry;
import com.aas.mw.meta.VendorFieldSpec;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import feign.FeignException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SetupService {
    private static final String SALES_INVOICE_PRINT_FORMAT_NAME = "AAS Sales Invoice Print";
    private static final String SALES_INVOICE_PRINT_FORMAT_HTML = """
            <style>
              .print-format { font-size: 12px; color: #111827; }
              .aas-header { display: flex; justify-content: space-between; gap: 24px; margin-bottom: 18px; }
              .aas-title { font-size: 22px; font-weight: 700; margin: 0 0 6px; }
              .aas-subtitle { color: #6b7280; margin: 0; }
              .aas-meta, .aas-items, .aas-summary { width: 100%; border-collapse: collapse; }
              .aas-meta { margin-bottom: 18px; }
              .aas-meta td { padding: 4px 8px 4px 0; vertical-align: top; }
              .aas-meta-label { color: #6b7280; white-space: nowrap; width: 140px; }
              .aas-items { margin-bottom: 18px; }
              .aas-items th, .aas-items td { border-bottom: 1px solid #e5e7eb; padding: 8px 10px; text-align: left; vertical-align: top; }
              .aas-items th { font-size: 11px; text-transform: uppercase; letter-spacing: 0.04em; color: #6b7280; }
              .aas-items .num { text-align: right; white-space: nowrap; }
              .aas-items .item-name { font-weight: 600; }
              .aas-summary { width: 320px; margin-left: auto; }
              .aas-summary td { padding: 6px 0 6px 16px; }
              .aas-summary .label { color: #6b7280; }
              .aas-summary .value { text-align: right; white-space: nowrap; }
              .aas-summary .total td { border-top: 1px solid #111827; font-weight: 700; padding-top: 10px; }
            </style>
            <div class="aas-header">
              <div>
                <p class="aas-title">Sales Invoice</p>
                <p class="aas-subtitle">{{ doc.name }}</p>
              </div>
              <div style="text-align: right;">
                <div><strong>{{ doc.company_name or doc.company }}</strong></div>
                <div>{{ doc.customer }}</div>
              </div>
            </div>
            <table class="aas-meta">
              <tr>
                <td class="aas-meta-label">Date</td>
                <td>{{ frappe.utils.formatdate(doc.posting_date) }}</td>
                <td class="aas-meta-label">Customer</td>
                <td>{{ doc.customer }}</td>
              </tr>
              <tr>
                <td class="aas-meta-label">Source Sales Order</td>
                <td>{{ doc.aas_source_sales_order or '-' }}</td>
                <td class="aas-meta-label">Payment Due Date</td>
                <td>{{ frappe.utils.formatdate(doc.due_date) if doc.due_date else '-' }}</td>
              </tr>
            </table>
            <table class="aas-items">
              <thead>
                <tr>
                  <th style="width: 56px;">Sr</th>
                  <th>Item</th>
                  <th>Description</th>
                  <th class="num" style="width: 88px;">Quantity</th>
                  <th style="width: 88px;">UOM</th>
                  <th class="num" style="width: 110px;">Rate</th>
                  <th class="num" style="width: 88px;">GST %</th>
                  <th class="num" style="width: 130px;">Amount</th>
                </tr>
              </thead>
              <tbody>
                {% for item in doc.items %}
                {% set gst_percent = item.aas_gst_percent if item.aas_gst_percent else 0 %}
                <tr>
                  <td>{{ loop.index }}</td>
                  <td class="item-name">{{ item.item_code or item.item_name }}</td>
                  <td>{{ item.description or item.item_name or item.item_code }}</td>
                  <td class="num">{{ frappe.utils.flt(item.qty, 0) }}</td>
                  <td>{{ item.uom or item.stock_uom or '-' }}</td>
                  <td class="num">{{ frappe.utils.fmt_money(item.rate, currency=doc.currency) }}</td>
                  <td class="num">{{ frappe.utils.flt(gst_percent, 0) }}</td>
                  <td class="num">{{ frappe.utils.fmt_money(item.amount, currency=doc.currency) }}</td>
                </tr>
                {% endfor %}
              </tbody>
            </table>
            <table class="aas-summary">
              <tr>
                <td class="label">Net Total</td>
                <td class="value">{{ frappe.utils.fmt_money(doc.net_total or doc.total, currency=doc.currency) }}</td>
              </tr>
              {% if doc.aas_rounding_adjustment %}
              <tr>
                <td class="label">Rounding Adjustment</td>
                <td class="value">{{ frappe.utils.fmt_money(doc.aas_rounding_adjustment, currency=doc.currency) }}</td>
              </tr>
              {% endif %}
              <tr class="total">
                <td class="label">Grand Total</td>
                <td class="value">{{ frappe.utils.fmt_money(doc.grand_total, currency=doc.currency) }}</td>
              </tr>
            </table>
            """;

    private final ErpNextClient erpNextClient;
    private final CustomFieldProvisioner customFieldProvisioner;
    private final VendorFieldRegistry vendorFieldRegistry;
    private final CatalogRoutingService catalogRoutingService;
    private final boolean defaultsEnabled;
    private final String vendorRole;
    private final String shopRole;
    private final String helperRole;
    private final String defaultVendorEmail;
    private final String defaultVendorName;
    private final String defaultVendorPassword;
    private final String defaultVendorSupplier;
    private final String defaultShopEmail;
    private final String defaultShopName;
    private final String defaultShopPassword;
    private final String defaultShopCustomer;
    private final String defaultHelperEmail;
    private final String defaultHelperName;
    private final String defaultHelperPassword;
    private final double defaultMarginPercent;

    public SetupService(
            ErpNextClient erpNextClient,
            CustomFieldProvisioner customFieldProvisioner,
            VendorFieldRegistry vendorFieldRegistry,
            CatalogRoutingService catalogRoutingService,
            @Value("${app.defaults.enabled:true}") boolean defaultsEnabled,
            @Value("${app.role.vendor:Supplier}") String vendorRole,
            @Value("${app.role.shop:Customer}") String shopRole,
            @Value("${app.role.helper:Stock User}") String helperRole,
            @Value("${app.defaults.vendor.email:}") String defaultVendorEmail,
            @Value("${app.defaults.vendor.name:Vendor User}") String defaultVendorName,
            @Value("${app.defaults.vendor.password:vendor123}") String defaultVendorPassword,
            @Value("${app.defaults.vendor.supplier:}") String defaultVendorSupplier,
            @Value("${app.defaults.shop.email:}") String defaultShopEmail,
            @Value("${app.defaults.shop.name:Shop User}") String defaultShopName,
            @Value("${app.defaults.shop.password:shop123}") String defaultShopPassword,
            @Value("${app.defaults.shop.customer:}") String defaultShopCustomer,
            @Value("${app.defaults.helper.email:}") String defaultHelperEmail,
            @Value("${app.defaults.helper.name:Helper User}") String defaultHelperName,
            @Value("${app.defaults.helper.password:helper123}") String defaultHelperPassword,
            @Value("${app.order.margin.default-percent:7}") double defaultMarginPercent) {
        this.erpNextClient = erpNextClient;
        this.customFieldProvisioner = customFieldProvisioner;
        this.vendorFieldRegistry = vendorFieldRegistry;
        this.catalogRoutingService = catalogRoutingService;
        this.defaultsEnabled = defaultsEnabled;
        this.vendorRole = vendorRole;
        this.shopRole = shopRole;
        this.helperRole = helperRole;
        this.defaultVendorEmail = defaultVendorEmail;
        this.defaultVendorName = defaultVendorName;
        this.defaultVendorPassword = defaultVendorPassword;
        this.defaultVendorSupplier = defaultVendorSupplier;
        this.defaultShopEmail = defaultShopEmail;
        this.defaultShopName = defaultShopName;
        this.defaultShopPassword = defaultShopPassword;
        this.defaultShopCustomer = defaultShopCustomer;
        this.defaultHelperEmail = defaultHelperEmail;
        this.defaultHelperName = defaultHelperName;
        this.defaultHelperPassword = defaultHelperPassword;
        this.defaultMarginPercent = defaultMarginPercent;
    }

    public Map<String, Object> ensureSetup() {
        boolean vendorField = ensureCustomField(
                "Sales Order",
                "aas_vendor",
                "Vendor",
                "Link",
                "Supplier",
                "customer");
        boolean statusField = ensureCustomField(
                "Sales Order",
                "aas_status",
                "AAS Status",
                "Select",
                "DRAFT\nVENDOR_ASSIGNED\nVENDOR_PDF_RECEIVED\nVENDOR_BILL_CAPTURED\nSELL_ORDER_CREATED\nINVOICED\nDELETED\nAccepted\nPreparing\nReady\nDelivered",
                "aas_vendor");
        boolean deletedFlagField = ensureCustomField(
                "Sales Order",
                "aas_is_deleted",
                "AAS Deleted",
                "Check",
                null,
                "aas_status");
        boolean deletedAtField = ensureCustomField(
                "Sales Order",
                "aas_deleted_at",
                "AAS Deleted At",
                "Datetime",
                null,
                "aas_is_deleted");
        boolean salesOrderMarginField = ensureCustomField(
                "Sales Order",
                "aas_margin_percent",
                "Margin %",
                "Float",
                null,
                "aas_deleted_at");
        boolean vendorPdfField = ensureCustomField(
                "Sales Order",
                "aas_vendor_pdf",
                "Vendor PDF",
                "Attach",
                null,
                "aas_margin_percent");
        boolean purchaseOrderField = ensureCustomField(
                "Sales Order",
                "aas_po",
                "Vendor Purchase Order",
                "Link",
                "Purchase Order",
                "aas_vendor_pdf");
        boolean branchSalesOrderField = ensureCustomField(
                "Sales Order",
                "aas_so_branch",
                "Branch Sales Order",
                "Link",
                "Sales Order",
                "aas_po");
        boolean branchInvoiceField = ensureCustomField(
                "Sales Order",
                "aas_si_branch",
                "Branch Sales Invoice",
                "Link",
                "Sales Invoice",
                "aas_so_branch");
        boolean vendorBillTotalField = ensureCustomField(
                "Sales Order",
                "aas_vendor_bill_total",
                "Vendor Bill Total",
                "Currency",
                null,
                "aas_si_branch");
        boolean vendorBillRefField = ensureCustomField(
                "Sales Order",
                "aas_vendor_bill_ref",
                "Vendor Bill Ref",
                "Data",
                null,
                "aas_vendor_bill_total");
        boolean vendorBillDateField = ensureCustomField(
                "Sales Order",
                "aas_vendor_bill_date",
                "Vendor Bill Date",
                "Date",
                null,
                "aas_vendor_bill_ref");
        boolean transportChargeField = ensureCustomField(
                "Sales Order",
                "aas_transport_charge",
                "Transport Charge",
                "Currency",
                null,
                "aas_vendor_bill_date");
        boolean roundingAdjustmentField = ensureCustomField(
                "Sales Order",
                "aas_rounding_adjustment",
                "Rounding Adjustment",
                "Currency",
                null,
                "aas_transport_charge");
        boolean vendorPurchaseInvoiceField = ensureCustomField(
                "Sales Order",
                "aas_pi_vendor",
                "Vendor Purchase Invoice",
                "Link",
                "Purchase Invoice",
                "aas_rounding_adjustment");
        boolean sellOrderTotalField = ensureCustomField(
                "Sales Order",
                "aas_sell_order_total",
                "Sell Order Total",
                "Currency",
                null,
                "aas_pi_vendor");
        boolean branchLocationField = ensureCustomField(
                "Customer",
                "aas_branch_location",
                "Branch Location",
                "Data",
                null,
                "customer_name");
        boolean branchWhatsappField = ensureCustomField(
                "Customer",
                "aas_whatsapp_group_name",
                "WhatsApp Group Name",
                "Data",
                null,
                "aas_branch_location");
        boolean branchCreditDaysField = ensureCustomField(
                "Customer",
                "aas_credit_days",
                "Credit Days",
                "Int",
                null,
                "aas_whatsapp_group_name");
        boolean poSourceOrderField = ensureCustomField(
                "Purchase Order",
                "aas_source_sales_order",
                "Source Sales Order",
                "Link",
                "Sales Order",
                "supplier");
        boolean purchaseInvoiceSourceOrderField = ensureCustomField(
                "Purchase Invoice",
                "aas_source_sales_order",
                "Source Sales Order",
                "Link",
                "Sales Order",
                "supplier");
        boolean invoiceSourceOrderField = ensureCustomField(
                "Sales Invoice",
                "aas_source_sales_order",
                "Source Sales Order",
                "Link",
                "Sales Order",
                "customer");
        boolean invoiceRoundingAdjustmentField = ensureCustomField(
                "Sales Invoice",
                "aas_rounding_adjustment",
                "Rounding Adjustment",
                "Currency",
                null,
                "aas_source_sales_order");
        boolean companyInvoicePrintFormatField = ensureCustomField(
                "Company",
                "aas_sales_invoice_print_format",
                "Sales Invoice Print Format",
                "Link",
                "Print Format",
                "default_currency");
        boolean salesOrderCategoryField = ensureCustomField(
                "Sales Order",
                "aas_category",
                "Category",
                "Link",
                "Item Group",
                "customer");
        boolean categoryCodeField = ensureCustomField(
                "Item Group",
                "aas_category_code",
                "Category Code",
                "Data",
                null,
                "item_group_name");
        boolean marginField = ensureCustomField(
                "Item",
                "aas_margin_percent",
                "Margin %",
                "Float",
                null,
                "item_name");
        boolean vendorRateField = ensureCustomField(
                "Item",
                "aas_vendor_rate",
                "Vendor Rate",
                "Currency",
                null,
                "aas_margin_percent");
        boolean packagingUnitField = ensureCustomField(
                "Item",
                "aas_packaging_unit",
                "Packaging Unit",
                "Data",
                null,
                "stock_uom");
        boolean itemVendorField = ensureCustomField(
                "Item",
                "aas_vendor",
                "Vendor",
                "Link",
                "Supplier",
                "item_group");
        boolean itemVendorHsnField = ensureCustomField(
                "Item",
                "aas_vendor_hsn_code",
                "Vendor HSN Code",
                "Data",
                null,
                "aas_vendor");
        boolean soItemMarginField = ensureCustomField(
                "Sales Order Item",
                "aas_margin_percent",
                "Margin %",
                "Float",
                null,
                "rate");
        boolean soItemVendorRateField = ensureCustomField(
                "Sales Order Item",
                "aas_vendor_rate",
                "Vendor Rate",
                "Currency",
                null,
                "aas_margin_percent");
        boolean soItemMrpField = ensureCustomField(
                "Sales Order Item",
                "aas_mrp",
                "MRP",
                "Currency",
                null,
                "aas_vendor_rate");
        boolean soItemGstField = ensureCustomField(
                "Sales Order Item",
                "aas_gst_percent",
                "GST %",
                "Float",
                null,
                "aas_mrp");
        boolean siItemGstField = ensureCustomField(
                "Sales Invoice Item",
                "aas_gst_percent",
                "GST %",
                "Float",
                null,
                "rate");
        Map<String, Object> result = new HashMap<>();
        result.put("vendorFieldCreated", vendorField);
        result.put("statusFieldCreated", statusField);
        result.put("salesOrderMarginFieldCreated", salesOrderMarginField);
        result.put("marginFieldCreated", marginField);
        result.put("vendorRateFieldCreated", vendorRateField);
        result.put("packagingUnitFieldCreated", packagingUnitField);
        result.put("soItemMarginFieldCreated", soItemMarginField);
        result.put("soItemVendorRateFieldCreated", soItemVendorRateField);
        result.put("soItemMrpFieldCreated", soItemMrpField);
        result.put("soItemGstFieldCreated", soItemGstField);
        result.put("siItemGstFieldCreated", siItemGstField);
        result.put("vendorPdfFieldCreated", vendorPdfField);
        result.put("purchaseOrderFieldCreated", purchaseOrderField);
        result.put("branchSalesOrderFieldCreated", branchSalesOrderField);
        result.put("branchInvoiceFieldCreated", branchInvoiceField);
        result.put("vendorBillTotalFieldCreated", vendorBillTotalField);
        result.put("vendorBillRefFieldCreated", vendorBillRefField);
        result.put("vendorBillDateFieldCreated", vendorBillDateField);
        result.put("transportChargeFieldCreated", transportChargeField);
        result.put("roundingAdjustmentFieldCreated", roundingAdjustmentField);
        result.put("vendorPurchaseInvoiceFieldCreated", vendorPurchaseInvoiceField);
        result.put("sellOrderTotalFieldCreated", sellOrderTotalField);
        result.put("branchLocationFieldCreated", branchLocationField);
        result.put("branchWhatsappFieldCreated", branchWhatsappField);
        result.put("branchCreditDaysFieldCreated", branchCreditDaysField);
        result.put("poSourceOrderFieldCreated", poSourceOrderField);
        result.put("purchaseInvoiceSourceOrderFieldCreated", purchaseInvoiceSourceOrderField);
        result.put("invoiceSourceOrderFieldCreated", invoiceSourceOrderField);
        result.put("invoiceRoundingAdjustmentFieldCreated", invoiceRoundingAdjustmentField);
        result.put("companyInvoicePrintFormatFieldCreated", companyInvoicePrintFormatField);
        result.put("salesInvoicePrintFormatEnsured", ensureSalesInvoicePrintFormat());
        result.put("salesOrderCategoryFieldCreated", salesOrderCategoryField);
        result.put("categoryCodeFieldCreated", categoryCodeField);
        result.put("supplierGroupEnsured", ensureSupplierGroupRoot());
        result.put("vendorSupplierCustomFieldsChanged", ensureVendorSupplierCustomFields());
        result.put("itemVendorFieldCreated", itemVendorField);
        result.put("itemVendorHsnFieldCreated", itemVendorHsnField);
        result.put("categoryCodesBackfilled", backfillCategoryCodes());
        MarginBackfillResult salesOrderBackfill = backfillSalesOrdersAndItems();
        result.put("salesOrdersMarginBackfilled", salesOrderBackfill.documentCount());
        result.put("salesOrderItemsMarginBackfilled", salesOrderBackfill.itemCount());
        result.put("itemsMarginBackfilled", backfillMarginPercent("Item", "aas_margin_percent"));
        result.putAll(ensureDefaultUsers());
        return result;
    }

    private MarginBackfillResult backfillSalesOrdersAndItems() {
        int ordersUpdated = 0;
        int itemsUpdated = 0;
        int start = 0;
        final int pageSize = 200;
        while (true) {
            Map<String, Object> params = new HashMap<>();
            params.put("fields", "[\"name\",\"aas_margin_percent\"]");
            params.put("limit_page_length", pageSize);
            params.put("limit_start", start);
            List<Map<String, Object>> rows = erpNextClient.listResources("Sales Order", params);
            if (rows == null || rows.isEmpty()) {
                break;
            }
            for (Map<String, Object> row : rows) {
                String name = asText(row.get("name"));
                if (name.isBlank()) {
                    continue;
                }
                Map<String, Object> orderDoc = unwrap(erpNextClient.getResource("Sales Order", name));
                boolean documentChanged = shouldBackfillMargin(orderDoc.get("aas_margin_percent"));
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> items =
                        orderDoc.get("items") instanceof List<?> list ? (List<Map<String, Object>>) list : List.of();
                List<Map<String, Object>> updatedItems = new java.util.ArrayList<>();
                int changedItemsForOrder = 0;
                for (Map<String, Object> item : items) {
                    if (item == null) {
                        continue;
                    }
                    Map<String, Object> copy = new HashMap<>(item);
                    if (shouldBackfillMargin(copy.get("aas_margin_percent"))) {
                        copy.put("aas_margin_percent", defaultMarginPercent);
                        changedItemsForOrder++;
                    }
                    updatedItems.add(copy);
                }
                if (!documentChanged && changedItemsForOrder == 0) {
                    continue;
                }
                Map<String, Object> payload = new HashMap<>();
                if (documentChanged) {
                    payload.put("aas_margin_percent", defaultMarginPercent);
                }
                if (changedItemsForOrder > 0) {
                    payload.put("items", updatedItems);
                }
                erpNextClient.updateResource("Sales Order", name, payload);
                if (documentChanged) {
                    ordersUpdated++;
                }
                itemsUpdated += changedItemsForOrder;
            }
            if (rows.size() < pageSize) {
                break;
            }
            start += pageSize;
        }
        return new MarginBackfillResult(ordersUpdated, itemsUpdated);
    }

    private int backfillMarginPercent(String doctype, String fieldname) {
        int updated = 0;
        int start = 0;
        final int pageSize = 500;
        while (true) {
            Map<String, Object> params = new HashMap<>();
            params.put("fields", "[\"name\",\"" + fieldname + "\"]");
            params.put("limit_page_length", pageSize);
            params.put("limit_start", start);
            List<Map<String, Object>> rows = erpNextClient.listResources(doctype, params);
            if (rows == null || rows.isEmpty()) {
                break;
            }
            for (Map<String, Object> row : rows) {
                if (row == null) {
                    continue;
                }
                String name = asText(row.get("name"));
                if (name.isBlank() || !shouldBackfillMargin(row.get(fieldname))) {
                    continue;
                }
                erpNextClient.updateResource(doctype, name, Map.of(fieldname, defaultMarginPercent));
                updated++;
            }
            if (rows.size() < pageSize) {
                break;
            }
            start += pageSize;
        }
        return updated;
    }

    private int backfillCategoryCodes() {
        int updated = 0;
        int start = 0;
        final int pageSize = 500;
        while (true) {
            Map<String, Object> params = new HashMap<>();
            params.put("fields", "[\"name\",\"item_group_name\",\"parent_item_group\",\"aas_category_code\"]");
            params.put("limit_page_length", pageSize);
            params.put("limit_start", start);
            List<Map<String, Object>> rows = erpNextClient.listResources("Item Group", params);
            if (rows == null || rows.isEmpty()) {
                break;
            }
            for (Map<String, Object> row : rows) {
                if (row == null) {
                    continue;
                }
                String name = asText(row.get("name"));
                String existing = asText(row.get("aas_category_code"));
                String parent = asText(row.get("parent_item_group"));
                if (name.isBlank() || !existing.isBlank() || parent.isBlank() || "All Item Groups".equals(name)) {
                    continue;
                }
                String source = firstText(row.get("item_group_name"), row.get("name"));
                String generated = catalogRoutingService.normalizeCodeSegment(source);
                if (generated.isBlank()) {
                    continue;
                }
                try {
                    erpNextClient.updateResource("Item Group", name, Map.of("aas_category_code", generated));
                    updated++;
                } catch (Exception ignored) {
                    // Best-effort backfill: skip problematic rows and continue with the rest.
                }
            }
            if (rows.size() < pageSize) {
                break;
            }
            start += pageSize;
        }
        return updated;
    }

    private boolean shouldBackfillMargin(Object value) {
        if (value == null) {
            return true;
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return true;
        }
        try {
            double margin = Double.parseDouble(text);
            return margin == 0.0 || margin == 10.0;
        } catch (NumberFormatException ex) {
            return false;
        }
    }

    private String asText(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private String firstText(Object... values) {
        for (Object value : values) {
            String text = asText(value);
            if (!text.isBlank()) {
                return text;
            }
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrap(Map<String, Object> resource) {
        if (resource == null) {
            return Map.of();
        }
        Object data = resource.get("data");
        if (data instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return resource;
    }

    private record MarginBackfillResult(int documentCount, int itemCount) {
    }

    private boolean ensureSupplierGroupRoot() {
        // ERPNext expects a valid Supplier Group on Supplier. Some test sites start without the root group,
        // which breaks vendor creation via API.
        final String doctype = "Supplier Group";
        final String name = "All Supplier Groups";
        try {
            erpNextClient.getResource(doctype, name);
            return false;
        } catch (Exception ignored) {
            // create
        }
        try {
            Map<String, Object> payload = new HashMap<>();
            payload.put("supplier_group_name", name);
            payload.put("is_group", 1);
            erpNextClient.createResource(doctype, payload);
            return true;
        } catch (Exception ex) {
            return false;
        }
    }

    private int ensureVendorSupplierCustomFields() {
        int changed = 0;
        for (VendorFieldSpec spec : vendorFieldRegistry.vendorFields()) {
            boolean didChange = customFieldProvisioner.ensure(
                    "Supplier",
                    spec.fieldname(),
                    spec.label(),
                    spec.fieldtype(),
                    spec.options(),
                    spec.insertAfter(),
                    spec.inListView(),
                    spec.required());
            if (didChange) {
                changed++;
            }
        }
        return changed;
    }

    private Map<String, Object> ensureDefaultUsers() {
        Map<String, Object> result = new HashMap<>();
        if (!defaultsEnabled) {
            result.put("defaultsEnabled", false);
            return result;
        }
        result.put("defaultsEnabled", true);
        boolean vendorSupplierCreated = ensureSupplier(defaultVendorSupplier);
        boolean shopCustomerCreated = ensureCustomer(defaultShopCustomer);
        boolean vendorUserCreated = ensureUser(
                defaultVendorEmail,
                defaultVendorName,
                defaultVendorPassword,
                vendorRole,
                defaultVendorSupplier,
                null);
        boolean shopUserCreated = ensureUser(
                defaultShopEmail,
                defaultShopName,
                defaultShopPassword,
                shopRole,
                null,
                defaultShopCustomer);
        boolean helperUserCreated = ensureUser(
                defaultHelperEmail,
                defaultHelperName,
                defaultHelperPassword,
                helperRole,
                null,
                null);
        result.put("vendorSupplierCreated", vendorSupplierCreated);
        result.put("shopCustomerCreated", shopCustomerCreated);
        result.put("vendorUserCreated", vendorUserCreated);
        result.put("shopUserCreated", shopUserCreated);
        result.put("helperUserCreated", helperUserCreated);
        return result;
    }

    private boolean ensureSupplier(String supplierName) {
        if (supplierName == null || supplierName.isBlank()) {
            return false;
        }
        if (resourceExists("Supplier", supplierName)) {
            return false;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("supplier_name", supplierName);
        payload.put("supplier_group", "All Supplier Groups");
        payload.put("aas_vendor_code", catalogRoutingService.normalizeCodeSegment(supplierName));
        erpNextClient.createResource("Supplier", payload);
        return true;
    }

    private boolean ensureCustomer(String customerName) {
        if (customerName == null || customerName.isBlank()) {
            return false;
        }
        if (resourceExists("Customer", customerName)) {
            return false;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("customer_name", customerName);
        erpNextClient.createResource("Customer", payload);
        return true;
    }

    private boolean ensureItem(String itemCode, String itemName, String description) {
        if (itemCode == null || itemCode.isBlank()) {
            return false;
        }
        if (resourceExists("Item", itemCode)) {
            return false;
        }
        ensureUom("Nos");
        String itemGroup = resolveItemGroup();
        if (itemGroup.isBlank()) {
            throw new IllegalStateException("No Item Group found in ERPNext; cannot create placeholder item.");
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("item_code", itemCode);
        payload.put("item_name", itemName == null || itemName.isBlank() ? itemCode : itemName);
        payload.put("item_group", itemGroup);
        payload.put("stock_uom", "Nos");
        payload.put("is_stock_item", 0);
        payload.put("is_sales_item", 1);
        payload.put("is_purchase_item", 0);
        payload.put("description", description == null ? "" : description);
        erpNextClient.createResource("Item", payload);
        return true;
    }

    private void ensureUom(String uomName) {
        if (uomName == null || uomName.isBlank()) {
            return;
        }
        if (resourceExists("UOM", uomName)) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("uom_name", uomName);
        payload.put("must_be_whole_number", 0);
        erpNextClient.createResource("UOM", payload);
    }

    private String resolveItemGroup() {
        // Prefer the standard root group if it exists, otherwise use any existing Item Group.
        if (resourceExists("Item Group", "All Item Groups")) {
            return "All Item Groups";
        }
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\"]");
        params.put("limit_page_length", "1");
        List<Map<String, Object>> groups = erpNextClient.listResources("Item Group", params);
        if (groups.isEmpty()) {
            return "";
        }
        Object name = groups.get(0).get("name");
        return name == null ? "" : name.toString();
    }

    private boolean ensureUser(
            String email,
            String fullName,
            String password,
            String role,
            String supplier,
            String customer) {
        if (email == null || email.isBlank()) {
            return false;
        }
        if (resourceExists("User", email)) {
            return false;
        }
        String resolvedName = fullName == null || fullName.isBlank() ? email : fullName.trim();
        String firstName = resolvedName;
        String lastName = "";
        int space = resolvedName.indexOf(' ');
        if (space > 0) {
            firstName = resolvedName.substring(0, space).trim();
            lastName = resolvedName.substring(space + 1).trim();
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("email", email);
        payload.put("first_name", firstName);
        if (!lastName.isBlank()) {
            payload.put("last_name", lastName);
        }
        payload.put("enabled", 1);
        payload.put("send_welcome_email", 0);
        payload.put("new_password", password);
        payload.put("user_type", "System User");
        if (supplier != null && !supplier.isBlank()) {
            payload.put("supplier", supplier);
        }
        if (customer != null && !customer.isBlank()) {
            payload.put("customer", customer);
        }
        if (role != null && !role.isBlank()) {
            payload.put("roles", List.of(Map.of("role", role)));
        }
        erpNextClient.createResource("User", payload);
        return true;
    }

    private boolean resourceExists(String doctype, String name) {
        try {
            Map<String, Object> data = erpNextClient.getResource(doctype, name);
            return data != null && !data.isEmpty();
        } catch (FeignException.NotFound ignored) {
            return false;
        }
    }

    private boolean ensureSalesInvoicePrintFormat() {
        String printFormatName = ensureSalesInvoicePrintFormatDoc();
        if (printFormatName.isBlank()) {
            return false;
        }
        boolean changed = false;
        int start = 0;
        final int pageSize = 200;
        while (true) {
            Map<String, Object> params = new HashMap<>();
            params.put("fields", "[\"name\",\"aas_sales_invoice_print_format\"]");
            params.put("limit_page_length", pageSize);
            params.put("limit_start", start);
            List<Map<String, Object>> companies = erpNextClient.listResources("Company", params);
            if (companies == null || companies.isEmpty()) {
                break;
            }
            for (Map<String, Object> row : companies) {
                String companyName = asText(row.get("name"));
                if (companyName.isBlank()) {
                    continue;
                }
                if (printFormatName.equals(asText(row.get("aas_sales_invoice_print_format")))) {
                    continue;
                }
                erpNextClient.updateResource("Company", companyName, Map.of("aas_sales_invoice_print_format", printFormatName));
                changed = true;
            }
            if (companies.size() < pageSize) {
                break;
            }
            start += pageSize;
        }
        return changed;
    }

    private String ensureSalesInvoicePrintFormatDoc() {
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\"]");
        params.put(
                "filters",
                "[[\"name\",\"=\",\"" + escape(SALES_INVOICE_PRINT_FORMAT_NAME) + "\"],[\"doc_type\",\"=\",\"Sales Invoice\"]]");
        params.put("limit_page_length", 1);
        List<Map<String, Object>> existing = erpNextClient.listResources("Print Format", params);
        Map<String, Object> payload = new HashMap<>();
        payload.put("name", SALES_INVOICE_PRINT_FORMAT_NAME);
        payload.put("doc_type", "Sales Invoice");
        payload.put("module", "Accounts");
        payload.put("custom_format", 1);
        payload.put("print_format_type", "Jinja");
        payload.put("raw_printing", 0);
        payload.put("disabled", 0);
        payload.put("html", SALES_INVOICE_PRINT_FORMAT_HTML);
        if (existing != null && !existing.isEmpty()) {
            erpNextClient.updateResource("Print Format", SALES_INVOICE_PRINT_FORMAT_NAME, payload);
            return SALES_INVOICE_PRINT_FORMAT_NAME;
        }
        Map<String, Object> created = erpNextClient.createResource("Print Format", payload);
        return asText(unwrap(created).get("name"));
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private boolean ensureCustomField(
            String dt,
            String fieldname,
            String label,
            String fieldtype,
            String options,
            String insertAfter) {
        return customFieldProvisioner.ensure(
                dt,
                fieldname,
                label,
                fieldtype,
                options,
                insertAfter,
                true,
                false);
    }
}
