package com.aas.mw.dto.reporting;

import java.util.ArrayList;
import java.util.List;

public class ReportingCatalogItem {

    private String reportName = "";
    private String label = "";
    private String description = "";
    private boolean supportsBaseline = false;
    private List<String> supportedFilters = new ArrayList<>();

    public ReportingCatalogItem() {}

    public ReportingCatalogItem(
            String reportName,
            String label,
            String description,
            boolean supportsBaseline,
            List<String> supportedFilters) {
        this.reportName = reportName;
        this.label = label;
        this.description = description;
        this.supportsBaseline = supportsBaseline;
        this.supportedFilters = supportedFilters == null ? new ArrayList<>() : new ArrayList<>(supportedFilters);
    }

    public String getReportName() {
        return reportName;
    }

    public void setReportName(String reportName) {
        this.reportName = reportName == null ? "" : reportName;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label == null ? "" : label;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description == null ? "" : description;
    }

    public boolean isSupportsBaseline() {
        return supportsBaseline;
    }

    public void setSupportsBaseline(boolean supportsBaseline) {
        this.supportsBaseline = supportsBaseline;
    }

    public List<String> getSupportedFilters() {
        return supportedFilters;
    }

    public void setSupportedFilters(List<String> supportedFilters) {
        this.supportedFilters = supportedFilters == null ? new ArrayList<>() : new ArrayList<>(supportedFilters);
    }
}

