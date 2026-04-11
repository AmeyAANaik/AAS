package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.dto.ApproveMasterDataReviewRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class MasterDataReviewService {

    private static final String ITEM = "Item";
    private static final List<String> REVIEWABLE_STATUSES = List.of("PENDING_REVIEW", "APPROVED", "MERGED", "REJECTED");

    private final ErpNextClient erpNextClient;
    private final OrderService orderService;

    public MasterDataReviewService(ErpNextClient erpNextClient, OrderService orderService) {
        this.erpNextClient = erpNextClient;
        this.orderService = orderService;
    }

    public Map<String, Object> getPendingCount() {
        long pendingCount = listReviewItems("PENDING_REVIEW").size();
        return Map.of("pendingCount", pendingCount);
    }

    public List<Map<String, Object>> listReviewItems(String status) {
        Map<String, Object> params = new HashMap<>();
        params.put(
                "fields",
                "[\"name\",\"item_code\",\"item_name\",\"item_group\",\"stock_uom\",\"aas_packaging_unit\","
                        + "\"aas_margin_percent\",\"aas_vendor\",\"aas_vendor_hsn_code\",\"aas_gst_percent\","
                        + "\"aas_review_status\",\"aas_review_source_order\",\"aas_review_source_invoice_ref\","
                        + "\"aas_review_created_at\",\"aas_review_created_by\",\"aas_review_notes\",\"aas_review_default_margin_used\"]");
        params.put("limit_page_length", 1000);
        List<Map<String, Object>> items = erpNextClient.listResources(ITEM, params);
        String normalizedStatus = normalizeStatus(status);
        List<Map<String, Object>> response = new ArrayList<>();
        for (Map<String, Object> item : items) {
            Map<String, Object> view = toListItem(item);
            String reviewStatus = String.valueOf(view.getOrDefault("reviewStatus", "")).trim();
            if (reviewStatus.isBlank() || !REVIEWABLE_STATUSES.contains(reviewStatus)) {
                continue;
            }
            if (!normalizedStatus.isBlank() && !normalizedStatus.equals(reviewStatus)) {
                continue;
            }
            response.add(view);
        }
        response.sort((left, right) -> String.valueOf(right.getOrDefault("createdAt", ""))
                .compareTo(String.valueOf(left.getOrDefault("createdAt", ""))));
        return response;
    }

    public Map<String, Object> getReviewDetail(String itemId) {
        Map<String, Object> item = unwrap(erpNextClient.getResource(ITEM, itemId));
        if (item.isEmpty()) {
            throw new IllegalArgumentException("Item not found.");
        }
        Map<String, Object> detail = new LinkedHashMap<>(toListItem(item));
        detail.put("reviewNotes", asText(item.get("aas_review_notes")));
        detail.put("sourceOrderId", asText(item.get("aas_review_source_order")));
        detail.put("sourceInvoiceRef", asText(item.get("aas_review_source_invoice_ref")));
        detail.put("createdBy", asText(item.get("aas_review_created_by")));
        detail.put("createdAt", asText(item.get("aas_review_created_at")));
        detail.put("defaultMarginUsed", asFlag(item.get("aas_review_default_margin_used")));
        detail.put("raw", item);
        return detail;
    }

    public Map<String, Object> approveReviewItem(String itemId, ApproveMasterDataReviewRequest request) {
        Map<String, Object> current = unwrap(erpNextClient.getResource(ITEM, itemId));
        if (current.isEmpty()) {
            throw new IllegalArgumentException("Item not found.");
        }
        Map<String, Object> payload = new HashMap<>();
        putIfHasText(payload, "item_name", request.getItem_name());
        putIfHasText(payload, "item_group", request.getItem_group());
        putIfHasText(payload, "stock_uom", request.getStock_uom());
        putIfHasText(payload, "aas_packaging_unit", request.getAas_packaging_unit());
        putIfHasText(payload, "aas_vendor_hsn_code", request.getAas_vendor_hsn_code());
        if (request.getAas_margin_percent() != null) {
            payload.put("aas_margin_percent", sanitizeNonNegative(request.getAas_margin_percent(), "aas_margin_percent"));
        }
        if (request.getAas_gst_percent() != null) {
            payload.put("aas_gst_percent", sanitizeNonNegative(request.getAas_gst_percent(), "aas_gst_percent"));
        }
        payload.put("aas_review_status", "APPROVED");
        payload.put("aas_review_notes", asText(request.getReviewNotes()));
        payload.put("aas_review_default_margin_used", 0);

        Map<String, Object> updated = unwrap(erpNextClient.updateResource(ITEM, itemId, payload));
        boolean sourceOrderUpdated = false;
        String sourceOrderId = asText(current.get("aas_review_source_order"));
        double approvedMargin = request.getAas_margin_percent() != null
                ? sanitizeNonNegative(request.getAas_margin_percent(), "aas_margin_percent")
                : asDouble(updated.get("aas_margin_percent"));
        if (request.isApplyToSourceOrder() && !sourceOrderId.isBlank() && approvedMargin >= 0) {
            sourceOrderUpdated = orderService.applyReviewedMarginToOrderItem(sourceOrderId, itemId, approvedMargin);
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("item", updated);
        response.put("detail", getReviewDetail(itemId));
        response.put("sourceOrderUpdated", sourceOrderUpdated);
        response.put("pendingCount", getPendingCount().get("pendingCount"));
        return response;
    }

    private Map<String, Object> toListItem(Map<String, Object> item) {
        Map<String, Object> view = new LinkedHashMap<>();
        view.put("id", asText(item.get("name")));
        view.put("itemCode", asText(item.get("item_code")));
        view.put("itemName", asText(item.get("item_name")));
        view.put("category", asText(item.get("item_group")));
        view.put("uom", asText(item.get("stock_uom")));
        view.put("packagingUnit", asText(item.get("aas_packaging_unit")));
        view.put("marginPercent", asDouble(item.get("aas_margin_percent")));
        view.put("vendorId", asText(item.get("aas_vendor")));
        view.put("vendorHsnCode", asText(item.get("aas_vendor_hsn_code")));
        view.put("gstPercent", asDouble(item.get("aas_gst_percent")));
        view.put("reviewStatus", asText(item.get("aas_review_status")));
        view.put("sourceOrderId", asText(item.get("aas_review_source_order")));
        view.put("sourceInvoiceRef", asText(item.get("aas_review_source_invoice_ref")));
        view.put("createdAt", asText(item.get("aas_review_created_at")));
        view.put("createdBy", asText(item.get("aas_review_created_by")));
        view.put("reviewNotes", asText(item.get("aas_review_notes")));
        view.put("defaultMarginUsed", asFlag(item.get("aas_review_default_margin_used")));
        return view;
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

    private String normalizeStatus(String status) {
        String normalized = asText(status).toUpperCase(Locale.ROOT);
        return REVIEWABLE_STATUSES.contains(normalized) ? normalized : "";
    }

    private void putIfHasText(Map<String, Object> target, String key, String value) {
        if (value != null && !value.trim().isEmpty()) {
            target.put(key, value.trim());
        }
    }

    private String asText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        if (value == null) {
            return 0.0;
        }
        try {
            return Double.parseDouble(String.valueOf(value).trim());
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }

    private double sanitizeNonNegative(double value, String fieldName) {
        if (!Double.isFinite(value) || value < 0) {
            throw new IllegalArgumentException(fieldName + " must be non-negative.");
        }
        return Math.round(value * 100.0) / 100.0;
    }

    private boolean asFlag(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String normalized = asText(value).toLowerCase(Locale.ROOT);
        return "1".equals(normalized) || "true".equals(normalized) || "yes".equals(normalized);
    }
}
