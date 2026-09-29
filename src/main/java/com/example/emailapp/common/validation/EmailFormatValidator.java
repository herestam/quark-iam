package com.example.emailapp.common.validation;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

/**
 * Pragmatic email syntax check. Deliberately stricter than RFC 5322 (no quoted
 * local parts, no address literals) because the addresses end up in SMTP
 * envelope commands, where whitespace and control characters are dangerous.
 */
public class EmailFormatValidator implements ConstraintValidator<ValidEmail, String> {

    private static final int MAX_LENGTH = 320;

    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*"
                    + "@"
                    + "(?:[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?\\.)+"
                    + "[A-Za-z]{2,}$");

    /** Characters that must never reach an SMTP header or the database. */
    private static final Pattern FORBIDDEN = Pattern.compile("[\\r\\n\\u0000]");

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return false;
        }
        String trimmed = value.trim();
        if (trimmed.length() > MAX_LENGTH || FORBIDDEN.matcher(trimmed).find()) {
            return false;
        }
        // A literal would be valid RFC-wise but is a common abuse vector.
        if (trimmed.contains("..") || trimmed.startsWith(".") || trimmed.contains(".@")) {
            return false;
        }
        try {
            return EMAIL.matcher(trimmed).matches();
        } catch (PatternSyntaxException e) {
            return false;
        }
    }
}
