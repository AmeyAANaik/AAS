package com.aas.mw.controller;

import com.aas.mw.dto.DownloadedFile;
import com.aas.mw.service.ErpNextFileService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class FileProxyController {

    private final ErpNextFileService erpNextFileService;

    public FileProxyController(ErpNextFileService erpNextFileService) {
        this.erpNextFileService = erpNextFileService;
    }

    @RequestMapping(value = {"/api/files/**", "/api/private/files/**"}, method = RequestMethod.GET)
    public ResponseEntity<byte[]> proxyFile(HttpServletRequest request) {
        String uri = request.getRequestURI() == null ? "" : request.getRequestURI().trim();
        if (!uri.startsWith("/api/files/") && !uri.startsWith("/api/private/files/")) {
            return ResponseEntity.notFound().build();
        }

        String path = uri.substring("/api".length());
        String query = request.getQueryString();
        String fileUrl = (query == null || query.isBlank()) ? path : path + "?" + query;

        DownloadedFile downloaded = erpNextFileService.downloadFile(fileUrl);
        MediaType mediaType;
        try {
            mediaType = MediaType.parseMediaType(downloaded.contentType());
        } catch (Exception ignored) {
            mediaType = MediaType.APPLICATION_OCTET_STREAM;
        }

        return ResponseEntity.ok()
                .contentType(mediaType)
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + downloaded.filename() + "\"")
                .cacheControl(CacheControl.noCache())
                .body(downloaded.bytes());
    }
}

