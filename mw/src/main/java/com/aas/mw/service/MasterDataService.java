package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.dto.FieldsRequest;
import com.aas.mw.dto.ParsedItem;
import com.aas.mw.dto.UploadedFileInfo;
import com.aas.mw.meta.VendorFieldMapper;
import com.aas.mw.meta.VendorFieldRegistry;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Set;
import java.util.Locale;
import java.util.LinkedHashSet;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.multipart.MultipartFile;

@Service
public class MasterDataService {
    private static final String SYSTEM_BRANCH_IMAGE_ITEM = "AAS-SYSTEM-BRANCH-IMAGE";
    private static final DateTimeFormatter ERP_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ErpNextClient erpNextClient;
    private final VendorFieldRegistry vendorFieldRegistry;
    private final ObjectMapper objectMapper;
    private final OcrService ocrService;
    private final VendorInvoiceTemplateParser templateParser;
    private final NativeLayoutInvoiceService nativeLayoutInvoiceService;
    private final InvoiceTemplateModelService invoiceTemplateModelService;
    private final CatalogRoutingService catalogRoutingService;
    private final ErpNextFileService erpNextFileService;
    private final UomService uomService;
    private final String erpPublicBaseUrl;

    public MasterDataService(
            ErpNextClient erpNextClient,
            VendorFieldRegistry vendorFieldRegistry,
            ObjectMapper objectMapper,
            OcrService ocrService,
            VendorInvoiceTemplateParser templateParser,
            NativeLayoutInvoiceService nativeLayoutInvoiceService,
            InvoiceTemplateModelService invoiceTemplateModelService,
            CatalogRoutingService catalogRoutingService,
            ErpNextFileService erpNextFileService,
            UomService uomService,
            @Value("${erpnext.public-base-url:${erpnext.base-url}}") String erpPublicBaseUrl) {
        this.erpNextClient = erpNextClient;
        this.vendorFieldRegistry = vendorFieldRegistry;
        this.objectMapper = objectMapper;
        this.ocrService = ocrService;
        this.templateParser = templateParser;
        this.nativeLayoutInvoiceService = nativeLayoutInvoiceService;
        this.invoiceTemplateModelService = invoiceTemplateModelService;
        this.catalogRoutingService = catalogRoutingService;
        this.erpNextFileService = erpNextFileService;
        this.uomService = uomService;
        this.erpPublicBaseUrl = erpPublicBaseUrl;
    }

    public List<Map<String, Object>> listItems() {
        Map<String, Object> params = new HashMap<>();
        params.put(
                "fields",
                "[\"name\",\"item_name\",\"item_code\",\"item_group\",\"stock_uom\",\"aas_margin_percent\",\"aas_vendor_rate\",\"aas_packaging_unit\",\"aas_vendor\",\"aas_vendor_hsn_code\",\"aas_gst_percent\"]");
        params.put("filters", activeItemFiltersJson());
        params.put("limit_page_length", 1000);
        return erpNextClient.listResources("Item", params);
    }

    public Map<String, Object> listItemsPaged(int page, int size, String search, String sort, String dir) {
        int safePage = Math.max(page, 1);
        int safeSize = Math.min(Math.max(size, 1), 200);
        String safeDir = "desc".equalsIgnoreCase(dir) ? "desc" : "asc";
        String orderBy = resolveItemOrderBy(sort, safeDir);

        Map<String, Object> params = new HashMap<>();
        params.put(
                "fields",
                "[\"name\",\"item_name\",\"item_code\",\"item_group\",\"stock_uom\",\"aas_margin_percent\",\"aas_vendor_rate\",\"aas_packaging_unit\",\"aas_vendor\",\"aas_vendor_hsn_code\",\"aas_gst_percent\"]");
        params.put("limit_start", (safePage - 1) * safeSize);
        params.put("limit_page_length", safeSize);
        params.put("order_by", orderBy);
        params.put("filters", activeItemFiltersJson());

        String trimmed = search == null ? "" : search.trim();
        if (!trimmed.isEmpty()) {
            params.put("or_filters", buildItemSearchFilters(trimmed));
        }

        List<Map<String, Object>> items = erpNextClient.listResources("Item", params);

        Map<String, Object> countParams = new HashMap<>();
        countParams.put("filters", params.get("filters"));
        if (!trimmed.isEmpty()) {
            countParams.put("or_filters", params.get("or_filters"));
        }
        long total = erpNextClient.getCount("Item", countParams);

        Map<String, Object> response = new HashMap<>();
        response.put("items", items);
        response.put("total", total);
        response.put("page", safePage);
        response.put("size", safeSize);
        return response;
    }

    public List<Map<String, Object>> listVendors() {
        VendorFieldMapper mapper = new VendorFieldMapper(vendorFieldRegistry.vendorFields());
        Map<String, Object> params = new HashMap<>();
        params.put("fields", mapper.erpListFieldsJson());
        return erpNextClient.listResources("Supplier", params).stream()
                .map(mapper::withApiAliases)
                .map(this::resolveVendorTemplateFileUrl)
                .toList();
    }

