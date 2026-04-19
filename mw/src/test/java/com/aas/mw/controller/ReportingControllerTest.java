package com.aas.mw.controller;

import com.aas.mw.config.ReportingProperties;
import com.aas.mw.dto.reporting.ReportingRunRequest;
import com.aas.mw.dto.reporting.ReportingRunResponse;
import com.aas.mw.service.ErpReportingService;
import com.aas.mw.service.ReportingCatalogService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportingControllerTest {

    private ReportingController controller;
    private ErpReportingService erpReportingService;

    @BeforeEach
    void setup() {
        ReportingProperties properties = new ReportingProperties();
        ReportingCatalogService catalogService = new ReportingCatalogService(properties);
        erpReportingService = mock(ErpReportingService.class);
        controller = new ReportingController(catalogService, erpReportingService);
    }

    @Test
    void runRejectsMissingDateFilters() {
        ReportingRunRequest request = new ReportingRunRequest();
        request.setReportName("AAS - Sales Profit Summary");
        request.setFilters(Map.of());

        ResponseEntity<?> response = controller.run(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void runRejectsInvalidDateRange() {
        ReportingRunRequest request = new ReportingRunRequest();
        request.setReportName("AAS - Sales Profit Summary");
        request.setFilters(Map.of("from_date", "2026-04-10", "to_date", "2026-04-01"));

        ResponseEntity<?> response = controller.run(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void runRequiresBaselineDatesForBaselineReports() {
        ReportingRunRequest request = new ReportingRunRequest();
        request.setReportName("AAS - Item Trend vs Baseline");
        request.setFilters(Map.of("from_date", "2026-04-01", "to_date", "2026-04-10"));

        ResponseEntity<?> response = controller.run(request);

        assertEquals(HttpStatus.BAD_REQUEST, response.getStatusCode());
    }

    @Test
    void runCallsErpWhenFiltersValid() {
        ReportingRunRequest request = new ReportingRunRequest();
        request.setReportName("AAS - Sales Profit Summary");
        request.setFilters(Map.of("from_date", "2026-04-01", "to_date", "2026-04-10"));

        when(erpReportingService.runReport(eq("AAS - Sales Profit Summary"), anyMap()))
                .thenReturn(new ReportingRunResponse());

        ResponseEntity<?> response = controller.run(request);

        assertEquals(HttpStatus.OK, response.getStatusCode());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> filtersCaptor = ArgumentCaptor.forClass(Map.class);
        verify(erpReportingService).runReport(eq("AAS - Sales Profit Summary"), filtersCaptor.capture());
        Map<String, Object> filters = filtersCaptor.getValue();
        assertTrue(filters.containsKey("company"));
        assertTrue(filters.containsKey("branch"));
        assertTrue(filters.containsKey("vendor"));
    }
}
