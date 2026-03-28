package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.config.InvoiceTemplateModelProperties;
import com.aas.mw.dto.ParsedItem;
import com.aas.mw.dto.UploadedFileInfo;
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
    private CatalogRoutingService catalogRoutingService;
    private VendorPdfService service;

    @BeforeEach
    void setup() {
        erpNextClient = mock(ErpNextClient.class);
        fileService = mock(ErpNextFileService.class);
        templateResolver = mock(VendorInvoiceTemplateResolver.class);
        nativeLayoutInvoiceService = mock(NativeLayoutInvoiceService.class);
        orderFlowStateMachine = new OrderFlowStateMachine();
        catalogRoutingService = mock(CatalogRoutingService.class);
        when(catalogRoutingService.normalizeCodeSegment(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(fileService.uploadOrderPdf(any(), any(), any()))
                .thenReturn(new UploadedFileInfo("vendor_order.pdf", "/files/vendor_order.pdf", "FILE-0001"));
        service = new VendorPdfService(
                erpNextClient,
                fileService,
                templateResolver,
                nativeLayoutInvoiceService,
                new InvoiceTemplateModelService(new InvoiceTemplateModelProperties()),
                orderFlowStateMachine,
                catalogRoutingService,
                new OrderPricingService(),
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
