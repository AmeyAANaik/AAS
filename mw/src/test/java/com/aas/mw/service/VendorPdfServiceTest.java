package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.dto.ParsedItem;
import com.aas.mw.dto.UploadedFileInfo;
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

    private ErpNextClient erpNextClient;
    private ErpNextFileService fileService;
    private OcrService ocrService;
    private VendorPdfParser parser;
    private VendorInvoiceTemplateResolver templateResolver;
    private VendorInvoiceTemplateCatalog templateCatalog;
    private VendorInvoiceTemplateParser templateParser;
    private OrderFlowStateMachine orderFlowStateMachine;
    private VendorPdfService service;

    @BeforeEach
    void setup() {
        erpNextClient = mock(ErpNextClient.class);
        fileService = mock(ErpNextFileService.class);
        ocrService = mock(OcrService.class);
        parser = mock(VendorPdfParser.class);
        templateResolver = mock(VendorInvoiceTemplateResolver.class);
        templateCatalog = new VendorInvoiceTemplateCatalog();
        templateParser = mock(VendorInvoiceTemplateParser.class);
        orderFlowStateMachine = new OrderFlowStateMachine();
        service = new VendorPdfService(
                erpNextClient,
                fileService,
                ocrService,
                parser,
                templateResolver,
                templateCatalog,
                templateParser,
                orderFlowStateMachine,
                10.0);
    }

    @Test
    void processesVendorPdfAndCreatesDocs() {
        MockMultipartFile pdf = new MockMultipartFile("file", "vendor_order.pdf", "application/pdf", new byte[]{1, 2});
        when(erpNextClient.getResource(eq("Sales Order"), eq("SO-0001")))
                .thenReturn(Map.of(
                        "customer", "Shop A",
                        "company", "AAS Core",
                        "aas_vendor", "Vendor A",
                        "aas_status", "VENDOR_ASSIGNED"));
        when(templateResolver.loadTemplateKey(eq("Vendor A"))).thenReturn(java.util.Optional.empty());
        when(fileService.uploadOrderPdf(eq("SO-0001"), eq(pdf), any()))
                .thenReturn(new UploadedFileInfo("vendor_order.pdf", "/files/vendor_order.pdf", "FILE-0001"));
        when(ocrService.extractTextFromPdf(any())).thenReturn("Tomatoes 2 45 90");
        when(parser.parseItems(any())).thenReturn(List.of(new ParsedItem("Tomatoes", 2, 45, 90)));
        when(erpNextClient.listResources(eq("Item"), any())).thenReturn(List.of());
        when(erpNextClient.createResource(eq("Item"), any())).thenReturn(Map.of("name", "ITEM-001"));
        when(erpNextClient.createResource(eq("Purchase Order"), any())).thenReturn(Map.of("name", "PO-0001"));

        Map<String, Object> response = service.processVendorPdf("SO-0001", pdf, "sid=abc");

        assertNotNull(response.get("purchaseOrder"));
        assertNotNull(response.get("sellPreview"));
        assertEquals(10.0, response.get("marginPercent"));
        assertEquals(java.util.Map.of("configured", false, "used", false, "key", ""), response.get("template"));

        ArgumentCaptor<Map<String, Object>> updateCaptor = ArgumentCaptor.forClass(Map.class);
        Mockito.verify(erpNextClient, Mockito.atLeastOnce())
                .updateResource(eq("Sales Order"), eq("SO-0001"), updateCaptor.capture());
        assertEquals("VENDOR_PDF_RECEIVED", updateCaptor.getValue().get("aas_status"));
    }

    @Test
    void prefersTemplateParserWhenConfiguredAndProducesItems() {
        MockMultipartFile pdf = new MockMultipartFile("file", "vendor_order.pdf", "application/pdf", new byte[]{1, 2});
        when(erpNextClient.getResource(eq("Sales Order"), eq("SO-0001")))
                .thenReturn(Map.of(
                        "customer", "Shop A",
                        "company", "AAS Core",
                        "aas_vendor", "Vendor A",
                        "aas_status", "VENDOR_ASSIGNED"));
        when(templateResolver.loadTemplateKey(eq("Vendor A"))).thenReturn(java.util.Optional.of("table_v1"));
        when(ocrService.extractTextFromPdf(any())).thenReturn("Tomatoes 2 45 90");
        when(templateParser.parseItems(any(), any())).thenReturn(List.of(new ParsedItem("Tomatoes", 2, 45, 90)));
        when(erpNextClient.listResources(eq("Item"), any())).thenReturn(List.of());
        when(erpNextClient.createResource(eq("Item"), any())).thenReturn(Map.of("name", "ITEM-001"));
        when(erpNextClient.createResource(eq("Purchase Order"), any())).thenReturn(Map.of("name", "PO-0001"));

        Map<String, Object> response = service.processVendorPdf("SO-0001", pdf, "sid=abc");

        Mockito.verify(parser, Mockito.never()).parseItems(any());
        assertEquals(java.util.Map.of("configured", true, "used", true, "key", "table_v1"), response.get("template"));
    }

    @Test
    void rejectsVendorPdfWhenStateIsNotVendorAssigned() {
        MockMultipartFile pdf = new MockMultipartFile("file", "vendor_order.pdf", "application/pdf", new byte[]{1, 2});
        when(erpNextClient.getResource(eq("Sales Order"), eq("SO-0001")))
                .thenReturn(Map.of(
                        "customer", "Shop A",
                        "company", "AAS Core",
                        "aas_vendor", "Vendor A",
                        "aas_status", "DRAFT"));

        assertThrows(IllegalStateException.class, () -> service.processVendorPdf("SO-0001", pdf, "sid=abc"));
    }
}
