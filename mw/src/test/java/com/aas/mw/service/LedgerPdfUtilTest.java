package com.aas.mw.service;

import com.aas.mw.util.LedgerPdfUtil;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LedgerPdfUtilTest {

    @Test
    void toPdfGeneratesPdfWithLedgerRows() throws Exception {
        byte[] pdf = LedgerPdfUtil.toPdf(
                List.of(Map.of(
                        "date", "2026-06-10",
                        "voucherType", "Sales Invoice",
                        "voucherNo", "SINV-001",
                        "reference", "Test Branch",
                        "debit", 5000.0,
                        "credit", 0.0,
                        "netChange", 5000.0,
                        "runningBalance", 5000.0)),
                "Branch Ledger Test");

        assertThat(pdf).isNotEmpty();
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }
}
