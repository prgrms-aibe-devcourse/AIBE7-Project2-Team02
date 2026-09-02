package org.example.matcheat.global.dto;

import java.util.List;
import java.util.function.Predicate;

public record PageResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {
    public static <T> PageResponse<T> from(List<T> source, int page, int size, Predicate<T> filter) {
        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(100, size));
        List<T> filtered = source.stream().filter(filter).toList();
        int from = Math.min(filtered.size(), safePage * safeSize);
        int to = Math.min(filtered.size(), from + safeSize);
        int pages = filtered.isEmpty() ? 0 : (filtered.size() + safeSize - 1) / safeSize;
        return new PageResponse<>(filtered.subList(from, to), safePage, safeSize, filtered.size(), pages);
    }
}
