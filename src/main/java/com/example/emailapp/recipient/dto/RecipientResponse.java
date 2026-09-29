package com.example.emailapp.recipient.dto;

import java.time.Instant;

public record RecipientResponse(
        Long id,
        String email,
        String name,
        String company,
        Instant createdAt
) {
}
