package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

class OpeningBalanceImportServiceTest {

    @Test
    void templateCsv_includesRequiredHeaderAndExampleRows() {
        OpeningBalanceImportService service = new OpeningBalanceImportService(mock(ErpNextClient.class), mock(PaymentDueService.class));

        String csv = service.templateCsv("ACME");

        String[] lines = csv.split("\\r?\\n");
        assertThat(lines[0]).isEqualTo("record_type,account,debit,credit,cost_center,party_id,amount,bill_no,invoice_ref,category");
        assertThat(lines).anyMatch(line -> line.startsWith("ACCOUNT,"));
        assertThat(lines).anyMatch(line -> line.startsWith("SUPPLIER,"));
        assertThat(lines).anyMatch(line -> line.startsWith("CUSTOMER,"));
    }

    @Test
    void templateCsv_requiresCompanyId() {
        OpeningBalanceImportService service = new OpeningBalanceImportService(mock(ErpNextClient.class), mock(PaymentDueService.class));

        assertThatThrownBy(() -> service.templateCsv("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("companyId is required");
    }
}
