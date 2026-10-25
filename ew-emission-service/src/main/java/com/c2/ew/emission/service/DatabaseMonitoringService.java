package com.c2.ew.emission.service;

import com.c2.ew.emission.dto.DatabaseMetricsDto;
import com.c2.ew.emission.dto.DatabaseMetricsDto.DatabaseSummaryDto;
import com.c2.ew.emission.dto.DatabaseMetricsDto.IndexMetricDto;
import com.c2.ew.emission.dto.DatabaseMetricsDto.QueryMetricDto;
import com.c2.ew.emission.dto.DatabaseMetricsDto.TableMetricDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 🛡️ PostgreSQL / PostGIS Performans ve Sorgu İzleme Servisi
 * pg_stat_statements, pg_stat_user_tables ve pg_stat_user_indexes verilerini toplar.
 */
@Service
public class DatabaseMonitoringService {

    private static final Logger log = LoggerFactory.getLogger(DatabaseMonitoringService.class);
    private final JdbcTemplate jdbcTemplate;

    public DatabaseMonitoringService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * PostgreSQL performans ve sorgu istatistiklerini getirir.
     */
    public DatabaseMetricsDto getDatabaseMetrics(int limit, String sortBy) {
        int safeLimit = Math.max(5, Math.min(limit, 100));
        
        // 1. pg_stat_statements aktif mi kontrol et
        boolean pgStatActive = checkPgStatStatementsActive();

        String dbName = "tactical_ew_db_3";
        int activeConnections = 1;
        try {
            dbName = jdbcTemplate.queryForObject("SELECT current_database();", String.class);
            Integer conn = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_stat_activity WHERE state = 'active' AND datname = current_database();",
                Integer.class
            );
            if (conn != null) {
                activeConnections = conn;
            }
        } catch (Exception e) {
            log.warn("Veritabanı bağlantı bilgisi alınamadı: {}", e.getMessage());
        }

        List<QueryMetricDto> topQueries = new ArrayList<>();
        int totalQueriesTracked = 0;
        long totalCalls = 0;
        double totalExecTimeMs = 0.0;
        double meanExecTimeMs = 0.0;
        long slowQueryCount = 0;
        double overallCacheHit = 100.0;

        if (pgStatActive) {
            String safeOrderCol = switch (sortBy != null ? sortBy.toLowerCase() : "") {
                case "total_time" -> "total_exec_time";
                case "calls" -> "calls";
                case "max_time" -> "max_exec_time";
                default -> "mean_exec_time";
            };

            String querySql = String.format("""
                SELECT 
                    queryid::text as q_id,
                    query,
                    calls,
                    round(total_exec_time::numeric, 2) as total_time_ms,
                    round(mean_exec_time::numeric, 2) as mean_time_ms,
                    round(max_exec_time::numeric, 2) as max_time_ms,
                    round(min_exec_time::numeric, 2) as min_time_ms,
                    rows,
                    shared_blks_hit,
                    shared_blks_read
                FROM pg_stat_statements
                WHERE query NOT ILIKE '%%pg_stat_statements%%'
                  AND query NOT ILIKE '%%pg_catalog%%'
                  AND query NOT ILIKE '%%current_database%%'
                ORDER BY %s DESC
                LIMIT ?
                """, safeOrderCol);

            try {
                topQueries = jdbcTemplate.query(querySql, (rs, rowNum) -> {
                    long blksHit = rs.getLong("shared_blks_hit");
                    long blksRead = rs.getLong("shared_blks_read");
                    double hitPct = (blksHit + blksRead) > 0 
                        ? Math.round((blksHit * 100.0 / (blksHit + blksRead)) * 10.0) / 10.0 
                        : 100.0;

                    return new QueryMetricDto(
                        rs.getString("q_id"),
                        rs.getString("query"),
                        rs.getLong("calls"),
                        rs.getDouble("total_time_ms"),
                        rs.getDouble("mean_time_ms"),
                        rs.getDouble("max_time_ms"),
                        rs.getDouble("min_time_ms"),
                        rs.getLong("rows"),
                        blksHit,
                        blksRead,
                        hitPct
                    );
                }, safeLimit);

                // Kümülatif toplamlar
                String summarySql = """
                    SELECT 
                        count(*) as total_tracked,
                        coalesce(sum(calls), 0) as total_calls,
                        coalesce(round(sum(total_exec_time)::numeric, 2), 0.0) as sum_total_time,
                        coalesce(round(avg(mean_exec_time)::numeric, 2), 0.0) as avg_mean_time,
                        count(*) filter (where max_exec_time > 50.0) as slow_count,
                        coalesce(sum(shared_blks_hit), 0) as sum_blks_hit,
                        coalesce(sum(shared_blks_read), 0) as sum_blks_read
                    FROM pg_stat_statements
                    WHERE query NOT ILIKE '%pg_stat_statements%'
                      AND query NOT ILIKE '%pg_catalog%'
                    """;

                Map<String, Object> sumMap = jdbcTemplate.queryForMap(summarySql);
                totalQueriesTracked = ((Number) sumMap.getOrDefault("total_tracked", 0)).intValue();
                totalCalls = ((Number) sumMap.getOrDefault("total_calls", 0)).longValue();
                totalExecTimeMs = ((Number) sumMap.getOrDefault("sum_total_time", 0.0)).doubleValue();
                meanExecTimeMs = ((Number) sumMap.getOrDefault("avg_mean_time", 0.0)).doubleValue();
                slowQueryCount = ((Number) sumMap.getOrDefault("slow_count", 0)).longValue();

                long sumHit = ((Number) sumMap.getOrDefault("sum_blks_hit", 0)).longValue();
                long sumRead = ((Number) sumMap.getOrDefault("sum_blks_read", 0)).longValue();
                if (sumHit + sumRead > 0) {
                    overallCacheHit = Math.round((sumHit * 100.0 / (sumHit + sumRead)) * 10.0) / 10.0;
                }
            } catch (Exception e) {
                log.error("pg_stat_statements sorgusu başarısız: {}", e.getMessage(), e);
            }
        }

