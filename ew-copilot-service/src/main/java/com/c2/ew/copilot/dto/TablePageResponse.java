package com.c2.ew.copilot.dto;

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
) {}
