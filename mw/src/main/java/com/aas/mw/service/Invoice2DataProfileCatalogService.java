package com.aas.mw.service;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

@Service
public class Invoice2DataProfileCatalogService {

    public record Profile(
            String id,
            String label,
            String vendorName,
            String description,
            String inputReader,
            String yaml,
            List<String> supportedItemFields,
            List<String> supportedSummaryFields) {
    }

    private final Map<String, Profile> profiles;

    public Invoice2DataProfileCatalogService() {
        Map<String, Profile> catalog = new LinkedHashMap<>();
        catalog.put(
                "sanshray_foods_sales",
                new Profile(
                        "sanshray_foods_sales",
                        "Sanshray Foods sales invoice",
                        "Sanshray Foods",
                        "Structured grocery invoice with quantity, UOM, GST, transport, and total.",
                        "pdftotext",
                        loadYaml("invoice2data-templates/sanshray_foods_sales.yml"),
                        List.of("item_name", "item_id", "qty", "uom", "rate", "mrp", "gst", "total"),
                        List.of("final_bill_amount", "transport_charge")));
        catalog.put(
                "swastik_traders_invoice",
                new Profile(
                        "swastik_traders_invoice",
                        "Swastik Traders tax invoice",
                        "Swastik Traders",
                        "Tax invoice with item table, subtotal totals, and no dedicated UOM column.",
                        "pdftotext",
                        loadYaml("invoice2data-templates/swastik_traders_invoice.yml"),
                        List.of("item_name", "item_id", "qty", "rate", "gst", "total"),
                        List.of("final_bill_amount")));
        this.profiles = Map.copyOf(catalog);
    }

    public List<Map<String, Object>> describeProfiles() {
        return profiles.values().stream()
                .map(profile -> Map.<String, Object>of(
                        "id", profile.id(),
                        "label", profile.label(),
                        "vendorName", profile.vendorName(),
                        "description", profile.description(),
                        "inputReader", profile.inputReader(),
                        "supportedItemFields", profile.supportedItemFields(),
                        "supportedSummaryFields", profile.supportedSummaryFields()))
                .toList();
    }

    public Optional<Profile> byId(String id) {
        if (id == null || id.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(profiles.get(id.trim()));
    }

    public Optional<Profile> suggestForVendor(String vendorName) {
        if (vendorName == null || vendorName.isBlank()) {
            return Optional.empty();
        }
        String normalized = vendorName.trim();
        return profiles.values().stream()
                .filter(profile -> normalized.equalsIgnoreCase(profile.vendorName()))
                .findFirst();
    }

    private String loadYaml(String path) {
        ClassPathResource resource = new ClassPathResource(path);
        try (InputStream stream = resource.getInputStream()) {
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to load invoice2data template resource: " + path, ex);
        }
    }
}
