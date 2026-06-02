package com.aas.mw.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.dto.UploadedFileInfo;
import java.io.ByteArrayOutputStream;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

public class VendorPdfServiceReuploadTests {

    @Test
    void reuploadBlockedWhenVendorInvoiceSubmitted() throws Exception {
        ErpNextClient erpNextClient = mock(ErpNextClient.class);
        ErpNextFileService fileService = mock(ErpNextFileService.class);
        VendorInvoiceTemplateResolver templateResolver = mock(VendorInvoiceTemplateResolver.class);
        NativeLayoutInvoiceService nativeLayoutInvoiceService = mock(NativeLayoutInvoiceService.class);
        InvoiceTemplateModelService invoiceTemplateModelService = mock(InvoiceTemplateModelService.class);
        OrderFlowStateMachine orderFlowStateMachine = new OrderFlowStateMachine();
        OrderBillingService orderBillingService = mock(OrderBillingService.class);
        CatalogRoutingService catalogRoutingService = mock(CatalogRoutingService.class);
        OrderPricingService orderPricingService = mock(OrderPricingService.class);
        UomService uomService = mock(UomService.class);

        VendorPdfService vendorPdfService = new VendorPdfService(
                erpNextClient,
                fileService,
                templateResolver,
                nativeLayoutInvoiceService,
                invoiceTemplateModelService,
                orderFlowStateMachine,
                orderBillingService,
                catalogRoutingService,
                orderPricingService,
                uomService,
                7.0);

        String orderId = "SO-1";
        when(erpNextClient.getResource(eq("Sales Order"), eq(orderId))).thenReturn(Map.of(
                "data", Map.of(
                        "customer", "CUST-1",
                        "company", "COMP-1",
                        "aas_vendor", "SUP-1",
                        "aas_category", "CAT-1",
                        "aas_status", "VENDOR_BILL_CAPTURED",
                        "aas_pi_vendor", "PI-OLD")));
        when(erpNextClient.getResource(eq("Purchase Invoice"), eq("PI-OLD"))).thenReturn(Map.of(
                "data", Map.of("docstatus", 1)));

        MockMultipartFile pdfFile = new MockMultipartFile("file", "invoice.pdf", "application/pdf", minimalPdfBytes());

        assertThrows(IllegalStateException.class, () -> vendorPdfService.processVendorPdf(orderId, pdfFile, "sid=123"));
    }

