package com.example.emailapp.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Database diagnostics, bound from {@code app.database.*}.
 *
 * <p>SQL logging cannot be toggled by {@code application.properties} at
 * runtime, so it is applied once at startup by {@code Application}.</p>
 */
@ConfigMapping(prefix = "app.database")
public interface DatabaseConfig {

    @WithDefault("false")
    boolean logSql();

    @WithDefault("false")
    boolean showSql();
}
