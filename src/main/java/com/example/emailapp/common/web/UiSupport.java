package com.example.emailapp.common.web;

/** Small helpers shared by the Qute controllers. */
public final class UiSupport {

    /** Navigation section highlighted in the sidebar. */
    public enum Section {
        DASHBOARD, CAMPAIGNS, TEMPLATES, RECIPIENTS, JOBS, SETTINGS
    }

    private UiSupport() {
    }

    /**
     * Formats a duration such as {@code 04:32} or {@code 1d 02:15:09} for the
     * dashboard. Implemented in Java rather than in Qute to keep the templates
     * free of arithmetic.
     */
    public static String duration(java.time.Duration duration) {
        if (duration == null || duration.isNegative()) {
            return "-";
        }
        long seconds = duration.getSeconds();
        long days = seconds / 86_400;
        long hours = (seconds % 86_400) / 3_600;
        long minutes = (seconds % 3_600) / 60;
        long remaining = seconds % 60;
        if (days > 0) {
            return String.format("%dd %02d:%02d:%02d", days, hours, minutes, remaining);
        }
        if (hours > 0) {
            return String.format("%02d:%02d:%02d", hours, minutes, remaining);
        }
        return String.format("%02d:%02d", minutes, remaining);
    }

    /** Elapsed time of a job as of now, using the most relevant end timestamp. */
    public static String elapsed(java.time.Instant startedAt, java.time.Instant endedAt) {
        if (startedAt == null) {
            return "-";
        }
        return duration(java.time.Duration.between(startedAt, endedAt == null ? java.time.Instant.now() : endedAt));
    }

    public static int percent(int done, int total) {
        if (total <= 0) {
            return 0;
        }
        return Math.min(100, Math.max(0, (int) Math.round((done * 100.0) / total)));
    }
}
