package com.aas.mw.dto;

import java.util.List;

public record NativeLayoutTableRow(
        int rowNumber,
        List<String> cells,
        String rawText) {
}