    @Test
    void reuploadReplacesDraftVendorInvoiceAndMarksOld() throws Exception {
        ErpNextClient erpNextClient = mock(ErpNextClient.class);
        ErpNextFileService fileService = mock(ErpNextFileService.class);
        VendorInvoiceTemplateResolver templateResolver = mock(VendorInvoiceTemplateResolver.class);
        NativeLayoutInvoiceService nativeLayoutInvoiceService = mock(NativeLayoutInvoiceService.class);
        InvoiceTemplateModelService invoiceTemplateModelService = mock(InvoiceTemplateModelService.class);
        OrderFlowStateMachine orderFlowStateMachine = new OrderFlowStateMachine();
        OrderBillingService orderBillingService = mock(OrderBillingService.class);
        CatalogRoutingService catalogRoutingService = mock(CatalogRoutingService.class);
        OrderPricingService orderPricingService = mock(OrderPricingService.class);
        UomService uomService = mock(UomService.class);

        VendorPdfService vendorPdfService = new VendorPdfService(
                erpNextClient,
                fileService,
                templateResolver,
                nativeLayoutInvoiceService,
                invoiceTemplateModelService,
                orderFlowStateMachine,
                orderBillingService,
                catalogRoutingService,
                orderPricingService,
                uomService,
                7.0);

        String orderId = "SO-1";
        when(erpNextClient.getResource(eq("Sales Order"), eq(orderId))).thenReturn(Map.of(
                "data", Map.of(
                        "customer", "CUST-1",
                        "company", "COMP-1",
                        "aas_vendor", "SUP-1",
                        "aas_category", "CAT-1",
                        "aas_status", "VENDOR_BILL_CAPTURED",
                        "aas_pi_vendor", "PI-OLD",
                        "aas_po", "")));
        when(erpNextClient.getResource(eq("Purchase Invoice"), eq("PI-OLD"))).thenReturn(Map.of(
                "data", Map.of("docstatus", 0)));

        when(catalogRoutingService.resolveVendorForCategory(eq("SUP-1"), eq("CAT-1"))).thenReturn(
                new CatalogRoutingService.VendorCategoryResolution("SUP-1", "Vendor", "V", "CAT-1", "Cat", "C"));

        when(templateResolver.loadTemplateJson(eq("SUP-1"))).thenReturn(Optional.of("{}"));
        when(nativeLayoutInvoiceService.parseStoredProfile(any())).thenReturn(new NativeLayoutInvoiceService.StoredProfile(
                "p",
                "label",
                "vendor",
                "desc",
                List.of(),
                List.of(),
                "",
                "",
                "",
                "",
                "",
                Map.of(),
                Map.of(),
                Map.of(),
                List.of(),
                List.of()));
        when(invoiceTemplateModelService.requiredItemKeys()).thenReturn(List.of());
        when(nativeLayoutInvoiceService.extract(any(byte[].class), any())).thenReturn(new NativeLayoutInvoiceService.ExtractionResult(
                List.of(),
                "",
                "2026-05-30",
                "100.00",
                "",
                "",
                List.of(),
                List.of()));

        when(erpNextClient.createResource(eq("Purchase Order"), any())).thenReturn(Map.of("name", "PO-1"));
        when(fileService.uploadOrderPdf(eq(orderId), any(), any())).thenReturn(new UploadedFileInfo("invoice.pdf", "/files/invoice.pdf", "FILE-1"));

        when(orderBillingService.recordGeneratedVendorBill(eq(orderId), any())).thenReturn(Map.of(
                "purchaseInvoice", Map.of("name", "PI-NEW")));

        MockMultipartFile pdfFile = new MockMultipartFile("file", "invoice.pdf", "application/pdf", minimalPdfBytes());
        vendorPdfService.processVendorPdf(orderId, pdfFile, "sid=123");

        verify(erpNextClient).updateResource(eq("Sales Order"), eq(orderId), eq(Map.of("aas_pi_vendor", "")));
        verify(erpNextClient).updateResource(eq("Purchase Invoice"), eq("PI-OLD"), eq(Map.of("aas_source_sales_order", "")));
        verify(erpNextClient).deleteResource(eq("Purchase Invoice"), eq("PI-OLD"));
        verify(orderBillingService, org.mockito.Mockito.never()).recordGeneratedVendorBill(eq(orderId), any());
    }

