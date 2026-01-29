package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.dto.FieldsRequest;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class VendorAssignmentService {

    private static final String DOCTYPE = "Sales Order";

    private final ErpNextClient erpNextClient;

    public VendorAssignmentService(ErpNextClient erpNextClient) {
        this.erpNextClient = erpNextClient;
    }

    public Map<String, Object> assignVendor(String orderId, FieldsRequest request) {
        return erpNextClient.updateResource(DOCTYPE, orderId, request.getFields());
    }
}
