package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.config.InvoiceTemplateModelProperties;
import com.aas.mw.dto.ParsedItem;
import com.aas.mw.dto.UploadedFileInfo;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.atLeastOnce;

class VendorPdfServiceTest {

    private static final String NATIVE_PROFILE_JSON = """
            {
              "kind": "native_layout_mapping",
              "engine": {
                "type": "native_layout",
                "reader": "pdftotext_layout"
              },
              "profile": {
                "id": "vendor_a_native_layout",
                "label": "Vendor A native layout",
                "vendorName": "Vendor A"
              },
              "fieldMapping": {
                "itemMappings": [],
                "summaryMappings": []
              }
            }
            """;

    private ErpNextClient erpNextClient;
    private ErpNextFileService fileService;
    private VendorInvoiceTemplateResolver templateResolver;
    private NativeLayoutInvoiceService nativeLayoutInvoiceService;
    private OrderFlowStateMachine orderFlowStateMachine;
    private OrderBillingService orderBillingService;
    private CatalogRoutingService catalogRoutingService;
    private UomService uomService;
    private VendorPdfService service;

    @BeforeEach
    void setup() {
        erpNextClient = mock(ErpNextClient.class);
        fileService = mock(ErpNextFileService.class);
        templateResolver = mock(VendorInvoiceTemplateResolver.class);
        nativeLayoutInvoiceService = mock(NativeLayoutInvoiceService.class);
        orderFlowStateMachine = new OrderFlowStateMachine();
        orderBillingService = mock(OrderBillingService.class);
        catalogRoutingService = mock(CatalogRoutingService.class);
        uomService = mock(UomService.class);
        when(uomService.normalizeUom(any())).thenAnswer(invocation -> {
            Object raw = invocation.getArgument(0);
            return raw == null ? "" : raw.toString().trim();
        });
        when(catalogRoutingService.normalizeCodeSegment(any())).thenAnswer(invocation -> {
            Object raw = invocation.getArgument(0);
            String value = raw == null ? "" : raw.toString().trim().toUpperCase(Locale.ROOT);
            value = value.replaceAll("[^A-Z0-9]+", "_");
            value = value.replaceAll("_+", "_");
            value = value.replaceAll("^_+", "").replaceAll("_+$", "");
            return value;
        });
        when(catalogRoutingService.buildItemCode(any(), any(), any())).thenAnswer(invocation -> {
            String vendorCode = catalogRoutingService.normalizeCodeSegment(invocation.getArgument(0));
            String categoryCode = catalogRoutingService.normalizeCodeSegment(invocation.getArgument(1));
            String vendorHsnCode = catalogRoutingService.normalizeCodeSegment(invocation.getArgument(2));
            return vendorCode + "_" + categoryCode + "_" + vendorHsnCode;
        });
        when(catalogRoutingService.buildParsedItemCode(any(), any(), any(), any())).thenAnswer(invocation -> {
            String base = catalogRoutingService.buildItemCode(
                    invocation.getArgument(0),
                    invocation.getArgument(1),
                    invocation.getArgument(2));
            String itemName = catalogRoutingService.normalizeCodeSegment(invocation.getArgument(3));
            return itemName.isBlank() ? base : base + "_" + itemName;
        });
        when(fileService.uploadOrderPdf(any(), any(), any()))
                .thenReturn(new UploadedFileInfo("vendor_order.pdf", "/files/vendor_order.pdf", "FILE-0001"));
        service = new VendorPdfService(
                erpNextClient,
                fileService,
                templateResolver,
                nativeLayoutInvoiceService,
                new InvoiceTemplateModelService(new InvoiceTemplateModelProperties()),
                orderFlowStateMachine,
                orderBillingService,
                catalogRoutingService,
                new OrderPricingService(),
                uomService,
                7.0);
    }

