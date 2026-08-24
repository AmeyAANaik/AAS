package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.dto.AnalyticsColumn;
import com.aas.mw.dto.AnalyticsKpi;
import com.aas.mw.dto.AnalyticsQueryRequest;
import com.aas.mw.dto.AnalyticsQueryResponse;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class AnalyticsService {

    private static final List<String> VALID_DIMS = List.of("date", "vendor", "branch", "item_group", "item");
    private static final List<String> VALID_METRICS = List.of("revenue", "cost", "profit", "profit_ex_gst", "margin_pct", "margin_pct_ex_gst", "orders", "avg_order_value", "quantity");
    private static final int BATCH_CHUNK_SIZE = 50;
    private static final int HSN_WARNING_EXAMPLE_LIMIT = 5;

    private final ErpNextClient erpNextClient;
    private final InvoiceService invoiceService;

    public AnalyticsService(ErpNextClient erpNextClient, InvoiceService invoiceService) {
        this.erpNextClient = erpNextClient;
        this.invoiceService = invoiceService;
    }

    public AnalyticsQueryResponse query(AnalyticsQueryRequest req) {
        DateRange range = resolveRange(req.getDateFrom(), req.getDateTo());
        List<String> dims = normalizeDims(req.getDimensions());
        List<String> mets = normalizeMetrics(req.getMetrics());
        String granularity = req.getGranularity() != null ? req.getGranularity().trim().toLowerCase(Locale.ROOT) : "day";
        Map<String, String> filters = normalizeFilters(req.getFilters());

        List<String> warnings = new ArrayList<>();
        List<FactRow> facts = loadInvoiceFacts(range, dims, mets, filters, warnings);
        Map<String, AggRow> agg = aggregateFacts(facts, dims, granularity);

        List<Map<String, Object>> rows = buildRows(agg, dims, mets, granularity);
        rows = sortRows(rows, dims);

        List<AnalyticsColumn> columns = buildColumns(dims, mets, granularity);
        Map<String, Object> totalsRow = buildTotals(rows, dims, mets);
        List<AnalyticsKpi> kpis = buildKpis(totalsRow, mets);

        return new AnalyticsQueryResponse(columns, rows, totalsRow, kpis, warnings);
    }

    public AnalyticsQueryResponse itemPriceHistory(AnalyticsQueryRequest req) {
        DateRange range = resolveRange(req.getDateFrom(), req.getDateTo());
        Map<String, String> filters = normalizeFilters(req.getFilters());

        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"item_code\",\"item_name\",\"price_list_rate\",\"valid_from\",\"currency\"]");
        params.put("order_by", "valid_from asc");
        // 0 = no limit; otherwise ERPNext caps the price history at its default 20 rows.
        params.put("limit_page_length", "0");
        params.put("filters", toJson(List.of(
                List.of("price_list", "=", "Standard Selling"),
                List.of("valid_from", ">=", range.start()),
                List.of("valid_from", "<=", range.end()))));

        List<Map<String, Object>> prices = erpNextClient.listResources("Item Price", params);
        Set<String> itemCodes = prices.stream()
                .map(row -> asString(row.get("item_code")).trim())
                .filter(code -> !code.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, ItemMeta> itemMeta = batchFetchItemMeta(itemCodes);

        List<Map<String, Object>> rows = new ArrayList<>();
        Set<String> uniqueItems = new HashSet<>();
        double totalPrice = 0.0;
        for (Map<String, Object> row : prices) {
            String itemCode = asString(row.get("item_code")).trim();
            String itemName = asString(row.get("item_name")).trim();
            ItemMeta meta = itemMeta.getOrDefault(itemCode, ItemMeta.empty());
            if (!matchesFilter(filters.get("vendor"), meta.vendor())) {
                continue;
            }
            if (!matchesFilter(filters.get("itemGroup"), meta.itemGroup())) {
                continue;
            }
            if (!matchesFilter(filters.get("item"), itemCode, itemName, displayItem(itemCode, itemName))) {
                continue;
            }
            Map<String, Object> historyRow = new LinkedHashMap<>();
            historyRow.put("date", asString(row.get("valid_from")));
            historyRow.put("item_code", itemCode);
            historyRow.put("item_name", itemName);
            historyRow.put("item_group", meta.itemGroup());
            historyRow.put("vendor", meta.vendor());
            historyRow.put("price", round(asDouble(row.get("price_list_rate"))));
            historyRow.put("currency", asString(row.get("currency")));
            rows.add(historyRow);
            uniqueItems.add(itemCode.isBlank() ? itemName : itemCode);
            totalPrice += asDouble(row.get("price_list_rate"));
        }

        rows.sort(Comparator
                .comparing((Map<String, Object> row) -> asString(row.get("date")))
                .thenComparing(row -> asString(row.get("item_code"))));

        List<AnalyticsColumn> columns = List.of(
                new AnalyticsColumn("date", "Date", "DIMENSION"),
                new AnalyticsColumn("item_code", "Item Code", "DIMENSION"),
                new AnalyticsColumn("item_name", "Item Name", "DIMENSION"),
                new AnalyticsColumn("item_group", "Category", "DIMENSION"),
                new AnalyticsColumn("vendor", "Vendor", "DIMENSION"),
                new AnalyticsColumn("price", "Price", "CURRENCY"),
                new AnalyticsColumn("currency", "Currency", "DIMENSION"));

        List<AnalyticsKpi> kpis = new ArrayList<>();
        kpis.add(new AnalyticsKpi("price_points", "Price Points", rows.size(), "NUMBER"));
        kpis.add(new AnalyticsKpi("items", "Items", uniqueItems.size(), "NUMBER"));
        kpis.add(new AnalyticsKpi("avg_price", "Avg Price", rows.isEmpty() ? 0.0 : round(totalPrice / rows.size()), "CURRENCY"));

        return new AnalyticsQueryResponse(columns, rows, Map.of(), kpis);
    }

    public AnalyticsQueryResponse gstr1Report(AnalyticsQueryRequest req) {
        DateRange range = resolveRange(req.getDateFrom(), req.getDateTo());
        Map<String, String> filters = normalizeFilters(req.getFilters());
        String report = trimToEmpty(filters.get("gstReport")).toLowerCase(Locale.ROOT);
        return switch (report) {
            case "b2b" -> gstr1B2b(range);
            case "b2c", "b2cs" -> gstr1B2c(range);
            case "cdnr" -> gstr1Cdnr(range);
            case "hsn" -> gstr1Hsn(range);
            case "docs" -> gstr1Docs(range);
            default -> gstr1B2b(range);
        };
    }

    private AnalyticsQueryResponse gstr1B2b(DateRange range) {
        GstrContext context = loadGstrContext(range);
        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> warnings = new ArrayList<>(context.warnings());
        for (Map<String, Object> invoice : context.invoices()) {
            CustomerTaxMeta customer = context.customerMeta(asString(invoice.get("customer")));
            if (customer.gstin().isBlank()) {
                continue;
            }
            List<Map<String, Object>> items = context.invoiceItems(asString(invoice.get("name")));
            for (Map<String, Object> item : items) {
                GstrLine line = gstrLine(invoice, item, context, customer);
                Map<String, Object> row = baseB2bRow(customer, line);
                row.put("Invoice Type", "Regular");
                row.put("Reverse Charge (Indicate if supply attracts reverse charge)", "No");
                row.put("Applicable % of Tax Rate", "");
                row.put("GSTIN of E-Commerce Operator (If applicable)", "");
                rows.add(row);
            }
        }
        warnings.addAll(context.hsnAudit().warnings());
        return gstrResponse(b2bColumns(), rows, warnings);
    }

    private AnalyticsQueryResponse gstr1B2c(DateRange range) {
        GstrContext context = loadGstrContext(range);
        List<Map<String, Object>> rows = new ArrayList<>();
        List<String> warnings = new ArrayList<>(context.warnings());
        Map<String, GstrAggregate> aggregates = new LinkedHashMap<>();
        for (Map<String, Object> invoice : context.invoices()) {
            CustomerTaxMeta customer = context.customerMeta(asString(invoice.get("customer")));
            if (!customer.gstin().isBlank()) {
                continue;
            }
            for (Map<String, Object> item : context.invoiceItems(asString(invoice.get("name")))) {
                GstrLine line = gstrLine(invoice, item, context, customer);
                String key = line.placeOfSupply() + "::" + line.hsn() + "::" + line.itemDescription() + "::"
                        + line.uqc() + "::" + line.taxRate();
                GstrAggregate aggregate = aggregates.computeIfAbsent(key, unused -> new GstrAggregate(
                        line.placeOfSupply(), line.hsn(), line.itemDescription(), line.uqc(), line.taxRate()));
                aggregate.add(line);
            }
        }
        for (GstrAggregate aggregate : aggregates.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("Place of Supply * (Required)", aggregate.placeOfSupply);
            row.put("HSN/SAC", aggregate.hsn);
            row.put("Item Description", aggregate.itemDescription);
            row.put("UQC", aggregate.uqc);
            row.put("Quantity", round(aggregate.quantity));
            row.put("Total Taxable Value * (Required)", round(aggregate.taxableValue));
            row.put("Tax Rate * (Required)", aggregate.taxRate);
            row.put("IGST Amount", round(aggregate.igst));
            row.put("CGST Amount", round(aggregate.cgst));
            row.put("SGST/UT Amount", round(aggregate.sgst));
            row.put("Cess Amount", 0.0);
            row.put("GSTIN of E-Commerce Operator (If applicable)", "");
            row.put("Applicable % of Tax Rate", "");
            rows.add(row);
        }
        warnings.addAll(context.hsnAudit().warnings());
        return gstrResponse(b2cColumns(), rows, warnings);
    }

    private AnalyticsQueryResponse gstr1Hsn(DateRange range) {
        GstrContext context = loadGstrContext(range);
        List<String> warnings = new ArrayList<>(context.warnings());
        Map<String, GstrAggregate> aggregates = new LinkedHashMap<>();
        for (Map<String, Object> invoice : context.invoices()) {
            CustomerTaxMeta customer = context.customerMeta(asString(invoice.get("customer")));
            for (Map<String, Object> item : context.invoiceItems(asString(invoice.get("name")))) {
                GstrLine line = gstrLine(invoice, item, context, customer);
                String key = line.hsn() + "::" + line.itemDescription() + "::" + line.uqc() + "::" + line.taxRate();
                GstrAggregate aggregate = aggregates.computeIfAbsent(key, unused -> new GstrAggregate(
                        "", line.hsn(), line.itemDescription(), line.uqc(), line.taxRate()));
                aggregate.add(line);
            }
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (GstrAggregate aggregate : aggregates.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("HSN Type * (Required from May-25)", aggregate.hsn == null || aggregate.hsn.isBlank() ? "" : "Goods");
            row.put("Description", aggregate.itemDescription);
            row.put("HSN/SAC * (Required)", aggregate.hsn);
            row.put("Quantity * (Required for HSN Code)", round(aggregate.quantity));
            row.put("UQC * (Required for HSN Code)", aggregate.uqc);
            row.put("Total Value * (Required till April-21)", round(aggregate.taxableValue + aggregate.igst + aggregate.cgst + aggregate.sgst));
            row.put("Total Taxable Value * (Required)", round(aggregate.taxableValue));
            row.put("Tax Rate (%) * (Required from May-21)", aggregate.taxRate);
            row.put("IGST Amount * (Required if supply is Inter-State)", round(aggregate.igst));
            row.put("CGST Amount * (Required if supply is Intra-State)", round(aggregate.cgst));
            row.put("SGST/UT Amount * (Required if supply is Intra-State)", round(aggregate.sgst));
            row.put("Cess Amount", 0.0);
            rows.add(row);
        }
        warnings.addAll(context.hsnAudit().warnings());
        return gstrResponse(hsnColumns(), rows, warnings);
    }

    private AnalyticsQueryResponse gstr1Docs(DateRange range) {
        List<Map<String, Object>> invoices = invoiceService.listInvoices("Customer", null, range.start(), range.end());
        List<String> names = invoices.stream()
                .map(invoice -> trimToEmpty(asString(invoice.get("name"))))
                .filter(name -> !name.isBlank())
                .sorted()
                .toList();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("Nature of Document * (Required)", "Invoices for outward supply");
        row.put("Sr. No. * (Required)", names.isEmpty() ? "" : names.get(0));
        row.put("From", names.isEmpty() ? "" : names.get(0));
        row.put("To", names.isEmpty() ? "" : names.get(names.size() - 1));
        row.put("Total No of Invoices * (Required)", names.size());
        row.put("Cancelled Invoices * (Required)", 0);
        row.put("Net Invoices Issued * (Required)", names.size());
        return gstrResponse(docsColumns(), names.isEmpty() ? List.of() : List.of(row), List.of(
                "DOCS is a working report: cancelled/deleted sequence gaps are not counted unless cancelled documents are fetched and included."));
    }

    private AnalyticsQueryResponse gstr1Cdnr(DateRange range) {
        List<String> warnings = new ArrayList<>();
        warnings.add("CDNR is built from approved customer adjustment Journal Entries. HSN, quantity, UQC, original invoice, and tax split are approximated unless captured on the note.");
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\",\"posting_date\",\"docstatus\",\"aas_adjustment_party\",\"aas_adjustment_party_type\","
                + "\"aas_adjustment_note_type\",\"aas_adjustment_amount\",\"aas_adjustment_item_name\",\"aas_reference_invoice\","
                + "\"aas_adjustment_reason\",\"aas_adjustment_review_status\"]");
        params.put("limit_page_length", 0);
        params.put("order_by", "posting_date asc");
        params.put("filters", toJson(List.of(
                List.of("posting_date", ">=", range.start()),
                List.of("posting_date", "<=", range.end()),
                List.of("docstatus", "=", "1"),
                List.of("aas_adjustment_party_type", "=", "Customer"),
                List.of("aas_adjustment_review_status", "=", "APPROVED"))));
        List<Map<String, Object>> notes = erpNextClient.listResources("Journal Entry", params);
        Set<String> customers = notes.stream()
                .map(note -> trimToEmpty(asString(note.get("aas_adjustment_party"))))
                .filter(value -> !value.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, CustomerTaxMeta> customerMeta = fetchCustomerTaxMeta(customers);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (Map<String, Object> note : notes) {
            String customerId = trimToEmpty(asString(note.get("aas_adjustment_party")));
            CustomerTaxMeta customer = customerMeta.getOrDefault(customerId, CustomerTaxMeta.empty(customerId));
            if (customer.gstin().isBlank()) {
                continue;
            }
            double amount = round(asDouble(note.get("aas_adjustment_amount")));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("Name of the Recipient (Optional)", customer.label());
            row.put("Receiver GSTIN/UIN * (Required)", customer.gstin());
            row.put("Place of Supply * (Required)", placeOfSupply(customer, ""));
            row.put("Note Type  (Debit/Credit/Refund) * (Required)", noteTypeLabel(asString(note.get("aas_adjustment_note_type"))));
            row.put("Note Supply Type", "");
            row.put("Reverse Charge", "No");
            row.put("Note/Refund Voucher Number * (Required)", asString(note.get("name")));
            row.put("Note/Refund Voucher Date * (Required)", asString(note.get("posting_date")));
            row.put("Note/Refund Voucher Value * (Required)", amount);
            row.put("HSN/SAC", "");
            row.put("Item Description", firstNonBlank(asString(note.get("aas_adjustment_item_name")), asString(note.get("aas_reference_invoice"))));
            row.put("UQC", "");
            row.put("Quantity", "");
            row.put("Taxable Value * (Required)", amount);
            row.put("Tax Rate * (Required)", 0.0);
            row.put("IGST Amount", 0.0);
            row.put("CGST Amount", 0.0);
            row.put("SGST/UT Amount", 0.0);
            row.put("Cess Amount", 0.0);
            row.put("Applicable % of Tax Rate", "");
            rows.add(row);
        }
        return gstrResponse(cdnrColumns(), rows, warnings);
    }

    private GstrContext loadGstrContext(DateRange range) {
        List<String> warnings = new ArrayList<>();
        List<Map<String, Object>> invoices = invoiceService.listInvoices("Customer", null, range.start(), range.end());
        Map<String, List<Map<String, Object>>> invoiceItems = batchFetchInvoiceItems(invoices);
        Set<String> customers = invoices.stream()
                .map(invoice -> trimToEmpty(asString(invoice.get("customer"))))
                .filter(value -> !value.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> itemCodes = invoiceItems.values().stream()
                .flatMap(List::stream)
                .map(item -> trimToEmpty(asString(item.get("item_code"))))
                .filter(value -> !value.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Set<String> itemNames = invoiceItems.values().stream()
                .flatMap(List::stream)
                .map(item -> trimToEmpty(asString(item.get("item_name"))))
                .filter(value -> !value.isBlank())
                .collect(Collectors.toCollection(LinkedHashSet::new));
        Map<String, CustomerTaxMeta> customerMeta = fetchCustomerTaxMeta(customers);
        Map<String, ItemTaxMeta> itemMeta = fetchItemTaxMeta(itemCodes);
        Map<String, ItemTaxMeta> itemMetaByName = fetchItemTaxMetaByName(itemNames);
        String companyGstin = fetchCompanyGstin(invoices);
        if (companyGstin.isBlank()) {
            warnings.add("Company GSTIN is missing; place-of-supply tax split falls back to intra-state CGST/SGST.");
        }
        warnings.add("Place of Supply uses customer GSTIN state prefix when available; otherwise it falls back to company GSTIN state.");
        return new GstrContext(invoices, invoiceItems, customerMeta, itemMeta, itemMetaByName, companyGstin, warnings, new HsnAudit());
    }

    private Map<String, CustomerTaxMeta> fetchCustomerTaxMeta(Set<String> customerIds) {
        if (customerIds.isEmpty()) {
            return Map.of();
        }
        Map<String, CustomerTaxMeta> result = new HashMap<>();
        for (List<String> chunk : partition(new ArrayList<>(customerIds), BATCH_CHUNK_SIZE)) {
            Map<String, Object> params = new HashMap<>();
            params.put("fields", "[\"name\",\"customer_name\",\"tax_id\",\"gstin\"]");
            params.put("filters", buildInFilter("name", chunk));
            params.put("limit_page_length", chunk.size() + 1);
            try {
                for (Map<String, Object> row : erpNextClient.listResources("Customer", params)) {
                    String id = trimToEmpty(asString(row.get("name")));
                    String label = firstNonBlank(asString(row.get("customer_name")), id);
                    String gstin = firstNonBlank(asString(row.get("tax_id")), asString(row.get("gstin"))).toUpperCase(Locale.ROOT);
                    if (!id.isBlank()) {
                        result.put(id, new CustomerTaxMeta(label, gstin));
                    }
                }
            } catch (Exception ignored) {}
        }
        return result;
    }

    private Map<String, ItemTaxMeta> fetchItemTaxMeta(Set<String> itemCodes) {
        if (itemCodes.isEmpty()) {
            return Map.of();
        }
        Map<String, ItemTaxMeta> result = new HashMap<>();
        for (List<String> chunk : partition(new ArrayList<>(itemCodes), BATCH_CHUNK_SIZE)) {
            Map<String, Object> params = new HashMap<>();
            params.put("fields", "[\"name\",\"item_name\",\"stock_uom\",\"aas_vendor_hsn_code\",\"gst_hsn_code\",\"aas_gst_percent\"]");
            params.put("filters", buildInFilter("name", chunk));
            params.put("limit_page_length", chunk.size() + 1);
            try {
                for (Map<String, Object> row : erpNextClient.listResources("Item", params)) {
                    String id = trimToEmpty(asString(row.get("name")));
                    if (!id.isBlank()) {
                        result.put(id, new ItemTaxMeta(
                                firstNonBlank(asString(row.get("item_name")), id),
                                firstNonBlank(asString(row.get("aas_vendor_hsn_code")), asString(row.get("gst_hsn_code"))),
                                firstNonBlank(asString(row.get("stock_uom")), "Nos"),
                                asDouble(row.get("aas_gst_percent"))));
                    }
                }
            } catch (Exception ignored) {}
        }
        return result;
    }

    private Map<String, ItemTaxMeta> fetchItemTaxMetaByName(Set<String> itemNames) {
        if (itemNames.isEmpty()) {
            return Map.of();
        }
        Map<String, ItemTaxMeta> result = new HashMap<>();
        for (List<String> chunk : partition(new ArrayList<>(itemNames), BATCH_CHUNK_SIZE)) {
            Map<String, Object> params = new HashMap<>();
            params.put("fields", "[\"name\",\"item_name\",\"stock_uom\",\"aas_vendor_hsn_code\",\"gst_hsn_code\",\"aas_gst_percent\"]");
            params.put("filters", buildInFilter("item_name", chunk));
            params.put("limit_page_length", chunk.size() + 1);
            try {
                for (Map<String, Object> row : erpNextClient.listResources("Item", params)) {
                    String name = trimToEmpty(asString(row.get("item_name")));
                    if (!name.isBlank()) {
                        result.put(normalizeItemNameKey(name), new ItemTaxMeta(
                                name,
                                firstNonBlank(asString(row.get("aas_vendor_hsn_code")), asString(row.get("gst_hsn_code"))),
                                firstNonBlank(asString(row.get("stock_uom")), "Nos"),
                                asDouble(row.get("aas_gst_percent"))));
                    }
                }
            } catch (Exception ignored) {}
        }
        return result;
    }

    private String fetchCompanyGstin(List<Map<String, Object>> invoices) {
        String company = invoices.stream()
                .map(invoice -> trimToEmpty(asString(invoice.get("company"))))
                .filter(value -> !value.isBlank())
                .findFirst()
                .orElse("");
        if (company.isBlank()) {
            return "";
        }
        try {
            Map<String, Object> doc = unwrapDoc(erpNextClient.getResource("Company", company));
            return firstNonBlank(asString(doc.get("tax_id")), asString(doc.get("gstin"))).toUpperCase(Locale.ROOT);
        } catch (Exception ignored) {
            return "";
        }
    }

    private GstrLine gstrLine(
            Map<String, Object> invoice,
            Map<String, Object> item,
            GstrContext context,
            CustomerTaxMeta customer) {
        String itemCode = trimToEmpty(asString(item.get("item_code")));
        String itemName = trimToEmpty(asString(item.get("item_name")));
        ItemTaxMeta meta = context.itemMeta().getOrDefault(
                itemCode,
                context.itemMetaByName().getOrDefault(normalizeItemNameKey(itemName), ItemTaxMeta.empty(itemCode)));
        double taxable = round(asDouble(firstNonNull(item.get("amount"), item.get("net_amount"))));
        double taxRate = asDouble(firstNonNull(item.get("aas_gst_percent"), meta.gstRate()));
        double tax = round(taxable * taxRate / 100.0);
        boolean interstate = isInterstate(customer.gstin(), context.companyGstin());
        double igst = interstate ? tax : 0.0;
        double cgst = interstate ? 0.0 : round(tax / 2.0);
        double sgst = interstate ? 0.0 : round(tax - cgst);
        String resolvedHsn = resolveReportHsn(
                firstNonBlank(asString(item.get("aas_vendor_hsn_code")), asString(item.get("gst_hsn_code")), meta.hsn()),
                firstNonBlank(itemName, meta.itemName(), itemCode),
                context);
        return new GstrLine(
                customer.label(),
                customer.gstin(),
                placeOfSupply(customer, context.companyGstin()),
                asString(invoice.get("name")),
                asString(invoice.get("posting_date")),
                round(asDouble(invoice.get("grand_total"))),
                resolvedHsn,
                firstNonBlank(itemName, meta.itemName(), itemCode),
                firstNonBlank(asString(item.get("uom")), asString(item.get("stock_uom")), meta.uqc()),
                round(asDouble(item.get("qty"))),
                taxable,
                round(taxRate),
                igst,
                cgst,
                sgst);
    }

    private Map<String, Object> baseB2bRow(CustomerTaxMeta customer, GstrLine line) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("Receiver Name (Optional)", customer.label());
        row.put("Receiver GSTIN/UIN * (Required)", customer.gstin());
        row.put("Place of Supply * (Required)", line.placeOfSupply());
        row.put("Invoice No * (Required)", line.invoiceNo());
        row.put("Invoice Date * (Required)", line.invoiceDate());
        row.put("Total Invoice Value * (Required)", line.invoiceValue());
        row.put("HSN/SAC", line.hsn());
        row.put("Item Description", line.itemDescription());
        row.put("UQC", line.uqc());
        row.put("Quantity", line.quantity());
        row.put("Taxable Value * (Required)", line.taxableValue());
        row.put("Tax Rate * (Required)", line.taxRate());
        row.put("IGST Amount", line.igst());
        row.put("CGST Amount", line.cgst());
        row.put("SGST/UT Amount", line.sgst());
        row.put("Cess Amount", 0.0);
        return row;
    }

    private AnalyticsQueryResponse gstrResponse(List<AnalyticsColumn> columns, List<Map<String, Object>> rows, List<String> warnings) {
        Map<String, Object> totals = new LinkedHashMap<>();
        if (!rows.isEmpty()) {
            totals.put(columns.get(0).id(), "Total");
            for (AnalyticsColumn column : columns) {
                String id = column.id();
                if (id.equals(columns.get(0).id())) {
                    continue;
                }
                if (!shouldTotalGstrColumn(id)) {
                    continue;
                }
                double sum = rows.stream().mapToDouble(row -> asDouble(row.get(id))).sum();
                if (sum != 0.0) {
                    totals.put(id, round(sum));
                }
            }
        }
        List<AnalyticsKpi> kpis = List.of(
                new AnalyticsKpi("rows", "Rows", rows.size(), "NUMBER"),
                new AnalyticsKpi("taxable", "Taxable Value", rows.stream().mapToDouble(row -> asDouble(firstNonNull(
                        row.get("Taxable Value * (Required)"),
                        row.get("Total Taxable Value * (Required)"),
                        row.get("Total Taxable Value * (Required)")))).sum(), "CURRENCY"));
        return new AnalyticsQueryResponse(columns, rows, totals, kpis, warnings);
    }

    private String resolveReportHsn(String rawHsn, String itemLabel, GstrContext context) {
        String hsn = trimToEmpty(rawHsn);
        if (hsn.isBlank()) {
            context.hsnAudit().recordMissing(itemLabel);
            return "";
        }
        if (!isFilingQualityHsn(hsn)) {
            context.hsnAudit().recordInvalid(itemLabel, hsn);
            return "";
        }
        return hsn;
    }

    private boolean isFilingQualityHsn(String hsn) {
        String value = trimToEmpty(hsn);
        if (!value.matches("\\d{4}|\\d{6}|\\d{8}")) {
            return false;
        }
        return !value.startsWith("00");
    }

    private boolean shouldTotalGstrColumn(String id) {
        String normalized = id.toLowerCase(Locale.ROOT);
        return !normalized.contains("tax rate")
                && !normalized.contains("applicable %");
    }

    private List<FactRow> loadInvoiceFacts(
            DateRange range,
            List<String> dims,
            List<String> mets,
            Map<String, String> filters,
            List<String> warnings) {
        boolean needCost = mets.stream().anyMatch(metric -> Set.of("cost", "profit", "margin_pct").contains(metric));
        boolean needQuantity = mets.contains("quantity");
        boolean needItemFacts = needCost  // invoice items carry aas_vendor_rate as fallback when order items don't
                || needQuantity
                || dims.contains("item_group")
                || dims.contains("item")
                || hasText(filters.get("itemGroup"))
                || hasText(filters.get("item"));
        boolean needSourceOrders = needCost || dims.contains("vendor") || hasText(filters.get("vendor")) || needItemFacts;

        List<Map<String, Object>> invoices = fetchInvoices(range, filters);

        // Collect source order IDs upfront (batch fetch instead of N individual calls)
        Set<String> sourceOrderIds = new LinkedHashSet<>();
        for (Map<String, Object> invoice : invoices) {
            String id = asString(invoice.get("aas_source_sales_order")).trim();
            if (!id.isBlank()) sourceOrderIds.add(id);
        }

        Map<String, Map<String, Object>> sourceOrders = needSourceOrders
                ? batchFetchOrders(sourceOrderIds)
                : Map.of();
        Map<String, Double> purchaseCostBySourceOrder = needCost
                ? batchFetchPurchaseInvoiceCosts(sourceOrderIds)
                : Map.of();

        // Check cost completeness
        if (needCost && !sourceOrderIds.isEmpty()) {
            long ordersWithoutCost = sourceOrderIds.stream()
                    .filter(orderId -> extractCostFromItems(sourceOrders.getOrDefault(orderId, Map.of())) == 0.0)
                    .filter(orderId -> purchaseCostBySourceOrder.getOrDefault(orderId, 0.0) == 0.0)
                    .count();
            if (ordersWithoutCost > 0) {
                warnings.add("Cost data may be incomplete: " + ordersWithoutCost + " order(s) have neither vendor rate nor purchase invoice cost. Margin and profit figures may be overstated.");
            }
        }

        if (needItemFacts) {
            // Batch-fetch invoice line items and item metadata
            Map<String, List<Map<String, Object>>> invoiceItemMap = batchFetchInvoiceItems(invoices);
            Set<String> allItemCodes = invoiceItemMap.values().stream()
                    .flatMap(List::stream)
                    .map(item -> asString(item.get("item_code")).trim())
                    .filter(code -> !code.isBlank())
                    .collect(Collectors.toCollection(LinkedHashSet::new));
            Map<String, ItemMeta> itemMeta = batchFetchItemMeta(allItemCodes);
            return buildItemFacts(invoices, sourceOrders, purchaseCostBySourceOrder, filters, invoiceItemMap, itemMeta);
        }
        return buildInvoiceFacts(invoices, sourceOrders, purchaseCostBySourceOrder, filters, needCost);
    }

    private List<Map<String, Object>> fetchInvoices(DateRange range, Map<String, String> filters) {
        String branch = trimToEmpty(filters.get("branch"));
        return invoiceService.listInvoices("Customer", branch.isBlank() ? null : branch, range.start(), range.end());
    }

    // Batch-fetch Sales Order headers and their line items in ~2 list calls per chunk
    private Map<String, Map<String, Object>> batchFetchOrders(Set<String> orderIds) {
        if (orderIds.isEmpty()) return Map.of();
        Map<String, Map<String, Object>> result = new HashMap<>();

        List<List<String>> chunks = partition(new ArrayList<>(orderIds), BATCH_CHUNK_SIZE);
        for (List<String> chunk : chunks) {
            // Fetch order headers (name, aas_vendor, aas_category)
            try {
                Map<String, Object> params = new HashMap<>();
                params.put("fields", "[\"name\",\"aas_vendor\",\"aas_category\"]");
                params.put("filters", buildInFilter("name", chunk));
                params.put("limit_page_length", String.valueOf(chunk.size() + 1));
                for (Map<String, Object> row : erpNextClient.listResources("Sales Order", params)) {
                    String name = asString(row.get("name")).trim();
                    if (!name.isBlank()) {
                        Map<String, Object> doc = new HashMap<>(row);
                        doc.put("items", new ArrayList<>());
                        result.put(name, doc);
                    }
                }
            } catch (Exception ignored) {}

            // Fetch order line items (parent, item_code, item_name, item_group, qty, aas_vendor_rate)
            try {
                Map<String, Object> params = new HashMap<>();
                params.put("fields", "[\"parent\",\"item_code\",\"item_name\",\"item_group\",\"qty\",\"rate\",\"aas_vendor_rate\"]");
                params.put("filters", buildInFilter("parent", chunk));
                params.put("limit_page_length", "500");
                for (Map<String, Object> item : erpNextClient.listResources("Sales Order Item", params)) {
                    String parent = asString(item.get("parent")).trim();
                    result.computeIfAbsent(parent, k -> {
                        Map<String, Object> stub = new HashMap<>();
                        stub.put("items", new ArrayList<>());
                        return stub;
                    });
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> items = (List<Map<String, Object>>) result.get(parent).get("items");
                    items.add(item);
                }
            } catch (Exception ignored) {}
        }
        return result;
    }

    // Fetch Sales Invoice line items via individual document fetches.
    // ERPNext's list API silently returns 0 rows for Sales Invoice Item when the
    // parent is in Draft status (docstatus=0), so we must fetch the full document
    // which returns items[] regardless of docstatus.
    private Map<String, List<Map<String, Object>>> batchFetchInvoiceItems(List<Map<String, Object>> invoices) {
        if (invoices.isEmpty()) return Map.of();
        List<String> invoiceIds = invoices.stream()
                .map(inv -> asString(inv.get("name")).trim())
                .filter(id -> !id.isBlank())
                .collect(Collectors.toList());

        Map<String, List<Map<String, Object>>> result = new HashMap<>();

        for (String invoiceId : invoiceIds) {
            try {
                Map<String, Object> doc = unwrapDoc(erpNextClient.getResource("Sales Invoice", invoiceId));
                Object items = doc.get("items");
                if (items instanceof List<?> list && !list.isEmpty()) {
                    @SuppressWarnings("unchecked")
                    List<Map<String, Object>> itemList = (List<Map<String, Object>>) list;
                    result.put(invoiceId, itemList);
                }
            } catch (Exception ignored) {}
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrapDoc(Map<String, Object> response) {
        if (response == null) return Map.of();
        Object data = response.get("data");
        return data instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    private List<FactRow> buildInvoiceFacts(
            List<Map<String, Object>> invoices,
            Map<String, Map<String, Object>> sourceOrders,
            Map<String, Double> purchaseCostBySourceOrder,
            Map<String, String> filters,
            boolean needCost) {
        List<FactRow> facts = new ArrayList<>();
        for (Map<String, Object> invoice : invoices) {
            String invoiceId = asString(invoice.get("name")).trim();
            String sourceOrderId = asString(invoice.get("aas_source_sales_order")).trim();
            Map<String, Object> sourceOrder = sourceOrders.getOrDefault(sourceOrderId, Map.of());
            String vendor = asString(sourceOrder.get("aas_vendor")).trim();
            String branch = asString(invoice.get("customer")).trim();
            String category = firstNonBlank(asString(invoice.get("aas_category")).trim(), resolveFirstItemGroup(sourceOrder));
            double cost = needCost ? resolveCost(sourceOrderId, sourceOrder, purchaseCostBySourceOrder) : 0.0;
            FactRow fact = new FactRow(
                    invoiceId,
                    sourceOrderId,
                    asString(invoice.get("posting_date")).trim(),
                    vendor,
                    branch,
                    category,
                    "",
                    "",
                    round(asDouble(invoice.get("grand_total"))),
                    round(asDouble(invoice.get("net_total"))),
                    cost,
                    0.0);
            if (matchesCommonFilters(filters, fact)) {
                facts.add(fact);
            }
        }
        return facts;
    }

    private List<FactRow> buildItemFacts(
            List<Map<String, Object>> invoices,
            Map<String, Map<String, Object>> sourceOrders,
            Map<String, Double> purchaseCostBySourceOrder,
            Map<String, String> filters,
            Map<String, List<Map<String, Object>>> invoiceItemMap,
            Map<String, ItemMeta> itemMeta) {
        List<FactRow> facts = new ArrayList<>();
        for (Map<String, Object> invoice : invoices) {
            String invoiceId = asString(invoice.get("name")).trim();
            String sourceOrderId = asString(invoice.get("aas_source_sales_order")).trim();
            Map<String, Object> sourceOrder = sourceOrders.getOrDefault(sourceOrderId, Map.of());
            List<Map<String, Object>> invoiceItems = invoiceItemMap.getOrDefault(invoiceId, List.of());

            if (invoiceItems.isEmpty()) {
                facts.addAll(buildInvoiceFacts(List.of(invoice), sourceOrders, purchaseCostBySourceOrder, filters, true));
                continue;
            }

            String vendor = asString(sourceOrder.get("aas_vendor")).trim();
            String branch = asString(invoice.get("customer")).trim();
            double invoiceRevenue = round(asDouble(invoice.get("grand_total")));
            double invoiceRevenueNet = round(asDouble(invoice.get("net_total")));
            double invoiceCost = resolveCost(sourceOrderId, sourceOrder, purchaseCostBySourceOrder);
            // If cost from Sales Order items equals the invoice revenue, it indicates
            // aas_vendor_rate was stored as the sell rate (corrupted data from the old
            // buildSellItems bug). Treat as unknown rather than reporting Revenue = Cost.
            if (invoiceCost > 0.0 && Math.abs(invoiceCost - invoiceRevenue) < 1.0) {
                invoiceCost = 0.0;
            }
            List<SourceOrderLine> sourceLines = buildSourceOrderLines(sourceOrder);
            List<PreparedLine> preparedLines = prepareLines(invoiceItems, sourceLines, itemMeta, asString(invoice.get("aas_category")).trim());
            if (preparedLines.isEmpty()) {
                facts.addAll(buildInvoiceFacts(List.of(invoice), sourceOrders, purchaseCostBySourceOrder, filters, true));
                continue;
            }

            double totalAmount = preparedLines.stream().mapToDouble(PreparedLine::amount).sum();
            double totalDirectCost = preparedLines.stream().mapToDouble(PreparedLine::directCost).sum();
            // Fall back to per-line vendor rates from Sales Invoice items only when:
            // 1. No cost from source order or Purchase Invoice
            // 2. The derived cost is strictly less than revenue — if they are equal it means
            //    aas_vendor_rate was incorrectly stored as the sell rate (a known data issue
            //    fixed in OrderBillingService), so we treat cost as unknown (0) instead of
            //    silently reporting Revenue = Cost and Profit = 0.
            if (invoiceCost == 0.0 && totalDirectCost > 0.0 && totalDirectCost < invoiceRevenue) {
                invoiceCost = totalDirectCost;
            }
            double remainingRevenue = invoiceRevenue;
            double remainingRevenueNet = invoiceRevenueNet;
            double remainingCost = invoiceCost;
            for (int index = 0; index < preparedLines.size(); index++) {
                PreparedLine line = preparedLines.get(index);
                double amountShare = totalAmount > 0 ? line.amount() / totalAmount : 1.0 / preparedLines.size();
                double costShareBase = totalDirectCost > 0 ? line.directCost() / totalDirectCost : amountShare;
                boolean lastLine = index == preparedLines.size() - 1;
                double revenue = lastLine ? remainingRevenue : round(invoiceRevenue * amountShare);
                double revenueNet = lastLine ? remainingRevenueNet : round(invoiceRevenueNet * amountShare);
                double cost = lastLine ? remainingCost : round(invoiceCost * costShareBase);
                remainingRevenue = round(remainingRevenue - revenue);
                remainingRevenueNet = round(remainingRevenueNet - revenueNet);
                remainingCost = round(remainingCost - cost);

                FactRow fact = new FactRow(
                        invoiceId,
                        sourceOrderId,
                        asString(invoice.get("posting_date")).trim(),
                        vendor,
                        branch,
                        line.itemGroup(),
                        line.itemCode(),
                        line.itemName(),
                        revenue,
                        revenueNet,
                        cost,
                        line.qty());
                if (matchesCommonFilters(filters, fact)) {
                    facts.add(fact);
                }
            }
        }
        return facts;
    }

    private Map<String, Double> batchFetchPurchaseInvoiceCosts(Set<String> sourceOrderIds) {
        if (sourceOrderIds.isEmpty()) {
            return Map.of();
        }
        Map<String, Map<String, Object>> latestBySourceOrder = new HashMap<>();
        for (List<String> chunk : partition(new ArrayList<>(sourceOrderIds), BATCH_CHUNK_SIZE)) {
            try {
                Map<String, Object> params = new HashMap<>();
                params.put(
                        "fields",
                        "[\"name\",\"posting_date\",\"grand_total\",\"docstatus\",\"status\",\"modified\",\"creation\","
                                + "\"aas_source_sales_order\",\"aas_replaced_by\",\"aas_invoice_version_status\"]");
                params.put("filters", buildInFilter("aas_source_sales_order", chunk));
                params.put("limit_page_length", "500");
                for (Map<String, Object> invoice : erpNextClient.listResources("Purchase Invoice", params)) {
                    if ((int) asDouble(invoice.get("docstatus")) == 2) {
                        continue;
                    }
                    if ("Cancelled".equalsIgnoreCase(asString(invoice.get("status")).trim())) {
                        continue;
                    }
                    if ("OLD".equalsIgnoreCase(asString(invoice.get("aas_invoice_version_status")).trim())) {
                        continue;
                    }
                    if (!asString(invoice.get("aas_replaced_by")).trim().isBlank()) {
                        continue;
                    }
                    String sourceOrderId = asString(invoice.get("aas_source_sales_order")).trim();
                    if (sourceOrderId.isBlank()) {
                        continue;
                    }
                    Map<String, Object> best = latestBySourceOrder.get(sourceOrderId);
                    if (best == null || compareInvoiceRecency(invoice, best) > 0) {
                        latestBySourceOrder.put(sourceOrderId, invoice);
                    }
                }
            } catch (Exception ignored) {}
        }
        Map<String, Double> costs = new HashMap<>();
        for (Map.Entry<String, Map<String, Object>> entry : latestBySourceOrder.entrySet()) {
            costs.put(entry.getKey(), round(asDouble(entry.getValue().get("grand_total"))));
        }
        return costs;
    }

    private double resolveCost(
            String sourceOrderId,
            Map<String, Object> sourceOrder,
            Map<String, Double> purchaseCostBySourceOrder) {
        double orderCost = round(extractCostFromItems(sourceOrder));
        if (orderCost > 0.0) {
            return orderCost;
        }
        return round(purchaseCostBySourceOrder.getOrDefault(trimToEmpty(sourceOrderId), 0.0));
    }

    private int compareInvoiceRecency(Map<String, Object> left, Map<String, Object> right) {
        String leftModified = asString(left == null ? null : left.get("modified")).trim();
        String rightModified = asString(right == null ? null : right.get("modified")).trim();
        if (!leftModified.isBlank() || !rightModified.isBlank()) {
            return leftModified.compareTo(rightModified);
        }
        String leftCreated = asString(left == null ? null : left.get("creation")).trim();
        String rightCreated = asString(right == null ? null : right.get("creation")).trim();
        if (!leftCreated.isBlank() || !rightCreated.isBlank()) {
            return leftCreated.compareTo(rightCreated);
        }
        return asString(left == null ? null : left.get("name")).compareTo(asString(right == null ? null : right.get("name")));
    }

    private List<PreparedLine> prepareLines(
            List<Map<String, Object>> invoiceItems,
            List<SourceOrderLine> sourceLines,
            Map<String, ItemMeta> itemMeta,
            String fallbackCategory) {
        List<PreparedLine> prepared = new ArrayList<>();
        int sourceIndex = 0;
        for (Map<String, Object> item : invoiceItems) {
            String itemCode = asString(item.get("item_code")).trim();
            String itemName = asString(firstNonNull(item.get("item_name"), item.get("description"))).trim();
            double qty = asDouble(item.get("qty"));
            double rate = asDouble(item.get("rate"));
            double amount = asDouble(item.get("amount"));
            if (amount == 0.0 && qty > 0.0) {
                amount = qty * rate;
            }
            SourceOrderLine sourceLine = matchSourceLine(sourceLines, sourceIndex, itemCode, itemName);
            if (sourceLine != null) {
                sourceIndex = Math.max(sourceIndex, sourceLine.index() + 1);
            }
            ItemMeta meta = itemMeta.getOrDefault(itemCode, ItemMeta.empty());
            String itemGroup = firstNonBlank(
                    asString(item.get("item_group")).trim(),
                    sourceLine == null ? "" : sourceLine.itemGroup(),
                    meta.itemGroup(),
                    fallbackCategory);
            double directCost = sourceLine != null ? sourceLine.directCost() : 0.0;
            if (directCost == 0.0) {
                double vendorRate = asDouble(item.get("aas_vendor_rate"));
                // Skip aas_vendor_rate equal to sell rate (same corruption guard as extractCostFromItems).
                directCost = vendorRate > 0.0 && qty > 0.0 && Math.abs(vendorRate - rate) >= 0.01 ? vendorRate * qty : 0.0;
            }
            prepared.add(new PreparedLine(itemCode, itemName, itemGroup, round(amount), round(directCost), qty));
        }
        return prepared;
    }

    private List<SourceOrderLine> buildSourceOrderLines(Map<String, Object> sourceOrder) {
        Object items = sourceOrder.get("items");
        if (!(items instanceof List<?> list)) {
            return List.of();
        }
        List<SourceOrderLine> lines = new ArrayList<>();
        for (int index = 0; index < list.size(); index++) {
            Object itemObj = list.get(index);
            if (!(itemObj instanceof Map<?, ?> item)) {
                continue;
            }
            double qty = asDouble(item.get("qty"));
            double rate = asDouble(item.get("rate"));
            double vendorRate = asDouble(item.get("aas_vendor_rate"));
            lines.add(new SourceOrderLine(
                    index,
                    asString(item.get("item_code")).trim(),
                    asString(firstNonNull(item.get("item_name"), item.get("description"))).trim(),
                    asString(item.get("item_group")).trim(),
                    vendorRate > 0.0 && qty > 0.0 && Math.abs(vendorRate - rate) >= 0.01 ? round(vendorRate * qty) : 0.0));
        }
        return lines;
    }

    private SourceOrderLine matchSourceLine(List<SourceOrderLine> lines, int startIndex, String itemCode, String itemName) {
        for (int index = startIndex; index < lines.size(); index++) {
            SourceOrderLine line = lines.get(index);
            if (!itemCode.isBlank() && itemCode.equalsIgnoreCase(line.itemCode())) {
                return line;
            }
            if (!itemName.isBlank() && itemName.equalsIgnoreCase(line.itemName())) {
                return line;
            }
        }
        for (SourceOrderLine line : lines) {
            if (!itemCode.isBlank() && itemCode.equalsIgnoreCase(line.itemCode())) {
                return line;
            }
            if (!itemName.isBlank() && itemName.equalsIgnoreCase(line.itemName())) {
                return line;
            }
        }
        return null;
    }

    private boolean matchesCommonFilters(Map<String, String> filters, FactRow fact) {
        return matchesFilter(filters.get("vendor"), fact.vendor())
                && matchesFilter(filters.get("branch"), fact.branch())
                && matchesFilter(filters.get("itemGroup"), fact.itemGroup())
                && matchesFilter(filters.get("item"), fact.itemCode(), fact.itemName(), fact.itemLabel());
    }

    private boolean matchesFilter(String filterValue, String... candidates) {
        String needle = normalizeText(filterValue);
        if (needle.isBlank()) {
            return true;
        }
        for (String candidate : candidates) {
            String haystack = normalizeText(candidate);
            if (haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private Map<String, ItemMeta> batchFetchItemMeta(Set<String> itemCodes) {
        if (itemCodes.isEmpty()) return Map.of();
        Map<String, ItemMeta> itemMeta = new HashMap<>();

        for (List<String> chunk : partition(new ArrayList<>(itemCodes), BATCH_CHUNK_SIZE)) {
            try {
                Map<String, Object> params = new HashMap<>();
                params.put("fields", "[\"name\",\"item_group\",\"aas_vendor\"]");
                params.put("filters", buildInFilter("name", chunk));
                params.put("limit_page_length", String.valueOf(chunk.size() + 1));
                for (Map<String, Object> item : erpNextClient.listResources("Item", params)) {
                    String name = asString(item.get("name")).trim();
                    if (!name.isBlank()) {
                        itemMeta.put(name, new ItemMeta(
                                asString(item.get("item_group")).trim(),
                                asString(item.get("aas_vendor")).trim()));
                    }
                }
            } catch (Exception ignored) {}
        }
        return itemMeta;
    }

    private String resolveFirstItemGroup(Map<String, Object> sourceOrder) {
        Object items = sourceOrder.get("items");
        if (!(items instanceof List<?> list)) {
            return "";
        }
        for (Object itemObj : list) {
            if (itemObj instanceof Map<?, ?> item) {
                String itemGroup = asString(item.get("item_group")).trim();
                if (!itemGroup.isBlank()) {
                    return itemGroup;
                }
            }
        }
        return "";
    }

    private double extractCostFromItems(Map<String, Object> order) {
        Object items = order.get("items");
        if (!(items instanceof List<?> list)) {
            return 0.0;
        }
        double cost = 0.0;
        for (Object obj : list) {
            if (!(obj instanceof Map<?, ?> item)) {
                continue;
            }
            double qty = asDouble(item.get("qty"));
            double rate = asDouble(item.get("rate"));
            double vendorRate = asDouble(item.get("aas_vendor_rate"));
            // Skip aas_vendor_rate that equals the sell rate — old buildSellItems bug
            // stored sell rate as aas_vendor_rate; treating it as cost gives Revenue=Cost.
            if (vendorRate > 0.0 && qty > 0.0 && Math.abs(vendorRate - rate) >= 0.01) {
                cost += vendorRate * qty;
            }
        }
        return round(cost);
    }

    private Map<String, AggRow> aggregateFacts(List<FactRow> facts, List<String> dims, String granularity) {
        Map<String, AggRow> agg = new LinkedHashMap<>();
        Grouping grouping = Grouping.parse(granularity);
        for (FactRow fact : facts) {
            String key = buildKey(fact, dims, grouping);
            AggRow row = agg.computeIfAbsent(key, ignored -> {
                AggRow created = new AggRow();
                populateDims(created, fact, dims, grouping);
                return created;
            });
            row.revenue += fact.revenue();
            row.revenueNet += fact.revenueNet();
            row.cost += fact.cost();
            row.qty += fact.qty();
            row.orderIds.add(fact.invoiceId());
        }
        return agg;
    }

    private String buildKey(FactRow fact, List<String> dims, Grouping grouping) {
        StringBuilder builder = new StringBuilder();
        for (String dim : dims) {
            builder.append(dim).append('=').append(dimValue(fact, dim, grouping)).append('|');
        }
        return builder.toString();
    }

    private String dimValue(FactRow fact, String dim, Grouping grouping) {
        return switch (dim) {
            case "date" -> {
                LocalDate date = parseDate(fact.date());
                yield date != null ? grouping.periodFor(date).start() : "";
            }
            case "vendor" -> fact.vendor();
            case "branch" -> fact.branch();
            case "item_group" -> fact.itemGroup();
            case "item" -> fact.itemLabel();
            default -> "";
        };
    }

    private void populateDims(AggRow row, FactRow fact, List<String> dims, Grouping grouping) {
        for (String dim : dims) {
            switch (dim) {
                case "date" -> {
                    LocalDate date = parseDate(fact.date());
                    if (date != null) {
                        PeriodKey period = grouping.periodFor(date);
                        row.dims.put("date", period.start());
                        row.dims.put("_date_end", period.end());
                    }
                }
                case "vendor" -> row.dims.put("vendor", fact.vendor());
                case "branch" -> row.dims.put("branch", fact.branch());
                case "item_group" -> row.dims.put("item_group", fact.itemGroup());
                case "item" -> row.dims.put("item", fact.itemLabel());
            }
        }
    }

    private List<Map<String, Object>> buildRows(Map<String, AggRow> agg, List<String> dims, List<String> mets, String granularity) {
        boolean multiDayGranularity = !"day".equals(granularity);
        List<Map<String, Object>> rows = new ArrayList<>();
        for (AggRow aggRow : agg.values()) {
            Map<String, Object> row = new LinkedHashMap<>();
            for (String dim : dims) {
                row.put(dim, aggRow.dims.getOrDefault(dim, ""));
                if ("date".equals(dim) && multiDayGranularity) {
                    row.put("date_end", aggRow.dims.getOrDefault("_date_end", ""));
                }
            }
            int orders = aggRow.orderIds.size();
            double revenue = round(aggRow.revenue);
            double revenueNet = round(aggRow.revenueNet);
            double cost = round(aggRow.cost);
            double profit = round(revenue - cost);
            double profitExGst = round(revenueNet - cost);
            double marginPct = revenue > 0 ? round((profit / revenue) * 100.0) : 0.0;
            double marginPctExGst = revenueNet > 0 ? round((profitExGst / revenueNet) * 100.0) : 0.0;
            double avgOrderValue = orders > 0 ? round(revenue / orders) : 0.0;
            double qty = round(aggRow.qty);
            // Hidden net-revenue base so buildTotals can divide excl-GST margin correctly.
            if (mets.contains("profit_ex_gst") || mets.contains("margin_pct_ex_gst")) {
                row.put("revenue_net", revenueNet);
            }
            for (String metric : mets) {
                switch (metric) {
                    case "revenue" -> row.put("revenue", revenue);
                    case "cost" -> row.put("cost", cost);
                    case "profit" -> row.put("profit", profit);
                    case "profit_ex_gst" -> row.put("profit_ex_gst", profitExGst);
                    case "margin_pct" -> row.put("margin_pct", marginPct);
                    case "margin_pct_ex_gst" -> row.put("margin_pct_ex_gst", marginPctExGst);
                    case "orders" -> row.put("orders", orders);
                    case "avg_order_value" -> row.put("avg_order_value", avgOrderValue);
                    case "quantity" -> row.put("quantity", qty);
                }
            }
            rows.add(row);
        }
        return rows;
    }

    private List<Map<String, Object>> sortRows(List<Map<String, Object>> rows, List<String> dims) {
        if (dims.contains("date")) {
            rows.sort(Comparator
                    .comparing((Map<String, Object> row) -> asString(row.getOrDefault("date", "")))
                    .thenComparing(row -> asString(row.getOrDefault("item", ""))));
            return rows;
        }
        if (dims.contains("item") || dims.contains("item_group")) {
            rows.sort((left, right) -> Double.compare(
                    asDouble(right.getOrDefault("revenue", 0.0)),
                    asDouble(left.getOrDefault("revenue", 0.0))));
            return rows;
        }
        rows.sort(Comparator.comparing(row -> asString(row.values().stream().findFirst().orElse(""))));
        return rows;
    }

    private List<AnalyticsColumn> buildColumns(List<String> dims, List<String> mets, String granularity) {
        boolean multiDayGranularity = !"day".equals(granularity);
        List<AnalyticsColumn> columns = new ArrayList<>();
        for (String dim : dims) {
            switch (dim) {
                case "date" -> {
                    columns.add(new AnalyticsColumn("date", granularityLabel(granularity), "DIMENSION"));
                    if (multiDayGranularity) {
                        columns.add(new AnalyticsColumn("date_end", "Period End", "DIMENSION"));
                    }
                }
                case "vendor" -> columns.add(new AnalyticsColumn("vendor", "Vendor", "DIMENSION"));
                case "branch" -> columns.add(new AnalyticsColumn("branch", "Branch", "DIMENSION"));
                case "item_group" -> columns.add(new AnalyticsColumn("item_group", "Category", "DIMENSION"));
                case "item" -> columns.add(new AnalyticsColumn("item", "Item", "DIMENSION"));
            }
        }
        for (String metric : mets) {
            switch (metric) {
                case "revenue" -> columns.add(new AnalyticsColumn("revenue", "Revenue", "CURRENCY"));
                case "cost" -> columns.add(new AnalyticsColumn("cost", "Cost", "CURRENCY"));
                case "profit" -> columns.add(new AnalyticsColumn("profit", "Profit (incl GST)", "CURRENCY"));
                case "profit_ex_gst" -> columns.add(new AnalyticsColumn("profit_ex_gst", "Profit (excl GST)", "CURRENCY"));
                case "margin_pct" -> columns.add(new AnalyticsColumn("margin_pct", "Margin % (incl GST)", "PERCENT"));
                case "margin_pct_ex_gst" -> columns.add(new AnalyticsColumn("margin_pct_ex_gst", "Margin % (excl GST)", "PERCENT"));
                case "orders" -> columns.add(new AnalyticsColumn("orders", "Orders", "NUMBER"));
                case "avg_order_value" -> columns.add(new AnalyticsColumn("avg_order_value", "Avg Order", "CURRENCY"));
                case "quantity" -> columns.add(new AnalyticsColumn("quantity", "Qty", "NUMBER"));
            }
        }
        return columns;
    }

    private String granularityLabel(String granularity) {
        return switch (granularity) {
            case "week" -> "Week";
            case "month" -> "Month";
            case "quarter" -> "Quarter";
            default -> "Date";
        };
    }

    private Map<String, Object> buildTotals(List<Map<String, Object>> rows, List<String> dims, List<String> mets) {
        if (rows.isEmpty()) {
            return Map.of();
        }
        Map<String, Object> totals = new LinkedHashMap<>();
        boolean labeled = false;
        for (String dim : dims) {
            totals.put(dim, labeled ? "" : "Total");
            labeled = true;
        }
        double totalRevenue = 0.0;
        double totalRevenueNet = 0.0;
        double totalCost = 0.0;
        int totalOrders = 0;
        double totalQty = 0.0;
        for (Map<String, Object> row : rows) {
            totalRevenue += asDouble(row.getOrDefault("revenue", 0.0));
            totalRevenueNet += asDouble(row.getOrDefault("revenue_net", 0.0));
            totalCost += asDouble(row.getOrDefault("cost", 0.0));
            totalOrders += (int) asDouble(row.getOrDefault("orders", 0.0));
            totalQty += asDouble(row.getOrDefault("quantity", 0.0));
        }
        double totalProfit = round(totalRevenue - totalCost);
        double totalProfitExGst = round(totalRevenueNet - totalCost);
        double totalMarginPct = totalRevenue > 0 ? round((totalProfit / totalRevenue) * 100.0) : 0.0;
        double totalMarginPctExGst = totalRevenueNet > 0 ? round((totalProfitExGst / totalRevenueNet) * 100.0) : 0.0;
        double totalAvgOrderValue = totalOrders > 0 ? round(totalRevenue / totalOrders) : 0.0;
        for (String metric : mets) {
            switch (metric) {
                case "revenue" -> totals.put("revenue", round(totalRevenue));
                case "cost" -> totals.put("cost", round(totalCost));
                case "profit" -> totals.put("profit", totalProfit);
                case "profit_ex_gst" -> totals.put("profit_ex_gst", totalProfitExGst);
                case "margin_pct" -> totals.put("margin_pct", totalMarginPct);
                case "margin_pct_ex_gst" -> totals.put("margin_pct_ex_gst", totalMarginPctExGst);
                case "orders" -> totals.put("orders", totalOrders);
                case "avg_order_value" -> totals.put("avg_order_value", totalAvgOrderValue);
                case "quantity" -> totals.put("quantity", round(totalQty));
            }
        }
        return totals;
    }

    private List<AnalyticsKpi> buildKpis(Map<String, Object> totals, List<String> mets) {
        List<AnalyticsKpi> kpis = new ArrayList<>();
        for (String metric : mets) {
            double value = asDouble(totals.getOrDefault(metric, 0.0));
            String label = switch (metric) {
                case "revenue" -> "Total Revenue";
                case "cost" -> "Total Cost";
                case "profit" -> "Total Profit (incl GST)";
                case "profit_ex_gst" -> "Total Profit (excl GST)";
                case "margin_pct" -> "Avg Margin (incl GST)";
                case "margin_pct_ex_gst" -> "Avg Margin (excl GST)";
                case "orders" -> "Total Orders";
                case "avg_order_value" -> "Avg Order Value";
                case "quantity" -> "Total Qty";
                default -> metric;
            };
            String valueType = switch (metric) {
                case "revenue", "cost", "profit", "profit_ex_gst", "avg_order_value" -> "CURRENCY";
                case "margin_pct", "margin_pct_ex_gst" -> "PERCENT";
                default -> "NUMBER";
            };
            kpis.add(new AnalyticsKpi(metric, label, value, valueType));
        }
        return kpis;
    }

    private Map<String, String> normalizeFilters(Map<String, String> filters) {
        if (filters == null || filters.isEmpty()) {
            return Map.of();
        }
        Map<String, String> normalized = new HashMap<>();
        for (Map.Entry<String, String> entry : filters.entrySet()) {
            if (entry.getKey() == null) {
                continue;
            }
            normalized.put(entry.getKey(), trimToEmpty(entry.getValue()));
        }
        return normalized;
    }

    private List<String> normalizeDims(List<String> dims) {
        if (dims == null || dims.isEmpty()) {
            return List.of("date");
        }
        return dims.stream()
                .filter(Objects::nonNull)
                .map(dim -> dim.trim().toLowerCase(Locale.ROOT))
                .filter(VALID_DIMS::contains)
                .distinct()
                .collect(Collectors.toList());
    }

    private List<String> normalizeMetrics(List<String> metrics) {
        if (metrics == null || metrics.isEmpty()) {
            return List.of("revenue", "profit", "orders");
        }
        return metrics.stream()
                .filter(Objects::nonNull)
                .map(metric -> metric.trim().toLowerCase(Locale.ROOT))
                .filter(VALID_METRICS::contains)
                .distinct()
                .collect(Collectors.toList());
    }

    private DateRange resolveRange(String from, String to) {
        if (!hasText(from) && !hasText(to)) {
            YearMonth now = YearMonth.now();
            return new DateRange(now.atDay(1).toString(), now.atEndOfMonth().toString());
        }
        LocalDate start = hasText(from) ? LocalDate.parse(from.trim()) : LocalDate.parse(to.trim());
        LocalDate end = hasText(to) ? LocalDate.parse(to.trim()) : start;
        if (start.isAfter(end)) {
            LocalDate temp = start;
            start = end;
            end = temp;
        }
        return new DateRange(start.toString(), end.toString());
    }

    // Build ERPNext "in" filter JSON: [["fieldname","in",["v1","v2"]]]
    private String buildInFilter(String field, List<String> values) {
        String safeField = field.replace("\\", "\\\\").replace("\"", "\\\"");
        String valuesJson = values.stream()
                .map(v -> "\"" + v.replace("\\", "\\\\").replace("\"", "\\\"") + "\"")
                .collect(Collectors.joining(",", "[", "]"));
        return "[[\"" + safeField + "\",\"in\"," + valuesJson + "]]";
    }

    private <T> List<List<T>> partition(List<T> source, int size) {
        List<List<T>> result = new ArrayList<>();
        for (int i = 0; i < source.size(); i += size) {
            result.add(source.subList(i, Math.min(i + size, source.size())));
        }
        return result;
    }

    private Object firstNonNull(Object... values) {
        if (values == null) {
            return null;
        }
        for (Object value : values) {
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            String text = trimToEmpty(value);
            if (!text.isBlank()) {
                return text;
            }
        }
        return "";
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isBlank();
    }

    private static String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeText(String value) {
        return trimToEmpty(value).toLowerCase(Locale.ROOT);
    }

    private String normalizeItemNameKey(String value) {
        return trimToEmpty(value).replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private LocalDate parseDate(String value) {
        String text = trimToEmpty(value);
        if (text.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(text);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String asString(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private double asDouble(Object value) {
        if (value instanceof Number number) {
            return number.doubleValue();
        }
        try {
            return value == null ? 0.0 : Double.parseDouble(value.toString());
        } catch (NumberFormatException ignored) {
            return 0.0;
        }
    }

    private double round(double value) {
        return Math.round(value * 100.0) / 100.0;
    }

    private static String displayItem(String itemCode, String itemName) {
        String code = trimToEmpty(itemCode);
        String name = trimToEmpty(itemName);
        if (code.isBlank()) {
            return name;
        }
        if (name.isBlank()) {
            return code;
        }
        return code + " - " + name;
    }

    private String toJson(List<List<String>> filters) {
        StringBuilder builder = new StringBuilder("[");
        for (int index = 0; index < filters.size(); index++) {
            List<String> entry = filters.get(index);
            builder.append("[");
            for (int inner = 0; inner < entry.size(); inner++) {
                builder.append("\"").append(entry.get(inner).replace("\\", "\\\\").replace("\"", "\\\"")).append("\"");
                if (inner < entry.size() - 1) {
                    builder.append(",");
                }
            }
            builder.append("]");
            if (index < filters.size() - 1) {
                builder.append(",");
            }
        }
        return builder.append("]").toString();
    }

    private List<AnalyticsColumn> b2bColumns() {
        return gstrColumns(List.of(
                "Receiver Name (Optional)",
                "Receiver GSTIN/UIN * (Required)",
                "Place of Supply * (Required)",
                "Invoice No * (Required)",
                "Invoice Date * (Required)",
                "Total Invoice Value * (Required)",
                "HSN/SAC",
                "Item Description",
                "UQC",
                "Quantity",
                "Taxable Value * (Required)",
                "Tax Rate * (Required)",
                "IGST Amount",
                "CGST Amount",
                "SGST/UT Amount",
                "Cess Amount",
                "Invoice Type",
                "Reverse Charge (Indicate if supply attracts reverse charge)",
                "Applicable % of Tax Rate",
                "GSTIN of E-Commerce Operator (If applicable)"));
    }

    private List<AnalyticsColumn> b2cColumns() {
        return gstrColumns(List.of(
                "Place of Supply * (Required)",
                "HSN/SAC",
                "Item Description",
                "UQC",
                "Quantity",
                "Total Taxable Value * (Required)",
                "Tax Rate * (Required)",
                "IGST Amount",
                "CGST Amount",
                "SGST/UT Amount",
                "Cess Amount",
                "GSTIN of E-Commerce Operator (If applicable)",
                "Applicable % of Tax Rate"));
    }

    private List<AnalyticsColumn> cdnrColumns() {
        return gstrColumns(List.of(
                "Name of the Recipient (Optional)",
                "Receiver GSTIN/UIN * (Required)",
                "Place of Supply * (Required)",
                "Note Type  (Debit/Credit/Refund) * (Required)",
                "Note Supply Type",
                "Reverse Charge",
                "Note/Refund Voucher Number * (Required)",
                "Note/Refund Voucher Date * (Required)",
                "Note/Refund Voucher Value * (Required)",
                "HSN/SAC",
                "Item Description",
                "UQC",
                "Quantity",
                "Taxable Value * (Required)",
                "Tax Rate * (Required)",
                "IGST Amount",
                "CGST Amount",
                "SGST/UT Amount",
                "Cess Amount",
                "Applicable % of Tax Rate"));
    }

    private List<AnalyticsColumn> hsnColumns() {
        return gstrColumns(List.of(
                "HSN Type * (Required from May-25)",
                "Description",
                "HSN/SAC * (Required)",
                "Quantity * (Required for HSN Code)",
                "UQC * (Required for HSN Code)",
                "Total Value * (Required till April-21)",
                "Total Taxable Value * (Required)",
                "Tax Rate (%) * (Required from May-21)",
                "IGST Amount * (Required if supply is Inter-State)",
                "CGST Amount * (Required if supply is Intra-State)",
                "SGST/UT Amount * (Required if supply is Intra-State)",
                "Cess Amount"));
    }

    private List<AnalyticsColumn> docsColumns() {
        return gstrColumns(List.of(
                "Nature of Document * (Required)",
                "Sr. No. * (Required)",
                "From",
                "To",
                "Total No of Invoices * (Required)",
                "Cancelled Invoices * (Required)",
                "Net Invoices Issued * (Required)"));
    }

    private List<AnalyticsColumn> gstrColumns(List<String> labels) {
        return labels.stream()
                .map(label -> new AnalyticsColumn(label, label, numericGstrColumn(label) ? "NUMBER" : "DIMENSION"))
                .toList();
    }

    private boolean numericGstrColumn(String label) {
        String normalized = label.toLowerCase(Locale.ROOT);
        return normalized.contains("value")
                || normalized.contains("amount")
                || normalized.contains("quantity")
                || normalized.contains("tax rate")
                || normalized.contains("total no")
                || normalized.contains("cancelled")
                || normalized.contains("net invoices");
    }

    private String placeOfSupply(CustomerTaxMeta customer, String companyGstin) {
        String stateCode = firstNonBlank(stateCode(customer.gstin()), stateCode(companyGstin));
        if (stateCode.isBlank()) {
            return "";
        }
        return stateCode + "-" + GST_STATE_NAMES.getOrDefault(stateCode, "Unknown");
    }

    private boolean isInterstate(String customerGstin, String companyGstin) {
        String customerState = stateCode(customerGstin);
        String companyState = stateCode(companyGstin);
        return !customerState.isBlank() && !companyState.isBlank() && !customerState.equals(companyState);
    }

    private String stateCode(String gstin) {
        String text = trimToEmpty(gstin);
        if (text.length() < 2) {
            return "";
        }
        String code = text.substring(0, 2);
        return code.chars().allMatch(Character::isDigit) ? code : "";
    }

    private String noteTypeLabel(String value) {
        String normalized = trimToEmpty(value).toUpperCase(Locale.ROOT);
        if (normalized.contains("DEBIT")) {
            return "Debit";
        }
        if (normalized.contains("REFUND")) {
            return "Refund";
        }
        return "Credit";
    }

    private static final Map<String, String> GST_STATE_NAMES = Map.ofEntries(
            Map.entry("01", "Jammu and Kashmir"),
            Map.entry("02", "Himachal Pradesh"),
            Map.entry("03", "Punjab"),
            Map.entry("04", "Chandigarh"),
            Map.entry("05", "Uttarakhand"),
            Map.entry("06", "Haryana"),
            Map.entry("07", "Delhi"),
            Map.entry("08", "Rajasthan"),
            Map.entry("09", "Uttar Pradesh"),
            Map.entry("10", "Bihar"),
            Map.entry("11", "Sikkim"),
            Map.entry("12", "Arunachal Pradesh"),
            Map.entry("13", "Nagaland"),
            Map.entry("14", "Manipur"),
            Map.entry("15", "Mizoram"),
            Map.entry("16", "Tripura"),
            Map.entry("17", "Meghalaya"),
            Map.entry("18", "Assam"),
            Map.entry("19", "West Bengal"),
            Map.entry("20", "Jharkhand"),
            Map.entry("21", "Odisha"),
            Map.entry("22", "Chhattisgarh"),
            Map.entry("23", "Madhya Pradesh"),
            Map.entry("24", "Gujarat"),
            Map.entry("25", "Daman and Diu"),
            Map.entry("26", "Dadra and Nagar Haveli and Daman and Diu"),
            Map.entry("27", "Maharashtra"),
            Map.entry("28", "Andhra Pradesh"),
            Map.entry("29", "Karnataka"),
            Map.entry("30", "Goa"),
            Map.entry("31", "Lakshadweep"),
            Map.entry("32", "Kerala"),
            Map.entry("33", "Tamil Nadu"),
            Map.entry("34", "Puducherry"),
            Map.entry("35", "Andaman and Nicobar Islands"),
            Map.entry("36", "Telangana"),
            Map.entry("37", "Andhra Pradesh"),
            Map.entry("38", "Ladakh"),
            Map.entry("97", "Other Territory"));

    private record DateRange(String start, String end) {}

    private record CustomerTaxMeta(String label, String gstin) {
        static CustomerTaxMeta empty(String label) {
            return new CustomerTaxMeta(label, "");
        }
    }

    private record ItemTaxMeta(String itemName, String hsn, String uqc, double gstRate) {
        static ItemTaxMeta empty(String itemCode) {
            return new ItemTaxMeta(itemCode, "", "Nos", 0.0);
        }
    }

    private record GstrLine(
            String receiverName,
            String receiverGstin,
            String placeOfSupply,
            String invoiceNo,
            String invoiceDate,
            double invoiceValue,
            String hsn,
            String itemDescription,
            String uqc,
            double quantity,
            double taxableValue,
            double taxRate,
            double igst,
            double cgst,
            double sgst) {}

    private record GstrContext(
            List<Map<String, Object>> invoices,
            Map<String, List<Map<String, Object>>> invoiceItems,
            Map<String, CustomerTaxMeta> customerMeta,
            Map<String, ItemTaxMeta> itemMeta,
            Map<String, ItemTaxMeta> itemMetaByName,
            String companyGstin,
            List<String> warnings,
            HsnAudit hsnAudit) {
        CustomerTaxMeta customerMeta(String customerId) {
            return customerMeta.getOrDefault(trimToEmpty(customerId), CustomerTaxMeta.empty(trimToEmpty(customerId)));
        }

        List<Map<String, Object>> invoiceItems(String invoiceId) {
            return invoiceItems.getOrDefault(trimToEmpty(invoiceId), List.of());
        }
    }

    private static class HsnAudit {
        private int missingCount;
        private int invalidCount;
        private final Set<String> missingExamples = new LinkedHashSet<>();
        private final Set<String> invalidExamples = new LinkedHashSet<>();

        void recordMissing(String itemLabel) {
            missingCount++;
            addExample(missingExamples, itemLabel);
        }

        void recordInvalid(String itemLabel, String hsn) {
            invalidCount++;
            addExample(invalidExamples, itemLabel + " [" + hsn + "]");
        }

        List<String> warnings() {
            List<String> result = new ArrayList<>();
            if (invalidCount > 0) {
                result.add("GSTR-1 HSN audit: " + invalidCount
                        + " invoice line(s) have invalid or suspicious HSN values and were left blank. Examples: "
                        + String.join(", ", invalidExamples) + ".");
            }
            if (missingCount > 0) {
                result.add("GSTR-1 HSN audit: " + missingCount
                        + " invoice line(s) have no HSN in invoice or item master and were left blank. Examples: "
                        + String.join(", ", missingExamples) + ".");
            }
            return result;
        }

        private void addExample(Set<String> examples, String value) {
            if (examples.size() >= HSN_WARNING_EXAMPLE_LIMIT) {
                return;
            }
            String text = trimToEmpty(value);
            examples.add(text.isBlank() ? "Unknown item" : text);
        }
    }

    private static class GstrAggregate {
        final String placeOfSupply;
        final String hsn;
        final String itemDescription;
        final String uqc;
        final double taxRate;
        double quantity;
        double taxableValue;
        double igst;
        double cgst;
        double sgst;

        GstrAggregate(String placeOfSupply, String hsn, String itemDescription, String uqc, double taxRate) {
            this.placeOfSupply = placeOfSupply;
            this.hsn = hsn;
            this.itemDescription = itemDescription;
            this.uqc = uqc;
            this.taxRate = taxRate;
        }

        void add(GstrLine line) {
            quantity += line.quantity();
            taxableValue += line.taxableValue();
            igst += line.igst();
            cgst += line.cgst();
            sgst += line.sgst();
        }
    }

    private record FactRow(
            String invoiceId,
            String sourceOrderId,
            String date,
            String vendor,
            String branch,
            String itemGroup,
            String itemCode,
            String itemName,
            double revenue,
            double revenueNet,
            double cost,
            double qty) {
        String itemLabel() {
            return displayItem(itemCode, itemName);
        }
    }

    private record PreparedLine(String itemCode, String itemName, String itemGroup, double amount, double directCost, double qty) {}

    private record SourceOrderLine(int index, String itemCode, String itemName, String itemGroup, double directCost) {}

    private record ItemMeta(String itemGroup, String vendor) {
        static ItemMeta empty() {
            return new ItemMeta("", "");
        }
    }

    private static class AggRow {
        final Map<String, Object> dims = new LinkedHashMap<>();
        double revenue;
        double revenueNet;
        double cost;
        double qty;
        final Set<String> orderIds = new HashSet<>();
    }

    private enum Grouping {
        DAY {
            @Override
            PeriodKey periodFor(LocalDate date) {
                String value = date.toString();
                return new PeriodKey(value, value);
            }
        },
        WEEK {
            @Override
            PeriodKey periodFor(LocalDate date) {
                LocalDate start = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                return new PeriodKey(start.toString(), start.plusDays(6).toString());
            }
        },
        MONTH {
            @Override
            PeriodKey periodFor(LocalDate date) {
                YearMonth yearMonth = YearMonth.from(date);
                return new PeriodKey(yearMonth.atDay(1).toString(), yearMonth.atEndOfMonth().toString());
            }
        },
        QUARTER {
            @Override
            PeriodKey periodFor(LocalDate date) {
                int startMonth = ((date.getMonthValue() - 1) / 3) * 3 + 1;
                LocalDate start = LocalDate.of(date.getYear(), startMonth, 1);
                return new PeriodKey(start.toString(), start.plusMonths(2).with(TemporalAdjusters.lastDayOfMonth()).toString());
            }
        };

        abstract PeriodKey periodFor(LocalDate date);

        static Grouping parse(String raw) {
            return switch (raw == null ? "" : raw.trim().toLowerCase(Locale.ROOT)) {
                case "week", "weekly" -> WEEK;
                case "month", "monthly" -> MONTH;
                case "quarter", "quarterly" -> QUARTER;
                default -> DAY;
            };
        }
    }

    private record PeriodKey(String start, String end) {}
}
