package com.aas.mw.dto.reporting;

import java.util.HashMap;
import java.util.Map;

public class ReportingRunRequest {

    private String reportName = "";
    private Map<String, Object> filters = new HashMap<>();

    public ReportingRunRequest() {}

    public String getReportName() {
        return reportName;
    }

    public void setReportName(String reportName) {
        this.reportName = reportName == null ? "" : reportName;
    }

    public Map<String, Object> getFilters() {
        return filters;
    }

    public void setFilters(Map<String, Object> filters) {
        this.filters = filters == null ? new HashMap<>() : new HashMap<>(filters);
    }
}