    @Test
    void processesVendorPdfUsingNativeLayoutProfile() {
        MockMultipartFile pdf = validPdf();
        mockOrderContext();
        mockNativeProfile();
        when(nativeLayoutInvoiceService.extract(any(), any()))
                .thenReturn(new NativeLayoutInvoiceService.ExtractionResult(
                        List.of(new ParsedItem("Tomatoes", 2, 45, 90, "11010000", 5.0, "KG", null)),
                        "INV-1",
                        "2026-02-19",
                        "90.00",
                        "",
                        "",
                        List.of(),
                        List.of()));
        when(erpNextClient.createResource(eq("Item"), any())).thenReturn(Map.of("name", "ITEM-001"));
        when(erpNextClient.createResource(eq("Purchase Order"), any())).thenReturn(Map.of("name", "PO-0001"));

        Map<String, Object> response = service.processVendorPdf("SO-0001", pdf, "sid=abc");

        assertNotNull(response.get("purchaseOrder"));
        assertEquals(90.0, response.get("vendorBillTotal"));
        assertEquals(Map.of("configured", true, "used", true, "key", "vendor_a_native_layout"), response.get("template"));

        ArgumentCaptor<Map<String, Object>> updateCaptor = ArgumentCaptor.forClass(Map.class);
        verify(erpNextClient, Mockito.atLeastOnce()).updateResource(eq("Sales Order"), eq("SO-0001"), updateCaptor.capture());
        assertEquals("VENDOR_PDF_RECEIVED", updateCaptor.getValue().get("aas_status"));
        verify(nativeLayoutInvoiceService).extract(any(), any());
    }

    @Test
    void marksAutoCreatedItemsForAdminReview() {
        MockMultipartFile pdf = validPdf();
        mockOrderContext();
        mockNativeProfile();
        when(nativeLayoutInvoiceService.extract(any(), any()))
                .thenReturn(new NativeLayoutInvoiceService.ExtractionResult(
                        List.of(new ParsedItem("Tomatoes", 2, 45, 90, "11010000", 5.0, "KG", null)),
                        "INV-1",
                        "2026-02-19",
                        "90.00",
                        "",
                        "",
                        List.of(),
                        List.of()));
        when(erpNextClient.createResource(eq("Item"), any())).thenReturn(Map.of("name", "ITEM-001"));
        when(erpNextClient.createResource(eq("Purchase Order"), any())).thenReturn(Map.of("name", "PO-0001"));

        service.processVendorPdf("SO-0001", pdf, "sid=abc");

        ArgumentCaptor<Map<String, Object>> createCaptor = ArgumentCaptor.forClass(Map.class);
        verify(erpNextClient).createResource(eq("Item"), createCaptor.capture());
        assertEquals("PENDING_REVIEW", createCaptor.getValue().get("aas_review_status"));
        assertEquals("SO-0001", createCaptor.getValue().get("aas_review_source_order"));
        assertEquals("vendor_order.pdf", createCaptor.getValue().get("aas_review_source_invoice_ref"));
        assertEquals(1, createCaptor.getValue().get("aas_review_default_margin_used"));
        assertEquals(7.0, createCaptor.getValue().get("aas_margin_percent"));
        assertTrue(String.valueOf(createCaptor.getValue().get("aas_review_created_at")).startsWith("20"));
    }

    @Test
    void storesTransportChargeFromNativeLayoutExtraction() {
        MockMultipartFile pdf = validPdf();
        mockOrderContext();
        mockNativeProfile();
        when(nativeLayoutInvoiceService.extract(any(), any()))
                .thenReturn(new NativeLayoutInvoiceService.ExtractionResult(
                        List.of(new ParsedItem("Tomatoes", 2, 45, 90, "11010000", 5.0, "KG", null)),
                        "INV-1",
                        "2026-02-19",
                        "140.00",
                        "50.00",
                        "",
                        List.of(),
                        List.of()));
        when(erpNextClient.createResource(eq("Item"), any())).thenReturn(Map.of("name", "ITEM-001"));
        when(erpNextClient.createResource(eq("Purchase Order"), any())).thenReturn(Map.of("name", "PO-0001"));

        Map<String, Object> response = service.processVendorPdf("SO-0001", pdf, "sid=abc");

        assertEquals(50.0, response.get("transportCharge"));
    }

