package com.aas.mw.controller;

import com.aas.mw.service.SetupService;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/setup")
public class SetupController {

    private final SetupService setupService;

    public SetupController(SetupService setupService) {
        this.setupService = setupService;
    }

    @PostMapping("/ensure")
    public ResponseEntity<Map<String, Object>> ensureSetup() {
        return ResponseEntity.ok(setupService.ensureSetup());
    }
}
