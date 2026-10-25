package com.c2.ew.emission.service;

import com.c2.ew.emission.dto.TacticalTrackDto;
import com.c2.ew.emission.dto.TacticalWaveformTimelineDto;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Taktik Kinematik Hedef İntikal ve Konum Geçmişi İzi Servisi.
 * Radar ve Muhabere hedeflerinin zaman serisi konum kestirimlerini
 * vektörel intikal rotası, mesafe, hız ve yön analizi ile hesaplar.
 */
@Service
public class TacticalTrackService {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final TacticalWaveformTimelineService timelineService;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss")
            .withZone(ZoneId.of("Europe/Istanbul"));

    public TacticalTrackService(NamedParameterJdbcTemplate jdbcTemplate, TacticalWaveformTimelineService timelineService) {
        this.jdbcTemplate = jdbcTemplate;
        this.timelineService = timelineService;
    }

    public TacticalTrackDto getTargetTrack(String source, String targetKey, String timeRange, Integer limit) {
        int effectiveLimit = (limit != null && limit > 0) ? Math.min(limit, 300) : 100;
        String cleanSource = (source != null && !source.isBlank()) ? source.trim().toUpperCase() : "AUTO";
        String cleanKey = (targetKey != null) ? targetKey.trim() : "";

        // 1. Hedef ve Kaynak Otomatik Tespiti (Akıllı Çözümleme)
        boolean isRadar = true;
        if ("COMINT".equals(cleanSource) || "MUHABERE".equals(cleanSource)) {
            isRadar = false;
        } else if ("RADAR".equals(cleanSource)) {
            isRadar = true;
        } else {
            // AUTO: Hedef anahtarından kaynağı tahmin et
            if (cleanKey.toUpperCase().startsWith("VOLGA") ||
                cleanKey.toUpperCase().startsWith("OMAR") ||
                cleanKey.toUpperCase().startsWith("KARTAL") ||
                cleanKey.toUpperCase().startsWith("HERMES") ||
                cleanKey.toUpperCase().startsWith("NATO") ||
                cleanKey.toUpperCase().startsWith("COM-")) {
                isRadar = false;
            } else {
                isRadar = true;
            }
        }

        // Eğer targetKey bir ID ise (EMI-LIVE-XXXX veya COM-LIVE-XXXX), veritabanından hedefi bul
        if (cleanKey.toUpperCase().startsWith("EMI-") || cleanKey.toUpperCase().startsWith("CLUSTER-")) {
            try {
                String sqlFind = "SELECT spot_no, radar_adi FROM tactical_emissions WHERE UPPER(emission_id) = UPPER(:id) LIMIT 1";
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(sqlFind, new MapSqlParameterSource("id", cleanKey));
                if (!rows.isEmpty()) {
                    String spot = (String) rows.get(0).get("spot_no");
                    String rName = (String) rows.get(0).get("radar_adi");
                    cleanKey = (spot != null && !spot.isBlank()) ? spot : (rName != null ? rName : cleanKey);
                    isRadar = true;
                }
            } catch (Exception ignored) {}
        } else if (cleanKey.toUpperCase().startsWith("COM-")) {
            try {
                String sqlFind = "SELECT cagri_adi FROM tactical_comint WHERE UPPER(comint_id) = UPPER(:id) LIMIT 1";
                List<Map<String, Object>> rows = jdbcTemplate.queryForList(sqlFind, new MapSqlParameterSource("id", cleanKey));
                if (!rows.isEmpty()) {
                    String cagri = (String) rows.get(0).get("cagri_adi");
                    if (cagri != null && !cagri.isBlank()) cleanKey = cagri;
                    isRadar = false;
                }
            } catch (Exception ignored) {}
        }

        List<TacticalWaveformTimelineDto.AvailableTarget> availableTargets = timelineService.getAvailableTargets(isRadar);

        // Eğer anahtar boşsa en yoğun hedeften başla
        if (cleanKey.isBlank() && !availableTargets.isEmpty()) {
            cleanKey = availableTargets.get(0).key();
        }

        // 2. Zaman Aralığı Süzgeci
        StringBuilder timeFilter = new StringBuilder();
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("key", cleanKey);
        params.addValue("limit", effectiveLimit);

        if (timeRange != null && !timeRange.isBlank() && !"ALL".equalsIgnoreCase(timeRange) && !"TUMU".equalsIgnoreCase(timeRange)) {
            Instant refTime = Instant.now();
            Instant start = null;
            if (timeRange.contains("1_SAAT")) start = refTime.minus(Duration.ofHours(1));
            else if (timeRange.contains("6_SAAT")) start = refTime.minus(Duration.ofHours(6));
            else if (timeRange.contains("24_SAAT")) start = refTime.minus(Duration.ofHours(24));
            else if (timeRange.contains("7_GUN") || timeRange.contains("HAFTA")) start = refTime.minus(Duration.ofDays(7));

            if (start != null) {
                timeFilter.append(" AND son_tespit_zamani >= :start AND son_tespit_zamani <= :end ");
                params.addValue("start", Timestamp.from(start));
                params.addValue("end", Timestamp.from(refTime));
            }
        }

        // 3. Kronolojik Kestirim Sorgusu
        List<Map<String, Object>> rows = new ArrayList<>();
        String dominantTargetName = null;

        if (isRadar) {
            String sql = """
                SELECT emission_id, son_tespit_zamani, yayin_enlem, yayin_boylam,
                       yayin_semi_major_meters, yayin_semi_minor_meters, yayin_orientation_degrees,
                       teshis_kimlik, radar_adi, spot_no, min_frekans_mhz, max_frekans_mhz,
                       min_pri_micro_sec, min_pw_micro_sec, modulasyon, hedef_mevzi_bilgisi
                FROM tactical_emissions
                WHERE (
                    UPPER(spot_no) = UPPER(:key)
                    OR UPPER(radar_adi) LIKE UPPER(CONCAT('%', :key, '%'))
                    OR UPPER(elint_notasyonu) = UPPER(:key)
                )
                AND yayin_enlem IS NOT NULL AND yayin_boylam IS NOT NULL
                """ + timeFilter + """
                ORDER BY son_tespit_zamani ASC
                LIMIT :limit
                """;
            rows = jdbcTemplate.queryForList(sql, params);
        } else {
            String sql = """
                SELECT comint_id, son_tespit_zamani, yayin_enlem, yayin_boylam,
                       yayin_semi_major_meters, yayin_semi_minor_meters, yayin_orientation_degrees,
                       teshis_kimlik, cagri_adi, min_frekans_mhz, max_frekans_mhz,
                       modulasyon, haberlesme_sekli, hedef_yer_bilgisi
                FROM tactical_comint
                WHERE (
                    UPPER(cagri_adi) = UPPER(:key)
                    OR UPPER(karsi_cagri_adi) = UPPER(:key)
                )
                AND yayin_enlem IS NOT NULL AND yayin_boylam IS NOT NULL
                """ + timeFilter + """
                ORDER BY son_tespit_zamani ASC
                LIMIT :limit
                """;
            rows = jdbcTemplate.queryForList(sql, params);
        }

        // 4. Noktaların İnşası ve Kinematik Metriklerin Hesaplanması
        List<TacticalTrackDto.TrackPoint> points = new ArrayList<>();
        double totalDistanceKm = 0.0;
        Double prevLat = null;
        Double prevLon = null;
        Instant prevTime = null;

        int seq = 1;
        for (Map<String, Object> r : rows) {
            String id = isRadar ? (String) r.get("emission_id") : (String) r.get("comint_id");
            Timestamp ts = (Timestamp) r.get("son_tespit_zamani");
            Instant inst = ts != null ? ts.toInstant() : Instant.now();
            String formatted = TIME_FORMATTER.format(inst);

            Double lat = getDouble(r, "yayin_enlem");
            Double lon = getDouble(r, "yayin_boylam");
            Double sMaj = getDouble(r, "yayin_semi_major_meters");
            Double sMin = getDouble(r, "yayin_semi_minor_meters");
            Double azim = getDouble(r, "yayin_orientation_degrees");

            String teshis = (String) r.get("teshis_kimlik");
            String hedefAdi = isRadar ? (String) r.get("radar_adi") : (String) r.get("cagri_adi");
            String spotOrCall = isRadar ? (String) r.get("spot_no") : (String) r.get("cagri_adi");
            String mevzi = isRadar ? (String) r.get("hedef_mevzi_bilgisi") : (String) r.get("hedef_yer_bilgisi");

            if (dominantTargetName == null && hedefAdi != null) {
                dominantTargetName = hedefAdi;
            }

            Double minF = getDouble(r, "min_frekans_mhz");
            Double maxF = getDouble(r, "max_frekans_mhz");
            Double pri = isRadar ? getDouble(r, "min_pri_micro_sec") : null;
            Double pw = isRadar ? getDouble(r, "min_pw_micro_sec") : null;
            String mod = (String) r.get("modulasyon");

            Double distFromPrev = null;
            Double bearingFromPrev = null;
            Double speedFromPrev = null;

            if (prevLat != null && prevLon != null && lat != null && lon != null) {
                distFromPrev = round(haversineKm(prevLat, prevLon, lat, lon), 2);
                bearingFromPrev = round(calculateBearingDeg(prevLat, prevLon, lat, lon), 1);
                totalDistanceKm += distFromPrev;

                if (prevTime != null && inst.isAfter(prevTime)) {
                    double hours = Duration.between(prevTime, inst).toSeconds() / 3600.0;
                    if (hours > 0.001) {
                        speedFromPrev = round(distFromPrev / hours, 1);
                    }
                }
            }

            points.add(new TacticalTrackDto.TrackPoint(
                id,
                seq++,
                lat,
                lon,
                sMaj,
                sMin,
                azim,
                inst,
                formatted,
                teshis,
                hedefAdi,
                spotOrCall,
                minF,
                maxF,
                pri,
                pw,
                mod,
                distFromPrev,
                bearingFromPrev,
                speedFromPrev,
                mevzi
            ));

            prevLat = lat;
            prevLon = lon;
            prevTime = inst;
        }

        // 5. Genel Özet İstatistikleri
        Double timeSpanHours = null;
        Double avgSpeedKmh = null;
        Double overallHeadingDeg = null;
        String overallHeadingCardinal = "-";
        String firstDetectedFormatted = "-";
        String lastDetectedFormatted = "-";

        if (!points.isEmpty()) {
            firstDetectedFormatted = points.get(0).timestampFormatted();
            lastDetectedFormatted = points.get(points.size() - 1).timestampFormatted();

            TacticalTrackDto.TrackPoint firstPt = points.get(0);
            TacticalTrackDto.TrackPoint lastPt = points.get(points.size() - 1);

            if (points.size() >= 2 && firstPt.timestamp() != null && lastPt.timestamp() != null) {
                long durationSeconds = Math.max(0, Duration.between(firstPt.timestamp(), lastPt.timestamp()).toSeconds());
                timeSpanHours = round(durationSeconds / 3600.0, 2);

                if (timeSpanHours > 0.01 && totalDistanceKm > 0) {
                    avgSpeedKmh = round(totalDistanceKm / timeSpanHours, 1);
                }

                if (firstPt.enlem() != null && firstPt.boylam() != null && lastPt.enlem() != null && lastPt.boylam() != null) {
                    overallHeadingDeg = round(calculateBearingDeg(firstPt.enlem(), firstPt.boylam(), lastPt.enlem(), lastPt.boylam()), 1);
                    overallHeadingCardinal = getCardinalDirection(overallHeadingDeg);
                }
            }
        }

        String targetLabel = cleanKey;
        if (dominantTargetName != null) {
            targetLabel = String.format("%s [%s]", dominantTargetName, cleanKey);
        } else if (isRadar) {
            targetLabel = String.format("Radar Hedefi: %s", cleanKey);
        } else {
            targetLabel = String.format("Telsiz Çağrı Adı: %s", cleanKey);
        }

        return new TacticalTrackDto(
            isRadar ? "RADAR" : "COMINT",
            cleanKey,
            targetLabel,
            points.size(),
            round(totalDistanceKm, 2),
            timeSpanHours,
            avgSpeedKmh,
            overallHeadingDeg,
            overallHeadingCardinal,
            firstDetectedFormatted,
            lastDetectedFormatted,
            points,
            availableTargets
        );
    }

