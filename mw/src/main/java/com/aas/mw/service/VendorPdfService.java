package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.dto.ParsedItem;
import com.aas.mw.dto.UploadedFileInfo;
import feign.FeignException;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.function.Supplier;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

@Service
public class VendorPdfService {

    private static final String SALES_ORDER = "Sales Order";
    private static final String PURCHASE_ORDER = "Purchase Order";
    private static final String PURCHASE_INVOICE = "Purchase Invoice";
    private static final String SALES_INVOICE = "Sales Invoice";
    private static final String ITEM = "Item";
    private static final String WAREHOUSE = "Warehouse";
    private static final String COMPANY = "Company";
    private static final String TRANSPORT_ITEM_CODE = "AAS-TRANSPORT-CHARGE";
    private static final Pattern NON_ALNUM_PATTERN = Pattern.compile("[^a-zA-Z0-9]+");
    private static final Pattern LEADING_SERIAL_PATTERN =
            Pattern.compile("^\\s*(\\d{1,3})(?:(?:\\s{2,})|(?=[A-Z(]))");
    private static final String TEMPLATE_REQUIRED_ERROR =
            "Vendor native invoice mapping is required before uploading vendor PDF.";
    private static final DateTimeFormatter ERP_DATE_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ErpNextClient erpNextClient;
    private final ErpNextFileService fileService;
    private final VendorInvoiceTemplateResolver templateResolver;
    private final NativeLayoutInvoiceService nativeLayoutInvoiceService;
    private final InvoiceTemplateModelService invoiceTemplateModelService;
    private final OrderFlowStateMachine orderFlowStateMachine;
    private final OrderBillingService orderBillingService;
    private final CatalogRoutingService catalogRoutingService;
    private final OrderPricingService orderPricingService;
    private final UomService uomService;
    private final double defaultMarginPercent;

    public VendorPdfService(
            ErpNextClient erpNextClient,
            ErpNextFileService fileService,
            VendorInvoiceTemplateResolver templateResolver,
            NativeLayoutInvoiceService nativeLayoutInvoiceService,
            InvoiceTemplateModelService invoiceTemplateModelService,
            OrderFlowStateMachine orderFlowStateMachine,
            OrderBillingService orderBillingService,
            CatalogRoutingService catalogRoutingService,
            OrderPricingService orderPricingService,
            UomService uomService,
            @Value("${app.order.margin.default-percent:7}") double defaultMarginPercent) {
        this.erpNextClient = erpNextClient;
        this.fileService = fileService;
        this.templateResolver = templateResolver;
        this.nativeLayoutInvoiceService = nativeLayoutInvoiceService;
        this.invoiceTemplateModelService = invoiceTemplateModelService;
        this.orderFlowStateMachine = orderFlowStateMachine;
        this.orderBillingService = orderBillingService;
        this.catalogRoutingService = catalogRoutingService;
        this.orderPricingService = orderPricingService;
        this.uomService = uomService;
        this.defaultMarginPercent = defaultMarginPercent;
    }

    public Map<String, Object> processVendorPdf(String orderId, MultipartFile pdfFile, String sessionCookie) {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("Order id is required.");
        }
        if (pdfFile == null || pdfFile.isEmpty()) {
            throw new IllegalArgumentException("Vendor PDF is required.");
        }
        byte[] pdfBytes = toBytes(pdfFile);
        validatePdf(pdfBytes);

        Map<String, Object> originalOrder = erpNextClient.getResource(SALES_ORDER, orderId);
        Map<String, Object> orderData = unwrapResource(originalOrder);
        String customer = asText(orderData.get("customer"));
        String company = asText(orderData.get("company"));
        String vendor = asText(orderData.get("aas_vendor"));
        String category = asText(orderData.get("aas_category"));
        String currentStatus = asText(orderData.get("aas_status"));
        orderFlowStateMachine.ensureCanUploadVendorPdf(currentStatus);
        String normalizedStatus = orderFlowStateMachine.normalize(currentStatus);
        if (vendor.isBlank()) {
            throw new IllegalStateException("Vendor must be assigned before uploading vendor PDF.");
        }
        if (category.isBlank()) {
            throw new IllegalStateException("Order category is required before uploading vendor PDF.");
        }
        if (customer.isBlank() || company.isBlank()) {
            throw new IllegalStateException("Order must include customer and company before processing vendor PDF.");
        }
        CatalogRoutingService.VendorCategoryResolution vendorResolution =
                catalogRoutingService.resolveVendorForCategory(vendor, category);
        String reviewSourceInvoiceRef = asText(pdfFile.getOriginalFilename());
        String reviewCreatedBy = currentUsername();

        var resolvedJson = templateResolver.loadTemplateJson(vendor);
        if (resolvedJson.isEmpty()) {
            throw new IllegalStateException(TEMPLATE_REQUIRED_ERROR);
        }
        NativeLayoutInvoiceService.StoredProfile nativeProfile = nativeLayoutInvoiceService.parseStoredProfile(resolvedJson.get());
        if (nativeProfile == null) {
            throw new IllegalStateException(TEMPLATE_REQUIRED_ERROR);
        }
        List<ParsedItem> parsedItems;
        String finalAmount;
        String invoiceDate;
        String parserText;
        String transportChargeText;
        NativeLayoutInvoiceService.ExtractionResult extractionResult = nativeLayoutInvoiceService.extract(pdfBytes, nativeProfile);
        parsedItems = coerceParsedItems(extractionResult.items());
        validateParsedTemplateOutput(parsedItems, extractionResult.finalAmount(), extractionResult.layoutText());
        finalAmount = extractionResult.finalAmount();
        invoiceDate = extractionResult.invoiceDate();
        parserText = extractionResult.layoutText();
        transportChargeText = extractionResult.transportCharge();
        List<Integer> expectedSerials = detectExpectedSerials(
                extractionResult.rawTableLines(),
                parserText,
                extractionResult.extractedSerials());
        List<Integer> missingSerials = detectMissingSerials(expectedSerials, extractionResult.extractedSerials());

