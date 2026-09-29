package com.example.emailapp.common.exception;

/** Requested resource does not exist. Rendered as HTTP 404. */
public class ResourceNotFoundException extends AppException {

    private static final long serialVersionUID = 1L;

    public ResourceNotFoundException(String message) {
        super("NOT_FOUND", message);
    }

    public ResourceNotFoundException(String type, Object id) {
        super("NOT_FOUND", type + " " + id + " was not found");
    }
}