    public static double haversineKm(double lat1, double lon1, double lat2, double lon2) {
        final double R = 6371.0;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) +
                   Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) *
                   Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return R * c;
    }

    public static double calculateBearingDeg(double lat1, double lon1, double lat2, double lon2) {
        double phi1 = Math.toRadians(lat1);
        double phi2 = Math.toRadians(lat2);
        double deltaLambda = Math.toRadians(lon2 - lon1);
        double y = Math.sin(deltaLambda) * Math.cos(phi2);
        double x = Math.cos(phi1) * Math.sin(phi2) -
                   Math.sin(phi1) * Math.cos(phi2) * Math.cos(deltaLambda);
        double theta = Math.atan2(y, x);
        return (Math.toDegrees(theta) + 360.0) % 360.0;
    }

    private static String getCardinalDirection(Double deg) {
        if (deg == null) return "-";
        double d = (deg % 360.0 + 360.0) % 360.0;
        if (d >= 337.5 || d < 22.5) return "Kuzey (N)";
        if (d >= 22.5 && d < 67.5) return "Kuzeydoğu (NE)";
        if (d >= 67.5 && d < 112.5) return "Doğu (E)";
        if (d >= 112.5 && d < 157.5) return "Güneydoğu (SE)";
        if (d >= 157.5 && d < 202.5) return "Güney (S)";
        if (d >= 202.5 && d < 247.5) return "Güneybatı (SW)";
        if (d >= 247.5 && d < 292.5) return "Batı (W)";
        return "Kuzeybatı (NW)";
    }

    private Double getDouble(Map<String, Object> map, String key) {
        Object v = map.get(key);
        if (v == null) return null;
        if (v instanceof Number n) return n.doubleValue();
        try {
            return Double.parseDouble(v.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private double round(double value, int places) {
        double scale = Math.pow(10, places);
        return Math.round(value * scale) / scale;
    }
}
