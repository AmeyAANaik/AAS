package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.dto.PaymentRequest;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    private final ErpNextClient erpNextClient;

    public PaymentService(ErpNextClient erpNextClient) {
        this.erpNextClient = erpNextClient;
    }

    public Map<String, Object> createPayment(PaymentRequest request) {
        Map<String, Object> response = erpNextClient.getResource("Company", request.getCompany());
        Map<String, Object> company = unwrap(response);
        String receivable = asString(company.get("default_receivable_account"));
        String cash = asString(company.get("default_cash_account"));
        if (receivable == null || cash == null) {
            throw new IllegalStateException("Company default accounts missing");
        }
        Map<String, Object> payload = new HashMap<>();
        payload.put("payment_type", "Receive");
        payload.put("party_type", "Customer");
        payload.put("party", request.getCustomer());
        payload.put("company", request.getCompany());
        payload.put("paid_amount", request.getAmount());
        payload.put("received_amount", request.getAmount());
        payload.put("paid_from", receivable);
        payload.put("paid_to", cash);
        if (request.getReferenceNo() != null && !request.getReferenceNo().isBlank()) {
            payload.put("reference_no", request.getReferenceNo());
        }
        if (request.getReferenceDate() != null && !request.getReferenceDate().isBlank()) {
            payload.put("reference_date", request.getReferenceDate());
        }
        BigDecimal amount = request.getAmount() == null ? BigDecimal.ZERO : request.getAmount();
        if (request.getInvoiceId() != null && !request.getInvoiceId().isBlank()) {
            Map<String, Object> invoiceDoc = loadInvoice(request.getInvoiceId());
            BigDecimal outstanding = asDecimal(invoiceDoc.get("outstanding_amount"));
            BigDecimal allocated = amount.min(outstanding);
            payload.put("references", List.of(buildReference(request.getInvoiceId(), allocated)));
            BigDecimal surplus = amount.subtract(allocated);
            if (surplus.compareTo(BigDecimal.ZERO) > 0) {
                payload.put("unallocated_amount", surplus);
            }
        }
        Map<String, Object> created = erpNextClient.createResource("Payment Entry", payload);
        Map<String, Object> createdDoc = unwrapDoc(created);
        Map<String, Object> submitted = erpNextClient.submitDoc(createdDoc);
        return unwrapDoc(submitted);
    }

    private Map<String, Object> buildReference(String invoiceId, BigDecimal amount) {
        Map<String, Object> reference = new HashMap<>();
        reference.put("reference_doctype", "Sales Invoice");
        reference.put("reference_name", invoiceId);
        reference.put("allocated_amount", amount);
        return reference;
    }

    private Map<String, Object> loadInvoice(String invoiceId) {
        Map<String, Object> invoice = unwrapDoc(erpNextClient.getResource("Sales Invoice", invoiceId));
        int docstatus = asInt(invoice.get("docstatus"));
        if (docstatus == 0) {
            erpNextClient.submitDoc(invoice);
            invoice = unwrapDoc(erpNextClient.getResource("Sales Invoice", invoiceId));
        }
        return invoice;
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private BigDecimal asDecimal(Object value) {
        if (value instanceof BigDecimal number) {
            return number;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        if (value == null) {
            return BigDecimal.ZERO;
        }
        try {
            return new BigDecimal(value.toString());
        } catch (Exception ex) {
            return BigDecimal.ZERO;
        }
    }

    private int asInt(Object value) {
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (Exception ex) {
            return 0;
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> unwrapDoc(Map<String, Object> response) {
        if (response == null) {
            return Map.of();
        }
        Object data = response.get("data");
        if (data instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        Object message = response.get("message");
        if (message instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        return response;
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
