package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import java.math.BigDecimal;
import com.aas.mw.dto.InvoiceRequest;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class InvoiceService {

    private static final String DOCTYPE = "Sales Invoice";

    private final ErpNextClient erpNextClient;
    private final String gstTemplate;

    public InvoiceService(
            ErpNextClient erpNextClient,
            @Value("${app.billing.gst-template:}") String gstTemplate) {
        this.erpNextClient = erpNextClient;
        this.gstTemplate = gstTemplate == null ? "" : gstTemplate.trim();
    }

    public Map<String, Object> createInvoice(InvoiceRequest request) {
        validateFields(request);
        Map<String, Object> payload = new HashMap<>(request.getFields());
        boolean applyGst = readBoolean(payload.remove("apply_gst"), true);
        if (payload.containsKey("applyGst")) {
            applyGst = readBoolean(payload.remove("applyGst"), applyGst);
        }
        normalizeRounding(payload);
        if (applyGst) {
            applyItemTaxTemplates(payload);
        }
        if (applyGst) {
            if (!gstTemplate.isBlank()
                    && !payload.containsKey("taxes_and_charges")
                    && !payload.containsKey("taxes")) {
                payload.put("taxes_and_charges", gstTemplate);
            }
        } else {
            payload.remove("taxes_and_charges");
            payload.remove("taxes");
            payload.remove("tax_category");
        }

        String customer = asText(payload.get("customer"));
        int creditDays = resolveCreditDays(customer);
        if (creditDays > 0 && !payload.containsKey("due_date")) {
            LocalDate base = resolveBaseDate(payload.get("posting_date"));
            payload.put("due_date", base.plusDays(creditDays).toString());
        }

        String company = asText(payload.get("company"));
        if (!company.isBlank() && !hasValue(payload.get("currency"))) {
            String currency = resolveCompanyCurrency(company);
            if (!currency.isBlank()) {
                payload.put("currency", currency);
            }
        }

        return erpNextClient.createResource(DOCTYPE, payload);
    }

    private void normalizeRounding(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return;
        }
        double roundingAdjustment = asDouble(payload.remove("rounding_adjustment"));
        if (Math.abs(roundingAdjustment) <= 0.0001) {
            roundingAdjustment = asDouble(payload.get("aas_rounding_adjustment"));
        }
        if (Math.abs(roundingAdjustment) <= 0.0001) {
            payload.remove("aas_rounding_adjustment");
            return;
        }
        payload.put("aas_rounding_adjustment", roundingAdjustment);
        payload.put("rounding_adjustment", roundingAdjustment);
        payload.putIfAbsent("disable_rounded_total", 0);
    }

    private void applyItemTaxTemplates(Map<String, Object> payload) {
        if (payload == null || payload.isEmpty()) {
            return;
        }
        String company = asText(payload.get("company"));
        if (company.isBlank()) {
            return;
        }
        Object rawItems = payload.get("items");
        if (!(rawItems instanceof List<?> rawList) || rawList.isEmpty()) {
            return;
        }
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) rawItems;
        String gstAccountHead = ensureGstAccountHead(company);
        if (gstAccountHead.isBlank()) {
            return;
        }
        boolean applied = false;
        for (Map<String, Object> item : items) {
            if (item == null) {
                continue;
            }
            double gstPercent = readGstPercent(item);
            if (gstPercent <= 0) {
                continue;
            }
            item.put("aas_gst_percent", gstPercent);
            item.put("item_tax_template", ensureItemTaxTemplate(company, gstAccountHead, gstPercent));
            applied = true;
        }
        if (applied) {
            payload.remove("taxes_and_charges");
            payload.remove("taxes");
        }
    }

    private double readGstPercent(Map<String, Object> item) {
        double gstPercent = asDouble(item.get("aas_gst_percent"));
        if (gstPercent <= 0) {
            gstPercent = asDouble(item.get("gst_percent"));
        }
        if (gstPercent <= 0) {
            gstPercent = asDouble(item.get("gst"));
        }
        return gstPercent;
    }

    private String ensureGstAccountHead(String company) {
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\",\"account_name\",\"company\",\"account_type\",\"parent_account\",\"root_type\",\"report_type\"]");
        params.put("filters", "[[\"company\",\"=\",\"" + escape(company) + "\"]]");
        params.put("limit_page_length", 200);
        List<Map<String, Object>> accounts = erpNextClient.listResources("Account", params);
        if (accounts != null) {
            for (Map<String, Object> account : accounts) {
                if (account == null) {
                    continue;
                }
                if (company.equals(asText(account.get("company")))
                        && "Tax".equalsIgnoreCase(asText(account.get("account_type")))
                        && "GST".equalsIgnoreCase(asText(account.get("account_name")))) {
                    return asText(account.get("name"));
                }
            }
            for (Map<String, Object> account : accounts) {
                if (account == null) {
                    continue;
                }
                if ("Duties and Taxes".equalsIgnoreCase(asText(account.get("account_name")))
                        && company.equals(asText(account.get("company")))) {
                    Map<String, Object> payload = new HashMap<>();
                    payload.put("account_name", "GST");
                    payload.put("company", company);
                    payload.put("parent_account", asText(account.get("name")));
                    payload.put("account_type", "Tax");
                    payload.put("is_group", 0);
                    if (!asText(account.get("root_type")).isBlank()) {
                        payload.put("root_type", asText(account.get("root_type")));
                    }
                    if (!asText(account.get("report_type")).isBlank()) {
                        payload.put("report_type", asText(account.get("report_type")));
                    }
                    return asText(unwrap(erpNextClient.createResource("Account", payload)).get("name"));
                }
            }
        }
        return "";
    }

    private String ensureItemTaxTemplate(String company, String gstAccountHead, double gstPercent) {
        String title = buildGstTemplateTitle(gstPercent);
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\",\"title\",\"company\"]");
        params.put(
                "filters",
                "[[\"company\",\"=\",\"" + escape(company) + "\"],[\"title\",\"=\",\"" + escape(title) + "\"]]");
        params.put("limit_page_length", 20);
        List<Map<String, Object>> templates = erpNextClient.listResources("Item Tax Template", params);
        if (templates != null) {
            for (Map<String, Object> template : templates) {
                if (template == null) {
                    continue;
                }
                if (company.equals(asText(template.get("company"))) && title.equals(asText(template.get("title")))) {
                    return asText(template.get("name"));
                }
            }
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("title", title);
        payload.put("company", company);
        payload.put("taxes", List.of(Map.of(
                "tax_type", gstAccountHead,
                "tax_rate", round(gstPercent))));
        return asText(unwrap(erpNextClient.createResource("Item Tax Template", payload)).get("name"));
    }

    private String buildGstTemplateTitle(double gstPercent) {
        return "AAS GST " + BigDecimal.valueOf(round(gstPercent)).stripTrailingZeros().toPlainString() + "%";
    }

    private String escape(String value) {
        return value == null ? "" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public List<Map<String, Object>> listInvoices(String customer, String fromDate, String toDate) {
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\",\"customer\",\"company\",\"posting_date\",\"grand_total\",\"outstanding_amount\",\"status\"]");
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
        String printFormat = resolveInvoicePrintFormat(invoiceId);
        byte[] pdf = printFormat.isBlank()
                ? erpNextClient.downloadPdf(DOCTYPE, invoiceId)
                : erpNextClient.downloadPdf(DOCTYPE, invoiceId, Map.of("format", printFormat));
        // Guard: ERPNext may return an HTML/JSON error payload (still 200) if print format fails.
        if (pdf == null || pdf.length < 4 || pdf[0] != '%' || pdf[1] != 'P' || pdf[2] != 'D' || pdf[3] != 'F') {
            String snippet = pdf == null ? "" : new String(pdf, 0, Math.min(pdf.length, 240));
            throw new IllegalStateException("ERPNext did not return a valid PDF for " + invoiceId + ". " + snippet);
        }
        return pdf;
    }

    private String resolveInvoicePrintFormat(String invoiceId) {
        if (invoiceId == null || invoiceId.isBlank()) {
            return "";
        }
        try {
            Map<String, Object> invoice = erpNextClient.getResource(DOCTYPE, invoiceId);
            Map<String, Object> invoiceDoc = unwrap(invoice);
            String company = asText(invoiceDoc.get("company"));
            if (company.isBlank()) {
                return "";
            }
            Map<String, Object> companyResp = erpNextClient.getResource("Company", company);
            Map<String, Object> companyDoc = unwrap(companyResp);
            return asText(companyDoc.get("aas_sales_invoice_print_format"));
        } catch (Exception ex) {
            // Never block downloads due to missing optional configuration.
            return "";
        }
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

    private void validateFields(InvoiceRequest request) {
        Map<String, Object> fields = request == null ? null : request.getFields();
        if (fields == null || fields.isEmpty()) {
            throw new IllegalArgumentException("Invoice fields are required.");
        }
        Object customer = fields.get("customer");
        Object company = fields.get("company");
        Object items = fields.get("items");
        if (customer == null || customer.toString().isBlank()) {
            throw new IllegalArgumentException("Invoice customer is required.");
        }
        if (company == null || company.toString().isBlank()) {
            throw new IllegalArgumentException("Invoice company is required.");
        }
        if (!(items instanceof List<?> list) || list.isEmpty()) {
            throw new IllegalArgumentException("Invoice items are required.");
        }
    }

    private int resolveCreditDays(String customer) {
        if (customer == null || customer.isBlank()) {
            return 0;
        }
        Map<String, Object> response = erpNextClient.getResource("Customer", customer);
        Map<String, Object> customerDoc = unwrap(response);
        int creditDays = asInt(customerDoc.get("aas_credit_days"));
        if (creditDays <= 0) {
            creditDays = asInt(customerDoc.get("credit_days"));
        }
        return Math.max(creditDays, 0);
    }

    private String resolveCompanyCurrency(String company) {
        Map<String, Object> response = erpNextClient.getResource("Company", company);
        Map<String, Object> companyDoc = unwrap(response);
        return asText(companyDoc.get("default_currency"));
    }

    private LocalDate resolveBaseDate(Object value) {
        if (value == null) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(value.toString());
        } catch (DateTimeParseException ex) {
            return LocalDate.now();
        }
    }

    private boolean readBoolean(Object value, boolean fallback) {
        if (value == null) {
            return fallback;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        String raw = value.toString().trim();
        if (raw.isEmpty()) {
            return fallback;
        }
        return raw.equalsIgnoreCase("true") || raw.equalsIgnoreCase("1") || raw.equalsIgnoreCase("yes");
    }

    private int asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return value == null ? 0 : Integer.parseInt(value.toString());
        } catch (Exception ex) {
            return 0;
        }
    }

    private String asText(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    private double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? 0.0 : Double.parseDouble(value.toString());
        } catch (Exception ex) {
            return 0.0;
        }
    }

    private boolean hasValue(Object value) {
        return value != null && !value.toString().isBlank();
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrap(Map<String, Object> response) {
        if (response == null) {
            return Map.of();
        }
        Object data = response.get("data");
        if (data instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return response;
    }
}
