package com.example.emailapp.common.exception;

import java.util.List;

/** Input failed validation. Carries every field error so the UI can show them all. */
public class InputValidationException extends AppException {

    private static final long serialVersionUID = 1L;

    private final transient List<String> fieldErrors;

    public InputValidationException(String message) {
        this(message, List.of());
    }

    public InputValidationException(String message, List<String> fieldErrors) {
        super("VALIDATION_ERROR", message);
        this.fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }

    public List<String> getFieldErrors() {
        return fieldErrors;
    }
}
