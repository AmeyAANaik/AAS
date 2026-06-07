package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import java.time.LocalDate;
import java.time.DayOfWeek;
import java.time.temporal.TemporalAdjusters;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.LinkedHashSet;
import org.springframework.stereotype.Service;

@Service
public class ReportService {

    private final ErpNextClient erpNextClient;

    public ReportService(ErpNextClient erpNextClient) {
        this.erpNextClient = erpNextClient;
    }

    public List<Map<String, Object>> vendorOrdersByShop(String vendor, String month) {
        List<Map<String, Object>> orders = fetchSalesOrders(month);
        Map<String, OrderCost> costMap = computeOrderCosts(orders);
        Map<String, Map<String, Object>> aggregated = new HashMap<>();
        for (Map<String, Object> order : orders) {
            String orderVendor = asString(order.get("aas_vendor"));
            if (vendor != null && !vendor.isBlank() && !vendor.equals(orderVendor)) {
                continue;
            }
            String customer = asString(order.get("customer"));
            String key = orderVendor + "::" + customer;
            Map<String, Object> entry = aggregated.computeIfAbsent(key, k -> {
                Map<String, Object> map = new HashMap<>();
                map.put("vendor", orderVendor);
                map.put("shop", customer);
                map.put("orders", 0);
                map.put("total", 0.0);
                map.put("cost_total", 0.0);
                map.put("margin_total", 0.0);
                return map;
            });
            entry.put("orders", ((Integer) entry.get("orders")) + 1);
            entry.put("total", ((Double) entry.get("total")) + asDouble(order.get("grand_total")));
            OrderCost cost = costMap.getOrDefault(asString(order.get("name")), OrderCost.empty());
            entry.put("cost_total", ((Double) entry.get("cost_total")) + cost.costTotal());
            entry.put("margin_total", ((Double) entry.get("margin_total")) + cost.marginTotal());
        }
        return new ArrayList<>(aggregated.values());
    }

    public List<Map<String, Object>> vendorBilling(String vendor, String month) {
        List<Map<String, Object>> orders = fetchSalesOrders(month);
        Map<String, OrderCost> costMap = computeOrderCosts(orders);
        Map<String, Double> totals = new HashMap<>();
        Map<String, Double> costTotals = new HashMap<>();
        Map<String, Double> marginTotals = new HashMap<>();
        for (Map<String, Object> order : orders) {
            String orderVendor = asString(order.get("aas_vendor"));
            if (vendor != null && !vendor.isBlank() && !vendor.equals(orderVendor)) {
                continue;
            }
            totals.put(orderVendor, totals.getOrDefault(orderVendor, 0.0) + asDouble(order.get("grand_total")));
            OrderCost cost = costMap.getOrDefault(asString(order.get("name")), OrderCost.empty());
            costTotals.put(orderVendor, costTotals.getOrDefault(orderVendor, 0.0) + cost.costTotal());
            marginTotals.put(orderVendor, marginTotals.getOrDefault(orderVendor, 0.0) + cost.marginTotal());
        }
        return totals.entrySet().stream().map(entry -> {
            Map<String, Object> map = new HashMap<>();
            map.put("vendor", entry.getKey());
            map.put("total", entry.getValue());
            map.put("cost_total", round(costTotals.getOrDefault(entry.getKey(), 0.0)));
            map.put("margin_total", round(marginTotals.getOrDefault(entry.getKey(), 0.0)));
            return map;
        }).toList();
    }

    public List<Map<String, Object>> vendorPayments(String vendor, String month) {
        return paymentSummary("Supplier", "party", vendor, month);
    }

    public List<Map<String, Object>> shopBilling(String customer, String month) {
        List<Map<String, Object>> orders = fetchSalesOrders(month);
        Map<String, OrderCost> costMap = computeOrderCosts(orders);
        Map<String, Double> totals = new HashMap<>();
        Map<String, Double> costTotals = new HashMap<>();
        Map<String, Double> marginTotals = new HashMap<>();
        for (Map<String, Object> order : orders) {
            String shop = asString(order.get("customer"));
            if (customer != null && !customer.isBlank() && !customer.equals(shop)) {
                continue;
            }
            totals.put(shop, totals.getOrDefault(shop, 0.0) + asDouble(order.get("grand_total")));
            OrderCost cost = costMap.getOrDefault(asString(order.get("name")), OrderCost.empty());
            costTotals.put(shop, costTotals.getOrDefault(shop, 0.0) + cost.costTotal());
            marginTotals.put(shop, marginTotals.getOrDefault(shop, 0.0) + cost.marginTotal());
        }
        return totals.entrySet().stream().map(entry -> {
            Map<String, Object> map = new HashMap<>();
            map.put("shop", entry.getKey());
            map.put("total", entry.getValue());
            map.put("cost_total", round(costTotals.getOrDefault(entry.getKey(), 0.0)));
            map.put("margin_total", round(marginTotals.getOrDefault(entry.getKey(), 0.0)));
            return map;
        }).toList();
    }

