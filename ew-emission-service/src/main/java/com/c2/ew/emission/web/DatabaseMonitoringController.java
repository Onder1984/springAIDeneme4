package com.c2.ew.emission.web;

import com.c2.ew.emission.dto.DatabaseMetricsDto;
import com.c2.ew.emission.service.DatabaseMonitoringService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 🛡️ PostgreSQL / PostGIS Performans ve Sorgu İzleme REST API Kontrolcüsü
 */
@RestController
@RequestMapping({"/api/v1/tactical/db-monitor", "/api/tactical/db-monitor"})
public class DatabaseMonitoringController {

    private final DatabaseMonitoringService databaseMonitoringService;

    public DatabaseMonitoringController(DatabaseMonitoringService databaseMonitoringService) {
        this.databaseMonitoringService = databaseMonitoringService;
    }

    /**
     * Canlı veritabanı sorgu ölçümleri ve performans istatistiklerini getirir.
     *
     * @param limit    Dönecek maksimum sorgu sayısı (Varsayılan: 20)
     * @param sortBy   Sıralama kriteri ('mean_time', 'total_time', 'calls', 'max_time')
     */
    @GetMapping("/stats")
    public ResponseEntity<DatabaseMetricsDto> getDatabaseStats(
        @RequestParam(defaultValue = "20") int limit,
        @RequestParam(defaultValue = "mean_time") String sortBy
    ) {
        DatabaseMetricsDto metrics = databaseMonitoringService.getDatabaseMetrics(limit, sortBy);
        return ResponseEntity.ok(metrics);
    }

    /**
     * pg_stat_statements sorgu sayaçlarını sıfırlar.
     */
    @PostMapping("/reset")
    public ResponseEntity<Map<String, Object>> resetDatabaseStats() {
        boolean success = databaseMonitoringService.resetStatistics();
        return ResponseEntity.ok(Map.of(
            "success", success,
            "message", success 
                ? "Sorgu performans istatistikleri başarıyla sıfırlandı." 
                : "İstatistikler sıfırlanamadı (Yetki veya uzantı hatası)."
        ));
    }
}
