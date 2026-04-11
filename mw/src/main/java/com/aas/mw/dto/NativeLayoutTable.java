package com.aas.mw.dto;

import java.util.List;

public record NativeLayoutTable(
        String tableId,
        List<String> headers,
        List<NativeLayoutTableRow> rows,
        List<String> rawLines) {
}
