package com.aas.mw.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Positive;

public class OrderItemLine {

    @NotBlank
    private String item_code;

    @Positive
    private double qty;

    private double rate;

    @PositiveOrZero
    private double aas_margin_percent;

    public String getItem_code() {
        return item_code;
    }

    public void setItem_code(String item_code) {
        this.item_code = item_code;
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
}
