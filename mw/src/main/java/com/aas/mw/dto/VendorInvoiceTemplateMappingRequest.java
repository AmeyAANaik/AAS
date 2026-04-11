package com.aas.mw.dto;

import java.util.List;

public record VendorInvoiceTemplateMappingRequest(
        List<VendorInvoiceFieldMapping> itemMappings,
        List<VendorInvoiceFieldMapping> summaryMappings) {
}
