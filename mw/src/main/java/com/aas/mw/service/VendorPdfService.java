package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.dto.ParsedItem;
import com.aas.mw.dto.UploadedFileInfo;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class VendorPdfService {

    private static final String SALES_ORDER = "Sales Order";
    private static final String PURCHASE_ORDER = "Purchase Order";
    private static final String ITEM = "Item";

    private final ErpNextClient erpNextClient;
    private final ErpNextFileService fileService;
    private final OcrService ocrService;
    private final VendorPdfParser parser;
    private final OrderFlowStateMachine orderFlowStateMachine;
    private final double defaultMarginPercent;

    public VendorPdfService(
            ErpNextClient erpNextClient,
            ErpNextFileService fileService,
            OcrService ocrService,
            VendorPdfParser parser,
            OrderFlowStateMachine orderFlowStateMachine,
            @Value("${app.order.margin.default-percent:10}") double defaultMarginPercent) {
        this.erpNextClient = erpNextClient;
        this.fileService = fileService;
        this.ocrService = ocrService;
        this.parser = parser;
        this.orderFlowStateMachine = orderFlowStateMachine;
        this.defaultMarginPercent = defaultMarginPercent;
    }

    public Map<String, Object> processVendorPdf(String orderId, MultipartFile pdfFile, String sessionCookie) {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("Order id is required.");
        }
        if (pdfFile == null || pdfFile.isEmpty()) {
            throw new IllegalArgumentException("Vendor PDF is required.");
        }

        Map<String, Object> originalOrder = erpNextClient.getResource(SALES_ORDER, orderId);
        Map<String, Object> orderData = unwrapResource(originalOrder);
        String customer = asText(orderData.get("customer"));
        String company = asText(orderData.get("company"));
        String vendor = asText(orderData.get("aas_vendor"));
        String currentStatus = asText(orderData.get("aas_status"));
        orderFlowStateMachine.ensureCanUploadVendorPdf(currentStatus);
        if (vendor.isBlank()) {
            throw new IllegalStateException("Vendor must be assigned before uploading vendor PDF.");
        }
        if (customer.isBlank() || company.isBlank()) {
            throw new IllegalStateException("Order must include customer and company before processing vendor PDF.");
        }

        UploadedFileInfo pdfInfo = fileService.uploadOrderPdf(orderId, pdfFile, sessionCookie);
        if (pdfInfo.fileUrl() != null) {
            erpNextClient.updateResource(SALES_ORDER, orderId, Map.of(
                    "aas_vendor_pdf", pdfInfo.fileUrl(),
                    "aas_status", "VENDOR_PDF_RECEIVED"));
        } else {
            erpNextClient.updateResource(SALES_ORDER, orderId, Map.of(
                    "aas_status", "VENDOR_PDF_RECEIVED"));
        }

        String ocrText = ocrService.extractTextFromPdf(toBytes(pdfFile));
        List<ParsedItem> parsedItems = parser.parseItems(ocrText);
        if (parsedItems.isEmpty()) {
            throw new IllegalStateException("No items could be parsed from vendor PDF.");
        }

        List<Map<String, Object>> baseItems = resolveItems(parsedItems);
        double marginPercent = resolveMarginPercent(orderData.get("aas_margin_percent"));
        List<Map<String, Object>> sourceOrderItems = withVendorRate(baseItems);
        List<Map<String, Object>> sellItems = withSellMargin(baseItems, marginPercent);
        erpNextClient.updateResource(SALES_ORDER, orderId, Map.of(
                "items", sourceOrderItems,
                "aas_margin_percent", marginPercent));

        Map<String, Object> purchaseOrder = createPurchaseOrder(orderId, vendor, company, baseItems, orderData);

        Map<String, Object> linkUpdate = new HashMap<>();
        linkUpdate.put("aas_po", extractDocName(purchaseOrder));
        linkUpdate.put("aas_status", "VENDOR_PDF_RECEIVED");
        erpNextClient.updateResource(SALES_ORDER, orderId, linkUpdate);

        Map<String, Object> response = new HashMap<>();
        response.put("orderId", orderId);
        response.put("purchaseOrder", purchaseOrder);
        response.put("sellPreview", Map.of(
                "vendorTotal", sumAmount(baseItems),
                "marginPercent", marginPercent,
                "sellTotal", sumAmount(sellItems)));
        response.put("marginPercent", marginPercent);
        response.put("items", parsedItems);
        response.put("file", Map.of(
                "fileName", pdfInfo.fileName(),
                "fileUrl", pdfInfo.fileUrl(),
                "fileId", pdfInfo.fileId()));
        return response;
    }

    private List<Map<String, Object>> resolveItems(List<ParsedItem> parsedItems) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ParsedItem item : parsedItems) {
            String itemName = item.name();
            String itemCode = findItemCodeByName(itemName);
            if (itemCode == null) {
                itemCode = createItem(itemName);
            }
            Map<String, Object> row = new HashMap<>();
            row.put("item_code", itemCode);
            row.put("item_name", itemName);
            row.put("qty", item.qty());
            row.put("rate", item.rate());
            double amount = item.amount();
            if (amount <= 0 && item.qty() > 0) {
                amount = item.rate() * item.qty();
            }
            row.put("amount", amount);
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> withVendorRate(List<Map<String, Object>> baseItems) {
        List<Map<String, Object>> enriched = new ArrayList<>();
        for (Map<String, Object> row : baseItems) {
            Map<String, Object> copy = new HashMap<>(row);
            Object rate = row.get("rate");
            copy.put("aas_vendor_rate", rate);
            enriched.add(copy);
        }
        return enriched;
    }

    private List<Map<String, Object>> withSellMargin(List<Map<String, Object>> baseItems, double marginPercent) {
        List<Map<String, Object>> enriched = new ArrayList<>();
        for (Map<String, Object> row : baseItems) {
            Map<String, Object> copy = new HashMap<>(row);
            double vendorRate = asDouble(row.get("rate"));
            double qty = asDouble(row.get("qty"));
            double sellRate = round(vendorRate * (1 + marginPercent / 100.0));
            copy.put("rate", sellRate);
            copy.put("amount", round(sellRate * qty));
            copy.put("aas_vendor_rate", vendorRate);
            copy.put("aas_margin_percent", marginPercent);
            enriched.add(copy);
        }
        return enriched;
    }

    private String findItemCodeByName(String itemName) {
        if (itemName == null || itemName.isBlank()) {
            return null;
        }
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\",\"item_name\"]");
        params.put("limit_page_length", "1");
        params.put("filters", "[[\"item_name\",\"=\",\"" + escape(itemName) + "\"]]");
        List<Map<String, Object>> data = erpNextClient.listResources(ITEM, params);
        if (data.isEmpty()) {
            return null;
        }
        Object name = data.get(0).get("name");
        return name == null ? null : name.toString();
    }

    private String createItem(String itemName) {
        Map<String, Object> payload = new HashMap<>();
        String code = normalizeItemCode(itemName);
        payload.put("item_code", code);
        payload.put("item_name", itemName);
        payload.put("item_group", "All Item Groups");
        payload.put("stock_uom", "Nos");
        payload.put("is_stock_item", 1);
        Map<String, Object> created = erpNextClient.createResource(ITEM, payload);
        Object name = created.get("name");
        return name == null ? code : name.toString();
    }

    private Map<String, Object> createPurchaseOrder(
            String sourceOrderId,
            String vendor,
            String company,
            List<Map<String, Object>> items,
            Map<String, Object> originalOrder) {
        String warehouse = resolveWarehouse(originalOrder);
        List<Map<String, Object>> poItems = withWarehouse(items, warehouse);
        Map<String, Object> payload = new HashMap<>();
        payload.put("supplier", vendor);
        payload.put("company", company);
        payload.put("schedule_date", resolveScheduleDate(originalOrder));
        payload.put("items", poItems);
        payload.put("aas_source_sales_order", sourceOrderId);
        return erpNextClient.createResource(PURCHASE_ORDER, payload);
    }

    @SuppressWarnings("unchecked")
    private String resolveWarehouse(Map<String, Object> originalOrder) {
        String orderWarehouse = asText(originalOrder == null ? null : originalOrder.get("set_warehouse"));
        if (!orderWarehouse.isBlank()) {
            return orderWarehouse;
        }
        Object itemsObj = originalOrder == null ? null : originalOrder.get("items");
        if (itemsObj instanceof List<?> list) {
            for (Object rowObj : list) {
                if (rowObj instanceof Map<?, ?> row) {
                    String rowWarehouse = asText(((Map<String, Object>) row).get("warehouse"));
                    if (!rowWarehouse.isBlank()) {
                        return rowWarehouse;
                    }
                }
            }
        }
        return "";
    }

    private List<Map<String, Object>> withWarehouse(List<Map<String, Object>> items, String warehouse) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> item : items) {
            Map<String, Object> copy = new HashMap<>(item);
            if (!warehouse.isBlank()) {
                copy.put("warehouse", warehouse);
            }
            rows.add(copy);
        }
        return rows;
    }

    private String resolveDate(Object value) {
        String text = asText(value);
        if (!text.isBlank()) {
            return text;
        }
        return LocalDate.now().toString();
    }

    private String resolveScheduleDate(Map<String, Object> originalOrder) {
        LocalDate today = LocalDate.now();
        LocalDate minScheduleDate = today.plusDays(1);
        LocalDate transactionDate = parseDate(originalOrder == null ? null : originalOrder.get("transaction_date"), today);
        LocalDate deliveryDate = parseDate(originalOrder == null ? null : originalOrder.get("delivery_date"), transactionDate);
        LocalDate scheduleDate = deliveryDate;
        if (scheduleDate.isBefore(transactionDate)) {
            scheduleDate = transactionDate;
        }
        if (scheduleDate.isBefore(minScheduleDate)) {
            scheduleDate = minScheduleDate;
        }
        return scheduleDate.toString();
    }

    private LocalDate parseDate(Object value, LocalDate fallback) {
        String text = asText(value);
        if (text.isBlank()) {
            return fallback;
        }
        try {
            return LocalDate.parse(text);
        } catch (Exception ex) {
            return fallback;
        }
    }

    private double resolveMarginPercent(Object value) {
        double margin = asDouble(value);
        if (margin == 0.0 && value == null) {
            margin = defaultMarginPercent;
        }
        if (margin < 0) {
            throw new IllegalArgumentException("Margin percent must be non-negative.");
        }
        return margin;
    }

    private double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? 0.0 : Double.parseDouble(value.toString());
        } catch (Exception ex) {
            return 0.0;
        }
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private double sumAmount(List<Map<String, Object>> items) {
        double total = 0.0;
        for (Map<String, Object> row : items) {
            total += asDouble(row.get("amount"));
        }
        return round(total);
    }

    private String asText(Object value) {
        return value == null ? "" : value.toString().trim();
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

    private byte[] toBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to read vendor PDF.", ex);
        }
    }

    private String normalizeItemCode(String name) {
        String cleaned = name.trim().replaceAll("[^a-zA-Z0-9]+", "-").replaceAll("(^-|-$)", "");
        if (cleaned.isEmpty()) {
            return "ITEM-" + System.currentTimeMillis();
        }
        if (cleaned.length() > 100) {
            cleaned = cleaned.substring(0, 100);
        }
        return cleaned.toUpperCase();
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String extractDocName(Map<String, Object> response) {
        if (response == null) {
            return null;
        }
        Object direct = response.get("name");
        if (direct != null) {
            return direct.toString();
        }
        Object data = response.get("data");
        if (data instanceof Map<?, ?> map) {
            Object name = map.get("name");
            return name == null ? null : name.toString();
        }
        return null;
    }
}
