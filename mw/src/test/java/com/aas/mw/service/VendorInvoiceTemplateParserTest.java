package com.aas.mw.service;

import com.aas.mw.dto.ParsedItem;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class VendorInvoiceTemplateParserTest {

    @Test
    void parsesItemsUsingNamedGroups() {
        VendorInvoiceTemplateParser parser = new VendorInvoiceTemplateParser();
        VendorInvoiceTemplate template = new VendorInvoiceTemplate(
                1,
                "^(?<name>.+?)\\s+(?<qty>\\d+(?:\\.\\d+)?)\\s+(?<rate>\\d+(?:\\.\\d+)?)\\s+(?<amount>\\d+(?:\\.\\d+)?)\\s+(?<hsn>\\d{4,10})$",
                null,
                null);

        String text = """
                Item Description Qty Rate Amount HSN
                Potato - 50 kg sack            3       1325.00   3975.00   0701
                Onion - 50 kg sack             2       1480.00   2960.00   0703
                """;

        List<ParsedItem> items = parser.parseItems(text, template);

        assertFalse(items.isEmpty());
        assertEquals(2, items.size());
        assertEquals("Potato - 50 kg sack", items.get(0).name());
        assertEquals(3.0, items.get(0).qty());
        assertEquals(1325.0, items.get(0).rate());
        assertEquals(3975.0, items.get(0).amount());
        assertEquals("0701", items.get(0).hsn());
    }
}
