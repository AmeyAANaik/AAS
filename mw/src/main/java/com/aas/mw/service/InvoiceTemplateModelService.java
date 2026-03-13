package com.aas.mw.service;

import com.aas.mw.config.InvoiceTemplateModelProperties;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class InvoiceTemplateModelService {

    private final InvoiceTemplateModelProperties properties;

    public InvoiceTemplateModelService(InvoiceTemplateModelProperties properties) {
        this.properties = properties;
    }

    public Map<String, Object> describeModel() {
        return Map.of(
                "itemFields", toFieldMaps(properties.getItemFields()),
                "summaryFields", toFieldMaps(properties.getSummaryFields()));
    }

    public List<String> requiredItemKeys() {
        return properties.getItemFields().stream()
                .filter(InvoiceTemplateModelProperties.TemplateField::isRequired)
                .map(InvoiceTemplateModelProperties.TemplateField::getKey)
                .toList();
    }

    public List<String> requiredSummaryKeys() {
        return properties.getSummaryFields().stream()
                .filter(InvoiceTemplateModelProperties.TemplateField::isRequired)
                .map(InvoiceTemplateModelProperties.TemplateField::getKey)
                .toList();
    }

    private List<Map<String, Object>> toFieldMaps(List<InvoiceTemplateModelProperties.TemplateField> fields) {
        return fields.stream()
                .map(field -> Map.<String, Object>of(
                        "key", field.getKey(),
                        "label", field.getLabel(),
                        "required", field.isRequired(),
                        "sourceAliases", field.getSourceAliases()))
                .toList();
    }
}
