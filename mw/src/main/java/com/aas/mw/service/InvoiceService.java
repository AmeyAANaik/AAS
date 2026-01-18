package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.dto.InvoiceRequest;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class InvoiceService {

    private static final String DOCTYPE = "Sales Invoice";

    private final ErpNextClient erpNextClient;

    public InvoiceService(ErpNextClient erpNextClient) {
        this.erpNextClient = erpNextClient;
    }

    public Map<String, Object> createInvoice(InvoiceRequest request) {
        return erpNextClient.createResource(DOCTYPE, request.getFields());
    }
}