    public List<Map<String, Object>> listCategories() {
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\",\"item_group_name\",\"parent_item_group\",\"aas_category_code\"]");
        params.put("limit_page_length", 1000);
        return erpNextClient.listResources("Item Group", params);
    }

    public List<Map<String, Object>> listShops() {
        Map<String, Object> params = new HashMap<>();
        params.put(
                "fields",
                "[\"name\",\"customer_name\",\"customer_type\",\"customer_group\",\"territory\","
                        + "\"aas_branch_location\",\"aas_whatsapp_group_name\",\"aas_credit_days\","
                        + "\"aas_invoice_email\",\"aas_whatsapp_number\",\"tax_id\",\"aas_food_license_no\","
                        + "\"aas_is_deleted\",\"aas_deleted_at\",\"disabled\"]");
        return erpNextClient.listResources("Customer", params).stream()
                .filter(this::isNotSoftDeletedBranch)
                .toList();
    }

    private boolean isNotSoftDeletedBranch(Map<String, Object> branch) {
        if (branch == null || branch.isEmpty()) {
            return false;
        }
        if (asFlag(branch.get("aas_is_deleted")) || !asText(branch.get("aas_deleted_at")).isBlank()) {
            return false;
        }
        String customerName = asText(branch.get("customer_name")).trim();
        if (customerName.startsWith("[DELETED]")) {
            return false;
        }
        // Some ERPNext role configurations allow updating these flags, but do not permit
        // listing them in query fields. In that case, perform a targeted fetch for disabled
        // branches to determine whether they're soft-deleted.
        if (!branch.containsKey("aas_is_deleted") && !branch.containsKey("aas_deleted_at") && asFlag(branch.get("disabled"))) {
            String branchId = asText(branch.get("name")).trim();
            if (!branchId.isBlank()) {
                try {
                    Map<String, Object> full = unwrapResource(erpNextClient.getResource("Customer", branchId));
                    if (asFlag(full.get("aas_is_deleted")) || !asText(full.get("aas_deleted_at")).isBlank()) {
                        return false;
                    }
                } catch (Exception ignored) {
                    // If we can't confirm deletion state, keep the branch visible.
                }
            }
        }
        return true;
    }

    public List<Map<String, Object>> listCompanies() {
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\",\"abbr\",\"default_currency\"]");
        params.put("filters", "[[\"Company\",\"is_group\",\"=\",0]]");
        return erpNextClient.listResources("Company", params);
    }

    public Map<String, Object> getCompanyContext(String preferredCompanyId, String preferredBranchId) {
        List<Map<String, Object>> companies = listCompanies();
        List<Map<String, Object>> branches = listShops();

        String companyId = hasText(preferredCompanyId)
                ? preferredCompanyId
                : companies.stream().map(company -> asText(company.get("name"))).filter(this::hasText).findFirst().orElse("");
        String branchId = hasText(preferredBranchId)
                ? preferredBranchId
                : branches.stream().map(branch -> asText(branch.get("name"))).filter(this::hasText).findFirst().orElse("");

        Map<String, Object> company = companyId.isBlank() ? Map.of() : getCompanyProfile(companyId);
        Map<String, Object> branch = branchId.isBlank() ? Map.of() : getBranchProfile(branchId);

        return Map.of(
                "company", company,
                "branch", branch,
                "companies", companies,
                "branches", branches);
    }

    public Map<String, Object> getCompanyProfile(String id) {
        if (id == null || id.isBlank()) {
            return Map.of();
        }
        Map<String, Object> company = unwrapResource(erpNextClient.getResource("Company", id));
        if (company.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Company not found.");
        }
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", asText(company.get("name")));
        profile.put("name", asText(company.get("company_name")).isBlank() ? asText(company.get("name")) : asText(company.get("company_name")));
        profile.put("abbr", asText(company.get("abbr")));
        profile.put("default_currency", asText(company.get("default_currency")));
        profile.put("country", asText(company.get("country")));
        profile.put("default_letter_head", asText(company.get("default_letter_head")));
        profile.put("tax_id", firstText(company.get("tax_id"), company.get("gstin")));
        profile.put("logo_url", firstText(company.get("company_logo"), company.get("logo"), company.get("letter_head_image")));
        profile.put("signature_url", asText(company.get("aas_authorized_signature")));
        profile.put("bank_beneficiary_name", asText(company.get("aas_bank_beneficiary_name")));
        profile.put("bank_name", asText(company.get("aas_bank_name")));
        profile.put("bank_account_number", asText(company.get("aas_bank_account_number")));
        profile.put("bank_ifsc_code", asText(company.get("aas_bank_ifsc_code")));
        profile.put("bank_branch", asText(company.get("aas_bank_branch")));
        profile.put("invoice_email", asText(company.get("aas_invoice_email")));
        profile.put("whatsapp_number", asText(company.get("aas_whatsapp_number")));
        return profile;
    }

    public Map<String, Object> updateCompanyProfile(String id, FieldsRequest request) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Company id is required.");
        }
        Map<String, Object> fields = request == null || request.getFields() == null ? Map.of() : request.getFields();
        String companyId = id.trim();
        String requestedDisplayName = asText(fields.getOrDefault("company_name", fields.getOrDefault("name", ""))).trim();
        Map<String, Object> payload = new HashMap<>();
        copyIfPresent(fields, payload, "company_name", "name");
        copyIfPresent(fields, payload, "company_name");
        copyIfPresent(fields, payload, "abbr");
        copyIfPresent(fields, payload, "default_currency");
        copyIfPresent(fields, payload, "country");
        copyIfPresent(fields, payload, "default_letter_head");
        copyIfPresent(fields, payload, "tax_id");
        copyIfPresent(fields, payload, "company_logo");
        copyIfPresent(fields, payload, "logo");
        copyIfPresent(fields, payload, "aas_authorized_signature", "signature_url");
        copyIfPresent(fields, payload, "aas_bank_beneficiary_name", "bank_beneficiary_name");
        copyIfPresent(fields, payload, "aas_bank_name", "bank_name");
        copyIfPresent(fields, payload, "aas_bank_account_number", "bank_account_number");
        copyIfPresent(fields, payload, "aas_bank_ifsc_code", "bank_ifsc_code");
        copyIfPresent(fields, payload, "aas_bank_branch", "bank_branch");
        copyIfPresent(fields, payload, "aas_invoice_email", "invoice_email");
        copyIfPresent(fields, payload, "aas_whatsapp_number", "whatsapp_number");

        // ERPNext often uses the document name as the effective company label across the system.
        // Attempt a rename when the requested display name differs from the current document id.
        // If the rename fails (permissions/constraints), fall back to updating fields only.
        if (!requestedDisplayName.isBlank() && !requestedDisplayName.equals(companyId)) {
            try {
                Map<String, Object> renamePayload = new HashMap<>();
                renamePayload.put("doctype", "Company");
                renamePayload.put("old_name", companyId);
                renamePayload.put("new_name", requestedDisplayName);
                renamePayload.put("merge", 0);
                erpNextClient.postMethod("frappe.client.rename_doc", renamePayload);
                companyId = requestedDisplayName;
            } catch (Exception ignored) {
                // Ignore rename failures and continue with field updates on the original id.
            }
        }
        if (payload.isEmpty()) {
            return getCompanyProfile(companyId);
        }
        erpNextClient.updateResource("Company", companyId, payload);
        return getCompanyProfile(companyId);
    }

    public Map<String, Object> uploadCompanyLogo(String id, MultipartFile file, String sessionCookie) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Company id is required.");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Logo file is required.");
        }
        UploadedFileInfo uploaded = erpNextFileService.uploadFile(
                "Company",
                id,
                resolveCompanyLogoFilename(file.getOriginalFilename()),
                file,
                sessionCookie);
        if (uploaded.fileUrl() == null || uploaded.fileUrl().isBlank()) {
            throw new IllegalStateException("Unable to upload company logo.");
        }
        erpNextClient.updateResource("Company", id, Map.of("company_logo", uploaded.fileUrl()));
        return getCompanyProfile(id);
    }

    public Map<String, Object> uploadCompanySignature(String id, MultipartFile file, String sessionCookie) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Company id is required.");
        }
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Signature file is required.");
        }
        UploadedFileInfo uploaded = erpNextFileService.uploadFile(
                "Company",
                id,
                resolveCompanySignatureFilename(file.getOriginalFilename()),
                file,
                sessionCookie);
        if (uploaded.fileUrl() == null || uploaded.fileUrl().isBlank()) {
            throw new IllegalStateException("Unable to upload company signature.");
        }
        erpNextClient.updateResource("Company", id, Map.of("aas_authorized_signature", uploaded.fileUrl()));
        return getCompanyProfile(id);
    }

    public Map<String, Object> getBranchProfile(String id) {
        if (id == null || id.isBlank()) {
            return Map.of();
        }
        Map<String, Object> branch = unwrapResource(erpNextClient.getResource("Customer", id));
        if (branch.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found.");
        }
        Map<String, Object> profile = new LinkedHashMap<>();
        profile.put("id", asText(branch.get("name")));
        profile.put("name", asText(branch.get("customer_name")).isBlank() ? asText(branch.get("name")) : asText(branch.get("customer_name")));
        profile.put("location", asText(branch.get("aas_branch_location")));
        profile.put("whatsapp_group", asText(branch.get("aas_whatsapp_group_name")));
        profile.put("invoice_email", asText(branch.get("aas_invoice_email")));
        profile.put("whatsapp_number", asText(branch.get("aas_whatsapp_number")));
        profile.put("credit_days", branch.getOrDefault("aas_credit_days", 0));
        profile.put("tax_id", firstText(branch.get("tax_id"), branch.get("gstin")));
        profile.put("fssai_no", asText(branch.get("aas_food_license_no")));
        profile.put("logo_url", firstText(branch.get("image"), branch.get("logo")));
        return profile;
    }

    public Map<String, Object> createShop(FieldsRequest request) {
        Map<String, Object> payload = new HashMap<>(request.getFields());
        payload.putIfAbsent("customer_type", "Company");
        payload.putIfAbsent("customer_group", "All Customer Groups");
        payload.putIfAbsent("territory", "All Territories");
        payload.put("disabled", 0);
        return erpNextClient.createResource("Customer", payload);
    }

    public Map<String, Object> createVendor(FieldsRequest request) {
        validateVendorTemplateRequirement(null, request.getFields());
        VendorFieldMapper mapper = new VendorFieldMapper(vendorFieldRegistry.vendorFields());
        Map<String, Object> payload = new HashMap<>();
        payload.putAll(filterVendorBaseFields(request.getFields()));
        payload.putAll(mapper.toErpPayload(request.getFields()));
        normalizeVendorCodePayload(payload);
        validateVendorConfiguration(null, payload);
        applyTemplateFlags(payload);
        payload.putIfAbsent("supplier_type", "Company");
        payload.putIfAbsent("supplier_group", "All Supplier Groups");
        return erpNextClient.createResource("Supplier", payload);
    }

    public Map<String, Object> updateVendor(String id, FieldsRequest request) {
        validateVendorTemplateRequirement(id, request.getFields());
        VendorFieldMapper mapper = new VendorFieldMapper(vendorFieldRegistry.vendorFields());
        Map<String, Object> payload = new HashMap<>();
        payload.putAll(filterVendorBaseFields(request.getFields()));
        payload.putAll(mapper.toErpPayload(request.getFields()));
        normalizeVendorCodePayload(payload);
        validateVendorConfiguration(id, payload);
        applyTemplateFlags(payload);
        return erpNextClient.updateResource("Supplier", id, payload);
    }

    public Map<String, Object> deleteVendor(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Vendor id is required.");
        }
        Map<String, Object> vendor = unwrapResource(erpNextClient.getResource("Supplier", id));
        if (vendor.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Vendor not found.");
        }
        if (!asFlag(vendor.get("disabled"))) {
            throw new IllegalStateException("Only inactive vendors can be deleted.");
        }
        if (hasLinkedVendorUsage("Sales Order", "aas_vendor", id)
                || hasLinkedVendorUsage("Purchase Order", "supplier", id)
                || hasLinkedVendorUsage("Purchase Invoice", "supplier", id)) {
            throw new IllegalStateException("Vendor cannot be deleted because it is linked to orders or bills.");
        }
        erpNextClient.deleteResource("Supplier", id);
        return Map.of("vendorId", id, "deleted", true);
    }

    private Map<String, Object> filterVendorBaseFields(Map<String, Object> fields) {
        if (fields == null || fields.isEmpty()) {
            return Map.of();
        }
        // Allow only known built-in Supplier fields to pass through directly.
        Set<String> allowed = Set.of(
                "supplier_name",
                "supplier_type",
                "supplier_group",
                "disabled");
        Map<String, Object> out = new HashMap<>();
        for (String key : allowed) {
            if (fields.containsKey(key)) {
                out.put(key, fields.get(key));
            }
        }
        return out;
    }

    public Map<String, Object> createCategory(FieldsRequest request) {
        Map<String, Object> payload = new HashMap<>(request.getFields());
        payload.putIfAbsent("parent_item_group", "All Item Groups");
        String categoryCode = catalogRoutingService.normalizeCodeSegment(asText(payload.get("aas_category_code")));
        if (categoryCode.isBlank()) {
            throw new IllegalArgumentException("Category code is required.");
        }
        payload.put("aas_category_code", categoryCode);
        return erpNextClient.createResource("Item Group", payload);
    }

    public Map<String, Object> updateCategory(String id, FieldsRequest request) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Category id is required.");
        }
        Map<String, Object> payload = new HashMap<>(request.getFields());
        String requestedName = payload.containsKey("item_group_name") ? asText(payload.get("item_group_name")).trim() : "";
        String categoryId = id.trim();

        if (!requestedName.isBlank() && !requestedName.equals(categoryId)) {
            payload.remove("item_group_name");
            Map<String, Object> renamePayload = new HashMap<>();
            renamePayload.put("doctype", "Item Group");
            renamePayload.put("old_name", categoryId);
            renamePayload.put("new_name", requestedName);
            renamePayload.put("merge", 0);
            erpNextClient.postMethod("frappe.client.rename_doc", renamePayload);
            categoryId = requestedName;
        }
        if (payload.containsKey("aas_category_code")) {
            payload.put("aas_category_code", catalogRoutingService.normalizeCodeSegment(asText(payload.get("aas_category_code"))));
        }
        if (payload.isEmpty()) {
            return erpNextClient.getResource("Item Group", categoryId);
        }
        return erpNextClient.updateResource("Item Group", categoryId, payload);
    }

    public Map<String, Object> deleteCategory(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Category id is required.");
        }
        if ("All Item Groups".equals(id)) {
            throw new IllegalStateException("Root category cannot be deleted.");
        }
        Map<String, Object> category = unwrapResource(erpNextClient.getResource("Item Group", id));
        if (category.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found.");
        }

        long childGroupCount = erpNextClient.getCount("Item Group", Map.of(
                "filters", "[[\"Item Group\",\"parent_item_group\",\"=\",\"" + escapeJson(id) + "\"]]"));
        if (childGroupCount > 0) {
            throw new IllegalStateException("Category cannot be deleted because it has child categories.");
        }

        long linkedItemCount = erpNextClient.getCount("Item", Map.of(
                "filters", "[[\"Item\",\"item_group\",\"=\",\"" + escapeJson(id) + "\"]]"));
        if (linkedItemCount > 0) {
            throw new IllegalStateException("Category cannot be deleted because it is linked to items.");
        }

        erpNextClient.deleteResource("Item Group", id);
        return Map.of("categoryId", id, "deleted", true);
    }

    public Map<String, Object> createItem(FieldsRequest request) {
        Map<String, Object> payload = new HashMap<>(request.getFields());
        String categoryId = asText(payload.get("item_group"));
        if (categoryId.isBlank()) {
            throw new IllegalArgumentException("Category is required.");
        }
        String vendorHsnCode = firstText(payload.get("aas_vendor_hsn_code"), payload.get("vendor_hsn_code"));
        CatalogRoutingService.VendorCategoryResolution resolution = catalogRoutingService.resolveTopVendorForCategory(categoryId);
        payload.put("item_group", resolution.categoryId());
        payload.put("item_code", catalogRoutingService.buildItemCode(
                resolution.vendorCode(),
                resolution.categoryCode(),
                vendorHsnCode));
        payload.put("aas_vendor", resolution.vendorId());
        payload.put("aas_vendor_hsn_code", catalogRoutingService.normalizeCodeSegment(vendorHsnCode));
        payload.remove("vendor_hsn_code");
        payload.putIfAbsent("stock_uom", "Nos");
        String uom = uomService.normalizeUom(asText(payload.get("stock_uom")).isBlank() ? "Nos" : asText(payload.get("stock_uom")));
        if (!uom.isBlank()) {
            uomService.ensureUomExists(uom);
            payload.put("stock_uom", uom);
        }
        payload.putIfAbsent("is_stock_item", 1);
        return erpNextClient.createResource("Item", payload);
    }

    public Map<String, Object> updateShop(String id, FieldsRequest request) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Shop id is required.");
        }
        Map<String, Object> fields = request == null || request.getFields() == null ? Map.of() : request.getFields();
        Map<String, Object> existing = unwrapResource(erpNextClient.getResource("Customer", id));
        if (existing.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found.");
        }
        if (asFlag(existing.get("aas_is_deleted"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Deleted branches cannot be modified.");
        }
        boolean wasDisabled = asFlag(existing.get("disabled"));
        boolean requestedDisabled = fields.containsKey("disabled") && asFlag(fields.get("disabled"));
        if (requestedDisabled && !wasDisabled) {
            ensureBranchCanBeDisabled(id);
        }

        Map<String, Object> payload = new HashMap<>();
        Set<String> allowed = Set.of(
                "customer_name",
                "aas_branch_location",
                "aas_whatsapp_group_name",
                "aas_credit_days",
                "aas_invoice_email",
                "aas_whatsapp_number",
                "tax_id",
                "aas_food_license_no",
                "disabled");
        for (String key : allowed) {
            if (fields.containsKey(key)) {
                payload.put(key, fields.get(key));
            }
        }
        // Never allow branch ID updates; Customer.name is treated as the immutable branch id across AAS.
        payload.remove("name");
        if (payload.isEmpty()) {
            return existing;
        }
        return erpNextClient.updateResource("Customer", id, payload);
    }

    private void ensureBranchCanBeDisabled(String branchId) {
        long openInvoiceCount = erpNextClient.getCount("Sales Invoice", Map.of(
                "filters",
                "[[\"Sales Invoice\",\"customer\",\"=\",\"" + escapeJson(branchId) + "\"],"
                        + "[\"Sales Invoice\",\"docstatus\",\"!=\",2],"
                        + "[\"Sales Invoice\",\"outstanding_amount\",\">\",0]]"));
        if (openInvoiceCount > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot disable branch while unpaid invoices exist.");
        }

        long activeOrderCount = erpNextClient.getCount("Sales Order", Map.of(
                "filters",
                "[[\"Sales Order\",\"customer\",\"=\",\"" + escapeJson(branchId) + "\"],"
                        + "[\"Sales Order\",\"aas_is_deleted\",\"!=\",1],"
                        + "[\"Sales Order\",\"aas_status\",\"!=\",\"DELETED\"],"
                        + "[\"Sales Order\",\"aas_status\",\"!=\",\"INVOICED\"]]"));
        if (activeOrderCount > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Cannot disable branch while active orders exist.");
        }
    }

    public Map<String, Object> deleteShop(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Shop id is required.");
        }
        String branchId = id.trim();
        Map<String, Object> existing = unwrapResource(erpNextClient.getResource("Customer", branchId));
        if (existing.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Branch not found.");
        }
        if (asFlag(existing.get("aas_is_deleted"))) {
            return Map.of("branchId", branchId, "deleted", true);
        }
        if (!asFlag(existing.get("disabled"))) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only disabled branches can be deleted.");
        }
        ensureBranchCanBeDeleted(branchId);

        Map<String, Object> payload = new HashMap<>();
        payload.put("aas_is_deleted", 1);
        payload.put("aas_deleted_at", LocalDateTime.now().format(ERP_DATE_TIME));
        payload.put("disabled", 1);
        erpNextClient.updateResource("Customer", branchId, payload);
        try {
            Map<String, Object> reloaded = unwrapResource(erpNextClient.getResource("Customer", branchId));
            boolean deleteFieldsPresent = reloaded.containsKey("aas_is_deleted") || reloaded.containsKey("aas_deleted_at");
            if (!deleteFieldsPresent) {
                String currentName = asText(reloaded.get("customer_name")).isBlank()
                        ? branchId
                        : asText(reloaded.get("customer_name"));
                String marked = currentName.startsWith("[DELETED] ") ? currentName : "[DELETED] " + currentName;
                erpNextClient.updateResource("Customer", branchId, Map.of("customer_name", marked, "disabled", 1));
            }
        } catch (Exception ignore) {
            // Best-effort marker; don't fail delete if ERP doesn't return updated doc.
        }
        return Map.of("branchId", branchId, "deleted", true);
    }

    private void ensureBranchCanBeDeleted(String branchId) {
        // Deletion in AAS is a soft-delete (aas_is_deleted) and should be allowed even if
        // historical invoices/orders/payments reference the branch. We only block deletion
        // when there are unpaid invoices or active orders, matching the disable guardrails.
        ensureBranchCanBeDisabled(branchId);
    }

    public Map<String, Object> updateItem(String id, FieldsRequest request) {
        Map<String, Object> fields = request == null || request.getFields() == null ? Map.of() : request.getFields();
        Map<String, Object> payload = new HashMap<>();
        copyIfPresent(fields, payload, "item_name");
        copyIfPresent(fields, payload, "stock_uom");
        copyIfPresent(fields, payload, "aas_packaging_unit");
        copyIfPresent(fields, payload, "aas_margin_percent");
        copyIfPresent(fields, payload, "aas_vendor_hsn_code");
        copyIfPresent(fields, payload, "aas_gst_percent");
        if (payload.isEmpty()) {
            return unwrapResource(erpNextClient.getResource("Item", id));
        }
        if (payload.containsKey("stock_uom")) {
            String normalized = uomService.normalizeUom(asText(payload.get("stock_uom")));
            if (!normalized.isBlank()) {
                uomService.ensureUomExists(normalized);
                payload.put("stock_uom", normalized);
            } else {
                payload.remove("stock_uom");
            }
        }
        return erpNextClient.updateResource("Item", id, payload);
    }

    public Map<String, Object> deleteItem(String id) {
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Item id is required.");
        }
        if (SYSTEM_BRANCH_IMAGE_ITEM.equals(id)) {
            throw new IllegalStateException("System branch image item cannot be deleted.");
        }
        Map<String, Object> item = unwrapResource(erpNextClient.getResource("Item", id));
        if (item.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Item not found.");
        }
        erpNextClient.updateResource("Item", id, Map.of("disabled", 1));
        return Map.of("itemId", id, "deleted", true, "softDeleted", true);
    }

    private String resolveItemOrderBy(String sort, String dir) {
        if (sort == null || sort.isBlank()) {
            return "item_name " + dir;
        }
        String normalized = sort.trim().toLowerCase(Locale.ROOT);
        String field = switch (normalized) {
            case "code" -> "item_code";
            case "name" -> "item_name";
            case "category" -> "item_group";
            case "uom" -> "stock_uom";
            case "packaging" -> "aas_packaging_unit";
            case "margin" -> "aas_margin_percent";
            default -> "item_name";
        };
        return field + " " + dir;
    }

    private String buildItemSearchFilters(String term) {
        String escaped = escapeJson(term);
        String pattern = "%" + escaped + "%";
        return "[[\"Item\",\"item_code\",\"like\",\"" + pattern + "\"],"
                + "[\"Item\",\"item_name\",\"like\",\"" + pattern + "\"]]";
    }

    private String activeItemFiltersJson() {
        return "[[\"Item\",\"disabled\",\"=\",0],[\"Item\",\"item_code\",\"!=\",\"" + SYSTEM_BRANCH_IMAGE_ITEM + "\"]]";
    }

    private String escapeJson(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String key) {
        if (source.containsKey(key)) {
            target.put(key, source.get(key));
        }
    }

    private void copyIfPresent(Map<String, Object> source, Map<String, Object> target, String targetKey, String sourceKey) {
        if (source.containsKey(sourceKey)) {
            target.put(targetKey, source.get(sourceKey));
        }
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

    private void normalizeVendorCodePayload(Map<String, Object> payload) {
        if (payload == null) {
            return;
        }
        if (payload.containsKey("aas_vendor_code")) {
            payload.put("aas_vendor_code", catalogRoutingService.normalizeCodeSegment(asText(payload.get("aas_vendor_code"))));
        }
    }

    private void validateVendorConfiguration(String vendorId, Map<String, Object> payload) {
        Map<String, Object> existing = vendorId == null || vendorId.isBlank()
                ? Map.of()
                : unwrapResource(erpNextClient.getResource("Supplier", vendorId));

        String vendorCode = payload != null && payload.containsKey("aas_vendor_code")
                ? asText(payload.get("aas_vendor_code"))
                : asText(existing.get("aas_vendor_code"));
        if (vendorCode.isBlank()) {
            throw new IllegalArgumentException("Vendor code is required.");
        }

        String category = payload != null && payload.containsKey("aas_category")
                ? asText(payload.get("aas_category"))
                : asText(existing.get("aas_category"));
        if (category.isBlank()) {
            throw new IllegalArgumentException("Category is required.");
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrapResource(Map<String, Object> response) {
        if (response == null) {
            return Map.of();
        }
        Object data = response.get("data");
        if (data instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return response;
    }

    private boolean hasLinkedVendorUsage(String doctype, String field, String vendorId) {
        Map<String, Object> params = new HashMap<>();
        params.put("filters", "[[\"" + escapeJson(doctype) + "\",\"" + escapeJson(field) + "\",\"=\",\"" + escapeJson(vendorId) + "\"]]");
        return erpNextClient.getCount(doctype, params) > 0;
    }

    private void validateVendorTemplateRequirement(String vendorId, Map<String, Object> fields) {
        if (fields == null || fields.isEmpty()) {
            return;
        }
        Map<String, Object> supplier = vendorId == null || vendorId.isBlank()
                ? Map.of()
                : unwrapResource(erpNextClient.getResource("Supplier", vendorId));
        boolean disabled = fields.containsKey("disabled")
                ? asFlag(fields.get("disabled"))
                : asFlag(supplier.get("disabled"));
        if (disabled) {
            return;
        }
        String templateJson = firstText(
                fields.get("invoice_template_json"),
                fields.get("aas_invoice_template_json"),
                supplier.get("aas_invoice_template_json"),
                supplier.get("invoice_template_json"));
        if (templateJson.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Active vendors require invoice template JSON to be present.");
        }
        boolean isCreate = vendorId == null || vendorId.isBlank();
        boolean activatingVendor = !isCreate && asFlag(supplier.get("disabled"));
        String currentTemplateJson = firstText(
                supplier.get("aas_invoice_template_json"),
                supplier.get("invoice_template_json"));
        boolean templateChanged = isCreate || !templateJson.equals(currentTemplateJson);
        if (!templateChanged && !activatingVendor) {
            return;
        }
        if (!hasSupportedTemplateProfile(templateJson)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Vendor invoice template JSON must contain a supported native layout profile.");
        }
        if (!hasTemplateSample(supplier)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Active vendors require a validated sample invoice PDF before activation.");
        }
        if (!sampleMatchesTemplate(supplier, templateJson)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Active vendors require a sample invoice that parses item_name, item_id, qty, rate, gst, and total.");
        }
    }

    private String asText(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private void applyTemplateFlags(Map<String, Object> payload) {
        String templateJson = asText(payload.get("aas_invoice_template_json"));
        payload.put("aas_invoice_template_enabled", templateJson.isBlank() ? 0 : 1);
    }

    private boolean hasSupportedTemplateProfile(String templateJson) {
        if (nativeLayoutInvoiceService.parseStoredProfile(templateJson) != null) {
            return true;
        }
        try {
            Map<String, Object> map = objectMapper.readValue(templateJson, new TypeReference<Map<String, Object>>() {});
            Object parserObj = map.get("parser");
            Map<String, Object> parser = map;
            if (parserObj instanceof Map<?, ?> parserMap) {
                @SuppressWarnings("unchecked")
                Map<String, Object> casted = (Map<String, Object>) parserMap;
                parser = casted;
            }
            String itemLineRegex = asText(parser.get("itemLineRegex"));
            return !itemLineRegex.isBlank();
        } catch (Exception ex) {
            return false;
        }
    }

    private boolean hasTemplateSample(Map<String, Object> supplier) {
        String samplePdf = asText(supplier.get("aas_invoice_template_sample_pdf"));
        return !samplePdf.isBlank();
    }

    private boolean sampleMatchesTemplate(Map<String, Object> supplier, String templateJson) {
        if (supplier == null || supplier.isEmpty()) {
            return false;
        }
        String samplePdf = asText(supplier.get("aas_invoice_template_sample_pdf"));
        if (samplePdf.isBlank()) {
            return false;
        }
        NativeLayoutInvoiceService.StoredProfile nativeProfile = nativeLayoutInvoiceService.parseStoredProfile(templateJson);
        if (nativeProfile != null) {
            try {
                byte[] pdfBytes = erpNextFileService.downloadFile(samplePdf).bytes();
                NativeLayoutInvoiceService.ExtractionResult extraction = nativeLayoutInvoiceService.extract(pdfBytes, nativeProfile);
                if (extraction.items().isEmpty()) {
                    return false;
                }
                Set<String> parsedColumns = new LinkedHashSet<>();
                for (ParsedItem item : extraction.items()) {
                    if (item == null) {
                        continue;
                    }
                    if (hasText(item.name())) {
                        parsedColumns.add("item_name");
                    }
                    if (hasText(item.hsn())) {
                        parsedColumns.add("item_id");
                    }
                    if (item.qty() > 0) {
                        parsedColumns.add("qty");
                    }
                    if (hasText(item.uom())) {
                        parsedColumns.add("uom");
                    }
                    if (item.rate() > 0) {
                        parsedColumns.add("rate");
                    }
                    if (item.gstPercent() != null) {
                        parsedColumns.add("gst");
                    }
                    if (item.amount() > 0) {
                        parsedColumns.add("total");
                    }
                }
                if (!parsedColumns.containsAll(invoiceTemplateModelService.requiredItemKeys())) {
                    return false;
                }
                Set<String> parsedSummaryFields = new LinkedHashSet<>();
                if (hasText(extraction.finalAmount())) {
                    parsedSummaryFields.add("final_bill_amount");
                }
                return parsedSummaryFields.containsAll(invoiceTemplateModelService.requiredSummaryKeys());
            } catch (Exception ex) {
                return false;
            }
        }
        try {
            Map<String, Object> map = objectMapper.readValue(templateJson, new TypeReference<Map<String, Object>>() {});
            Object parserObj = map.get("parser");
            Map<String, Object> parser = map;
            if (parserObj instanceof Map<?, ?> parserMap) {
                @SuppressWarnings("unchecked")
                Map<String, Object> casted = (Map<String, Object>) parserMap;
                parser = casted;
            }
            int version = parser.get("version") instanceof Number n
                    ? n.intValue()
                    : Integer.parseInt(asText(parser.get("version")));
            String itemLineRegex = asText(parser.get("itemLineRegex"));
            String billDateRegex = asText(parser.get("billDateRegex"));
            String finalAmountRegex = asText(parser.get("finalAmountRegex"));
            String transportChargeRegex = asText(parser.get("transportChargeRegex"));
            if (version <= 0 || itemLineRegex.isBlank()) {
                return false;
            }
            String ocrText = ocrService.extractTextFromPdf(erpNextFileService.downloadFile(samplePdf).bytes());
            List<ParsedItem> items = templateParser.parseItems(
                    ocrText,
                    new VendorInvoiceTemplate(
                            version,
                            itemLineRegex,
                            billDateRegex.isBlank() ? null : billDateRegex,
                            finalAmountRegex.isBlank() ? null : finalAmountRegex,
                            transportChargeRegex.isBlank() ? null : transportChargeRegex));
            if (items.isEmpty()) {
                return false;
            }
            Set<String> parsedColumns = new LinkedHashSet<>();
            for (ParsedItem item : items) {
                if (item == null) {
                    continue;
                }
                if (hasText(item.name())) {
                    parsedColumns.add("item_name");
                }
                if (hasText(item.hsn())) {
                    parsedColumns.add("item_id");
                }
                if (item.qty() > 0) {
                    parsedColumns.add("qty");
                }
                if (hasText(item.uom())) {
                    parsedColumns.add("uom");
                }
                if (item.rate() > 0) {
                    parsedColumns.add("rate");
                }
                if (item.gstPercent() != null) {
                    parsedColumns.add("gst");
                }
                if (item.amount() > 0) {
                    parsedColumns.add("total");
                }
            }
            if (!parsedColumns.containsAll(invoiceTemplateModelService.requiredItemKeys())) {
                return false;
            }
            Set<String> parsedSummaryFields = new LinkedHashSet<>();
            String finalBillAmount = extractFinalAmount(
                    ocrText,
                    new VendorInvoiceTemplate(
                            version,
                            itemLineRegex,
                            billDateRegex.isBlank() ? null : billDateRegex,
                            finalAmountRegex.isBlank() ? null : finalAmountRegex,
                            transportChargeRegex.isBlank() ? null : transportChargeRegex));
            if (!finalBillAmount.isBlank()) {
                parsedSummaryFields.add("final_bill_amount");
            }
            return parsedSummaryFields.containsAll(invoiceTemplateModelService.requiredSummaryKeys());
        } catch (Exception ex) {
            return false;
        }
    }

    private String resolveFileUrl(String filePath) {
        String base = erpPublicBaseUrl.endsWith("/") ? erpPublicBaseUrl.substring(0, erpPublicBaseUrl.length() - 1) : erpPublicBaseUrl;
        if (filePath.startsWith("http://") || filePath.startsWith("https://")) {
            return filePath;
        }
        String path = filePath.startsWith("/") ? filePath : "/" + filePath;
        return base + path;
    }

    private String resolveCompanyLogoFilename(String originalFilename) {
        return resolveCompanyAssetFilename("company_logo", originalFilename);
    }

    private String resolveCompanySignatureFilename(String originalFilename) {
        return resolveCompanyAssetFilename("company_signature", originalFilename);
    }

    private String resolveCompanyAssetFilename(String basename, String originalFilename) {
        String extension = ".png";
        if (originalFilename != null && !originalFilename.isBlank()) {
            int dot = originalFilename.lastIndexOf('.');
            if (dot > -1 && dot < originalFilename.length() - 1) {
                extension = originalFilename.substring(dot);
            }
        }
        return basename + extension;
    }

    private Map<String, Object> resolveVendorTemplateFileUrl(Map<String, Object> vendor) {
        if (vendor == null) {
            return null;
        }
        String samplePdf = asText(vendor.get("invoice_template_sample_pdf"));
        if (!samplePdf.isBlank()) {
            vendor.put("invoice_template_sample_pdf", resolveFileUrl(samplePdf));
        }
        return vendor;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private String extractFinalAmount(String ocrText, VendorInvoiceTemplate template) {
        if (ocrText == null || ocrText.isBlank() || template == null) {
            return "";
        }
        InvoiceSummaryExtractor.Extraction extraction =
                InvoiceSummaryExtractor.extractFinalAmount(ocrText, template.finalAmountRegex());
        return extraction.amount();
    }

    private double parseAmount(String raw) {
        return InvoiceSummaryExtractor.parseAmount(raw);
    }

    private boolean asFlag(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            return n.intValue() != 0;
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return false;
        }
        return "1".equals(text) || "true".equalsIgnoreCase(text) || "yes".equalsIgnoreCase(text);
    }
}
