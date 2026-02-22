package com.aas.mw.meta;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class VendorFieldMapper {

    private final List<VendorFieldSpec> specs;
    private final Map<String, VendorFieldSpec> byKey;
    private final Map<String, VendorFieldSpec> byFieldname;

    public VendorFieldMapper(List<VendorFieldSpec> specs) {
        this.specs = specs == null ? List.of() : List.copyOf(specs);
        Map<String, VendorFieldSpec> keyIndex = new HashMap<>();
        Map<String, VendorFieldSpec> fieldIndex = new HashMap<>();
        for (VendorFieldSpec spec : this.specs) {
            if (spec == null) {
                continue;
            }
            if (spec.key() != null && !spec.key().isBlank()) {
                keyIndex.put(spec.key(), spec);
            }
            if (spec.fieldname() != null && !spec.fieldname().isBlank()) {
                fieldIndex.put(spec.fieldname(), spec);
            }
        }
        this.byKey = Map.copyOf(keyIndex);
        this.byFieldname = Map.copyOf(fieldIndex);
    }

    public Map<String, Object> toErpPayload(Map<String, Object> apiFields) {
        if (apiFields == null || apiFields.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> out = new HashMap<>();
        for (Map.Entry<String, Object> entry : apiFields.entrySet()) {
            String key = entry.getKey();
            if (key == null || key.isBlank()) {
                continue;
            }
            Object value = entry.getValue();
            VendorFieldSpec spec = byKey.get(key);
            if (spec != null) {
                out.put(spec.fieldname(), value);
                continue;
            }
            // Allow callers to send ERP fieldnames directly.
            VendorFieldSpec byErpName = byFieldname.get(key);
            if (byErpName != null) {
                out.put(byErpName.fieldname(), value);
            }
        }
        return out;
    }

    public Map<String, Object> withApiAliases(Map<String, Object> erpVendor) {
        if (erpVendor == null || erpVendor.isEmpty()) {
            return erpVendor;
        }
        Map<String, Object> out = new HashMap<>(erpVendor);
        for (VendorFieldSpec spec : specs) {
            if (spec == null || spec.key() == null || spec.key().isBlank()) {
                continue;
            }
            Object value = out.get(spec.fieldname());
            if (value != null || out.containsKey(spec.fieldname())) {
                out.put(spec.key(), value);
            }
        }
        return out;
    }

    public String erpListFieldsJson() {
        // Frappe expects JSON array string for "fields".
        Set<String> fields = new LinkedHashSet<>();
        fields.add("name");
        fields.add("supplier_name");
        fields.add("disabled");
        for (VendorFieldSpec spec : specs) {
            if (spec == null) {
                continue;
            }
            String fieldname = spec.fieldname();
            if (fieldname != null && !fieldname.isBlank()) {
                fields.add(fieldname);
            }
        }
        StringBuilder sb = new StringBuilder();
        sb.append('[');
        boolean first = true;
        for (String field : fields) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append('"').append(field.replace("\"", "\\\"")).append('"');
        }
        sb.append(']');
        return sb.toString();
    }
}

