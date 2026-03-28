package com.aas.mw.dto;

import java.util.List;

public record NativeLayoutSample(
        String fileName,
        String fileUrl,
        String layoutText,
        int pageCount,
        List<NativeLayoutTable> tables,
        List<String> summaryLabels,
        List<String> summaryLines) {
}
