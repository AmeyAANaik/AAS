package com.aas.mw.controller;

import com.aas.mw.service.VendorOpsService;
import com.aas.mw.service.ErpNextFileService;
import com.aas.mw.service.MasterDataService;
import com.aas.mw.service.UserService;
import com.aas.mw.util.CsvUtil;
import com.aas.mw.util.LedgerPdfUtil;
import com.aas.mw.util.XlsUtil;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/vendor-ops")
public class VendorOpsController {

    private final VendorOpsService vendorOpsService;
    private final MasterDataService masterDataService;
    private final UserService userService;
    private final ErpNextFileService erpNextFileService;

    public VendorOpsController(
            VendorOpsService vendorOpsService,
            MasterDataService masterDataService,
            UserService userService,
            ErpNextFileService erpNextFileService) {
        this.vendorOpsService = vendorOpsService;
        this.masterDataService = masterDataService;
        this.userService = userService;
        this.erpNextFileService = erpNextFileService;
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Object>> summary() {
        return ResponseEntity.ok(vendorOpsService.getSummary());
    }

    @GetMapping("/ledger/export")
    public ResponseEntity<byte[]> exportAllVendorLedgers(
            @RequestParam(required = false, defaultValue = "csv") String format) {
        List<Map<String, Object>> rows = vendorOpsService.getAllVendorLedgerEntries();
        return buildExportResponse(rows, "vendor-ledger-all", format);
    }

    @GetMapping("/{vendorId}")
    public ResponseEntity<Map<String, Object>> vendorDetail(@PathVariable String vendorId) {
        return ResponseEntity.ok(vendorOpsService.getVendorDetail(vendorId));
    }

    @GetMapping("/{vendorId}/orders")
    public ResponseEntity<List<Map<String, Object>>> vendorOrders(
            @PathVariable String vendorId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String branch,
            @RequestParam(required = false, name = "from") String fromDate,
            @RequestParam(required = false, name = "to") String toDate) {
        return ResponseEntity.ok(vendorOpsService.getVendorOrders(vendorId, status, branch, fromDate, toDate));
    }

    @GetMapping("/{vendorId}/analytics")
    public ResponseEntity<Map<String, Object>> vendorAnalytics(@PathVariable String vendorId) {
        return ResponseEntity.ok(vendorOpsService.getVendorAnalytics(vendorId));
    }

    @GetMapping("/{vendorId}/ledger")
    public ResponseEntity<Map<String, Object>> vendorLedger(
            @PathVariable String vendorId,
            @RequestParam(required = false, name = "from") String fromDate,
            @RequestParam(required = false, name = "to") String toDate) {
        return ResponseEntity.ok(vendorOpsService.getVendorLedger(vendorId, fromDate, toDate));
    }

    @GetMapping("/{vendorId}/ledger/export")
    public ResponseEntity<byte[]> exportVendorLedger(
            @PathVariable String vendorId,
            @RequestParam(required = false, name = "from") String fromDate,
            @RequestParam(required = false, name = "to") String toDate,
            @RequestParam(required = false, defaultValue = "csv") String format) {
        Map<String, Object> ledger = vendorOpsService.getVendorLedger(vendorId, fromDate, toDate);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) ledger.getOrDefault("entries", List.of());
        return buildExportResponse(entries, "vendor-ledger-" + sanitizeFileName(vendorId), format);
    }

    @GetMapping("/{vendorId}/ledger/category")
    public ResponseEntity<Map<String, Object>> vendorLedgerByCategory(
            @PathVariable String vendorId,
            @RequestParam String categoryId,
            @RequestParam(required = false, name = "from") String fromDate,
            @RequestParam(required = false, name = "to") String toDate) {
        return ResponseEntity.ok(vendorOpsService.getVendorLedgerByCategory(vendorId, categoryId, fromDate, toDate));
    }

    @GetMapping("/{vendorId}/ledger/category/export")
    public ResponseEntity<byte[]> exportVendorLedgerByCategory(
            @PathVariable String vendorId,
            @RequestParam String categoryId,
            @RequestParam(required = false, name = "from") String fromDate,
            @RequestParam(required = false, name = "to") String toDate,
            @RequestParam(required = false, defaultValue = "csv") String format) {
        Map<String, Object> ledger = vendorOpsService.getVendorLedgerByCategory(vendorId, categoryId, fromDate, toDate);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> entries = (List<Map<String, Object>>) ledger.getOrDefault("entries", List.of());
        return buildExportResponse(entries, "vendor-ledger-" + sanitizeFileName(vendorId) + "-" + sanitizeFileName(categoryId), format);
    }

