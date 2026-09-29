package com.example.emailapp.common;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.emailapp.common.validation.SecretRedactor;

class SecretRedactorTest {

    private final SecretRedactor redactor = new SecretRedactor();

    @Test
    @DisplayName("replaces a registered secret anywhere in a message")
    void redactsRegisteredSecret() {
        redactor.register("sup3r-s3cret");

        String message = redactor.redact("Auth failed for password sup3r-s3cret on host mail.example.com");

        assertFalse(message.contains("sup3r-s3cret"), message);
        assertTrue(message.contains("mail.example.com"), message);
    }

    @Test
    @DisplayName("ignores values too short to be a secret")
    void ignoresShortValues() {
        redactor.register("ab");

        assertEquals("about", redactor.redact("about"));
    }

    @Test
    @DisplayName("stops redacting after the secret is forgotten")
    void forgetsSecret() {
        redactor.register("hunter2000");
        redactor.forget("hunter2000");

        assertEquals("password hunter2000", redactor.redact("password hunter2000"));
    }

    @Test
    @DisplayName("caps the length so a provider stack trace cannot flood the UI")
    void trimsLongText() {
        String trimmed = redactor.redactAndTrim("x".repeat(5_000), 100);

        assertEquals(103, trimmed.length());
        assertTrue(trimmed.endsWith("..."));
    }

    @Test
    @DisplayName("is null safe everywhere")
    void isNullSafe() {
        assertNull(redactor.redact(null));
        assertNull(redactor.redactAndTrim(null, 10));
        assertTrue(redactor.redactAll(null).isEmpty());
    }
}
