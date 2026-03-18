package com.aas.mw.service;

import com.aas.mw.dto.ParsedItem;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void parsesSerializedRowsWithWrappedNameSuffix() {
        VendorInvoiceTemplateParser parser = new VendorInvoiceTemplateParser();
        VendorInvoiceTemplate template = new VendorInvoiceTemplate(
                1,
                "^\\d+\\s+(?<name>[A-Z0-9/&().,'%-]+(?:\\s+[A-Z0-9/&().,'%-]+)*)\\s+(?<hsn>\\d{4,10})\\s+"
                        + "(?<gst>\\d+(?:\\.\\d+)?)\\s*%\\s+(?<qty>\\d+(?:\\.\\d+)?)\\s+(?:KG|PCS|LTR|ML|TIN|GMS|PKT)\\s+"
                        + "(?<mrp>\\d+(?:\\.\\d+)?)\\s+\\d+(?:\\.\\d+)?\\s+(?<rate>\\d+(?:\\.\\d+)?)\\s+"
                        + "(?:KG|PCS|LTR|ML|TIN|GMS|PKT)\\s+(?<amount>\\d+(?:,\\d{3})*(?:\\.\\d+)?)"
                        + "(?:\\s+(?!\\d+\\s)[A-Z0-9/&().,'%-]+(?:\\s+[A-Z0-9/&().,'%-]+)*)?$",
                null,
                null);

        String text = """
                Sl Description of Goods HSN/SAC GST Quantity MRP Rate Rate per Amount
                6 CHITLE GULABJAMUN 21069099 5 % 6.00 KG 410 375.00 357.14 KG 2,142.84
                MIX 1KG
                7 SFK MOONG DAL 07132000 0 % 30.00 KG 0 106.00 106.00 KG 3,180.00
                """;

        List<ParsedItem> items = parser.parseItems(text, template);

        assertEquals(2, items.size());
        assertEquals("CHITLE GULABJAMUN", items.get(0).name());
        assertEquals("21069099", items.get(0).hsn());
        assertEquals(6.0, items.get(0).qty());
        assertEquals(357.14, items.get(0).rate());
        assertEquals(2142.84, items.get(0).amount());
        assertTrue(items.get(0).mrp() != null && items.get(0).mrp() == 410.0);
        assertEquals("SFK MOONG DAL", items.get(1).name());
    }
}
