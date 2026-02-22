package com.aas.mw.meta;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

public class VendorFieldMapperTest {

    @Test
    void mapsApiKeysToErpFieldnamesAndAllowsErpNames() {
        VendorFieldMapper mapper = new VendorFieldMapper(List.of(
                new VendorFieldSpec("gst", "aas_gst_no", "GST No", "Data", null, null, true, false),
                new VendorFieldSpec("phone", "aas_phone", "Phone No", "Data", null, null, true, false)
        ));

        Map<String, Object> erp = mapper.toErpPayload(Map.of(
                "gst", "29ABCDE1234F1Z5",
                "aas_phone", "9999999999",
                "unknown", "drop-me"
        ));

        assertThat(erp).containsEntry("aas_gst_no", "29ABCDE1234F1Z5");
        assertThat(erp).containsEntry("aas_phone", "9999999999");
        assertThat(erp).doesNotContainKey("unknown");
        assertThat(erp).doesNotContainKey("gst");
    }

    @Test
    void addsApiAliasesFromErpRecord() {
        VendorFieldMapper mapper = new VendorFieldMapper(List.of(
                new VendorFieldSpec("gst", "aas_gst_no", "GST No", "Data", null, null, true, false),
                new VendorFieldSpec("phone", "aas_phone", "Phone No", "Data", null, null, true, false)
        ));

        Map<String, Object> erpRecord = new HashMap<>();
        erpRecord.put("name", "SUP-0001");
        erpRecord.put("supplier_name", "Vendor A");
        erpRecord.put("aas_gst_no", "29ABCDE1234F1Z5");
        erpRecord.put("aas_phone", null);

        Map<String, Object> apiView = mapper.withApiAliases(erpRecord);

        assertThat(apiView).containsEntry("gst", "29ABCDE1234F1Z5");
        assertThat(apiView).containsKey("phone");
        assertThat(apiView.get("phone")).isNull();
    }

    @Test
    void producesFieldsJsonForFrappeList() {
        VendorFieldMapper mapper = new VendorFieldMapper(List.of(
                new VendorFieldSpec("gst", "aas_gst_no", "GST No", "Data", null, null, true, false),
                new VendorFieldSpec("phone", "aas_phone", "Phone No", "Data", null, null, true, false)
        ));

        String fieldsJson = mapper.erpListFieldsJson();
        assertThat(fieldsJson).contains("\"name\"");
        assertThat(fieldsJson).contains("\"supplier_name\"");
        assertThat(fieldsJson).contains("\"disabled\"");
        assertThat(fieldsJson).contains("\"aas_gst_no\"");
        assertThat(fieldsJson).contains("\"aas_phone\"");
    }
}
