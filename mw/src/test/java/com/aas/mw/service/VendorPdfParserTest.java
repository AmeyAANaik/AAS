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
}
