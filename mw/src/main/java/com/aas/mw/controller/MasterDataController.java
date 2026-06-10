package com.aas.mw.controller;

import com.aas.mw.service.MasterDataService;
import com.aas.mw.service.UomService;
import com.aas.mw.service.UserService;
import com.aas.mw.service.ErpSessionStore;
import com.aas.mw.service.AuthenticationService;
import com.aas.mw.dto.FieldsRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.aas.mw.service.ItemMarginImportService;

@RestController
@RequestMapping("/api")
public class MasterDataController {

    private final MasterDataService masterDataService;
    private final UomService uomService;
    private final UserService userService;
    private final ItemMarginImportService itemMarginImportService;
    private final AuthenticationService authenticationService;

    public MasterDataController(
            MasterDataService masterDataService,
            UomService uomService,
            UserService userService,
            ItemMarginImportService itemMarginImportService,
            AuthenticationService authenticationService) {
        this.masterDataService = masterDataService;
        this.uomService = uomService;
        this.userService = userService;
        this.itemMarginImportService = itemMarginImportService;
        this.authenticationService = authenticationService;
    }

    @GetMapping("/items")
    public ResponseEntity<List<Map<String, Object>>> listItems(HttpServletRequest request) {
        usePrivilegedErpSession(request);
        return ResponseEntity.ok(masterDataService.listItems());
    }

