package com.example.emailapp.email.service;

import com.example.emailapp.email.entity.EmailProvider;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * Incoming payload for saving the SMTP settings.
 *
 * <p>{@code password} is optional on purpose: an administrator editing an
 * existing configuration should not have to retype the secret, and a blank value
 * means "keep what is already stored".</p>
 */
public record EmailConfigurationCommand(
        @NotNull EmailProvider provider,
        @NotBlank @Size(max = 255) String smtpHost,
        @Min(1) @Max(65535) int smtpPort,
        @Size(max = 255) String username,
        @Size(max = 1000) String password,
        @NotBlank @Email @Size(max = 320) String fromEmail,
        @Size(max = 200) String fromName,
        boolean useTls,
        boolean useSsl,
        @Min(1000) @Max(300_000) int connectionTimeoutMs,
        boolean active
) {

    public EmailConfigurationCommand {
        if (smtpHost == null) {
            smtpHost = "";
        }
        smtpHost = smtpHost.trim();
    }

    public boolean changesPassword() {
        return password != null && !password.isBlank();
    }
}
