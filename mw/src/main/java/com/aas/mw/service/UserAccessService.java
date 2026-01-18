package com.aas.mw.service;

import com.aas.mw.client.ErpNextClient;
import java.util.Map;
import org.springframework.stereotype.Service;

@Service
public class UserAccessService {

    private final ErpNextClient erpNextClient;

    public UserAccessService(ErpNextClient erpNextClient) {
        this.erpNextClient = erpNextClient;
    }

    public Map<String, Object> getUser(String userId) {
        return erpNextClient.getResource("User", userId);
    }
}