    @GetMapping("/items/paged")
    public ResponseEntity<Map<String, Object>> listItemsPaged(
            @RequestParam(value = "page", required = false, defaultValue = "1") int page,
            @RequestParam(value = "size", required = false, defaultValue = "25") int size,
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "dir", required = false) String dir,
            HttpServletRequest request) {
        usePrivilegedErpSession(request);
        return ResponseEntity.ok(masterDataService.listItemsPaged(page, size, search, sort, dir));
    }

    @GetMapping("/vendors")
    public ResponseEntity<List<Map<String, Object>>> listVendors(HttpServletRequest request) {
        usePrivilegedErpSession(request);
        return ResponseEntity.ok(masterDataService.listVendors());
    }

    @PostMapping("/vendors")
    public ResponseEntity<Map<String, Object>> createVendor(
            @Valid @RequestBody FieldsRequest request,
            HttpServletRequest httpRequest) {
        usePrivilegedErpSession(httpRequest);
        return ResponseEntity.ok(masterDataService.createVendor(request));
    }

    @PutMapping("/vendors/{id}")
    public ResponseEntity<Map<String, Object>> updateVendor(
            @PathVariable String id,
            @Valid @RequestBody FieldsRequest request,
            HttpServletRequest httpRequest) {
        usePrivilegedErpSession(httpRequest);
        return ResponseEntity.ok(masterDataService.updateVendor(id, request));
    }

    @DeleteMapping("/vendors/{id}")
    public ResponseEntity<Map<String, Object>> deleteVendor(@PathVariable String id, HttpServletRequest request) {
        usePrivilegedErpSession(request);
        return ResponseEntity.ok(masterDataService.deleteVendor(id));
    }

    @GetMapping("/categories")
    public ResponseEntity<List<Map<String, Object>>> listCategories(HttpServletRequest request) {
        usePrivilegedErpSession(request);
        return ResponseEntity.ok(masterDataService.listCategories());
    }

    @PostMapping("/categories")
    public ResponseEntity<Map<String, Object>> createCategory(
            @Valid @RequestBody FieldsRequest request,
            HttpServletRequest httpRequest) {
        usePrivilegedErpSession(httpRequest);
        return ResponseEntity.ok(masterDataService.createCategory(request));
    }

    @PutMapping("/categories/{id}")
    public ResponseEntity<Map<String, Object>> updateCategory(
            @PathVariable String id,
            @Valid @RequestBody FieldsRequest request,
            HttpServletRequest httpRequest) {
        usePrivilegedErpSession(httpRequest);
        return ResponseEntity.ok(masterDataService.updateCategory(id, request));
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<Map<String, Object>> deleteCategory(@PathVariable String id, HttpServletRequest request) {
        usePrivilegedErpSession(request);
        return ResponseEntity.ok(masterDataService.deleteCategory(id));
    }

    @GetMapping("/shops")
    public ResponseEntity<List<Map<String, Object>>> listShops(HttpServletRequest request) {
        usePrivilegedErpSession(request);
        return ResponseEntity.ok(masterDataService.listShops());
    }

    @GetMapping("/uoms")
    public ResponseEntity<List<Map<String, Object>>> listUoms(
            @RequestParam(value = "refresh", required = false, defaultValue = "0") int refresh,
            HttpServletRequest request) {
        usePrivilegedErpSession(request);
        return ResponseEntity.ok(uomService.listUoms(refresh == 1));
    }

    @GetMapping("/companies")
    public ResponseEntity<List<Map<String, Object>>> listCompanies() {
        return ResponseEntity.ok(masterDataService.listCompanies());
    }

    @GetMapping("/company-context")
    public ResponseEntity<Map<String, Object>> companyContext() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        String username = auth == null ? null : auth.getName();
        Map<String, Object> profile = userService.getUserProfile(username);
        return ResponseEntity.ok(masterDataService.getCompanyContext(
                String.valueOf(profile.getOrDefault("company", "")),
                String.valueOf(profile.getOrDefault("customer", ""))));
    }

    @GetMapping("/companies/{id}")
    public ResponseEntity<Map<String, Object>> getCompany(@PathVariable String id) {
        return ResponseEntity.ok(masterDataService.getCompanyProfile(id));
    }

    @PutMapping("/companies/{id}")
    public ResponseEntity<Map<String, Object>> updateCompany(
            @PathVariable String id,
            @Valid @RequestBody FieldsRequest request,
            HttpServletRequest httpRequest) {
        ensureErpSessionCookie(httpRequest);
        return ResponseEntity.ok(masterDataService.updateCompanyProfile(id, request));
    }

    @PostMapping(value = "/companies/{id}/logo", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadCompanyLogo(
            @PathVariable String id,
            @RequestPart("file") MultipartFile file,
            HttpServletRequest request) {
        String sessionCookie = ensureErpSessionCookie(request);
        return ResponseEntity.ok(masterDataService.uploadCompanyLogo(id, file, sessionCookie));
    }

    @PostMapping(value = "/companies/{id}/signature", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> uploadCompanySignature(
            @PathVariable String id,
            @RequestPart("file") MultipartFile file,
            HttpServletRequest request) {
        String sessionCookie = ensureErpSessionCookie(request);
        return ResponseEntity.ok(masterDataService.uploadCompanySignature(id, file, sessionCookie));
    }

    private String ensureErpSessionCookie(HttpServletRequest request) {
        Object session = request.getAttribute(ErpSessionStore.REQUEST_ATTR);
        if (session instanceof String sessionCookie && !sessionCookie.isBlank()) {
            return sessionCookie;
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        boolean isAdmin = auth != null && auth.getAuthorities() != null
                && auth.getAuthorities().stream()
                        .map(GrantedAuthority::getAuthority)
                        .anyMatch("ROLE_ADMIN"::equals);
        if (!isAdmin) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Session expired. Please log out and log in again.");
        }
        String setupCookie = authenticationService.getSetupSessionCookie();
        request.setAttribute(ErpSessionStore.REQUEST_ATTR, setupCookie);
        return setupCookie;
    }

    @GetMapping("/users/{id}")
    public ResponseEntity<Map<String, Object>> getUser(@PathVariable String id) {
        return ResponseEntity.ok(userService.getUserProfileById(id));
    }

    @PutMapping("/users/{id}")
    public ResponseEntity<Map<String, Object>> updateUser(
            @PathVariable String id,
            @Valid @RequestBody FieldsRequest request) {
        return ResponseEntity.ok(userService.updateUserProfile(id, request));
    }

    @PostMapping("/shops")
    public ResponseEntity<Map<String, Object>> createShop(
            @Valid @RequestBody FieldsRequest request,
            HttpServletRequest httpRequest) {
        usePrivilegedErpSession(httpRequest);
        return ResponseEntity.ok(masterDataService.createShop(request));
    }

    @PutMapping("/shops/{id}")
    public ResponseEntity<Map<String, Object>> updateShop(
            @PathVariable String id,
            @Valid @RequestBody FieldsRequest request,
            HttpServletRequest httpRequest) {
        usePrivilegedErpSession(httpRequest);
        return ResponseEntity.ok(masterDataService.updateShop(id, request));
    }

    @DeleteMapping("/shops/{id}")
    public ResponseEntity<Map<String, Object>> deleteShop(@PathVariable String id, HttpServletRequest request) {
        usePrivilegedErpSession(request);
        return ResponseEntity.ok(masterDataService.deleteShop(id));
    }

    @PostMapping("/items")
    public ResponseEntity<Map<String, Object>> createItem(
            @Valid @RequestBody FieldsRequest request,
            HttpServletRequest httpRequest) {
        usePrivilegedErpSession(httpRequest);
        return ResponseEntity.ok(masterDataService.createItem(request));
    }

    @PostMapping(value = "/items/import-margins", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, Object>> importItemMargins(
            @RequestPart("file") MultipartFile file,
            @RequestPart("categoryId") String categoryId,
            HttpServletRequest request) {
        usePrivilegedErpSession(request);
        return ResponseEntity.ok(itemMarginImportService.importMargins(file, categoryId));
    }

    @PutMapping("/items/{id}")
    public ResponseEntity<Map<String, Object>> updateItem(
            @PathVariable String id,
            @Valid @RequestBody FieldsRequest request,
            HttpServletRequest httpRequest) {
        usePrivilegedErpSession(httpRequest);
        return ResponseEntity.ok(masterDataService.updateItem(id, request));
    }

    @DeleteMapping("/items/{id}")
    public ResponseEntity<Map<String, Object>> deleteItem(@PathVariable String id, HttpServletRequest request) {
        usePrivilegedErpSession(request);
        return ResponseEntity.ok(masterDataService.deleteItem(id));
    }

    private void usePrivilegedErpSession(HttpServletRequest request) {
        if (request == null) {
            return;
        }
        try {
            request.setAttribute(ErpSessionStore.REQUEST_ATTR, authenticationService.getSetupSessionCookie());
        } catch (RuntimeException ignored) {
            // Keep the authenticated user's ERP session if setup credentials are unavailable.
        }
    }
}
