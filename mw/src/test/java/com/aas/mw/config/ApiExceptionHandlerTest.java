package com.aas.mw.config;

import feign.Request;
import feign.Response;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ApiExceptionHandlerTest {

    private final ApiExceptionHandler handler = new ApiExceptionHandler();

    @Test
    void mapsErpFieldNotPermittedErrorsToFriendlySetupMessage() {
        String body = """
                {"exception":"frappe.exceptions.DataError: Field not permitted in query: aas_category","exc_type":"DataError"}
                """;
        Request request = Request.create(
                Request.HttpMethod.GET,
                "http://localhost:8080/api/resource/Sales Order",
                Map.of(),
                null,
                StandardCharsets.UTF_8,
                null);
        Response response = Response.builder()
                .status(400)
                .request(request)
                .headers(Map.of())
                .body(body, StandardCharsets.UTF_8)
                .build();

        ResponseEntity<Map<String, Object>> entity = handler.handleFeign(feign.FeignException.errorStatus("listOrders", response));

        assertEquals(HttpStatus.BAD_REQUEST, entity.getStatusCode());
        assertEquals("request_failed", entity.getBody().get("error"));
        assertEquals(
                "System setup is incomplete. Please run setup again and refresh the page.",
                entity.getBody().get("message"));
    }
}
