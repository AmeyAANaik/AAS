package com.aas.mw.controller;

import com.aas.mw.dto.OrderRequest;
import com.aas.mw.dto.FieldsRequest;
import com.aas.mw.service.OrderService;
import com.aas.mw.service.UserService;
import com.aas.mw.util.CsvUtil;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.List;
import java.util.HashMap;
import java.util.Optional;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.multipart.MultipartFile;
import jakarta.servlet.http.HttpServletRequest;
import com.aas.mw.service.ErpSessionStore;
import org.springframework.http.HttpStatus;

@RestController
@RequestMapping("/api/orders")
public class OrdersController {

    private final OrderService orderService;
    private final UserService userService;

    public OrdersController(OrderService orderService, UserService userService) {
        this.orderService = orderService;
        this.userService = userService;
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> createOrder(@Valid @RequestBody OrderRequest request) {
        return ResponseEntity.ok(orderService.createOrder(request));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> getOrder(@PathVariable String id) {
        return ResponseEntity.ok(orderService.getOrder(id));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> updateOrder(
            @PathVariable String id,
            @Valid @RequestBody OrderRequest request) {
        return ResponseEntity.ok(orderService.updateOrder(id, request));
    }

    @GetMapping
    public ResponseEntity<List<Map<String, Object>>> listOrders(
            @RequestParam(required = false) String customer,
            @RequestParam(required = false) String vendor,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, name = "from") String fromDate,
            @RequestParam(required = false, name = "to") String toDate) {
        return ResponseEntity.ok(buildOrders(customer, vendor, status, fromDate, toDate));
    }

    @GetMapping("/export")
    public ResponseEntity<String> exportOrders(
            @RequestParam(required = false) String customer,
            @RequestParam(required = false) String vendor,
            @RequestParam(required = false) String status,
            @RequestParam(required = false, name = "from") String fromDate,
            @RequestParam(required = false, name = "to") String toDate) {
        List<Map<String, Object>> orders = buildOrders(customer, vendor, status, fromDate, toDate);
        String csv = CsvUtil.toCsv(orders);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"orders.csv\"")
                .contentType(MediaType.valueOf("text/csv"))
                .body(csv);
    }

    private List<Map<String, Object>> buildOrders(
            String customer,
            String vendor,
            String status,
            String fromDate,
            String toDate) {
        Map<String, String> filters = new HashMap<>();
        String role = resolveRole();
        if ("vendor".equals(role) && (vendor == null || vendor.isBlank())) {
            String supplier = resolveSupplier();
            if (supplier != null && !supplier.isBlank()) {
                vendor = supplier;
            }
        }
        if ("shop".equals(role) && (customer == null || customer.isBlank())) {
            String shop = resolveCustomer();
            if (shop != null && !shop.isBlank()) {
                customer = shop;
            }
        }
        if (customer != null && !customer.isBlank()) {
            filters.put("customer", customer);
        }
        if (vendor != null && !vendor.isBlank()) {
            filters.put("aas_vendor", vendor);
        }
        if (status != null && !status.isBlank()) {
            filters.put("aas_status", status);
        }
        if (fromDate != null && !fromDate.isBlank()) {
            filters.put("from", fromDate);
        }
        if (toDate != null && !toDate.isBlank()) {
            filters.put("to", toDate);
        }
        return orderService.listOrders(filters);
    }

    private String resolveRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null) {
            return "admin";
        }
        Optional<String> role = auth.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> a.substring("ROLE_".length()).toLowerCase())
                .findFirst();
        return role.orElse("admin");
    }

    private String resolveSupplier() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth == null ? null : auth.getName();
        return String.valueOf(userService.getUserProfile(username).getOrDefault("supplier", ""));
    }

    private String resolveCustomer() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth == null ? null : auth.getName();
        return String.valueOf(userService.getUserProfile(username).getOrDefault("customer", ""));
    }

    @PostMapping("/{id}/status")
    public ResponseEntity<Map<String, Object>> updateStatus(
            @PathVariable String id,
            @Valid @RequestBody FieldsRequest request) {
        return ResponseEntity.ok(orderService.updateOrderFields(id, request.getFields()));
    }

    @PostMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadOrderImage(
            @PathVariable String id,
            @RequestPart("file") MultipartFile file,
            HttpServletRequest request) {
        Object session = request.getAttribute(ErpSessionStore.REQUEST_ATTR);
        if (!(session instanceof String sessionCookie) || sessionCookie.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "ERPNext session not found."));
        }
        if (file == null || file.isEmpty()) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Image file is required."));
        }
        return ResponseEntity.ok(orderService.attachOrderImage(id, file, sessionCookie));
    }
}
