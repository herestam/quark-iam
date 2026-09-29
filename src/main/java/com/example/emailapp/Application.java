package com.example.emailapp;

import org.jboss.logging.Logger;

import com.example.emailapp.config.DatabaseConfig;
import com.example.emailapp.email.service.EmailConfigurationService;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import java.util.logging.Level;

/**
 * Application bootstrap. Applies runtime-only settings that cannot be expressed
 * as static configuration, most notably turning Hibernate SQL logging on or off
 * without a rebuild.
 */
@ApplicationScoped
public class Application {

    private static final Logger LOG = Logger.getLogger(Application.class);

    void onStart(@Observes StartupEvent event, DatabaseConfig databaseConfig,
            EmailConfigurationService emailConfigurationService) {
        LOG.infof("Email campaign manager starting (Java %s)", System.getProperty("java.version"));

        // Seeding here guarantees a usable row exists before the background
        // worker can pick up a job, even on a brand new database.
        emailConfigurationService.ensureDefaults();

        if (databaseConfig.logSql()) {
            java.util.logging.Logger.getLogger("org.hibernate.SQL").setLevel(Level.INFO);
        } else {
            java.util.logging.Logger.getLogger("org.hibernate.SQL").setLevel(Level.WARNING);
        }
        if (databaseConfig.showSql()) {
            java.util.logging.Logger.getLogger("org.hibernate.orm.jdbc.bind").setLevel(Level.INFO);
        }
    }
}
