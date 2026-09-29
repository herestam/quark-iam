package com.example.emailapp.email.repository;

import java.util.List;
import java.util.Optional;

import com.example.emailapp.email.entity.EmailConfiguration;

import io.quarkus.hibernate.orm.panache.PanacheRepositoryBase;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class EmailConfigurationRepository implements PanacheRepositoryBase<EmailConfiguration, Long> {

    public Optional<EmailConfiguration> findByIdOptional(Long id) {
        if (id == null) {
            return Optional.empty();
        }
        return find("id", id).firstResultOptional();
    }

    /** The single configuration the application sends through, if one exists. */
    public Optional<EmailConfiguration> findActive() {
        return find("active = true order by updatedAt desc").firstResultOptional();
    }

    public List<EmailConfiguration> listAllOrdered() {
        return listAll();
    }

    public long countAll() {
        return count();
    }
}
