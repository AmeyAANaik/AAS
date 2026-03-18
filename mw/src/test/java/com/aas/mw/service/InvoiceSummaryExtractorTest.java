package com.aas.mw.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InvoiceSummaryExtractorTest {

    @Test
    void extractsGrandTotalFromTransportAndAmountChargeableBlock() {
        String text = """
                Transport 1,500.00
                Total ī 1,78,855.00
                Amount Chargeable (in words) E. & O.E
                INR One Lakh Seventy Eight Thousand Eight Hundred Fifty Five Only
                Taxable CGST SGST/UTGST Total
                Value Rate Amount Rate Amount Tax Amount
                51,954.00 0% 0%
                1,15,939.16 2.50% 2,898.46 2.50% 2,898.46 5,796.92
                3,105.49 9% 279.50 9% 279.50 559.00
                Total: 1,70,998.65 3,177.96 3,177.96 6,355.92
                """;

        InvoiceSummaryExtractor.Extraction extraction = InvoiceSummaryExtractor.extractFinalAmount(text, "");

        assertEquals("1,78,855.00", extraction.amount());
        assertEquals("Total ī 1,78,855.00", extraction.matchedText());
    }

    @Test
    void respectsConfiguredRegexWhenItMatches() {
        String text = "Transport 1,500.00\nTotal ī 1,78,855.00\n";

        InvoiceSummaryExtractor.Extraction extraction = InvoiceSummaryExtractor.extractFinalAmount(
                text,
                "(?m)^Total\\s+[^\\d\\r\\n]*\\s*(?<final_bill_amount>\\d{1,3}(?:,\\d{2,3})*(?:\\.\\d{2}))\\s*$");

        assertEquals("1,78,855.00", extraction.amount());
        assertEquals("Total ī 1,78,855.00", extraction.matchedText());
    }
}
