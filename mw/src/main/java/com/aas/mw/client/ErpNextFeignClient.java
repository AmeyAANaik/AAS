package com.aas.mw.client;

import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;

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

    @GetMapping("/api/resource/{doctype}")
    Map<String, Object> listResourcesWithCookie(
            @PathVariable("doctype") String doctype,
            @RequestParam Map<String, Object> params,
            @RequestHeader("Cookie") String cookie);

    @PostMapping("/api/resource/{doctype}")
    Map<String, Object> createResource(
            @PathVariable("doctype") String doctype,
            @RequestBody Map<String, Object> payload);

    @PutMapping("/api/resource/{doctype}/{id}")
    Map<String, Object> updateResource(
            @PathVariable("doctype") String doctype,
            @PathVariable("id") String id,
            @RequestBody Map<String, Object> payload);

    @DeleteMapping("/api/resource/{doctype}/{id}")
    Map<String, Object> deleteResource(
            @PathVariable("doctype") String doctype,
            @PathVariable("id") String id);

    @GetMapping(value = "/api/method/frappe.utils.print_format.download_pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    byte[] downloadPdf(@RequestParam("doctype") String doctype, @RequestParam("name") String name);

    @GetMapping(value = "/api/method/frappe.utils.print_format.download_pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    byte[] downloadPdfWithParams(@RequestParam Map<String, Object> params);

    @GetMapping(value = "/api/method/frappe.utils.print_format.download_pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    byte[] downloadPdfWithParamsAndCookie(
            @RequestParam Map<String, Object> params,
            @RequestHeader("Cookie") String cookie);

    @GetMapping("/api/method/frappe.desk.reportview.get_count")
    Map<String, Object> getCount(@RequestParam Map<String, Object> params);

    @PostMapping("/api/method/frappe.client.submit")
    Map<String, Object> submit(@RequestBody Map<String, Object> payload);

    @PostMapping("/api/method/frappe.client.cancel")
    Map<String, Object> cancel(@RequestBody Map<String, Object> payload);

    @PostMapping("/api/method/{method}")
    Map<String, Object> postMethod(
            @PathVariable("method") String method,
            @RequestBody Map<String, Object> payload);

    @GetMapping("/api/method/{method}")
    Map<String, Object> getMethod(
            @PathVariable("method") String method,
            @RequestParam Map<String, Object> params);

    @RequestMapping(method = RequestMethod.POST, value = "/")
    Map<String, Object> postCommand(@RequestBody MultiValueMap<String, String> form);

}
