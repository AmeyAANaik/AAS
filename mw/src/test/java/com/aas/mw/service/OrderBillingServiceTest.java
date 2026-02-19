package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class OrderBillingServiceTest {

    private ErpNextClient erpNextClient;
    private OrderBillingService service;

    @BeforeEach
    void setup() {
        erpNextClient = mock(ErpNextClient.class);
        service = new OrderBillingService(erpNextClient, new OrderFlowStateMachine(), 10.0);
    }

    @Test
    void calculatesSellPreviewFromVendorBillAndMargin() {
        when(erpNextClient.getResource(eq("Sales Order"), eq("SO-1"))).thenReturn(Map.of(
                "aas_vendor_bill_total", 200.0,
                "aas_margin_percent", 10.0));

        Map<String, Object> preview = service.getSellPreview("SO-1");

        assertEquals(220.0, preview.get("sellAmount"));
        assertEquals(20.0, preview.get("marginAmount"));
    }

    @Test
    void capturesVendorBillAndCreatesPurchaseInvoice() {
        when(erpNextClient.getResource(eq("Sales Order"), eq("SO-1"))).thenReturn(Map.of(
                "aas_status", "VENDOR_PDF_RECEIVED",
                "aas_vendor", "SUP-1",
                "company", "AAS",
                "aas_margin_percent", 10.0));
        when(erpNextClient.getResource(eq("Item"), eq("AAS-VENDOR-BILL")))
                .thenReturn(Map.of("name", "AAS-VENDOR-BILL"));
        when(erpNextClient.createResource(eq("Purchase Invoice"), anyMap()))
                .thenReturn(Map.of("name", "PINV-1"));
        when(erpNextClient.updateResource(eq("Sales Order"), eq("SO-1"), anyMap()))
                .thenReturn(Map.of("name", "SO-1"));

        Map<String, Object> fields = new HashMap<>();
        fields.put("vendor_bill_total", 250);
        fields.put("vendor_bill_ref", "VB-1");
        fields.put("vendor_bill_date", "2026-02-19");
        fields.put("margin_percent", 10);

        Map<String, Object> response = service.captureVendorBill("SO-1", fields);

        assertEquals(250.0, response.get("vendorBillTotal"));
        verify(erpNextClient).createResource(eq("Purchase Invoice"), anyMap());
    }

    @Test
    void createsSellOrderFromCapturedBill() {
        when(erpNextClient.getResource(eq("Sales Order"), eq("SO-1"))).thenReturn(Map.of(
                "aas_status", "VENDOR_BILL_CAPTURED",
                "customer", "SHOP-1",
                "company", "AAS",
                "transaction_date", "2026-02-19",
                "delivery_date", "2026-02-20",
                "aas_vendor_bill_total", 100.0,
                "aas_margin_percent", 10.0,
                "items", List.of(Map.of("item_code", "ITEM-1", "qty", 2, "aas_vendor_rate", 50))));
        when(erpNextClient.createResource(eq("Sales Order"), anyMap())).thenReturn(Map.of("name", "SO-SELL"));
        when(erpNextClient.createResource(eq("Sales Invoice"), anyMap())).thenReturn(Map.of("name", "SI-SELL"));
        when(erpNextClient.updateResource(eq("Sales Order"), eq("SO-1"), anyMap())).thenReturn(Map.of("name", "SO-1"));

        Map<String, Object> response = service.createSellOrder("SO-1");

        assertEquals(110.0, response.get("sellTotal"));
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(erpNextClient).createResource(eq("Sales Order"), captor.capture());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) captor.getValue().get("items");
        assertEquals(55.0, items.get(0).get("rate"));
    }

    @Test
    void rejectsCreateSellOrderBeforeVendorBillCapture() {
        when(erpNextClient.getResource(eq("Sales Order"), eq("SO-1"))).thenReturn(Map.of(
                "aas_status", "VENDOR_ASSIGNED"));
        assertThrows(IllegalStateException.class, () -> service.createSellOrder("SO-1"));
    }
}
