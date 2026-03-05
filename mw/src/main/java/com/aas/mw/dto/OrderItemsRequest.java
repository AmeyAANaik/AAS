package com.aas.mw.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public class OrderItemsRequest {

    @NotEmpty
    @Valid
    private List<OrderItemLine> items;

    public List<OrderItemLine> getItems() {
        return items;
    }

    public void setItems(List<OrderItemLine> items) {
        this.items = items;
    }
}

