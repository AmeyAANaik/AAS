package com.aas.mw.service;

import com.aas.mw.config.InvoiceTemplateModelProperties;
import com.aas.mw.dto.NativeLayoutSample;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NativeLayoutInvoiceServiceTest {

    private static NativeLayoutInvoiceService service;

    @BeforeAll
    static void setup() {
        service = new NativeLayoutInvoiceService(new ObjectMapper(), "");
    }

    static Stream<Arguments> vendorPdfs() {
        return Stream.of(
                Arguments.of("DECCAN.pdf",           "../temp/DECCAN.pdf"),
                Arguments.of("Sales3329.pdf",        "../temp/Sales3329.pdf"),
                Arguments.of("Sales_3391.pdf",       "../temp/Sales_3391.pdf"),
                Arguments.of("invoice_template.pdf", "../temp/invoice_template.pdf")
        );
    }

    @ParameterizedTest(name = "extractSample: {0}")
    @MethodSource("vendorPdfs")
    void extractSampleReturnsParsedLayoutForEachVendorPdf(String fileName, String relativePath) throws IOException {
        assumePdftotextAvailable();
        byte[] pdfBytes = Files.readAllBytes(Path.of(relativePath));

        NativeLayoutSample sample = service.extractSample(fileName, "", pdfBytes);

        assertFalse(sample.layoutText().isBlank(),
                "Expected non-blank layoutText for " + fileName);
        assertFalse(sample.tables().isEmpty(),
                "Expected at least one detected table for " + fileName);
    }

    @ParameterizedTest(name = "extractWithEmptyProfile: {0}")
    @MethodSource("vendorPdfs")
    void extractWithEmptyProfileDoesNotCrashForEachVendorPdf(String fileName, String relativePath) throws IOException {
        assumePdftotextAvailable();
        byte[] pdfBytes = Files.readAllBytes(Path.of(relativePath));

        NativeLayoutInvoiceService.StoredProfile profile = new NativeLayoutInvoiceService.StoredProfile(
                "test-profile",
                "Test Profile",
                "Test Vendor",
                "",
                List.of(),
                List.of(),
                "",
                "native_layout",
                "",
                "",
                "",
                Map.of(),
                Map.of(),
                Map.of(),
                List.of(),
                List.of());

        NativeLayoutInvoiceService.ExtractionResult result = service.extract(pdfBytes, profile);

        assertNotNull(result, "Expected non-null ExtractionResult for " + fileName);
        assertFalse(result.layoutText().isBlank(),
                "Expected non-blank layoutText in ExtractionResult for " + fileName);
    }

    @ParameterizedTest(name = "extractionCount: {0}")
    @MethodSource("vendorPdfs")
    void extractionCountMatchesExpectedSerialsForEachVendorPdf(String fileName, String relativePath) throws IOException {
        assumePdftotextAvailable();
        byte[] pdfBytes = Files.readAllBytes(Path.of(relativePath));

        NativeLayoutSample sample = service.extractSample(fileName, "", pdfBytes);

        InvoiceTemplateModelProperties props = new InvoiceTemplateModelProperties();
        String promptInput = service.buildMappingPromptInput(
                "test-vendor", "Test Vendor", sample,
                props.getItemFields(), props.getSummaryFields());

        Matcher m = Pattern.compile("\"totalTableRows\"\\s*:\\s*(\\d+)").matcher(promptInput);
        assertTrue(m.find(), "Expected totalTableRows in mapping prompt for " + fileName);
        int totalTableRows = Integer.parseInt(m.group(1));
        assertTrue(totalTableRows > 0,
                "Expected totalTableRows > 0 for " + fileName + ", got " + totalTableRows);

        // Run full extraction with empty profile to count extracted items
        NativeLayoutInvoiceService.StoredProfile emptyProfile = new NativeLayoutInvoiceService.StoredProfile(
                "test", "Test", "Test", "", List.of(), List.of(),
                "", "native_layout", "", "", "",
                Map.of(), Map.of(), Map.of(), List.of(), List.of());
        NativeLayoutInvoiceService.ExtractionResult result = service.extract(pdfBytes, emptyProfile);

        int largestSingleTable = sample.tables().isEmpty() ? 0
                : sample.tables().stream().mapToInt(t -> t.rows().size()).max().orElse(0);

        System.out.printf(
                "[%s] pages=%d  tables=%d  mergedRows=%d  extractedItems=%d  largestSingleTable=%d%n",
                fileName,
                sample.pageCount(),
                sample.tables().size(),
                totalTableRows,
                result.items().size(),
                largestSingleTable);
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
        Assumptions.assumeTrue(available, "pdftotext not available — skipping PDF extraction tests");
    }
}
