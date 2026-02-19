package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderServiceTest {

    private ErpNextClient erpNextClient;
    private OrderService orderService;

    @BeforeEach
    void setup() {
        erpNextClient = mock(ErpNextClient.class);
        ErpNextFileService fileService = mock(ErpNextFileService.class);
        orderService = new OrderService(erpNextClient, fileService, new OrderFlowStateMachine());
    }

    @Test
    void acceptsSequentialStatusTransition() {
        when(erpNextClient.getResource(eq("Sales Order"), eq("SO-1")))
                .thenReturn(Map.of("aas_status", "DRAFT"));
        when(erpNextClient.updateResource(eq("Sales Order"), eq("SO-1"), eq(Map.of("aas_status", "VENDOR_ASSIGNED"))))
                .thenReturn(Map.of("name", "SO-1"));

        orderService.updateOrderFields("SO-1", new HashMap<>(Map.of("aas_status", "VENDOR_ASSIGNED")));

        verify(erpNextClient).updateResource(eq("Sales Order"), eq("SO-1"), eq(Map.of("aas_status", "VENDOR_ASSIGNED")));
    }

    @Test
    void rejectsInvalidStatusTransition() {
        when(erpNextClient.getResource(eq("Sales Order"), eq("SO-1")))
                .thenReturn(Map.of("aas_status", "DRAFT"));

        assertThrows(IllegalStateException.class,
                () -> orderService.updateOrderFields("SO-1", new HashMap<>(Map.of("aas_status", "INVOICED"))));
    }
}
