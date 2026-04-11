package com.aas.mw.controller;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.config.InvoiceTemplateModelProperties;
import com.aas.mw.dto.NativeLayoutSample;
import com.aas.mw.dto.NativeLayoutTable;
import com.aas.mw.dto.NativeLayoutTableRow;
import com.aas.mw.service.ErpNextFileService;
import com.aas.mw.service.InvoiceFieldMappingService;
import com.aas.mw.service.InvoiceTemplateModelService;
import com.aas.mw.service.CamelotTableExtractionService;
import com.aas.mw.service.NativeLayoutInvoiceService;
import com.aas.mw.service.OcrService;
import com.aas.mw.service.PdfTextExtractionService;
import com.aas.mw.service.VendorInvoiceTemplateCatalog;
import com.aas.mw.service.VendorInvoiceTemplateParser;
import com.aas.mw.service.ErpSessionStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class VendorInvoiceTemplateControllerTest {

    private ErpNextClient erpNextClient;
    private VendorInvoiceTemplateController controller;

    @BeforeEach
    void setup() {
        erpNextClient = mock(ErpNextClient.class);
        controller = new VendorInvoiceTemplateController(
                erpNextClient,
                mock(ErpNextFileService.class),
                mock(OcrService.class),
                new VendorInvoiceTemplateCatalog(),
                new VendorInvoiceTemplateParser(),
                new ObjectMapper(),
                mock(InvoiceTemplateModelService.class),
                mock(PdfTextExtractionService.class),
                mock(InvoiceFieldMappingService.class),
                mock(CamelotTableExtractionService.class),
                mock(NativeLayoutInvoiceService.class));
    }

    @Test
    void clearTemplateStillRemovesStoredProfile() {
        when(erpNextClient.updateResource(eq("Supplier"), eq("SUP-1"), any())).thenReturn(Map.of("name", "SUP-1"));

        ResponseEntity<Map<String, Object>> response = controller.clearTemplate("SUP-1", authorizedRequest());

        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertEquals(Map.of("vendor", Map.of("name", "SUP-1")), response.getBody());
    }

    @Test
    void normalizeNativeStoredProfileFillsObviousHeaderGaps() {
        controller = new VendorInvoiceTemplateController(
                erpNextClient,
                mock(ErpNextFileService.class),
                mock(OcrService.class),
                new VendorInvoiceTemplateCatalog(),
                new VendorInvoiceTemplateParser(),
                new ObjectMapper(),
                new InvoiceTemplateModelService(new InvoiceTemplateModelProperties()),
                mock(PdfTextExtractionService.class),
                mock(InvoiceFieldMappingService.class),
                mock(CamelotTableExtractionService.class),
                mock(NativeLayoutInvoiceService.class));

        NativeLayoutSample sample = new NativeLayoutSample(
                "Sales3329.pdf",
                "",
                "",
                1,
                List.of(new NativeLayoutTable(
                        "table-1",
                        List.of("Sl", "Description of Goods", "HSN/SAC", "GST", "Quantity", "MRP", "Rate", "per", "Amount"),
                        List.of(new NativeLayoutTableRow(1, List.of(), "")),
                        List.of(
                                "Sl Description of Goods HSN/SAC GST Quantity MRP Rate per Amount",
                                "No. Description of Goods",
                                "Rate (Incl. of Tax)"))),
                List.of("Bill Amount"),
                List.of("Bill Amount 101727.00"));
        InvoiceFieldMappingService.MappingResult mappingResult = new InvoiceFieldMappingService.MappingResult(
                List.of(
                        new InvoiceFieldMappingService.FieldMapping("item_name", "Description of Goods", true, true, "high"),
                        new InvoiceFieldMappingService.FieldMapping("item_id", "", true, false, "low"),
                        new InvoiceFieldMappingService.FieldMapping("qty", "", true, false, "low"),
                        new InvoiceFieldMappingService.FieldMapping("uom", "", false, false, "low"),
                        new InvoiceFieldMappingService.FieldMapping("rate", "", true, false, "low"),
                        new InvoiceFieldMappingService.FieldMapping("mrp", "", false, false, "low"),
                        new InvoiceFieldMappingService.FieldMapping("gst", "", true, false, "low"),
                        new InvoiceFieldMappingService.FieldMapping("total", "", true, false, "low")),
                List.of(),
                "",
                "external_api",
                "openai/gpt-4o-mini",
                72);
        InvoiceFieldMappingService.LayoutRuleResult layoutRules = new InvoiceFieldMappingService.LayoutRuleResult(
                "",
                "",
                Map.of(),
                Map.of(),
                Map.of(),
                new InvoiceFieldMappingService.RowRules(List.of(), List.of()),
                "",
                "external_api",
                "openai/gpt-4o-mini");

        NativeLayoutInvoiceService.StoredProfile profile = controller.normalizeNativeStoredProfile(
                sample,
                "profile-1",
                "Profile",
                "Sanshray Foods",
                "",
                mappingResult,
                layoutRules);

        Map<String, String> itemMappings = profile.itemMappings().stream()
                .collect(java.util.stream.Collectors.toMap(
                        InvoiceFieldMappingService.FieldMapping::targetField,
                        InvoiceFieldMappingService.FieldMapping::sourceLabel));

        assertEquals("table-1", profile.primaryItemTableBlockId());
        assertEquals("HSN/SAC", itemMappings.get("item_id"));
        assertEquals("Quantity", itemMappings.get("qty"));
        assertEquals("per", itemMappings.get("uom"));
        assertEquals("Rate", itemMappings.get("rate"));
        assertEquals("MRP", itemMappings.get("mrp"));
        assertEquals("GST", itemMappings.get("gst"));
        assertEquals("Amount", itemMappings.get("total"));
        assertEquals("number_with_uom", profile.fieldParsingRules().get("qty"));
        assertEquals("decimal_amount", profile.fieldParsingRules().get("rate"));
        assertEquals("percentage", profile.fieldParsingRules().get("gst"));
        assertEquals("decimal_amount", profile.fieldParsingRules().get("total"));
        assertTrue(profile.headerContinuationLabels().contains("No."));
        assertTrue(profile.headerContinuationLabels().contains("Rate"));
        assertTrue(profile.headerContinuationLabels().contains("(Incl. of Tax)"));
        assertTrue(profile.skipLabels().contains("Bill Amount"));
    }

    private HttpServletRequest authorizedRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(ErpSessionStore.REQUEST_ATTR, "sid=abc");
        return request;
    }
}
