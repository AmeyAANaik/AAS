package com.aas.mw.config;

import feign.Request;
import feign.Response;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void mapsMissingReportToFriendlyMessage() {
        String body = """
                {"exception":"frappe.exceptions.DoesNotExistError: Report \\"AAS - Branch Sales & Profit\\" not found","exc_type":"DoesNotExistError"}
                """;
        Request request = Request.create(
                Request.HttpMethod.GET,
                "http://localhost:8080/api/method/frappe.desk.query_report.run",
                Map.of(),
                null,
                StandardCharsets.UTF_8,
                null);
        Response response = Response.builder()
                .status(404)
                .request(request)
                .headers(Map.of())
                .body(body, StandardCharsets.UTF_8)
                .build();

        ResponseEntity<Map<String, Object>> entity = handler.handleFeign(feign.FeignException.errorStatus("runReport", response));

        assertEquals(HttpStatus.NOT_FOUND, entity.getStatusCode());
        assertEquals("request_failed", entity.getBody().get("error"));
        assertTrue(String.valueOf(entity.getBody().get("message")).contains("not found in the system"));
        assertTrue(String.valueOf(entity.getBody().get("message")).contains("AAS - Branch Sales & Profit"));
    }

    @Test
    void extractsServerMessagesAsDetails() {
        String body = """
                {
                  "_server_messages": "[\\"{\\\\\\"message\\\\\\":\\\\\\"Not permitted\\\\\\",\\\\\\"title\\\\\\":\\\\\\"PermissionError\\\\\\"}\\"]",
                  "exc_type": "PermissionError"
                }
                """;
        Request request = Request.create(
                Request.HttpMethod.GET,
                "http://localhost:8080/api/method/frappe.desk.query_report.run",
                Map.of(),
                null,
                StandardCharsets.UTF_8,
                null);
        Response response = Response.builder()
                .status(403)
                .request(request)
                .headers(Map.of())
                .body(body, StandardCharsets.UTF_8)
                .build();

        ResponseEntity<Map<String, Object>> entity = handler.handleFeign(feign.FeignException.errorStatus("runReport", response));

        assertEquals(HttpStatus.FORBIDDEN, entity.getStatusCode());
        assertEquals("You don’t have permission to run this report.", entity.getBody().get("message"));
        Object details = entity.getBody().get("details");
        assertTrue(details instanceof List<?>);
    }

    @Test
    void mapsReportNotFoundInServerMessagesToFriendlyMessage() {
        String body = """
                {
                  "_server_messages": "[\\"{\\\\\\"message\\\\\\":\\\\\\"Report AAS - Sales Profit Summary not found\\\\\\"}\\"]"
                }
                """;
        Request request = Request.create(
                Request.HttpMethod.GET,
                "http://localhost:8080/api/method/frappe.desk.query_report.run",
                Map.of(),
                null,
                StandardCharsets.UTF_8,
                null);
        Response response = Response.builder()
                .status(404)
                .request(request)
                .headers(Map.of())
                .body(body, StandardCharsets.UTF_8)
                .build();

        ResponseEntity<Map<String, Object>> entity = handler.handleFeign(feign.FeignException.errorStatus("runReport", response));

        assertEquals(HttpStatus.NOT_FOUND, entity.getStatusCode());
        assertTrue(String.valueOf(entity.getBody().get("message")).contains("not found in the system"));
        assertTrue(String.valueOf(entity.getBody().get("message")).contains("AAS - Sales Profit Summary"));
    }
}
