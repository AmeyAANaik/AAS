package com.aas.mw.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.reporting")
public class ReportingProperties {

    /**
     * List of ERPNext Query Report names that MW is allowed to execute via /api/reporting.
     * If empty, MW will fall back to the built-in report catalog.
     */
    private List<String> allowedReportNames = new ArrayList<>();

    /**
     * Maximum number of rows returned by /api/reporting/run (UI display).
     * Export endpoints may bypass this (or apply their own limits).
     */
    private int maxRows = 500;

    public List<String> getAllowedReportNames() {
        return allowedReportNames;
    }

    public void setAllowedReportNames(List<String> allowedReportNames) {
        this.allowedReportNames = allowedReportNames == null ? new ArrayList<>() : new ArrayList<>(allowedReportNames);
    }

    public int getMaxRows() {
        return maxRows;
    }

    public void setMaxRows(int maxRows) {
        this.maxRows = maxRows;
    }
}

