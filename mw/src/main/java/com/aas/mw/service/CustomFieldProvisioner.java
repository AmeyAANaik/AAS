package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class CustomFieldProvisioner {

    private final ErpNextClient erpNextClient;

    public CustomFieldProvisioner(ErpNextClient erpNextClient) {
        this.erpNextClient = erpNextClient;
    }

    public boolean ensure(
            String dt,
            String fieldname,
            String label,
            String fieldtype,
            String options,
            String insertAfter,
            boolean inListView,
            boolean required) {
        Map<String, Object> existing = findCustomField(dt, fieldname);
        if (existing != null) {
            boolean changed = false;
            if (options != null && !options.isBlank()) {
                String current = String.valueOf(existing.getOrDefault("options", ""));
                if (!options.equals(current)) {
                    String name = String.valueOf(existing.get("name"));
                    erpNextClient.updateResource("Custom Field", name, Map.of("options", options));
                    changed = true;
                }
            }
            return changed;
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("dt", dt);
        payload.put("fieldname", fieldname);
        payload.put("label", label);
        payload.put("fieldtype", fieldtype);
        if (options != null && !options.isBlank()) {
            payload.put("options", options);
        }
        if (insertAfter != null && !insertAfter.isBlank()) {
            payload.put("insert_after", insertAfter);
        }
        payload.put("in_list_view", inListView ? 1 : 0);
        payload.put("reqd", required ? 1 : 0);
        erpNextClient.createResource("Custom Field", payload);
        return true;
    }

    private Map<String, Object> findCustomField(String dt, String fieldname) {
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\",\"options\"]");
        params.put("limit_page_length", "1");
        params.put("filters", "[[\"dt\",\"=\",\"" + dt + "\"],[\"fieldname\",\"=\",\"" + fieldname + "\"]]");
        List<Map<String, Object>> data = erpNextClient.listResources("Custom Field", params);
        return data.isEmpty() ? null : data.get(0);
    }
}

