package com.aas.mw.service;

import com.aas.mw.config.ReportingProperties;
import com.aas.mw.dto.reporting.ReportingCatalogItem;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ReportingCatalogService {

    private final ReportingProperties properties;
    private final Map<String, ReportingCatalogItem> catalog;

    public ReportingCatalogService(ReportingProperties properties) {
        this.properties = properties;
        this.catalog = buildDefaultCatalog();
    }

    public List<ReportingCatalogItem> catalog() {
        List<String> allowed = normalizeAllowedReports(properties.getAllowedReportNames());
        if (allowed.isEmpty()) {
            return List.copyOf(catalog.values());
        }
        List<ReportingCatalogItem> filtered = new ArrayList<>();
        for (String name : allowed) {
            ReportingCatalogItem item = catalog.get(name);
            if (item != null) {
                filtered.add(item);
            } else {
                // Unknown report name in config - skip from catalog output.
            }
        }
        return List.copyOf(filtered);
    }

    public boolean isAllowed(String reportName) {
        if (reportName == null || reportName.isBlank()) {
            return false;
        }
        List<String> allowed = normalizeAllowedReports(properties.getAllowedReportNames());
        if (allowed.isEmpty()) {
            return catalog.containsKey(reportName);
        }
        return allowed.contains(reportName);
    }

    public ReportingCatalogItem get(String reportName) {
        return catalog.get(reportName);
    }

    private List<String> normalizeAllowedReports(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String value : raw) {
            String name = value == null ? "" : value.trim();
            if (name.isBlank() || normalized.contains(name)) {
                continue;
            }
            normalized.add(name);
        }
        return List.copyOf(normalized);
    }

    private Map<String, ReportingCatalogItem> buildDefaultCatalog() {
        LinkedHashMap<String, ReportingCatalogItem> map = new LinkedHashMap<>();

        map.put("AAS - Sales Profit Summary", new ReportingCatalogItem(
                "AAS - Sales Profit Summary",
                "Sales Profit Summary",
                "Overall sales, cost, and profit totals for a date range.",
                false,
                List.of("from_date", "to_date", "company", "branch", "vendor")));

        map.put("AAS - Branch Sales & Profit", new ReportingCatalogItem(
                "AAS - Branch Sales & Profit",
                "Branchwise Sales & Profit",
                "Sales, cost, and profit totals grouped by branch for a date range.",
                false,
                List.of("from_date", "to_date", "company", "branch", "vendor")));

        map.put("AAS - Vendor Sales & Profit", new ReportingCatalogItem(
                "AAS - Vendor Sales & Profit",
                "Vendorwise Sales & Profit",
                "Sales, cost, and profit totals grouped by vendor for a date range.",
                false,
                List.of("from_date", "to_date", "company", "vendor")));

        map.put("AAS - Item Trend vs Baseline", new ReportingCatalogItem(
                "AAS - Item Trend vs Baseline",
                "Item Trend vs Baseline",
                "Baseline-period average vs current-period average per item.",
                true,
                List.of(
                        "baseline_from_date",
                        "baseline_to_date",
                        "from_date",
                        "to_date",
                        "company",
                        "vendor",
                        "item",
                        "item_group")));

        return Map.copyOf(map);
    }
}
