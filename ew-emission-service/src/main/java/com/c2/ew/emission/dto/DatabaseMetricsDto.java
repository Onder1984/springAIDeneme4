package com.c2.ew.emission.dto;

import java.util.List;

/**
 * 🛡️ PostgreSQL / PostGIS Performans ve Sorgu İzleme DTO'su
 */
public record DatabaseMetricsDto(
    DatabaseSummaryDto summary,
    List<QueryMetricDto> topQueries,
    List<TableMetricDto> tableMetrics,
    List<IndexMetricDto> indexMetrics
) {
    public record DatabaseSummaryDto(
        int totalQueriesTracked,
        long totalCalls,
        double totalExecTimeMs,
        double meanExecTimeMs,
        long slowQueryCount,
        double cacheHitPercentage,
        String databaseName,
        int activeConnections,
        boolean pgStatStatementsAvailable
    ) {}

    public record QueryMetricDto(
        String queryId,
        String query,
        long calls,
        double totalExecTimeMs,
        double meanExecTimeMs,
        double maxExecTimeMs,
        double minExecTimeMs,
        long rows,
        long sharedBlksHit,
        long sharedBlksRead,
        double cacheHitPercentage
    ) {}

    public record TableMetricDto(
        String tableName,
        long seqScan,
        long seqTupRead,
        long idxScan,
        long idxTupFetch,
        long liveRows,
        double indexUsagePercentage
    ) {}

    public record IndexMetricDto(
        String tableName,
        String indexName,
        long idxScan,
        long idxTupRead,
        long idxTupFetch,
        String indexSizePretty
    ) {}
}
