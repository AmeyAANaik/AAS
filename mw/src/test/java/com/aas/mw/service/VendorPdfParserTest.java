package com.aas.mw.service;

import com.aas.mw.dto.ParsedItem;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class VendorPdfParserTest {

    @Test
    void parsesItemsFromTextLines() {
        VendorPdfParser parser = new VendorPdfParser();
        String text = """
                Item Qty Rate Amount
                Tomatoes 2 45 90
                Onions 1 30 30
                Total 120
                """;

        List<ParsedItem> items = parser.parseItems(text);

        assertFalse(items.isEmpty());
        assertEquals(2, items.size());
        assertEquals("Tomatoes", items.get(0).name());
        assertEquals(2.0, items.get(0).qty());
        assertEquals(45.0, items.get(0).rate());
        assertEquals(90.0, items.get(0).amount());
    }

    @Test
    void parsesItemLinesWithRowIndexAndCommas() {
        VendorPdfParser parser = new VendorPdfParser();
        String text = """
                1 Tomatoes 2 45 90
                2 Basmati Rice 1 1,200.50 1,200.50
                """;

        List<ParsedItem> items = parser.parseItems(text);

        assertEquals(2, items.size());
        assertEquals("Tomatoes", items.get(0).name());
        assertEquals(2.0, items.get(0).qty());
        assertEquals(90.0, items.get(0).amount());
        assertEquals("Basmati Rice", items.get(1).name());
        assertEquals(1200.50, items.get(1).rate());
        assertEquals(1200.50, items.get(1).amount());
    }

    @Test
    void infersMissingAmountOrRateWhenZero() {
        VendorPdfParser parser = new VendorPdfParser();
        String text = """
                Bananas 2 10 0
                Apples 2 100
                """;

        List<ParsedItem> items = parser.parseItems(text);

        assertEquals(2, items.size());
        assertEquals("Bananas", items.get(0).name());
        assertEquals(20.0, items.get(0).amount());
        assertEquals("Apples", items.get(1).name());
        assertEquals(50.0, items.get(1).rate());
        assertEquals(100.0, items.get(1).amount());
    }

    @Test
    void parsesLabeledColumnsInline() {
        VendorPdfParser parser = new VendorPdfParser();
        String text = "Tomatoes Qty: 2 Rate: 45 Amount: 90";

        List<ParsedItem> items = parser.parseItems(text);

        assertEquals(1, items.size());
        assertEquals("Tomatoes", items.get(0).name());
        assertEquals(2.0, items.get(0).qty());
        assertEquals(45.0, items.get(0).rate());
        assertEquals(90.0, items.get(0).amount());
    }

    @Test
    void parsesGstInvoiceRowWithPendingNameAndHsn() {
        VendorPdfParser parser = new VendorPdfParser();
        String text = """
                RICE BRAN OIL
                1 15.000 34200.03 2280.00 15121120
                """;

        List<ParsedItem> items = parser.parseItems(text);

        assertEquals(1, items.size());
        assertEquals("RICE BRAN OIL", items.get(0).name());
        assertEquals(15.0, items.get(0).qty());
        assertEquals(2280.0, items.get(0).rate());
        assertEquals(34200.03, items.get(0).amount());
    }

    @Test
    void parsesGstInvoiceRowWithoutNameUsingHsnFallback() {
        VendorPdfParser parser = new VendorPdfParser();
        String text = "1 15.000 34200.03 2280.00 15121120";

        List<ParsedItem> items = parser.parseItems(text);

        assertEquals(1, items.size());
        assertEquals(15.0, items.get(0).qty());
        assertEquals(2280.0, items.get(0).rate());
        assertEquals(34200.03, items.get(0).amount());
        assertEquals("HSN 15121120 (Line 1)", items.get(0).name());
    }

    @Test
    void deduplicatesItemsByNameAndHsn() {
        VendorPdfParser parser = new VendorPdfParser();
        String text = """
                RICE BRAN OIL
                1 10.000 100.00 10.00 15121120
                RICE BRAN OIL
                2 5.000 60.00 12.00 15121120
                """;

        List<ParsedItem> items = parser.parseItems(text);

        assertEquals(1, items.size());
        assertEquals("RICE BRAN OIL", items.get(0).name());
        assertEquals("15121120", items.get(0).hsn());
        assertEquals(15.0, items.get(0).qty());
        assertEquals(12.0, items.get(0).rate());
        assertEquals(100.0, items.get(0).amount());
    }
}