    @Test
    void usesExistingItemUomWhenFractionalQuantityWouldOtherwiseDefaultToNos() {
        when(erpNextClient.getResource(eq("Item"), eq("VEND_A_GROCERY_11010000_TOMATOES")))
                .thenReturn(Map.of("data", Map.of(
                        "name", "VEND_A_GROCERY_11010000_TOMATOES",
                        "stock_uom", "Kg")));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) ReflectionTestUtils.invokeMethod(
                service,
                "resolveItems",
                List.of(new ParsedItem("Tomatoes", 0.5, 45, 22.5, "11010000", 5.0, null, null)),
                new CatalogRoutingService.VendorCategoryResolution("Vendor A", "Vendor A", "VEND_A", "Grocery", "Grocery", "GROCERY"),
                "SO-0001",
                "vendor_order.pdf",
                "Administrator");

        assertEquals("Kg", rows.get(0).get("uom"));
        assertEquals("Kg", rows.get(0).get("stock_uom"));
    }

    @Test
    void usesFractionSafeQtyUomForNewItemsWhenQuantityIsFractionalAndNoUomIsParsed() {
        when(erpNextClient.getResource(eq("Item"), eq("VEND_A_GROCERY_11010000_TOMATOES")))
                .thenThrow(new RuntimeException("not found"));
        when(erpNextClient.listResources(eq("Item"), any())).thenReturn(List.of());
        when(erpNextClient.createResource(eq("Item"), any())).thenReturn(Map.of("name", "ITEM-001"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) ReflectionTestUtils.invokeMethod(
                service,
                "resolveItems",
                List.of(new ParsedItem("Tomatoes", 0.5, 45, 22.5, "11010000", 5.0, null, null)),
                new CatalogRoutingService.VendorCategoryResolution("Vendor A", "Vendor A", "VEND_A", "Grocery", "Grocery", "GROCERY"),
                "SO-0001",
                "vendor_order.pdf",
                "Administrator");

        ArgumentCaptor<Map<String, Object>> createCaptor = ArgumentCaptor.forClass(Map.class);
        verify(erpNextClient).createResource(eq("Item"), createCaptor.capture());
        assertEquals("Qty", createCaptor.getValue().get("stock_uom"));
        assertEquals("Qty", rows.get(0).get("uom"));
    }

    @Test
    void convertsFractionalPcsToQtyInsteadOfNos() {
        when(erpNextClient.getResource(eq("Item"), eq("VEND_A_GROCERY_11010000_MDH_CHANA_MASALA")))
                .thenThrow(new RuntimeException("not found"));
        when(erpNextClient.listResources(eq("Item"), any())).thenReturn(List.of());
        when(erpNextClient.createResource(eq("Item"), any())).thenReturn(Map.of("name", "ITEM-PCS-001"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) ReflectionTestUtils.invokeMethod(
                service,
                "resolveItems",
                List.of(new ParsedItem("MDH CHANA MASALA", 0.5, 750, 375, "09109929", 5.0, "PCS", null)),
                new CatalogRoutingService.VendorCategoryResolution("Vendor A", "Vendor A", "VEND_A", "Grocery", "Grocery", "GROCERY"),
                "SO-0001",
                "vendor_order.pdf",
                "Administrator");

        ArgumentCaptor<Map<String, Object>> createCaptor = ArgumentCaptor.forClass(Map.class);
        verify(erpNextClient).createResource(eq("Item"), createCaptor.capture());
        assertEquals("Qty", createCaptor.getValue().get("stock_uom"));
        assertEquals("Qty", rows.get(0).get("uom"));
        assertEquals("Qty", rows.get(0).get("stock_uom"));
    }

    @Test
    void createsUomSpecificVariantWhenExistingBaseItemUsesNosForFractionalQuantity() {
        when(erpNextClient.getResource(eq("Item"), eq("VEND_A_GROCERY_11010000_TOMATOES")))
                .thenReturn(Map.of("data", Map.of(
                        "name", "VEND_A_GROCERY_11010000_TOMATOES",
                        "stock_uom", "Nos")));
        when(erpNextClient.getResource(eq("Item"), eq("VEND_A_GROCERY_11010000_TOMATOES_QTY")))
                .thenThrow(new RuntimeException("not found"));
        when(erpNextClient.listResources(eq("Item"), any())).thenReturn(List.of());
        when(erpNextClient.createResource(eq("Item"), any())).thenReturn(Map.of("name", "ITEM-QTY-001"));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> rows = (List<Map<String, Object>>) ReflectionTestUtils.invokeMethod(
                service,
                "resolveItems",
                List.of(new ParsedItem("Tomatoes", 0.5, 45, 22.5, "11010000", 5.0, null, null)),
                new CatalogRoutingService.VendorCategoryResolution("Vendor A", "Vendor A", "VEND_A", "Grocery", "Grocery", "GROCERY"),
                "SO-0001",
                "vendor_order.pdf",
                "Administrator");

        ArgumentCaptor<Map<String, Object>> createCaptor = ArgumentCaptor.forClass(Map.class);
        verify(erpNextClient).createResource(eq("Item"), createCaptor.capture());
        assertEquals("VEND_A_GROCERY_11010000_TOMATOES_QTY", createCaptor.getValue().get("item_code"));
        assertEquals("Qty", createCaptor.getValue().get("stock_uom"));
        assertEquals("ITEM-QTY-001", rows.get(0).get("item_code"));
        assertEquals("Qty", rows.get(0).get("stock_uom"));
    }

    @Test
    void reuploadUpdatesExistingDraftPurchaseOrderInsteadOfDeletingIt() {
        MockMultipartFile pdf = validPdf();
        when(erpNextClient.getResource(eq("Sales Order"), eq("SO-0001")))
                .thenReturn(Map.of(
                        "customer", "Sukarta Aundh",
                        "company", "AAS",
                        "aas_category", "Grocery",
                        "aas_vendor", "Vendor A",
                        "aas_status", "VENDOR_PDF_RECEIVED",
                        "aas_po", "PO-0001"));
        when(catalogRoutingService.resolveVendorForCategory(eq("Vendor A"), eq("Grocery")))
                .thenReturn(new CatalogRoutingService.VendorCategoryResolution("Vendor A", "Vendor A", "VEND_A", "Grocery", "Grocery", "GROCERY"));
        mockNativeProfile();
        when(nativeLayoutInvoiceService.extract(any(), any()))
                .thenReturn(new NativeLayoutInvoiceService.ExtractionResult(
                        List.of(new ParsedItem("Tomatoes", 2, 45, 90, "11010000", 5.0, "KG", null)),
                        "INV-1",
                        "2026-02-19",
                        "90.00",
                        "",
                        "",
                        List.of(),
                        List.of()));
        when(erpNextClient.createResource(eq("Item"), any())).thenReturn(Map.of("name", "ITEM-001"));
        when(erpNextClient.getResource(eq("Purchase Order"), eq("PO-0001")))
                .thenReturn(Map.of("name", "PO-0001", "docstatus", 0));
        when(erpNextClient.updateResource(eq("Purchase Order"), eq("PO-0001"), any()))
                .thenReturn(Map.of("name", "PO-0001"));

        Map<String, Object> response = service.processVendorPdf("SO-0001", pdf, "sid=abc");

        assertNotNull(response.get("purchaseOrder"));
        verify(erpNextClient).updateResource(eq("Purchase Order"), eq("PO-0001"), any());
        verify(erpNextClient, never()).deleteResource(eq("Purchase Order"), eq("PO-0001"));
        verify(erpNextClient, never()).createResource(eq("Purchase Order"), any());
        verify(erpNextClient, atLeastOnce()).updateResource(eq("Sales Order"), eq("SO-0001"), any());
    }

    @Test
    void capsStoredMarginWhenMrpWouldBeExceeded() {
        MockMultipartFile pdf = validPdf();
        mockOrderContext();
        mockNativeProfile();
        when(nativeLayoutInvoiceService.extract(any(), any()))
                .thenReturn(new NativeLayoutInvoiceService.ExtractionResult(
                        List.of(new ParsedItem("Tomatoes", 1, 100, 100, "11010000", 5.0, "KG", 105.0)),
                        "INV-1",
                        "2026-02-19",
                        "100.00",
                        "",
                        "",
                        List.of(),
                        List.of()));
        when(erpNextClient.createResource(eq("Item"), any())).thenReturn(Map.of("name", "ITEM-001"));
        when(erpNextClient.createResource(eq("Purchase Order"), any())).thenReturn(Map.of("name", "PO-0001"));

        service.processVendorPdf("SO-0001", pdf, "sid=abc");

        ArgumentCaptor<Map<String, Object>> updateCaptor = ArgumentCaptor.forClass(Map.class);
        verify(erpNextClient, Mockito.atLeastOnce()).updateResource(eq("Sales Order"), eq("SO-0001"), updateCaptor.capture());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> updatedItems = (List<Map<String, Object>>) updateCaptor.getValue().get("items");
        assertEquals(5.0, updatedItems.get(0).get("aas_margin_percent"));
        assertEquals(105.0, updatedItems.get(0).get("aas_mrp"));
    }

    @Test
    void rejectsVendorPdfWhenVendorRateExceedsMrp() {
        MockMultipartFile pdf = validPdf();
        mockOrderContext();
        mockNativeProfile();
        when(nativeLayoutInvoiceService.extract(any(), any()))
                .thenReturn(new NativeLayoutInvoiceService.ExtractionResult(
                        List.of(new ParsedItem("Tomatoes", 1, 120, 120, "11010000", 5.0, "KG", 110.0)),
                        "INV-1",
                        "2026-02-19",
                        "120.00",
                        "",
                        "",
                        List.of(),
                        List.of()));
        when(erpNextClient.createResource(eq("Item"), any())).thenReturn(Map.of("name", "ITEM-001"));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.processVendorPdf("SO-0001", pdf, "sid=abc"));

        assertEquals("Vendor rate exceeds MRP for Tomatoes. Vendor rate=120.0, MRP=110.0.", ex.getMessage());
    }

    @Test
    void rejectsVendorPdfWhenStateIsNotVendorAssigned() {
        MockMultipartFile pdf = validPdf();
        when(erpNextClient.getResource(eq("Sales Order"), eq("SO-0001")))
                .thenReturn(Map.of(
                        "customer", "Sukarta Aundh",
                        "company", "AAS",
                        "aas_category", "Grocery",
                        "aas_vendor", "Vendor A",
                        "aas_status", "DRAFT"));

        assertThrows(IllegalStateException.class, () -> service.processVendorPdf("SO-0001", pdf, "sid=abc"));
    }

    @Test
    void rejectsVendorPdfWhenNoNativeProfileIsConfigured() {
        MockMultipartFile pdf = validPdf();
        mockOrderContext();
        when(templateResolver.loadTemplateJson(eq("Vendor A"))).thenReturn(java.util.Optional.empty());

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.processVendorPdf("SO-0001", pdf, "sid=abc"));

        assertEquals("Vendor native invoice mapping is required before uploading vendor PDF.", ex.getMessage());
        verify(nativeLayoutInvoiceService, never()).extract(any(), any());
    }

    @Test
    void rejectsVendorPdfWhenStoredTemplateJsonIsNotNativeLayout() {
        MockMultipartFile pdf = validPdf();
        mockOrderContext();
        when(templateResolver.loadTemplateJson(eq("Vendor A"))).thenReturn(java.util.Optional.of("""
                {"kind":"unsupported_template_kind","profile":{"id":"legacy"}}
                """));
        when(nativeLayoutInvoiceService.parseStoredProfile(any())).thenReturn(null);

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.processVendorPdf("SO-0001", pdf, "sid=abc"));

        assertEquals("Vendor native invoice mapping is required before uploading vendor PDF.", ex.getMessage());
        verify(nativeLayoutInvoiceService, never()).extract(any(), any());
    }

    @Test
    void surfacesNativeLayoutExecutionErrorsWithoutFallback() {
        MockMultipartFile pdf = validPdf();
        mockOrderContext();
        mockNativeProfile();
        when(nativeLayoutInvoiceService.extract(any(), any()))
                .thenThrow(new IllegalStateException("native layout extraction failed for profile vendor_a_native_layout: parser exploded"));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.processVendorPdf("SO-0001", pdf, "sid=abc"));

        assertEquals("native layout extraction failed for profile vendor_a_native_layout: parser exploded", ex.getMessage());
    }

    @Test
    void rejectsVendorPdfWhenNativeLayoutReturnsNoRows() {
        MockMultipartFile pdf = validPdf();
        mockOrderContext();
        mockNativeProfile();
        when(nativeLayoutInvoiceService.extract(any(), any()))
                .thenReturn(new NativeLayoutInvoiceService.ExtractionResult(
                        List.of(),
                        "INV-1",
                        "2026-02-19",
                        "100.00",
                        "",
                        "",
                        List.of(),
                        List.of()));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.processVendorPdf("SO-0001", pdf, "sid=abc"));

        assertEquals("Configured vendor template did not extract required item fields: item_name, item_id, qty, rate, gst, total.", ex.getMessage());
    }

    @Test
    void rejectsVendorPdfWhenNativeLayoutMissesRequiredSummaryFields() {
        MockMultipartFile pdf = validPdf();
        mockOrderContext();
        mockNativeProfile();
        when(nativeLayoutInvoiceService.extract(any(), any()))
                .thenReturn(new NativeLayoutInvoiceService.ExtractionResult(
                        List.of(new ParsedItem("Tomatoes", 2, 45, 90, "11010000", 5.0, "KG", null)),
                        "INV-1",
                        "2026-02-19",
                        "",
                        "",
                        "",
                        List.of(),
                        List.of()));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.processVendorPdf("SO-0001", pdf, "sid=abc"));

        assertEquals("Configured vendor template did not extract required summary fields: final_bill_amount.", ex.getMessage());
    }

    @Test
    void usesParsedItemCodeDuringVendorPdfProcessing() {
        MockMultipartFile pdf = validPdf();
        mockOrderContext();
        mockNativeProfile();
        when(nativeLayoutInvoiceService.extract(any(), any()))
                .thenReturn(new NativeLayoutInvoiceService.ExtractionResult(
                        List.of(new ParsedItem("Tomatoes", 2, 45, 90, "11010000", 5.0, "KG", null)),
                        "INV-1",
                        "2026-02-19",
                        "90.00",
                        "",
                        "",
                        List.of(),
                        List.of()));
        when(erpNextClient.getResource(eq("Item"), eq("VEND_A_GROCERY_11010000_TOMATOES")))
                .thenReturn(Map.of(
                        "name", "VEND_A_GROCERY_11010000_TOMATOES",
                        "item_code", "VEND_A_GROCERY_11010000_TOMATOES",
                        "disabled", 0,
                        "aas_margin_percent", 12.5));
        when(erpNextClient.createResource(eq("Purchase Order"), any())).thenReturn(Map.of("name", "PO-0001"));

        service.processVendorPdf("SO-0001", pdf, "sid=abc");

        verify(erpNextClient, never()).createResource(eq("Item"), any());
        ArgumentCaptor<Map<String, Object>> updateCaptor = ArgumentCaptor.forClass(Map.class);
        verify(erpNextClient, Mockito.atLeastOnce()).updateResource(eq("Sales Order"), eq("SO-0001"), updateCaptor.capture());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> updatedItems = (List<Map<String, Object>>) updateCaptor.getValue().get("items");
        assertEquals("VEND_A_GROCERY_11010000_TOMATOES", updatedItems.get(0).get("item_code"));
        assertEquals(12.5, updatedItems.get(0).get("aas_margin_percent"));
    }

    @Test
    void marksNoSpaceInvoiceSerialAsMissingWhenParserSkipsThatRow() {
        MockMultipartFile pdf = validPdf();
        mockOrderContext();
        mockNativeProfile();
        String parserText = """
                109, Market Yard, Pune 411037
                200ML BOTTLES
                500GM PACKS
                65LIJJAT PAPAD SMALL 3,400.00 KG340.00 340.00 0 10.00 KG0 % 19059040
                66LIJJAT PAPAD BIG 1,700.00 KG340.00 340.00 0 5.00 KG0 % 19059040
                67KHAJUR SEEDLESS 1,523.80 KG152.38 160.00 0 10.00 KG5 %
                68KAJUKANI BHARI 2,999.99 KG428.57 450.00 0 7.00 KG5 % 08013210
                """;
        List<String> rawTableLines = List.of(
                "65LIJJAT PAPAD SMALL 3,400.00 KG340.00 340.00 0 10.00 KG0 % 19059040",
                "66LIJJAT PAPAD BIG 1,700.00 KG340.00 340.00 0 5.00 KG0 % 19059040",
                "67KHAJUR SEEDLESS 1,523.80 KG152.38 160.00 0 10.00 KG5 %",
                "68KAJUKANI BHARI 2,999.99 KG428.57 450.00 0 7.00 KG5 % 08013210");
        when(nativeLayoutInvoiceService.extract(any(), any()))
                .thenReturn(new NativeLayoutInvoiceService.ExtractionResult(
                        List.of(
                                new ParsedItem("LIJJAT PAPAD SMALL", 10, 340, 3400, "19059040", 0.0, "KG", null),
                                new ParsedItem("LIJJAT PAPAD BIG", 5, 340, 1700, "19059040", 0.0, "KG", null),
                                new ParsedItem("KAJUKANI BHARI", 7, 428.57, 2999.99, "08013210", 5.0, "KG", null)),
                        "INV-1",
                        "2026-02-19",
                        "8099.99",
                        "",
                        parserText,
                        rawTableLines,
                        java.util.stream.IntStream.concat(
                                        java.util.stream.IntStream.rangeClosed(1, 66),
                                        java.util.stream.IntStream.of(68))
                                .boxed()
                                .toList()));
        when(erpNextClient.createResource(eq("Item"), any())).thenReturn(Map.of("name", "ITEM-001"));
        when(erpNextClient.createResource(eq("Purchase Order"), any())).thenReturn(Map.of("name", "PO-0001"));

        Map<String, Object> response = service.processVendorPdf("SO-0001", pdf, "sid=abc");

        @SuppressWarnings("unchecked")
        Map<String, Object> completeness = (Map<String, Object>) response.get("completeness");
        assertNotNull(completeness);
        assertEquals(List.of(67), completeness.get("missingSerials"));
    }

    @Test
    void fallsBackToExtractedSerialRangeWhenRawExpectedSequenceCollapsesToOne() {
        MockMultipartFile pdf = validPdf();
        mockOrderContext();
        mockNativeProfile();
        String parserText = """
                109, Market Yard, Pune 411037
                200ML BOTTLES
                500GM PACKS
                1 FIRST ITEM
                """;
        when(nativeLayoutInvoiceService.extract(any(), any()))
                .thenReturn(new NativeLayoutInvoiceService.ExtractionResult(
                        List.of(
                                new ParsedItem("FIRST ITEM", 1, 10, 10, "11010000", 5.0, "KG", null),
                                new ParsedItem("SECOND ITEM", 1, 10, 10, "11010000", 5.0, "KG", null)),
                        "INV-1",
                        "2026-02-19",
                        "20.00",
                        "",
                        parserText,
                        List.of("1 FIRST ITEM"),
                        List.of(1, 2, 4)));
        when(erpNextClient.createResource(eq("Item"), any())).thenReturn(Map.of("name", "ITEM-001"));
        when(erpNextClient.createResource(eq("Purchase Order"), any())).thenReturn(Map.of("name", "PO-0001"));

        Map<String, Object> response = service.processVendorPdf("SO-0001", pdf, "sid=abc");

        @SuppressWarnings("unchecked")
        Map<String, Object> completeness = (Map<String, Object>) response.get("completeness");
        assertNotNull(completeness);
        assertEquals(List.of(1, 2, 3, 4), completeness.get("expectedSerials"));
        assertEquals(List.of(3), completeness.get("missingSerials"));
    }

    @Test
    void prefersExtractedSerialRangeWhenRawExpectedRangeLooksInflated() {
        MockMultipartFile pdf = validPdf();
        mockOrderContext();
        mockNativeProfile();
        List<String> rawTableLines = new ArrayList<>();
        for (int serial = 1; serial <= 427; serial++) {
            rawTableLines.add(serial + " RAW ITEM " + serial + ".00");
        }
        List<Integer> extractedSerials = java.util.stream.IntStream.rangeClosed(1, 50)
                .filter(value -> value != 39)
                .boxed()
                .toList();
        when(nativeLayoutInvoiceService.extract(any(), any()))
                .thenReturn(new NativeLayoutInvoiceService.ExtractionResult(
                        List.of(
                                new ParsedItem("ITEM 38", 1, 10, 10, "11010000", 5.0, "KG", null),
                                new ParsedItem("ITEM 40", 1, 10, 10, "11010000", 5.0, "KG", null),
                                new ParsedItem("ITEM 50", 1, 10, 10, "11010000", 5.0, "KG", null)),
                        "INV-1",
                        "2026-02-19",
                        "30.00",
                        "",
                        "",
                        rawTableLines,
                        extractedSerials));
        when(erpNextClient.createResource(eq("Item"), any())).thenReturn(Map.of("name", "ITEM-001"));
        when(erpNextClient.createResource(eq("Purchase Order"), any())).thenReturn(Map.of("name", "PO-0001"));

        Map<String, Object> response = service.processVendorPdf("SO-0001", pdf, "sid=abc");

        @SuppressWarnings("unchecked")
        Map<String, Object> completeness = (Map<String, Object>) response.get("completeness");
        assertNotNull(completeness);
        assertEquals(50, ((List<?>) completeness.get("expectedSerials")).size());
        assertEquals(List.of(39), completeness.get("missingSerials"));
    }

    private void mockOrderContext() {
        when(erpNextClient.getResource(eq("Sales Order"), eq("SO-0001")))
                .thenReturn(Map.of(
                        "customer", "Sukarta Aundh",
                        "company", "AAS",
                        "aas_category", "Grocery",
                        "aas_vendor", "Vendor A",
                        "aas_status", "VENDOR_ASSIGNED"));
        when(catalogRoutingService.resolveVendorForCategory(eq("Vendor A"), eq("Grocery")))
                .thenReturn(new CatalogRoutingService.VendorCategoryResolution("Vendor A", "Vendor A", "VEND_A", "Grocery", "Grocery", "GROCERY"));
    }

    private void mockNativeProfile() {
        when(templateResolver.loadTemplateJson(eq("Vendor A"))).thenReturn(java.util.Optional.of(NATIVE_PROFILE_JSON));
        when(nativeLayoutInvoiceService.parseStoredProfile(any()))
                .thenReturn(new NativeLayoutInvoiceService.StoredProfile(
                        "vendor_a_native_layout",
                        "Vendor A native layout",
                        "Vendor A",
                        "Native PDF layout extraction with LLM field mapping.",
                        List.of(),
                        List.of(),
                        "",
                        "native_layout",
                        "",
                        "",
                        "",
                        Map.of(),
                        Map.of(),
                        Map.of(),
                        List.of(),
                        List.of()));
    }

    private MockMultipartFile validPdf() {
        try {
            byte[] bytes = Files.readAllBytes(Path.of("../images/vendor_order.pdf"));
            return new MockMultipartFile("file", "vendor_order.pdf", "application/pdf", bytes);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to load test PDF fixture.", ex);
        }
    }
}
