package com.example.emailapp.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Sending / rate limiting settings, bound from {@code app.email.*}.
 *
 * <p>{@code fromEmail}/{@code fromName} are only fallbacks: once an email
 * configuration has been saved in the UI the database values win.</p>
 */
@ConfigMapping(prefix = "app.email")
public interface EmailConfig {

    /** Recipients dispatched per batch before the worker pauses for {@link #delayMs()}. */
    @WithDefault("10")
    int batchSize();

    /** Pause between two batches, in milliseconds. This is the sending rate limit. */
    @WithDefault("1000")
    long delayMs();

    /** Maximum number of attempts for a single recipient. */
    @WithDefault("3")
    int maxRetries();

    /** SMTP connect / read / write timeout in milliseconds. */
    @WithDefault("10000")
    int connectionTimeoutMs();

    @WithDefault("no-reply@localhost")
    String fromEmail();

    @WithDefault("Email Campaign Manager")
    String fromName();
}
