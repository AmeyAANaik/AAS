package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
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
        service = new OrderBillingService(erpNextClient, new OrderFlowStateMachine(), new OrderPricingService(), "GST", 7.0);
    }

    @Test
    void calculatesSellPreviewFromVendorBillAndMargin() {
        when(erpNextClient.getResource(eq("Sales Order"), eq("SO-1"))).thenReturn(Map.of(
                "aas_vendor_bill_total", 200.0,
                "items", List.of(
                        Map.of("item_code", "ITEM-1", "qty", 2, "aas_vendor_rate", 50.0, "aas_margin_percent", 10.0),
                        Map.of("item_code", "ITEM-2", "qty", 1, "aas_vendor_rate", 100.0, "aas_margin_percent", 20.0))));

        Map<String, Object> preview = service.getSellPreview("SO-1");

        assertEquals(230.0, preview.get("sellAmount"));
        assertEquals(30.0, preview.get("marginAmount"));
        assertEquals(15.0, preview.get("marginPercent"));
    }

    @Test
    void capturesVendorBillAndCreatesPurchaseInvoice() {
        when(erpNextClient.getResource(eq("Sales Order"), eq("SO-1"))).thenReturn(Map.of(
                "aas_status", "VENDOR_PDF_RECEIVED",
                "aas_vendor", "SUP-1",
                "company", "AAS",
                "items", List.of(Map.of("item_code", "ITEM-1", "qty", 5, "rate", 50.0, "amount", 250.0, "aas_margin_percent", 12.0))));
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

        Map<String, Object> response = service.captureVendorBill("SO-1", fields);

        assertEquals(250.0, response.get("vendorBillTotal"));
        assertEquals(12.0, response.get("marginPercent"));
        verify(erpNextClient).createResource(eq("Purchase Invoice"), anyMap());
    }

    @Test
    void generatedInvoicesAreMarkedCurrentAndUseCleanDescriptions() {
        when(erpNextClient.getResource(eq("Sales Order"), eq("SO-1"))).thenReturn(Map.of(
                "aas_status", "VENDOR_BILL_CAPTURED",
                "aas_vendor_bill_total", 105.0,
                "aas_vendor", "SUP-1",
                "aas_margin_percent", 7.0,
                "customer", "BRANCH-1",
                "company", "AAS",
                "transaction_date", "2026-04-05",
                "items", List.of(Map.of(
                        "item_code", "ITEM-1",
                        "item_name", "SFK ATTA",
                        "description", "ATTA\n[AAS_MANUAL_ENTRY]",
                        "qty", 1,
                        "rate", 100.0,
                        "amount", 100.0,
                        "aas_vendor_rate", 100.0,
                        "aas_gst_percent", 5.0,
                        "aas_margin_percent", 7.0))));
        when(erpNextClient.getResource(eq("Supplier"), eq("SUP-1")))
                .thenReturn(Map.of("aas_category", "CAT-1"));
        when(erpNextClient.getResource(eq("Item"), eq("AAS-VENDOR-BILL")))
                .thenReturn(Map.of("name", "AAS-VENDOR-BILL"));
        when(erpNextClient.getResource(eq("Company"), eq("AAS")))
                .thenReturn(Map.of("default_currency", "INR"));
        when(erpNextClient.listResources(eq("Warehouse"), anyMap()))
                .thenReturn(List.of(Map.of("name", "Stores - AAS", "company", "AAS", "is_group", 0, "disabled", 0)));
        when(erpNextClient.createResource(eq("Purchase Invoice"), anyMap()))
                .thenReturn(Map.of("name", "PINV-NEW"));
        when(erpNextClient.createResource(eq("Sales Invoice"), anyMap()))
                .thenReturn(Map.of("name", "SINV-NEW"));
        when(erpNextClient.updateResource(eq("Sales Order"), eq("SO-1"), anyMap()))
                .thenReturn(Map.of("name", "SO-1"));

        service.recordGeneratedVendorBill("SO-1", Map.of(
                "vendor_bill_total", 105.0,
                "vendor_bill_ref", "SO-1",
                "vendor_bill_date", "2026-04-05"));
        service.createSellOrder("SO-1");

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> purchaseInvoiceCaptor = ArgumentCaptor.forClass(Map.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> salesInvoiceCaptor = ArgumentCaptor.forClass(Map.class);
        verify(erpNextClient).createResource(eq("Purchase Invoice"), purchaseInvoiceCaptor.capture());
        verify(erpNextClient).createResource(eq("Sales Invoice"), salesInvoiceCaptor.capture());

        assertEquals("CURRENT", purchaseInvoiceCaptor.getValue().get("aas_invoice_version_status"));
        assertEquals("CURRENT", salesInvoiceCaptor.getValue().get("aas_invoice_version_status"));
        assertEquals("CAT-1", salesInvoiceCaptor.getValue().get("aas_category"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> invoiceItems = (List<Map<String, Object>>) salesInvoiceCaptor.getValue().get("items");
        assertEquals("ATTA", invoiceItems.get(0).get("description"));
        assertTrue(invoiceItems.get(0).containsKey("warehouse"));
    }

    @Test
    void capturesVendorBillWhenItemsTotalMatchesIncludingGst() {
        when(erpNextClient.getResource(eq("Sales Order"), eq("SO-1"))).thenReturn(Map.of(
                "aas_status", "VENDOR_PDF_RECEIVED",
                "aas_vendor", "SUP-1",
                "company", "AAS",
                "items", List.of(Map.of(
                        "item_code", "ITEM-1",
                        "qty", 5,
                        "rate", 50.0,
                        "amount", 250.0,
                        "aas_gst_percent", 5.0,
                        "aas_margin_percent", 12.0))));
        when(erpNextClient.getResource(eq("Item"), eq("AAS-VENDOR-BILL")))
                .thenReturn(Map.of("name", "AAS-VENDOR-BILL"));
        when(erpNextClient.createResource(eq("Purchase Invoice"), anyMap()))
                .thenReturn(Map.of("name", "PINV-1"));
        when(erpNextClient.updateResource(eq("Sales Order"), eq("SO-1"), anyMap()))
                .thenReturn(Map.of("name", "SO-1"));

        Map<String, Object> fields = new HashMap<>();
        fields.put("vendor_bill_total", 262.5);
        fields.put("vendor_bill_ref", "VB-GST-1");
        fields.put("vendor_bill_date", "2026-03-19");

        Map<String, Object> response = service.captureVendorBill("SO-1", fields);

        assertEquals(262.5, response.get("vendorBillTotal"));
        assertEquals(0.0, response.get("roundingAdjustment"));
        verify(erpNextClient).createResource(eq("Purchase Invoice"), anyMap());
    }

    @Test
    void capturesVendorBillWhenLineAmountsAlreadyIncludeGst() {
        when(erpNextClient.getResource(eq("Sales Order"), eq("SO-1"))).thenReturn(Map.of(
                "aas_status", "VENDOR_PDF_RECEIVED",
                "aas_vendor", "SUP-1",
                "company", "AAS",
                "items", List.of(Map.of(
                        "item_code", "ITEM-1",
                        "qty", 60,
                        "rate", 60.17,
                        "amount", 3610.2,
                        "aas_gst_percent", 18.0,
                        "aas_rate_before_tax", 50.99,
                        "aas_rate_after_tax", 60.17,
                        "aas_margin_percent", 7.0))));
        when(erpNextClient.getResource(eq("Item"), eq("AAS-VENDOR-BILL")))
                .thenReturn(Map.of("name", "AAS-VENDOR-BILL"));
        when(erpNextClient.createResource(eq("Purchase Invoice"), anyMap()))
                .thenReturn(Map.of("name", "PINV-1"));
        when(erpNextClient.updateResource(eq("Sales Order"), eq("SO-1"), anyMap()))
                .thenReturn(Map.of("name", "SO-1"));

        Map<String, Object> fields = new HashMap<>();
        fields.put("vendor_bill_total", 3610.2);
        fields.put("vendor_bill_ref", "VB-INCL-1");
        fields.put("vendor_bill_date", "2026-03-31");

        Map<String, Object> response = service.captureVendorBill("SO-1", fields);

        assertEquals(3610.2, response.get("vendorBillTotal"));
        assertEquals(0.0, response.get("roundingAdjustment"));
        verify(erpNextClient).createResource(eq("Purchase Invoice"), anyMap());
    }

    @Test
    void capturesVendorBillWithRoundingAdjustmentWhenDiffIsWithinTolerance() {
        when(erpNextClient.getResource(eq("Sales Order"), eq("SO-1"))).thenReturn(Map.of(
                "aas_status", "VENDOR_PDF_RECEIVED",
                "aas_vendor", "SUP-1",
                "company", "AAS",
                "items", List.of(Map.of(
                        "item_code", "ITEM-1",
                        "qty", 5,
                        "rate", 50.0,
                        "amount", 250.0,
                        "aas_gst_percent", 5.0,
                        "aas_margin_percent", 12.0))));
        when(erpNextClient.getResource(eq("Item"), eq("AAS-VENDOR-BILL")))
                .thenReturn(Map.of("name", "AAS-VENDOR-BILL"));
        when(erpNextClient.createResource(eq("Purchase Invoice"), anyMap()))
                .thenReturn(Map.of("name", "PINV-1"));
        when(erpNextClient.updateResource(eq("Sales Order"), eq("SO-1"), anyMap()))
                .thenReturn(Map.of("name", "SO-1"));

        Map<String, Object> fields = new HashMap<>();
        fields.put("vendor_bill_total", 262.1);

        Map<String, Object> response = service.captureVendorBill("SO-1", fields);

        assertEquals(-0.4, response.get("roundingAdjustment"));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> updateCaptor = ArgumentCaptor.forClass(Map.class);
        verify(erpNextClient).updateResource(eq("Sales Order"), eq("SO-1"), updateCaptor.capture());
        assertEquals(-0.4, updateCaptor.getValue().get("aas_rounding_adjustment"));
    }

    @Test
    void capturesVendorBillWithRoundingAdjustmentWhenDiffIsBelowOne() {
        when(erpNextClient.getResource(eq("Sales Order"), eq("SO-1"))).thenReturn(Map.of(
                "aas_status", "VENDOR_PDF_RECEIVED",
                "aas_vendor", "SUP-1",
                "company", "AAS",
                "items", List.of(Map.of(
                        "item_code", "ITEM-1",
                        "qty", 5,
                        "rate", 50.0,
                        "amount", 250.0,
                        "aas_gst_percent", 5.0,
                        "aas_margin_percent", 12.0))));
        when(erpNextClient.getResource(eq("Item"), eq("AAS-VENDOR-BILL")))
                .thenReturn(Map.of("name", "AAS-VENDOR-BILL"));
        when(erpNextClient.createResource(eq("Purchase Invoice"), anyMap()))
                .thenReturn(Map.of("name", "PINV-1"));
        when(erpNextClient.updateResource(eq("Sales Order"), eq("SO-1"), anyMap()))
                .thenReturn(Map.of("name", "SO-1"));

        Map<String, Object> fields = new HashMap<>();
        fields.put("vendor_bill_total", 263.2);

        Map<String, Object> response = service.captureVendorBill("SO-1", fields);

        assertEquals(0.7, response.get("roundingAdjustment"));
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> updateCaptor = ArgumentCaptor.forClass(Map.class);
        verify(erpNextClient).updateResource(eq("Sales Order"), eq("SO-1"), updateCaptor.capture());
        assertEquals(0.7, updateCaptor.getValue().get("aas_rounding_adjustment"));
    }

    @Test
    void capturesVendorBillWithTransportCharge() {
        when(erpNextClient.getResource(eq("Sales Order"), eq("SO-1"))).thenReturn(Map.of(
                "aas_status", "VENDOR_PDF_RECEIVED",
                "aas_vendor", "SUP-1",
                "company", "AAS",
                "items", List.of(Map.of("item_code", "ITEM-1", "qty", 5, "rate", 50.0, "amount", 250.0, "aas_margin_percent", 12.0))));
        when(erpNextClient.getResource(eq("Item"), eq("AAS-VENDOR-BILL")))
                .thenReturn(Map.of("name", "AAS-VENDOR-BILL"));
        when(erpNextClient.createResource(eq("Purchase Invoice"), anyMap()))
                .thenReturn(Map.of("name", "PINV-1"));
        when(erpNextClient.updateResource(eq("Sales Order"), eq("SO-1"), anyMap()))
                .thenReturn(Map.of("name", "SO-1"));

        Map<String, Object> fields = new HashMap<>();
        fields.put("vendor_bill_total", 1750);
        fields.put("transport_charge", 1500);
        fields.put("vendor_bill_ref", "VB-TRN");
        fields.put("vendor_bill_date", "2026-03-18");

        Map<String, Object> response = service.captureVendorBill("SO-1", fields);

        assertEquals(1750.0, response.get("vendorBillTotal"));
        assertEquals(1500.0, response.get("transportCharge"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> updateCaptor = ArgumentCaptor.forClass(Map.class);
        verify(erpNextClient).updateResource(eq("Sales Order"), eq("SO-1"), updateCaptor.capture());
        assertEquals(1500.0, updateCaptor.getValue().get("aas_transport_charge"));
    }

    @Test
    void rejectsVendorBillWhenTransportAdjustedTotalDoesNotMatch() {
        when(erpNextClient.getResource(eq("Sales Order"), eq("SO-1"))).thenReturn(Map.of(
                "aas_status", "VENDOR_PDF_RECEIVED",
                "aas_vendor", "SUP-1",
                "company", "AAS",
                "items", List.of(Map.of("item_code", "ITEM-1", "qty", 5, "rate", 50.0, "amount", 250.0, "aas_margin_percent", 12.0))));

        Map<String, Object> fields = new HashMap<>();
        fields.put("vendor_bill_total", 1600);
        fields.put("transport_charge", 1500);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.captureVendorBill("SO-1", fields));

        assertEquals(
                "Vendor bill total must match scanned items total including GST plus transport charge. Items total=250.0, Transport=1500.0, Bill total=1600.0, Diff=-150.0.",
                ex.getMessage());
    }

    @Test
    void allowsVendorBillMismatchWhenExplicitlyApprovedWithoutTransport() {
        when(erpNextClient.getResource(eq("Sales Order"), eq("SO-1"))).thenReturn(Map.of(
                "aas_status", "VENDOR_PDF_RECEIVED",
                "aas_vendor", "SUP-1",
                "company", "AAS",
                "items", List.of(Map.of("item_code", "ITEM-1", "qty", 5, "rate", 50.0, "amount", 250.0, "aas_margin_percent", 12.0))));
        when(erpNextClient.getResource(eq("Item"), eq("AAS-VENDOR-BILL")))
                .thenReturn(Map.of("name", "AAS-VENDOR-BILL"));
        when(erpNextClient.createResource(eq("Purchase Invoice"), anyMap()))
                .thenReturn(Map.of("name", "PINV-1"));
        when(erpNextClient.updateResource(eq("Sales Order"), eq("SO-1"), anyMap()))
                .thenReturn(Map.of("name", "SO-1"));

        Map<String, Object> fields = new HashMap<>();
        fields.put("vendor_bill_total", 300);
        fields.put("transport_charge", 0);
        fields.put("allow_mismatch", true);

        Map<String, Object> response = service.captureVendorBill("SO-1", fields);

        assertEquals(300.0, response.get("vendorBillTotal"));
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
                "aas_rounding_adjustment", -0.4,
                "items", List.of(
                        Map.of("item_code", "ITEM-1", "qty", 1, "aas_vendor_rate", 50.0, "aas_margin_percent", 10.0, "aas_gst_percent", 5.0),
                        Map.of("item_code", "ITEM-2", "qty", 1, "aas_vendor_rate", 50.0, "aas_margin_percent", 20.0))));
        when(erpNextClient.listResources(eq("Account"), anyMap())).thenReturn(List.of(
                Map.of(
                        "name", "GST - A",
                        "account_name", "GST",
                        "company", "AAS",
                        "account_type", "Tax")));
        when(erpNextClient.listResources(eq("Item Tax Template"), anyMap())).thenReturn(List.of(
                Map.of(
                        "name", "AAS GST 5% - A",
                        "title", "AAS GST 5%",
                        "company", "AAS")));
        when(erpNextClient.createResource(eq("Sales Order"), anyMap())).thenReturn(Map.of("name", "SO-SELL"));
        when(erpNextClient.createResource(eq("Sales Invoice"), anyMap())).thenReturn(Map.of("name", "SI-SELL"));
        when(erpNextClient.updateResource(eq("Sales Order"), eq("SO-1"), anyMap())).thenReturn(Map.of("name", "SO-1"));

        Map<String, Object> response = service.createSellOrder("SO-1");

        assertEquals(115.0, response.get("sellTotal"));
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(erpNextClient).createResource(eq("Sales Invoice"), captor.capture());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) captor.getValue().get("items");
        assertEquals(55.0, items.get(0).get("rate"));
        assertEquals(60.0, items.get(1).get("rate"));
        assertEquals(5.0, items.get(0).get("aas_gst_percent"));
        assertEquals("AAS GST 5% - A", items.get(0).get("item_tax_template"));
        assertEquals(-0.4, captor.getValue().get("aas_rounding_adjustment"));
        assertEquals(0, captor.getValue().get("disable_rounded_total"));
        assertFalse(captor.getValue().containsKey("taxes_and_charges"));
    }

    @Test
    void appliesTransportToCustomerInvoiceOnlyWhenRequested() {
        when(erpNextClient.getResource(eq("Sales Order"), eq("SO-1"))).thenReturn(Map.of(
                "aas_status", "VENDOR_BILL_CAPTURED",
                "customer", "SHOP-1",
                "company", "AAS",
                "aas_category", "Grocery",
                "transaction_date", "2026-02-19",
                "delivery_date", "2026-02-20",
                "aas_vendor_bill_total", 100.0,
                "aas_transport_charge", 15.0,
                "items", List.of(
                        Map.of("item_code", "ITEM-1", "qty", 1, "aas_vendor_rate", 50.0, "aas_margin_percent", 10.0, "aas_gst_percent", 5.0),
                        Map.of("item_code", "ITEM-2", "qty", 1, "aas_vendor_rate", 50.0, "aas_margin_percent", 20.0))));
        when(erpNextClient.listResources(eq("Account"), anyMap())).thenReturn(List.of(
                Map.of(
                        "name", "GST - A",
                        "account_name", "GST",
                        "company", "AAS",
                        "account_type", "Tax")));
        when(erpNextClient.listResources(eq("Item Tax Template"), anyMap())).thenReturn(List.of(
                Map.of(
                        "name", "AAS GST 5% - A",
                        "title", "AAS GST 5%",
                        "company", "AAS")));
        when(erpNextClient.getResource(eq("Item"), eq("AAS-TRANSPORT-CHARGE")))
                .thenReturn(Map.of("name", "AAS-TRANSPORT-CHARGE"));
        when(erpNextClient.createResource(eq("Sales Invoice"), anyMap())).thenReturn(Map.of("name", "SI-SELL"));
        when(erpNextClient.updateResource(eq("Sales Order"), eq("SO-1"), anyMap())).thenReturn(Map.of("name", "SO-1"));

        Map<String, Object> response = service.createSellOrder("SO-1", Map.of("apply_transport_to_invoice", true));

        assertEquals(130.0, response.get("sellTotal"));
        assertEquals(true, response.get("transportAppliedToInvoice"));

        ArgumentCaptor<Map<String, Object>> createCaptor = ArgumentCaptor.forClass(Map.class);
        verify(erpNextClient).createResource(eq("Sales Invoice"), createCaptor.capture());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> invoiceItems = (List<Map<String, Object>>) createCaptor.getValue().get("items");
        assertEquals(3, invoiceItems.size());
        assertEquals("AAS-TRANSPORT-CHARGE", invoiceItems.get(2).get("item_code"));
        assertEquals(15.0, invoiceItems.get(2).get("rate"));
        assertEquals("Grocery", invoiceItems.get(2).get("item_group"));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> updateCaptor = ArgumentCaptor.forClass(Map.class);
        verify(erpNextClient).updateResource(eq("Sales Order"), eq("SO-1"), updateCaptor.capture());
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> persistedItems = (List<Map<String, Object>>) updateCaptor.getValue().get("items");
        assertEquals(2, persistedItems.size());
        assertEquals(130.0, updateCaptor.getValue().get("aas_sell_order_total"));
    }

    @Test
    void capsSellRateToMrpWhenMarginWouldExceedIt() {
        when(erpNextClient.getResource(eq("Sales Order"), eq("SO-1"))).thenReturn(Map.of(
                "aas_vendor_bill_total", 100.0,
                "items", List.of(Map.of(
                        "item_code", "ITEM-1",
                        "item_name", "Item 1",
                        "qty", 1,
                        "aas_vendor_rate", 100.0,
                        "aas_margin_percent", 20.0,
                        "aas_mrp", 110.0))));

        Map<String, Object> preview = service.getSellPreview("SO-1");

        assertEquals(110.0, preview.get("sellAmount"));
        assertEquals(10.0, preview.get("marginPercent"));
    }

    @Test
    void preservesExplicitZeroMargin() {
        when(erpNextClient.getResource(eq("Sales Order"), eq("SO-1"))).thenReturn(Map.of(
                "aas_vendor_bill_total", 100.0,
                "aas_margin_percent", 0.0));

        Map<String, Object> preview = service.getSellPreview("SO-1");

        assertEquals(100.0, preview.get("sellAmount"));
        assertEquals(0.0, preview.get("marginAmount"));
        assertEquals(0.0, preview.get("marginPercent"));
    }

    @Test
    void rejectsCreateSellOrderBeforeVendorBillCapture() {
        when(erpNextClient.getResource(eq("Sales Order"), eq("SO-1"))).thenReturn(Map.of(
                "aas_status", "VENDOR_ASSIGNED"));
        assertThrows(IllegalStateException.class, () -> service.createSellOrder("SO-1"));
    }
}
