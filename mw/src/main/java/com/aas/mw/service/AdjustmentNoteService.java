package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.dto.AdjustmentNoteRequest;
import com.aas.mw.dto.UploadedFileInfo;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class AdjustmentNoteService {

    private static final DateTimeFormatter ERP_DATETIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final ErpNextClient erpNextClient;
    private final PaymentDueService paymentDueService;
    private final ErpNextFileService fileService;
    private final AdjustmentNoteErpService adjustmentNoteErpService;

    public AdjustmentNoteService(
            ErpNextClient erpNextClient,
            PaymentDueService paymentDueService,
            ErpNextFileService fileService,
            AdjustmentNoteErpService adjustmentNoteErpService) {
        this.erpNextClient = erpNextClient;
        this.paymentDueService = paymentDueService;
        this.fileService = fileService;
        this.adjustmentNoteErpService = adjustmentNoteErpService;
    }

    public List<Map<String, Object>> listItemOptions() {
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\",\"item_code\",\"item_name\",\"item_group\"]");
        params.put("filters", "[[\"Item\",\"disabled\",\"=\",0]]");
        params.put("limit_page_length", 500);
        params.put("order_by", "item_name asc");
        List<Map<String, Object>> items = erpNextClient.listResources("Item", params);
        if (items == null) {
            return List.of();
        }
        return items.stream().map(item -> {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("id", adjustmentNoteErpService.asText(item.get("item_code")));
            out.put("name", adjustmentNoteErpService.asText(item.get("item_name")));
            out.put("code", adjustmentNoteErpService.asText(item.get("item_code")));
            out.put("group", adjustmentNoteErpService.asText(item.get("item_group")));
            return out;
        }).filter(out -> !((String) out.get("id")).isBlank()).toList();
    }

    public Map<String, Object> createDraftWithAttachments(
            AdjustmentNoteRequest request,
            MultipartFile[] files,
            String createdBy,
            String sessionCookie) {
        validateRequest(request, files);

        String partyType = adjustmentNoteErpService.normalizePartyType(request.getPartyType());
        String direction = adjustmentNoteErpService.normalizeDirection(request.getDirection());
        String partyId = adjustmentNoteErpService.asText(request.getPartyId());
        String categoryId = adjustmentNoteErpService.asText(request.getCategoryId());
        String itemCode = adjustmentNoteErpService.asText(request.getItemCode());
        String itemName = adjustmentNoteErpService.asText(request.getItemName());
        BigDecimal amount = request.getAmount() == null ? BigDecimal.ZERO : request.getAmount();

        InvoiceContext invoiceContext = resolveInvoiceContext(partyType);

        BigDecimal dueSnapshot = BigDecimal.ZERO;
        try {
            Map<String, Object> due = paymentDueService.dueByCategory(partyType, partyId, categoryId);
            dueSnapshot = adjustmentNoteErpService.asDecimal(due.get("dueAmount"));
            validateReducibleAmountLimit(
                    partyType,
                    direction,
                    amount,
                    adjustmentNoteErpService.asDecimal(due.get("availableDueAmount")),
                    adjustmentNoteErpService.asDecimal(due.get("pendingAdjustmentAmount")));
        } catch (IllegalArgumentException ex) {
            throw ex;
        } catch (Exception ignored) {
            dueSnapshot = BigDecimal.ZERO;
        }

        String company = resolveDraftCompany(partyType, partyId, categoryId, invoiceContext, Map.of());
        Map<String, Object> companyDoc = adjustmentNoteErpService.unwrapDoc(erpNextClient.getResource("Company", company));
        String partyAccount = partyType.equals(AdjustmentNoteErpService.PARTY_SUPPLIER)
                ? adjustmentNoteErpService.asText(companyDoc.get("default_payable_account"))
                : adjustmentNoteErpService.asText(companyDoc.get("default_receivable_account"));
        String offsetAccount = resolveTemporaryOpeningAccount(company, companyDoc);
        if (partyAccount.isBlank()) {
            throw new IllegalStateException("Party account is not configured for company " + company + ".");
        }
        if (offsetAccount.isBlank()) {
            throw new IllegalStateException("Temporary Opening Account is not configured for company " + company + ".");
        }

        String actor = createdBy == null ? "" : createdBy.trim();
        String now = LocalDateTime.now(ZoneId.systemDefault()).format(ERP_DATETIME);
        String postingDate = request.getNoteDate() == null || request.getNoteDate().isBlank()
                ? LocalDate.now(ZoneId.systemDefault()).toString()
                : request.getNoteDate().trim();

        Map<String, Object> payload = new HashMap<>();
        payload.put("voucher_type", "Journal Entry");
        payload.put("posting_date", postingDate);
        payload.put("company", company);
        payload.put("user_remark", buildRemark(partyType, partyId, direction, itemCode, itemName, request.getReason()));
        payload.put(AdjustmentNoteErpService.FIELD_REVIEW_STATUS, AdjustmentNoteErpService.STATUS_UNDER_REVIEW);
        payload.put(AdjustmentNoteErpService.FIELD_DIRECTION, direction);
        payload.put(AdjustmentNoteErpService.FIELD_NOTE_TYPE, adjustmentNoteErpService.noteTypeForDirection(direction));
        payload.put(AdjustmentNoteErpService.FIELD_PARTY_TYPE, partyType);
        payload.put(AdjustmentNoteErpService.FIELD_PARTY, partyId);
        payload.put(AdjustmentNoteErpService.FIELD_CATEGORY, categoryId);
        // Store item code in aas_reference_invoice for ledger display compatibility
        payload.put(AdjustmentNoteErpService.FIELD_REFERENCE_INVOICE, itemCode);
        payload.put(AdjustmentNoteErpService.FIELD_ITEM_NAME, itemName);
        payload.put(AdjustmentNoteErpService.FIELD_AMOUNT, amount);
        payload.put(AdjustmentNoteErpService.FIELD_DUE_AMOUNT, dueSnapshot);
        payload.put(AdjustmentNoteErpService.FIELD_REASON, adjustmentNoteErpService.asText(request.getReason()));
        if (!actor.isBlank()) {
            payload.put(AdjustmentNoteErpService.FIELD_CREATED_BY, actor);
        }
        payload.put(AdjustmentNoteErpService.FIELD_CREATED_AT, now);
        payload.put("accounts", buildAccounts(direction, amount, partyType, partyId, partyAccount, offsetAccount));

        Map<String, Object> created = adjustmentNoteErpService.unwrapDoc(erpNextClient.createResource(AdjustmentNoteErpService.JOURNAL_ENTRY, payload));
        String noteId = adjustmentNoteErpService.asText(created.get("name"));

        List<UploadedFileInfo> uploadedFiles = new ArrayList<>();

        // Generate and attach the credit/debit note PDF inline at creation time.
        // Prefer the branded "AAS Adjustment Note Print" format so the draft matches the
        // application's standard document layout; fall back to a plain PDF if ERPNext
        // rendering is unavailable so note creation never fails for lack of a PDF.
        String kind = AdjustmentNoteErpService.DIRECTION_TAKE.equals(direction) ? "Debit Note" : "Credit Note";
        try {
            byte[] pdfBytes = renderBrandedNotePdf(noteId, sessionCookie);
            if (!isPdf(pdfBytes)) {
                pdfBytes = buildNotePdf(noteId, kind, partyType, partyId, categoryId, itemCode, itemName,
                        amount, postingDate, adjustmentNoteErpService.asText(request.getReason()), company);
            }
            String pdfFilename = noteId.toLowerCase().replace("/", "-") + "-" + kind.toLowerCase().replace(" ", "-") + ".pdf";
            UploadedFileInfo pdfFile = fileService.uploadBytes(
                    AdjustmentNoteErpService.JOURNAL_ENTRY, noteId, pdfFilename, pdfBytes, "application/pdf", sessionCookie);
            uploadedFiles.add(pdfFile);
        } catch (Exception ignored) {
            // PDF generation is best-effort; don't fail note creation if PDF fails
        }

        for (MultipartFile file : files) {
            if (file == null || file.isEmpty()) {
                continue;
            }
            uploadedFiles.add(fileService.uploadFile(
                    AdjustmentNoteErpService.JOURNAL_ENTRY,
                    noteId,
                    file.getOriginalFilename() == null ? "adjustment_note" : file.getOriginalFilename(),
                    file,
                    sessionCookie));
        }
        Map<String, Object> reloaded = adjustmentNoteErpService.getNote(noteId);
        return Map.of(
                "note", reloaded,
                "files", uploadedFiles);
    }

    private void validateReducibleAmountLimit(
            String partyType,
            String direction,
            BigDecimal amount,
            BigDecimal availableDueAmount,
            BigDecimal pendingAdjustmentAmount) {
        if (!reducesDue(partyType, direction)) {
            return;
        }
        BigDecimal available = safe(availableDueAmount).add(minZero(safe(pendingAdjustmentAmount)));
        if (safe(amount).compareTo(available.max(BigDecimal.ZERO)) > 0) {
            throw new IllegalArgumentException("Adjustment amount exceeds available due for this category.");
        }
    }

    private boolean reducesDue(String partyType, String direction) {
        String normalizedPartyType = adjustmentNoteErpService.normalizePartyType(partyType);
        String normalizedDirection = adjustmentNoteErpService.normalizeDirection(direction);
        return AdjustmentNoteErpService.PARTY_SUPPLIER.equals(normalizedPartyType)
                ? AdjustmentNoteErpService.DIRECTION_TAKE.equals(normalizedDirection)
                : AdjustmentNoteErpService.DIRECTION_GIVE.equals(normalizedDirection);
    }

    private BigDecimal safe(BigDecimal value) {
        return value == null ? BigDecimal.ZERO : value;
    }

    private BigDecimal minZero(BigDecimal value) {
        if (value == null || value.compareTo(BigDecimal.ZERO) >= 0) {
            return BigDecimal.ZERO;
        }
        return value;
    }

    private void validateRequest(AdjustmentNoteRequest request, MultipartFile[] files) {
        if (request == null) {
            throw new IllegalArgumentException("Adjustment note payload is required.");
        }
        if (adjustmentNoteErpService.asText(request.getPartyType()).isBlank()
                || adjustmentNoteErpService.asText(request.getPartyId()).isBlank()
                || adjustmentNoteErpService.asText(request.getCategoryId()).isBlank()
                || adjustmentNoteErpService.asText(request.getItemCode()).isBlank()
                || adjustmentNoteErpService.asText(request.getDirection()).isBlank()
                || request.getAmount() == null
                || request.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("partyType, partyId, categoryId, itemCode, direction, and amount are required.");
        }
        if (files == null || files.length == 0) {
            throw new IllegalArgumentException("Evidence upload is required.");
        }
        boolean hasFile = false;
        for (MultipartFile file : files) {
            if (file != null && !file.isEmpty()) {
                hasFile = true;
                break;
            }
        }
        if (!hasFile) {
            throw new IllegalArgumentException("Evidence upload is required.");
        }
    }

    private List<Map<String, Object>> buildAccounts(
            String direction,
            BigDecimal amount,
            String partyType,
            String partyId,
            String partyAccount,
            String offsetAccount) {
        List<Map<String, Object>> accounts = new ArrayList<>();
        Map<String, Object> partyLine = new HashMap<>();
        partyLine.put("account", partyAccount);
        partyLine.put("party_type", partyType);
        partyLine.put("party", partyId);
        Map<String, Object> offsetLine = new HashMap<>();
        offsetLine.put("account", offsetAccount);
        if (AdjustmentNoteErpService.DIRECTION_TAKE.equals(direction)) {
            partyLine.put("debit_in_account_currency", amount);
            offsetLine.put("credit_in_account_currency", amount);
        } else {
            partyLine.put("credit_in_account_currency", amount);
            offsetLine.put("debit_in_account_currency", amount);
        }
        accounts.add(partyLine);
        accounts.add(offsetLine);
        return accounts;
    }

    private String buildRemark(String partyType, String partyId, String direction, String itemCode, String itemName, String reason) {
        String kind = AdjustmentNoteErpService.DIRECTION_TAKE.equals(direction) ? "Debit Note" : "Credit Note";
        String text = adjustmentNoteErpService.asText(reason);
        String remark = kind + " · " + partyType + " " + partyId;
        String displayItem = adjustmentNoteErpService.asText(itemName).isBlank() ? itemCode : itemName;
        if (!adjustmentNoteErpService.asText(itemCode).isBlank()) {
            remark = remark + " · Item " + displayItem;
        }
        return text.isBlank() ? remark : remark + " · " + text;
    }

    // Render the credit/debit note using the shared branded ERPNext print format so it matches
    // the application's standard document layout. Returns null if rendering is unavailable.
    private byte[] renderBrandedNotePdf(String noteId, String sessionCookie) {
        try {
            return erpNextClient.downloadPdfWithSession(
                    AdjustmentNoteErpService.JOURNAL_ENTRY,
                    noteId,
                    Map.of("format", AdjustmentNotePdfService.PRINT_FORMAT_NAME),
                    sessionCookie);
        } catch (Exception ex) {
            return null;
        }
    }

    private boolean isPdf(byte[] bytes) {
        return bytes != null && bytes.length > 4
                && bytes[0] == '%' && bytes[1] == 'P' && bytes[2] == 'D' && bytes[3] == 'F';
    }

    private byte[] buildNotePdf(
            String noteId,
            String kind,
            String partyType,
            String partyId,
            String categoryId,
            String itemCode,
            String itemName,
            BigDecimal amount,
            String postingDate,
            String reason,
            String company) throws IOException {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                float margin = 44f;
                float y = page.getMediaBox().getHeight() - margin;

                y = writeLine(content, bold, 16f, margin, y, kind);
                y = writeLine(content, regular, 9f, margin, y - 4f, "Computer generated adjustment note — pending admin approval.");
                y -= 10f;
                y = writeLine(content, regular, 10f, margin, y, "Note ID   : " + noteId);
                y = writeLine(content, regular, 10f, margin, y - 2f, "Company   : " + company);
                y = writeLine(content, regular, 10f, margin, y - 2f, "Party     : " + partyType + " — " + partyId);
                y = writeLine(content, regular, 10f, margin, y - 2f, "Category  : " + categoryId);
                y -= 10f;
                if (!itemName.isBlank()) {
                    y = writeLine(content, bold, 11f, margin, y, "Item");
                    y = writeLine(content, regular, 10f, margin, y - 2f, "Name : " + itemName);
                    y -= 10f;
                }
                y = writeLine(content, bold, 12f, margin, y, "Amount    : " + amount.toPlainString());
                y = writeLine(content, regular, 10f, margin, y - 2f, "Date      : " + postingDate);
                if (!reason.isBlank()) {
                    y = writeLine(content, regular, 10f, margin, y - 2f, "Reason    : " + reason);
                }
                y -= 14f;
                writeLine(content, regular, 8f, margin, y, "This note is under admin review and has not been approved yet.");
            }
            document.save(out);
            return out.toByteArray();
        }
    }

    private float writeLine(PDPageContentStream content, PDType1Font font, float fontSize, float x, float y, String text) throws IOException {
        content.beginText();
        content.setFont(font, fontSize);
        content.newLineAtOffset(x, y);
        content.showText(text == null ? "" : text);
        content.endText();
        return y - fontSize - 2f;
    }

    private InvoiceContext resolveInvoiceContext(String partyType) {
        if (AdjustmentNoteErpService.PARTY_SUPPLIER.equals(partyType)) {
            return new InvoiceContext("Purchase Invoice", "supplier");
        }
        return new InvoiceContext("Sales Invoice", "customer");
    }

    private String resolveDraftCompany(
            String partyType,
            String partyId,
            String categoryId,
            InvoiceContext invoiceContext,
            Map<String, Object> invoice) {
        String invoiceCompany = adjustmentNoteErpService.asText(invoice.get("company"));
        if (!invoiceCompany.isBlank()) {
            return invoiceCompany;
        }

        String discovered = resolveCompanyFromPartyHistory(partyId, categoryId, invoiceContext);
        if (!discovered.isBlank()) {
            return discovered;
        }

        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\"]");
        params.put("limit_page_length", 1);
        List<Map<String, Object>> companies = erpNextClient.listResources("Company", params);
        if (companies != null && !companies.isEmpty()) {
            String first = adjustmentNoteErpService.asText(companies.get(0).get("name"));
            if (!first.isBlank()) {
                return first;
            }
        }

        throw new IllegalStateException("Unable to resolve company for the selected " + partyType + ".");
    }

    private String resolveCompanyFromPartyHistory(String partyId, String categoryId, InvoiceContext invoiceContext) {
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\",\"company\",\"" + AdjustmentNoteErpService.FIELD_CATEGORY + "\"]");
        params.put("order_by", "posting_date desc");
        params.put("limit_page_length", 20);
        List<List<String>> filters = new ArrayList<>();
        filters.add(List.of(invoiceContext.partyField(), "=", partyId));
        filters.add(List.of("docstatus", "!=", "2"));
        params.put("filters", adjustmentNoteErpService.toJson(filters));
        List<Map<String, Object>> invoices = erpNextClient.listResources(invoiceContext.doctype(), params);
        if (invoices == null || invoices.isEmpty()) {
            return "";
        }
        for (Map<String, Object> row : invoices) {
            String rowCategory = adjustmentNoteErpService.asText(row.get(AdjustmentNoteErpService.FIELD_CATEGORY));
            String rowCompany = adjustmentNoteErpService.asText(row.get("company"));
            if (!rowCompany.isBlank() && (categoryId.isBlank() || rowCategory.isBlank() || rowCategory.equals(categoryId))) {
                return rowCompany;
            }
        }
        for (Map<String, Object> row : invoices) {
            String rowCompany = adjustmentNoteErpService.asText(row.get("company"));
            if (!rowCompany.isBlank()) {
                return rowCompany;
            }
        }
        return "";
    }

    private String resolveTemporaryOpeningAccount(String companyName, Map<String, Object> companyDoc) {
        String configured = adjustmentNoteErpService.asText(companyDoc.get("temporary_opening_account"));
        if (!configured.isBlank()) {
            return configured;
        }
        String companyAbbr = adjustmentNoteErpService.asText(companyDoc.get("abbr"));
        String ensured = ensureTemporaryOpeningAccountDoc(companyName, companyAbbr);
        if (ensured.isBlank()) {
            return "";
        }
        try {
            erpNextClient.updateResource("Company", companyName, Map.of("temporary_opening_account", ensured));
        } catch (Exception ignored) {
            // Best effort: continue using the ensured account for this draft even if Company write-back fails.
        }
        return ensured;
    }

    private String ensureTemporaryOpeningAccountDoc(String companyName, String companyAbbr) {
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\",\"account_name\",\"company\"]");
        params.put("filters", adjustmentNoteErpService.toJson(List.of(
                List.of("company", "=", companyName),
                List.of("account_name", "=", "Temporary Opening"))));
        params.put("limit_page_length", 5);
        List<Map<String, Object>> existing = erpNextClient.listResources("Account", params);
        if (existing != null) {
            for (Map<String, Object> account : existing) {
                if (account == null) {
                    continue;
                }
                if ("Temporary Opening".equalsIgnoreCase(adjustmentNoteErpService.asText(account.get("account_name")))
                        && companyName.equalsIgnoreCase(adjustmentNoteErpService.asText(account.get("company")))) {
                    return adjustmentNoteErpService.asText(account.get("name"));
                }
            }
        }

        String parent = resolveTemporaryOpeningParent(companyName, companyAbbr);
        Map<String, Object> payload = new HashMap<>();
        payload.put("account_name", "Temporary Opening");
        payload.put("company", companyName);
        payload.put("is_group", 0);
        if (!parent.isBlank()) {
            payload.put("parent_account", parent);
        }
        payload.put("root_type", "Equity");
        payload.put("report_type", "Balance Sheet");
        Map<String, Object> created = erpNextClient.createResource("Account", payload);
        return adjustmentNoteErpService.asText(adjustmentNoteErpService.unwrapDoc(created).get("name"));
    }

    private String resolveTemporaryOpeningParent(String companyName, String companyAbbr) {
        String abbr = companyAbbr == null ? "" : companyAbbr.trim();
        Map<String, Object> params = new HashMap<>();
        params.put("fields", "[\"name\",\"account_name\",\"company\",\"is_group\",\"root_type\"]");
        params.put("filters", adjustmentNoteErpService.toJson(List.of(
                List.of("company", "=", companyName),
                List.of("is_group", "=", "1"))));
        params.put("limit_page_length", 500);
        List<Map<String, Object>> accounts;
        try {
            accounts = erpNextClient.listResources("Account", params);
        } catch (Exception ex) {
            return "";
        }
        if (accounts == null || accounts.isEmpty()) {
            return "";
        }
        for (Map<String, Object> account : accounts) {
            if ("Equity".equalsIgnoreCase(adjustmentNoteErpService.asText(account.get("root_type")))
                    && "Temporary Opening".equalsIgnoreCase(adjustmentNoteErpService.asText(account.get("account_name")))) {
                return adjustmentNoteErpService.asText(account.get("name"));
            }
        }
        for (Map<String, Object> account : accounts) {
            if ("Equity".equalsIgnoreCase(adjustmentNoteErpService.asText(account.get("root_type")))) {
                String name = adjustmentNoteErpService.asText(account.get("name"));
                if (!abbr.isBlank() && name.endsWith(" - " + abbr)) {
                    return name;
                }
            }
        }
        for (Map<String, Object> account : accounts) {
            if ("Equity".equalsIgnoreCase(adjustmentNoteErpService.asText(account.get("root_type")))) {
                return adjustmentNoteErpService.asText(account.get("name"));
            }
        }
        return "";
    }

    private record InvoiceContext(String doctype, String partyField) {}
}
