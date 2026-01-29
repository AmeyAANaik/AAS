package com.aas.mw.client;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
        name = "erpnext",
        url = "${erpnext.base-url}",
        configuration = ErpNextFeignConfig.class
)
public interface ErpNextFeignClient {

    @PostMapping(value = "/api/method/login", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    ResponseEntity<Void> login(@RequestBody MultiValueMap<String, String> form);

    @GetMapping("/api/resource/{doctype}/{id}")
    Map<String, Object> getResource(@PathVariable("doctype") String doctype, @PathVariable("id") String id);

    @GetMapping("/api/resource/{doctype}/{id}")
    Map<String, Object> getResourceWithCookie(
            @PathVariable("doctype") String doctype,
            @PathVariable("id") String id,
            @RequestHeader("Cookie") String cookie);

    @GetMapping("/api/resource/{doctype}")
    Map<String, Object> listResources(
            @PathVariable("doctype") String doctype,
            @RequestParam Map<String, Object> params);

    @PostMapping("/api/resource/{doctype}")
    Map<String, Object> createResource(
            @PathVariable("doctype") String doctype,
            @RequestBody Map<String, Object> payload);

    @PutMapping("/api/resource/{doctype}/{id}")
    Map<String, Object> updateResource(
            @PathVariable("doctype") String doctype,
            @PathVariable("id") String id,
            @RequestBody Map<String, Object> payload);

    @GetMapping(value = "/api/method/frappe.utils.print_format.download_pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    byte[] downloadPdf(@RequestParam("doctype") String doctype, @RequestParam("name") String name);
}
