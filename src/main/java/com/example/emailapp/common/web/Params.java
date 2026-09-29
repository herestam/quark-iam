package com.example.emailapp.common.web;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import jakarta.ws.rs.core.MultivaluedMap;
import jakarta.ws.rs.core.UriInfo;

/**
 * Null-safe read only view over the current request's query or form parameters.
 *
 * <p>Templates call {@code params.get('search')} without having to guard against
 * a missing value, which matters because Qute runs in strict rendering mode and
 * a null dereference would fail the whole page.</p>
 */
public final class Params {

    private final Map<String, String> values = new LinkedHashMap<>();

    public static Params of(MultivaluedMap<String, String> source) {
        Params params = new Params();
        if (source != null) {
            source.forEach((key, list) -> {
                if (!list.isEmpty() && list.get(0) != null) {
                    params.values.put(key, list.get(0));
                }
            });
        }
        return params;
    }

    /**
     * The query string of the current request.
     *
     * <p>{@code @Context MultivaluedMap} is not a supported JAX-RS injection, so
     * the page resources read the parameters from {@link UriInfo} instead.</p>
     */
    public static Params of(UriInfo info) {
        return info == null ? new Params() : of(info.getQueryParameters());
    }

    public static Params of(Map<String, String> source) {
        Params params = new Params();
        if (source != null) {
            source.forEach((key, value) -> {
                if (value != null) {
                    params.values.put(key, value);
                }
            });
        }
        return params;
    }

    /** Returns the trimmed value or an empty string, never null. */
    public String get(String key) {
        return get(key, "");
    }

    public String get(String key, String fallback) {
        String value = values.get(key);
        return value == null || value.isBlank() ? fallback : value.trim();
    }

    public int getInt(String key, int fallback) {
        String value = get(key, null);
        if (value == null) {
            return fallback;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    public boolean getBoolean(String key) {
        String value = get(key, null);
        return value != null && (value.equalsIgnoreCase("true") || value.equals("1") || value.equals("on"));
    }

    public long[] getIds(String key) {
        String raw = get(key, null);
        if (raw == null) {
            return new long[0];
        }
        String[] parts = raw.split(",");
        long[] ids = new long[parts.length];
        int found = 0;
        for (String part : parts) {
            if (part.isBlank()) {
                continue;
            }
            try {
                ids[found++] = Long.parseLong(part.trim());
            } catch (NumberFormatException e) {
                // A malformed id is ignored rather than failing the whole form.
            }
        }
        if (found == ids.length) {
            return ids;
        }
        long[] trimmed = new long[found];
        System.arraycopy(ids, 0, trimmed, 0, found);
        return trimmed;
    }

    public int page() {
        return Math.max(0, getInt("page", 0));
    }

    public int size(int fallback) {
        int requested = getInt("size", fallback);
        return Math.min(200, Math.max(1, requested));
    }

    public String section(String key) {
        String value = get(key, "").toUpperCase(Locale.ROOT);
        return value.isEmpty() ? "" : value;
    }

    public String url(String path, String... pairs) {
        StringBuilder url = new StringBuilder(path);
        boolean first = true;
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            String value = pairs[i + 1];
            if (value == null || value.isBlank()) {
                continue;
            }
            url.append(first ? '?' : '&');
            url.append(pairs[i]).append('=').append(Redirects.encode(value));
            first = false;
        }
        return url.toString();
    }
}
