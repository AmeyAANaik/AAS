package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.dto.FieldsRequest;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class VendorAssignmentService {

    private static final String DOCTYPE = "Sales Order";

    private final ErpNextClient erpNextClient;
    private final OrderFlowStateMachine orderFlowStateMachine;

    public VendorAssignmentService(ErpNextClient erpNextClient, OrderFlowStateMachine orderFlowStateMachine) {
        this.erpNextClient = erpNextClient;
        this.orderFlowStateMachine = orderFlowStateMachine;
    }

    public Map<String, Object> assignVendor(String orderId, FieldsRequest request) {
        Map<String, Object> fields = request.getFields();
        if (!fields.containsKey("aas_vendor") || String.valueOf(fields.get("aas_vendor")).isBlank()) {
            throw new IllegalArgumentException("Vendor is required.");
        }
        Map<String, Object> current = erpNextClient.getResource(DOCTYPE, orderId);
        String currentStatus = readField(current, "aas_status");
        orderFlowStateMachine.ensureCanAssignVendor(currentStatus);
        if (!fields.containsKey("aas_status")) {
            fields.put("aas_status", "VENDOR_ASSIGNED");
        }
        return erpNextClient.updateResource(DOCTYPE, orderId, fields);
    }

    @SuppressWarnings("unchecked")
    private String readField(Map<String, Object> resource, String fieldName) {
        if (resource == null) {
            return "";
        }
        Object data = resource.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            return String.valueOf(((Map<String, Object>) dataMap).getOrDefault(fieldName, ""));
        }
        return String.valueOf(resource.getOrDefault(fieldName, ""));
    }
}
