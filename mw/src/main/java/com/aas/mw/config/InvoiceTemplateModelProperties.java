package com.aas.mw.config;

import java.util.ArrayList;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.invoice-template")
public class InvoiceTemplateModelProperties {

    private List<TemplateField> itemFields = defaultItemFields();
    private List<TemplateField> summaryFields = defaultSummaryFields();

    public List<TemplateField> getItemFields() {
        return itemFields;
    }

    public void setItemFields(List<TemplateField> itemFields) {
        this.itemFields = itemFields == null || itemFields.isEmpty() ? defaultItemFields() : copy(itemFields);
    }

    public List<TemplateField> getSummaryFields() {
        return summaryFields;
    }

    public void setSummaryFields(List<TemplateField> summaryFields) {
        this.summaryFields = summaryFields == null || summaryFields.isEmpty() ? defaultSummaryFields() : copy(summaryFields);
    }

    private List<TemplateField> defaultItemFields() {
        List<TemplateField> fields = new ArrayList<>();
        fields.add(new TemplateField("item_name", "Item Name", true, List.of("name", "description", "item")));
        fields.add(new TemplateField("item_id", "Item No / HSN", true, List.of("item_no", "item_code", "hsn", "hsn_code", "id")));
        fields.add(new TemplateField("qty", "Quantity", true, List.of("quantity")));
        fields.add(new TemplateField("rate", "Rate", true, List.of("unit_rate", "rate_after_tax")));
        fields.add(new TemplateField("gst", "GST / Tax %", true, List.of("tax", "tax_percent", "gst_percent")));
        fields.add(new TemplateField("total", "Line Total", true, List.of("amount", "line_total", "value_after_tax", "total_value_after_tax")));
        return fields;
    }

    private List<TemplateField> defaultSummaryFields() {
        List<TemplateField> fields = new ArrayList<>();
        fields.add(new TemplateField("final_bill_amount", "Final Bill Amount", true, List.of("grand_total", "invoice_total", "net_amount", "bill_amount")));
        return fields;
    }

    private List<TemplateField> copy(List<TemplateField> source) {
        return source.stream()
                .map(field -> new TemplateField(field.getKey(), field.getLabel(), field.isRequired(), field.getSourceAliases()))
                .toList();
    }

    public static class TemplateField {
        private String key;
        private String label;
        private boolean required;
        private List<String> sourceAliases = new ArrayList<>();

        public TemplateField() {
        }

        public TemplateField(String key, String label, boolean required, List<String> sourceAliases) {
            this.key = key;
            this.label = label;
            this.required = required;
            this.sourceAliases = sourceAliases == null ? new ArrayList<>() : new ArrayList<>(sourceAliases);
        }

        public String getKey() {
            return key;
        }

        public void setKey(String key) {
            this.key = key;
        }

        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        public boolean isRequired() {
            return required;
        }

        public void setRequired(boolean required) {
            this.required = required;
        }

        public List<String> getSourceAliases() {
            return sourceAliases;
        }

        public void setSourceAliases(List<String> sourceAliases) {
            this.sourceAliases = sourceAliases == null ? new ArrayList<>() : new ArrayList<>(sourceAliases);
        }
    }
}
