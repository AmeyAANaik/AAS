package com.aas.mw.dto.reporting;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ReportingRunResponse {

    private List<ReportingColumn> columns = new ArrayList<>();
    private List<Map<String, Object>> rows = new ArrayList<>();
    private Map<String, Object> totals = new HashMap<>();
    private Map<String, Object> chart = new HashMap<>();
    private boolean truncated = false;

    public ReportingRunResponse() {}

    public ReportingRunResponse(
            List<ReportingColumn> columns,
            List<Map<String, Object>> rows,
            Map<String, Object> totals,
            Map<String, Object> chart,
            boolean truncated) {
        this.columns = columns == null ? new ArrayList<>() : new ArrayList<>(columns);
        this.rows = rows == null ? new ArrayList<>() : new ArrayList<>(rows);
        this.totals = totals == null ? new HashMap<>() : new HashMap<>(totals);
        this.chart = chart == null ? new HashMap<>() : new HashMap<>(chart);
        this.truncated = truncated;
    }

    public List<ReportingColumn> getColumns() {
        return columns;
    }

    public void setColumns(List<ReportingColumn> columns) {
        this.columns = columns == null ? new ArrayList<>() : new ArrayList<>(columns);
    }

    public List<Map<String, Object>> getRows() {
        return rows;
    }

    public void setRows(List<Map<String, Object>> rows) {
        this.rows = rows == null ? new ArrayList<>() : new ArrayList<>(rows);
    }

    public Map<String, Object> getTotals() {
        return totals;
    }

    public void setTotals(Map<String, Object> totals) {
        this.totals = totals == null ? new HashMap<>() : new HashMap<>(totals);
    }

    public Map<String, Object> getChart() {
        return chart;
    }

    public void setChart(Map<String, Object> chart) {
        this.chart = chart == null ? new HashMap<>() : new HashMap<>(chart);
    }

    public boolean isTruncated() {
        return truncated;
    }

    public void setTruncated(boolean truncated) {
        this.truncated = truncated;
    }
}

