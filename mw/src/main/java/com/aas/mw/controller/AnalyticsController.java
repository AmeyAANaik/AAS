package com.aas.mw.controller;

import com.aas.mw.dto.AnalyticsColumn;
import com.aas.mw.dto.AnalyticsQueryRequest;
import com.aas.mw.dto.AnalyticsQueryResponse;
import com.aas.mw.service.AnalyticsService;
import com.aas.mw.service.ErpNextFileService;
import com.aas.mw.service.MasterDataService;
import com.aas.mw.service.UserService;
import com.aas.mw.util.CsvUtil;
import com.aas.mw.util.LedgerPdfUtil;
import com.aas.mw.util.XlsUtil;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;
    private final MasterDataService masterDataService;
    private final UserService userService;
    private final ErpNextFileService erpNextFileService;

    public AnalyticsController(
            AnalyticsService analyticsService,
            MasterDataService masterDataService,
            UserService userService,
            ErpNextFileService erpNextFileService) {
        this.analyticsService = analyticsService;
        this.masterDataService = masterDataService;
        this.userService = userService;
        this.erpNextFileService = erpNextFileService;
    }

    @PostMapping("/query")
    public ResponseEntity<AnalyticsQueryResponse> query(@RequestBody AnalyticsQueryRequest req) {
        return ResponseEntity.ok(analyticsService.query(req));
    }

    @PostMapping("/query/export")
    public ResponseEntity<?> export(
            @RequestBody AnalyticsQueryRequest req,
            @RequestParam(required = false, defaultValue = "csv") String format) {
        AnalyticsQueryResponse result = analyticsService.query(req);
        return buildExportResponse(result, "analytics-export", format,
                new LedgerPdfUtil.ReportContext(
                        "Analytics Report", "", "",
                        asText(req.getDateFrom()), asText(req.getDateTo())));
    }

    @PostMapping("/item-price-history")
    public ResponseEntity<AnalyticsQueryResponse> itemPriceHistory(@RequestBody AnalyticsQueryRequest req) {
        return ResponseEntity.ok(analyticsService.itemPriceHistory(req));
    }

    @PostMapping("/item-price-history/export")
    public ResponseEntity<?> exportItemPriceHistory(
            @RequestBody AnalyticsQueryRequest req,
            @RequestParam(required = false, defaultValue = "csv") String format) {
        AnalyticsQueryResponse result = analyticsService.itemPriceHistory(req);
        return buildExportResponse(result, "analytics-item-price-history", format,
                new LedgerPdfUtil.ReportContext(
                        "Item Price History", "", "",
                        asText(req.getDateFrom()), asText(req.getDateTo())));
    }

    private ResponseEntity<?> buildExportResponse(
            AnalyticsQueryResponse result,
            String baseName,
            String format,
            LedgerPdfUtil.ReportContext reportContext) {
        List<AnalyticsColumn> columns = result.getColumns();
        List<Map<String, Object>> csvRows = new ArrayList<>();
        for (Map<String, Object> row : result.getRows()) {
            Map<String, Object> csvRow = new LinkedHashMap<>();
            for (AnalyticsColumn col : columns) csvRow.put(col.label(), row.getOrDefault(col.id(), ""));
            csvRows.add(csvRow);
        }
        if (!result.getTotalsRow().isEmpty()) {
            Map<String, Object> totals = new LinkedHashMap<>();
            for (AnalyticsColumn col : columns) totals.put(col.label(), result.getTotalsRow().getOrDefault(col.id(), ""));
            csvRows.add(totals);
        }

        String fmt = format == null || format.isBlank() ? "csv" : format.trim().toLowerCase();
        try {
            return switch (fmt) {
                case "xlsx" -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + baseName + ".xlsx\"")
                        .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                        .body(XlsUtil.toXls(csvRows));
                case "pdf" -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + baseName + ".pdf\"")
                        .contentType(MediaType.parseMediaType("application/pdf"))
                        .body(LedgerPdfUtil.toPdf(csvRows, reportContext.title(), resolveBranding(), reportContext));
                default -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + baseName + ".csv\"")
                        .contentType(MediaType.valueOf("text/csv;charset=UTF-8"))
                        .body(CsvUtil.toCsv(csvRows).getBytes(StandardCharsets.UTF_8));
            };
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private LedgerPdfUtil.Branding resolveBranding() {
        Map<String, Object> company = resolvePreferredCompanyProfile();
        if (company.isEmpty()) return null;
        byte[] logoBytes = null;
        String logoUrl = asText(company.get("logo_url"));
        if (!logoUrl.isBlank()) {
            try {
                logoBytes = erpNextFileService.downloadFile(logoUrl).bytes();
            } catch (Exception ignored) {
                logoBytes = null;
            }
        }
        return new LedgerPdfUtil.Branding(asText(company.get("name")), asText(company.get("tax_id")), logoBytes);
    }

    private Map<String, Object> resolvePreferredCompanyProfile() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth == null ? "" : asText(auth.getName());
        if (!username.isBlank()) {
            try {
                Map<String, Object> profile = userService.getUserProfile(username);
                String companyId = asText(profile.get("company"));
                if (!companyId.isBlank()) {
                    return masterDataService.getCompanyProfile(companyId);
                }
            } catch (Exception ignored) {}
        }
        List<Map<String, Object>> companies = masterDataService.listCompanies();
        if (companies == null || companies.isEmpty()) return Map.of();
        String companyId = asText(companies.get(0).get("name"));
        return companyId.isBlank() ? Map.of() : masterDataService.getCompanyProfile(companyId);
    }

    private String asText(Object value) {
        return value == null ? "" : value.toString().trim();
    }
}
