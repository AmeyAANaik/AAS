package com.aas.mw.service;

import static org.mockito.Mockito.anyMap;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aas.mw.client.ErpNextClient;
import java.util.Map;
import org.junit.jupiter.api.Test;

class UomServiceTest {

    @Test
    void normalizeUom_mapsLtrToLitre() {
        ErpNextClient erpNextClient = mock(ErpNextClient.class);
        UomService service = new UomService(erpNextClient, 0);
        org.junit.jupiter.api.Assertions.assertEquals("Litre", service.normalizeUom("LTR"));
    }

    @Test
    void ensureUomExists_createsWhenMissing() {
        ErpNextClient erpNextClient = mock(ErpNextClient.class);
        when(erpNextClient.getResource(eq("UOM"), eq("Litre"))).thenReturn(Map.of("data", Map.of()));
        UomService service = new UomService(erpNextClient, 0);

        service.ensureUomExists("Litre");

        verify(erpNextClient).createResource(eq("UOM"), anyMap());
    }

    @Test
    void ensureUomExists_doesNothingWhenPresent() {
        ErpNextClient erpNextClient = mock(ErpNextClient.class);
        when(erpNextClient.getResource(eq("UOM"), eq("Litre"))).thenReturn(Map.of("data", Map.of("name", "Litre")));
        UomService service = new UomService(erpNextClient, 0);

        service.ensureUomExists("Litre");

        verify(erpNextClient, never()).createResource(eq("UOM"), anyMap());
    }
}

