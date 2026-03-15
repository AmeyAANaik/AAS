package com.aas.mw.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.meta.VendorFieldRegistry;
import com.aas.mw.meta.VendorFieldsProperties;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SetupServiceTest {

    private ErpNextClient erpNextClient;
    private SetupService service;

    @BeforeEach
    void setup() {
        erpNextClient = mock(ErpNextClient.class);
        CustomFieldProvisioner customFieldProvisioner = mock(CustomFieldProvisioner.class);
        VendorFieldRegistry vendorFieldRegistry = new VendorFieldRegistry(new VendorFieldsProperties());
        CatalogRoutingService catalogRoutingService = new CatalogRoutingService(erpNextClient);
        when(customFieldProvisioner.ensure(
                eq("Supplier"), org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyBoolean(),
                org.mockito.ArgumentMatchers.anyBoolean())).thenReturn(false);
        when(erpNextClient.listResources(eq("Custom Field"), anyMap())).thenReturn(Collections.emptyList());
        when(erpNextClient.listResources(eq("Supplier Group"), anyMap())).thenReturn(Collections.emptyList());
        when(erpNextClient.listResources(eq("Item Group"), anyMap())).thenReturn(List.of(Map.of("name", "All Item Groups", "is_group", 0)));
        when(erpNextClient.listResources(eq("UOM"), anyMap())).thenReturn(List.of(Map.of("name", "Nos")));
        when(erpNextClient.createResource(eq("Supplier Group"), anyMap())).thenReturn(Map.of("name", "All Supplier Groups"));
        when(erpNextClient.createResource(eq("Item"), anyMap())).thenReturn(Map.of("name", "AAS-BRANCH-IMAGE"));
        when(erpNextClient.getResource(eq("Supplier"), eq("Vendor A"))).thenThrow(new RuntimeException("missing"));
        when(erpNextClient.getResource(eq("Customer"), eq("Shop A"))).thenThrow(new RuntimeException("missing"));
        when(erpNextClient.createResource(eq("Supplier"), anyMap())).thenReturn(Map.of("name", "Vendor A"));
        when(erpNextClient.createResource(eq("Customer"), anyMap())).thenReturn(Map.of("name", "Shop A"));

        when(erpNextClient.listResources(eq("Sales Order"), anyMap()))
                .thenReturn(List.of(
                        Map.of("name", "SO-1", "aas_margin_percent", 10.0),
                        Map.of("name", "SO-2", "aas_margin_percent", 0.0),
                        Map.of("name", "SO-3", "aas_margin_percent", 5.0)))
                .thenReturn(Collections.emptyList());
        when(erpNextClient.getResource(eq("Sales Order"), eq("SO-1")))
                .thenReturn(Map.of("data", Map.of(
                        "name", "SO-1",
                        "aas_margin_percent", 10.0,
                        "items", List.of(
                                Map.of("name", "SOI-1", "item_code", "ITEM-1", "aas_margin_percent", 10.0),
                                Map.of("name", "SOI-2", "item_code", "ITEM-2", "aas_margin_percent", 4.0)))));
        when(erpNextClient.getResource(eq("Sales Order"), eq("SO-2")))
                .thenReturn(Map.of("data", Map.of(
                        "name", "SO-2",
                        "aas_margin_percent", 0.0,
                        "items", List.of(
                                Map.of("name", "SOI-3", "item_code", "ITEM-3", "aas_margin_percent", 0.0)))));
        when(erpNextClient.getResource(eq("Sales Order"), eq("SO-3")))
                .thenReturn(Map.of("data", Map.of(
                        "name", "SO-3",
                        "aas_margin_percent", 5.0,
                        "items", List.of(
                                Map.of("name", "SOI-4", "item_code", "ITEM-4", "aas_margin_percent", 4.0)))));
        when(erpNextClient.listResources(eq("Item"), anyMap()))
                .thenReturn(List.of(
                        Map.of("name", "ITEM-1", "aas_margin_percent", 10.0),
                        Map.of("name", "ITEM-2", "aas_margin_percent", 0.0),
                        Map.of("name", "ITEM-3", "aas_margin_percent", 3.0)))
                .thenReturn(Collections.emptyList());

        service = new SetupService(
                erpNextClient,
                customFieldProvisioner,
                vendorFieldRegistry,
                catalogRoutingService,
                false,
                "Supplier",
                "Customer",
                "Stock User",
                "vendor@example.com",
                "Vendor User",
                "vendor123",
                "Vendor A",
                "shop@example.com",
                "Shop User",
                "shop123",
                "Shop A",
                "helper@example.com",
                "Helper User",
                "helper123",
                7.0);
    }

    @Test
    void backfillsLegacyMarginValuesToConfiguredDefault() {
        Map<String, Object> result = service.ensureSetup();

        assertEquals(2, result.get("salesOrdersMarginBackfilled"));
        assertEquals(2, result.get("salesOrderItemsMarginBackfilled"));
        assertEquals(2, result.get("itemsMarginBackfilled"));
    }
}
