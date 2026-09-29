package com.example.emailapp.common.exception;

/**
 * Base type for every business error raised by the application.
 * Mappers translate it into a JSON {@code ApiResponse} payload.
 */
public class AppException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final String code;

    public AppException(String code, String message) {
        super(message);
        this.code = code;
    }

    public AppException(String code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

    public String getCode() {
        return code;
    }
}
