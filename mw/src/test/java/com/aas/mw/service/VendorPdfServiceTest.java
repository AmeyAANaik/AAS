package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.config.InvoiceTemplateModelProperties;
import com.aas.mw.dto.ParsedItem;
import com.aas.mw.dto.UploadedFileInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VendorPdfServiceTest {

    private static final String STRICT_TEMPLATE_JSON = """
            {
              "parser": {
                "version": 1,
                "itemLineRegex": "^(?<name>.+?)\\\\s+(?<hsn>\\\\d{4,10})\\\\s+(?<qty>\\\\d+(?:\\\\.\\\\d+)?)\\\\s+(?<uom>[A-Za-z]{1,6})\\\\s+(?<rate>\\\\d+(?:\\\\.\\\\d+)?)\\\\s+(?<gst>\\\\d+(?:\\\\.\\\\d+)?)\\\\s+(?<amount>\\\\d+(?:\\\\.\\\\d+)?)$",
                "finalAmountRegex": "(?im)^total\\\\s+(?<amount>\\\\d+(?:,\\\\d{3})*(?:\\\\.\\\\d+)?)$"
              }
            }
            """;

    private ErpNextClient erpNextClient;
    private ErpNextFileService fileService;
    private OcrService ocrService;
    private VendorInvoiceTemplateResolver templateResolver;
    private VendorInvoiceTemplateCatalog templateCatalog;
    private VendorInvoiceTemplateParser templateParser;
    private Invoice2DataExtractionService invoice2DataExtractionService;
    private OrderFlowStateMachine orderFlowStateMachine;
    private CatalogRoutingService catalogRoutingService;
    private VendorPdfService service;

    @BeforeEach
    void setup() {
        erpNextClient = mock(ErpNextClient.class);
        fileService = mock(ErpNextFileService.class);
        ocrService = mock(OcrService.class);
        templateResolver = mock(VendorInvoiceTemplateResolver.class);
        templateCatalog = new VendorInvoiceTemplateCatalog();
        templateParser = mock(VendorInvoiceTemplateParser.class);
        invoice2DataExtractionService = mock(Invoice2DataExtractionService.class);
        orderFlowStateMachine = new OrderFlowStateMachine();
        catalogRoutingService = mock(CatalogRoutingService.class);
        when(catalogRoutingService.normalizeCodeSegment(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(fileService.uploadOrderPdf(any(), any(), any()))
                .thenReturn(new UploadedFileInfo("vendor_order.pdf", "/files/vendor_order.pdf", "FILE-0001"));
        service = new VendorPdfService(
                erpNextClient,
                fileService,
                ocrService,
                templateResolver,
                templateCatalog,
                templateParser,
                invoice2DataExtractionService,
                new InvoiceTemplateModelService(new InvoiceTemplateModelProperties()),
                orderFlowStateMachine,
                catalogRoutingService,
                new OrderPricingService(),
                new ObjectMapper(),
                7.0);
    }

    @Test
    void processesVendorPdfAndCreatesDocs() {
        MockMultipartFile pdf = validPdf();
        mockStrictOrderContext();
        when(ocrService.extractTextFromPdf(any())).thenReturn("""
                Tomatoes 11010000 2 KG 45 5 90
                Total 90.00
                """);
        when(templateParser.parseItems(any(), any()))
                .thenReturn(List.of(new ParsedItem("Tomatoes", 2, 45, 90, "11010000", 5.0, "KG", null)));
        when(erpNextClient.createResource(eq("Item"), any())).thenReturn(Map.of("name", "ITEM-001"));
        when(erpNextClient.createResource(eq("Purchase Order"), any())).thenReturn(Map.of("name", "PO-0001"));

        Map<String, Object> response = service.processVendorPdf("SO-0001", pdf, "sid=abc");

        assertNotNull(response.get("purchaseOrder"));
        assertNotNull(response.get("sellPreview"));
        assertEquals(7.0, response.get("marginPercent"));
        assertEquals(0.0, response.get("transportCharge"));
        assertEquals(java.util.Map.of("configured", true, "used", true, "key", "vendor_json"), response.get("template"));

        ArgumentCaptor<Map<String, Object>> updateCaptor = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(erpNextClient, Mockito.atLeastOnce())
                .updateResource(eq("Sales Order"), eq("SO-0001"), updateCaptor.capture());
        assertEquals("VENDOR_PDF_RECEIVED", updateCaptor.getValue().get("aas_status"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> updatedItems = (List<Map<String, Object>>) updateCaptor.getValue().get("items");
        assertEquals(7.0, updatedItems.get(0).get("aas_margin_percent"));
        assertEquals("KG", updatedItems.get(0).get("uom"));
    }

    @Test
    void storesGstPercentAndUomFromParsedVendorItems() {
        MockMultipartFile pdf = validPdf();
        mockStrictOrderContext();
        when(ocrService.extractTextFromPdf(any())).thenReturn("""
                EVEREST KASHMIRI CHILLY POWDER 09109100 6 KG 447.62 5 2685.72
                Total 2685.72
                """);
        when(templateParser.parseItems(any(), any()))
                .thenReturn(List.of(new ParsedItem("EVEREST KASHMIRI CHILLY POWDER", 6, 447.62, 2685.72, "09109100", 5.0, "KG", 530.0)));
        when(erpNextClient.createResource(eq("Item"), any())).thenReturn(Map.of("name", "ITEM-001"));
        when(erpNextClient.createResource(eq("Purchase Order"), any())).thenReturn(Map.of("name", "PO-0001"));

        Map<String, Object> response = service.processVendorPdf("SO-0001", pdf, "sid=abc");

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> orderItems = (List<Map<String, Object>>) response.get("orderItems");
        assertEquals(5.0, orderItems.get(0).get("aas_gst_percent"));
        assertEquals("KG", orderItems.get(0).get("uom"));
    }

    @Test
    void capturesTransportChargeFromVendorPdfText() {
        MockMultipartFile pdf = validPdf();
        mockStrictOrderContext("""
                {
                  "parser": {
                    "version": 1,
                    "itemLineRegex": "^(?<name>.+?)\\\\s+(?<hsn>\\\\d{4,10})\\\\s+(?<qty>\\\\d+(?:\\\\.\\\\d+)?)\\\\s+(?<uom>[A-Za-z]{1,6})\\\\s+(?<rate>\\\\d+(?:\\\\.\\\\d+)?)\\\\s+(?<gst>\\\\d+(?:\\\\.\\\\d+)?)\\\\s+(?<amount>\\\\d+(?:\\\\.\\\\d+)?)$",
                    "finalAmountRegex": "(?im)^total\\\\s+(?<amount>\\\\d+(?:,\\\\d{3})*(?:\\\\.\\\\d+)?)$",
                    "transportChargeRegex": "(?im)^transport\\\\s+(?<amount>\\\\d+(?:,\\\\d{3})*(?:\\\\.\\\\d+)?)$"
                  }
                }
                """);
        when(ocrService.extractTextFromPdf(any())).thenReturn("""
                Tomatoes 11010000 2 KG 45 5 90
                Transport 50.00
                Total 140.00
                """);
        when(templateParser.parseItems(any(), any()))
                .thenReturn(List.of(new ParsedItem("Tomatoes", 2, 45, 90, "11010000", 5.0, "KG", null)));
        when(erpNextClient.createResource(eq("Item"), any())).thenReturn(Map.of("name", "ITEM-001"));
        when(erpNextClient.createResource(eq("Purchase Order"), any())).thenReturn(Map.of("name", "PO-0001"));

        Map<String, Object> response = service.processVendorPdf("SO-0001", pdf, "sid=abc");

        assertEquals(50.0, response.get("transportCharge"));
    }

    @Test
    void prefersBuiltInTemplateKeyWhenConfigured() {
        MockMultipartFile pdf = validPdf();
        when(erpNextClient.getResource(eq("Sales Order"), eq("SO-0001")))
                .thenReturn(Map.of(
                        "customer", "Sukarta Aundh",
                        "company", "AAS",
                        "aas_category", "Grocery",
                        "aas_vendor", "Vendor A",
                        "aas_status", "VENDOR_ASSIGNED"));
        when(catalogRoutingService.resolveVendorForCategory(eq("Vendor A"), eq("Grocery")))
                .thenReturn(new CatalogRoutingService.VendorCategoryResolution("Vendor A", "Vendor A", "VEND_A", "Grocery", "Grocery", "GROCERY"));
        when(templateResolver.loadTemplateJson(eq("Vendor A"))).thenReturn(java.util.Optional.empty());
        when(templateResolver.loadTemplateKey(eq("Vendor A"))).thenReturn(java.util.Optional.of("table_v1"));
        when(ocrService.extractTextFromPdf(any())).thenReturn("""
                Tomatoes 11010000 2 KG 45 5 90
                Total 90.00
                """);
        when(templateParser.parseItems(any(), any()))
                .thenReturn(List.of(new ParsedItem("Tomatoes", 2, 45, 90, "11010000", 5.0, "KG", null)));
        when(erpNextClient.createResource(eq("Item"), any())).thenReturn(Map.of("name", "ITEM-001"));
        when(erpNextClient.createResource(eq("Purchase Order"), any())).thenReturn(Map.of("name", "PO-0001"));

        Map<String, Object> response = service.processVendorPdf("SO-0001", pdf, "sid=abc");

        assertEquals(java.util.Map.of("configured", true, "used", true, "key", "table_v1"), response.get("template"));
    }

    @Test
    void fallsBackToDefaultMarginWhenOrderMarginIsZero() {
        MockMultipartFile pdf = validPdf();
        when(erpNextClient.getResource(eq("Sales Order"), eq("SO-0001")))
                .thenReturn(Map.of(
                        "customer", "Sukarta Aundh",
                        "company", "AAS",
                        "aas_category", "Grocery",
                        "aas_vendor", "Vendor A",
                        "aas_status", "VENDOR_ASSIGNED",
                        "aas_margin_percent", 0.0));
        when(catalogRoutingService.resolveVendorForCategory(eq("Vendor A"), eq("Grocery")))
                .thenReturn(new CatalogRoutingService.VendorCategoryResolution("Vendor A", "Vendor A", "VEND_A", "Grocery", "Grocery", "GROCERY"));
        when(templateResolver.loadTemplateJson(eq("Vendor A"))).thenReturn(java.util.Optional.of(STRICT_TEMPLATE_JSON));
        when(ocrService.extractTextFromPdf(any())).thenReturn("""
                Tomatoes 11010000 2 KG 45 5 90
                Total 90.00
                """);
        when(templateParser.parseItems(any(), any()))
                .thenReturn(List.of(new ParsedItem("Tomatoes", 2, 45, 90, "11010000", 5.0, "KG", null)));
        when(erpNextClient.createResource(eq("Item"), any())).thenReturn(Map.of("name", "ITEM-001"));
        when(erpNextClient.createResource(eq("Purchase Order"), any())).thenReturn(Map.of("name", "PO-0001"));

        Map<String, Object> response = service.processVendorPdf("SO-0001", pdf, "sid=abc");

        assertEquals(7.0, response.get("marginPercent"));
    }

    @Test
    void capsStoredMarginWhenTemplateMrpWouldBeExceeded() {
        MockMultipartFile pdf = validPdf();
        mockStrictOrderContext();
        when(ocrService.extractTextFromPdf(any())).thenReturn("""
                Tomatoes 11010000 1 KG 100 5 100
                Total 100.00
                """);
        when(templateParser.parseItems(any(), any()))
                .thenReturn(List.of(new ParsedItem("Tomatoes", 1, 100, 100, "11010000", 5.0, "KG", 105.0)));
        when(erpNextClient.createResource(eq("Item"), any())).thenReturn(Map.of("name", "ITEM-001"));
        when(erpNextClient.createResource(eq("Purchase Order"), any())).thenReturn(Map.of("name", "PO-0001"));

        service.processVendorPdf("SO-0001", pdf, "sid=abc");

        ArgumentCaptor<Map<String, Object>> updateCaptor = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(erpNextClient, Mockito.atLeastOnce())
                .updateResource(eq("Sales Order"), eq("SO-0001"), updateCaptor.capture());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> updatedItems = (List<Map<String, Object>>) updateCaptor.getValue().get("items");
        assertEquals(5.0, updatedItems.get(0).get("aas_margin_percent"));
        assertEquals(105.0, updatedItems.get(0).get("aas_mrp"));
    }

    @Test
    void rejectsVendorPdfWhenVendorRateExceedsMrp() {
        MockMultipartFile pdf = validPdf();
        mockStrictOrderContext();
        when(ocrService.extractTextFromPdf(any())).thenReturn("""
                Tomatoes 11010000 1 KG 120 5 120
                Total 120.00
                """);
        when(templateParser.parseItems(any(), any()))
                .thenReturn(List.of(new ParsedItem("Tomatoes", 1, 120, 120, "11010000", 5.0, "KG", 110.0)));
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
    void rejectsVendorPdfWhenNoTemplateIsConfigured() {
        MockMultipartFile pdf = validPdf();
        when(erpNextClient.getResource(eq("Sales Order"), eq("SO-0001")))
                .thenReturn(Map.of(
                        "customer", "Sukarta Aundh",
                        "company", "AAS",
                        "aas_category", "Grocery",
                        "aas_vendor", "Vendor A",
                        "aas_status", "VENDOR_ASSIGNED"));
        when(catalogRoutingService.resolveVendorForCategory(eq("Vendor A"), eq("Grocery")))
                .thenReturn(new CatalogRoutingService.VendorCategoryResolution("Vendor A", "Vendor A", "VEND_A", "Grocery", "Grocery", "GROCERY"));
        when(templateResolver.loadTemplateJson(eq("Vendor A"))).thenReturn(java.util.Optional.empty());
        when(templateResolver.loadTemplateKey(eq("Vendor A"))).thenReturn(java.util.Optional.empty());
        when(ocrService.extractTextFromPdf(any())).thenReturn("Tomatoes 11010000 1 KG 120 5 120");

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.processVendorPdf("SO-0001", pdf, "sid=abc"));

        assertEquals(
                "Vendor invoice template is required before uploading vendor PDF. Configure and validate the vendor template first.",
                ex.getMessage());
    }

    private void mockStrictOrderContext() {
        mockStrictOrderContext(STRICT_TEMPLATE_JSON);
    }

    private void mockStrictOrderContext(String templateJson) {
        when(erpNextClient.getResource(eq("Sales Order"), eq("SO-0001")))
                .thenReturn(Map.of(
                        "customer", "Sukarta Aundh",
                        "company", "AAS",
                        "aas_category", "Grocery",
                        "aas_vendor", "Vendor A",
                        "aas_status", "VENDOR_ASSIGNED"));
        when(catalogRoutingService.resolveVendorForCategory(eq("Vendor A"), eq("Grocery")))
                .thenReturn(new CatalogRoutingService.VendorCategoryResolution("Vendor A", "Vendor A", "VEND_A", "Grocery", "Grocery", "GROCERY"));
        when(templateResolver.loadTemplateJson(eq("Vendor A"))).thenReturn(java.util.Optional.of(templateJson));
        when(templateResolver.loadTemplateKey(eq("Vendor A"))).thenReturn(java.util.Optional.empty());
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
