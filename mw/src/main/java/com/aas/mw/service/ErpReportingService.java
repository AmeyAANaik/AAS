package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.config.ReportingProperties;
import com.aas.mw.dto.reporting.ReportingColumn;
import com.aas.mw.dto.reporting.ReportingRunResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class ErpReportingService {

    private static final String ERP_METHOD = "frappe.desk.query_report.run";

    private final ErpNextClient erpNextClient;
    private final ObjectMapper objectMapper;
    private final ReportingProperties properties;

    public ErpReportingService(ErpNextClient erpNextClient, ObjectMapper objectMapper, ReportingProperties properties) {
        this.erpNextClient = erpNextClient;
        this.objectMapper = objectMapper;
        this.properties = properties;
    }

    public ReportingRunResponse runReport(String reportName, Map<String, Object> filters) {
        int maxRows = Math.max(1, properties.getMaxRows());
        ReportingRunResponse response = execute(reportName, filters);
        if (response.getRows().size() <= maxRows) {
            return response;
        }
        List<Map<String, Object>> truncated = response.getRows().subList(0, maxRows);
        return new ReportingRunResponse(
                response.getColumns(),
                new ArrayList<>(truncated),
                response.getTotals(),
                response.getChart(),
                true);
    }

    public ReportingRunResponse runReportUnbounded(String reportName, Map<String, Object> filters) {
        return execute(reportName, filters);
    }

    private ReportingRunResponse execute(String reportName, Map<String, Object> filters) {
        Map<String, Object> params = new HashMap<>();
        params.put("report_name", reportName);
        params.put("filters", encodeFilters(filters));
        params.put("ignore_prepared_report", "1");
        params.put("are_default_filters", "0");
        params.put("with_chart", "1");
        params.put("with_report_summary", "1");
        Map<String, Object> raw = erpNextClient.getMethod(ERP_METHOD, params);
        Map<String, Object> message = unwrapMessage(raw);

        List<ReportingColumn> columns = normalizeColumns(message.get("columns"));
        List<Map<String, Object>> rows = normalizeRows(message.get("result"), columns);
        Map<String, Object> totals = castMap(message.get("report_summary"));
        Map<String, Object> chart = castMap(message.get("chart"));

        return new ReportingRunResponse(columns, rows, totals, chart, false);
    }

    private String encodeFilters(Map<String, Object> filters) {
        if (filters == null || filters.isEmpty()) {
            return "{}";
        }
        try {
            return objectMapper.writeValueAsString(filters);
        } catch (Exception ex) {
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrapMessage(Map<String, Object> response) {
        if (response == null) {
            return Map.of();
        }
        Object message = response.get("message");
        if (message instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return response;
    }

    private List<ReportingColumn> normalizeColumns(Object rawColumns) {
        if (!(rawColumns instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<ReportingColumn> columns = new ArrayList<>();
        int i = 0;
        for (Object entry : list) {
            if (entry instanceof Map<?, ?> map) {
                String id = asText(map.get("fieldname"));
                String label = asText(map.get("label"));
                if (id.isBlank()) {
                    id = "c" + i;
                }
                if (label.isBlank()) {
                    label = id;
                }
                columns.add(new ReportingColumn(id, label));
            } else if (entry != null) {
                String label = parseLabelFromColumnString(entry.toString());
                String id = "c" + i;
                columns.add(new ReportingColumn(id, label.isBlank() ? id : label));
            }
            i++;
        }
        return List.copyOf(columns);
    }

    private String parseLabelFromColumnString(String value) {
        if (value == null) {
            return "";
        }
        String trimmed = value.trim();
        if (trimmed.isBlank()) {
            return "";
        }
        int idx = trimmed.indexOf(':');
        if (idx > 0) {
            return trimmed.substring(0, idx).trim();
        }
        return trimmed;
    }

    private List<Map<String, Object>> normalizeRows(Object rawRows, List<ReportingColumn> columns) {
        if (!(rawRows instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        if (list.get(0) instanceof Map<?, ?>) {
            return normalizeMapRows(list, columns);
        }
        if (list.get(0) instanceof List<?>) {
            return normalizeListRows(list, columns);
        }
        return List.of();
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> normalizeMapRows(List<?> list, List<ReportingColumn> columns) {
        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> order = columns.stream().map(ReportingColumn::getId).toList();
        for (Object rowObj : list) {
            if (!(rowObj instanceof Map<?, ?> map)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            if (!order.isEmpty()) {
                for (String key : order) {
                    if (((Map<?, ?>) map).containsKey(key)) {
                        row.put(key, ((Map<?, ?>) map).get(key));
                    }
                }
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) map).entrySet()) {
                    if (entry.getKey() == null) {
                        continue;
                    }
                    String key = entry.getKey().toString();
                    if (row.containsKey(key)) {
                        continue;
                    }
                    row.put(key, entry.getValue());
                }
            } else {
                ((Map<?, ?>) map).forEach((k, v) -> {
                    if (k != null) {
                        row.put(k.toString(), v);
                    }
                });
            }
            rows.add(row);
        }
        return List.copyOf(rows);
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> normalizeListRows(List<?> list, List<ReportingColumn> columns) {
        List<Map<String, Object>> rows = new ArrayList<>();
        List<ReportingColumn> safeColumns = columns == null ? List.of() : columns;
        for (Object rowObj : list) {
            if (!(rowObj instanceof List<?> rowList)) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            for (int i = 0; i < rowList.size(); i++) {
                String key = i < safeColumns.size() ? safeColumns.get(i).getId() : "c" + i;
                row.put(key, rowList.get(i));
            }
            rows.add(row);
        }
        return List.copyOf(rows);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> castMap(Object value) {
        if (value instanceof Map<?, ?> map) {
            Map<String, Object> casted = new LinkedHashMap<>();
            map.forEach((k, v) -> {
                if (k != null) {
                    casted.put(k.toString(), v);
                }
            });
            return casted;
        }
        if (value instanceof List<?> list) {
            // Some ERP endpoints return report_summary as a list of objects. Preserve under a consistent key.
            return Map.of("items", list);
        }
        return Map.of();
    }

    private String asText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }
}
