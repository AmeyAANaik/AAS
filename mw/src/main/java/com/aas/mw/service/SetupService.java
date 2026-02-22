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

    private final ErpNextClient erpNextClient;
    private final CustomFieldProvisioner customFieldProvisioner;
    private final VendorFieldRegistry vendorFieldRegistry;
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

    public SetupService(
            ErpNextClient erpNextClient,
            CustomFieldProvisioner customFieldProvisioner,
            VendorFieldRegistry vendorFieldRegistry,
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
            @Value("${app.defaults.helper.password:helper123}") String defaultHelperPassword) {
        this.erpNextClient = erpNextClient;
        this.customFieldProvisioner = customFieldProvisioner;
        this.vendorFieldRegistry = vendorFieldRegistry;
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
                "DRAFT\nVENDOR_ASSIGNED\nVENDOR_PDF_RECEIVED\nVENDOR_BILL_CAPTURED\nSELL_ORDER_CREATED\nINVOICED\nAccepted\nPreparing\nReady\nDelivered",
                "aas_vendor");
        boolean salesOrderMarginField = ensureCustomField(
                "Sales Order",
                "aas_margin_percent",
                "Margin %",
                "Float",
                null,
                "aas_status");
        boolean branchImageField = ensureCustomField(
                "Sales Order",
                "aas_branch_image",
                "Branch Image",
                "Attach",
                null,
                "aas_margin_percent");
        boolean vendorPdfField = ensureCustomField(
                "Sales Order",
                "aas_vendor_pdf",
                "Vendor PDF",
                "Attach",
                null,
                "aas_branch_image");
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
        boolean vendorPurchaseInvoiceField = ensureCustomField(
                "Sales Order",
                "aas_pi_vendor",
                "Vendor Purchase Invoice",
                "Link",
                "Purchase Invoice",
                "aas_vendor_bill_date");
        boolean sellOrderTotalField = ensureCustomField(
                "Sales Order",
                "aas_sell_order_total",
                "Sell Order Total",
                "Currency",
                null,
                "aas_pi_vendor");
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
        boolean branchPlaceholderItem = ensureItem(
                "AAS-BRANCH-IMAGE",
                "Branch Image Placeholder",
                "Placeholder item for branch image orders.");
        Map<String, Object> result = new HashMap<>();
        result.put("vendorFieldCreated", vendorField);
        result.put("statusFieldCreated", statusField);
        result.put("salesOrderMarginFieldCreated", salesOrderMarginField);
        result.put("marginFieldCreated", marginField);
        result.put("vendorRateFieldCreated", vendorRateField);
        result.put("packagingUnitFieldCreated", packagingUnitField);
        result.put("soItemMarginFieldCreated", soItemMarginField);
        result.put("soItemVendorRateFieldCreated", soItemVendorRateField);
        result.put("branchPlaceholderItemCreated", branchPlaceholderItem);
        result.put("branchImageFieldCreated", branchImageField);
        result.put("vendorPdfFieldCreated", vendorPdfField);
        result.put("purchaseOrderFieldCreated", purchaseOrderField);
        result.put("branchSalesOrderFieldCreated", branchSalesOrderField);
        result.put("branchInvoiceFieldCreated", branchInvoiceField);
        result.put("vendorBillTotalFieldCreated", vendorBillTotalField);
        result.put("vendorBillRefFieldCreated", vendorBillRefField);
        result.put("vendorBillDateFieldCreated", vendorBillDateField);
        result.put("vendorPurchaseInvoiceFieldCreated", vendorPurchaseInvoiceField);
        result.put("sellOrderTotalFieldCreated", sellOrderTotalField);
        result.put("poSourceOrderFieldCreated", poSourceOrderField);
        result.put("purchaseInvoiceSourceOrderFieldCreated", purchaseInvoiceSourceOrderField);
        result.put("invoiceSourceOrderFieldCreated", invoiceSourceOrderField);
        result.put("vendorSupplierCustomFieldsChanged", ensureVendorSupplierCustomFields());
        result.putAll(ensureDefaultUsers());
        return result;
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
        Map<String, Object> payload = new HashMap<>();
        payload.put("item_code", itemCode);
        payload.put("item_name", itemName == null || itemName.isBlank() ? itemCode : itemName);
        payload.put("item_group", "All Item Groups");
        payload.put("stock_uom", "Nos");
        payload.put("is_stock_item", 0);
        payload.put("is_sales_item", 1);
        payload.put("is_purchase_item", 0);
        payload.put("description", description == null ? "" : description);
        erpNextClient.createResource("Item", payload);
        return true;
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
