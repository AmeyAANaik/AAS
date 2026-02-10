package com.aas.mw.service;

import java.io.IOException;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ErpNextFileService {

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public ErpNextFileService(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${erpnext.base-url}") String baseUrl) {
        this.restTemplate = restTemplateBuilder.build();
        this.baseUrl = baseUrl;
    }

    public Map<String, Object> uploadOrderImage(String orderId, MultipartFile file, String sessionCookie) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("Image file is required.");
        }
        if (sessionCookie == null || sessionCookie.isBlank()) {
            throw new IllegalStateException("Missing ERPNext session cookie.");
        }
        String filename = normalizeFilename(file.getOriginalFilename(), orderId);
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("doctype", "Sales Order");
        body.add("docname", orderId);
        body.add("file_name", filename);
        body.add("is_private", "0");
        body.add("file", new ByteArrayResource(toBytes(file)) {
            @Override
            public String getFilename() {
                return filename;
            }
        });

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);
        headers.add(HttpHeaders.COOKIE, sessionCookie);

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        @SuppressWarnings("unchecked")
        Map<String, Object> response = restTemplate.postForObject(
                baseUrl + "/api/method/upload_file",
                requestEntity,
                Map.class);
        return response;
    }

    private byte[] toBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read uploaded file.", ex);
        }
    }

    private String normalizeFilename(String original, String orderId) {
        if (original == null || original.isBlank()) {
            return "order-" + orderId + ".png";
        }
        return original.replaceAll("\\s+", "_");
    }
}
