package com.c2.ew.emission.service;

import com.c2.ew.emission.dto.TacticalDistributionDto;
import com.c2.ew.emission.infrastructure.repository.TacticalComintJpaRepository;
import com.c2.ew.emission.infrastructure.repository.TacticalEmissionJpaRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class TacticalDistributionService {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final TacticalEmissionJpaRepository emissionRepo;
    private final TacticalComintJpaRepository comintRepo;

    public TacticalDistributionService(
        NamedParameterJdbcTemplate jdbcTemplate,
        TacticalEmissionJpaRepository emissionRepo,
        TacticalComintJpaRepository comintRepo
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.emissionRepo = emissionRepo;
        this.comintRepo = comintRepo;
    }

    public TacticalDistributionDto getDistribution(String source, String criterion, String timeRange, Integer limit) {
        String cleanSource = (source == null || source.isBlank()) ? "RADAR" : source.trim().toUpperCase(Locale.ENGLISH);
        String cleanCriterion = (criterion == null || criterion.isBlank()) ? "TESHIS" : criterion.trim().toUpperCase(Locale.ENGLISH);
        String cleanTime = (timeRange == null || timeRange.isBlank()) ? "ALL" : timeRange.trim().toUpperCase(Locale.ENGLISH);
        int maxItems = (limit == null || limit <= 0) ? 8 : Math.min(limit, 20);

        boolean isRadar = cleanSource.contains("RADAR") || cleanSource.contains("ELINT");
        String tableName = isRadar ? "tactical_emissions" : "tactical_comint";

        // Güvenli Kolon ve İfade Eşleştirmesi (SQL Injection Korumalı Whitelist)
        String columnExpr;
        String criterionLabel;

        if (isRadar) {
            switch (cleanCriterion) {
                case "SPOT_NO", "SPOT", "SPOT_NUMARASI" -> { columnExpr = "spot_no"; criterionLabel = "Radar Spot No Dağılımı"; }
                case "POLARIZASYON" -> { columnExpr = "polarizasyon"; criterionLabel = "Radar Polarizasyon Dağılımı"; }
                case "MODULASYON" -> { columnExpr = "modulasyon"; criterionLabel = "Radar Darbe İçi Modülasyon (MOP) Dağılımı"; }
                case "TESHIS", "TESHIS_KIMLIK" -> { columnExpr = "teshis_kimlik"; criterionLabel = "Radar Teşhis / Tehdit Dağılımı"; }
                case "GOREV", "RADAR_GOREVI" -> { columnExpr = "radar_gorevi"; criterionLabel = "Radar Operasyonel Görev Dağılımı"; }
                case "PLATFORM", "PLATFORM_ORTAMI" -> { columnExpr = "platform_ortami"; criterionLabel = "Radar Platform / Konuşlanma Ortamı"; }
                case "HEDEF_YER", "HEDEF_YER_BILGISI", "BOLGE" -> { columnExpr = "hedef_yer_bilgisi"; criterionLabel = "Radar Hedef Yer / Sektör Dağılımı"; }
                case "MEVZI", "HEDEF_MEVZI", "HEDEF_MEVZI_BILGISI" -> { columnExpr = "hedef_mevzi_bilgisi"; criterionLabel = "Radar Hedef Mevzi Bilgisi"; }
                case "HEDEF_KAYNAGI" -> { columnExpr = "hedef_kaynagi"; criterionLabel = "Radar Hedef / İstihbarat Kaynağı"; }
                case "VERI_KAYNAGI" -> { columnExpr = "veri_kaynagi"; criterionLabel = "Radar Veri Giriş Kaynağı (Kuvvet)"; }
                case "PULSE_CW" -> { columnExpr = "pulse_cw"; criterionLabel = "Radar Pulse / CW Yayın Dağılımı"; }
                case "ET_DURUMU" -> { columnExpr = "et_uygulama_durumu"; criterionLabel = "Elektronik Taarruz (ET) Uygulama Durumu"; }
                case "BANT", "FREKANS_BANDI" -> {
                    columnExpr = "CASE " +
                        "WHEN min_frekans_mhz < 1000 THEN 'UHF / VHF (<1 GHz)' " +
                        "WHEN min_frekans_mhz >= 1000 AND min_frekans_mhz < 2000 THEN 'L-Band (1-2 GHz)' " +
                        "WHEN min_frekans_mhz >= 2000 AND min_frekans_mhz < 4000 THEN 'S-Band (2-4 GHz)' " +
                        "WHEN min_frekans_mhz >= 4000 AND min_frekans_mhz < 8000 THEN 'C-Band (4-8 GHz)' " +
                        "WHEN min_frekans_mhz >= 8000 AND min_frekans_mhz < 12000 THEN 'X-Band (8-12 GHz)' " +
                        "WHEN min_frekans_mhz >= 12000 AND min_frekans_mhz < 18000 THEN 'Ku-Band (12-18 GHz)' " +
                        "ELSE 'K/Ka-Band (>18 GHz)' END";
                    criterionLabel = "NATO Radar Frekans Bandı Dağılımı";
                }
                default -> { columnExpr = "teshis_kimlik"; criterionLabel = "Radar Teşhis Dağılımı"; }
            }
        } else {
            // COMINT
            switch (cleanCriterion) {
                case "CAGRI_ADI", "CAGRI", "CALLSIGN" -> { columnExpr = "cagri_adi"; criterionLabel = "Muhabere Çağrı Adı Dağılımı"; }
                case "HABERLESME_SEKLI", "HABERLESME" -> { columnExpr = "haberlesme_sekli"; criterionLabel = "Muhabere Haberleşme Şekli Dağılımı"; }
                case "LISAN", "DIL" -> { columnExpr = "lisan"; criterionLabel = "Muhabere Lisan Dağılımı"; }
                case "BANT", "TIP", "FREKANS_BANDI" -> { columnExpr = "tip"; criterionLabel = "Telsiz Frekans Bandı (VHF/HF)"; }
                case "MODULASYON" -> { columnExpr = "modulasyon"; criterionLabel = "Muhabere Modülasyon Dağılımı"; }
                case "TESHIS", "TESHIS_KIMLIK" -> { columnExpr = "teshis_kimlik"; criterionLabel = "Muhabere Teşhis / Tehdit Dağılımı"; }
                case "PROTOKOL" -> { columnExpr = "protokol"; criterionLabel = "Dijital Muhabere Protokolü"; }
                case "CALISMA_SEKLI" -> { columnExpr = "calisma_sekli"; criterionLabel = "Telsiz Çalışma Şekli (Duplex/Röle)"; }
                case "HEDEF_YER", "HEDEF_YER_BILGISI", "BOLGE" -> { columnExpr = "hedef_yer_bilgisi"; criterionLabel = "Muhabere Hedef Yer / Sektör Dağılımı"; }
                case "HEDEF_KAYNAGI" -> { columnExpr = "hedef_kaynagi"; criterionLabel = "Muhabere Hedef / İstihbarat Kaynağı"; }
                case "VERI_KAYNAGI" -> { columnExpr = "veri_kaynagi"; criterionLabel = "Muhabere Veri Giriş Kaynağı (Kuvvet)"; }
                default -> { columnExpr = "haberlesme_sekli"; criterionLabel = "Muhabere Haberleşme Şekli Dağılımı"; }
            }
        }

        // Zaman Filtresi Hesaplama
        MapSqlParameterSource params = new MapSqlParameterSource();
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
                whereClause.append(" WHERE son_tespit_zamani >= :start AND son_tespit_zamani <= :end ");
                params.addValue("start", java.sql.Timestamp.from(start));
                params.addValue("end", java.sql.Timestamp.from(refTime));
            }
        }

        // Sorgu: Kategoriye göre sayım
        String sql = String.format(
            "SELECT COALESCE(NULLIF(TRIM(CAST(%s AS text)), ''), 'TANIMSIZ') AS kategori, COUNT(*) AS adet " +
            "FROM %s %s " +
            "GROUP BY 1 " +
            "ORDER BY adet DESC",
            columnExpr, tableName, whereClause
        );

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql, params);

        long totalCount = 0;
        for (Map<String, Object> r : rows) {
            totalCount += ((Number) r.get("adet")).longValue();
        }

        List<TacticalDistributionDto.Item> items = new ArrayList<>();
        long otherCount = 0;

        for (int i = 0; i < rows.size(); i++) {
            Map<String, Object> r = rows.get(i);
            String kat = (String) r.get("kategori");
            long adet = ((Number) r.get("adet")).longValue();

            if (i < maxItems) {
                double pct = (totalCount > 0) ? Math.round((adet * 100.0 / totalCount) * 100.0) / 100.0 : 0.0;
                items.add(new TacticalDistributionDto.Item(kat, adet, pct));
            } else {
                otherCount += adet;
            }
        }

        if (otherCount > 0) {
            double otherPct = (totalCount > 0) ? Math.round((otherCount * 100.0 / totalCount) * 100.0) / 100.0 : 0.0;
            items.add(new TacticalDistributionDto.Item("DİĞER", otherCount, otherPct));
        }

        return new TacticalDistributionDto(
            isRadar ? "RADAR" : "COMINT",
            cleanCriterion,
            criterionLabel,
            cleanTime,
            totalCount,
            items
        );
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
