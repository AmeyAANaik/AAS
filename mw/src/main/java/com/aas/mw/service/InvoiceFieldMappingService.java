package com.aas.mw.service;

import com.aas.mw.client.InvoiceTemplateGeneratorFeignClient;
import com.aas.mw.config.InvoiceTemplateModelProperties;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import java.net.URI;
import java.util.UUID;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class InvoiceFieldMappingService {

    private static final Logger log = LoggerFactory.getLogger(InvoiceFieldMappingService.class);

    public record FieldMapping(
            String targetField,
            String sourceLabel,
            boolean required,
            boolean present,
            String confidence) {
    }

    public record MappingResult(
            List<FieldMapping> itemMappings,
            List<FieldMapping> summaryMappings,
            String notes,
            String generatorType,
            String generatorModel,
            int totalItemCount) {
    }

    public record RowRules(
            List<String> skipLabels,
            List<String> headerContinuationLabels) {
    }

    public record LayoutRuleResult(
            String primaryItemTableBlockId,
            String gstSummaryBlockId,
            Map<String, String> summaryFieldRoles,
            Map<String, String> parsingHints,
            Map<String, String> fieldParsingRules,
            RowRules rowRules,
            String notes,
            String generatorType,
            String generatorModel) {
    }

    private static final int MAX_PROMPT_CHARS = 16000;

    private final InvoiceTemplateGeneratorFeignClient feignClient;
    private final ObjectMapper objectMapper;
    private final boolean enabled;
    private final String generatorBaseUrl;
    private final String generatorModel;
    private final String generatorType;
    private final String generatorApiKey;
    private final String generatorApiVersion;
    private final boolean debugEnabled;
    private final String openRouterSiteUrl;
    private final String openRouterTitle;

    public InvoiceFieldMappingService(
            InvoiceTemplateGeneratorFeignClient feignClient,
            ObjectMapper objectMapper,
            @Value("${app.invoice-template-generator.enabled:true}") boolean enabled,
            @Value("${app.invoice-template-generator.base-url:}") String generatorBaseUrl,
            @Value("${app.invoice-template-generator.model:}") String generatorModel,
            @Value("${app.invoice-template-generator.type:external_api}") String generatorType,
            @Value("${app.invoice-template-generator.api-key:}") String generatorApiKey,
            @Value("${app.invoice-template-generator.api-version:2023-06-01}") String generatorApiVersion,
            @Value("${app.invoice-template-generator.debug-enabled:false}") boolean debugEnabled,
            @Value("${app.invoice-template-generator.openrouter.site-url:http://localhost:4200}") String openRouterSiteUrl,
            @Value("${app.invoice-template-generator.openrouter.title:AAS Ops Console}") String openRouterTitle) {
        this.feignClient = feignClient;
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.generatorBaseUrl = generatorBaseUrl == null ? "" : generatorBaseUrl.trim();
        this.generatorModel = generatorModel == null ? "" : generatorModel.trim();
        this.generatorType = generatorType == null || generatorType.isBlank() ? "external_api" : generatorType.trim();
        this.generatorApiKey = generatorApiKey == null ? "" : generatorApiKey.trim();
        this.generatorApiVersion = generatorApiVersion == null || generatorApiVersion.isBlank()
                ? "2023-06-01"
                : generatorApiVersion.trim();
        this.debugEnabled = debugEnabled;
        this.openRouterSiteUrl = openRouterSiteUrl == null ? "" : openRouterSiteUrl.trim();
        this.openRouterTitle = openRouterTitle == null ? "" : openRouterTitle.trim();
    }

    public MappingResult detectMappings(
            String vendorId,
            String vendorName,
            String parserText,
            String camelotText,
            List<InvoiceTemplateModelProperties.TemplateField> itemFields,
            List<InvoiceTemplateModelProperties.TemplateField> summaryFields) {
        if (!enabled || generatorBaseUrl.isBlank() || generatorModel.isBlank()) {
            return new MappingResult(List.of(), List.of(), "", generatorType, generatorModel, 0);
        }
        if (parserText == null || parserText.isBlank()) {
            return new MappingResult(List.of(), List.of(), "", generatorType, generatorModel, 0);
        }
        if (requiresApiKey() && generatorApiKey.isBlank()) {
            return new MappingResult(List.of(), List.of(), "", generatorType, generatorModel, 0);
        }
        try {
            String prompt = buildPrompt(vendorId, vendorName, parserText, camelotText, itemFields, summaryFields);
            String traceId = buildTraceId(vendorId);
            logDebugRequest(vendorId, traceId, prompt, parserText, camelotText);
            String rawResponse = request(prompt, vendorId, traceId);
            logDebugRawCompletion(vendorId, traceId, rawResponse);
            Map<String, Object> parsed = parseJsonResponse(rawResponse, vendorId, traceId);
            List<FieldMapping> itemMappings = normalizeMappings(parsed.get("itemMappings"), itemFields);
            List<FieldMapping> summaryMappings = normalizeMappings(parsed.get("summaryMappings"), summaryFields);
            String notes = asText(parsed.get("notes"));
            int totalItemCount = asInt(parsed.get("totalItemCount"));
            return new MappingResult(itemMappings, summaryMappings, notes, generatorType, generatorModel, totalItemCount);
        } catch (Exception ex) {
            return new MappingResult(List.of(), List.of(), "", generatorType, generatorModel, 0);
        }
    }

    public LayoutRuleResult detectLayoutRules(
            String vendorId,
            String vendorName,
            String nativeLayoutPayload) {
        if (!enabled || generatorBaseUrl.isBlank() || generatorModel.isBlank()) {
            return emptyLayoutRules();
        }
        if (nativeLayoutPayload == null || nativeLayoutPayload.isBlank()) {
            return emptyLayoutRules();
        }
        if (requiresApiKey() && generatorApiKey.isBlank()) {
            return emptyLayoutRules();
        }
        try {
            String prompt = buildLayoutRulePrompt(vendorId, vendorName, nativeLayoutPayload);
            String traceId = buildTraceId(vendorId);
            logDebugRequest(vendorId, traceId, prompt, nativeLayoutPayload, "");
            String rawResponse = request(prompt, vendorId, traceId);
            logDebugRawCompletion(vendorId, traceId, rawResponse);
            Map<String, Object> parsed = parseJsonResponse(rawResponse, vendorId, traceId);
            return new LayoutRuleResult(
                    asText(parsed.get("primaryItemTableBlockId")),
                    asText(parsed.get("gstSummaryBlockId")),
                    normalizeStringMap(parsed.get("summaryFieldRoles")),
                    normalizeStringMap(parsed.get("parsingHints")),
                    normalizeFieldParsingRules(parsed.get("fieldParsingRules")),
                    normalizeRowRules(parsed.get("rowRules")),
                    asText(parsed.get("notes")),
                    generatorType,
                    generatorModel);
        } catch (Exception ex) {
            return emptyLayoutRules();
        }
    }

    private String buildPrompt(
            String vendorId,
            String vendorName,
            String parserText,
            String camelotText,
            List<InvoiceTemplateModelProperties.TemplateField> itemFields,
            List<InvoiceTemplateModelProperties.TemplateField> summaryFields) {
        String trimmedParserText = parserText.length() > MAX_PROMPT_CHARS ? parserText.substring(0, MAX_PROMPT_CHARS) : parserText;
        String trimmedCamelotText = camelotText == null ? "" : camelotText.trim();
        if (trimmedCamelotText.length() > 6000) {
            trimmedCamelotText = trimmedCamelotText.substring(0, 6000);
        }
        return """
                You map native invoice layout fields to a fixed business schema.

                Return exactly one valid JSON object. No markdown. No explanation.

                Top-level keys:
                - itemMappings
                - summaryMappings
                - notes
                - totalItemCount

                Each mapping object must contain exactly:
                - targetField
                - sourceLabel
                - present
                - confidence

                Confidence must be one of:
                - high
                - medium
                - low

                Mapping rules:
                - use only labels or headers visible in the provided native-layout payload
                - do not invent columns or labels
                - do not extract row values
                - map meanings only
                - if a field is not clearly present, return sourceLabel as "" and present as false
                - use line-item amount/value columns for total, not invoice-level totals
                - use tax percent columns for gst
                - use HSN/SAC/item code style columns for item_id
                - transport_charge is present only if the invoice explicitly shows a transport, freight, cartage, delivery, or shipping charge amount
                - totalItemCount: count every distinct item/product row visible in the full layout (integer); exclude header rows, total rows, tax rows, blank lines, and continuation lines; use totalTableRows from the payload as a cross-check

                Native layout payload:
                %s

                Additional table hint rows:
                %s
                """.formatted(
                trimmedParserText,
                trimmedCamelotText.isBlank() ? "(none)" : trimmedCamelotText);
    }

    private String buildLayoutRulePrompt(String vendorId, String vendorName, String nativeLayoutPayload) {
        String trimmedPayload = nativeLayoutPayload.length() > MAX_PROMPT_CHARS
                ? nativeLayoutPayload.substring(0, MAX_PROMPT_CHARS)
                : nativeLayoutPayload;
        return """
                You classify invoice layout blocks and parsing rules for deterministic extraction.

                Return exactly one valid JSON object. No markdown. No explanation.

                Top-level keys:
                - primaryItemTableBlockId
                - gstSummaryBlockId
                - summaryFieldRoles
                - parsingHints
                - rowRules
                - fieldParsingRules
                - notes

                Rules:
                - choose one visible block id for the primary item table
                - choose one visible block id for the gst/tax summary block
                - summaryFieldRoles must contain exactly:
                  - final_bill_amount
                  - transport_charge
                - parsingHints must contain:
                  - preferredRateColumn
                  - amountLabel
                  - taxLabel
                  - totalSourceLabel
                - rowRules must contain:
                  - skipLabels
                  - headerContinuationLabels
                - fieldParsingRules must contain at least:
                  - gst
                  - qty
                  - rate
                  - total
                - each fieldParsingRules value must be one of:
                  - percentage
                  - number_with_uom
                  - decimal_amount
                  - integer
                  - text
                - include only labels that are visible in the payload
                - skipLabels should include non-item labels such as totals, taxes, transport, round off, or bill amount rows when visible
                - headerContinuationLabels should include wrapped header labels that are not item data rows
                - if transport is not explicit, return an empty string
                - do not invent block ids or labels

                Native deterministic-layout payload:
                %s
                """.formatted(trimmedPayload);
    }

    private String describeFields(List<InvoiceTemplateModelProperties.TemplateField> fields) {
        if (fields == null || fields.isEmpty()) {
            return "(none)";
        }
        return fields.stream()
                .map(field -> field.getKey() + " (required=" + field.isRequired() + ", aliases="
                        + String.join("/", field.getSourceAliases()) + ")")
                .collect(Collectors.joining(", "));
    }

    private LayoutRuleResult emptyLayoutRules() {
        return new LayoutRuleResult(
                "",
                "",
                Map.of(),
                Map.of(),
                Map.of(),
                new RowRules(List.of(), List.of()),
                "",
                generatorType,
                generatorModel);
    }

    private String request(String prompt, String vendorId, String traceId) {
        try {
            String normalizedType = generatorType.toLowerCase(Locale.ROOT);
            if ("anthropic".equals(normalizedType) || "claude".equals(normalizedType)) {
                return requestAnthropicMessages(prompt, vendorId, traceId);
            }
            return requestOpenAiCompatible(prompt, vendorId, traceId);
        } catch (FeignException ex) {
            throw new IllegalStateException("Unable to reach the invoice field mapping generator.", ex);
        }
    }

    private String requestAnthropicMessages(String prompt, String vendorId, String traceId) {
        Map<String, String> headers = buildBaseHeaders();
        headers.put("x-api-key", generatorApiKey);
        headers.put("anthropic-version", generatorApiVersion);

        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", generatorModel);
        request.put("max_tokens", 1200);
        request.put("temperature", 0.1);
        request.put("system", "You return strict JSON for invoice field mapping. Return only valid JSON.");
        request.put("messages", List.of(Map.of("role", "user", "content", prompt)));
        request.put("user", buildUserTag(vendorId));

        Map<String, Object> body = feignClient.createAnthropicMessage(
                URI.create(buildAnthropicMessagesUrl(generatorBaseUrl)),
                headers,
                request);
        logDebugResponse("anthropic", vendorId, traceId, body);
        return extractAnthropicContent(body);
    }

    private String requestOpenAiCompatible(String prompt, String vendorId, String traceId) {
        Map<String, String> headers = buildBaseHeaders();
        if (!generatorApiKey.isBlank()) {
            headers.put("Authorization", "Bearer " + generatorApiKey);
        }
        if (isOpenRouterRequest()) {
            if (!openRouterSiteUrl.isBlank()) {
                headers.put("HTTP-Referer", openRouterSiteUrl);
            }
            if (!openRouterTitle.isBlank()) {
                headers.put("X-Title", openRouterTitle);
            }
        }
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("model", generatorModel);
        request.put("temperature", 0.1);
        request.put("response_format", Map.of("type", "json_object"));
        request.put("user", buildUserTag(vendorId));
        request.put("messages", List.of(
                Map.of("role", "system", "content", "You return strict JSON for invoice field mapping. Return only valid JSON."),
                Map.of("role", "user", "content", prompt)));
        if (isOpenRouterRequest()) {
            request.put("transforms", List.of());
        }

        Map<String, Object> body = feignClient.createChatCompletion(
                URI.create(buildChatCompletionsUrl(generatorBaseUrl)),
                headers,
                request);
        logDebugResponse("openai-compatible", vendorId, traceId, body);
        return extractChatCompletionContent(body);
    }

    private List<FieldMapping> normalizeMappings(Object rawMappings, List<InvoiceTemplateModelProperties.TemplateField> definitions) {
        Map<String, InvoiceTemplateModelProperties.TemplateField> byKey = (definitions == null ? List.<InvoiceTemplateModelProperties.TemplateField>of() : definitions)
                .stream()
                .collect(Collectors.toMap(InvoiceTemplateModelProperties.TemplateField::getKey, field -> field));
        List<FieldMapping> normalized = new ArrayList<>();
        Set<String> seen = new java.util.LinkedHashSet<>();
        if (rawMappings instanceof List<?> mappings) {
            for (Object raw : mappings) {
                if (!(raw instanceof Map<?, ?> map)) {
                    continue;
                }
                String targetField = asText(map.get("targetField"));
                if (targetField.isBlank() || !byKey.containsKey(targetField) || !seen.add(targetField)) {
                    continue;
                }
                InvoiceTemplateModelProperties.TemplateField definition = byKey.get(targetField);
                normalized.add(new FieldMapping(
                        targetField,
                        asText(map.get("sourceLabel")),
                        definition.isRequired(),
                        readBoolean(map.get("present")),
                        normalizeConfidence(asText(map.get("confidence")))));
            }
        }
        for (InvoiceTemplateModelProperties.TemplateField definition : byKey.values()) {
            if (seen.contains(definition.getKey())) {
                continue;
            }
            normalized.add(new FieldMapping(definition.getKey(), "", definition.isRequired(), false, "low"));
        }
        return normalized;
    }

    private String normalizeConfidence(String value) {
        return switch (value == null ? "" : value.trim().toLowerCase(Locale.ROOT)) {
            case "high", "medium", "low" -> value.trim().toLowerCase(Locale.ROOT);
            default -> "low";
        };
    }

    private boolean readBoolean(Object value) {
        if (value instanceof Boolean flag) {
            return flag;
        }
        if (value instanceof String text) {
            return "true".equalsIgnoreCase(text.trim());
        }
        return false;
    }

    private Map<String, String> normalizeStringMap(Object raw) {
        if (!(raw instanceof Map<?, ?> map) || map.isEmpty()) {
            return Map.of();
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            String key = asText(entry.getKey());
            if (key.isBlank()) {
                continue;
            }
            normalized.put(key, asText(entry.getValue()));
        }
        return normalized;
    }

    private Map<String, String> normalizeFieldParsingRules(Object raw) {
        Map<String, String> normalized = new LinkedHashMap<>(normalizeStringMap(raw));
        normalized.replaceAll((key, value) -> switch (blankSafe(value).toLowerCase(Locale.ROOT)) {
            case "percentage", "number_with_uom", "decimal_amount", "integer", "text" -> blankSafe(value);
            default -> "text";
        });
        return normalized;
    }

    private RowRules normalizeRowRules(Object raw) {
        if (!(raw instanceof Map<?, ?> map) || map.isEmpty()) {
            return new RowRules(List.of(), List.of());
        }
        return new RowRules(
                normalizeStringList(map.get("skipLabels")),
                normalizeStringList(map.get("headerContinuationLabels")));
    }

    private List<String> normalizeStringList(Object raw) {
        if (!(raw instanceof List<?> list) || list.isEmpty()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (Object entry : list) {
            String value = asText(entry);
            if (!value.isBlank()) {
                values.add(value);
            }
        }
        return values;
    }

    private Map<String, Object> parseJsonResponse(String rawResponse, String vendorId, String traceId) {
        try {
            return objectMapper.readValue(rawResponse, new TypeReference<Map<String, Object>>() {});
        } catch (Exception ex) {
            log.warn(
                    "Invoice field mapping JSON parse failed vendorId={} traceId={} generatorType={} model={} rawResponse={}",
                    blankSafe(vendorId),
                    traceId,
                    generatorType,
                    generatorModel,
                    summarize(rawResponse),
                    ex);
            throw new IllegalStateException("Invoice field mapping generation returned invalid JSON.", ex);
        }
    }

    private Map<String, String> buildBaseHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", "application/json");
        return headers;
    }

    private boolean isOpenRouterRequest() {
        return generatorBaseUrl.toLowerCase(Locale.ROOT).contains("openrouter.ai");
    }

    private String buildUserTag(String vendorId) {
        String normalized = blankSafe(vendorId).replaceAll("[^a-zA-Z0-9_-]+", "_");
        return normalized.isBlank() ? "aas_vendor_mapping" : "aas_vendor_mapping_" + normalized;
    }

    private String buildTraceId(String vendorId) {
        String normalized = blankSafe(vendorId).replaceAll("[^a-zA-Z0-9_-]+", "_");
        if (normalized.isBlank()) {
            normalized = "vendor";
        }
        return normalized + "_" + UUID.randomUUID().toString().substring(0, 8);
    }

    private void logDebugResponse(String apiType, String vendorId, String traceId, Map<String, Object> body) {
        if (!debugEnabled) {
            return;
        }
        log.info(
                "Invoice field mapping response vendorId={} traceId={} apiType={} generatorType={} model={} responseId={} body={}",
                blankSafe(vendorId),
                traceId,
                apiType,
                generatorType,
                generatorModel,
                asText(body == null ? null : body.get("id")),
                summarize(body));
    }

    private void logDebugRequest(String vendorId, String traceId, String prompt, String parserText, String camelotText) {
        if (!debugEnabled) {
            return;
        }
        log.info(
                "Invoice field mapping request vendorId={} traceId={} generatorType={} model={} prompt={} parserText={} camelotText={}",
                blankSafe(vendorId),
                traceId,
                generatorType,
                generatorModel,
                summarize(prompt),
                summarize(parserText),
                summarize(camelotText));
    }

    private void logDebugRawCompletion(String vendorId, String traceId, String rawResponse) {
        if (!debugEnabled) {
            return;
        }
        log.info(
                "Invoice field mapping completion vendorId={} traceId={} generatorType={} model={} rawResponse={}",
                blankSafe(vendorId),
                traceId,
                generatorType,
                generatorModel,
                summarize(rawResponse));
    }

    private String summarize(Object value) {
        if (value == null) {
            return "";
        }
        String text;
        try {
            text = value instanceof String s ? s : objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            text = String.valueOf(value);
        }
        text = text.replaceAll("\\s+", " ").trim();
        return text.length() > 2000 ? text.substring(0, 2000) + "..." : text;
    }

    private String blankSafe(String value) {
        return value == null ? "" : value.trim();
    }

    private boolean requiresApiKey() {
        String normalizedType = generatorType.toLowerCase(Locale.ROOT);
        return "anthropic".equals(normalizedType)
                || "claude".equals(normalizedType)
                || "external_api".equals(normalizedType)
                || "openai".equals(normalizedType)
                || "openai_compatible".equals(normalizedType);
    }

    private String buildChatCompletionsUrl(String baseUrl) {
        String normalized = normalizeBaseUrl(baseUrl);
        return normalized.endsWith("/chat/completions")
                ? normalized
                : normalized + "/chat/completions";
    }

    private String buildAnthropicMessagesUrl(String baseUrl) {
        String normalized = normalizeBaseUrl(baseUrl);
        return normalized.endsWith("/v1/messages")
                ? normalized
                : normalized + "/v1/messages";
    }

    private String normalizeBaseUrl(String baseUrl) {
        String normalized = baseUrl == null ? "" : baseUrl.trim();
        if (normalized.endsWith("/")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        if (normalized.endsWith("/v1")) {
            return normalized;
        }
        return normalized;
    }

    private String extractChatCompletionContent(Map<String, Object> body) {
        Object choicesObj = body.get("choices");
        if (!(choicesObj instanceof List<?> choices) || choices.isEmpty()) {
            return "";
        }
        Object firstChoice = choices.getFirst();
        if (!(firstChoice instanceof Map<?, ?> choiceMap)) {
            return "";
        }
        Object messageObj = choiceMap.get("message");
        if (!(messageObj instanceof Map<?, ?> messageMap)) {
            return "";
        }
        Object contentObj = messageMap.get("content");
        return contentObj instanceof String text ? text.trim() : "";
    }

    private String extractAnthropicContent(Map<String, Object> body) {
        Object contentObj = body.get("content");
        if (!(contentObj instanceof List<?> contentParts) || contentParts.isEmpty()) {
            return "";
        }
        StringBuilder combined = new StringBuilder();
        for (Object partObj : contentParts) {
            if (!(partObj instanceof Map<?, ?> partMap)) {
                continue;
            }
            Object textObj = partMap.get("text");
            if (textObj == null) {
                continue;
            }
            if (!combined.isEmpty()) {
                combined.append('\n');
            }
            combined.append(String.valueOf(textObj).trim());
        }
        return combined.toString().trim();
    }

    private String asText(Object value) {
        return value == null ? "" : String.valueOf(value).trim();
    }

    private int asInt(Object value) {
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(String.valueOf(value).trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }
}
