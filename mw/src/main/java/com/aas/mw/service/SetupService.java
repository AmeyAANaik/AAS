package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import feign.FeignException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SetupService {

    private final ErpNextClient erpNextClient;
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
                "Accepted\nPreparing\nReady\nDelivered",
                "aas_vendor");
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
        Map<String, Object> result = new HashMap<>();
        result.put("vendorFieldCreated", vendorField);
        result.put("statusFieldCreated", statusField);
        result.put("marginFieldCreated", marginField);
        result.put("vendorRateFieldCreated", vendorRateField);
        result.put("soItemMarginFieldCreated", soItemMarginField);
        result.put("soItemVendorRateFieldCreated", soItemVendorRateField);
        result.putAll(ensureDefaultUsers());
        return result;
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
        if (customFieldExists(dt, fieldname)) {
            return false;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("dt", dt);
        payload.put("fieldname", fieldname);
        payload.put("label", label);
        payload.put("fieldtype", fieldtype);
        if (options != null && !options.isBlank()) {
            payload.put("options", options);
        }
        payload.put("insert_after", insertAfter);
        payload.put("in_list_view", 1);
        erpNextClient.createResource("Custom Field", payload);
        return true;
    }

    private boolean customFieldExists(String dt, String fieldname) {
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\"]");
        params.put("limit_page_length", "1");
        params.put("filters", "[[\"dt\",\"=\",\"" + dt + "\"],[\"fieldname\",\"=\",\"" + fieldname + "\"]]");
        List<Map<String, Object>> data = erpNextClient.listResources("Custom Field", params);
        return !data.isEmpty();
    }
}
