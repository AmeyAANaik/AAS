package com.aas.mw.controller;

import com.aas.mw.dto.reporting.ReportingCatalogItem;
import com.aas.mw.dto.reporting.ReportingRunRequest;
import com.aas.mw.dto.reporting.ReportingRunResponse;
import com.aas.mw.service.ErpReportingService;
import com.aas.mw.service.ReportingCatalogService;
import com.aas.mw.util.CsvUtil;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.HashMap;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reporting")
public class ReportingController {

    private final ReportingCatalogService catalogService;
    private final ErpReportingService erpReportingService;

    public ReportingController(ReportingCatalogService catalogService, ErpReportingService erpReportingService) {
        this.catalogService = catalogService;
        this.erpReportingService = erpReportingService;
    }

    @GetMapping("/catalog")
    public ResponseEntity<List<ReportingCatalogItem>> catalog() {
        return ResponseEntity.ok(catalogService.catalog());
    }

    @PostMapping("/run")
    public ResponseEntity<?> run(@RequestBody ReportingRunRequest request) {
        String reportName = request == null ? "" : request.getReportName();
        if (reportName == null || reportName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "reportName is required."));
        }
        if (!catalogService.isAllowed(reportName)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Report is not allowed.",
                    "reportName", reportName));
        }
        ResponseEntity<Map<String, Object>> validation = validateFilters(reportName, request.getFilters());
        if (validation != null) {
            return validation;
        }
        Map<String, Object> effectiveFilters = normalizeFilters(reportName, request.getFilters());
        ReportingRunResponse response = erpReportingService.runReport(reportName, effectiveFilters);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/export")
    public ResponseEntity<?> export(@RequestBody ReportingRunRequest request) {
        String reportName = request == null ? "" : request.getReportName();
        if (reportName == null || reportName.isBlank()) {
            return ResponseEntity.badRequest().body(Map.of("error", "reportName is required."));
        }
        if (!catalogService.isAllowed(reportName)) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Report is not allowed.",
                    "reportName", reportName));
        }
        ResponseEntity<Map<String, Object>> validation = validateFilters(reportName, request.getFilters());
        if (validation != null) {
            return validation;
        }
        Map<String, Object> effectiveFilters = normalizeFilters(reportName, request.getFilters());
        ReportingRunResponse response = erpReportingService.runReportUnbounded(reportName, effectiveFilters);
        String csv = CsvUtil.toCsv(response.getRows());
        String filename = sanitizeFilename(reportName) + ".csv";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.valueOf("text/csv"))
                .body(csv);
    }

    private Map<String, Object> normalizeFilters(String reportName, Map<String, Object> filters) {
        ReportingCatalogItem item = catalogService.get(reportName);
        if (item == null || item.getSupportedFilters() == null || item.getSupportedFilters().isEmpty()) {
            return filters == null ? Map.of() : filters;
        }
        Map<String, Object> normalized = new HashMap<>();
        if (filters != null) {
            normalized.putAll(filters);
        }
        for (String key : item.getSupportedFilters()) {
            if (key == null || key.isBlank()) {
                continue;
            }
            normalized.putIfAbsent(key, "");
        }
        return normalized;
    }

    private ResponseEntity<Map<String, Object>> validateFilters(String reportName, Map<String, Object> filters) {
        ReportingCatalogItem item = catalogService.get(reportName);
        boolean supportsBaseline = item != null && item.isSupportsBaseline();

        List<String> details = new ArrayList<>();
        LocalDate fromDate = readDate(filters, "from_date", details);
        LocalDate toDate = readDate(filters, "to_date", details);
        if (fromDate != null && toDate != null && fromDate.isAfter(toDate)) {
            details.add("from_date must be on or before to_date.");
        }

        if (supportsBaseline) {
            LocalDate baselineFrom = readDate(filters, "baseline_from_date", details);
            LocalDate baselineTo = readDate(filters, "baseline_to_date", details);
            if (baselineFrom != null && baselineTo != null && baselineFrom.isAfter(baselineTo)) {
                details.add("baseline_from_date must be on or before baseline_to_date.");
            }
        }

        if (details.isEmpty()) {
            return null;
        }
        return ResponseEntity.badRequest().body(Map.of(
                "error", "validation_error",
                "message", "Invalid report filters.",
                "details", details));
    }

    private LocalDate readDate(Map<String, Object> filters, String key, List<String> details) {
        if (filters == null) {
            details.add(key + " is required.");
            return null;
        }
        Object raw = filters.get(key);
        String text = raw == null ? "" : String.valueOf(raw).trim();
        if (text.isBlank()) {
            details.add(key + " is required.");
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (Exception ex) {
            details.add(key + " must be in YYYY-MM-DD format.");
            return null;
        }
    }

    private String sanitizeFilename(String value) {
        if (value == null || value.isBlank()) {
            return "report";
        }
        return value.trim()
                .replaceAll("[^a-zA-Z0-9\\-_\\.]+", "-")
                .replaceAll("-{2,}", "-")
                .replaceAll("^-", "")
                .replaceAll("-$", "")
                .toLowerCase();
    }
}
