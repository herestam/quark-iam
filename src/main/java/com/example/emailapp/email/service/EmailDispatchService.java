package com.example.emailapp.email.service;

import java.util.Map;

import com.example.emailapp.email.dto.OutboundEmail;

/**
 * Turns a campaign template into a concrete message and hands it to the
 * {@link EmailService}. Every send in the job system goes through this bean,
 * so rendering rules (escaping, placeholders) live in exactly one place.
 */
public interface EmailDispatchService {

    /**
     * Renders the template for the given variables and delivers the message.
     * Any failure is propagated so the caller can mark the recipient as failed.
     */
    void dispatch(OutboundEmail email);

    /** Convenience overload that builds the variable map from explicit fields. */
    void dispatch(OutboundEmail email, Map<String, String> extraVariables);
}
