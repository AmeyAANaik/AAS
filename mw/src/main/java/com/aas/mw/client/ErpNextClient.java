package com.aas.mw.client;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

@Component
public class ErpNextClient {

    private final ErpNextFeignClient feignClient;

    public ErpNextClient(ErpNextFeignClient feignClient) {
        this.feignClient = feignClient;
    }

    public void login(String username, String password) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("usr", username);
        form.add("pwd", password);
        feignClient.login(form);
    }

    public Map<String, Object> getResource(String doctype, String id) {
        return feignClient.getResource(doctype, id);
    }

    public List<Map<String, Object>> listResources(String doctype, Map<String, Object> params) {
        Map<String, Object> body = feignClient.listResources(doctype, params == null ? Collections.emptyMap() : params);
        if (body == null || !body.containsKey("data")) {
            return Collections.emptyList();
        }
        Object data = body.get("data");
        if (data instanceof List<?>) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> casted = (List<Map<String, Object>>) data;
            return casted;
        }
        return Collections.emptyList();
    }

    public Map<String, Object> createResource(String doctype, Map<String, Object> payload) {
        return feignClient.createResource(doctype, payload);
    }

    public Map<String, Object> updateResource(String doctype, String id, Map<String, Object> payload) {
        return feignClient.updateResource(doctype, id, payload);
    }
}
