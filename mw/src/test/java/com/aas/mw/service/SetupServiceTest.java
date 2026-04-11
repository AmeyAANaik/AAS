package com.aas.mw.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.atLeastOnce;

import com.aas.mw.client.ErpNextClient;
import feign.Request;
import feign.Response;
import com.aas.mw.meta.VendorFieldRegistry;
import com.aas.mw.meta.VendorFieldsProperties;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class SetupServiceTest {

    private ErpNextClient erpNextClient;
    private CustomFieldProvisioner customFieldProvisioner;
    private VendorFieldRegistry vendorFieldRegistry;
    private CatalogRoutingService catalogRoutingService;
    private SetupService service;

    @BeforeEach
    void setup() {
        erpNextClient = mock(ErpNextClient.class);
        customFieldProvisioner = mock(CustomFieldProvisioner.class);
        vendorFieldRegistry = new VendorFieldRegistry(new VendorFieldsProperties());
        catalogRoutingService = new CatalogRoutingService(erpNextClient);
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
        when(erpNextClient.getResource(eq("Supplier"), eq("FreshHarvest Agro Foods"))).thenThrow(notFound("Supplier", "FreshHarvest Agro Foods"));
        when(erpNextClient.getResource(eq("Customer"), eq("Sukarta Aundh"))).thenThrow(notFound("Customer", "Sukarta Aundh"));
        when(erpNextClient.createResource(eq("Supplier"), anyMap())).thenReturn(Map.of("name", "FreshHarvest Agro Foods"));
        when(erpNextClient.createResource(eq("Customer"), anyMap())).thenReturn(Map.of("name", "Sukarta Aundh"));

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
                "Accounts User,Sales User",
                "vendor@example.com",
                "Vendor User",
                "vendor123",
                "FreshHarvest Agro Foods",
                "shop@example.com",
                "Shop User",
                "shop123",
                "Sukarta Aundh",
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
        verify(customFieldProvisioner, atLeastOnce()).ensure(
                eq("Item"),
                eq("aas_review_status"),
                eq("Review Status"),
                eq("Select"),
                eq("PENDING_REVIEW\nAPPROVED\nMERGED\nREJECTED"),
                eq("aas_gst_percent"),
                eq(true),
                eq(false));
        verify(customFieldProvisioner, atLeastOnce()).ensure(
                eq("Item"),
                eq("aas_review_default_margin_used"),
                eq("Review Default Margin Used"),
                eq("Check"),
                eq(null),
                eq("aas_review_notes"),
                eq(true),
                eq(false));
        verify(customFieldProvisioner, atLeastOnce()).ensure(
                eq("Sales Invoice"),
                eq("aas_invoice_version_status"),
                eq("Invoice Version Status"),
                eq("Select"),
                eq("CURRENT\nOLD"),
                eq("aas_source_sales_order"),
                eq(true),
                eq(false));
        verify(customFieldProvisioner, atLeastOnce()).ensure(
                eq("Purchase Invoice"),
                eq("aas_invoice_version_status"),
                eq("Invoice Version Status"),
                eq("Select"),
                eq("CURRENT\nOLD"),
                eq("aas_source_sales_order"),
                eq(true),
                eq(false));
        verify(customFieldProvisioner, atLeastOnce()).ensure(
                eq("Company"),
                eq("aas_bank_beneficiary_name"),
                eq("Bank Beneficiary Name"),
                eq("Data"),
                eq(null),
                eq("aas_sales_invoice_print_format"),
                eq(true),
                eq(false));
        verify(customFieldProvisioner, atLeastOnce()).ensure(
                eq("Company"),
                eq("aas_bank_account_number"),
                eq("Bank Account Number"),
                eq("Data"),
                eq(null),
                eq("aas_bank_name"),
                eq(true),
                eq(false));
    }

    @Test
    void defaultSupplierUsesRootSupplierGroup() {
        service = new SetupService(
                erpNextClient,
                customFieldProvisioner,
                vendorFieldRegistry,
                catalogRoutingService,
                true,
                "Supplier",
                "Customer",
                "Stock User",
                "Accounts User,Sales User",
                "vendor@example.com",
                "Vendor User",
                "vendor123",
                "FreshHarvest Agro Foods",
                "shop@example.com",
                "Shop User",
                "shop123",
                "Sukarta Aundh",
                "helper@example.com",
                "Helper User",
                "helper123",
                7.0);

        service.ensureSetup();

        org.mockito.Mockito.verify(erpNextClient).createResource(
                eq("Supplier"),
                argThat(payload -> "All Supplier Groups".equals(payload.get("supplier_group"))
                        && "FRESHHARVEST_AGRO_FOODS".equals(payload.get("aas_vendor_code"))
                        && "FreshHarvest Agro Foods".equals(payload.get("supplier_name"))));
    }

    @Test
    void existingDefaultHelperUserIsSyncedWithConfiguredPasswordAndRole() {
        when(erpNextClient.getResource(eq("User"), eq("helper@example.com")))
                .thenReturn(Map.of("data", Map.of("name", "helper@example.com")));
        when(erpNextClient.updateResource(eq("User"), eq("helper@example.com"), anyMap()))
                .thenReturn(Map.of("name", "helper@example.com"));

        service = new SetupService(
                erpNextClient,
                customFieldProvisioner,
                vendorFieldRegistry,
                catalogRoutingService,
                true,
                "Supplier",
                "Customer",
                "Stock User",
                "Accounts User,Sales User",
                "vendor@example.com",
                "Vendor User",
                "vendor123",
                "FreshHarvest Agro Foods",
                "shop@example.com",
                "Shop User",
                "shop123",
                "Sukarta Aundh",
                "helper@example.com",
                "Helper User",
                "helper123",
                7.0);

        service.ensureSetup();

        verify(erpNextClient).updateResource(
                eq("User"),
                eq("helper@example.com"),
                argThat(payload -> "helper123".equals(payload.get("new_password"))
                        && List.of(
                                Map.of("role", "Stock User"),
                                Map.of("role", "Accounts User"),
                                Map.of("role", "Sales User"))
                                .equals(payload.get("roles"))
                        && "".equals(payload.get("supplier"))
                        && "".equals(payload.get("customer"))));
    }

    @Test
    void salesInvoicePrintFormatHidesTransportLineItemsFromVisibleTable() {
        when(erpNextClient.listResources(eq("Print Format"), anyMap()))
                .thenReturn(List.of(Map.of("name", "AAS Sales Invoice Print")));
        when(erpNextClient.updateResource(eq("Print Format"), eq("AAS Sales Invoice Print"), anyMap()))
                .thenReturn(Map.of("name", "AAS Sales Invoice Print"));

        service.ensureSetup();

        verify(erpNextClient).updateResource(
                eq("Print Format"),
                eq("AAS Sales Invoice Print"),
                argThat(payload -> {
                    String html = String.valueOf(payload.get("html"));
                    assertTrue(html.contains("{% if not is_transport %}"));
                    assertTrue(html.contains("{% set totals.visible_rows = totals.visible_rows + 1 %}"));
                    assertTrue(html.contains("<td class=\"center\">{{ totals.visible_rows }}</td>"));
                    return true;
                }));
    }

    private feign.FeignException.NotFound notFound(String doctype, String name) {
        Request request = Request.create(Request.HttpMethod.GET, "/api/resource/" + doctype + "/" + name, Map.of(), null, null, null);
        Response response = Response.builder()
                .status(404)
                .reason("Not Found")
                .request(request)
                .headers(Map.of())
                .build();
        return (feign.FeignException.NotFound) feign.FeignException.errorStatus("getResource", response);
    }
}