    @Test
    void vendorPdfReceivedUploadDoesNotDeleteInvoicesOrAutoGenerateBills() throws Exception {
        ErpNextClient erpNextClient = mock(ErpNextClient.class);
        ErpNextFileService fileService = mock(ErpNextFileService.class);
        VendorInvoiceTemplateResolver templateResolver = mock(VendorInvoiceTemplateResolver.class);
        NativeLayoutInvoiceService nativeLayoutInvoiceService = mock(NativeLayoutInvoiceService.class);
        InvoiceTemplateModelService invoiceTemplateModelService = mock(InvoiceTemplateModelService.class);
        OrderFlowStateMachine orderFlowStateMachine = new OrderFlowStateMachine();
        OrderBillingService orderBillingService = mock(OrderBillingService.class);
        CatalogRoutingService catalogRoutingService = mock(CatalogRoutingService.class);
        OrderPricingService orderPricingService = mock(OrderPricingService.class);
        UomService uomService = mock(UomService.class);

        VendorPdfService vendorPdfService = new VendorPdfService(
                erpNextClient,
                fileService,
                templateResolver,
                nativeLayoutInvoiceService,
                invoiceTemplateModelService,
                orderFlowStateMachine,
                orderBillingService,
                catalogRoutingService,
                orderPricingService,
                uomService,
                7.0);

        String orderId = "SO-1";
        when(erpNextClient.getResource(eq("Sales Order"), eq(orderId))).thenReturn(Map.of(
                "data", Map.of(
                        "customer", "CUST-1",
                        "company", "COMP-1",
                        "aas_vendor", "SUP-1",
                        "aas_category", "CAT-1",
                        "aas_status", "VENDOR_PDF_RECEIVED",
                        "aas_po", "")));

        when(catalogRoutingService.resolveVendorForCategory(eq("SUP-1"), eq("CAT-1"))).thenReturn(
                new CatalogRoutingService.VendorCategoryResolution("SUP-1", "Vendor", "V", "CAT-1", "Cat", "C"));

        when(templateResolver.loadTemplateJson(eq("SUP-1"))).thenReturn(Optional.of("{}"));
        when(nativeLayoutInvoiceService.parseStoredProfile(any())).thenReturn(new NativeLayoutInvoiceService.StoredProfile(
                "p",
                "label",
                "vendor",
                "desc",
                List.of(),
                List.of(),
                "",
                "",
                "",
                "",
                "",
                Map.of(),
                Map.of(),
                Map.of(),
                List.of(),
                List.of()));
        when(invoiceTemplateModelService.requiredItemKeys()).thenReturn(List.of());
        when(nativeLayoutInvoiceService.extract(any(byte[].class), any())).thenReturn(new NativeLayoutInvoiceService.ExtractionResult(
                List.of(),
                "",
                "2026-05-30",
                "100.00",
                "",
                "",
                List.of(),
                List.of()));

        when(erpNextClient.createResource(eq("Purchase Order"), any())).thenReturn(Map.of("name", "PO-1"));
        when(fileService.uploadOrderPdf(eq(orderId), any(), any())).thenReturn(new UploadedFileInfo("invoice.pdf", "/files/invoice.pdf", "FILE-1"));

        MockMultipartFile pdfFile = new MockMultipartFile("file", "invoice.pdf", "application/pdf", minimalPdfBytes());
        vendorPdfService.processVendorPdf(orderId, pdfFile, "sid=123");

        verify(erpNextClient, org.mockito.Mockito.never()).deleteResource(eq("Purchase Invoice"), any());
        verify(erpNextClient, org.mockito.Mockito.never()).deleteResource(eq("Sales Invoice"), any());
        verify(orderBillingService, org.mockito.Mockito.never()).recordGeneratedVendorBill(eq(orderId), any());
        verify(orderBillingService, org.mockito.Mockito.never()).createOrReplaceSellOrder(eq(orderId), any());
    }

