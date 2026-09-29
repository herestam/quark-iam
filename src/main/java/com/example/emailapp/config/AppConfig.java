package com.example.emailapp.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

/**
 * Background job engine settings, bound from {@code app.job.*}.
 * The poll interval is additionally referenced by the scheduler annotation
 * on {@code JobWorker}.
 */
@ConfigMapping(prefix = "app.job")
public interface AppConfig {

    /** How often the scheduler looks for queued jobs. */
    @WithDefault("1000")
    long pollIntervalMs();

    /** Cooperative cancellation check interval while a job is running. */
    @WithDefault("250")
    long controlCheckIntervalMs();

    /** Kill switch: when false the worker never picks up a job. */
    @WithDefault("true")
    boolean workerEnabled();

    /** Upper bound of jobs this instance processes at the same time. */
    @WithDefault("2")
    int maxConcurrentJobs();
}
