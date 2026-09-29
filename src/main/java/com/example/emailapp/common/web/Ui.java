package com.example.emailapp.common.web;

import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

import com.example.emailapp.campaign.entity.CampaignStatus;
import com.example.emailapp.email.entity.EmailProvider;
import com.example.emailapp.job.entity.JobLogLevel;
import com.example.emailapp.job.entity.JobRecipientStatus;
import com.example.emailapp.job.entity.JobStatus;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Formatting and status helpers used by the Qute templates.
 *
 * <p>Injected into every page render as the {@code ui} data attribute so the
 * templates stay free of arithmetic and of null checks. Everything here is
 * side effect free so it is safe to call repeatedly while a page polls.</p>
 */
@ApplicationScoped
public class Ui {

    private static final DateTimeFormatter DATE_TIME = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss").withZone(ZoneId.systemDefault());
    private static final DateTimeFormatter DATE = DateTimeFormatter
            .ofPattern("yyyy-MM-dd").withZone(ZoneId.systemDefault());

    /** Placeholder used wherever a value is optional. */
    public static final String DASH = "\u2014";

    @Inject
    Views views;

    // ------------------------------------------------------------------ status

    public String badge(JobStatus status) {
        return switch (status == null ? JobStatus.PENDING : status) {
            case PENDING -> "badge badge-pending";
            case RUNNING -> "badge badge-running";
            case PAUSED -> "badge badge-paused";
            case COMPLETED -> "badge badge-completed";
            case CANCELLED -> "badge badge-cancelled";
            case FAILED -> "badge badge-failed";
        };
    }

    public String badge(CampaignStatus status) {
        return switch (status == null ? CampaignStatus.DRAFT : status) {
            case DRAFT -> "badge badge-pending";
            case READY -> "badge badge-completed";
            case RUNNING -> "badge badge-running";
            case PAUSED -> "badge badge-paused";
            case COMPLETED -> "badge badge-completed";
            case CANCELLED -> "badge badge-cancelled";
            case FAILED -> "badge badge-failed";
        };
    }

    public String badge(JobRecipientStatus status) {
        return switch (status == null ? JobRecipientStatus.PENDING : status) {
            case PENDING -> "badge badge-pending";
            case PROCESSING -> "badge badge-running";
            case SENT -> "badge badge-completed";
            case FAILED -> "badge badge-failed";
            case CANCELLED -> "badge badge-cancelled";
        };
    }

    public String badge(JobLogLevel level) {
        return switch (level == null ? JobLogLevel.INFO : level) {
            case INFO -> "badge badge-pending";
            case WARN -> "badge badge-paused";
            case ERROR -> "badge badge-failed";
        };
    }

    public String badge(EmailProvider provider) {
        return "badge badge-" + (provider == null ? "smtp" : provider.name().toLowerCase());
    }

    public String progressClass(JobStatus status) {
        return status == JobStatus.FAILED ? "bar bar-failed"
                : status == JobStatus.CANCELLED ? "bar bar-cancelled"
                        : "bar";
    }

    // -------------------------------------------------------------- formatting

    public String dash(String value) {
        return value == null || value.isBlank() ? DASH : value;
    }

    public String instant(Instant value) {
        return value == null ? DASH : DATE_TIME.format(value);
    }

    public String date(Instant value) {
        return value == null ? DASH : DATE.format(value);
    }

    /** Compact "3m ago" style timestamp used in the live job tables. */
    public String ago(Instant value) {
        if (value == null) {
            return DASH;
        }
        Duration elapsed = Duration.between(value, Instant.now());
        if (elapsed.isNegative()) {
            return "just now";
        }
        long seconds = elapsed.getSeconds();
        if (seconds < 60) {
            return seconds + "s ago";
        }
        if (seconds < 3_600) {
            return (seconds / 60) + "m ago";
        }
        if (seconds < 86_400) {
            return (seconds / 3_600) + "h ago";
        }
        return (seconds / 86_400) + "d ago";
    }

    public String number(long value) {
        return java.text.NumberFormat.getIntegerInstance().format(value);
    }

    public String elapsed(Instant startedAt, Instant endedAt) {
        return UiSupport.elapsed(startedAt, endedAt);
    }

    public String percent(int done, int total) {
        return UiSupport.percent(done, total) + "%";
    }

    public boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    public String truncate(String value, int max) {
        if (value == null) {
            return DASH;
        }
        String flat = value.replaceAll("\\s+", " ").trim();
        return flat.length() <= max ? flat : flat.substring(0, max - 1) + "\u2026";
    }

    // ------------------------------------------------------------------ errors

    /**
     * Renders the shared error page. Kept here so both the HTML and the JSON
     * exception mappers stay in sync.
     */
    public String renderError(int status, String code, String message) {
        return views.render(Views.ERROR)
                .data("status", status)
                .data("code", code)
                .data("message", message == null || message.isBlank() ? "Something went wrong." : message)
                .data("ui", this)
                .data("section", UiSupport.Section.DASHBOARD.name())
                .render();
    }
}
