package com.aas.mw.controller;

import com.aas.mw.dto.AnalyticsColumn;
import com.aas.mw.dto.AnalyticsQueryRequest;
import com.aas.mw.dto.AnalyticsQueryResponse;
import com.aas.mw.service.AnalyticsService;
import com.aas.mw.util.CsvUtil;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/analytics")
public class AnalyticsController {

    private final AnalyticsService analyticsService;

    public AnalyticsController(AnalyticsService analyticsService) {
        this.analyticsService = analyticsService;
    }

    @PostMapping("/query")
    public ResponseEntity<AnalyticsQueryResponse> query(@RequestBody AnalyticsQueryRequest req) {
        return ResponseEntity.ok(analyticsService.query(req));
    }

    @PostMapping("/query/export")
    public ResponseEntity<String> export(@RequestBody AnalyticsQueryRequest req) {
        AnalyticsQueryResponse result = analyticsService.query(req);
        return csvResponse(result, "analytics-export.csv");
    }

    @PostMapping("/item-price-history")
    public ResponseEntity<AnalyticsQueryResponse> itemPriceHistory(@RequestBody AnalyticsQueryRequest req) {
        return ResponseEntity.ok(analyticsService.itemPriceHistory(req));
    }

    @PostMapping("/item-price-history/export")
    public ResponseEntity<String> exportItemPriceHistory(@RequestBody AnalyticsQueryRequest req) {
        AnalyticsQueryResponse result = analyticsService.itemPriceHistory(req);
        return csvResponse(result, "analytics-item-price-history.csv");
    }

    private ResponseEntity<String> csvResponse(AnalyticsQueryResponse result, String fileName) {
        List<AnalyticsColumn> columns = result.getColumns();
        List<Map<String, Object>> csvRows = new ArrayList<>();
        for (Map<String, Object> row : result.getRows()) {
            Map<String, Object> csvRow = new LinkedHashMap<>();
            for (AnalyticsColumn col : columns) csvRow.put(col.label(), row.getOrDefault(col.id(), ""));
            csvRows.add(csvRow);
        }
        if (!result.getTotalsRow().isEmpty()) {
            Map<String, Object> totals = new LinkedHashMap<>();
            for (AnalyticsColumn col : columns) totals.put(col.label(), result.getTotalsRow().getOrDefault(col.id(), ""));
            csvRows.add(totals);
        }
        String csv = CsvUtil.toCsv(csvRows);
        HttpHeaders httpHeaders = new HttpHeaders();
        httpHeaders.setContentType(MediaType.parseMediaType("text/csv"));
        httpHeaders.setContentDispositionFormData("attachment", fileName);
        return ResponseEntity.ok().headers(httpHeaders).body(csv);
    }
}
