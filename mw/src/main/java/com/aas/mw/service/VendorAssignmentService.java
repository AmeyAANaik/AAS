package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.dto.FieldsRequest;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

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
        ensureVendorIsActive(String.valueOf(fields.get("aas_vendor")).trim());
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

    @SuppressWarnings("unchecked")
    private void ensureVendorIsActive(String vendorId) {
        Map<String, Object> response = erpNextClient.getResource("Supplier", vendorId);
        Object data = response == null ? null : response.get("data");
        Map<String, Object> vendor = data instanceof Map<?, ?> map ? (Map<String, Object>) map : response;
        if (vendor == null || vendor.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Vendor not found.");
        }
        if (isDisabled(vendor.get("disabled"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Inactive vendors cannot be assigned to orders.");
        }
    }

    private boolean isDisabled(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean b) {
            return b;
        }
        if (value instanceof Number n) {
            return n.intValue() != 0;
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return false;
        }
        return "1".equals(text) || "true".equalsIgnoreCase(text) || "yes".equalsIgnoreCase(text);
    }
}
