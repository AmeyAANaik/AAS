package com.aas.mw.service;

import com.aas.mw.config.ReportingProperties;
import com.aas.mw.dto.reporting.ReportingCatalogItem;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReportingCatalogServiceTest {

    @Test
    void defaultCatalogIsUsedWhenAllowedListEmpty() {
        ReportingProperties properties = new ReportingProperties();
        properties.setAllowedReportNames(List.of());
        ReportingCatalogService service = new ReportingCatalogService(properties);

        List<ReportingCatalogItem> catalog = service.catalog();
        assertEquals(4, catalog.size());
        assertTrue(service.isAllowed("AAS - Sales Profit Summary"));
        assertFalse(service.isAllowed("Some Random Report"));
    }

    @Test
    void catalogIsFilteredWhenAllowedListProvided() {
        ReportingProperties properties = new ReportingProperties();
        properties.setAllowedReportNames(List.of("AAS - Vendor Sales & Profit"));
        ReportingCatalogService service = new ReportingCatalogService(properties);

        List<ReportingCatalogItem> catalog = service.catalog();
        assertEquals(1, catalog.size());
        assertEquals("AAS - Vendor Sales & Profit", catalog.get(0).getReportName());
        assertTrue(service.isAllowed("AAS - Vendor Sales & Profit"));
        assertFalse(service.isAllowed("AAS - Sales Profit Summary"));
    }
}

