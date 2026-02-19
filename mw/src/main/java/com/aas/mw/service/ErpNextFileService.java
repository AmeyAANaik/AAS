package com.aas.mw.service;

import com.aas.mw.dto.UploadedFileInfo;
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

    public UploadedFileInfo uploadOrderImage(String orderId, MultipartFile file, String sessionCookie) {
        String filename = normalizeFilename(file.getOriginalFilename(), "branch_order");
        return uploadFile("Sales Order", orderId, filename, file, sessionCookie);
    }

    public UploadedFileInfo uploadOrderPdf(String orderId, MultipartFile file, String sessionCookie) {
        String filename = normalizeFilename(file.getOriginalFilename(), "vendor_order");
        return uploadFile("Sales Order", orderId, filename, file, sessionCookie);
    }

    public UploadedFileInfo uploadFile(
            String doctype,
            String docname,
            String filename,
            MultipartFile file,
            String sessionCookie) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("File is required.");
        }
        if (sessionCookie == null || sessionCookie.isBlank()) {
            throw new IllegalStateException("Missing ERPNext session cookie.");
        }
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("doctype", doctype);
        body.add("docname", docname);
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
        return extractFileInfo(response, filename);
    }

    private byte[] toBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to read uploaded file.", ex);
        }
    }

    private UploadedFileInfo extractFileInfo(Map<String, Object> response, String fallbackName) {
        if (response == null) {
            return new UploadedFileInfo(fallbackName, null, null);
        }
        Object message = response.get("message");
        if (message instanceof Map<?, ?> map) {
            String fileName = getString(map, "file_name", fallbackName);
            String fileUrl = getString(map, "file_url", "");
            String fileId = getString(map, "name", "");
            return new UploadedFileInfo(fileName, fileUrl.isBlank() ? null : fileUrl, fileId.isBlank() ? null : fileId);
        }
        return new UploadedFileInfo(fallbackName, null, null);
    }

    private String getString(Map<?, ?> map, String key, String fallback) {
        Object value = map.get(key);
        if (value == null) {
            return fallback;
        }
        String text = value.toString();
        return text.isBlank() ? fallback : text;
    }

    private String normalizeFilename(String original, String prefix) {
        String extension = ".bin";
        if (original != null && !original.isBlank()) {
            int dot = original.lastIndexOf('.');
            if (dot > -1 && dot < original.length() - 1) {
                extension = original.substring(dot);
            }
        }
        return prefix + extension;
    }
}
