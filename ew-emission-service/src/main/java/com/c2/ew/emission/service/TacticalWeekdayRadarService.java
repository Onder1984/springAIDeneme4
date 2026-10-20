package com.c2.ew.emission.service;

import com.c2.ew.emission.dto.WeekdayRadarDistributionDto;
import com.c2.ew.emission.infrastructure.repository.TacticalComintJpaRepository;
import com.c2.ew.emission.infrastructure.repository.TacticalEmissionJpaRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

/**
 * Haftanın Günlerine Göre LOB ve Kestirim Dağılımı Hesaplama Servisi (Örümcek Ağı / Spider Web Grafiği)
 * PostgreSQL ISODOW (1: Pazartesi .. 7: Pazar) ve tek geçişli COUNT(*) FILTER (...) optimizasyonu kullanır.
 */
@Service
public class TacticalWeekdayRadarService {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final TacticalEmissionJpaRepository emissionRepo;
    private final TacticalComintJpaRepository comintRepo;

    private static final String[] DAY_NAMES = {
        "Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar"
    };

    private static final String[] SHORT_DAY_NAMES = {
        "Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz"
    };

    public TacticalWeekdayRadarService(
        NamedParameterJdbcTemplate jdbcTemplate,
        TacticalEmissionJpaRepository emissionRepo,
        TacticalComintJpaRepository comintRepo
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.emissionRepo = emissionRepo;
        this.comintRepo = comintRepo;
    }

    public WeekdayRadarDistributionDto calculateWeekdayDistribution(String source, String recordType, String timeRange) {
        String cleanSource = (source == null || source.isBlank()) ? "TUMU" : source.trim().toUpperCase(Locale.ENGLISH);
        if (!cleanSource.equals("RADAR") && !cleanSource.equals("COMINT") && !cleanSource.equals("TUMU")) {
            if (cleanSource.contains("RADAR") || cleanSource.contains("ELINT")) cleanSource = "RADAR";
            else if (cleanSource.contains("COMINT") || cleanSource.contains("MUHABERE") || cleanSource.contains("TELSİZ")) cleanSource = "COMINT";
            else cleanSource = "TUMU";
        }

        String cleanRecordType = (recordType == null || recordType.isBlank()) ? "TUMU" : recordType.trim().toUpperCase(Locale.ENGLISH);
        if (!cleanRecordType.equals("SADECE_LOB") && !cleanRecordType.equals("SADECE_KESTIRIM") && !cleanRecordType.equals("TUMU")) {
            if (cleanRecordType.contains("LOB")) cleanRecordType = "SADECE_LOB";
            else if (cleanRecordType.contains("KESTIRIM") || cleanRecordType.contains("ELIPS")) cleanRecordType = "SADECE_KESTIRIM";
            else cleanRecordType = "TUMU";
        }

        String cleanTime = (timeRange == null || timeRange.isBlank()) ? "ALL" : timeRange.trim().toUpperCase(Locale.ENGLISH);

        // Radar ve Muhabere referans zamanları
        Instant radarRef = getRadarRefTime();
        Instant comintRef = getComintRefTime();

        // 7 günlük dizi hazırlığı (1: Pazartesi .. 7: Pazar)
        long[] radarLob = new long[7];
        long[] radarKestirim = new long[7];
        long[] radarTotal = new long[7];

        long[] comintLob = new long[7];
        long[] comintKestirim = new long[7];
        long[] comintTotal = new long[7];

        // 1. Radar emisyonlarını sorgula (Gerekiyorsa)
        if (cleanSource.equals("RADAR") || cleanSource.equals("TUMU")) {
            queryTable("tactical_emissions", cleanTime, radarRef, radarLob, radarKestirim, radarTotal);
        }

        // 2. Muhabere emisyonlarını sorgula (Gerekiyorsa)
        if (cleanSource.equals("COMINT") || cleanSource.equals("TUMU")) {
            queryTable("tactical_comint", cleanTime, comintRef, comintLob, comintKestirim, comintTotal);
        }

        // DTO maddelerini inşa et ve istatistikleri topla
        List<WeekdayRadarDistributionDto.WeekdayItem> dayItems = new ArrayList<>(7);
        long totalRadarCount = 0;
        long totalComintCount = 0;
        long grandTotal = 0;
        long maxActiveValue = 0;

        for (int i = 0; i < 7; i++) {
            int dow = i + 1;
            String dayName = DAY_NAMES[i];
            String shortName = SHORT_DAY_NAMES[i];

            long rLob = radarLob[i];
            long rKes = radarKestirim[i];
            long rTot = radarTotal[i];

            long cLob = comintLob[i];
            long cKes = comintKestirim[i];
            long cTot = comintTotal[i];

            // Aktif filtrelere göre bu günün sayısını hesapla
            long activeVal = 0;
            if (cleanSource.equals("RADAR")) {
                if (cleanRecordType.equals("SADECE_LOB")) activeVal = rLob;
                else if (cleanRecordType.equals("SADECE_KESTIRIM")) activeVal = rKes;
                else activeVal = rTot;
            } else if (cleanSource.equals("COMINT")) {
                if (cleanRecordType.equals("SADECE_LOB")) activeVal = cLob;
                else if (cleanRecordType.equals("SADECE_KESTIRIM")) activeVal = cKes;
                else activeVal = cTot;
            } else {
                // TUMU
                if (cleanRecordType.equals("SADECE_LOB")) activeVal = rLob + cLob;
                else if (cleanRecordType.equals("SADECE_KESTIRIM")) activeVal = rKes + cKes;
                else activeVal = rTot + cTot;
            }

            if (activeVal > maxActiveValue) {
                maxActiveValue = activeVal;
            }

            // Ayrıca radar ve comint'in kendi tepe değerlerini de dikkate al
            long radarRefVal = cleanRecordType.equals("SADECE_LOB") ? rLob : (cleanRecordType.equals("SADECE_KESTIRIM") ? rKes : rTot);
            long comintRefVal = cleanRecordType.equals("SADECE_LOB") ? cLob : (cleanRecordType.equals("SADECE_KESTIRIM") ? cKes : cTot);
            if (radarRefVal > maxActiveValue) maxActiveValue = radarRefVal;
            if (comintRefVal > maxActiveValue) maxActiveValue = comintRefVal;

            totalRadarCount += radarRefVal;
            totalComintCount += comintRefVal;
            grandTotal += activeVal;

            dayItems.add(new WeekdayRadarDistributionDto.WeekdayItem(
                dow,
                dayName,
                shortName,
                rLob,
                rKes,
                rTot,
                cLob,
                cKes,
                cTot,
                activeVal
            ));
        }

        // ECharts radar indicator tavanını hesapla (Estetik yuvarlama)
        long indicatorMax = calculateIndicatorMax(maxActiveValue);

        return new WeekdayRadarDistributionDto(
            cleanSource,
            cleanRecordType,
            cleanTime,
            totalRadarCount,
            totalComintCount,
            grandTotal,
            indicatorMax,
            dayItems
        );
    }

