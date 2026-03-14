package com.aas.mw.dto;

public record DownloadedFile(
        String fileName,
        String contentType,
        byte[] bytes) {
}
