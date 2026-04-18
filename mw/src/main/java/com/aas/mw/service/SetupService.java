package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.meta.VendorFieldRegistry;
import com.aas.mw.meta.VendorFieldSpec;
import java.util.Arrays;
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
              .print-format { font-size: 12px; color: #111827; line-height: 1.45; }
              .aas-title-row, .aas-meta-table, .aas-party-table, .aas-items, .aas-summary { width: 100%; border-collapse: collapse; }
              .aas-title-row { margin-bottom: 16px; }
              .aas-title-row td { vertical-align: top; }
              .aas-brand { width: 58%; }
              .aas-invoice-meta { width: 42%; }
              .aas-brand-wrap { display: block; }
              .aas-logo-box { width: 198px; margin: 10px 0 12px; }
              .aas-logo { max-width: 198px; max-height: 198px; object-fit: contain; }
              .aas-company-name { font-size: 28px; font-weight: 700; margin: 0; letter-spacing: 0.01em; }
              .aas-company-line, .aas-bank-line, .aas-bill-line { margin: 0 0 2px; color: #374151; }
              .aas-gst-line { margin: 6px 0 0; font-weight: 600; }
              .aas-invoice-card { border: 1px solid #111827; padding: 10px 12px; }
              .aas-invoice-heading { font-size: 18px; font-weight: 700; text-align: center; letter-spacing: 0.08em; margin: 0 0 10px; }
              .aas-meta-table td { border: 1px solid #111827; padding: 6px 8px; vertical-align: top; }
              .aas-meta-label { font-weight: 600; width: 34%; white-space: nowrap; }
              .aas-party-table { margin-bottom: 16px; }
              .aas-party-table td { width: 50%; border: 1px solid #111827; vertical-align: top; padding: 10px 12px; }
              .aas-section-title { font-size: 11px; text-transform: uppercase; letter-spacing: 0.08em; font-weight: 700; margin: 0 0 8px; }
              .aas-items { margin-bottom: 16px; table-layout: fixed; page-break-inside: auto; }
              .aas-items tr { page-break-inside: avoid; page-break-after: auto; }
              .aas-items thead { display: table-header-group; }
              .aas-items tfoot { display: table-row-group; }
              .aas-items th, .aas-items td { border: 1px solid #111827; padding: 6px 6px; vertical-align: top; }
              .aas-items th { font-size: 10px; text-transform: uppercase; letter-spacing: 0.05em; text-align: center; }
              .aas-items td { font-size: 11px; }
              .aas-items .num { text-align: right; white-space: nowrap; }
              .aas-items .center { text-align: center; }
              .aas-item-name { font-weight: 600; overflow-wrap: break-word; word-wrap: break-word; }
              .aas-summary { width: 360px; margin: 12px 0 24px auto; table-layout: fixed; border-collapse: collapse; page-break-inside: avoid; }
              .aas-summary tr { page-break-inside: avoid; }
              .aas-summary td { border: 1px solid #111827; padding: 8px 10px; line-height: 1.35; vertical-align: middle; }
              .aas-summary .aas-summary-heading td { padding: 8px 10px; font-size: 11px; text-transform: uppercase; letter-spacing: 0.08em; font-weight: 700; text-align: left; background: #f3f4f6; color: #4b5563; }
              .aas-summary .aas-summary-label-cell { font-weight: 600; color: #111827; }
              .aas-summary .aas-summary-value-cell { text-align: right; white-space: nowrap; font-variant-numeric: tabular-nums; }
              .aas-summary .aas-summary-grand td { font-weight: 700; background: #eef2ff; font-size: 13px; }
              .aas-footer-grid { width: 100%; margin-top: 20px; border-collapse: separate; border-spacing: 0; border-top: 1px solid #111827; padding-top: 14px; }
              .aas-footer-grid td { vertical-align: top; }
              .aas-footer-left { width: 62%; padding-right: 18px; }
              .aas-footer-right { width: 38%; padding-left: 18px; border-left: 1px solid #d1d5db; }
              .aas-footer-heading { margin: 0 0 8px; font-size: 11px; text-transform: uppercase; letter-spacing: 0.08em; font-weight: 700; color: #4b5563; }
              .aas-inwords { font-weight: 600; color: #111827; }
              .aas-signatory-block { min-height: 116px; text-align: center; display: flex; flex-direction: column; justify-content: flex-end; }
              .aas-signature-image { max-width: 180px; max-height: 64px; object-fit: contain; margin: 0 auto 10px; }
              .aas-signatory-line { border-top: 1px solid #111827; margin: 0 20px 8px; }
              .aas-signatory-label { margin: 0; font-size: 11px; text-transform: uppercase; letter-spacing: 0.08em; color: #4b5563; }
              .aas-signatory-name { margin: 6px 0 0; font-weight: 700; color: #111827; }
              .aas-empty { color: #6b7280; }
            </style>
            {% set company_doc = frappe.get_doc("Company", doc.company) if doc.company else None %}
            {% set company_logo = (company_doc.company_logo or company_doc.logo or company_doc.letter_head_image) if company_doc else "" %}
            {% if company_logo and company_logo.startswith("http://localhost:8080") %}
            {% set company_logo = company_logo.replace("http://localhost:8080", "http://frontend:8080") %}
            {% endif %}
            {% if company_logo %}
            {% set company_logo = company_logo.replace(" ", "%20") %}
            {% endif %}
            {% set company_signature = company_doc.aas_authorized_signature if company_doc and company_doc.aas_authorized_signature else "" %}
            {% if company_signature and company_signature.startswith("http://localhost:8080") %}
            {% set company_signature = company_signature.replace("http://localhost:8080", "http://frontend:8080") %}
            {% endif %}
            {% if company_signature %}
            {% set company_signature = company_signature.replace(" ", "%20") %}
            {% endif %}
            {% set company_tax_id = (company_doc.tax_id or company_doc.gstin) if company_doc else "" %}
            {% set customer_doc = frappe.get_doc("Customer", doc.customer) if doc.customer else None %}
            {% set company_address = [company_doc.address_line_1, company_doc.address_line_2, company_doc.city, company_doc.state, company_doc.pincode] if company_doc else [] %}
            {% set branch_lines = [] %}
            {% if customer_doc and customer_doc.aas_branch_location %}{% set _ = branch_lines.append(customer_doc.aas_branch_location) %}{% endif %}
            {% if doc.customer_address %}{% set _ = branch_lines.append(doc.customer_address) %}{% endif %}
            {% if doc.contact_display %}{% set _ = branch_lines.append(doc.contact_display) %}{% endif %}
            {% if customer_doc and customer_doc.aas_invoice_email %}{% set _ = branch_lines.append("Invoice Email: " ~ customer_doc.aas_invoice_email) %}{% endif %}
            {% if customer_doc and customer_doc.aas_whatsapp_number %}{% set _ = branch_lines.append("WhatsApp: " ~ customer_doc.aas_whatsapp_number) %}{% endif %}
            {% if customer_doc and (not customer_doc.aas_whatsapp_number) and customer_doc.aas_whatsapp_group_name %}{% set _ = branch_lines.append("WhatsApp Group: " ~ customer_doc.aas_whatsapp_group_name) %}{% endif %}
            {% set rounding_adjustment = frappe.utils.flt(doc.aas_rounding_adjustment if doc.aas_rounding_adjustment else doc.rounding_adjustment, 2) %}
            {% set invoice_total = frappe.utils.flt(doc.grand_total if doc.grand_total else 0, 2) %}
            {% set grand_total = frappe.utils.flt(invoice_total + rounding_adjustment, 2) %}
            {% set bank_lines = [] %}
            {% if company_doc and company_doc.aas_bank_beneficiary_name %}{% set _ = bank_lines.append("A/C Name: " ~ company_doc.aas_bank_beneficiary_name) %}{% endif %}
            {% if company_doc and company_doc.aas_bank_name %}{% set _ = bank_lines.append("Bank: " ~ company_doc.aas_bank_name) %}{% endif %}
            {% if company_doc and company_doc.aas_bank_account_number %}{% set _ = bank_lines.append("A/C No: " ~ company_doc.aas_bank_account_number) %}{% endif %}
            {% if company_doc and company_doc.aas_bank_ifsc_code %}{% set _ = bank_lines.append("IFSC: " ~ company_doc.aas_bank_ifsc_code) %}{% endif %}
            {% if company_doc and company_doc.aas_bank_branch %}{% set _ = bank_lines.append("Branch: " ~ company_doc.aas_bank_branch) %}{% endif %}
            <table class="aas-title-row">
              <tr>
                <td class="aas-brand">
                  <div class="aas-brand-wrap">
                    {% if company_logo %}
                    <div class="aas-logo-box">
                      <img class="aas-logo" src="{{ company_logo }}" alt="{{ doc.company }}" />
                    </div>
                    {% endif %}
                    <p class="aas-company-name">{{ doc.company_name or doc.company }}</p>
                    <div>
                      {% for address_part in company_address %}
                      {% if address_part %}
                      <p class="aas-company-line">{{ address_part }}</p>
                      {% endif %}
                      {% endfor %}
                      {% if company_tax_id %}
                      <p class="aas-gst-line">GSTIN: {{ company_tax_id }}</p>
                      {% endif %}
                    </div>
                  </div>
                </td>
                <td class="aas-invoice-meta">
                  <div class="aas-invoice-card">
                    <p class="aas-invoice-heading">Tax Invoice</p>
                    <table class="aas-meta-table">
                      <tr><td class="aas-meta-label">Invoice No</td><td>{{ doc.name }}</td></tr>
                      <tr><td class="aas-meta-label">Invoice Date</td><td>{{ frappe.utils.formatdate(doc.posting_date) }}</td></tr>
                      <tr><td class="aas-meta-label">Due Date</td><td>{{ frappe.utils.formatdate(doc.due_date) if doc.due_date else "-" }}</td></tr>
                      <tr><td class="aas-meta-label">Source Order</td><td>{{ doc.aas_source_sales_order or "-" }}</td></tr>
                    </table>
                  </div>
                </td>
              </tr>
            </table>
            <table class="aas-party-table">
              <tr>
                <td>
                  <p class="aas-section-title">Bill To</p>
                  <p class="aas-bill-line"><strong>{{ doc.customer_name or doc.customer }}</strong></p>
                  {% for branch_line in branch_lines %}
                  {% if branch_line %}
                  <p class="aas-bill-line">{{ branch_line }}</p>
                  {% endif %}
                  {% endfor %}
                </td>
                <td>
                  <p class="aas-section-title">Bank Details</p>
                  {% if bank_lines %}
                  {% for bank_line in bank_lines %}
                  <p class="aas-bank-line">{{ bank_line }}</p>
                  {% endfor %}
                  {% else %}
                  <p class="aas-bank-line aas-empty">Bank details not configured.</p>
                  {% endif %}
                </td>
              </tr>
            </table>
            <table class="aas-items">
              <thead>
                <tr>
                  <th style="width: 5%;">SR</th>
                  <th style="width: 22%;">Item</th>
                  <th style="width: 9%;">Qty</th>
                  <th style="width: 7%;">UOM</th>
                  <th style="width: 12%;">Rate</th>
                  <th style="width: 7%;">GST %</th>
                  <th style="width: 13%;">Amount Before Tax</th>
                  <th style="width: 12%;">Rate With GST</th>
                  <th style="width: 13%;">Total Amount</th>
                </tr>
              </thead>
              <tbody>
                {% set totals = namespace(goods_taxable=0.0, taxable=0.0, gst=0.0, transport=0.0, visible_rows=0) %}
                {% for item in doc.items %}
                {% set gst_percent = item.aas_gst_percent if item.aas_gst_percent else 0 %}
                {% set taxable_rate = frappe.utils.flt(item.net_rate if item.net_rate else item.rate, 2) %}
                {% set taxable_amount = frappe.utils.flt(item.net_amount if item.net_amount else item.amount, 2) %}
                {% set gst_amount = frappe.utils.flt(taxable_amount * gst_percent / 100, 2) %}
                {% set rate_with_gst = frappe.utils.flt(taxable_rate + (taxable_rate * gst_percent / 100), 2) %}
                {% set total_amount = frappe.utils.flt(taxable_amount + gst_amount, 2) %}
                {% set is_transport = item.item_code == "AAS-TRANSPORT-CHARGE" %}
                {% set display_name = "Transport / Additional Spend" if is_transport else (item.description or item.item_name or item.item_code or "-") %}
                {% set totals.taxable = totals.taxable + taxable_amount %}
                {% set totals.gst = totals.gst + gst_amount %}
                {% if is_transport %}
                {% set totals.transport = totals.transport + taxable_amount %}
                {% else %}
                {% set totals.goods_taxable = totals.goods_taxable + taxable_amount %}
                {% endif %}
                {% if not is_transport %}
                {% set totals.visible_rows = totals.visible_rows + 1 %}
                <tr>
                  <td class="center">{{ totals.visible_rows }}</td>
                  <td class="aas-item-name">{{ display_name }}</td>
                  <td class="num">{{ frappe.utils.flt(item.qty, 2) }}</td>
                  <td class="center">{{ item.uom or item.stock_uom or "-" }}</td>
                  <td class="num">{{ frappe.utils.fmt_money(taxable_rate, currency=doc.currency) }}</td>
                  <td class="num">{{ frappe.utils.flt(gst_percent, 2) }}</td>
                  <td class="num">{{ frappe.utils.fmt_money(taxable_amount, currency=doc.currency) }}</td>
                  <td class="num">{{ frappe.utils.fmt_money(rate_with_gst, currency=doc.currency) }}</td>
                  <td class="num">{{ frappe.utils.fmt_money(total_amount, currency=doc.currency) }}</td>
                </tr>
                {% endif %}
                {% endfor %}
              </tbody>
            </table>
            <table class="aas-summary">
              <tbody>
              <tr class="aas-summary-heading">
                <td class="aas-summary-label-cell" style="width: 58%;">Summary</td>
                <td class="aas-summary-value-cell" style="width: 42%;">Amount</td>
              </tr>
              <tr>
                <td class="aas-summary-label-cell">Goods Total Before Tax</td>
                <td class="aas-summary-value-cell">{{ frappe.utils.fmt_money(totals.goods_taxable, currency=doc.currency) }}</td>
              </tr>
              {% if totals.transport %}
              <tr>
                <td class="aas-summary-label-cell">Transport / Additional Spend</td>
                <td class="aas-summary-value-cell">{{ frappe.utils.fmt_money(totals.transport, currency=doc.currency) }}</td>
              </tr>
              {% endif %}
              <tr>
                <td class="aas-summary-label-cell">Taxable Total</td>
                <td class="aas-summary-value-cell">{{ frappe.utils.fmt_money(totals.taxable, currency=doc.currency) }}</td>
              </tr>
              <tr>
                <td class="aas-summary-label-cell">GST Total</td>
                <td class="aas-summary-value-cell">{{ frappe.utils.fmt_money(totals.gst, currency=doc.currency) }}</td>
              </tr>
              <tr>
                <td class="aas-summary-label-cell">Invoice Total</td>
                <td class="aas-summary-value-cell">{{ frappe.utils.fmt_money(invoice_total, currency=doc.currency) }}</td>
              </tr>
              {% if rounding_adjustment %}
              <tr>
                <td class="aas-summary-label-cell">Rounding Adjustment</td>
                <td class="aas-summary-value-cell">{{ frappe.utils.fmt_money(rounding_adjustment, currency=doc.currency) }}</td>
              </tr>
              {% endif %}
              <tr class="aas-summary-grand">
                <td class="aas-summary-label-cell">Grand Total</td>
                <td class="aas-summary-value-cell">{{ frappe.utils.fmt_money(grand_total, currency=doc.currency) }}</td>
              </tr>
              </tbody>
            </table>
            <table class="aas-footer-grid">
              <tr>
                <td class="aas-footer-left">
                  <div class="aas-footer-section">
                    <p class="aas-footer-heading">Amount In Words</p>
                    <div class="aas-inwords">{{ doc.in_words or frappe.utils.money_in_words(grand_total, doc.currency) }}</div>
                  </div>
                </td>
                <td class="aas-footer-right">
                  <div class="aas-signatory-block">
                    {% if company_signature %}
                    <img class="aas-signature-image" src="{{ company_signature }}" alt="Authorized signature" />
                    {% endif %}
                    <div class="aas-signatory-line"></div>
                    <p class="aas-signatory-label">Authorized Signatory</p>
                    <p class="aas-signatory-name">For {{ doc.company_name or doc.company }}</p>
                  </div>
                </td>
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
    private final List<String> helperAdditionalRoles;
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
            @Value("${app.roles.helper-extra:Accounts User,Sales User}") String helperAdditionalRoles,
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
        this.helperAdditionalRoles = parseRoles(helperAdditionalRoles);
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
        boolean branchInvoiceEmailField = ensureCustomField(
                "Customer",
                "aas_invoice_email",
                "Invoice Email",
                "Data",
                null,
                "aas_credit_days");
        boolean branchWhatsappNumberField = ensureCustomField(
                "Customer",
                "aas_whatsapp_number",
                "WhatsApp Number",
                "Data",
                null,
                "aas_invoice_email");
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
        boolean purchaseInvoiceVersionStatusField = ensureCustomField(
                "Purchase Invoice",
                "aas_invoice_version_status",
                "Invoice Version Status",
                "Select",
                "CURRENT\nOLD",
                "aas_source_sales_order");
        boolean purchaseInvoiceReplacedByField = ensureCustomField(
                "Purchase Invoice",
                "aas_replaced_by",
                "Replaced By",
                "Data",
                null,
                "aas_invoice_version_status");
        boolean invoiceSourceOrderField = ensureCustomField(
                "Sales Invoice",
                "aas_source_sales_order",
                "Source Sales Order",
                "Link",
                "Sales Order",
                "customer");
        boolean invoiceVersionStatusField = ensureCustomField(
                "Sales Invoice",
                "aas_invoice_version_status",
                "Invoice Version Status",
                "Select",
                "CURRENT\nOLD",
                "aas_source_sales_order");
        boolean invoiceReplacedByField = ensureCustomField(
                "Sales Invoice",
                "aas_replaced_by",
                "Replaced By",
                "Data",
                null,
                "aas_invoice_version_status");
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
        boolean companyBankBeneficiaryField = ensureCustomField(
                "Company",
                "aas_bank_beneficiary_name",
                "Bank Beneficiary Name",
                "Data",
                null,
                "aas_sales_invoice_print_format");
        boolean companyBankNameField = ensureCustomField(
                "Company",
                "aas_bank_name",
                "Bank Name",
                "Data",
                null,
                "aas_bank_beneficiary_name");
        boolean companyBankAccountNumberField = ensureCustomField(
                "Company",
                "aas_bank_account_number",
                "Bank Account Number",
                "Data",
                null,
                "aas_bank_name");
        boolean companyBankIfscField = ensureCustomField(
                "Company",
                "aas_bank_ifsc_code",
                "Bank IFSC Code",
                "Data",
                null,
                "aas_bank_account_number");
        boolean companyBankBranchField = ensureCustomField(
                "Company",
                "aas_bank_branch",
                "Bank Branch",
                "Data",
                null,
                "aas_bank_ifsc_code");
        boolean companyAuthorizedSignatureField = ensureCustomField(
                "Company",
                "aas_authorized_signature",
                "Authorized Signature",
                "Data",
                null,
                "aas_bank_branch");
        boolean userFeatureAllowField = ensureCustomField(
                "User",
                UserFeatureService.FEATURE_ALLOW_FIELD,
                "AAS Feature Allow JSON",
                "Small Text",
                null,
                "location");
        boolean userFeatureDenyField = ensureCustomField(
                "User",
                UserFeatureService.FEATURE_DENY_FIELD,
                "AAS Feature Deny JSON",
                "Small Text",
                null,
                UserFeatureService.FEATURE_ALLOW_FIELD);
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
        boolean itemGstField = ensureCustomField(
                "Item",
                "aas_gst_percent",
                "GST %",
                "Float",
                null,
                "aas_vendor_hsn_code");
        boolean itemReviewStatusField = ensureCustomField(
                "Item",
                "aas_review_status",
                "Review Status",
                "Select",
                "PENDING_REVIEW\nAPPROVED\nMERGED\nREJECTED",
                "aas_gst_percent");
        boolean itemReviewSourceOrderField = ensureCustomField(
                "Item",
                "aas_review_source_order",
                "Review Source Order",
                "Link",
                "Sales Order",
                "aas_review_status");
        boolean itemReviewSourceInvoiceField = ensureCustomField(
                "Item",
                "aas_review_source_invoice_ref",
                "Review Source Invoice Ref",
                "Data",
                null,
                "aas_review_source_order");
        boolean itemReviewCreatedAtField = ensureCustomField(
                "Item",
                "aas_review_created_at",
                "Review Created At",
                "Datetime",
                null,
                "aas_review_source_invoice_ref");
        boolean itemReviewCreatedByField = ensureCustomField(
                "Item",
                "aas_review_created_by",
                "Review Created By",
                "Data",
                null,
                "aas_review_created_at");
        boolean itemReviewNotesField = ensureCustomField(
                "Item",
                "aas_review_notes",
                "Review Notes",
                "Small Text",
                null,
                "aas_review_created_by");
        boolean itemReviewDefaultMarginField = ensureCustomField(
                "Item",
                "aas_review_default_margin_used",
                "Review Default Margin Used",
                "Check",
                null,
                "aas_review_notes");
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
        boolean paymentReviewStatusField = ensureCustomField(
                "Payment Entry",
                "aas_payment_review_status",
                "Review Status",
                "Select",
                "UNDER_REVIEW\nAPPROVED\nREJECTED",
                "party");
        boolean paymentCategoryField = ensureCustomField(
                "Payment Entry",
                "aas_category",
                "Category",
                "Link",
                "Item Group",
                "aas_payment_review_status");
        boolean paymentDueAmountField = ensureCustomField(
                "Payment Entry",
                "aas_due_amount",
                "Due Amount",
                "Currency",
                null,
                "aas_category");
        boolean paymentReviewNotesField = ensureCustomField(
                "Payment Entry",
                "aas_payment_review_notes",
                "Review Notes",
                "Small Text",
                null,
                "aas_payment_review_status");
        boolean paymentCreatedByField = ensureCustomField(
                "Payment Entry",
                "aas_payment_created_by",
                "Created By",
                "Data",
                null,
                "aas_payment_review_notes");
        boolean paymentCreatedAtField = ensureCustomField(
                "Payment Entry",
                "aas_payment_created_at",
                "Created At",
                "Datetime",
                null,
                "aas_payment_created_by");
        boolean paymentReviewedByField = ensureCustomField(
                "Payment Entry",
                "aas_payment_reviewed_by",
                "Reviewed By",
                "Data",
                null,
                "aas_payment_created_at");
        boolean paymentReviewedAtField = ensureCustomField(
                "Payment Entry",
                "aas_payment_reviewed_at",
                "Reviewed At",
                "Datetime",
                null,
                "aas_payment_reviewed_by");
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
        result.put("paymentReviewStatusFieldCreated", paymentReviewStatusField);
        result.put("paymentCategoryFieldCreated", paymentCategoryField);
        result.put("paymentDueAmountFieldCreated", paymentDueAmountField);
        result.put("paymentReviewNotesFieldCreated", paymentReviewNotesField);
        result.put("paymentCreatedByFieldCreated", paymentCreatedByField);
        result.put("paymentCreatedAtFieldCreated", paymentCreatedAtField);
        result.put("paymentReviewedByFieldCreated", paymentReviewedByField);
        result.put("paymentReviewedAtFieldCreated", paymentReviewedAtField);
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
        result.put("purchaseInvoiceVersionStatusFieldCreated", purchaseInvoiceVersionStatusField);
        result.put("purchaseInvoiceReplacedByFieldCreated", purchaseInvoiceReplacedByField);
        result.put("invoiceSourceOrderFieldCreated", invoiceSourceOrderField);
        result.put("invoiceVersionStatusFieldCreated", invoiceVersionStatusField);
        result.put("invoiceReplacedByFieldCreated", invoiceReplacedByField);
        result.put("invoiceRoundingAdjustmentFieldCreated", invoiceRoundingAdjustmentField);
        result.put("companyInvoicePrintFormatFieldCreated", companyInvoicePrintFormatField);
        result.put("companyBankBeneficiaryFieldCreated", companyBankBeneficiaryField);
        result.put("companyBankNameFieldCreated", companyBankNameField);
        result.put("companyBankAccountNumberFieldCreated", companyBankAccountNumberField);
        result.put("companyBankIfscFieldCreated", companyBankIfscField);
        result.put("companyBankBranchFieldCreated", companyBankBranchField);
        result.put("companyAuthorizedSignatureFieldCreated", companyAuthorizedSignatureField);
        result.put("userFeatureAllowFieldCreated", userFeatureAllowField);
        result.put("userFeatureDenyFieldCreated", userFeatureDenyField);
        result.put("salesInvoicePrintFormatEnsured", ensureSalesInvoicePrintFormat());
        result.put("salesOrderCategoryFieldCreated", salesOrderCategoryField);
        result.put("categoryCodeFieldCreated", categoryCodeField);
        result.put("supplierGroupEnsured", ensureSupplierGroupRoot());
        result.put("vendorSupplierCustomFieldsChanged", ensureVendorSupplierCustomFields());
        result.put("itemVendorFieldCreated", itemVendorField);
        result.put("itemVendorHsnFieldCreated", itemVendorHsnField);
        result.put("itemGstFieldCreated", itemGstField);
        result.put("itemReviewStatusFieldCreated", itemReviewStatusField);
        result.put("itemReviewSourceOrderFieldCreated", itemReviewSourceOrderField);
        result.put("itemReviewSourceInvoiceFieldCreated", itemReviewSourceInvoiceField);
        result.put("itemReviewCreatedAtFieldCreated", itemReviewCreatedAtField);
        result.put("itemReviewCreatedByFieldCreated", itemReviewCreatedByField);
        result.put("itemReviewNotesFieldCreated", itemReviewNotesField);
        result.put("itemReviewDefaultMarginFieldCreated", itemReviewDefaultMarginField);
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
                helperRoles(),
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
        } else {
            payload.put("customer", "");
        }
        if (supplier == null || supplier.isBlank()) {
            payload.put("supplier", "");
        }
        if (role != null && !role.isBlank()) {
            payload.put("roles", List.of(Map.of("role", role)));
        }
        if (resourceExists("User", email)) {
            erpNextClient.updateResource("User", email, payload);
            return true;
        }
        erpNextClient.createResource("User", payload);
        return true;
    }

    private boolean ensureUser(
            String email,
            String fullName,
            String password,
            List<String> roles,
            String supplier,
            String customer) {
        if (email == null || email.isBlank()) {
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
        } else {
            payload.put("customer", "");
        }
        if (supplier == null || supplier.isBlank()) {
            payload.put("supplier", "");
        }
        if (roles != null && !roles.isEmpty()) {
            payload.put("roles", roles.stream()
                    .filter(role -> role != null && !role.isBlank())
                    .distinct()
                    .map(role -> Map.of("role", role))
                    .toList());
        }
        if (resourceExists("User", email)) {
            erpNextClient.updateResource("User", email, payload);
            return true;
        }
        erpNextClient.createResource("User", payload);
        return true;
    }

    private List<String> helperRoles() {
        return Arrays.stream(
                        (helperRole + "," + String.join(",", helperAdditionalRoles)).split(","))
                .map(String::trim)
                .filter(role -> !role.isBlank())
                .distinct()
                .toList();
    }

    private List<String> parseRoles(String value) {
        if (value == null || value.isBlank()) {
            return List.of();
        }
        return Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(role -> !role.isBlank())
                .distinct()
                .toList();
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
