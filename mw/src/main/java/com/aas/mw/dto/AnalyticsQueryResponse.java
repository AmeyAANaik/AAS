package com.aas.mw.dto;

import java.util.List;
import java.util.Map;

public class AnalyticsQueryResponse {

    private final List<AnalyticsColumn> columns;
    private final List<Map<String, Object>> rows;
    private final Map<String, Object> totalsRow;
    private final List<AnalyticsKpi> kpis;

    public AnalyticsQueryResponse(
            List<AnalyticsColumn> columns,
            List<Map<String, Object>> rows,
            Map<String, Object> totalsRow,
            List<AnalyticsKpi> kpis) {
        this.columns = columns;
        this.rows = rows;
        this.totalsRow = totalsRow;
        this.kpis = kpis;
    }

    public List<AnalyticsColumn> getColumns() { return columns; }
    public List<Map<String, Object>> getRows() { return rows; }
    public Map<String, Object> getTotalsRow() { return totalsRow; }
    public List<AnalyticsKpi> getKpis() { return kpis; }
}