        List<Map<String, Object>> baseItems = runErpStep(
                "resolve vendor items",
                () -> resolveItems(parsedItems, vendorResolution, orderId, reviewSourceInvoiceRef, reviewCreatedBy));
        List<Map<String, Object>> sourceOrderItems = withVendorRate(baseItems);
        List<Map<String, Object>> sellItems = withSellMargin(baseItems);
        double marginPercent = calculateDerivedMarginPercent(sourceOrderItems);

        Map<String, Object> purchaseOrder = runErpStep(
                "upsert purchase order",
                () -> upsertPurchaseOrder(orderId, vendor, company, baseItems, orderData, currentStatus));
        String purchaseOrderId = extractDocName(purchaseOrder);
        double vendorBillTotal = parseNumber(finalAmount);
        String vendorBillDate = hasText(invoiceDate)
                ? normalizeDateToIso(invoiceDate)
                : "";
        String vendorBillRef = purchaseOrderId == null ? "" : purchaseOrderId;
        double transportCharge = hasText(transportChargeText)
                ? parseNumber(transportChargeText)
                : 0.0;

        UploadedFileInfo pdfInfo;
        try {
            pdfInfo = fileService.uploadOrderPdf(orderId, pdfFile, sessionCookie);
        } catch (Exception ex) {
            String fallbackName = pdfFile.getOriginalFilename() == null ? "vendor_order.pdf" : pdfFile.getOriginalFilename();
            pdfInfo = new UploadedFileInfo(fallbackName, null, null);
        }

        String previousPurchaseInvoiceId = asText(orderData.get("aas_pi_vendor")).trim();
        String previousSalesInvoiceId = asText(orderData.get("aas_si_branch")).trim();
        boolean isBillUpdate = "VENDOR_BILL_CAPTURED".equals(normalizedStatus) || "SELL_ORDER_CREATED".equals(normalizedStatus);
        boolean workflowReset = false;
        if (isBillUpdate) {
            // Re-upload after bill capture / sell order creation should reset workflow back to Step 3.
            // Delete any linked draft invoices so Step 3 & 4 can be re-initiated explicitly by the user.
            if (hasText(previousPurchaseInvoiceId)) {
                deleteLinkedDraftInvoice(orderId, PURCHASE_INVOICE, previousPurchaseInvoiceId, "vendor invoice");
            }
            if ("SELL_ORDER_CREATED".equals(normalizedStatus) && hasText(previousSalesInvoiceId)) {
                deleteLinkedDraftInvoice(orderId, SALES_INVOICE, previousSalesInvoiceId, "branch invoice");
            }
            workflowReset = true;
        }

        Map<String, Object> linkUpdate = new HashMap<>();
        linkUpdate.put("items", sourceOrderItems);
        linkUpdate.put("aas_margin_percent", marginPercent);
        linkUpdate.put("aas_po", purchaseOrderId);
        linkUpdate.put("aas_status", "VENDOR_PDF_RECEIVED");
        linkUpdate.put("aas_vendor_bill_total", vendorBillTotal);
        linkUpdate.put("aas_vendor_bill_ref", vendorBillRef);
        linkUpdate.put("aas_transport_charge", transportCharge);
        if (workflowReset) {
            linkUpdate.put("aas_pi_vendor", "");
            linkUpdate.put("aas_si_branch", "");
            linkUpdate.put("aas_sell_order_total", 0);
        }
        if (vendorBillDate != null && !vendorBillDate.isBlank()) {
            linkUpdate.put("aas_vendor_bill_date", vendorBillDate);
        }
        if (pdfInfo.fileUrl() != null) {
            linkUpdate.put("aas_vendor_pdf", pdfInfo.fileUrl());
        }
        runErpStep("update sales order with vendor invoice details", () -> {
            erpNextClient.updateResource(SALES_ORDER, orderId, linkUpdate);
            return null;
        });

