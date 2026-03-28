package com.aas.mw.client;

import feign.Logger;
import feign.Request;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

public class InvoiceTemplateGeneratorFeignConfig {

    @Bean
    public Request.Options invoiceTemplateGeneratorFeignOptions(
            @Value("${app.invoice-template-generator.connect-timeout-ms:10000}") long connectTimeoutMs,
            @Value("${app.invoice-template-generator.read-timeout-ms:180000}") long readTimeoutMs) {
        return new Request.Options(
                Duration.ofMillis(Math.max(connectTimeoutMs, 1000)),
                Duration.ofMillis(Math.max(readTimeoutMs, 1000)),
                true);
    }

    @Bean
    Logger.Level invoiceTemplateGeneratorFeignLoggerLevel(
            @Value("${app.invoice-template-generator.feign-log-level:BASIC}") String configuredLevel) {
        try {
            return Logger.Level.valueOf(configuredLevel == null ? "BASIC" : configuredLevel.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return Logger.Level.BASIC;
        }
    }
}
