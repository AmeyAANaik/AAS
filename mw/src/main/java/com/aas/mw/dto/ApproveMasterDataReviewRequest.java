package com.aas.mw.dto;

public class ApproveMasterDataReviewRequest {
    private String item_name;
    private String item_group;
    private String stock_uom;
    private String aas_packaging_unit;
    private Double aas_margin_percent;
    private String aas_vendor_hsn_code;
    private Double aas_gst_percent;
    private String reviewNotes;
    private boolean applyToSourceOrder;

    public String getItem_name() {
        return item_name;
    }

    public void setItem_name(String item_name) {
        this.item_name = item_name;
    }

    public String getItem_group() {
        return item_group;
    }

    public void setItem_group(String item_group) {
        this.item_group = item_group;
    }

    public String getStock_uom() {
        return stock_uom;
    }

    public void setStock_uom(String stock_uom) {
        this.stock_uom = stock_uom;
    }

    public String getAas_packaging_unit() {
        return aas_packaging_unit;
    }

    public void setAas_packaging_unit(String aas_packaging_unit) {
        this.aas_packaging_unit = aas_packaging_unit;
    }

    public Double getAas_margin_percent() {
        return aas_margin_percent;
    }

    public void setAas_margin_percent(Double aas_margin_percent) {
        this.aas_margin_percent = aas_margin_percent;
    }

    public String getAas_vendor_hsn_code() {
        return aas_vendor_hsn_code;
    }

    public void setAas_vendor_hsn_code(String aas_vendor_hsn_code) {
        this.aas_vendor_hsn_code = aas_vendor_hsn_code;
    }

    public Double getAas_gst_percent() {
        return aas_gst_percent;
    }

    public void setAas_gst_percent(Double aas_gst_percent) {
        this.aas_gst_percent = aas_gst_percent;
    }

    public String getReviewNotes() {
        return reviewNotes;
    }

    public void setReviewNotes(String reviewNotes) {
        this.reviewNotes = reviewNotes;
    }

    public boolean isApplyToSourceOrder() {
        return applyToSourceOrder;
    }

    public void setApplyToSourceOrder(boolean applyToSourceOrder) {
        this.applyToSourceOrder = applyToSourceOrder;
    }
}
