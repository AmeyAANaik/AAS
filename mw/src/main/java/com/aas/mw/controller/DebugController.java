package com.aas.mw.controller;

import com.aas.mw.service.ErpSessionStore;
import jakarta.servlet.http.HttpServletRequest;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/debug")
public class DebugController {

    @GetMapping("/auth")
    public ResponseEntity<Map<String, Object>> auth(HttpServletRequest request) {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        Map<String, Object> body = new LinkedHashMap<>();
        if (auth == null) {
            body.put("authenticated", false);
        } else {
            body.put("authenticated", auth.isAuthenticated());
            body.put("name", auth.getName());
            body.put("principal", String.valueOf(auth.getPrincipal()));
            body.put("authorities", auth.getAuthorities().stream()
                    .map(GrantedAuthority::getAuthority)
                    .collect(Collectors.toList()));
        }
        Object session = request.getAttribute(ErpSessionStore.REQUEST_ATTR);
        body.put("erpSessionPresent", session instanceof String sessionCookie && !sessionCookie.isBlank());
        return ResponseEntity.ok(body);
    }
}
