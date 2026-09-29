package com.example.emailapp.recipient.mapper;

import java.util.List;

import com.example.emailapp.recipient.dto.RecipientRequest;
import com.example.emailapp.recipient.dto.RecipientResponse;
import com.example.emailapp.recipient.entity.Recipient;

import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class RecipientMapper {

    public RecipientResponse toResponse(Recipient entity) {
        if (entity == null) {
            return null;
        }
        return new RecipientResponse(entity.id, entity.email, entity.name, entity.company, entity.createdAt);
    }

    public List<RecipientResponse> toResponses(List<Recipient> entities) {
        return entities.stream().map(this::toResponse).toList();
    }

    public void apply(RecipientRequest request, Recipient entity) {
        entity.email = request.email().trim();
        entity.name = request.name();
        entity.company = request.company();
    }
}
