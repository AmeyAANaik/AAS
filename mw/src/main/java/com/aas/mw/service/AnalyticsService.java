package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.dto.AnalyticsColumn;
import com.aas.mw.dto.AnalyticsKpi;
import com.aas.mw.dto.AnalyticsQueryRequest;
import com.aas.mw.dto.AnalyticsQueryResponse;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsService {

    private static final List<String> VALID_DIMS = List.of("date", "vendor", "branch", "item_group");
    private static final List<String> VALID_METRICS = List.of("revenue", "cost", "profit", "margin_pct", "orders", "avg_order_value");

    private final ErpNextClient erpNextClient;

    public AnalyticsService(ErpNextClient erpNextClient) {
        this.erpNextClient = erpNextClient;
    }

    public AnalyticsQueryResponse query(AnalyticsQueryRequest req) {
        DateRange range = resolveRange(req.getDateFrom(), req.getDateTo());
        List<String> dims = normalizeDims(req.getDimensions());
        List<String> mets = normalizeMetrics(req.getMetrics());
        String granularity = req.getGranularity() != null ? req.getGranularity().trim().toLowerCase() : "day";
        Map<String, String> filters = req.getFilters() != null ? req.getFilters() : Map.of();

        boolean hasItemGroup = dims.contains("item_group");
        boolean needCost = mets.stream().anyMatch(m -> Set.of("cost", "profit", "margin_pct", "avg_order_value").contains(m));
        boolean needItems = hasItemGroup || needCost;

        List<Map<String, Object>> orders = fetchOrders(range, filters);

        Map<String, Map<String, Object>> fullOrderMap = needItems ? fetchFullOrderMap(orders) : Map.of();

        Map<String, AggRow> agg = hasItemGroup
                ? aggregateByItems(orders, fullOrderMap, dims, granularity)
                : aggregateByOrders(orders, fullOrderMap, dims, granularity, needCost);

        List<Map<String, Object>> rows = buildRows(agg, dims, mets, granularity);
        rows = sortRows(rows, dims);

        List<AnalyticsColumn> columns = buildColumns(dims, mets, granularity);
        Map<String, Object> totalsRow = buildTotals(rows, dims, mets);
        List<AnalyticsKpi> kpis = buildKpis(totalsRow, mets);

        return new AnalyticsQueryResponse(columns, rows, totalsRow, kpis);
    }

    // -------------------------------------------------------------------------
    // Aggregation
    // -------------------------------------------------------------------------

    private Map<String, AggRow> aggregateByOrders(
            List<Map<String, Object>> orders,
            Map<String, Map<String, Object>> fullOrderMap,
            List<String> dims,
            String granularity,
            boolean needCost) {
        Map<String, AggRow> agg = new LinkedHashMap<>();
        Grouping grouping = Grouping.parse(granularity);
        for (Map<String, Object> order : orders) {
            String key = buildKey(order, dims, grouping);
            AggRow row = agg.computeIfAbsent(key, k -> {
                AggRow r = new AggRow();
                populateDims(r, order, dims, grouping);
                return r;
            });
            row.revenue += asDouble(order.get("grand_total"));
            row.orderNames.add(asString(order.get("name")));
            if (needCost) {
                Map<String, Object> full = fullOrderMap.getOrDefault(asString(order.get("name")), Map.of());
                row.cost += extractCostFromItems(full);
            }
        }
        return agg;
    }

    private Map<String, AggRow> aggregateByItems(
            List<Map<String, Object>> orders,
            Map<String, Map<String, Object>> fullOrderMap,
            List<String> dims,
            String granularity) {
        Map<String, AggRow> agg = new LinkedHashMap<>();
        Grouping grouping = Grouping.parse(granularity);
        for (Map<String, Object> order : orders) {
            String orderName = asString(order.get("name"));
            Map<String, Object> full = fullOrderMap.getOrDefault(orderName, Map.of());
            Object items = full.get("items");
            if (!(items instanceof List<?> list)) continue;
            for (Object obj : list) {
                if (!(obj instanceof Map<?, ?> item)) continue;
                Map<String, Object> orderWithGroup = new HashMap<>(order);
                orderWithGroup.put("item_group", asString(item.get("item_group")));
                String key = buildKey(orderWithGroup, dims, grouping);
                AggRow row = agg.computeIfAbsent(key, k -> {
                    AggRow r = new AggRow();
                    populateDims(r, orderWithGroup, dims, grouping);
                    return r;
                });
                double qty = asDouble(item.get("qty"));
                double rate = asDouble(item.get("rate"));
                double amount = asDouble(item.get("amount"));
                if (amount == 0 && qty > 0) amount = rate * qty;
                double vendorRate = asDouble(item.get("aas_vendor_rate"));
                row.revenue += amount;
                row.cost += (vendorRate > 0 && qty > 0) ? vendorRate * qty : 0;
                row.orderNames.add(orderName);
            }
        }
        return agg;
    }

    // -------------------------------------------------------------------------
    // Key / dimension helpers
    // -------------------------------------------------------------------------

    private String buildKey(Map<String, Object> order, List<String> dims, Grouping grouping) {
        StringBuilder sb = new StringBuilder();
        for (String dim : dims) {
            sb.append(dim).append('=').append(dimValue(order, dim, grouping)).append('|');
        }
        return sb.toString();
    }

    private String dimValue(Map<String, Object> order, String dim, Grouping grouping) {
        return switch (dim) {
            case "date" -> {
                LocalDate d = parseDate(order.get("transaction_date"));
                yield d != null ? grouping.periodFor(d).start() : "";
            }
            case "vendor" -> asString(order.get("aas_vendor"));
            case "branch" -> asString(order.get("customer"));
            case "item_group" -> asString(order.get("item_group"));
            default -> "";
        };
    }

    private void populateDims(AggRow row, Map<String, Object> order, List<String> dims, Grouping grouping) {
        for (String dim : dims) {
            switch (dim) {
                case "date" -> {
                    LocalDate d = parseDate(order.get("transaction_date"));
                    if (d != null) {
                        PeriodKey pk = grouping.periodFor(d);
                        row.dims.put("date", pk.start());
                        row.dims.put("_date_end", pk.end());
                    }
                }
                case "vendor" -> row.dims.put("vendor", asString(order.get("aas_vendor")));
                case "branch" -> row.dims.put("branch", asString(order.get("customer")));
                case "item_group" -> row.dims.put("item_group", asString(order.get("item_group")));
            }
        }
    }

    // -------------------------------------------------------------------------
    // Row / column building
    // -------------------------------------------------------------------------

    private List<Map<String, Object>> buildRows(Map<String, AggRow> agg, List<String> dims, List<String> mets, String granularity) {
        boolean multiDayGranularity = !granularity.equals("day");
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AggRow aggRow : agg.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String dim : dims) {
                row.put(dim, aggRow.dims.getOrDefault(dim, ""));
                if ("date".equals(dim) && multiDayGranularity) {
                    row.put("date_end", aggRow.dims.getOrDefault("_date_end", ""));
                }
            }
            int orders = aggRow.orderNames.size();
            double revenue = round(aggRow.revenue);
            double cost = round(aggRow.cost);
            double profit = round(revenue - cost);
            double marginPct = revenue > 0 ? round((profit / revenue) * 100) : 0;
            double avgOrderValue = orders > 0 ? round(revenue / orders) : 0;
            for (String met : mets) {
                switch (met) {
                    case "revenue" -> row.put("revenue", revenue);
                    case "cost" -> row.put("cost", cost);
                    case "profit" -> row.put("profit", profit);
                    case "margin_pct" -> row.put("margin_pct", marginPct);
                    case "orders" -> row.put("orders", orders);
                    case "avg_order_value" -> row.put("avg_order_value", avgOrderValue);
                }
            }
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> sortRows(List<Map<String, Object>> rows, List<String> dims) {
        if (dims.contains("date")) {
            rows.sort(Comparator.comparing(r -> String.valueOf(r.getOrDefault("date", ""))));
        } else {
            rows.sort((a, b) -> Double.compare(
                    asDouble(b.getOrDefault("revenue", 0)),
                    asDouble(a.getOrDefault("revenue", 0))));
        }
        return rows;
    }

    private List<AnalyticsColumn> buildColumns(List<String> dims, List<String> mets, String granularity) {
        boolean multiDayGranularity = !granularity.equals("day");
        List<AnalyticsColumn> columns = new ArrayList<>();
        for (String dim : dims) {
            switch (dim) {
                case "date" -> {
                    columns.add(new AnalyticsColumn("date", granularityLabel(granularity), "DIMENSION"));
                    if (multiDayGranularity) columns.add(new AnalyticsColumn("date_end", "Period End", "DIMENSION"));
                }
                case "vendor" -> columns.add(new AnalyticsColumn("vendor", "Vendor", "DIMENSION"));
                case "branch" -> columns.add(new AnalyticsColumn("branch", "Branch", "DIMENSION"));
                case "item_group" -> columns.add(new AnalyticsColumn("item_group", "Category", "DIMENSION"));
            }
        }
        for (String met : mets) {
            switch (met) {
                case "revenue" -> columns.add(new AnalyticsColumn("revenue", "Revenue", "CURRENCY"));
                case "cost" -> columns.add(new AnalyticsColumn("cost", "Cost", "CURRENCY"));
                case "profit" -> columns.add(new AnalyticsColumn("profit", "Profit", "CURRENCY"));
                case "margin_pct" -> columns.add(new AnalyticsColumn("margin_pct", "Margin %", "PERCENT"));
                case "orders" -> columns.add(new AnalyticsColumn("orders", "Orders", "NUMBER"));
                case "avg_order_value" -> columns.add(new AnalyticsColumn("avg_order_value", "Avg Order", "CURRENCY"));
            }
        }
        return columns;
    }

    private String granularityLabel(String granularity) {
        return switch (granularity) {
            case "week" -> "Week";
            case "month" -> "Month";
            case "quarter" -> "Quarter";
            default -> "Date";
        };
    }

    private Map<String, Object> buildTotals(List<Map<String, Object>> rows, List<String> dims, List<String> mets) {
        if (rows.isEmpty()) return Map.of();
        Map<String, Object> totals = new LinkedHashMap<>();
        boolean labeled = false;
        for (String dim : dims) {
            totals.put(dim, labeled ? "" : "Total");
            labeled = true;
        }
        double totalRevenue = 0, totalCost = 0;
        int totalOrders = 0;
        for (Map<String, Object> row : rows) {
            totalRevenue += asDouble(row.getOrDefault("revenue", 0));
            totalCost += asDouble(row.getOrDefault("cost", 0));
            totalOrders += (int) asDouble(row.getOrDefault("orders", 0));
        }
        double totalProfit = round(totalRevenue - totalCost);
        double totalMarginPct = totalRevenue > 0 ? round((totalProfit / totalRevenue) * 100) : 0;
        double totalAvgOrderValue = totalOrders > 0 ? round(totalRevenue / totalOrders) : 0;
        for (String met : mets) {
            switch (met) {
                case "revenue" -> totals.put("revenue", round(totalRevenue));
                case "cost" -> totals.put("cost", round(totalCost));
                case "profit" -> totals.put("profit", totalProfit);
                case "margin_pct" -> totals.put("margin_pct", totalMarginPct);
                case "orders" -> totals.put("orders", totalOrders);
                case "avg_order_value" -> totals.put("avg_order_value", totalAvgOrderValue);
            }
        }
        return totals;
    }

    private List<AnalyticsKpi> buildKpis(Map<String, Object> totals, List<String> mets) {
        List<AnalyticsKpi> kpis = new ArrayList<>();
        for (String met : mets) {
            double value = asDouble(totals.getOrDefault(met, 0));
            String label = switch (met) {
                case "revenue" -> "Total Revenue";
                case "cost" -> "Total Cost";
                case "profit" -> "Total Profit";
                case "margin_pct" -> "Avg Margin";
                case "orders" -> "Total Orders";
                case "avg_order_value" -> "Avg Order Value";
                default -> met;
            };
            String valueType = switch (met) {
                case "revenue", "cost", "profit", "avg_order_value" -> "CURRENCY";
                case "margin_pct" -> "PERCENT";
                default -> "NUMBER";
            };
            kpis.add(new AnalyticsKpi(met, label, value, valueType));
        }
        return kpis;
    }

    // -------------------------------------------------------------------------
    // ERP fetch helpers
    // -------------------------------------------------------------------------

    private List<Map<String, Object>> fetchOrders(DateRange range, Map<String, String> filters) {
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\",\"customer\",\"transaction_date\",\"aas_vendor\",\"aas_status\",\"aas_is_deleted\",\"grand_total\"]");
        params.put("order_by", "transaction_date asc");
        List<List<String>> filterList = new ArrayList<>();
        filterList.add(List.of("transaction_date", ">=", range.start()));
        filterList.add(List.of("transaction_date", "<=", range.end()));
        String vendor = filters.getOrDefault("vendor", "");
        if (vendor != null && !vendor.isBlank()) filterList.add(List.of("aas_vendor", "=", vendor));
        String branch = filters.getOrDefault("branch", "");
        if (branch != null && !branch.isBlank()) filterList.add(List.of("customer", "=", branch));
        params.put("filters", toJson(filterList));
        return erpNextClient.listResources("Sales Order", params).stream()
                .filter(o -> !asFlag(o.get("aas_is_deleted")) && !"DELETED".equalsIgnoreCase(asString(o.get("aas_status"))))
                .toList();
    }

    private Map<String, Map<String, Object>> fetchFullOrderMap(List<Map<String, Object>> orders) {
        Map<String, Map<String, Object>> result = new HashMap<>();
        for (Map<String, Object> order : orders) {
            String name = asString(order.get("name"));
            if (name.isBlank()) continue;
            Map<String, Object> full = unwrapResource(erpNextClient.getResource("Sales Order", name));
            result.put(name, full);
        }
        return result;
    }

    private double extractCostFromItems(Map<String, Object> order) {
        Object items = order.get("items");
        if (!(items instanceof List<?> list)) return 0;
        double cost = 0;
        for (Object obj : list) {
            if (!(obj instanceof Map<?, ?> item)) continue;
            double qty = asDouble(item.get("qty"));
            double vendorRate = asDouble(item.get("aas_vendor_rate"));
            if (vendorRate > 0 && qty > 0) cost += vendorRate * qty;
        }
        return cost;
    }

    private Map<String, Object> unwrapResource(Map<String, Object> resource) {
        if (resource == null) return Map.of();
        Object data = resource.get("data");
        if (data instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> unwrapped = (Map<String, Object>) map;
            return unwrapped;
        }
        return resource;
    }

    // -------------------------------------------------------------------------
    // Normalization / validation
    // -------------------------------------------------------------------------

    private List<String> normalizeDims(List<String> dims) {
        if (dims == null || dims.isEmpty()) return List.of("date");
        return dims.stream()
                .map(String::toLowerCase)
                .filter(VALID_DIMS::contains)
                .distinct()
                .collect(Collectors.toList());
    }

    private List<String> normalizeMetrics(List<String> mets) {
        if (mets == null || mets.isEmpty()) return List.of("revenue", "profit", "orders");
        return mets.stream()
                .map(String::toLowerCase)
                .filter(VALID_METRICS::contains)
                .distinct()
                .collect(Collectors.toList());
    }

    // -------------------------------------------------------------------------
    // Utility
    // -------------------------------------------------------------------------

    private DateRange resolveRange(String from, String to) {
        if ((from == null || from.isBlank()) && (to == null || to.isBlank())) {
            YearMonth now = YearMonth.now();
            return new DateRange(now.atDay(1).toString(), now.atEndOfMonth().toString());
        }
        LocalDate start = (from != null && !from.isBlank()) ? LocalDate.parse(from.trim()) : LocalDate.parse(to.trim());
        LocalDate end = (to != null && !to.isBlank()) ? LocalDate.parse(to.trim()) : start;
        if (start.isAfter(end)) { LocalDate tmp = start; start = end; end = tmp; }
        return new DateRange(start.toString(), end.toString());
    }

    private LocalDate parseDate(Object value) {
        String text = asString(value).trim();
        if (text.isBlank()) return null;
        try { return LocalDate.parse(text); } catch (Exception e) { return null; }
    }

    private String asString(Object value) { return value == null ? "" : String.valueOf(value); }

    private double asDouble(Object value) {
        if (value instanceof Number n) return n.doubleValue();
        try { return value == null ? 0.0 : Double.parseDouble(value.toString()); } catch (NumberFormatException e) { return 0.0; }
    }

    private boolean asFlag(Object value) {
        if (value == null) return false;
        if (value instanceof Boolean b) return b;
        if (value instanceof Number n) return n.intValue() != 0;
        String text = value.toString().trim().toLowerCase();
        return "1".equals(text) || "true".equals(text) || "yes".equals(text);
    }

    private double round(double value) { return Math.round(value * 100.0) / 100.0; }

    private String toJson(List<List<String>> filters) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < filters.size(); i++) {
            List<String> entry = filters.get(i);
            sb.append("[");
            for (int j = 0; j < entry.size(); j++) {
                sb.append("\"").append(entry.get(j).replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
                if (j < entry.size() - 1) sb.append(",");
            }
            sb.append("]");
            if (i < filters.size() - 1) sb.append(",");
        }
        return sb.append("]").toString();
    }

    // -------------------------------------------------------------------------
    // Inner types
    // -------------------------------------------------------------------------

    private record DateRange(String start, String end) {}

    private static class AggRow {
        final Map<String, Object> dims = new LinkedHashMap<>();
        double revenue = 0;
        double cost = 0;
        final Set<String> orderNames = new HashSet<>();
    }

    private enum Grouping {
        DAY {
            @Override PeriodKey periodFor(LocalDate date) { String d = date.toString(); return new PeriodKey(d, d); }
        },
        WEEK {
            @Override PeriodKey periodFor(LocalDate date) {
                LocalDate start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                return new PeriodKey(start.toString(), start.plusDays(6).toString());
            }
        },
        MONTH {
            @Override PeriodKey periodFor(LocalDate date) {
                YearMonth ym = YearMonth.from(date);
                return new PeriodKey(ym.atDay(1).toString(), ym.atEndOfMonth().toString());
            }
        },
        QUARTER {
            @Override PeriodKey periodFor(LocalDate date) {
                int startMonth = ((date.getMonthValue() - 1) / 3) * 3 + 1;
                LocalDate start = LocalDate.of(date.getYear(), startMonth, 1);
                return new PeriodKey(start.toString(), start.plusMonths(2).with(TemporalAdjusters.lastDayOfMonth()).toString());
            }
        };

        abstract PeriodKey periodFor(LocalDate date);

        static Grouping parse(String raw) {
            return switch (raw == null ? "" : raw.trim().toLowerCase()) {
                case "week", "weekly" -> WEEK;
                case "month", "monthly" -> MONTH;
                case "quarter", "quarterly" -> QUARTER;
                default -> DAY;
            };
        }
    }

    private record PeriodKey(String start, String end) {}
}
