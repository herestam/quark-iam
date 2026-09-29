package com.example.emailapp.common.exception;

import java.util.List;

import com.example.emailapp.common.validation.SecretRedactor;
import com.example.emailapp.common.web.ErrorResponder;

import jakarta.inject.Inject;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;

/**
 * Maps a business error onto a response.
 *
 * <p>JSON for API clients, an HTML page for browsers; see
 * {@link ErrorResponder}. Messages are always passed through the redactor so a
 * stack trace or an SMTP credential can never reach the client.</p>
 */
@Provider
public class AppExceptionMapper implements ExceptionMapper<AppException> {

    @Context
    HttpHeaders headers;

    @Inject
    SecretRedactor redactor;

    @Inject
    ErrorResponder responder;

    @Override
    public Response toResponse(AppException exception) {
        String message = redactor.redact(exception.getMessage());
        Response.Status status = switch (exception) {
            case ResourceNotFoundException ignored -> Response.Status.NOT_FOUND;
            case InputValidationException ignored -> Response.Status.BAD_REQUEST;
            case BusinessRuleException ignored -> Response.Status.CONFLICT;
            case EmailProviderException ignored -> Response.Status.BAD_GATEWAY;
            default -> Response.Status.INTERNAL_SERVER_ERROR;
        };
        List<String> details = exception instanceof InputValidationException validation
                ? redactor.redactAll(validation.getFieldErrors())
                : List.of();
        return responder.respond(headers, status, exception.getCode(), message, details);
    }

    /** Catch-all for constraint violations raised by Jakarta Validation. */
    @Provider
    public static class ConstraintViolationMapper implements ExceptionMapper<ConstraintViolationException> {

        @Context
        HttpHeaders headers;

        @Inject
        SecretRedactor redactor;

        @Inject
        ErrorResponder responder;

        @Override
        public Response toResponse(ConstraintViolationException exception) {
            List<String> details = exception.getConstraintViolations().stream()
                    .map(violation -> violation.getPropertyPath() + " " + violation.getMessage())
                    .map(redactor::redact)
                    .sorted()
                    .toList();
            return responder.respond(headers, Response.Status.BAD_REQUEST, "VALIDATION_ERROR",
                    "Request validation failed", details);
        }
    }

    /** Last resort mapper: never leaks a stack trace to the client. */
    @Provider
    public static class UncaughtExceptionMapper implements ExceptionMapper<Throwable> {

        @Context
        HttpHeaders headers;

        @Inject
        ErrorResponder responder;

        @Override
        public Response toResponse(Throwable exception) {
            if (exception instanceof WebApplicationException webApplication) {
                // A 404, 405 or 415 raised by the JAX-RS layer is not a server
                // fault: keep the original status instead of reporting a 500.
                Response original = webApplication.getResponse();
                int status = original == null ? 404 : original.getStatus();
                if (status >= 500) {
                    org.jboss.logging.Logger.getLogger(UncaughtExceptionMapper.class)
                            .error("Unhandled exception while processing request", exception);
                }
                return responder.respond(headers, Response.Status.fromStatusCode(status),
                        "HTTP_" + status, statusMessage(status), List.of());
            }

            // The full stack trace belongs in the server log, not in the response.
            org.jboss.logging.Logger.getLogger(UncaughtExceptionMapper.class)
                    .error("Unhandled exception while processing request", exception);
            return responder.respond(headers, Response.Status.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR",
                    "An unexpected error occurred", List.of());
        }

        private static String statusMessage(int status) {
            return switch (status) {
                case 400 -> "The request could not be understood";
                case 401 -> "Authentication is required";
                case 403 -> "Access is denied";
                case 404 -> "The requested page does not exist";
                case 405 -> "That action is not allowed here";
                case 406 -> "The requested format is not available";
                case 409 -> "The request conflicts with the current state";
                case 415 -> "The submitted content type is not supported";
                default -> "The request could not be completed";
            };
        }
    }
}
