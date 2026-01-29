package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.dto.PaymentRequest;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class PaymentService {

    private final ErpNextClient erpNextClient;

    public PaymentService(ErpNextClient erpNextClient) {
        this.erpNextClient = erpNextClient;
    }

    public Map<String, Object> createPayment(PaymentRequest request) {
        Map<String, Object> company = erpNextClient.getResource("Company", request.getCompany());
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
        return erpNextClient.createResource("Payment Entry", payload);
    }

    private String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
