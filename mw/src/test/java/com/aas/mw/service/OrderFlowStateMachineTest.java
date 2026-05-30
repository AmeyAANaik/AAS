package com.aas.mw.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class OrderFlowStateMachineTest {

    @Test
    void normalizeCoercesHumanReadableStatuses() {
        OrderFlowStateMachine stateMachine = new OrderFlowStateMachine();
        assertEquals("SELL_ORDER_CREATED", stateMachine.normalize("Sell order created"));
        assertEquals("VENDOR_PDF_RECEIVED", stateMachine.normalize("Vendor PDF received"));
        assertEquals("VENDOR_BILL_CAPTURED", stateMachine.normalize("vendor-bill captured"));
    }

    @Test
    void vendorPdfUploadAllowedForHumanReadableSellOrderStatus() {
        OrderFlowStateMachine stateMachine = new OrderFlowStateMachine();
        assertDoesNotThrow(() -> stateMachine.ensureCanUploadVendorPdf("Sell order created"));
    }
}

