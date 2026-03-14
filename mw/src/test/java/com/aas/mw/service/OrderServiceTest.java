package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.dto.OrderItemLine;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
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
        orderService = new OrderService(erpNextClient, fileService, new OrderFlowStateMachine(), 7.0);
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

    @Test
    void appliesDefaultMarginWhenCreatingOrderWithoutMargin() {
        when(erpNextClient.getResource(eq("Company"), eq("AAS"))).thenReturn(Map.of("abbr", "A"));
        when(erpNextClient.listResources(eq("Warehouse"), anyMap()))
                .thenReturn(java.util.List.of(Map.of("name", "Finished Goods - A", "company", "AAS", "is_group", 0, "disabled", 0)));
        when(erpNextClient.getResource(eq("Item"), eq("AAS-BRANCH-IMAGE"))).thenReturn(Map.of("aas_margin_percent", 11.0));
        when(erpNextClient.createResource(eq("Sales Order"), anyMap())).thenReturn(Map.of("name", "SO-1"));

        com.aas.mw.dto.OrderRequest request = new com.aas.mw.dto.OrderRequest();
        Map<String, Object> fields = new HashMap<>();
        fields.put("customer", "Shop A");
        fields.put("company", "AAS");
        fields.put("items", java.util.List.of(new HashMap<>(Map.of("item_code", "AAS-BRANCH-IMAGE", "qty", 1, "rate", 0, "amount", 0))));
        request.setFields(fields);

        orderService.createOrder(request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(erpNextClient).createResource(eq("Sales Order"), payloadCaptor.capture());
        assertEquals(7.0, payloadCaptor.getValue().get("aas_margin_percent"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) payloadCaptor.getValue().get("items");
        assertEquals(11.0, items.get(0).get("aas_margin_percent"));
    }

    @Test
    void updateOrderItemsPersistsItemWiseMargin() {
        when(erpNextClient.getResource(eq("Sales Order"), eq("SO-1"))).thenReturn(Map.of(
                "aas_status", "VENDOR_PDF_RECEIVED",
                "items", List.of(Map.of("name", "SO-ITEM-1", "item_code", "ITEM-1", "aas_vendor_rate", 40.0)),
                "aas_po", "PO-1"));
        when(erpNextClient.getResource(eq("Purchase Order"), eq("PO-1"))).thenReturn(Map.of(
                "items", List.of(Map.of("name", "PO-ITEM-1", "item_code", "ITEM-1", "aas_vendor_rate", 40.0))));
        when(erpNextClient.updateResource(eq("Sales Order"), eq("SO-1"), anyMap())).thenReturn(Map.of(
                "items", List.of(Map.of(
                        "item_code", "ITEM-1",
                        "item_name", "Item 1",
                        "qty", 2.0,
                        "rate", 50.0,
                        "amount", 100.0,
                        "aas_margin_percent", 15.0))));
        when(erpNextClient.updateResource(eq("Purchase Order"), eq("PO-1"), anyMap())).thenReturn(Map.of("name", "PO-1"));

        OrderItemLine line = new OrderItemLine();
        line.setItem_code("ITEM-1");
        line.setQty(2);
        line.setRate(50);
        line.setAas_margin_percent(15);

        Map<String, Object> response = orderService.updateOrderItems("SO-1", List.of(line));

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) response.get("items");
        assertEquals(15.0, items.get(0).get("aas_margin_percent"));
    }
}
