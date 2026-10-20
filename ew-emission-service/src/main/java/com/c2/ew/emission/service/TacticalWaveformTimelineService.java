package com.c2.ew.emission.service;

import com.c2.ew.emission.dto.TacticalWaveformTimelineDto;
import com.c2.ew.emission.infrastructure.repository.TacticalComintJpaRepository;
import com.c2.ew.emission.infrastructure.repository.TacticalEmissionJpaRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
public class TacticalWaveformTimelineService {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
        .withZone(ZoneId.of("Europe/Istanbul"));

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final TacticalEmissionJpaRepository emissionRepo;
    private final TacticalComintJpaRepository comintRepo;

    public TacticalWaveformTimelineService(
        NamedParameterJdbcTemplate jdbcTemplate,
        TacticalEmissionJpaRepository emissionRepo,
        TacticalComintJpaRepository comintRepo
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.emissionRepo = emissionRepo;
        this.comintRepo = comintRepo;
    }

    public TacticalWaveformTimelineDto getTimeline(String source, String targetKey, String timeRange, Integer limit) {
        boolean isRadar = source == null || !source.equalsIgnoreCase("COMINT");
        int fetchLimit = (limit != null && limit > 0 && limit <= 500) ? limit : 200;
        String cleanTime = (timeRange != null && !timeRange.isBlank()) ? timeRange.toUpperCase().trim() : "ALL";

        List<TacticalWaveformTimelineDto.AvailableTarget> availableTargets = getAvailableTargets(isRadar);

        // Hedef anahtar boşsa en aktif ilk hedefi seç
        String effectiveTargetKey = (targetKey != null && !targetKey.isBlank()) ? targetKey.trim().toUpperCase() : null;
        if (effectiveTargetKey == null && !availableTargets.isEmpty()) {
            effectiveTargetKey = availableTargets.get(0).key();
        }

        if (effectiveTargetKey == null) {
            return new TacticalWaveformTimelineDto(
                isRadar ? "RADAR" : "COMINT",
                "-",
                "Hedef Bulunamadı",
                cleanTime,
                0,
                Collections.emptyList(),
                availableTargets
            );
        }

        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("targetKey", effectiveTargetKey);
        params.addValue("limit", fetchLimit);

        StringBuilder whereClause = new StringBuilder();
        if (!cleanTime.equalsIgnoreCase("ALL")) {
            Instant refTime = isRadar ? getRadarRefTime() : getComintRefTime();
            Instant start = null;
            if (cleanTime.contains("SON_1_SAAT") || cleanTime.contains("1_SAAT")) {
                start = refTime.minus(Duration.ofHours(1));
            } else if (cleanTime.contains("SON_6_SAAT") || cleanTime.contains("6_SAAT")) {
                start = refTime.minus(Duration.ofHours(6));
            } else if (cleanTime.contains("SON_24_SAAT") || cleanTime.contains("24_SAAT") || cleanTime.contains("BUGUN")) {
                start = refTime.minus(Duration.ofHours(24));
            } else if (cleanTime.contains("7_GUN") || cleanTime.contains("HAFTA")) {
                start = refTime.minus(Duration.ofDays(7));
            }

            if (start != null) {
                whereClause.append(" AND son_tespit_zamani >= :start AND son_tespit_zamani <= :end ");
                params.addValue("start", Timestamp.from(start));
                params.addValue("end", Timestamp.from(refTime));
            }
        }

        String targetLabel = effectiveTargetKey;
        List<TacticalWaveformTimelineDto.TimelinePoint> points = new ArrayList<>();

        if (isRadar) {
            String sql = """
                SELECT emission_id, son_tespit_zamani, min_frekans_mhz, max_frekans_mhz,
                       min_pri_micro_sec, max_pri_micro_sec, min_pw_micro_sec, max_pw_micro_sec,
                       genlik_dbm, sure_sn, teshis_kimlik, radar_adi, radar_gorevi, modulasyon,
                       hedef_yer_bilgisi, hedef_mevzi_bilgisi
                FROM tactical_emissions
                WHERE UPPER(spot_no) = :targetKey
                """ + whereClause + """
                ORDER BY son_tespit_zamani ASC
                LIMIT :limit
                """;

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params);
            String dominantRadarName = null;

            for (Map<String, Object> r : rows) {
                Timestamp ts = (Timestamp) r.get("son_tespit_zamani");
                Instant inst = ts != null ? ts.toInstant() : Instant.now();
                String formatted = TIME_FORMATTER.format(inst);

                Double minF = getDouble(r, "min_frekans_mhz");
                Double maxF = getDouble(r, "max_frekans_mhz");
                Double centerF = calculateCenter(minF, maxF);

                Double minP = getDouble(r, "min_pri_micro_sec");
                Double maxP = getDouble(r, "max_pri_micro_sec");
                Double centerP = calculateCenter(minP, maxP);

                Double minW = getDouble(r, "min_pw_micro_sec");
                Double maxW = getDouble(r, "max_pw_micro_sec");
                Double centerW = calculateCenter(minW, maxW);

                Double amp = getDouble(r, "genlik_dbm");
                Double sure = getDouble(r, "sure_sn");
                String rName = (String) r.get("radar_adi");
                if (dominantRadarName == null && rName != null) {
                    dominantRadarName = rName;
                }

                points.add(new TacticalWaveformTimelineDto.TimelinePoint(
                    (String) r.get("emission_id"),
                    inst,
                    formatted,
                    minF, maxF, centerF,
                    minP, maxP, centerP,
                    minW, maxW, centerW,
                    amp, sure,
                    (String) r.get("teshis_kimlik"),
                    rName,
                    (String) r.get("radar_gorevi"),
                    (String) r.get("modulasyon"),
                    (String) r.get("hedef_yer_bilgisi"),
                    (String) r.get("hedef_mevzi_bilgisi")
                ));
            }

            if (dominantRadarName != null) {
                targetLabel = String.format("%s [Spot No: %s]", dominantRadarName, effectiveTargetKey);
            } else {
                targetLabel = String.format("Radar Spot No: %s", effectiveTargetKey);
            }

        } else {
            String sql = """
                SELECT comint_id, son_tespit_zamani, min_frekans_mhz, max_frekans_mhz,
                       genlik_dbm, sure_sn, teshis_kimlik, cagri_adi, karsi_cagri_adi,
                       haberlesme_sekli, modulasyon, lisan, hedef_yer_bilgisi
                FROM tactical_comint
                WHERE UPPER(cagri_adi) = :targetKey
                """ + whereClause + """
                ORDER BY son_tespit_zamani ASC
                LIMIT :limit
                """;

            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params);
            for (Map<String, Object> r : rows) {
                Timestamp ts = (Timestamp) r.get("son_tespit_zamani");
                Instant inst = ts != null ? ts.toInstant() : Instant.now();
                String formatted = TIME_FORMATTER.format(inst);

                Double minF = getDouble(r, "min_frekans_mhz");
                Double maxF = getDouble(r, "max_frekans_mhz");
                Double centerF = calculateCenter(minF, maxF);

                Double amp = getDouble(r, "genlik_dbm");
                Double sure = getDouble(r, "sure_sn");

                points.add(new TacticalWaveformTimelineDto.TimelinePoint(
                    (String) r.get("comint_id"),
                    inst,
                    formatted,
                    minF, maxF, centerF,
                    null, null, null,
                    null, null, null,
                    amp, sure,
                    (String) r.get("teshis_kimlik"),
                    (String) r.get("cagri_adi"),
                    (String) r.get("haberlesme_sekli"),
                    (String) r.get("modulasyon"),
                    (String) r.get("hedef_yer_bilgisi"),
                    (String) r.get("karsi_cagri_adi")
                ));
            }

