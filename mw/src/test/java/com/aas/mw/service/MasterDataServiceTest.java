package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.dto.FieldsRequest;
import com.aas.mw.meta.VendorFieldRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MasterDataServiceTest {

    private ErpNextClient erpNextClient;
    private UomService uomService;
    private MasterDataService masterDataService;

    @BeforeEach
    void setup() {
        erpNextClient = mock(ErpNextClient.class);
        uomService = mock(UomService.class);
        when(uomService.normalizeUom(eq("Kg"))).thenReturn("Kg");
        masterDataService = new MasterDataService(
                erpNextClient,
                mock(VendorFieldRegistry.class),
                new ObjectMapper(),
                mock(OcrService.class),
                mock(VendorInvoiceTemplateParser.class),
                mock(NativeLayoutInvoiceService.class),
                mock(InvoiceTemplateModelService.class),
                mock(CatalogRoutingService.class),
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
}
