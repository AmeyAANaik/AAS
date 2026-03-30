package com.aas.mw.dto;

import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Positive;

public class OrderItemLine {

    private String item_code;

    private String item_name;

    @Positive
    private double qty;

    private double rate;

    @PositiveOrZero
    private double aas_margin_percent;

    @PositiveOrZero
    private Double aas_mrp;

    @PositiveOrZero
    private Double aas_gst_percent;

    private boolean manual_entry;

    private String parse_note;

    public String getItem_code() {
        return item_code;
    }

    public void setItem_code(String item_code) {
        this.item_code = item_code;
    }

    public String getItem_name() {
        return item_name;
    }

    public void setItem_name(String item_name) {
        this.item_name = item_name;
    }

    public double getQty() {
        return qty;
    }

    public void setQty(double qty) {
        this.qty = qty;
    }

    public double getRate() {
        return rate;
    }

    public void setRate(double rate) {
        this.rate = rate;
    }

    public double getAas_margin_percent() {
        return aas_margin_percent;
    }

    public void setAas_margin_percent(double aas_margin_percent) {
        this.aas_margin_percent = aas_margin_percent;
    }

    public Double getAas_mrp() {
        return aas_mrp;
    }

    public void setAas_mrp(Double aas_mrp) {
        this.aas_mrp = aas_mrp;
    }

    public Double getAas_gst_percent() {
        return aas_gst_percent;
    }

    public void setAas_gst_percent(Double aas_gst_percent) {
        this.aas_gst_percent = aas_gst_percent;
    }

    public boolean isManual_entry() {
        return manual_entry;
    }

    public void setManual_entry(boolean manual_entry) {
        this.manual_entry = manual_entry;
    }

    public String getParse_note() {
        return parse_note;
    }

    public void setParse_note(String parse_note) {
        this.parse_note = parse_note;
    }
}
