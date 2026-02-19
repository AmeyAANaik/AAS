package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.dto.FieldsRequest;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VendorAssignmentServiceTest {

    private ErpNextClient erpNextClient;
    private VendorAssignmentService service;

    @BeforeEach
    void setup() {
        erpNextClient = mock(ErpNextClient.class);
        service = new VendorAssignmentService(erpNextClient, new OrderFlowStateMachine());
    }

    @Test
    void assignsVendorOnlyFromDraft() {
        when(erpNextClient.getResource(eq("Sales Order"), eq("SO-1")))
                .thenReturn(Map.of("aas_status", "DRAFT"));
        when(erpNextClient.updateResource(eq("Sales Order"), eq("SO-1"), anyMap()))
                .thenReturn(Map.of("name", "SO-1"));

        FieldsRequest request = new FieldsRequest();
        Map<String, Object> fields = new HashMap<>();
        fields.put("aas_vendor", "SUP-1");
        request.setFields(fields);

        service.assignVendor("SO-1", request);

        verify(erpNextClient).updateResource(eq("Sales Order"), eq("SO-1"), anyMap());
    }

    @Test
    void rejectsAssignVendorWhenNotDraft() {
        when(erpNextClient.getResource(eq("Sales Order"), eq("SO-1")))
                .thenReturn(Map.of("aas_status", "VENDOR_ASSIGNED"));

        FieldsRequest request = new FieldsRequest();
        request.setFields(new HashMap<>(Map.of("aas_vendor", "SUP-1")));

        assertThrows(IllegalStateException.class, () -> service.assignVendor("SO-1", request));
    }
}
