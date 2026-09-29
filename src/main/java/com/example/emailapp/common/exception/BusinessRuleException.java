package com.example.emailapp.common.exception;

/** The request was well formed but violates a business rule. Rendered as HTTP 400. */
public class BusinessRuleException extends AppException {

    private static final long serialVersionUID = 1L;

    public BusinessRuleException(String message) {
        super("BUSINESS_RULE_VIOLATION", message);
    }
}
