package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import com.aas.mw.dto.OrderRequest;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class OrderService {

    private static final String DOCTYPE = "Sales Order";

    private final ErpNextClient erpNextClient;

    public OrderService(ErpNextClient erpNextClient) {
        this.erpNextClient = erpNextClient;
    }

    public Map<String, Object> createOrder(OrderRequest request) {
        return erpNextClient.createResource(DOCTYPE, request.getFields());
    }

    public Map<String, Object> getOrder(String id) {
        return erpNextClient.getResource(DOCTYPE, id);
    }

    public Map<String, Object> updateOrder(String id, OrderRequest request) {
        return erpNextClient.updateResource(DOCTYPE, id, request.getFields());
    }
}
