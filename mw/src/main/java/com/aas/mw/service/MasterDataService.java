package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class MasterDataService {

    private final ErpNextClient erpNextClient;

    public MasterDataService(ErpNextClient erpNextClient) {
        this.erpNextClient = erpNextClient;
    }

    public List<Map<String, Object>> listItems() {
        return erpNextClient.listResources("Item", null);
    }

    public List<Map<String, Object>> listVendors() {
        return erpNextClient.listResources("Supplier", null);
    }

    public List<Map<String, Object>> listCategories() {
        return erpNextClient.listResources("Item Group", null);
    }
}