        // 2. Tablo İstatistikleri (Seq Scan vs Index Scan)
        List<TableMetricDto> tableMetrics = new ArrayList<>();
        try {
            String tableSql = """
                SELECT 
                    relname as table_name,
                    seq_scan,
                    seq_tup_read,
                    idx_scan,
                    idx_tup_fetch,
                    n_live_tup as live_rows
                FROM pg_stat_user_tables
                ORDER BY (idx_scan + seq_scan) DESC, n_live_tup DESC
                """;
            tableMetrics = jdbcTemplate.query(tableSql, (rs, rowNum) -> {
                long seq = rs.getLong("seq_scan");
                long idx = rs.getLong("idx_scan");
                double idxUsage = (seq + idx) > 0 
                    ? Math.round((idx * 100.0 / (seq + idx)) * 10.0) / 10.0 
                    : 100.0;

                return new TableMetricDto(
                    rs.getString("table_name"),
                    seq,
                    rs.getLong("seq_tup_read"),
                    idx,
                    rs.getLong("idx_tup_fetch"),
                    rs.getLong("live_rows"),
                    idxUsage
                );
            });
        } catch (Exception e) {
            log.error("pg_stat_user_tables sorgusu başarısız: {}", e.getMessage());
        }

        // 3. İndeks İstatistikleri (Hangi indeks ne kadar kullanıldı)
        List<IndexMetricDto> indexMetrics = new ArrayList<>();
        try {
            String indexSql = """
                SELECT 
                    ui.relname as table_name,
                    ui.indexrelname as index_name,
                    ui.idx_scan,
                    ui.idx_tup_read,
                    ui.idx_tup_fetch,
                    pg_size_pretty(pg_relation_size(ui.indexrelid)) as index_size
                FROM pg_stat_user_indexes ui
                ORDER BY ui.idx_scan DESC, pg_relation_size(ui.indexrelid) DESC
                LIMIT 25
                """;
            indexMetrics = jdbcTemplate.query(indexSql, (rs, rowNum) -> new IndexMetricDto(
                rs.getString("table_name"),
                rs.getString("index_name"),
                rs.getLong("idx_scan"),
                rs.getLong("idx_tup_read"),
                rs.getLong("idx_tup_fetch"),
                rs.getString("index_size")
            ));
        } catch (Exception e) {
            log.error("pg_stat_user_indexes sorgusu başarısız: {}", e.getMessage());
        }

        DatabaseSummaryDto summary = new DatabaseSummaryDto(
            totalQueriesTracked,
            totalCalls,
            totalExecTimeMs,
            meanExecTimeMs,
            slowQueryCount,
            overallCacheHit,
            dbName,
            activeConnections,
            pgStatActive
        );

        return new DatabaseMetricsDto(summary, topQueries, tableMetrics, indexMetrics);
    }

    /**
     * pg_stat_statements istatistiklerini sıfırlar.
     */
    public boolean resetStatistics() {
        try {
            jdbcTemplate.execute("SELECT pg_stat_statements_reset();");
            log.info("🛡️ pg_stat_statements istatistikleri başarıyla sıfırlandı.");
            return true;
        } catch (Exception e) {
            log.error("İstatistik sıfırlama başarısız: {}", e.getMessage());
            return false;
        }
    }

    private boolean checkPgStatStatementsActive() {
        try {
            Integer count = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM pg_extension WHERE extname = 'pg_stat_statements';",
                Integer.class
            );
            return count != null && count > 0;
        } catch (Exception e) {
            return false;
        }
    }
}
