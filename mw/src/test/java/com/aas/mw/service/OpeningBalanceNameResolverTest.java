package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class OpeningBalanceNameResolverTest {

    @Test
    void resolvesAccountByDocnameWhenFound() {
        ErpNextClient client = mock(ErpNextClient.class);
        when(client.getResource("Account", "Cash - AAS"))
                .thenReturn(Map.of("data", Map.of("name", "Cash - AAS", "company", "AAS")));

        OpeningBalanceImportService.NameResolver resolver = new OpeningBalanceImportService.NameResolver(client);
        OpeningBalanceImportService.ResolveResult result = resolver.resolveAccount("AAS", "Cash - AAS");

        assertThat(result.isOk()).isTrue();
        assertThat(result.id()).isEqualTo("Cash - AAS");
    }

    @Test
    void resolvesAccountByAccountNameWhenDocnameMissing() {
        ErpNextClient client = mock(ErpNextClient.class);
        when(client.getResource("Account", "Cash - AAS"))
                .thenThrow(new RuntimeException("not found"));
        when(client.listResources(org.mockito.ArgumentMatchers.eq("Account"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(List.of(Map.of("name", "ACC-0001", "account_name", "Cash - AAS", "company", "AAS")));

        OpeningBalanceImportService.NameResolver resolver = new OpeningBalanceImportService.NameResolver(client);
        OpeningBalanceImportService.ResolveResult result = resolver.resolveAccount("AAS", "Cash - AAS");

        assertThat(result.isOk()).isTrue();
        assertThat(result.id()).isEqualTo("ACC-0001");
    }

    @Test
    void resolvesAccountFailsOnAmbiguousAccountName() {
        ErpNextClient client = mock(ErpNextClient.class);
        when(client.getResource("Account", "Cash"))
                .thenThrow(new RuntimeException("not found"));
        when(client.listResources(org.mockito.ArgumentMatchers.eq("Account"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(List.of(
                        Map.of("name", "ACC-0001", "account_name", "Cash", "company", "AAS"),
                        Map.of("name", "ACC-0002", "account_name", "Cash", "company", "AAS")));

        OpeningBalanceImportService.NameResolver resolver = new OpeningBalanceImportService.NameResolver(client);
        OpeningBalanceImportService.ResolveResult result = resolver.resolveAccount("AAS", "Cash");

        assertThat(result.isOk()).isFalse();
        assertThat(result.message()).contains("ambiguous");
        assertThat(result.message()).contains("ACC-0001");
        assertThat(result.message()).contains("ACC-0002");
    }

    @Test
    void resolvesCustomerByCustomerNameWhenDocnameMissing() {
        ErpNextClient client = mock(ErpNextClient.class);
        when(client.getResource("Customer", "Sahyadri All-Day Dining"))
                .thenThrow(new RuntimeException("not found"));
        when(client.listResources(org.mockito.ArgumentMatchers.eq("Customer"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(List.of(Map.of("name", "CUST-0001", "customer_name", "Sahyadri All-Day Dining")));

        OpeningBalanceImportService.NameResolver resolver = new OpeningBalanceImportService.NameResolver(client);
        OpeningBalanceImportService.ResolveResult result = resolver.resolveCustomer("Sahyadri All-Day Dining");

        assertThat(result.isOk()).isTrue();
        assertThat(result.id()).isEqualTo("CUST-0001");
    }

    @Test
    void resolvesItemGroupByItemGroupNameWhenDocnameMissing() {
        ErpNextClient client = mock(ErpNextClient.class);
        when(client.getResource("Item Group", "Food"))
                .thenThrow(new RuntimeException("not found"));
        when(client.listResources(org.mockito.ArgumentMatchers.eq("Item Group"), org.mockito.ArgumentMatchers.anyMap()))
                .thenReturn(List.of(Map.of("name", "FOOD", "item_group_name", "Food")));

        OpeningBalanceImportService.NameResolver resolver = new OpeningBalanceImportService.NameResolver(client);
        OpeningBalanceImportService.ResolveResult result = resolver.resolveItemGroup("Food");

        assertThat(result.isOk()).isTrue();
        assertThat(result.id()).isEqualTo("FOOD");
    }
}