        Map<String, Object> response = new HashMap<>();
        response.put("orderId", orderId);
        response.put("purchaseOrder", purchaseOrder);
        response.put("workflowReset", workflowReset);
        response.put("sellPreview", Map.of(
                "vendorTotal", sumAmount(baseItems),
                "marginPercent", marginPercent,
                "sellTotal", sumAmount(sellItems)));
        response.put("marginPercent", marginPercent);
        response.put("vendorBillTotal", vendorBillTotal);
        response.put("vendorBillRef", vendorBillRef);
        response.put("vendorBillDate", vendorBillDate);
        response.put("transportCharge", transportCharge);
        response.put("items", parsedItems);
        response.put("orderItems", baseItems);
        response.put("completeness", Map.of(
                "expectedItemCount", expectedSerials.size(),
                "extractedItemCount", extractionResult.extractedSerials().size(),
                "itemCountComplete", missingSerials.isEmpty(),
                "expectedSerials", expectedSerials,
                "extractedSerials", extractionResult.extractedSerials(),
                "missingSerials", missingSerials,
                "missingSerialContexts", List.of()));
        response.put("template", Map.of(
                "configured", true,
                "used", true,
                "key", nativeProfile.profileId().isBlank() ? "native_layout" : nativeProfile.profileId()));
        if (!nativeProfile.itemMappings().isEmpty() || !nativeProfile.summaryMappings().isEmpty()) {
            response.put("fieldMapping", Map.of(
                    "itemMappings", nativeProfile.itemMappings(),
                    "summaryMappings", nativeProfile.summaryMappings(),
                    "notes", nativeProfile.notes(),
                    "generatorType", nativeProfile.generatorType(),
                    "generatorModel", nativeProfile.generatorModel()));
        }
        response.put("file", Map.of(
                "fileName", pdfInfo.fileName(),
                "fileUrl", pdfInfo.fileUrl(),
                "fileId", pdfInfo.fileId()));
        return response;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Map<?, ?> map) {
        return (Map<String, Object>) map;
    }

    private void ensureDraftInvoiceCanBeReplaced(String doctype, String invoiceId, String label) {
        if (!hasText(invoiceId)) {
            return;
        }
        Map<String, Object> invoice = unwrapResource(erpNextClient.getResource(doctype, invoiceId));
        int docstatus = (int) Math.round(asDouble(invoice.get("docstatus")));
        if (docstatus != 0) {
            throw new IllegalStateException(
                    "Cannot re-upload/re-parse because the current " + label + " (" + invoiceId + ") is not draft.");
        }
    }

    private void deleteLinkedDraftInvoice(String orderId, String doctype, String invoiceId, String label) {
        if (!hasText(invoiceId)) {
            return;
        }
        Map<String, Object> invoice = unwrapResource(erpNextClient.getResource(doctype, invoiceId));
        int docstatus = (int) Math.round(asDouble(invoice.get("docstatus")));
        if (docstatus != 0) {
            throw new IllegalStateException(
                    "Cannot re-upload/re-parse because the current " + label + " (" + invoiceId + ") is not draft.");
        }
        // ERPNext may block deletion while cross-links exist.
        // Clear both sides deterministically; if we cannot clear, fail fast with a clear message.
        if (hasText(orderId)) {
            if (PURCHASE_INVOICE.equalsIgnoreCase(doctype)) {
                runErpStep("clear order link to vendor invoice before delete", () -> {
                    erpNextClient.updateResource(SALES_ORDER, orderId, Map.of("aas_pi_vendor", ""));
                    return null;
                });
            } else if (SALES_INVOICE.equalsIgnoreCase(doctype)) {
                runErpStep("clear order link to branch invoice before delete", () -> {
                    erpNextClient.updateResource(SALES_ORDER, orderId, Map.of("aas_si_branch", ""));
                    return null;
                });
            }
        }
        // Best-effort only: updating the invoice can trigger ERPNext validations on legacy/invalid drafts
        // (e.g., "Due Date cannot be before Posting / Supplier Invoice Date"). The sales-order link is the
        // important one to clear for deletion; ignore failures here and attempt delete.
        try {
            erpNextClient.updateResource(doctype, invoiceId, Map.of("aas_source_sales_order", ""));
        } catch (Exception ignored) {
            // ignore
        }
        erpNextClient.deleteResource(doctype, invoiceId);
    }

    private void archiveReplacedInvoice(String doctype, String oldInvoiceId, String newInvoiceId) {
        if (!hasText(oldInvoiceId) || !hasText(newInvoiceId) || oldInvoiceId.equals(newInvoiceId)) {
            return;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("aas_invoice_version_status", "OLD");
        payload.put("aas_replaced_by", asText(newInvoiceId));
        erpNextClient.updateResource(doctype, oldInvoiceId, payload);
    }

    private boolean shouldApplyTransportToReplacementInvoice(String salesInvoiceId) {
        if (!hasText(salesInvoiceId)) {
            return false;
        }
        try {
            Map<String, Object> salesInvoice = unwrapResource(erpNextClient.getResource(SALES_INVOICE, salesInvoiceId));
            Object itemsObj = salesInvoice.get("items");
            if (!(itemsObj instanceof List<?> list)) {
                return false;
            }
            for (Object rowObj : list) {
                if (rowObj instanceof Map<?, ?> row && TRANSPORT_ITEM_CODE.equals(asText(row.get("item_code")))) {
                    return true;
                }
            }
        } catch (Exception ignored) {
            return false;
        }
        return false;
    }

    private void validateParsedTemplateOutput(
            List<ParsedItem> parsedItems,
            String finalAmount,
            String parserText) {
        java.util.Set<String> parsedColumns = new java.util.LinkedHashSet<>();
        for (ParsedItem item : parsedItems) {
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
            // Some invoices (e.g. bill of supply / zero-GST) omit or print 0.00 for rate.
            // We still consider the "rate" column present if it was derived from qty + amount.
            if (item.rate() > 0 || (item.qty() > 0 && item.amount() > 0)) {
                parsedColumns.add("rate");
            }
            if (item.gstPercent() != null) {
                parsedColumns.add("gst");
            }
            if (item.amount() > 0) {
                parsedColumns.add("total");
            }
        }
        List<String> missingColumns = invoiceTemplateModelService.requiredItemKeys().stream()
                .filter(key -> !parsedColumns.contains(key))
                .toList();
        if (!missingColumns.isEmpty()) {
            throw new IllegalStateException(
                    "Configured vendor template did not extract required item fields: " + String.join(", ", missingColumns) + ".");
        }
        List<String> missingSummaryFields = new ArrayList<>();
        if (invoiceTemplateModelService.requiredSummaryKeys().contains("final_bill_amount")) {
            double extractedTotal = parseNumber(finalAmount);
            if (extractedTotal <= 0) {
                missingSummaryFields.add("final_bill_amount");
            }
        }
        if (!missingSummaryFields.isEmpty()) {
            throw new IllegalStateException(
                    "Configured vendor template did not extract required summary fields: " + String.join(", ", missingSummaryFields) + ".");
        }
    }

    private List<ParsedItem> coerceParsedItems(List<ParsedItem> parsedItems) {
        if (parsedItems == null || parsedItems.isEmpty()) {
            return List.of();
        }
        boolean hasAnyGst = false;
        for (ParsedItem item : parsedItems) {
            if (item != null && item.gstPercent() != null) {
                hasAnyGst = true;
                break;
            }
        }
        List<ParsedItem> coerced = new ArrayList<>(parsedItems.size());
        for (ParsedItem item : parsedItems) {
            if (item == null) {
                continue;
            }
            double qty = item.qty();
            double amount = item.amount();
            double rate = item.rate();
            if (rate <= 0 && qty > 0 && amount > 0) {
                rate = amount / qty;
            }
            Double gstPercent = item.gstPercent();
            // Treat missing GST as 0 only when the template extracted no GST values at all
            // (common for bill-of-supply / zero-GST formats).
            if (gstPercent == null && !hasAnyGst) {
                gstPercent = 0.0;
            }
            coerced.add(new ParsedItem(
                    item.name(),
                    qty,
                    rate,
                    amount,
                    item.hsn(),
                    gstPercent,
                    item.uom(),
                    item.mrp(),
                    item.rateBeforeTax(),
                    item.rateAfterTax()));
        }
        return coerced;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private double parseNumber(String raw) {
        if (raw == null || raw.isBlank()) {
            return 0.0;
        }
        String text = raw.replace(",", "").trim();
        text = text.replace('O', '0').replace('o', '0');
        text = text.replaceAll("(?i)inr|rs\\.?", "").trim();
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ex) {
            return 0.0;
        }
    }

    private String normalizeDateToIso(String raw) {
        if (raw == null) {
            return "";
        }
        String text = raw.trim();
        if (text.isEmpty()) {
            return "";
        }
        // Strip common OCR artifacts like leading punctuation/letters before the date.
        text = text.replaceAll("^[^0-9]+", "").trim();
        // Prefer ISO-like yyyy-mm-dd
        java.util.regex.Matcher ymd = java.util.regex.Pattern
                .compile("\\b(\\d{4})[\\-/.](\\d{1,2})[\\-/.](\\d{1,2})\\b")
                .matcher(text);
        if (ymd.find()) {
            int y = safeInt(ymd.group(1));
            int m = safeInt(ymd.group(2));
            int d = safeInt(ymd.group(3));
            return toIsoDate(y, m, d);
        }
        java.util.regex.Matcher dmy = java.util.regex.Pattern
                .compile("\\b(\\d{1,2})[\\-/.](\\d{1,2})[\\-/.](\\d{2,4})\\b")
                .matcher(text);
        if (dmy.find()) {
            int d = safeInt(dmy.group(1));
            int m = safeInt(dmy.group(2));
            int y = safeInt(dmy.group(3));
            if (y < 100) {
                y += 2000;
            }
            return toIsoDate(y, m, d);
        }
        // Month name format: 27-Feb-26 / 27-Feb-2026
        java.util.regex.Matcher dMonY = java.util.regex.Pattern
                .compile("\\b(\\d{1,2})[\\-/.]([A-Za-z]{3,})[\\-/.](\\d{2,4})\\b")
                .matcher(text);
        if (dMonY.find()) {
            int d = safeInt(dMonY.group(1));
            int m = monthToNumber(dMonY.group(2));
            int y = safeInt(dMonY.group(3));
            if (y < 100) {
                y += 2000;
            }
            return toIsoDate(y, m, d);
        }
        return "";
    }

    private int safeInt(String raw) {
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception ex) {
            return 0;
        }
    }

    private String toIsoDate(int y, int m, int d) {
        try {
            return java.time.LocalDate.of(y, m, d).toString();
        } catch (Exception ex) {
            return "";
        }
    }

    private int monthToNumber(String raw) {
        if (raw == null) {
            return 0;
        }
        String key = raw.trim().toLowerCase();
        if (key.length() >= 3) {
            key = key.substring(0, 3);
        }
        return switch (key) {
            case "jan" -> 1;
            case "feb" -> 2;
            case "mar" -> 3;
            case "apr" -> 4;
            case "may" -> 5;
            case "jun" -> 6;
            case "jul" -> 7;
            case "aug" -> 8;
            case "sep" -> 9;
            case "oct" -> 10;
            case "nov" -> 11;
            case "dec" -> 12;
            default -> 0;
        };
    }

    private void validatePdf(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IllegalArgumentException("Vendor PDF is required.");
        }
        try (PDDocument ignored = Loader.loadPDF(pdfBytes)) {
            // Valid PDF.
        } catch (IOException ex) {
            throw new IllegalArgumentException("Invalid PDF file. Please upload a real PDF export.");
        }
    }

    private List<Map<String, Object>> resolveItems(
            List<ParsedItem> parsedItems,
            CatalogRoutingService.VendorCategoryResolution vendorResolution,
            String sourceOrderId,
            String sourceInvoiceRef,
            String reviewCreatedBy) {
        List<Map<String, Object>> rows = new ArrayList<>();
        for (ParsedItem item : parsedItems) {
            String itemName = item.name();
            String vendorHsnCode = asText(catalogRoutingService.normalizeCodeSegment(item.hsn()));
            if (vendorHsnCode.isBlank()) {
                throw new IllegalStateException(
                        "Vendor HSN code is required for parsed item \"" + itemName
                                + "\". Update the vendor template/parser so item_id or hsn is captured.");
            }
            String itemCode = catalogRoutingService.buildParsedItemCode(
                    vendorResolution.vendorCode(),
                    vendorResolution.categoryCode(),
                    vendorHsnCode,
                    itemName);
            String uom = resolveItemUom(item, itemCode, itemName);
            uomService.ensureUomExists(uom);
            if (!resourceExists(itemCode)) {
                itemCode = createItem(itemName, vendorResolution, vendorHsnCode, uom, sourceOrderId, sourceInvoiceRef, reviewCreatedBy);
            }
            ensureItemEnabled(itemCode);
            Map<String, Object> row = new HashMap<>();
            row.put("item_code", itemCode);
            row.put("item_name", itemName);
            row.put("qty", item.qty());
            row.put("uom", uom);
            row.put("stock_uom", uom);
            row.put("rate", item.rate());
            if (item.rateBeforeTax() != null && item.rateBeforeTax() > 0) {
                row.put("aas_rate_before_tax", round(item.rateBeforeTax()));
            }
            if (item.rateAfterTax() != null && item.rateAfterTax() > 0) {
                row.put("aas_rate_after_tax", round(item.rateAfterTax()));
            }
            row.put("aas_margin_percent", resolveItemMarginPercent(itemCode));
            if (item.gstPercent() != null && item.gstPercent() >= 0) {
                row.put("aas_gst_percent", round(item.gstPercent()));
            }
            if (item.mrp() != null && item.mrp() > 0) {
                row.put("aas_mrp", round(item.mrp()));
            }
            double amount = item.amount();
            if (amount <= 0 && item.qty() > 0) {
                amount = item.rate() * item.qty();
            }
            row.put("amount", amount);
            rows.add(row);
        }
        return rows;
    }

    private String resolveItemUom(ParsedItem item, String candidateItemCode, String itemName) {
        String parsedUom = uomService.normalizeUom(item.uom());
        boolean fractionalQty = hasFractionalQuantity(item.qty());

        String existingUom = firstNonBlank(
                loadItemStockUom(candidateItemCode),
                loadItemStockUom(findItemCodeByName(itemName)));

        if (!parsedUom.isBlank()) {
            if (fractionalQty && "Nos".equalsIgnoreCase(parsedUom) && !existingUom.isBlank() && !"Nos".equalsIgnoreCase(existingUom)) {
                return existingUom;
            }
            return parsedUom;
        }

        if (!existingUom.isBlank()) {
            return existingUom;
        }

        if (fractionalQty) {
            return "Qty";
        }
        return "Nos";
    }

    private void ensureItemEnabled(String itemCode) {
        if (itemCode == null || itemCode.isBlank()) {
            return;
        }
        try {
            Map<String, Object> item = unwrapResource(erpNextClient.getResource(ITEM, itemCode));
            Object disabled = item == null ? null : item.get("disabled");
            if (disabled instanceof Number n && n.intValue() != 0) {
                erpNextClient.updateResource(ITEM, itemCode, Map.of("disabled", 0));
            } else if (disabled instanceof Boolean b && b) {
                erpNextClient.updateResource(ITEM, itemCode, Map.of("disabled", 0));
            } else if (disabled != null && "1".equals(disabled.toString().trim())) {
                erpNextClient.updateResource(ITEM, itemCode, Map.of("disabled", 0));
            }
        } catch (Exception ignored) {
            // Best-effort. If enabling fails, PO creation might still fail and the user will see ERP error.
        }
    }

    private String normalizeNameForLookup(String itemName) {
        if (itemName == null) {
            return "";
        }
        String cleaned = itemName.trim();
        // Remove likely HSN codes / long numeric tokens.
        cleaned = cleaned.replaceAll("\\b\\d{4,10}\\b", " ");
        // Remove trailing qty-like tokens that OCR sometimes appends into names.
        cleaned = cleaned.replaceAll("\\b\\d{1,3}(?:\\.\\d+)?\\b\\s*$", " ");
        cleaned = NON_ALNUM_PATTERN.matcher(cleaned).replaceAll(" ");
        cleaned = cleaned.replaceAll("\\s+", " ").trim();
        return cleaned;
    }

    private String findSingleItemCodeByLike(String cleanedName) {
        if (cleanedName == null || cleanedName.isBlank()) {
            return null;
        }
        if (cleanedName.length() < 4) {
            return null;
        }
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\",\"item_name\"]");
        params.put("limit_page_length", "5");
        params.put("filters", "[[\"item_name\",\"like\",\"%" + escape(cleanedName) + "%\"]]");
        List<Map<String, Object>> data = erpNextClient.listResources(ITEM, params);
        if (data.size() != 1) {
            return null;
        }
        Object name = data.get(0).get("name");
        return name == null ? null : name.toString();
    }

    private List<Map<String, Object>> withVendorRate(List<Map<String, Object>> baseItems) {
        List<Map<String, Object>> enriched = new ArrayList<>();
        for (Map<String, Object> row : baseItems) {
            Map<String, Object> copy = new HashMap<>(row);
            double vendorRate = resolveVendorRateBeforeTax(row);
            double marginPercent = resolveMarginPercent(row.get("aas_margin_percent"));
            OrderPricingService.LinePricing pricing = orderPricingService.applyMrpCap(
                    vendorRate,
                    marginPercent,
                    asNullableDouble(row.get("aas_mrp")),
                    asText(row.get("item_name")).isBlank() ? asText(row.get("item_code")) : asText(row.get("item_name")));
            copy.put("aas_vendor_rate", vendorRate);
            copy.put("aas_margin_percent", pricing.effectiveMarginPercent());
            enriched.add(copy);
        }
        return enriched;
    }

    private List<Map<String, Object>> withSellMargin(List<Map<String, Object>> baseItems) {
        List<Map<String, Object>> enriched = new ArrayList<>();
        for (Map<String, Object> row : baseItems) {
            Map<String, Object> copy = new HashMap<>(row);
            double vendorRate = resolveVendorRateBeforeTax(row);
            double qty = asDouble(row.get("qty"));
            double marginPercent = resolveMarginPercent(row.get("aas_margin_percent"));
            OrderPricingService.LinePricing pricing = orderPricingService.applyMrpCap(
                    vendorRate,
                    marginPercent,
                    asNullableDouble(row.get("aas_mrp")),
                    asText(row.get("item_name")).isBlank() ? asText(row.get("item_code")) : asText(row.get("item_name")));
            copy.put("rate", pricing.sellRate());
            copy.put("amount", round(pricing.sellRate() * qty));
            copy.put("aas_vendor_rate", vendorRate);
            copy.put("aas_margin_percent", pricing.effectiveMarginPercent());
            enriched.add(copy);
        }
        return enriched;
    }

    private double resolveVendorRateBeforeTax(Map<String, Object> row) {
        double explicitBeforeTax = asDouble(row.get("aas_rate_before_tax"));
        if (explicitBeforeTax > 0) {
            return explicitBeforeTax;
        }
        double vendorRate = asDouble(row.get("rate"));
        if (vendorRate > 0) {
            return vendorRate;
        }
        double rateAfterTax = asDouble(row.get("aas_rate_after_tax"));
        double gstPercent = asDouble(row.get("aas_gst_percent"));
        if (rateAfterTax > 0 && gstPercent > 0) {
            return round(rateAfterTax / (1 + (gstPercent / 100.0)));
        }
        return rateAfterTax;
    }

    private double calculateDerivedMarginPercent(List<Map<String, Object>> items) {
        double vendorTotal = 0.0;
        double sellTotal = 0.0;
        for (Map<String, Object> row : items) {
            double qty = asDouble(row.get("qty"));
            if (qty <= 0) {
                qty = 1.0;
            }
            double vendorRate = asDouble(row.get("aas_vendor_rate"));
            if (vendorRate <= 0) {
                vendorRate = asDouble(row.get("rate"));
            }
            double marginPercent = resolveMarginPercent(row.get("aas_margin_percent"));
            vendorTotal += vendorRate * qty;
            sellTotal += vendorRate * (1 + marginPercent / 100.0) * qty;
        }
        vendorTotal = round(vendorTotal);
        sellTotal = round(sellTotal);
        if (vendorTotal <= 0) {
            return defaultMarginPercent;
        }
        return round(((sellTotal - vendorTotal) / vendorTotal) * 100.0);
    }

    private double resolveItemMarginPercent(String itemCode) {
        if (itemCode == null || itemCode.isBlank()) {
            return defaultMarginPercent;
        }
        try {
            Map<String, Object> item = unwrapResource(erpNextClient.getResource(ITEM, itemCode));
            double margin = asDouble(item.get("aas_margin_percent"));
            if (margin > 0) {
                return margin;
            }
        } catch (Exception ignored) {
            // Fall back to default margin if item master lookup fails.
        }
        return defaultMarginPercent;
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

    private String loadItemStockUom(String itemCode) {
        if (itemCode == null || itemCode.isBlank()) {
            return "";
        }
        try {
            Map<String, Object> item = unwrapResource(erpNextClient.getResource(ITEM, itemCode));
            return uomService.normalizeUom(asText(item.get("stock_uom")));
        } catch (Exception ignored) {
            return "";
        }
    }

    private String createItem(
            String itemName,
            CatalogRoutingService.VendorCategoryResolution vendorResolution,
            String vendorHsnCode,
            String uom,
            String sourceOrderId,
            String sourceInvoiceRef,
            String reviewCreatedBy) {
        Map<String, Object> payload = new HashMap<>();
        String code = catalogRoutingService.buildParsedItemCode(
                vendorResolution.vendorCode(),
                vendorResolution.categoryCode(),
                vendorHsnCode,
                itemName);
        payload.put("item_code", code);
        payload.put("item_name", itemName);
        payload.put("item_group", vendorResolution.categoryId());
        payload.put("stock_uom", uomService.normalizeUom(asText(uom).isBlank() ? "Nos" : uom));
        payload.put("is_stock_item", 1);
        payload.put("aas_vendor", vendorResolution.vendorId());
        payload.put("aas_vendor_hsn_code", vendorHsnCode);
        payload.put("aas_margin_percent", defaultMarginPercent);
        payload.put("aas_review_status", "PENDING_REVIEW");
        payload.put("aas_review_source_order", asText(sourceOrderId));
        payload.put("aas_review_source_invoice_ref", asText(sourceInvoiceRef));
        payload.put("aas_review_created_at", LocalDateTime.now().format(ERP_DATE_TIME));
        payload.put("aas_review_created_by", asText(reviewCreatedBy));
        payload.put("aas_review_default_margin_used", 1);
        Map<String, Object> created = erpNextClient.createResource(ITEM, payload);
        Object name = created.get("name");
        return name == null ? code : name.toString();
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication.getName() == null) {
            return "";
        }
        return authentication.getName().trim();
    }

    private boolean resourceExists(String itemCode) {
        try {
            Map<String, Object> item = unwrapResource(erpNextClient.getResource(ITEM, itemCode));
            return !item.isEmpty();
        } catch (Exception ignored) {
            return false;
        }
    }

    private boolean hasFractionalQuantity(double qty) {
        return Math.abs(qty - Math.rint(qty)) > 0.000001d;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return "";
    }

    private boolean resourceExists(String doctype, String name) {
        if (doctype == null || doctype.isBlank() || name == null || name.isBlank()) {
            return false;
        }
        try {
            Map<String, Object> resource = unwrapResource(erpNextClient.getResource(doctype, name));
            return !resource.isEmpty();
        } catch (Exception ignored) {
            return false;
        }
    }

    private Map<String, Object> upsertPurchaseOrder(
            String sourceOrderId,
            String vendor,
            String company,
            List<Map<String, Object>> items,
            Map<String, Object> originalOrder,
            String currentStatus) {
        String normalizedStatus = orderFlowStateMachine.normalize(currentStatus);
        String warehouse = resolveWarehouse(originalOrder);
        if (warehouse.isBlank()) {
            warehouse = resolveDefaultWarehouse(company);
        }
        List<Map<String, Object>> poItems = withWarehouse(items, warehouse);
        Map<String, Object> payload = new HashMap<>();
        payload.put("supplier", vendor);
        payload.put("company", company);
        payload.put("schedule_date", resolveScheduleDate(originalOrder));
        payload.put("items", poItems);
        payload.put("aas_source_sales_order", sourceOrderId);
        String existingPurchaseOrderId = asText(originalOrder.get("aas_po")).trim();
        if (("VENDOR_PDF_RECEIVED".equals(normalizedStatus)
                || "VENDOR_BILL_CAPTURED".equals(normalizedStatus)
                || "SELL_ORDER_CREATED".equals(normalizedStatus))
                && !existingPurchaseOrderId.isBlank()
                && resourceExists(PURCHASE_ORDER, existingPurchaseOrderId)) {
            Map<String, Object> existingPurchaseOrder = unwrapResource(erpNextClient.getResource(PURCHASE_ORDER, existingPurchaseOrderId));
            int docstatus = (int) Math.round(asDouble(existingPurchaseOrder.get("docstatus")));
            if (docstatus != 0) {
                throw new IllegalStateException(
                        "Linked purchase order " + existingPurchaseOrderId + " is already submitted and cannot be refreshed from a re-uploaded vendor PDF.");
            }
            return erpNextClient.updateResource(PURCHASE_ORDER, existingPurchaseOrderId, payload);
        }
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
            if (!warehouse.isBlank() && asText(copy.get("warehouse")).isBlank()) {
                copy.put("warehouse", warehouse);
            }
            rows.add(copy);
        }
        return rows;
    }

    private String resolveDefaultWarehouse(String company) {
        if (company == null || company.isBlank()) {
            return "";
        }
        String abbr = "";
        try {
            Map<String, Object> companyDoc = erpNextClient.getResource(COMPANY, company);
            abbr = asText(companyDoc.get("abbr"));
        } catch (Exception ex) {
            abbr = "";
        }
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\",\"warehouse_name\",\"company\",\"is_group\"]");
        params.put("limit_page_length", "50");
        params.put("filters", "[[\"company\",\"=\",\"" + escape(company) + "\"],[\"is_group\",\"=\",\"0\"]]");
        List<Map<String, Object>> warehouses = erpNextClient.listResources(WAREHOUSE, params);
        if (warehouses.isEmpty()) {
            return "";
        }
        if (!abbr.isBlank()) {
            String preferred = "Stores - " + abbr;
            for (Map<String, Object> wh : warehouses) {
                String name = asText(wh.get("name"));
                if (preferred.equals(name)) {
                    return name;
                }
            }
        }
        return asText(warehouses.get(0).get("name"));
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
        if (value == null || value.toString().trim().isEmpty() || margin == 0.0) {
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

    private Double asNullableDouble(Object value) {
        double parsed = asDouble(value);
        return parsed > 0 ? parsed : null;
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

    private List<Integer> detectExpectedSerials(
            List<String> rawTableLines,
            String parserText,
            List<Integer> extractedSerials) {
        List<Integer> rawNumbers = detectSerialCandidates(rawTableLines, parserText);
        List<Integer> extractedNumbers = normalizePositiveSerials(extractedSerials);

        List<Integer> rawExpected = expectedRangeFromObserved(rawNumbers);
        List<Integer> extractedExpected = expectedRangeFromExtracted(extractedNumbers);

        if (shouldPreferExtractedExpected(rawExpected, extractedExpected, extractedNumbers)) {
            return extractedExpected;
        }
        if (!rawExpected.isEmpty() && rawExpected.size() > 1) {
            return rawExpected;
        }
        if (!extractedExpected.isEmpty() && extractedExpected.size() > 1) {
            return extractedExpected;
        }
        return rawExpected.isEmpty() ? extractedExpected : rawExpected;
    }

    private boolean shouldPreferExtractedExpected(
            List<Integer> rawExpected,
            List<Integer> extractedExpected,
            List<Integer> extractedNumbers) {
        if (rawExpected == null || rawExpected.isEmpty() || extractedExpected == null || extractedExpected.isEmpty()) {
            return false;
        }
        if (rawExpected.size() <= extractedExpected.size()) {
            return false;
        }
        int rawMax = rawExpected.getLast();
        int extractedMax = extractedExpected.getLast();
        int extractedCount = extractedNumbers == null ? 0 : extractedNumbers.size();
        if (extractedMax <= 0 || extractedCount <= 0) {
            return false;
        }
        int maxGap = rawMax - extractedMax;
        int sizeGap = rawExpected.size() - extractedExpected.size();
        return maxGap >= 25
                && sizeGap >= 25
                && extractedCount >= Math.max(10, extractedMax / 2);
    }

    private List<Integer> detectSerialCandidates(List<String> rawTableLines, String parserText) {
        List<Integer> numbers = new ArrayList<>();
        List<String> sourceLines = new ArrayList<>();
        if (rawTableLines != null && !rawTableLines.isEmpty()) {
            sourceLines.addAll(rawTableLines);
        } else if (parserText != null && !parserText.isBlank()) {
            for (String line : parserText.replace('\f', '\n').split("\\r?\\n")) {
                sourceLines.add(line);
            }
        }
        for (String rawLine : sourceLines) {
            String line = asText(rawLine);
            java.util.regex.Matcher leadingSerial = LEADING_SERIAL_PATTERN.matcher(line);
            int value;
            if (leadingSerial.find()) {
                value = parseInteger(leadingSerial.group(1));
            } else if (line.matches("^\\d{1,3}$")) {
                value = parseInteger(line);
            } else {
                continue;
            }
            if (value > 0 && value <= 500) {
                numbers.add(value);
            }
        }
        return numbers;
    }

    private List<Integer> normalizePositiveSerials(List<Integer> serials) {
        if (serials == null || serials.isEmpty()) {
            return List.of();
        }
        return serials.stream()
                .filter(Objects::nonNull)
                .map(Integer::intValue)
                .filter(value -> value > 0 && value <= 500)
                .distinct()
                .sorted()
                .toList();
    }

    private List<Integer> expectedRangeFromObserved(List<Integer> numbers) {
        if (numbers == null || numbers.isEmpty()) {
            return List.of();
        }
        List<Integer> bestRun = new ArrayList<>();
        List<Integer> currentRun = new ArrayList<>();
        for (Integer value : numbers) {
            if (currentRun.isEmpty()) {
                currentRun.add(value);
                continue;
            }
            int previous = currentRun.get(currentRun.size() - 1);
            if (value == previous) {
                continue;
            }
            if (value == previous + 1) {
                currentRun.add(value);
                continue;
            }
            if (preferSerialRun(currentRun, bestRun)) {
                bestRun = new ArrayList<>(currentRun);
            }
            currentRun = new ArrayList<>();
            currentRun.add(value);
        }
        if (preferSerialRun(currentRun, bestRun)) {
            bestRun = currentRun;
        }
        if (bestRun.isEmpty()) {
            return List.of();
        }
        List<Integer> expected = new ArrayList<>();
        for (int value = 1; value <= bestRun.get(bestRun.size() - 1); value++) {
            expected.add(value);
        }
        return expected;
    }

    private List<Integer> expectedRangeFromExtracted(List<Integer> extractedSerials) {
        if (extractedSerials == null || extractedSerials.isEmpty()) {
            return List.of();
        }
        if (extractedSerials.getFirst() != 1) {
            return List.of();
        }
        int max = extractedSerials.getLast();
        List<Integer> expected = new ArrayList<>();
        for (int value = 1; value <= max; value++) {
            expected.add(value);
        }
        return expected;
    }

    private List<Integer> detectMissingSerials(List<Integer> expectedSerials, List<Integer> extractedSerials) {
        if (expectedSerials == null || expectedSerials.isEmpty()) {
            return List.of();
        }
        Set<Integer> extracted = new LinkedHashSet<>(extractedSerials == null ? List.of() : extractedSerials);
        List<Integer> missing = new ArrayList<>();
        for (Integer expected : expectedSerials) {
            if (!extracted.contains(expected)) {
                missing.add(expected);
            }
        }
        return missing;
    }

    private boolean preferSerialRun(List<Integer> candidate, List<Integer> currentBest) {
        if (candidate.isEmpty()) {
            return false;
        }
        if (currentBest.isEmpty()) {
            return true;
        }
        boolean candidateStartsAtOne = candidate.getFirst() == 1;
        boolean bestStartsAtOne = currentBest.getFirst() == 1;
        if (candidateStartsAtOne != bestStartsAtOne) {
            return candidateStartsAtOne;
        }
        return candidate.size() > currentBest.size();
    }

    private int parseInteger(String raw) {
        try {
            return Integer.parseInt(asText(raw));
        } catch (Exception ex) {
            return 0;
        }
    }

    private <T> T runErpStep(String step, Supplier<T> action) {
        try {
            return action.get();
        } catch (FeignException ex) {
            // Let the global API exception handler translate/sanitize ERPNext error payloads.
            throw ex;
        }
    }
}
