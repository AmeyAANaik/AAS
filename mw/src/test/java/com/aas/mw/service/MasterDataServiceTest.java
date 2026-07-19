package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.dto.FieldsRequest;
import com.aas.mw.meta.VendorFieldRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MasterDataServiceTest {

    private ErpNextClient erpNextClient;
    private UomService uomService;
    private CatalogRoutingService catalogRoutingService;
    private MasterDataService masterDataService;

    @BeforeEach
    void setup() {
        erpNextClient = mock(ErpNextClient.class);
        uomService = mock(UomService.class);
        catalogRoutingService = mock(CatalogRoutingService.class);
        when(uomService.normalizeUom(eq("Kg"))).thenReturn("Kg");
        when(uomService.normalizeUom(eq("Nos"))).thenReturn("Nos");
        masterDataService = new MasterDataService(
                erpNextClient,
                mock(VendorFieldRegistry.class),
                new ObjectMapper(),
                mock(OcrService.class),
                mock(VendorInvoiceTemplateParser.class),
                mock(NativeLayoutInvoiceService.class),
                mock(InvoiceTemplateModelService.class),
                catalogRoutingService,
                mock(ErpNextFileService.class),
                uomService,
                "http://localhost:8080");
    }

    @Test
    void updateItemPersistsDefaultVendorRate() {
        when(erpNextClient.updateResource(eq("Item"), eq("ITEM-1"), anyMap()))
                .thenReturn(Map.of("name", "ITEM-1", "aas_vendor_rate", 42.0));
        FieldsRequest request = new FieldsRequest();
        request.setFields(Map.of(
                "item_name", "Rice",
                "stock_uom", "Kg",
                "aas_vendor_rate", 42.0,
                "aas_margin_percent", 7.0));

        masterDataService.updateItem("ITEM-1", request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(erpNextClient).updateResource(eq("Item"), eq("ITEM-1"), payloadCaptor.capture());
        assertEquals(42.0, payloadCaptor.getValue().get("aas_vendor_rate"));
    }

    @Test
    void createItemCreatesWhenItemCodeDoesNotAlreadyExist() {
        String itemCode = "BHAMCHANDRADAIRY_DAIRY_100999";
        when(catalogRoutingService.resolveTopVendorForCategory("Dairy"))
                .thenReturn(new CatalogRoutingService.VendorCategoryResolution(
                        "SUP-1", "Bhamchandra Dairy", "BHAMCHANDRADAIRY", "Dairy", "Dairy", "DAIRY"));
        when(catalogRoutingService.buildItemCode("BHAMCHANDRADAIRY", "DAIRY", "100999"))
                .thenReturn(itemCode);
        when(catalogRoutingService.normalizeCodeSegment("100999")).thenReturn("100999");
        when(erpNextClient.listResources(eq("Item"), anyMap())).thenReturn(List.of());
        when(erpNextClient.getResource("Item", itemCode))
                .thenThrow(new RuntimeException("DoesNotExistError"));
        when(erpNextClient.createResource(eq("Item"), anyMap()))
                .thenReturn(Map.of("name", itemCode));

        FieldsRequest request = new FieldsRequest();
        request.setFields(Map.of(
                "item_name", "Milk",
                "item_group", "Dairy",
                "stock_uom", "Nos",
                "aas_vendor_hsn_code", "100999"));

        masterDataService.createItem(request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> listParamsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(erpNextClient).listResources(eq("Item"), listParamsCaptor.capture());
        verify(erpNextClient, never()).getResource("Item", itemCode);
        verify(erpNextClient).createResource(eq("Item"), payloadCaptor.capture());
        assertEquals("[[\"Item\",\"item_code\",\"=\",\"" + itemCode + "\"]]", listParamsCaptor.getValue().get("filters"));
        assertEquals(itemCode, payloadCaptor.getValue().get("item_code"));
        assertEquals("SUP-1", payloadCaptor.getValue().get("aas_vendor"));
    }

    @Test
    void createItemReEnablesDisabledItemByDocumentName() {
        when(catalogRoutingService.resolveTopVendorForCategory("Dairy"))
                .thenReturn(new CatalogRoutingService.VendorCategoryResolution(
                        "SUP-1", "Bhamchandra Dairy", "BHAMCHANDRADAIRY", "Dairy", "Dairy", "DAIRY"));
        when(catalogRoutingService.buildItemCode("BHAMCHANDRADAIRY", "DAIRY", "100999"))
                .thenReturn("BHAMCHANDRADAIRY_DAIRY_100999");
        when(catalogRoutingService.normalizeCodeSegment("100999")).thenReturn("100999");
        when(erpNextClient.listResources(eq("Item"), anyMap()))
                .thenReturn(List.of(Map.of(
                        "name", "ITEM-DOC-1",
                        "item_code", "BHAMCHANDRADAIRY_DAIRY_100999",
                        "disabled", 1)));
        when(erpNextClient.updateResource(eq("Item"), eq("ITEM-DOC-1"), anyMap()))
                .thenReturn(Map.of("name", "ITEM-DOC-1"));

        FieldsRequest request = new FieldsRequest();
        request.setFields(Map.of(
                "item_name", "Milk",
                "item_group", "Dairy",
                "stock_uom", "Nos",
                "aas_vendor_hsn_code", "100999"));

        masterDataService.createItem(request);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(erpNextClient).updateResource(eq("Item"), eq("ITEM-DOC-1"), payloadCaptor.capture());
        assertEquals(0, payloadCaptor.getValue().get("disabled"));
    }
}
