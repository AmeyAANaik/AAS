package com.aas.mw.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class CamelotTableExtractionService {

    public record ExtractionResult(
            String text,
            List<String> preview,
            int rowCount) {
    }

    private final ObjectMapper objectMapper;
    private final String configuredPythonCommand;

    public CamelotTableExtractionService(
            ObjectMapper objectMapper,
            @Value("${app.pdf.python-command:}") String configuredPythonCommand) {
        this.objectMapper = objectMapper;
        this.configuredPythonCommand = configuredPythonCommand == null ? "" : configuredPythonCommand.trim();
    }

    public ExtractionResult extract(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            return new ExtractionResult("", List.of(), 0);
        }

        Path workingDir = null;
        try {
            workingDir = Files.createTempDirectory("aas-camelot-");
            Path pdfPath = workingDir.resolve("invoice.pdf");
            Files.write(pdfPath, pdfBytes);

            String script = """
                    import camelot
                    import json
                    import sys

                    pdf_path = sys.argv[1]

                    def normalize(rows):
                        normalized = []
                        for row in rows:
                            cells = []
                            for cell in row:
                                value = str(cell).replace("\\n", " ").strip()
                                if value:
                                    cells.append(" ".join(value.split()))
                            if cells:
                                normalized.append(" | ".join(cells))
                        return normalized

                    extracted = []
                    for flavor in ("stream", "lattice"):
                        try:
                            tables = camelot.read_pdf(pdf_path, pages="all", flavor=flavor)
                            rows = []
                            for table in tables:
                                rows.extend(normalize(table.df.values.tolist()))
                            extracted.extend(rows)
                        except Exception:
                            continue

                    print(json.dumps({"rows": extracted}))
                    """;

            List<String> command = List.of(
                    resolvePythonCommand(),
                    "-c",
                    script,
                    pdfPath.toString());

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            processBuilder.redirectErrorStream(true);
            Process process = processBuilder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            if (exitCode != 0 || output.isBlank()) {
                return new ExtractionResult("", List.of(), 0);
            }

            Map<String, Object> payload = objectMapper.readValue(output, new TypeReference<Map<String, Object>>() {});
            List<String> rows = readRows(payload.get("rows"));
            String text = String.join("\n", rows);
            return new ExtractionResult(text, rows.stream().limit(24).toList(), rows.size());
        } catch (Exception ignored) {
            return new ExtractionResult("", List.of(), 0);
        } finally {
            deleteQuietly(workingDir);
        }
    }

    private List<String> readRows(Object rowsObj) {
        if (!(rowsObj instanceof List<?> rows) || rows.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (Object row : rows) {
            String value = row == null ? "" : String.valueOf(row).trim();
            if (!value.isBlank() && seen.add(value)) {
                result.add(value);
            }
        }
        return result;
    }

    private String resolvePythonCommand() {
        if (!configuredPythonCommand.isBlank()) {
            return configuredPythonCommand;
        }
        Path localVenv = Path.of("/tmp/aas-pdf-eval/bin/python");
        if (Files.exists(localVenv)) {
            return localVenv.toString();
        }
        return "python3";
    }

    private void deleteQuietly(Path path) {
        if (path == null || !Files.exists(path)) {
            return;
        }
        try {
            Files.walk(path)
                    .sorted((left, right) -> right.compareTo(left))
                    .forEach(candidate -> {
                        try {
                            Files.deleteIfExists(candidate);
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException ignored) {
        }
    }
}
