package com.aas.mw.util;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

public final class XlsUtil {

    private XlsUtil() {}

    public static byte[] toXls(List<Map<String, Object>> rows) throws IOException {
        return toXls(rows, "Ledger");
    }

    public static byte[] toXls(List<Map<String, Object>> rows, String sheetName) throws IOException {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet(sheetName);

            if (rows == null || rows.isEmpty()) {
                workbook.write(out);
                return out.toByteArray();
            }

            Set<String> headerSet = new LinkedHashSet<>();
            for (Map<String, Object> row : rows) {
                if (row != null) {
                    headerSet.addAll(row.keySet());
                }
            }
            List<String> headers = new ArrayList<>(headerSet);

            CellStyle headerStyle = workbook.createCellStyle();
            Font headerFont = workbook.createFont();
            headerFont.setBold(true);
            headerStyle.setFont(headerFont);

            Row headerRow = sheet.createRow(0);
            for (int i = 0; i < headers.size(); i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers.get(i));
                cell.setCellStyle(headerStyle);
            }

            int rowIdx = 1;
            for (Map<String, Object> row : rows) {
                Row excelRow = sheet.createRow(rowIdx++);
                for (int i = 0; i < headers.size(); i++) {
                    Object value = row == null ? null : row.get(headers.get(i));
                    Cell cell = excelRow.createCell(i);
                    if (value instanceof Number) {
                        cell.setCellValue(((Number) value).doubleValue());
                    } else if (value != null) {
                        cell.setCellValue(String.valueOf(value));
                    }
                }
            }

            for (int i = 0; i < headers.size(); i++) {
                sheet.autoSizeColumn(i);
            }

            workbook.write(out);
            return out.toByteArray();
        }
    }
}
