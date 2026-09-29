package com.example.emailapp.common.response;

import java.time.Instant;
import java.util.List;

/**
 * Uniform JSON envelope for every REST response. Entities are never
 * serialised directly - controllers always map to DTOs first.
 */
public record ApiResponse<T>(
        boolean success,
        String message,
        T data,
        List<String> errors,
        Instant timestamp
) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "OK", data, List.of(), Instant.now());
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>(true, message, data, List.of(), Instant.now());
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(false, code + ": " + message, null, List.of(), Instant.now());
    }

    public static <T> ApiResponse<T> error(String code, String message, List<String> errors) {
        String prefix = message == null || message.isBlank() ? "" : message + " - ";
        return new ApiResponse<>(false, prefix + code, null, errors, Instant.now());
    }
}
