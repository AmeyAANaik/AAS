package com.aas.mw.client;

import java.net.URI;
import java.util.Map;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
        name = "invoiceTemplateGenerator",
        url = "http://placeholder.invalid",
        configuration = InvoiceTemplateGeneratorFeignConfig.class
)
public interface InvoiceTemplateGeneratorFeignClient {

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    Map<String, Object> createAnthropicMessage(
            URI uri,
            @RequestHeader Map<String, String> headers,
            @RequestBody Map<String, Object> payload);

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    Map<String, Object> createChatCompletion(
            URI uri,
            @RequestHeader Map<String, String> headers,
            @RequestBody Map<String, Object> payload);

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    Map<String, Object> generateLegacy(
            URI uri,
            @RequestHeader Map<String, String> headers,
            @RequestBody Map<String, Object> payload);
}
