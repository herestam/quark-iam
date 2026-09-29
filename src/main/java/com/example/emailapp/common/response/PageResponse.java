package com.example.emailapp.common.response;

import java.util.List;

/** Simple page wrapper so the UI can render "showing X-Y of Z". */
public record PageResponse<T>(
        List<T> items,
        int page,
        int size,
        long total
) {

    public static <T> PageResponse<T> of(List<T> items, int page, int size, long total) {
        return new PageResponse<>(items, page, size, total);
    }

    public static <T> PageResponse<T> single(List<T> items, long total) {
        return new PageResponse<>(items, 0, items.size(), total);
    }

    public int totalPages() {
        return size <= 0 ? 1 : (int) Math.ceil((double) total / size);
    }

    public boolean hasPrevious() {
        return page > 0;
    }

    public boolean hasNext() {
        return (long) (page + 1) * size < total;
    }
}
