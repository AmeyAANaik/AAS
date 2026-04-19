package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.config.ReportingProperties;
import com.aas.mw.dto.reporting.ReportingRunResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ErpReportingServiceTest {

    private ErpNextClient erpNextClient;
    private ReportingProperties properties;
    private ErpReportingService reportingService;

    @BeforeEach
    void setup() {
        erpNextClient = mock(ErpNextClient.class);
        properties = new ReportingProperties();
        properties.setMaxRows(2);
        reportingService = new ErpReportingService(erpNextClient, new ObjectMapper(), properties);
    }

    @Test
    void runReportTruncatesRowsAndEncodesFilters() {
        when(erpNextClient.getMethod(eq("frappe.desk.query_report.run"), anyMap()))
                .thenReturn(Map.of(
                        "message", Map.of(
                                "columns", List.of(
                                        Map.of("fieldname", "branch", "label", "Branch"),
                                        Map.of("fieldname", "profit_total", "label", "Profit")),
                                "result", List.of(
                                        Map.of("branch", "B1", "profit_total", 10),
                                        Map.of("branch", "B2", "profit_total", 20),
                                        Map.of("branch", "B3", "profit_total", 30)),
                                "chart", Map.of("type", "bar"))));

        ReportingRunResponse result = reportingService.runReport(
                "AAS - Branch Sales & Profit",
                Map.of("from_date", "2026-04-01", "to_date", "2026-04-10"));

        assertEquals(2, result.getRows().size());
        assertTrue(result.isTruncated());
        assertEquals(2, result.getColumns().size());

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(erpNextClient).getMethod(eq("frappe.desk.query_report.run"), paramsCaptor.capture());
        assertEquals("AAS - Branch Sales & Profit", paramsCaptor.getValue().get("report_name"));
        assertTrue(String.valueOf(paramsCaptor.getValue().get("filters")).contains("\"from_date\""));
        assertEquals("1", paramsCaptor.getValue().get("ignore_prepared_report"));
        assertEquals("1", paramsCaptor.getValue().get("with_chart"));
        assertEquals("1", paramsCaptor.getValue().get("with_report_summary"));
    }

    @Test
    void normalizesListRowsWhenErpReturnsArrays() {
        when(erpNextClient.getMethod(eq("frappe.desk.query_report.run"), anyMap()))
                .thenReturn(Map.of(
                        "message", Map.of(
                                "columns", List.of("Branch:Data:120", "Profit:Currency:120"),
                                "result", List.of(
                                        List.of("B1", 10),
                                        List.of("B2", 20)))));

        ReportingRunResponse result = reportingService.runReportUnbounded("AAS - Branch Sales & Profit", Map.of());

        assertEquals(2, result.getColumns().size());
        assertEquals("c0", result.getColumns().get(0).getId());
        assertEquals("Branch", result.getColumns().get(0).getLabel());
        assertEquals(2, result.getRows().size());
        assertEquals("B1", result.getRows().get(0).get("c0"));
        assertEquals(10, result.getRows().get(0).get("c1"));
    }
}
