package com.aas.mw.dto;

import java.util.List;
import java.util.Map;

public class AnalyticsQueryResponse {

    private final List<AnalyticsColumn> columns;
    private final List<Map<String, Object>> rows;
    private final Map<String, Object> totalsRow;
    private final List<AnalyticsKpi> kpis;
    private final List<String> warnings;

    public AnalyticsQueryResponse(
            List<AnalyticsColumn> columns,
            List<Map<String, Object>> rows,
            Map<String, Object> totalsRow,
            List<AnalyticsKpi> kpis,
            List<String> warnings) {
        this.columns = columns;
        this.rows = rows;
        this.totalsRow = totalsRow;
        this.kpis = kpis;
        this.warnings = warnings != null ? warnings : List.of();
    }

    public AnalyticsQueryResponse(
            List<AnalyticsColumn> columns,
            List<Map<String, Object>> rows,
            Map<String, Object> totalsRow,
            List<AnalyticsKpi> kpis) {
        this(columns, rows, totalsRow, kpis, List.of());
    }

    public List<AnalyticsColumn> getColumns() { return columns; }
    public List<Map<String, Object>> getRows() { return rows; }
    public Map<String, Object> getTotalsRow() { return totalsRow; }
    public List<AnalyticsKpi> getKpis() { return kpis; }
    public List<String> getWarnings() { return warnings; }
}
