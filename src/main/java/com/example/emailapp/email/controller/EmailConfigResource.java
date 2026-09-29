package com.example.emailapp.email.controller;

import com.example.emailapp.common.response.ApiResponse;
import com.example.emailapp.email.service.EmailConfigurationCommand;
import com.example.emailapp.email.service.EmailConfigurationService;
import com.example.emailapp.email.service.EmailConfigurationSnapshot;

import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Email / SMTP settings API.
 *
 * <p>The response type is {@link EmailConfigurationSnapshot}, which has no
 * password field at all: there is no code path that can return the secret or
 * the SendGrid API key to a client.</p>
 */
@Path("/api/email-config")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class EmailConfigResource {

    @Inject
    EmailConfigurationService configurationService;

    @GET
    public ApiResponse<EmailConfigurationSnapshot> get() {
        return ApiResponse.ok(configurationService.get());
    }

    @PUT
    public ApiResponse<EmailConfigurationSnapshot> save(@Valid EmailConfigurationCommand command) {
        return ApiResponse.ok("Email configuration saved", configurationService.save(command));
    }

    /** Opens and closes an SMTP connection to prove the settings work. */
    @POST
    @Path("/test")
    public ApiResponse<String> testConnection() {
        configurationService.testConnection();
        return ApiResponse.ok("SMTP connection succeeded", "SMTP connection succeeded");
    }

    /** Sends a real message through the configured provider. */
    @POST
    @Path("/test-email")
    public ApiResponse<String> sendTestEmail(TestEmailRequest request) {
        String to = request == null ? null : request.to();
        configurationService.sendTestEmail(to);
        return ApiResponse.ok("Test email sent to " + to, "Message accepted by the provider");
    }

    /** Body of the "Send test email" form. */
    public record TestEmailRequest(@NotBlank @Email String to) {
    }
}
