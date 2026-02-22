package com.aas.mw.meta;

import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class VendorFieldRegistry {

    private final VendorFieldsProperties properties;

    public VendorFieldRegistry(VendorFieldsProperties properties) {
        this.properties = properties;
    }

    public List<VendorFieldSpec> vendorFields() {
        List<VendorFieldSpec> configured = properties.getFields();
        if (configured != null && !configured.isEmpty()) {
            return List.copyOf(configured);
        }
        return defaultFields();
    }

    private List<VendorFieldSpec> defaultFields() {
        List<VendorFieldSpec> defaults = new ArrayList<>();
        // Keep UI-facing keys stable (e.g., "gst", "phone") while storing to ERP with "aas_*" fieldnames.
        defaults.add(new VendorFieldSpec(
                "branch_name",
                "aas_branch_name",
                "Branch Name",
                "Data",
                null,
                "supplier_name",
                true,
                false));
        defaults.add(new VendorFieldSpec(
                "address",
                "aas_address",
                "Address",
                "Small Text",
                null,
                "aas_branch_name",
                false,
                false));
        defaults.add(new VendorFieldSpec(
                "phone",
                "aas_phone",
                "Phone No",
                "Data",
                null,
                "aas_address",
                true,
                false));
        defaults.add(new VendorFieldSpec(
                "gst",
                "aas_gst_no",
                "GST No",
                "Data",
                null,
                "aas_phone",
                true,
                false));
        defaults.add(new VendorFieldSpec(
                "pan",
                "aas_pan_no",
                "PAN No",
                "Data",
                null,
                "aas_gst_no",
                false,
                false));
        defaults.add(new VendorFieldSpec(
                "food_license_no",
                "aas_food_license_no",
                "Food License No",
                "Data",
                null,
                "aas_pan_no",
                true,
                false));
        defaults.add(new VendorFieldSpec(
                "aas_priority",
                "aas_priority",
                "Priority",
                "Int",
                null,
                "aas_food_license_no",
                true,
                false));
        return List.copyOf(defaults);
    }
}

