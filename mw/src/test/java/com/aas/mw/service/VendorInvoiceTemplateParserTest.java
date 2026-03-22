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
                "^(?<name>.+?)\\s+(?<qty>\\d+(?:\\.\\d+)?)\\s+(?<uom>[A-Za-z]{1,6})\\s+(?<rate>\\d+(?:\\.\\d+)?)\\s+(?<amount>\\d+(?:\\.\\d+)?)\\s+(?<hsn>\\d{4,10})$",
                null,
                null,
                null);

        String text = """
                Item Description Qty UOM Rate Amount HSN
                Potato - 50 kg sack            3 KG    1325.00   3975.00   0701
                Onion - 50 kg sack             2 BAG   1480.00   2960.00   0703
                """;

        List<ParsedItem> items = parser.parseItems(text, template);

        assertFalse(items.isEmpty());
        assertEquals(2, items.size());
        assertEquals("Potato - 50 kg sack", items.get(0).name());
        assertEquals(3.0, items.get(0).qty());
        assertEquals("KG", items.get(0).uom());
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
                        + "(?<gst>\\d+(?:\\.\\d+)?)\\s*%\\s+(?<qty>\\d+(?:\\.\\d+)?)\\s+(?<uom>KG|PCS|LTR|ML|TIN|GMS|PKT)\\s+"
                        + "(?<mrp>\\d+(?:\\.\\d+)?)\\s+\\d+(?:\\.\\d+)?\\s+(?<rate>\\d+(?:\\.\\d+)?)\\s+"
                        + "(?:KG|PCS|LTR|ML|TIN|GMS|PKT)\\s+(?<amount>\\d+(?:,\\d{3})*(?:\\.\\d+)?)"
                        + "(?:\\s+(?!\\d+\\s)[A-Z0-9/&().,'%-]+(?:\\s+[A-Z0-9/&().,'%-]+)*)?$",
                null,
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
        assertEquals("KG", items.get(0).uom());
        assertEquals(357.14, items.get(0).rate());
        assertEquals(2142.84, items.get(0).amount());
        assertTrue(items.get(0).mrp() != null && items.get(0).mrp() == 410.0);
        assertEquals("SFK MOONG DAL", items.get(1).name());
    }

    @Test
    void parsesPercentGstAndCommaSeparatedRateFromSerializedRows() {
        VendorInvoiceTemplateParser parser = new VendorInvoiceTemplateParser();
        VendorInvoiceTemplate template = new VendorInvoiceTemplate(
                1,
                "^(?:\\d+\\s+)?(?<name>.+?)\\s+(?:\\S+\\s+){0,2}(?<hsn>\\d{4,10})\\s+(?:\\S+\\s+){0,2}"
                        + "(?<gst>\\d+(?:\\.\\d+)?\\s*%)\\s+(?:\\S+\\s+){0,2}(?<qty>\\d+(?:\\.\\d+)?)\\s+"
                        + "(?:\\S+\\s+){0,2}(?<mrp>\\d{1,3}(?:,\\d{2,3})*(?:\\.\\d+)?)\\s+"
                        + "(?:\\S+\\s+){0,2}(?<rate>\\d{1,3}(?:,\\d{2,3})*(?:\\.\\d+)?)\\s+"
                        + "(?:\\S+\\s+){0,2}(?<uom>[A-Za-z]{1,12})\\s+(?:\\S+\\s+){0,2}"
                        + "(?<amount>\\d{1,3}(?:,\\d{2,3})*(?:\\.\\d+)?)$",
                null,
                null,
                null);

        String text = """
                Sl Description of Goods HSN/SAC GST Quantity MRP Rate per Amount
                31 SFK KIRTI GOLD SUNFLOWER 15121910 5 % 12.00 TIN 0 2,295.24 TIN 27,542.88
                OIL 13kg
                32 SFK SABUDANA 07149010 5 % 2.00 KG 0 54.29 KG 108.58
                """;

        List<ParsedItem> items = parser.parseItems(text, template);

        assertEquals(2, items.size());
        assertEquals("SFK KIRTI", items.get(0).name());
        assertEquals("15121910", items.get(0).hsn());
        assertEquals(5.0, items.get(0).gstPercent());
        assertEquals(12.0, items.get(0).qty());
        assertEquals("TIN", items.get(0).uom());
        assertEquals(2295.24, items.get(0).rate());
        assertEquals(27542.88, items.get(0).amount());
        assertEquals("SFK", items.get(1).name());
        assertEquals(5.0, items.get(1).gstPercent());
    }

    @Test
    void stopsAtPageFooterWhenParsingNumberedRows() {
        VendorInvoiceTemplateParser parser = new VendorInvoiceTemplateParser();
        VendorInvoiceTemplate template = new VendorInvoiceTemplate(
                1,
                "^(?:\\d+\\s+)?(?<name>.+?)\\s+(?:\\S+\\s+){0,2}(?<hsn>\\d{4,10})\\s+(?:\\S+\\s+){0,2}"
                        + "(?<gst>\\d+(?:\\.\\d+)?\\s*%)\\s+(?:\\S+\\s+){0,2}(?<qty>\\d+(?:\\.\\d+)?)\\s+"
                        + "(?:\\S+\\s+){0,2}(?<mrp>\\d{1,3}(?:,\\d{2,3})*(?:\\.\\d+)?)\\s+"
                        + "(?:\\S+\\s+){0,2}(?<rate>\\d{1,3}(?:,\\d{2,3})*(?:\\.\\d+)?)\\s+"
                        + "(?:\\S+\\s+){0,2}(?<uom>[A-Za-z]{1,12})\\s+(?:\\S+\\s+){0,2}"
                        + "(?<amount>\\d{1,3}(?:,\\d{2,3})*(?:\\.\\d+)?)$",
                null,
                null,
                null);

        String text = """
                39 EVEREST KASHMIRI CHILLY 09109100 5 % 4.00 PCS 530 447.62 PKT 1,790.48
                POWDER 500GM
                40 PRAVIN AMBARI DHANIYA POWDER 09103090 5 % 5.00 KG 400 314.29 KG 1,571.45
                continued to page number  2
                This is a Computer Generated Invoice
                INVOICE(Page  2)
                """;

        List<ParsedItem> items = parser.parseItems(text, template);

        assertEquals(2, items.size());
        assertEquals(4.0, items.get(0).qty());
        assertEquals(5.0, items.get(0).gstPercent());
        assertEquals(5.0, items.get(1).qty());
        assertEquals(314.29, items.get(1).rate());
        assertEquals(1571.45, items.get(1).amount());
        assertEquals("KG", items.get(1).uom());
    }
}
