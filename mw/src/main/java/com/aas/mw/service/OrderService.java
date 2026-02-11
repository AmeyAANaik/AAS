package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.dto.OrderRequest;
import java.util.ArrayList;
import java.util.Map;
import java.util.HashMap;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private static final String DOCTYPE = "Sales Order";

    private final ErpNextClient erpNextClient;
    private final ErpNextFileService erpNextFileService;

    public OrderService(ErpNextClient erpNextClient, ErpNextFileService erpNextFileService) {
        this.erpNextClient = erpNextClient;
        this.erpNextFileService = erpNextFileService;
    }

    public Map<String, Object> createOrder(OrderRequest request) {
        return erpNextClient.createResource(DOCTYPE, request.getFields());
    }

    public Map<String, Object> getOrder(String id) {
        return erpNextClient.getResource(DOCTYPE, id);
    }

    public Map<String, Object> updateOrder(String id, OrderRequest request) {
        return erpNextClient.updateResource(DOCTYPE, id, request.getFields());
    }

    public Map<String, Object> updateOrderFields(String id, Map<String, Object> fields) {
        return erpNextClient.updateResource(DOCTYPE, id, fields);
    }

    public Map<String, Object> attachOrderImage(String orderId, org.springframework.web.multipart.MultipartFile file, String sessionCookie) {
        return erpNextFileService.uploadOrderImage(orderId, file, sessionCookie);
    }

    public List<Map<String, Object>> listOrders(Map<String, String> filters) {
        Map<String, Object> params = new HashMap<>();
        params.put("fields",
                "[\"name\",\"customer\",\"company\",\"transaction_date\",\"delivery_date\",\"aas_vendor\",\"aas_status\",\"status\",\"grand_total\"]");
        params.put("order_by", "transaction_date desc");
        if (!filters.isEmpty()) {
            List<List<String>> filterList = new ArrayList<>();
            filters.forEach((key, value) -> {
                if ("from".equals(key)) {
                    filterList.add(List.of("transaction_date", ">=", value));
                } else if ("to".equals(key)) {
                    filterList.add(List.of("transaction_date", "<=", value));
                } else {
                    filterList.add(List.of(key, "=", value));
                }
            });
            params.put("filters", toJson(filterList));
        }
        List<Map<String, Object>> orders = erpNextClient.listResources(DOCTYPE, params);
        addOrderCostMetrics(orders);
        return orders;
    }

    private void addOrderCostMetrics(List<Map<String, Object>> orders) {
        if (orders == null || orders.isEmpty()) {
            return;
        }
        for (Map<String, Object> order : orders) {
            String name = order == null ? null : String.valueOf(order.get("name"));
            if (name == null || name.isBlank()) {
                continue;
            }
            Map<String, Object> full = erpNextClient.getResource(DOCTYPE, name);
            OrderCost cost = computeOrderCost(full);
            order.put("aas_cost_total", cost.costTotal());
            order.put("aas_margin_total", cost.marginTotal());
            order.put("aas_margin_percent", cost.marginPercent());
        }
    }

    private OrderCost computeOrderCost(Map<String, Object> order) {
        if (order == null) {
            return new OrderCost(0.0, 0.0, 0.0);
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

    private record OrderCost(double costTotal, double marginTotal, double marginPercent) {
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
}