    private void queryTable(
        String tableName,
        String cleanTime,
        Instant refTime,
        long[] lobOut,
        long[] kestirimOut,
        long[] totalOut
    ) {
        MapSqlParameterSource params = new MapSqlParameterSource();
        StringBuilder where = new StringBuilder(" WHERE 1=1 ");

        if (!cleanTime.equalsIgnoreCase("ALL")) {
            Instant start = null;
            if (cleanTime.contains("7_GUN") || cleanTime.contains("HAFTA")) {
                start = refTime.minus(Duration.ofDays(7));
            } else if (cleanTime.contains("30_GUN") || cleanTime.contains("AY")) {
                start = refTime.minus(Duration.ofDays(30));
            } else if (cleanTime.contains("90_GUN")) {
                start = refTime.minus(Duration.ofDays(90));
            } else if (cleanTime.contains("24_SAAT") || cleanTime.contains("BUGUN")) {
                start = refTime.minus(Duration.ofHours(24));
            }

            if (start != null) {
                where.append(" AND COALESCE(ilk_tespit_zamani, son_tespit_zamani) >= :start ")
                     .append(" AND COALESCE(ilk_tespit_zamani, son_tespit_zamani) <= :end ");
                params.addValue("start", java.sql.Timestamp.from(start));
                params.addValue("end", java.sql.Timestamp.from(refTime));
            }
        }

        String sql = String.format(
            "SELECT " +
            "  EXTRACT(ISODOW FROM COALESCE(ilk_tespit_zamani, son_tespit_zamani))::int AS dow, " +
            "  COUNT(*) FILTER (WHERE kayit_tipi = 'HAM_LOB') AS lob_count, " +
            "  COUNT(*) FILTER (WHERE kayit_tipi = 'KESTIRIM_ELIPS') AS kestirim_count, " +
            "  COUNT(*) AS total_count " +
            "FROM %s %s " +
            "GROUP BY 1 ORDER BY 1",
            tableName, where
        );

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params);
        for (Map<String, Object> r : rows) {
            Number dowNum = (Number) r.get("dow");
            if (dowNum != null) {
                int dow = dowNum.intValue(); // 1..7
                if (dow >= 1 && dow <= 7) {
                    int idx = dow - 1;
                    lobOut[idx] = ((Number) r.get("lob_count")).longValue();
                    kestirimOut[idx] = ((Number) r.get("kestirim_count")).longValue();
                    totalOut[idx] = ((Number) r.get("total_count")).longValue();
                }
            }
        }
    }

    private long calculateIndicatorMax(long maxValue) {
        if (maxValue <= 0) return 100;
        // %15 pay ekleyip yuvarla
        long target = (long) (maxValue * 1.15);
        if (target <= 50) return 50;
        if (target <= 100) return 100;
        if (target <= 500) return ((target + 99) / 100) * 100;
        if (target <= 5000) return ((target + 499) / 500) * 500;
        if (target <= 50000) return ((target + 4999) / 5000) * 5000;
        return ((target + 9999) / 10000) * 10000;
    }

    private Instant getRadarRefTime() {
        try {
            Instant max = emissionRepo.findMaxSonTespitZamani();
            if (max != null) return max;
        } catch (Exception ignored) {}
        return Instant.now();
    }

    private Instant getComintRefTime() {
        try {
            Instant max = comintRepo.findMaxSonTespitZamani();
            if (max != null) return max;
        } catch (Exception ignored) {}
        return Instant.now();
    }
}
