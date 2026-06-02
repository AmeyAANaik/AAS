package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import java.math.BigDecimal;
import java.lang.reflect.Constructor;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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

    @Test
    void openingInvoicePayloadsUseTemporaryOpeningAccountOnItems() {
        ErpNextClient erpNextClient = mock(ErpNextClient.class);
        when(erpNextClient.getResource("Company", "AAS"))
                .thenReturn(Map.of("data", Map.of(
                        "default_currency", "INR",
                        "temporary_opening_account", "Temporary Opening - AAS")));
        OpeningBalanceImportService service = new OpeningBalanceImportService(erpNextClient, mock(PaymentDueService.class));
        Object row = partyAmount("Supplier", "SUP-1", new BigDecimal("500.00"), "OB-001", "GENERAL");

        @SuppressWarnings("unchecked")
        Map<String, Object> purchasePayload = (Map<String, Object>) ReflectionTestUtils.invokeMethod(
                service, "buildPurchaseInvoicePayload", "AAS", "2026-06-01", row);
        @SuppressWarnings("unchecked")
        Map<String, Object> salesPayload = (Map<String, Object>) ReflectionTestUtils.invokeMethod(
                service, "buildSalesInvoicePayload", "AAS", "2026-06-01", row);

        @SuppressWarnings("unchecked")
        Map<String, Object> purchaseItem = (Map<String, Object>) ((List<?>) purchasePayload.get("items")).get(0);
        @SuppressWarnings("unchecked")
        Map<String, Object> salesItem = (Map<String, Object>) ((List<?>) salesPayload.get("items")).get(0);

        assertThat(purchaseItem.get("expense_account")).isEqualTo("Temporary Opening - AAS");
        assertThat(salesItem.get("income_account")).isEqualTo("Temporary Opening - AAS");
    }

    private Object partyAmount(String partyType, String partyId, BigDecimal amount, String reference, String category) {
        try {
            Class<?> type = Class.forName("com.aas.mw.service.OpeningBalanceImportService$PartyAmount");
            Constructor<?> ctor = type.getDeclaredConstructor(String.class, String.class, BigDecimal.class, String.class, String.class);
            ctor.setAccessible(true);
            return ctor.newInstance(partyType, partyId, amount, reference, category);
        } catch (Exception ex) {
            throw new RuntimeException(ex);
        }
    }
}
