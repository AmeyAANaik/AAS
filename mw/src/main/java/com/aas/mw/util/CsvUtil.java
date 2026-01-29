package com.aas.mw.util;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class CsvUtil {

    private CsvUtil() {
    }

    public static String toCsv(List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) {
            return "";
        }
        Set<String> headers = new LinkedHashSet<>();
        for (Map<String, Object> row : rows) {
            if (row != null) {
                headers.addAll(row.keySet());
            }
        }
        List<String> ordered = new ArrayList<>(headers);
        StringBuilder builder = new StringBuilder();
        builder.append(String.join(",", ordered)).append("\n");
        for (Map<String, Object> row : rows) {
            for (int i = 0; i < ordered.size(); i++) {
                String key = ordered.get(i);
                Object value = row == null ? null : row.get(key);
                builder.append(escape(value));
                if (i < ordered.size() - 1) {
                    builder.append(",");
                }
            }
            builder.append("\n");
        }
        return builder.toString();
    }

    private static String escape(Object value) {
        if (value == null) {
            return "";
        }
        String text = String.valueOf(value);
        boolean needsQuotes = text.contains(",") || text.contains("\"") || text.contains("\n") || text.contains("\r");
        if (!needsQuotes) {
            return text;
        }
        return "\"" + text.replace("\"", "\"\"") + "\"";
    }
}
