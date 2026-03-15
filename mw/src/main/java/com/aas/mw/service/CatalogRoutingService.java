package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class CatalogRoutingService {

    private static final Pattern NON_ALNUM = Pattern.compile("[^A-Za-z0-9]+");

    private final ErpNextClient erpNextClient;

    public CatalogRoutingService(ErpNextClient erpNextClient) {
        this.erpNextClient = erpNextClient;
    }

    public VendorCategoryResolution resolveTopVendorForCategory(String categoryId) {
        String normalizedCategoryId = asText(categoryId);
        if (normalizedCategoryId.isBlank()) {
            throw new IllegalArgumentException("Category is required.");
        }

        Map<String, Object> category = loadCategory(normalizedCategoryId);
        String categoryName = firstText(category.get("item_group_name"), category.get("name"));
        String categoryCode = resolveCategoryCode(category);

        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\",\"supplier_name\",\"disabled\",\"aas_priority\",\"aas_category\",\"aas_vendor_code\"]");
        params.put("filters", "[[\"Supplier\",\"disabled\",\"=\",0],[\"Supplier\",\"aas_category\",\"=\",\"" + escapeJson(normalizedCategoryId) + "\"]]");
        params.put("limit_page_length", 1000);

        List<Map<String, Object>> vendors = new ArrayList<>(erpNextClient.listResources("Supplier", params));
        vendors.sort(Comparator
                .comparingInt((Map<String, Object> vendor) -> -priorityOf(vendor.get("aas_priority")))
                .thenComparing(vendor -> firstText(vendor.get("supplier_name"), vendor.get("name")).toLowerCase(Locale.ROOT)));

        if (vendors.isEmpty()) {
            throw new IllegalStateException("No active vendors are configured for category \"" + categoryName + "\".");
        }

        Map<String, Object> vendor = vendors.get(0);
        return new VendorCategoryResolution(
                asText(vendor.get("name")),
                firstText(vendor.get("supplier_name"), vendor.get("name")),
                resolveVendorCode(vendor),
                normalizedCategoryId,
                categoryName,
                categoryCode);
    }

    public VendorCategoryResolution resolveVendorForCategory(String vendorId, String categoryId) {
        String normalizedVendorId = asText(vendorId);
        String normalizedCategoryId = asText(categoryId);
        if (normalizedVendorId.isBlank()) {
            throw new IllegalArgumentException("Vendor is required.");
        }
        if (normalizedCategoryId.isBlank()) {
            throw new IllegalArgumentException("Category is required.");
        }

        Map<String, Object> vendor = loadVendor(normalizedVendorId);
        if (isDisabled(vendor.get("disabled"))) {
            throw new IllegalStateException("Inactive vendors cannot be used.");
        }

        String vendorCategory = asText(vendor.get("aas_category"));
        if (!vendorCategory.isBlank() && !vendorCategory.equals(normalizedCategoryId)) {
            throw new IllegalStateException("Vendor \"" + firstText(vendor.get("supplier_name"), vendor.get("name"))
                    + "\" does not belong to category \"" + normalizedCategoryId + "\".");
        }

        Map<String, Object> category = loadCategory(normalizedCategoryId);
        return new VendorCategoryResolution(
                normalizedVendorId,
                firstText(vendor.get("supplier_name"), vendor.get("name")),
                resolveVendorCode(vendor),
                normalizedCategoryId,
                firstText(category.get("item_group_name"), category.get("name")),
                resolveCategoryCode(category));
    }

    public String buildItemCode(String vendorCode, String categoryCode, String vendorHsnCode) {
        String normalizedVendorCode = normalizeCodeSegment(vendorCode);
        String normalizedCategoryCode = normalizeCodeSegment(categoryCode);
        String normalizedVendorHsnCode = normalizeCodeSegment(vendorHsnCode);
        if (normalizedVendorCode.isBlank()) {
            throw new IllegalStateException("Vendor code is required to generate item code.");
        }
        if (normalizedCategoryCode.isBlank()) {
            throw new IllegalStateException("Category code is required to generate item code.");
        }
        if (normalizedVendorHsnCode.isBlank()) {
            throw new IllegalArgumentException("Vendor HSN code is required.");
        }
        return normalizedVendorCode + "_" + normalizedCategoryCode + "_" + normalizedVendorHsnCode;
    }

    public String normalizeCodeSegment(String value) {
        String normalized = asText(value).trim().toUpperCase(Locale.ROOT);
        normalized = NON_ALNUM.matcher(normalized).replaceAll("_");
        normalized = normalized.replaceAll("_+", "_");
        normalized = normalized.replaceAll("^_+", "").replaceAll("_+$", "");
        return normalized;
    }

    private Map<String, Object> loadVendor(String vendorId) {
        Map<String, Object> vendor = unwrap(erpNextClient.getResource("Supplier", vendorId));
        if (vendor.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Vendor not found.");
        }
        return vendor;
    }

    private Map<String, Object> loadCategory(String categoryId) {
        Map<String, Object> category = unwrap(erpNextClient.getResource("Item Group", categoryId));
        if (category.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Category not found.");
        }
        return category;
    }

    private String resolveVendorCode(Map<String, Object> vendor) {
        return firstNonBlankNormalized(
                vendor == null ? null : vendor.get("aas_vendor_code"),
                vendor == null ? null : vendor.get("supplier_name"),
                vendor == null ? null : vendor.get("name"));
    }

    private String resolveCategoryCode(Map<String, Object> category) {
        return firstNonBlankNormalized(
                category == null ? null : category.get("aas_category_code"),
                category == null ? null : category.get("item_group_name"),
                category == null ? null : category.get("name"));
    }

    private String firstNonBlankNormalized(Object... values) {
        for (Object value : values) {
            String normalized = normalizeCodeSegment(asText(value));
            if (!normalized.isBlank()) {
                return normalized;
            }
        }
        return "";
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrap(Map<String, Object> response) {
        if (response == null) {
            return Map.of();
        }
        Object data = response.get("data");
        if (data instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return response;
    }

    private boolean isDisabled(Object value) {
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
        return "1".equals(text) || "true".equalsIgnoreCase(text) || "yes".equalsIgnoreCase(text);
    }

    private int priorityOf(Object value) {
        if (value instanceof Number n) {
            return n.intValue();
        }
        try {
            return Integer.parseInt(asText(value));
        } catch (NumberFormatException ignored) {
            return 0;
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

    private String asText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private String escapeJson(String value) {
        return asText(value).replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public record VendorCategoryResolution(
            String vendorId,
            String vendorName,
            String vendorCode,
            String categoryId,
            String categoryName,
            String categoryCode) {}
}