    @Test
    void sellOrderCreatedReuploadRegeneratesSalesInvoiceAndPreservesTransportFlag() throws Exception {
        ErpNextClient erpNextClient = mock(ErpNextClient.class);
        ErpNextFileService fileService = mock(ErpNextFileService.class);
        VendorInvoiceTemplateResolver templateResolver = mock(VendorInvoiceTemplateResolver.class);
        NativeLayoutInvoiceService nativeLayoutInvoiceService = mock(NativeLayoutInvoiceService.class);
        InvoiceTemplateModelService invoiceTemplateModelService = mock(InvoiceTemplateModelService.class);
        OrderFlowStateMachine orderFlowStateMachine = new OrderFlowStateMachine();
        OrderBillingService orderBillingService = mock(OrderBillingService.class);
        CatalogRoutingService catalogRoutingService = mock(CatalogRoutingService.class);
        OrderPricingService orderPricingService = mock(OrderPricingService.class);
        UomService uomService = mock(UomService.class);

        VendorPdfService vendorPdfService = new VendorPdfService(
                erpNextClient,
                fileService,
                templateResolver,
                nativeLayoutInvoiceService,
                invoiceTemplateModelService,
                orderFlowStateMachine,
                orderBillingService,
                catalogRoutingService,
                orderPricingService,
                uomService,
                7.0);

        String orderId = "SO-1";
        when(erpNextClient.getResource(eq("Sales Order"), eq(orderId))).thenReturn(Map.of(
                "data", Map.of(
                        "customer", "CUST-1",
                        "company", "COMP-1",
                        "aas_vendor", "SUP-1",
                        "aas_category", "CAT-1",
                        "aas_status", "SELL_ORDER_CREATED",
                        "aas_pi_vendor", "PI-OLD",
                        "aas_si_branch", "SI-OLD",
                        "aas_po", "")));
        when(erpNextClient.getResource(eq("Purchase Invoice"), eq("PI-OLD"))).thenReturn(Map.of(
                "data", Map.of("docstatus", 0)));
        when(erpNextClient.getResource(eq("Sales Invoice"), eq("SI-OLD"))).thenReturn(Map.of(
                "data", Map.of(
                        "docstatus", 0,
                        "items", List.of(Map.of("item_code", "AAS-TRANSPORT-CHARGE")))));

        when(catalogRoutingService.resolveVendorForCategory(eq("SUP-1"), eq("CAT-1"))).thenReturn(
                new CatalogRoutingService.VendorCategoryResolution("SUP-1", "Vendor", "V", "CAT-1", "Cat", "C"));

        when(templateResolver.loadTemplateJson(eq("SUP-1"))).thenReturn(Optional.of("{}"));
        when(nativeLayoutInvoiceService.parseStoredProfile(any())).thenReturn(new NativeLayoutInvoiceService.StoredProfile(
                "p",
                "label",
                "vendor",
                "desc",
                List.of(),
                List.of(),
                "",
                "",
                "",
                "",
                "",
                Map.of(),
                Map.of(),
                Map.of(),
                List.of(),
                List.of()));
        when(invoiceTemplateModelService.requiredItemKeys()).thenReturn(List.of());
        when(nativeLayoutInvoiceService.extract(any(byte[].class), any())).thenReturn(new NativeLayoutInvoiceService.ExtractionResult(
                List.of(),
                "",
                "2026-05-30",
                "100.00",
                "",
                "",
                List.of(),
                List.of()));

        when(erpNextClient.createResource(eq("Purchase Order"), any())).thenReturn(Map.of("name", "PO-1"));
        when(fileService.uploadOrderPdf(eq(orderId), any(), any())).thenReturn(new UploadedFileInfo("invoice.pdf", "/files/invoice.pdf", "FILE-1"));

        when(orderBillingService.recordGeneratedVendorBill(eq(orderId), any())).thenReturn(Map.of(
                "purchaseInvoice", Map.of("name", "PI-NEW")));
        when(orderBillingService.createOrReplaceSellOrder(eq(orderId), any())).thenReturn(Map.of(
                "salesInvoice", Map.of("name", "SI-NEW")));

        MockMultipartFile pdfFile = new MockMultipartFile("file", "invoice.pdf", "application/pdf", minimalPdfBytes());
        vendorPdfService.processVendorPdf(orderId, pdfFile, "sid=123");

        verify(erpNextClient).updateResource(eq("Sales Order"), eq(orderId), eq(Map.of("aas_pi_vendor", "")));
        verify(erpNextClient).updateResource(eq("Purchase Invoice"), eq("PI-OLD"), eq(Map.of("aas_source_sales_order", "")));
        verify(erpNextClient).deleteResource(eq("Purchase Invoice"), eq("PI-OLD"));
        verify(erpNextClient).updateResource(eq("Sales Order"), eq(orderId), eq(Map.of("aas_si_branch", "")));
        verify(erpNextClient).updateResource(eq("Sales Invoice"), eq("SI-OLD"), eq(Map.of("aas_source_sales_order", "")));
        verify(erpNextClient).deleteResource(eq("Sales Invoice"), eq("SI-OLD"));
        verify(orderBillingService, org.mockito.Mockito.never()).recordGeneratedVendorBill(eq(orderId), any());
        verify(orderBillingService, org.mockito.Mockito.never()).createOrReplaceSellOrder(eq(orderId), any());
    }

    private static byte[] minimalPdfBytes() throws Exception {
        try (PDDocument document = new PDDocument()) {
            document.addPage(new PDPage());
            try (ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                document.save(out);
                return out.toByteArray();
            }
        }
    }
}