    @GetMapping("/{vendorId}/ledger/categories/export")
    public ResponseEntity<byte[]> exportVendorCategoryLedgerSummary(
            @PathVariable String vendorId,
            @RequestParam(required = false, name = "from") String fromDate,
            @RequestParam(required = false, name = "to") String toDate,
            @RequestParam(required = false, defaultValue = "csv") String format) {
        Map<String, Object> ledger = vendorOpsService.getVendorLedger(vendorId, fromDate, toDate);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> categories = (List<Map<String, Object>>) ledger.getOrDefault("categorySummary", List.of());
        return buildExportResponse(categories, "vendor-ledger-categories-" + sanitizeFileName(vendorId), format);
    }

    @GetMapping("/ledger/categories/export")
    public ResponseEntity<byte[]> exportAllVendorsCategoryLedgers(
            @RequestParam(required = false, name = "from") String fromDate,
            @RequestParam(required = false, name = "to") String toDate,
            @RequestParam(required = false, defaultValue = "csv") String format) {
        List<Map<String, Object>> rows = vendorOpsService.getAllVendorCategorySummaries(fromDate, toDate);
        return buildExportResponse(rows, "vendor-ledger-categories-all", format);
    }

    private ResponseEntity<byte[]> buildExportResponse(List<Map<String, Object>> rows, String baseName, String format) {
        String fmt = format == null || format.isBlank() ? "csv" : format.toLowerCase();
        try {
            return switch (fmt) {
                case "xlsx" -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + baseName + ".xlsx\"")
                        .contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                        .body(XlsUtil.toXls(rows));
                case "pdf" -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + baseName + ".pdf\"")
                        .contentType(MediaType.APPLICATION_PDF)
                        .body(LedgerPdfUtil.toPdf(rows, humanizeTitle(baseName), resolveBranding()));
                default -> ResponseEntity.ok()
                        .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + baseName + ".csv\"")
                        .contentType(MediaType.valueOf("text/csv;charset=UTF-8"))
                        .body(CsvUtil.toCsv(rows).getBytes(StandardCharsets.UTF_8));
            };
        } catch (IOException e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    private String sanitizeFileName(String value) {
        if (value == null || value.isBlank()) {
            return "unknown";
        }
        return value.replaceAll("[^A-Za-z0-9._-]+", "_");
    }

    private String humanizeTitle(String baseName) {
        if (baseName == null || baseName.isBlank()) {
            return "Vendor Ledger";
        }
        String normalized = baseName.replace('-', ' ').replace('_', ' ').trim().replaceAll("\\s+", " ");
        if (normalized.isBlank()) {
            return "Vendor Ledger";
        }
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    private LedgerPdfUtil.Branding resolveBranding() {
        Map<String, Object> company = resolvePreferredCompanyProfile();
        if (company.isEmpty()) {
            return null;
        }
        byte[] logoBytes = null;
        String logoUrl = asText(company.get("logo_url"));
        if (!logoUrl.isBlank()) {
            try {
                logoBytes = erpNextFileService.downloadFile(logoUrl).bytes();
            } catch (Exception ignored) {
                logoBytes = null;
            }
        }
        return new LedgerPdfUtil.Branding(
                asText(company.get("name")),
                asText(company.get("tax_id")),
                logoBytes);
    }

    private Map<String, Object> resolvePreferredCompanyProfile() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth == null ? "" : asText(auth.getName());
        if (!username.isBlank()) {
            Map<String, Object> profile = userService.getUserProfile(username);
            String companyId = asText(profile.get("company"));
            if (!companyId.isBlank()) {
                try {
                    return masterDataService.getCompanyProfile(companyId);
                } catch (Exception ignored) {
                    // Fall back to first available company below.
                }
            }
        }
        List<Map<String, Object>> companies = masterDataService.listCompanies();
        if (companies == null || companies.isEmpty()) {
            return Map.of();
        }
        String companyId = asText(companies.get(0).get("name"));
        return companyId.isBlank() ? Map.of() : masterDataService.getCompanyProfile(companyId);
    }

    private String asText(Object value) {
        return value == null ? "" : value.toString().trim();
    }
}