    public List<Map<String, Object>> shopPayments(String customer, String month) {
        return paymentSummary("Customer", "party", customer, month);
    }

    public List<Map<String, Object>> shopCategory(String customer, String month) {
        List<Map<String, Object>> orders = fetchSalesOrders(month);
        Map<String, Double> categoryTotals = new HashMap<>();
        Map<String, Double> categoryCosts = new HashMap<>();
        Map<String, Double> categoryMargins = new HashMap<>();
        for (Map<String, Object> order : orders) {
            String shop = asString(order.get("customer"));
            if (customer != null && !customer.isBlank() && !customer.equals(shop)) {
                continue;
            }
            String name = asString(order.get("name"));
            Map<String, Object> full = unwrapResource(erpNextClient.getResource("Sales Order", name));
            Object items = full.get("items");
            if (items instanceof List<?> list) {
                for (Object itemObj : list) {
                    if (itemObj instanceof Map<?, ?> item) {
                        String group = asString(item.get("item_group"));
                        double amount = asDouble(item.get("amount"));
                        double qty = asDouble(item.get("qty"));
                        double vendorRate = asDouble(item.get("aas_vendor_rate"));
                        double cost = (vendorRate > 0 && qty > 0) ? vendorRate * qty : 0.0;
                        categoryTotals.put(group, categoryTotals.getOrDefault(group, 0.0) + amount);
                        categoryCosts.put(group, categoryCosts.getOrDefault(group, 0.0) + cost);
                        categoryMargins.put(group, categoryMargins.getOrDefault(group, 0.0) + (amount - cost));
                    }
                }
            }
        }
        return categoryTotals.entrySet().stream().map(entry -> {
            Map<String, Object> map = new HashMap<>();
            map.put("category", entry.getKey());
            map.put("total", entry.getValue());
            map.put("cost_total", round(categoryCosts.getOrDefault(entry.getKey(), 0.0)));
            map.put("margin_total", round(categoryMargins.getOrDefault(entry.getKey(), 0.0)));
            return map;
        }).toList();
    }

    public List<Map<String, Object>> itemPriceTrend(String from, String to) {
        return itemPriceTrend(from, to, null);
    }

    public List<Map<String, Object>> itemPriceTrend(String from, String to, String compareBy) {
        DateRange range = resolveDateRange(from, to);
        return applyComparison(
                range,
                compareBy,
                itemPriceTrend(range),
                this::itemPriceTrend,
                ComparisonKeyStrategy.DIMENSION);
    }

    private List<Map<String, Object>> itemPriceTrend(DateRange range) {
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"item_code\",\"item_name\",\"item_group\",\"price_list_rate\",\"valid_from\",\"currency\"]");
        params.put("order_by", "valid_from asc");
        List<List<String>> filters = new ArrayList<>();
        filters.add(List.of("price_list", "=", "Standard Selling"));
        filters.add(List.of("valid_from", ">=", range.start()));
        filters.add(List.of("valid_from", "<=", range.end()));
        params.put("filters", toJson(filters));
        List<Map<String, Object>> prices = erpNextClient.listResources("Item Price", params);

        // Group by item_code, keep only first (prev) and last (curr) entry per item
        Map<String, Map<String, Object>> firstPrice = new java.util.LinkedHashMap<>();
        Map<String, Map<String, Object>> lastPrice = new java.util.LinkedHashMap<>();
        for (Map<String, Object> entry : prices) {
            String code = asString(entry.get("item_code"));
            if (code.isBlank()) continue;
            firstPrice.putIfAbsent(code, entry);
            lastPrice.put(code, entry);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (String code : lastPrice.keySet()) {
            Map<String, Object> first = firstPrice.get(code);
            Map<String, Object> last = lastPrice.get(code);
            double prevPrice = asDouble(first.get("price_list_rate"));
            double currPrice = asDouble(last.get("price_list_rate"));
            double change = round(currPrice - prevPrice);
            double changePct = prevPrice > 0 ? round((change / prevPrice) * 100.0) : 0.0;
            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("item_code", code);
            row.put("item_name", asString(last.get("item_name")));
            row.put("category", asString(last.get("item_group")));
            row.put("prev_price", prevPrice);
            row.put("curr_price", currPrice);
            row.put("price_change", change);
            row.put("change_pct", changePct);
            row.put("currency", asString(last.get("currency")));
            result.add(row);
        }
        // Sort by absolute change descending so biggest movers appear first
        result.sort((a, b) -> Double.compare(
                Math.abs(asDouble(b.get("price_change"))),
                Math.abs(asDouble(a.get("price_change")))));
        return result;
    }

