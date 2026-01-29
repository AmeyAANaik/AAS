package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
            Map<String, Object> full = erpNextClient.getResource("Sales Order", name);
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
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\",\"customer\",\"company\",\"transaction_date\",\"aas_vendor\",\"aas_status\",\"status\",\"grand_total\"]");
        params.put("order_by", "transaction_date desc");
        DateRange range = dateRange(month);
        List<List<String>> filters = new ArrayList<>();
        filters.add(List.of("transaction_date", ">=", range.start()));
        filters.add(List.of("transaction_date", "<=", range.end()));
        params.put("filters", toJson(filters));
        return erpNextClient.listResources("Sales Order", params);
    }

    private Map<String, OrderCost> computeOrderCosts(List<Map<String, Object>> orders) {
        Map<String, OrderCost> costMap = new HashMap<>();
        for (Map<String, Object> order : orders) {
            String name = asString(order.get("name"));
            if (name.isBlank()) {
                continue;
            }
            Map<String, Object> full = erpNextClient.getResource("Sales Order", name);
            costMap.put(name, computeOrderCost(full));
        }
        return costMap;
    }

    private OrderCost computeOrderCost(Map<String, Object> order) {
        if (order == null) {
            return OrderCost.empty();
        }
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

    private record DateRange(String start, String end) {
    }

    private record OrderCost(double costTotal, double marginTotal, double marginPercent) {
        static OrderCost empty() {
            return new OrderCost(0.0, 0.0, 0.0);
        }
    }
}
