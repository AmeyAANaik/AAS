package com.aas.mw.service;

import java.util.List;
import java.util.Map;

public class OpeningBalanceValidationException extends IllegalArgumentException {

    private final List<Map<String, Object>> errors;

    public OpeningBalanceValidationException(String message, List<Map<String, Object>> errors) {
        super(message);
        this.errors = errors == null ? List.of() : List.copyOf(errors);
    }

    public List<Map<String, Object>> getErrors() {
        return errors;
    }
}

