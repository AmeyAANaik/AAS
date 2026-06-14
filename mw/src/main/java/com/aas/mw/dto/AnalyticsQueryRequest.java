package com.aas.mw.dto;

import java.util.List;
import java.util.Map;

public class AnalyticsQueryRequest {

    private String dateFrom;
    private String dateTo;
    private String granularity = "day"; // day, week, month, quarter
    private List<String> dimensions = List.of("date"); // date, vendor, branch, item_group, item
    private List<String> metrics = List.of("revenue", "profit", "orders"); // revenue, cost, profit, margin_pct, orders, avg_order_value
    private Map<String, String> filters = Map.of(); // keys: vendor, branch, itemGroup, item

    public String getDateFrom() { return dateFrom; }
    public void setDateFrom(String dateFrom) { this.dateFrom = dateFrom; }

    public String getDateTo() { return dateTo; }
    public void setDateTo(String dateTo) { this.dateTo = dateTo; }

    public String getGranularity() { return granularity; }
    public void setGranularity(String granularity) { this.granularity = granularity; }

    public List<String> getDimensions() { return dimensions; }
    public void setDimensions(List<String> dimensions) { this.dimensions = dimensions; }

    public List<String> getMetrics() { return metrics; }
    public void setMetrics(List<String> metrics) { this.metrics = metrics; }

    public Map<String, String> getFilters() { return filters; }
    public void setFilters(Map<String, String> filters) { this.filters = filters; }
}
