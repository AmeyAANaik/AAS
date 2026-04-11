package com.aas.mw.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NativeLayoutDeterministicFlowIntegrationTest {

    private static NativeLayoutInvoiceService service;

    @BeforeAll
    static void setup() {
        service = new NativeLayoutInvoiceService(new ObjectMapper(), "");
    }

    static Stream<Arguments> vendorExtractionProfiles() {
        return Stream.of(
                Arguments.of("DECCAN.pdf", "../temp/DECCAN.pdf", 38, profileForDeccanLikePdf()),
                Arguments.of("Sales3329.pdf", "../temp/Sales3329.pdf", 72, profileForSales3329Pdf()),
                Arguments.of("Sales_3391.pdf", "../temp/Sales_3391.pdf", 90, profileForSales3391Pdf()),
                Arguments.of("invoice_template.pdf", "../temp/invoice_template.pdf", 38, profileForDeccanLikePdf())
        );
    }

    @ParameterizedTest(name = "integrationFlowAllPages: {0}")
    @MethodSource("vendorExtractionProfiles")
    void integrationFlowCapturesAllNumberedRowsAcrossAllPages(
            String fileName,
            String relativePath,
            int expectedItemCount,
            NativeLayoutInvoiceService.StoredProfile profile) throws IOException {
        assumePdftotextAvailable();
        byte[] pdfBytes = Files.readAllBytes(Path.of(relativePath));

        NativeLayoutInvoiceService.ExtractionResult result = service.extract(pdfBytes, profile);

        assertEquals(expectedItemCount, result.items().size(), "Unexpected item count for " + fileName);
        assertEquals(expectedItemCount, result.extractedSerials().size(), "Unexpected serial count for " + fileName);
        assertEquals(1, result.extractedSerials().getFirst(), "Expected first serial to be 1 for " + fileName);
        assertEquals(expectedItemCount, result.extractedSerials().getLast(),
                "Expected last serial to match item count for " + fileName);
    }

    @Test
    void integrationFlowPreservesWrappedDescriptionsForSales3329() throws IOException {
        assumePdftotextAvailable();
        byte[] pdfBytes = Files.readAllBytes(Path.of("../temp/Sales3329.pdf"));

        NativeLayoutInvoiceService.ExtractionResult result = service.extract(pdfBytes, profileForSales3329Pdf());

        assertEquals("SFK KIRTI GOLD SUNFLOWER OIL 13kg", result.items().get(30).name());
        assertEquals("EVEREST KASHMIRI CHILLY POWDER 500GM", result.items().get(38).name());
        assertEquals("CHINGS GREEN CHILLY SAUCE 680GMS", result.items().get(68).name());
    }

    private static NativeLayoutInvoiceService.StoredProfile profileForDeccanLikePdf() {
        return new NativeLayoutInvoiceService.StoredProfile(
                "deccan-profile",
                "Deccan Profile",
                "Test Vendor",
                "",
                List.of(
                        mapping("item_name", "Particulars"),
                        mapping("item_id", "HSN Code"),
                        mapping("qty", "Qty"),
                        mapping("rate", "Rate"),
                        mapping("gst", "Tax(%)"),
                        mapping("total", "Total Value")),
                List.of(),
                "",
                "native_layout",
                "",
                "",
                "",
                Map.of(),
                Map.of(),
                Map.of(
                        "qty", "decimal_amount",
                        "rate", "decimal_amount",
                        "gst", "percentage",
                        "total", "decimal_amount"),
                List.of(),
                List.of("After Tax"));
    }

    private static NativeLayoutInvoiceService.StoredProfile profileForSales3329Pdf() {
        return new NativeLayoutInvoiceService.StoredProfile(
                "sales3329-profile",
                "Sales3329 Profile",
                "Test Vendor",
                "",
                List.of(
                        mapping("item_name", "Description of Goods"),
                        mapping("item_id", "HSN/SAC"),
                        mapping("qty", "Quantity"),
                        mapping("uom", "per"),
                        mapping("rate", "Rate"),
                        mapping("mrp", "MRP"),
                        mapping("gst", "GST"),
                        mapping("total", "Amount")),
                List.of(),
                "",
                "native_layout",
                "",
                "",
                "",
                Map.of(),
                Map.of(),
                Map.of(
                        "qty", "number_with_uom",
                        "rate", "decimal_amount",
                        "mrp", "decimal_amount",
                        "gst", "percentage",
                        "total", "decimal_amount"),
                List.of(),
                List.of("No.", "Rate"));
    }

    private static NativeLayoutInvoiceService.StoredProfile profileForSales3391Pdf() {
        return new NativeLayoutInvoiceService.StoredProfile(
                "sales3391-profile",
                "Sales3391 Profile",
                "Test Vendor",
                "",
                List.of(
                        mapping("item_name", "Description of Goods"),
                        mapping("item_id", "HSN/SAC"),
                        mapping("qty", "Quantity"),
                        mapping("uom", "per"),
                        mapping("rate", "Rate"),
                        mapping("mrp", "MRP"),
                        mapping("gst", "GST"),
                        mapping("total", "Amount")),
                List.of(),
                "",
                "native_layout",
                "",
                "",
                "",
                Map.of("preferredRateColumn", "Rate"),
                Map.of(),
                Map.of(
                        "qty", "number_with_uom",
                        "rate", "decimal_amount",
                        "mrp", "decimal_amount",
                        "gst", "percentage",
                        "total", "decimal_amount"),
                List.of(),
                List.of("No.", "Rate", "(Incl. of Tax)"));
    }

    private static InvoiceFieldMappingService.FieldMapping mapping(String targetField, String sourceLabel) {
        return new InvoiceFieldMappingService.FieldMapping(targetField, sourceLabel, true, true, "high");
    }

    private static void assumePdftotextAvailable() {
        boolean available;
        try {
            Process p = new ProcessBuilder("pdftotext", "-v")
                    .redirectErrorStream(true)
                    .start();
            p.waitFor();
            available = true;
        } catch (Exception e) {
            available = false;
        }
        Assumptions.assumeTrue(available, "pdftotext not available — skipping PDF extraction integration tests");
    }
}
