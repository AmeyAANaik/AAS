package com.aas.mw.service;

import com.aas.mw.dto.DownloadedFile;
import com.aas.mw.dto.UploadedFileInfo;
import java.io.IOException;
import java.net.URI;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ErpNextFileService {

    private final RestTemplate restTemplate;
    private final String baseUrl;
    private final String publicBaseUrl;
    private final String apiAuthHeader;

    public ErpNextFileService(
            RestTemplateBuilder restTemplateBuilder,
            @Value("${erpnext.base-url}") String baseUrl,
            @Value("${erpnext.public-base-url:${erpnext.base-url}}") String publicBaseUrl,
            @Value("${erpnext.api-key:}") String apiKey,
            @Value("${erpnext.api-secret:}") String apiSecret) {
        this.restTemplate = restTemplateBuilder.build();
        this.baseUrl = baseUrl;
        this.publicBaseUrl = publicBaseUrl;
        this.apiAuthHeader = (apiKey != null && !apiKey.isBlank() && apiSecret != null && !apiSecret.isBlank())
                ? "token " + apiKey.trim() + ":" + apiSecret.trim()
                : "";
    }

    public UploadedFileInfo uploadOrderImage(String orderId, MultipartFile file, String sessionCookie) {
        String filename = normalizeFilename(file.getOriginalFilename(), "branch_order");
        return uploadFile("Sales Order", orderId, filename, file, sessionCookie);
    }

    public UploadedFileInfo uploadOrderPdf(String orderId, MultipartFile file, String sessionCookie) {
        String filename = normalizeFilename(file.getOriginalFilename(), "vendor_order");
        return uploadFile("Sales Order", orderId, filename, file, sessionCookie);
    }

    public UploadedFileInfo uploadOrderPdf(
            String orderId,
            String filename,
            byte[] bytes,
            String contentType,
            String sessionCookie) {
        String safeFilename = normalizeFilename(filename, "vendor_order");
        return uploadBytes("Sales Order", orderId, safeFilename, bytes, contentType, sessionCookie);
    }

    public DownloadedFile downloadFile(String fileUrl) {
        String resolvedUrl = resolveInternalFileUrl(fileUrl);
        HttpEntity<?> request = HttpEntity.EMPTY;
        if (!apiAuthHeader.isBlank()) {
            HttpHeaders headers = new HttpHeaders();
            headers.set(HttpHeaders.AUTHORIZATION, apiAuthHeader);
            request = new HttpEntity<>(headers);
        }
        ResponseEntity<byte[]> response = restTemplate.exchange(
                resolvedUrl,
                HttpMethod.GET,
                request,
                byte[].class);
        byte[] bytes = response.getBody();
        if (bytes == null || bytes.length == 0) {
            throw new IllegalStateException("Downloaded file is empty.");
        }
        String contentType = response.getHeaders().getContentType() == null
                ? MediaType.APPLICATION_OCTET_STREAM_VALUE
                : response.getHeaders().getContentType().toString();
        String filename = resolveFilename(fileUrl);
        return new DownloadedFile(filename, resolveContentType(filename, contentType, bytes), bytes);
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
        return uploadBytes(doctype, docname, filename, toBytes(file), file.getContentType(), sessionCookie);
    }

    public UploadedFileInfo uploadBytes(
            String doctype,
            String docname,
            String filename,
            byte[] bytes,
            String contentType,
            String sessionCookie) {
        if (bytes == null || bytes.length == 0) {
            throw new IllegalArgumentException("File content is required.");
        }
        if (sessionCookie == null || sessionCookie.isBlank()) {
            throw new IllegalStateException("Missing ERPNext session cookie.");
        }
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("doctype", doctype);
        body.add("docname", docname);
        body.add("file_name", filename);
        body.add("is_private", "0");
        body.add("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }

            @Override
            public long contentLength() {
                return bytes.length;
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
            return new UploadedFileInfo(
                    fileName,
                    fileUrl.isBlank() ? null : resolveFileUrl(fileUrl),
                    fileId.isBlank() ? null : fileId);
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

    private String resolveInternalFileUrl(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            throw new IllegalArgumentException("File URL is required.");
        }
        if (filePath.startsWith("http://") || filePath.startsWith("https://")) {
            return filePath.replace(publicBaseUrl, baseUrl);
        }
        String base = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        String path = filePath.startsWith("/") ? filePath : "/" + filePath;
        return base + path;
    }

    private String resolveFilename(String fileUrl) {
        String value = fileUrl == null ? "" : fileUrl.trim();
        if (value.isBlank()) {
            return "download.bin";
        }
        String path = URI.create(resolveFileUrl(value)).getPath();
        int slash = path.lastIndexOf('/');
        if (slash >= 0 && slash < path.length() - 1) {
            return path.substring(slash + 1);
        }
        return "download.bin";
    }

    private String resolveContentType(String filename, String contentType, byte[] bytes) {
        String normalizedType = contentType == null ? "" : contentType.trim();
        String normalizedName = filename == null ? "" : filename.trim().toLowerCase();
        if (normalizedName.endsWith(".pdf") || startsWith(bytes, "%PDF")) {
            return MediaType.APPLICATION_PDF_VALUE;
        }
        if (normalizedName.endsWith(".jpg") || normalizedName.endsWith(".jpeg")) {
            return MediaType.IMAGE_JPEG_VALUE;
        }
        if (normalizedName.endsWith(".png")) {
            return MediaType.IMAGE_PNG_VALUE;
        }
        return normalizedType.isBlank() ? MediaType.APPLICATION_OCTET_STREAM_VALUE : contentType;
    }

    private boolean startsWith(byte[] bytes, String prefix) {
        if (bytes == null || prefix == null || bytes.length < prefix.length()) {
            return false;
        }
        byte[] prefixBytes = prefix.getBytes(java.nio.charset.StandardCharsets.US_ASCII);
        for (int i = 0; i < prefixBytes.length; i++) {
            if (bytes[i] != prefixBytes[i]) {
                return false;
            }
        }
        return true;
    }

    private String resolveFileUrl(String filePath) {
        if (filePath == null || filePath.isBlank()) {
            return filePath;
        }
        if (filePath.startsWith("http://") || filePath.startsWith("https://")) {
            return filePath;
        }
        String base = publicBaseUrl.endsWith("/") ? publicBaseUrl.substring(0, publicBaseUrl.length() - 1) : publicBaseUrl;
        String path = filePath.startsWith("/") ? filePath : "/" + filePath;
        return base + path;
    }
}
