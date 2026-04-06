package com.aas.mw.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.dto.ApproveMasterDataReviewRequest;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MasterDataReviewServiceTest {

    private ErpNextClient erpNextClient;
    private OrderService orderService;
    private MasterDataReviewService service;

    @BeforeEach
    void setup() {
        erpNextClient = mock(ErpNextClient.class);
        orderService = mock(OrderService.class);
        service = new MasterDataReviewService(erpNextClient, orderService);
    }

    @Test
    void countsOnlyPendingReviewItems() {
        when(erpNextClient.listResources(eq("Item"), anyMap())).thenReturn(List.of(
                Map.of("name", "ITEM-1", "item_code", "ITEM-1", "item_name", "Item 1", "aas_review_status", "PENDING_REVIEW"),
                Map.of("name", "ITEM-2", "item_code", "ITEM-2", "item_name", "Item 2", "aas_review_status", "APPROVED"),
                Map.of("name", "ITEM-3", "item_code", "ITEM-3", "item_name", "Item 3")));

        Map<String, Object> count = service.getPendingCount();

        assertEquals(1L, count.get("pendingCount"));
    }

    @Test
    void approvalCanUpdateSourceOrderMargin() {
        when(erpNextClient.getResource(eq("Item"), eq("ITEM-1"))).thenReturn(Map.of("data", Map.of(
                "name", "ITEM-1",
                "item_code", "ITEM-1",
                "item_name", "Item 1",
                "aas_review_status", "PENDING_REVIEW",
                "aas_review_source_order", "SO-1",
                "aas_margin_percent", 7.0)));
        when(erpNextClient.updateResource(eq("Item"), eq("ITEM-1"), anyMap())).thenReturn(Map.of("data", Map.of(
                "name", "ITEM-1",
                "item_code", "ITEM-1",
                "item_name", "Item 1",
                "aas_review_status", "APPROVED",
                "aas_review_source_order", "SO-1",
                "aas_margin_percent", 12.0)));
        when(orderService.applyReviewedMarginToOrderItem(eq("SO-1"), eq("ITEM-1"), eq(12.0))).thenReturn(true);
        when(erpNextClient.listResources(eq("Item"), anyMap())).thenReturn(List.of());

        ApproveMasterDataReviewRequest request = new ApproveMasterDataReviewRequest();
        request.setItem_name("Item 1");
        request.setItem_group("Grocery");
        request.setStock_uom("Nos");
        request.setAas_margin_percent(12.0);
        request.setApplyToSourceOrder(true);

        Map<String, Object> result = service.approveReviewItem("ITEM-1", request);

        assertTrue((Boolean) result.get("sourceOrderUpdated"));
        verify(orderService).applyReviewedMarginToOrderItem("SO-1", "ITEM-1", 12.0);
    }
}
