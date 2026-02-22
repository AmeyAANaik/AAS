package com.aas.mw.meta;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.vendor")
public class VendorFieldsProperties {

    private List<VendorFieldSpec> fields = new ArrayList<>();

    public List<VendorFieldSpec> getFields() {
        return fields;
    }

    public void setFields(List<VendorFieldSpec> fields) {
        this.fields = fields == null ? new ArrayList<>() : new ArrayList<>(fields);
    }
}