            targetLabel = String.format("Muhabere Çağrı Adı: %s", effectiveTargetKey);
        }

        return new TacticalWaveformTimelineDto(
            isRadar ? "RADAR" : "COMINT",
            effectiveTargetKey,
            targetLabel,
            cleanTime,
            points.size(),
            points,
            availableTargets
        );
    }

    public List<TacticalWaveformTimelineDto.AvailableTarget> getAvailableTargets(boolean isRadar) {
        List<TacticalWaveformTimelineDto.AvailableTarget> list = new ArrayList<>();
        if (isRadar) {
            String sql = """
                SELECT spot_no, COALESCE(MAX(radar_adi), 'Radar') AS radar_adi, COUNT(*) AS cnt
                FROM tactical_emissions
                WHERE spot_no IS NOT NULL AND TRIM(spot_no) != ''
                GROUP BY spot_no
                ORDER BY cnt DESC
                LIMIT 25
                """;
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, new MapSqlParameterSource());
            for (Map<String, Object> r : rows) {
                String sp = (String) r.get("spot_no");
                String name = (String) r.get("radar_adi");
                long count = ((Number) r.get("cnt")).longValue();
                list.add(new TacticalWaveformTimelineDto.AvailableTarget(
                    sp,
                    String.format("%s (%s) - %d Temas", sp, name, count),
                    count
                ));
            }
        } else {
            String sql = """
                SELECT cagri_adi, COUNT(*) AS cnt
                FROM tactical_comint
                WHERE cagri_adi IS NOT NULL AND TRIM(cagri_adi) != ''
                GROUP BY cagri_adi
                ORDER BY cnt DESC
                LIMIT 25
                """;
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, new MapSqlParameterSource());
            for (Map<String, Object> r : rows) {
                String cagri = (String) r.get("cagri_adi");
                long count = ((Number) r.get("cnt")).longValue();
                list.add(new TacticalWaveformTimelineDto.AvailableTarget(
                    cagri,
                    String.format("%s - %d Görüşme", cagri, count),
                    count
                ));
            }
        }
        return list;
    }

    private Double getDouble(Map<String, Object> map, String key) {
        Object val = map.get(key);
        if (val instanceof Number n) {
            return Math.round(n.doubleValue() * 100.0) / 100.0;
        }
        return null;
    }

    private Double calculateCenter(Double min, Double max) {
        if (min == null && max == null) return null;
        if (min == null) return max;
        if (max == null) return min;
        return Math.round(((min + max) / 2.0) * 100.0) / 100.0;
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