    public List<Map<String, Object>> itemPriceFunnel(String from, String to) {
        return itemPriceFunnel(from, to, null);
    }

    public List<Map<String, Object>> itemPriceFunnel(String from, String to, String compareBy) {
        DateRange range = resolveDateRange(from, to);
        return applyComparison(
                range,
                compareBy,
                itemPriceFunnel(range),
                this::itemPriceFunnel,
                ComparisonKeyStrategy.DIMENSION);
    }

    private List<Map<String, Object>> itemPriceFunnel(DateRange range) {
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"item_code\",\"item_name\",\"item_group\",\"price_list_rate\",\"valid_from\",\"currency\"]");
        params.put("order_by", "valid_from asc");
        List<List<String>> filters = new ArrayList<>();
        filters.add(List.of("price_list", "=", "Standard Selling"));
        filters.add(List.of("valid_from", ">=", range.start()));
        filters.add(List.of("valid_from", "<=", range.end()));
        params.put("filters", toJson(filters));
        List<Map<String, Object>> prices = erpNextClient.listResources("Item Price", params);

        Map<String, Map<String, Object>> latestPriceByItem = new LinkedHashMap<>();
        for (Map<String, Object> entry : prices) {
            String code = asString(entry.get("item_code")).trim();
            if (code.isBlank()) {
                continue;
            }
            latestPriceByItem.put(code, entry);
        }

        Map<String, PriceBandAggregate> bucketMap = new LinkedHashMap<>();
        for (String band : List.of("₹0–50", "₹51–100", "₹101–200", "₹201–500", "₹500+")) {
            bucketMap.put(band, new PriceBandAggregate());
        }

        for (Map<String, Object> entry : latestPriceByItem.values()) {
            double price = asDouble(entry.get("price_list_rate"));
            PriceBandAggregate aggregate = bucketMap.get(resolvePriceBand(price));
            if (aggregate == null) {
                continue;
            }
            aggregate.itemCount += 1;
            aggregate.totalPrice += price;
            aggregate.minPrice = aggregate.itemCount == 1 ? price : Math.min(aggregate.minPrice, price);
            aggregate.maxPrice = aggregate.itemCount == 1 ? price : Math.max(aggregate.maxPrice, price);
        }

