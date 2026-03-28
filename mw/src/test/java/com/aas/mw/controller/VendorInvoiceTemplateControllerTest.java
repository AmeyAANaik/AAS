package com.aas.mw.controller;

import com.aas.mw.client.ErpNextClient;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

    private HttpServletRequest authorizedRequest() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(ErpSessionStore.REQUEST_ATTR, "sid=abc");
        return request;
    }
}
