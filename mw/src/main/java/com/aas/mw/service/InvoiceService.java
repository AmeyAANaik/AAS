package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.dto.InvoiceRequest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class InvoiceService {

    private static final String DOCTYPE = "Sales Invoice";

    private final ErpNextClient erpNextClient;

    public InvoiceService(ErpNextClient erpNextClient) {
        this.erpNextClient = erpNextClient;
    }

    public Map<String, Object> createInvoice(InvoiceRequest request) {
        return erpNextClient.createResource(DOCTYPE, request.getFields());
    }

    public List<Map<String, Object>> listInvoices(String customer, String fromDate, String toDate) {
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\",\"customer\",\"posting_date\",\"grand_total\",\"status\"]");
        params.put("order_by", "posting_date desc");
        List<List<String>> filters = new ArrayList<>();
        if (customer != null && !customer.isBlank()) {
            filters.add(List.of("customer", "=", customer));
        }
        if (fromDate != null && !fromDate.isBlank()) {
            filters.add(List.of("posting_date", ">=", fromDate));
        }
        if (toDate != null && !toDate.isBlank()) {
            filters.add(List.of("posting_date", "<=", toDate));
        }
        if (!filters.isEmpty()) {
            params.put("filters", toJson(filters));
        }
        return erpNextClient.listResources(DOCTYPE, params);
    }

    public byte[] downloadPdf(String invoiceId) {
        return erpNextClient.downloadPdf(DOCTYPE, invoiceId);
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
