package com.example.emailapp.common.exception;

/** The email provider is not usable (missing settings, unreachable host, auth failure). */
public class EmailProviderException extends AppException {

    private static final long serialVersionUID = 1L;

    public EmailProviderException(String message) {
        super("EMAIL_PROVIDER_ERROR", message);
    }

    public EmailProviderException(String message, Throwable cause) {
        super("EMAIL_PROVIDER_ERROR", message, cause);
    }
}
