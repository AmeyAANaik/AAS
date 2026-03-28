package com.aas.mw.service;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PdfTextExtractionService {

    public String extractPlainText(byte[] pdfBytes) {
        if (pdfBytes == null || pdfBytes.length == 0) {
            throw new IllegalArgumentException("Sample PDF is required.");
        }

        Path workingDir = null;
        try {
            workingDir = Files.createTempDirectory("aas-pdf-text-");
            Path pdfPath = workingDir.resolve("invoice.pdf");
            Files.write(pdfPath, pdfBytes);

            List<String> command = List.of(
                    "pdftotext",
                    pdfPath.toString(),
                    "-");

            ProcessBuilder processBuilder = new ProcessBuilder(command);
            Process process = processBuilder.start();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            String errorOutput = new String(process.getErrorStream().readAllBytes(), StandardCharsets.UTF_8);
            int exitCode = process.waitFor();
            String normalized = normalizeText(output);
            if (!normalized.isBlank()) {
                return normalized;
            }
            if (exitCode != 0) {
                throw new IllegalStateException("pdftotext failed: " + summarizeProcessOutput(errorOutput));
            }
            throw new IllegalStateException("pdftotext did not extract any text from the sample PDF.");
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to prepare the sample PDF for pdftotext.", ex);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("pdftotext execution was interrupted.", ex);
        } finally {
            deleteQuietly(workingDir);
        }
    }

    private String normalizeText(String output) {
        if (output == null || output.isBlank()) {
            return "";
        }
        List<String> keptLines = new ArrayList<>();
        for (String rawLine : output.replace('\f', '\n').split("\\r?\\n")) {
            String line = rawLine == null ? "" : rawLine.trim();
            if (line.isBlank() || line.startsWith("Syntax Error (")) {
                continue;
            }
            keptLines.add(line);
        }
        return String.join("\n", keptLines).trim();
    }

    private String summarizeProcessOutput(String output) {
        if (output == null || output.isBlank()) {
            return "no process output";
        }
        String compact = output.replaceAll("\\s+", " ").trim();
        return compact.length() > 400 ? compact.substring(0, 400) + "..." : compact;
    }

    private void deleteQuietly(Path path) {
        if (path == null) {
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
