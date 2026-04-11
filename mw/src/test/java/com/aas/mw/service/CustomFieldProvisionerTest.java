package com.aas.mw.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aas.mw.client.ErpNextClient;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

public class CustomFieldProvisionerTest {

    @Test
    void createsMissingCustomField() {
        ErpNextClient erpNextClient = Mockito.mock(ErpNextClient.class);
        when(erpNextClient.listResources(eq("Custom Field"), anyMap()))
                .thenReturn(Collections.emptyList());

        CustomFieldProvisioner provisioner = new CustomFieldProvisioner(erpNextClient);
        boolean changed = provisioner.ensure(
                "Supplier",
                "aas_phone",
                "Phone No",
                "Data",
                null,
                "supplier_name",
                true,
                false);

        assertThat(changed).isTrue();
        ArgumentCaptor<Map<String, Object>> payloadCaptor = ArgumentCaptor.forClass(Map.class);
        verify(erpNextClient).createResource(eq("Custom Field"), payloadCaptor.capture());
        Map<String, Object> payload = payloadCaptor.getValue();
        assertThat(payload).containsEntry("dt", "Supplier");
        assertThat(payload).containsEntry("fieldname", "aas_phone");
        assertThat(payload).containsEntry("label", "Phone No");
        assertThat(payload).containsEntry("fieldtype", "Data");
        assertThat(payload).containsEntry("insert_after", "supplier_name");
        assertThat(payload).containsEntry("in_list_view", 1);
        assertThat(payload).containsEntry("reqd", 0);
    }

    @Test
    void updatesOptionsWhenDifferent() {
        ErpNextClient erpNextClient = Mockito.mock(ErpNextClient.class);
        when(erpNextClient.listResources(eq("Custom Field"), anyMap()))
                .thenReturn(List.of(Map.of("name", "Custom Field-1", "options", "OLD")));

        CustomFieldProvisioner provisioner = new CustomFieldProvisioner(erpNextClient);
        boolean changed = provisioner.ensure(
                "Sales Order",
                "aas_status",
                "AAS Status",
                "Select",
                "NEW",
                "customer",
                true,
                false);

        assertThat(changed).isTrue();
        verify(erpNextClient).updateResource(eq("Custom Field"), eq("Custom Field-1"), eq(Map.of("options", "NEW")));
    }
}