        int totalItems = latestPriceByItem.size();
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map.Entry<String, PriceBandAggregate> entry : bucketMap.entrySet()) {
            PriceBandAggregate aggregate = entry.getValue();
            if (aggregate.itemCount == 0) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("price_band", entry.getKey());
            row.put("item_count", aggregate.itemCount);
            row.put("share_pct", totalItems > 0 ? round((aggregate.itemCount * 100.0) / totalItems) : 0.0);
            row.put("avg_price", round(aggregate.totalPrice / aggregate.itemCount));
            row.put("min_price", round(aggregate.minPrice));
            row.put("max_price", round(aggregate.maxPrice));
            result.add(row);
        }
        return result;
    }

    public List<Map<String, Object>> companyBranchSalesProfit(String from, String to) {
        return companyBranchSalesProfit(from, to, null);
    }

    public List<Map<String, Object>> companyBranchSalesProfit(String from, String to, String compareBy) {
        DateRange range = resolveDateRange(from, to);
        return applyComparison(
                range,
                compareBy,
                companyBranchSalesProfit(range),
                this::companyBranchSalesProfit,
                ComparisonKeyStrategy.DIMENSION);
    }

    private List<Map<String, Object>> companyBranchSalesProfit(DateRange range) {
        List<Map<String, Object>> orders = fetchSalesOrders(range);
        Map<String, OrderCost> costMap = computeOrderCosts(orders);
        Map<String, BranchSalesProfit> aggregated = new HashMap<>();
        for (Map<String, Object> order : orders) {
            String shop = asString(order.get("customer"));
            if (shop.isBlank()) {
                continue;
            }
            BranchSalesProfit entry = aggregated.computeIfAbsent(shop, ignored -> new BranchSalesProfit());
            entry.orders += 1;
            entry.salesTotal += asDouble(order.get("grand_total"));
            OrderCost cost = costMap.getOrDefault(asString(order.get("name")), OrderCost.empty());
            entry.costTotal += cost.costTotal();
        }
        return aggregated.entrySet().stream()
                .map(e -> {
                    BranchSalesProfit entry = e.getValue();
                    double profitTotal = entry.salesTotal - entry.costTotal;
                    double profitPercent = entry.salesTotal > 0 ? (profitTotal / entry.salesTotal) * 100.0 : 0.0;
                    Map<String, Object> map = new HashMap<>();
                    map.put("shop", e.getKey());
                    map.put("orders", entry.orders);
                    map.put("sales_total", round(entry.salesTotal));
                    map.put("cost_total", round(entry.costTotal));
                    map.put("profit_total", round(profitTotal));
                    map.put("profit_percent", round(profitPercent));
                    return map;
                })
                .sorted((a, b) -> Double.compare(asDouble(b.get("sales_total")), asDouble(a.get("sales_total"))))
                .toList();
    }

    public List<Map<String, Object>> companyOverallSalesProfit(String from, String to) {
        return companyOverallSalesProfit(from, to, null);
    }

    public List<Map<String, Object>> companyOverallSalesProfit(String from, String to, String compareBy) {
        DateRange range = resolveDateRange(from, to);
        return applyComparison(
                range,
                compareBy,
                companyOverallSalesProfit(range),
                this::companyOverallSalesProfit,
                ComparisonKeyStrategy.DIMENSION);
    }

    private List<Map<String, Object>> companyOverallSalesProfit(DateRange range) {
        List<Map<String, Object>> orders = fetchSalesOrders(range);
        Map<String, OrderCost> costMap = computeOrderCosts(orders);
        int ordersCount = 0;
        double salesTotal = 0.0;
        double costTotal = 0.0;
        for (Map<String, Object> order : orders) {
            ordersCount += 1;
            salesTotal += asDouble(order.get("grand_total"));
            OrderCost cost = costMap.getOrDefault(asString(order.get("name")), OrderCost.empty());
            costTotal += cost.costTotal();
        }
        double profitTotal = salesTotal - costTotal;
        double profitPercent = salesTotal > 0 ? (profitTotal / salesTotal) * 100.0 : 0.0;
        Map<String, Object> row = new HashMap<>();
        row.put("sales_total", round(salesTotal));
        row.put("cost_total", round(costTotal));
        row.put("profit_total", round(profitTotal));
        row.put("profit_percent", round(profitPercent));
        row.put("orders", ordersCount);
        return List.of(row);
    }

    public List<Map<String, Object>> companySupplierExpenses(String from, String to) {
        return companySupplierExpenses(from, to, null);
    }

    public List<Map<String, Object>> companySupplierExpenses(String from, String to, String compareBy) {
        DateRange range = resolveDateRange(from, to);
        return applyComparison(
                range,
                compareBy,
                companySupplierExpenses(range),
                this::companySupplierExpenses,
                ComparisonKeyStrategy.DIMENSION);
    }

    private List<Map<String, Object>> companySupplierExpenses(DateRange range) {
        Map<String, Double> totals = paymentSummaryRange("Supplier", range);
        return totals.entrySet().stream()
                .map(e -> Map.<String, Object>of("vendor", e.getKey(), "paid_total", round(e.getValue())))
                .sorted((a, b) -> Double.compare(asDouble(b.get("paid_total")), asDouble(a.get("paid_total"))))
                .toList();
    }

    public List<Map<String, Object>> companyBranchIncome(String from, String to) {
        return companyBranchIncome(from, to, null);
    }

    public List<Map<String, Object>> companyBranchIncome(String from, String to, String compareBy) {
        DateRange range = resolveDateRange(from, to);
        return applyComparison(
                range,
                compareBy,
                companyBranchIncome(range),
                this::companyBranchIncome,
                ComparisonKeyStrategy.DIMENSION);
    }

    private List<Map<String, Object>> companyBranchIncome(DateRange range) {
        Map<String, Double> totals = paymentSummaryRange("Customer", range);
        return totals.entrySet().stream()
                .map(e -> Map.<String, Object>of("shop", e.getKey(), "paid_total", round(e.getValue())))
                .sorted((a, b) -> Double.compare(asDouble(b.get("paid_total")), asDouble(a.get("paid_total"))))
                .toList();
    }

    public List<Map<String, Object>> companySalesProfitTrend(String from, String to, String groupBy) {
        return companySalesProfitTrend(from, to, groupBy, null);
    }

    public List<Map<String, Object>> companySalesProfitTrend(String from, String to, String groupBy, String compareBy) {
        DateRange range = resolveDateRange(from, to);
        return applyComparison(
                range,
                compareBy,
                companySalesProfitTrend(range, groupBy),
                previousRange -> companySalesProfitTrend(previousRange, groupBy),
                ComparisonKeyStrategy.INDEX);
    }

    private List<Map<String, Object>> companySalesProfitTrend(DateRange range, String groupBy) {
        TrendGroupBy grouping = TrendGroupBy.parse(groupBy);
        List<Map<String, Object>> orders = fetchSalesOrders(range);
        Map<String, OrderCost> costMap = computeOrderCosts(orders);

        Map<PeriodKey, BranchSalesProfit> aggregated = new HashMap<>();
        for (Map<String, Object> order : orders) {
            LocalDate txDate = parseDate(order.get("transaction_date"));
            if (txDate == null) {
                continue;
            }
            PeriodKey period = grouping.periodFor(txDate);
            BranchSalesProfit entry = aggregated.computeIfAbsent(period, ignored -> new BranchSalesProfit());
            entry.orders += 1;
            entry.salesTotal += asDouble(order.get("grand_total"));
            OrderCost cost = costMap.getOrDefault(asString(order.get("name")), OrderCost.empty());
            entry.costTotal += cost.costTotal();
        }

        return aggregated.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .map(e -> {
                    PeriodKey period = e.getKey();
                    BranchSalesProfit entry = e.getValue();
                    double profitTotal = entry.salesTotal - entry.costTotal;
                    double profitPercent = entry.salesTotal > 0 ? (profitTotal / entry.salesTotal) * 100.0 : 0.0;
                    Map<String, Object> row = new HashMap<>();
                    row.put("period_start", period.start());
                    row.put("period_end", period.end());
                    row.put("orders", entry.orders);
                    row.put("sales_total", round(entry.salesTotal));
                    row.put("cost_total", round(entry.costTotal));
                    row.put("profit_total", round(profitTotal));
                    row.put("profit_percent", round(profitPercent));
                    return row;
                })
                .toList();
    }

    private List<Map<String, Object>> applyComparison(
            DateRange currentRange,
            String compareBy,
            List<Map<String, Object>> currentRows,
            RangeReportRunner previousRunner,
            ComparisonKeyStrategy keyStrategy) {
        ComparisonMode mode = ComparisonMode.parse(compareBy);
        if (mode == ComparisonMode.NONE || currentRows.isEmpty()) {
            return currentRows;
        }
        DateRange previousRange = mode.shift(currentRange);
        List<Map<String, Object>> previousRows = previousRunner.run(previousRange);
        return mergeComparisonRows(currentRows, previousRows, previousRange, mode, keyStrategy);
    }

    private List<Map<String, Object>> mergeComparisonRows(
            List<Map<String, Object>> currentRows,
            List<Map<String, Object>> previousRows,
            DateRange previousRange,
            ComparisonMode mode,
            ComparisonKeyStrategy keyStrategy) {
        if (currentRows.isEmpty()) {
            return currentRows;
        }
        List<String> numericColumns = numericColumns(currentRows, previousRows);
        List<String> dimensionColumns = dimensionColumns(currentRows, previousRows, numericColumns);
        Map<String, Map<String, Object>> previousByKey = new LinkedHashMap<>();
        if (keyStrategy == ComparisonKeyStrategy.DIMENSION) {
            for (Map<String, Object> previousRow : previousRows) {
                previousByKey.put(comparisonKey(previousRow, dimensionColumns), previousRow);
            }
        }
        List<Map<String, Object>> merged = new ArrayList<>();
        for (int index = 0; index < currentRows.size(); index++) {
            Map<String, Object> currentRow = currentRows.get(index);
            Map<String, Object> previousRow = keyStrategy == ComparisonKeyStrategy.INDEX
                    ? (index < previousRows.size() ? previousRows.get(index) : Map.of())
                    : previousByKey.getOrDefault(comparisonKey(currentRow, dimensionColumns), Map.of());
            Map<String, Object> row = new LinkedHashMap<>(currentRow);
            row.put("compare_by", mode.apiValue);
            row.put("compare_period_start", previousRange.start());
            row.put("compare_period_end", previousRange.end());
            if (currentRow.containsKey("period_start")) {
                row.put("previous_period_start", previousRow.getOrDefault("period_start", ""));
            }
            if (currentRow.containsKey("period_end")) {
                row.put("previous_period_end", previousRow.getOrDefault("period_end", ""));
            }
            for (String numericColumn : numericColumns) {
                double currentValue = asDouble(currentRow.get(numericColumn));
                double previousValue = asDouble(previousRow.get(numericColumn));
                double change = round(currentValue - previousValue);
                row.put("previous_" + numericColumn, round(previousValue));
                row.put("change_" + numericColumn, change);
                row.put("change_pct_" + numericColumn, previousValue != 0.0 ? round((change / previousValue) * 100.0) : null);
            }
            merged.add(row);
        }
        return merged;
    }

    private List<String> numericColumns(List<Map<String, Object>> currentRows, List<Map<String, Object>> previousRows) {
        Set<String> keys = new LinkedHashSet<>();
        currentRows.forEach(row -> keys.addAll(row.keySet()));
        previousRows.forEach(row -> keys.addAll(row.keySet()));
        List<String> numericColumns = new ArrayList<>();
        for (String key : keys) {
            if (hasNumericValue(currentRows, key) || hasNumericValue(previousRows, key)) {
                numericColumns.add(key);
            }
        }
        return numericColumns;
    }

    private List<String> dimensionColumns(
            List<Map<String, Object>> currentRows,
            List<Map<String, Object>> previousRows,
            List<String> numericColumns) {
        Set<String> numeric = Set.copyOf(numericColumns);
        Set<String> keys = new LinkedHashSet<>();
        currentRows.forEach(row -> keys.addAll(row.keySet()));
        previousRows.forEach(row -> keys.addAll(row.keySet()));
        List<String> dimensions = new ArrayList<>();
        for (String key : keys) {
            if (!numeric.contains(key)) {
                dimensions.add(key);
            }
        }
        return dimensions;
    }

    private boolean hasNumericValue(List<Map<String, Object>> rows, String key) {
        for (Map<String, Object> row : rows) {
            Object value = row.get(key);
            if (value instanceof Number) {
                return true;
            }
        }
        return false;
    }

    private String comparisonKey(Map<String, Object> row, List<String> dimensionColumns) {
        if (dimensionColumns.isEmpty()) {
            return "__overall__";
        }
        StringBuilder builder = new StringBuilder();
        for (String key : dimensionColumns) {
            builder.append(key).append('=').append(asString(row.get(key))).append('|');
        }
        return builder.toString();
    }

    DateRange resolveDateRange(String from, String to) {
        LocalDate start;
        LocalDate end;
        if ((from == null || from.isBlank()) && (to == null || to.isBlank())) {
            YearMonth now = YearMonth.now();
            start = now.atDay(1);
            end = now.atEndOfMonth();
            return new DateRange(start.toString(), end.toString());
        }
        if (from == null || from.isBlank()) {
            start = LocalDate.parse(to.trim());
            end = start;
        } else if (to == null || to.isBlank()) {
            start = LocalDate.parse(from.trim());
            end = start;
        } else {
            start = LocalDate.parse(from.trim());
            end = LocalDate.parse(to.trim());
        }
        if (start.isAfter(end)) {
            LocalDate tmp = start;
            start = end;
            end = tmp;
        }
        return new DateRange(start.toString(), end.toString());
    }

    private List<Map<String, Object>> paymentSummary(String partyType, String partyField, String party, String month) {
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"party\",\"paid_amount\",\"posting_date\"]");
        params.put("order_by", "posting_date desc");
        List<List<String>> filters = new ArrayList<>();
        filters.add(List.of("party_type", "=", partyType));
        if (party != null && !party.isBlank()) {
            filters.add(List.of(partyField, "=", party));
        }
        DateRange range = dateRange(month);
        filters.add(List.of("posting_date", ">=", range.start()));
        filters.add(List.of("posting_date", "<=", range.end()));
        params.put("filters", toJson(filters));
        List<Map<String, Object>> entries = erpNextClient.listResources("Payment Entry", params);
        Map<String, Double> totals = new HashMap<>();
        for (Map<String, Object> entry : entries) {
            String partyName = asString(entry.get("party"));
            totals.put(partyName, totals.getOrDefault(partyName, 0.0) + asDouble(entry.get("paid_amount")));
        }
        return totals.entrySet().stream().map(e -> {
            Map<String, Object> map = new HashMap<>();
            map.put(partyType.equals("Customer") ? "shop" : "vendor", e.getKey());
            map.put("total", e.getValue());
            return map;
        }).toList();
    }

    private List<Map<String, Object>> fetchSalesOrders(String month) {
        return fetchSalesOrders(dateRange(month));
    }

    private List<Map<String, Object>> fetchSalesOrders(DateRange range) {
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\",\"customer\",\"company\",\"transaction_date\",\"aas_vendor\",\"aas_status\",\"aas_is_deleted\",\"status\",\"grand_total\"]");
        params.put("order_by", "transaction_date desc");
        List<List<String>> filters = new ArrayList<>();
        filters.add(List.of("transaction_date", ">=", range.start()));
        filters.add(List.of("transaction_date", "<=", range.end()));
        params.put("filters", toJson(filters));
        return erpNextClient.listResources("Sales Order", params).stream()
                .filter(order -> !asFlag(order.get("aas_is_deleted")) && !"DELETED".equalsIgnoreCase(asString(order.get("aas_status"))))
                .toList();
    }

    private boolean asFlag(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (value instanceof Number number) {
            return number.intValue() != 0;
        }
        String text = value.toString().trim().toLowerCase();
        return "1".equals(text) || "true".equals(text) || "yes".equals(text);
    }

    private Map<String, OrderCost> computeOrderCosts(List<Map<String, Object>> orders) {
        Map<String, OrderCost> costMap = new HashMap<>();
        for (Map<String, Object> order : orders) {
            String name = asString(order.get("name"));
            if (name.isBlank()) {
                continue;
            }
            Map<String, Object> full = unwrapResource(erpNextClient.getResource("Sales Order", name));
            costMap.put(name, computeOrderCost(full));
        }
        return costMap;
    }

    private OrderCost computeOrderCost(Map<String, Object> order) {
        if (order == null) {
            return OrderCost.empty();
        }
        order = unwrapResource(order);
        double costTotal = 0.0;
        double sellTotal = 0.0;
        Object items = order.get("items");
        if (items instanceof List<?> list) {
            for (Object itemObj : list) {
                if (itemObj instanceof Map<?, ?> item) {
                    double qty = asDouble(item.get("qty"));
                    double rate = asDouble(item.get("rate"));
                    double amount = asDouble(item.get("amount"));
                    double vendorRate = asDouble(item.get("aas_vendor_rate"));
                    if (amount == 0 && qty > 0) {
                        amount = rate * qty;
                    }
                    sellTotal += amount;
                    if (vendorRate > 0 && qty > 0) {
                        costTotal += vendorRate * qty;
                    }
                }
            }
        }
        double marginTotal = sellTotal - costTotal;
        double marginPercent = costTotal > 0 ? (marginTotal / costTotal) * 100.0 : 0.0;
        return new OrderCost(round(costTotal), round(marginTotal), round(marginPercent));
    }

    private Map<String, Object> unwrapResource(Map<String, Object> resource) {
        if (resource == null) {
            return Map.of();
        }
        Object data = resource.get("data");
        if (data instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> unwrapped = (Map<String, Object>) map;
            return unwrapped;
        }
        return resource;
    }

    private DateRange dateRange(String month) {
        YearMonth ym;
        if (month == null || month.isBlank()) {
            ym = YearMonth.now();
        } else {
            ym = YearMonth.parse(month);
        }
        LocalDate start = ym.atDay(1);
        LocalDate end = ym.atEndOfMonth();
        return new DateRange(start.toString(), end.toString());
    }

    private Map<String, Double> paymentSummaryRange(String partyType, DateRange range) {
        Objects.requireNonNull(range, "range");
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"party\",\"paid_amount\",\"posting_date\",\"party_type\"]");
        params.put("order_by", "posting_date desc");
        List<List<String>> filters = new ArrayList<>();
        filters.add(List.of("party_type", "=", partyType));
        filters.add(List.of("posting_date", ">=", range.start()));
        filters.add(List.of("posting_date", "<=", range.end()));
        params.put("filters", toJson(filters));
        List<Map<String, Object>> entries = erpNextClient.listResources("Payment Entry", params);
        Map<String, Double> totals = new HashMap<>();
        for (Map<String, Object> entry : entries) {
            String party = asString(entry.get("party"));
            if (party.isBlank()) {
                continue;
            }
            totals.put(party, totals.getOrDefault(party, 0.0) + asDouble(entry.get("paid_amount")));
        }
        return totals;
    }

    private LocalDate parseDate(Object value) {
        String text = asString(value).trim();
        if (text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String toJson(List<List<String>> filters) {
        StringBuilder builder = new StringBuilder("[");
        for (int i = 0; i < filters.size(); i++) {
            List<String> entry = filters.get(i);
            builder.append("[");
            for (int j = 0; j < entry.size(); j++) {
                builder.append("\"").append(escape(entry.get(j))).append("\"");
                if (j < entry.size() - 1) {
                    builder.append(",");
                }
            }
            builder.append("]");
            if (i < filters.size() - 1) {
                builder.append(",");
            }
        }
        builder.append("]");
        return builder.toString();
    }

    private String escape(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? 0.0 : Double.parseDouble(value.toString());
        } catch (NumberFormatException ex) {
            return 0.0;
        }
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private String resolvePriceBand(double price) {
        if (price <= 50) {
            return "₹0–50";
        }
        if (price <= 100) {
            return "₹51–100";
        }
        if (price <= 200) {
            return "₹101–200";
        }
        if (price <= 500) {
            return "₹201–500";
        }
        return "₹500+";
    }

    record DateRange(String start, String end) {
    }

    private record OrderCost(double costTotal, double marginTotal, double marginPercent) {
        static OrderCost empty() {
            return new OrderCost(0.0, 0.0, 0.0);
        }
    }

    private static final class BranchSalesProfit {
        int orders;
        double salesTotal;
        double costTotal;
    }

    private static final class PriceBandAggregate {
        int itemCount;
        double totalPrice;
        double minPrice;
        double maxPrice;
    }

    @FunctionalInterface
    private interface RangeReportRunner {
        List<Map<String, Object>> run(DateRange range);
    }

    private enum ComparisonKeyStrategy {
        DIMENSION,
        INDEX
    }

    private enum ComparisonMode {
        NONE("none") {
            @Override
            DateRange shift(DateRange range) {
                return range;
            }
        },
        WOW("wow") {
            @Override
            DateRange shift(DateRange range) {
                return shiftDays(range, 7);
            }
        },
        MOM("mom") {
            @Override
            DateRange shift(DateRange range) {
                return shiftMonths(range, 1);
            }
        },
        QOQ("qoq") {
            @Override
            DateRange shift(DateRange range) {
                return shiftMonths(range, 3);
            }
        };

        private final String apiValue;

        ComparisonMode(String apiValue) {
            this.apiValue = apiValue;
        }

        abstract DateRange shift(DateRange range);

        static ComparisonMode parse(String raw) {
            String key = raw == null ? "" : raw.trim().toLowerCase();
            return switch (key) {
                case "wow", "week", "weekly" -> WOW;
                case "mom", "month", "monthly" -> MOM;
                case "qoq", "quarter", "quarterly" -> QOQ;
                default -> NONE;
            };
        }

        private static DateRange shiftDays(DateRange range, int days) {
            LocalDate start = LocalDate.parse(range.start()).minusDays(days);
            LocalDate end = LocalDate.parse(range.end()).minusDays(days);
            return new DateRange(start.toString(), end.toString());
        }

        private static DateRange shiftMonths(DateRange range, int months) {
            LocalDate start = LocalDate.parse(range.start()).minusMonths(months);
            LocalDate end = LocalDate.parse(range.end()).minusMonths(months);
            return new DateRange(start.toString(), end.toString());
        }
    }

    private enum TrendGroupBy {
        DAY {
            @Override
            PeriodKey periodFor(LocalDate date) {
                String key = date.toString();
                return new PeriodKey(key, key);
            }
        },
        WEEK {
            @Override
            PeriodKey periodFor(LocalDate date) {
                LocalDate start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                LocalDate end = start.plusDays(6);
                return new PeriodKey(start.toString(), end.toString());
            }
        },
        MONTH {
            @Override
            PeriodKey periodFor(LocalDate date) {
                YearMonth ym = YearMonth.from(date);
                LocalDate start = ym.atDay(1);
                LocalDate end = ym.atEndOfMonth();
                return new PeriodKey(start.toString(), end.toString());
            }
        },
        QUARTER {
            @Override
            PeriodKey periodFor(LocalDate date) {
                int quarterStartMonth = ((date.getMonthValue() - 1) / 3) * 3 + 1;
                LocalDate start = LocalDate.of(date.getYear(), quarterStartMonth, 1);
                LocalDate end = start.plusMonths(2).with(TemporalAdjusters.lastDayOfMonth());
                return new PeriodKey(start.toString(), end.toString());
            }
        };

        abstract PeriodKey periodFor(LocalDate date);

        static TrendGroupBy parse(String raw) {
            String key = raw == null ? "" : raw.trim().toLowerCase();
            return switch (key) {
                case "week", "weekly" -> WEEK;
                case "month", "monthly" -> MONTH;
                case "quarter", "quarterly", "qoq" -> QUARTER;
                case "day", "daily", "" -> DAY;
                default -> DAY;
            };
        }
    }

    private record PeriodKey(String start, String end) implements Comparable<PeriodKey> {
        @Override
        public int compareTo(PeriodKey other) {
            if (other == null) {
                return 1;
            }
            int byStart = start.compareTo(other.start);
            if (byStart != 0) {
                return byStart;
            }
            return end.compareTo(other.end);
        }
    }
}
