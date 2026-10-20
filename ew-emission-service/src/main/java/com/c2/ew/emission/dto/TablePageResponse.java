package com.c2.ew.emission.dto;

import org.springframework.data.domain.Page;
import java.util.List;

/**
 * Ant Design Table için sunucu taraflı sayfalama (Server-Side Pagination) DTO'su.
 */
public record TablePageResponse<T>(
    List<T> content,
    int page,
    int size,
    long totalElements,
    int totalPages
) {
    public static <T> TablePageResponse<T> of(Page<T> springPage) {
        return new TablePageResponse<>(
            springPage.getContent(),
            springPage.getNumber() + 1, // Ant Design için 1-indexed
            springPage.getSize(),
            springPage.getTotalElements(),
            springPage.getTotalPages()
        );
    }
}
