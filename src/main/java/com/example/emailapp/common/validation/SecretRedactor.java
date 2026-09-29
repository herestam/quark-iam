package com.example.emailapp.common.validation;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Central place that guarantees a stored secret never leaks through a log
 * statement, an exception message or a rendered page.
 *
 * <p>Every secret that is written to or read from the database registers
 * itself here, and every user facing string is passed through
 * {@link #redact(String)} before it leaves the process.</p>
 */
@ApplicationScoped
public class SecretRedactor {

    /** Guards against unbounded growth if a user re-saves a password repeatedly. */
    private static final int MAX_TRACKED_SECRETS = 32;

    private final Set<String> secrets = ConcurrentHashMap.newKeySet();

    public void register(String secret) {
        if (secret == null || secret.isBlank() || secret.length() < 4) {
            return;
        }
        if (secrets.size() >= MAX_TRACKED_SECRETS) {
            secrets.clear();
        }
        secrets.add(secret);
    }

    public void registerAll(Map<String, String> values) {
        values.values().forEach(this::register);
    }

    public void forget(String secret) {
        secrets.remove(secret);
    }

    public void clear() {
        secrets.clear();
    }

    /**
     * Replaces every known secret with {@code ***}. Safe to call on any
     * string, including {@code null}.
     */
    public String redact(String text) {
        if (text == null || text.isEmpty() || secrets.isEmpty()) {
            return text;
        }
        String result = text;
        for (String secret : secrets) {
            if (result.contains(secret)) {
                result = result.replace(secret, "***");
            }
        }
        return result;
    }

    /** Redacts every element of a list, preserving order. */
    public java.util.List<String> redactAll(java.util.List<String> values) {
        if (values == null || values.isEmpty()) {
            return java.util.List.of();
        }
        return values.stream().map(this::redact).toList();
    }

    /** Redacts and caps the length so a huge provider stack trace cannot flood the UI. */
    public String redactAndTrim(String text, int maxLength) {
        String safe = redact(text);
        if (safe == null) {
            return null;
        }
        return safe.length() <= maxLength ? safe : safe.substring(0, maxLength) + "...";
    }
}
