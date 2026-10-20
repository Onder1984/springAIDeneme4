package com.c2.ew.emission.service;

import com.c2.ew.emission.dto.RadarSpectrumOccupancyDto;
import com.c2.ew.emission.dto.RadarSpectrumOccupancyDto.BandOccupancyItem;
import com.c2.ew.emission.dto.RadarSpectrumOccupancyDto.IdentityBandDetail;
import com.c2.ew.emission.infrastructure.repository.TacticalEmissionJpaRepository;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Service
public class RadarSpectrumOccupancyService {

    private final NamedParameterJdbcTemplate jdbcTemplate;
    private final TacticalEmissionJpaRepository emissionRepo;

    public static final List<String> ALL_AVAILABLE_IDENTITIES = List.of(
        "DUSMAN", "MUHTEMEL_DUSMAN", "BILINMEYEN", "DOST", "TARAFSIZ"
    );

    private record BandDef(String id, String name, String description, double minFreq, double maxFreq) {}

    public static String getBandDescription(double minFreq, double maxFreq) {
        double center = (minFreq + maxFreq) / 2.0;
        if (center < 300.0) {
            return "VHF (Erken İhbar / Geniş Alan Arama)";
        } else if (center < 1000.0) {
            return "UHF (Hava Savunma Erken İhbar)";
        } else if (center < 2000.0) {
            return "L-Bant (Uzun Menzil Arama / Gözetleme)";
        } else if (center < 4000.0) {
            return "S-Bant (Orta Menzil Arama / Takip Radarı)";
        } else if (center < 8000.0) {
            return "C-Bant (Hava Savunma / Takip / Güdüm)";
        } else if (center < 12000.0) {
            return "X-Bant (Atış Kontrol / Füze Güdüm / Tehdit)";
        } else if (center < 18000.0) {
            return "Ku-Bant (Hassas Takip / Silah Arama)";
        } else {
            return "K/Ka-Bant (Milimetrik Dalga Hedefleme)";
        }
    }

    public RadarSpectrumOccupancyService(
        NamedParameterJdbcTemplate jdbcTemplate,
        TacticalEmissionJpaRepository emissionRepo
    ) {
        this.jdbcTemplate = jdbcTemplate;
        this.emissionRepo = emissionRepo;
    }

    public RadarSpectrumOccupancyDto calculateOccupancy(String timeRange, String teshisKimlik) {
        return calculateOccupancy(timeRange, teshisKimlik, 50.0, true);
    }

    public RadarSpectrumOccupancyDto calculateOccupancy(String timeRange, String teshisKimlik, Double binStepMhz, Boolean onlyActive) {
        // 1. Teşhis kimliklerini normalize et
        List<String> selectedIdentities = parseIdentities(teshisKimlik);

        // 2. Zaman kısıtını belirle
        String cleanTime = (timeRange == null || timeRange.isBlank()) ? "SON_24_SAAT" : timeRange.trim().toUpperCase(Locale.ENGLISH);
        Instant refTime = getRadarRefTime();
        Instant start = null;

        String timeLabel = "Tüm Zamanlar";
        if (cleanTime.contains("SON_1_SAAT") || cleanTime.contains("1_SAAT")) {
            start = refTime.minus(Duration.ofHours(1));
            timeLabel = "Son 1 Saat";
        } else if (cleanTime.contains("SON_6_SAAT") || cleanTime.contains("6_SAAT")) {
            start = refTime.minus(Duration.ofHours(6));
            timeLabel = "Son 6 Saat";
        } else if (cleanTime.contains("SON_24_SAAT") || cleanTime.contains("24_SAAT") || cleanTime.contains("1_GUN") || cleanTime.contains("BUGUN")) {
            start = refTime.minus(Duration.ofHours(24));
            timeLabel = "Son 24 Saat (1 Gün)";
        } else if (cleanTime.contains("7_GUN") || cleanTime.contains("HAFTA")) {
            start = refTime.minus(Duration.ofDays(7));
            timeLabel = "Son 1 Hafta";
        } else if (cleanTime.equalsIgnoreCase("ALL") || cleanTime.equalsIgnoreCase("TUMU")) {
            start = null;
            timeLabel = "Tüm Taktik Zamanlar";
        } else {
            // Varsayılan son 24 saat
            start = refTime.minus(Duration.ofHours(24));
            timeLabel = "Son 24 Saat (1 Gün)";
        }

        // 3. Veritabanından ilgili yayınları çek
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("identities", selectedIdentities);

        StringBuilder sql = new StringBuilder("""
            SELECT min_frekans_mhz, max_frekans_mhz, teshis_kimlik, radar_adi
            FROM tactical_emissions
            WHERE teshis_kimlik IN (:identities)
              AND min_frekans_mhz IS NOT NULL AND max_frekans_mhz IS NOT NULL
            """);

        if (start != null) {
            sql.append(" AND son_tespit_zamani >= :start AND son_tespit_zamani <= :end ");
            params.addValue("start", Timestamp.from(start));
            params.addValue("end", Timestamp.from(refTime));
        }

        List<Map<String, Object>> rows = jdbcTemplate.queryForList(sql.toString(), params);

        // Kayıtları nesneleştir
        record EmissionRecord(double minF, double maxF, String identity, String radarName) {}
        List<EmissionRecord> records = new ArrayList<>(rows.size());
        for (Map<String, Object> r : rows) {
            Number minVal = (Number) r.get("min_frekans_mhz");
            Number maxVal = (Number) r.get("max_frekans_mhz");
            if (minVal == null || maxVal == null) continue;
            double minF = minVal.doubleValue();
            double maxF = maxVal.doubleValue();
            if (minF > maxF) {
                double temp = minF;
                minF = maxF;
                maxF = temp;
            }
            String ident = (String) r.get("teshis_kimlik");
            String rName = (String) r.get("radar_adi");
            records.add(new EmissionRecord(minF, maxF, ident, rName != null ? rName : "Bilinmeyen Radar"));
        }

        // 4. Dinamik ve dar frekans dilimlerini oluştur (Varsayılan 50 MHz adım)
        double step = (binStepMhz != null && binStepMhz > 0.0) ? binStepMhz : 50.0;
        boolean activeOnly = (onlyActive == null) || onlyActive;

        Set<Double> activeBinStarts = new TreeSet<>();
        for (EmissionRecord rec : records) {
            double fStart = Math.floor(rec.minF() / step) * step;
            double fEnd = Math.ceil(rec.maxF() / step) * step;
            for (double f = fStart; f < fEnd; f += step) {
                activeBinStarts.add(f);
            }
        }

        Set<Double> displayStarts = new TreeSet<>(activeBinStarts);
        if (activeOnly) {
            // Spektrum analizörü eğrisi için aktif kanalların hemen öncesine ve sonrasına 1 koruma kanalı ekle
            for (Double f : activeBinStarts) {
                if (f - step >= 0) displayStarts.add(f - step);
                displayStarts.add(f + step);
            }
        }

        if (displayStarts.isEmpty()) {
            displayStarts.addAll(List.of(150.0, 4500.0, 9200.0));
        }

        List<BandDef> bandsToProcess = new ArrayList<>();
        for (double startF : displayStarts) {
            double endF = startF + step;
            String id = String.format(Locale.ENGLISH, "BIN_%d_%d", (long) startF, (long) endF);
            String name = String.format(Locale.ENGLISH, "%.0f - %.0f MHz", startF, endF);
            String desc = getBandDescription(startF, endF);
            bandsToProcess.add(new BandDef(id, name, desc, startF, endF));
        }

        // 5. Sabit frekans dilimlerine göre doluluk oranlarını hesapla (Interval Union)
        List<BandOccupancyItem> bandItems = new ArrayList<>();

        for (BandDef band : bandsToProcess) {
            double bandMin = band.minFreq();
            double bandMax = band.maxFreq();
            double bandWidth = bandMax - bandMin;

            List<double[]> allBandIntervals = new ArrayList<>();
            long totalEmissionCount = 0;

            Map<String, IdentityBandDetail> identityDetails = new LinkedHashMap<>();

            for (String identity : selectedIdentities) {
                List<double[]> identityIntervals = new ArrayList<>();
                long identityCount = 0;
                Map<String, Integer> radarFreqMap = new HashMap<>();

                for (EmissionRecord rec : records) {
                    if (identity.equalsIgnoreCase(rec.identity())) {
                        // Kesişim kontrolü
                        if (rec.maxF() > bandMin && rec.minF() < bandMax) {
                            double clipStart = Math.max(bandMin, rec.minF());
                            double clipEnd = Math.min(bandMax, rec.maxF());
                            if (clipEnd > clipStart) {
                                identityIntervals.add(new double[]{clipStart, clipEnd});
                                allBandIntervals.add(new double[]{clipStart, clipEnd});
                                identityCount++;
                                totalEmissionCount++;
                                radarFreqMap.put(rec.radarName(), radarFreqMap.getOrDefault(rec.radarName(), 0) + 1);
                            }
                        }
                    }
                }

                // Bu teşhis için Interval Union
                double occupiedMhz = mergeIntervalsAndSum(identityIntervals);
                double occupancyPct = (bandWidth > 0) ? Math.min(100.0, Math.round((occupiedMhz / bandWidth * 100.0) * 10.0) / 10.0) : 0.0;

                // En çok tespit edilen radar isimleri
                List<String> topRadars = radarFreqMap.entrySet().stream()
                    .sorted((e1, e2) -> Integer.compare(e2.getValue(), e1.getValue()))
                    .limit(4)
                    .map(e -> e.getKey())
                    .toList();

                identityDetails.put(identity, new IdentityBandDetail(
                    identity,
                    occupiedMhz,
                    occupancyPct,
                    identityCount,
                    topRadars
                ));
            }

            // Tüm seçili teşhislerin birleşik net doluluğu
            double totalOccupiedMhz = mergeIntervalsAndSum(allBandIntervals);
            double totalOccupancyPercent = (bandWidth > 0) ? Math.min(100.0, Math.round((totalOccupiedMhz / bandWidth * 100.0) * 10.0) / 10.0) : 0.0;

            bandItems.add(new BandOccupancyItem(
                band.id(),
                band.name(),
                band.description(),
                bandMin,
                bandMax,
                bandWidth,
                totalOccupiedMhz,
                totalOccupancyPercent,
                totalEmissionCount,
                identityDetails
            ));
        }

        // 6. Kokpit sohbeti için Markdown Tablosu oluştur
        String summaryMarkdown = buildSummaryMarkdown(timeLabel, selectedIdentities, bandItems, step);

        return new RadarSpectrumOccupancyDto(
            cleanTime,
            timeLabel,
            selectedIdentities,
            ALL_AVAILABLE_IDENTITIES,
            bandItems,
            summaryMarkdown
        );
    }

    /**
     * Kesişen aralıkların birleşimini (Interval Union) hesaplar ve mükerrer saymadan toplam MHz genişliğini döner.
     */
    private double mergeIntervalsAndSum(List<double[]> intervals) {
        if (intervals == null || intervals.isEmpty()) return 0.0;

        // Başlangıç frekansına göre sırala
        intervals.sort(Comparator.comparingDouble((double[] a) -> a[0]).thenComparingDouble(a -> a[1]));

        List<double[]> merged = new ArrayList<>();
        double[] current = intervals.get(0).clone();

        for (int i = 1; i < intervals.size(); i++) {
            double[] next = intervals.get(i);
            if (next[0] <= current[1]) {
                // Çakışma var: aralığı genişlet
                current[1] = Math.max(current[1], next[1]);
            } else {
                // Çakışma yok: mevcut aralığı ekle ve yeni aralığa geç
                merged.add(current);
                current = next.clone();
            }
        }
        merged.add(current);

        double total = 0.0;
        for (double[] interval : merged) {
            total += (interval[1] - interval[0]);
        }

        return Math.round(total * 100.0) / 100.0;
    }

    private List<String> parseIdentities(String teshisKimlik) {
        if (teshisKimlik == null || teshisKimlik.isBlank()) {
            return List.of("DUSMAN");
        }
        String clean = teshisKimlik.trim().toUpperCase(Locale.ENGLISH)
            .replace("Ç", "C").replace("Ğ", "G").replace("İ", "I").replace("I", "I")
            .replace("Ö", "O").replace("Ş", "S").replace("Ü", "U");

        if (clean.contains("ALL") || clean.contains("TUMU") || clean.contains("HEPSI")) {
            return ALL_AVAILABLE_IDENTITIES;
        }

        String[] parts = clean.split("[,;\\s]+");
        Set<String> set = new LinkedHashSet<>();
        for (String p : parts) {
            String token = p.trim();
            if (token.equalsIgnoreCase("DUSMAN") || token.equalsIgnoreCase("ENEMY")) {
                set.add("DUSMAN");
            } else if (token.equalsIgnoreCase("MUHTEMEL_DUSMAN") || token.contains("MUHTEMEL")) {
                set.add("MUHTEMEL_DUSMAN");
            } else if (token.equalsIgnoreCase("BILINMEYEN") || token.equalsIgnoreCase("UNKNOWN")) {
                set.add("BILINMEYEN");
            } else if (token.equalsIgnoreCase("DOST") || token.equalsIgnoreCase("FRIEND")) {
                set.add("DOST");
            } else if (token.equalsIgnoreCase("TARAFSIZ") || token.equalsIgnoreCase("NEUTRAL")) {
                set.add("TARAFSIZ");
            }
        }

        if (set.isEmpty()) {
            set.add("DUSMAN");
        }
        return new ArrayList<>(set);
    }

    private String buildSummaryMarkdown(String timeLabel, List<String> identities, List<BandOccupancyItem> bands, double step) {
        StringBuilder sb = new StringBuilder();
        sb.append("### 📡 Radar Frekans Spektrum Doluluk & Kullanım Yoğunluğu Analizi\n\n");
        sb.append("**Zaman Penceresi:** ").append(timeLabel).append("  \n");
        sb.append("**İncelenen Teşhis:** ").append(String.join(", ", identities)).append("  \n");
        sb.append(String.format(Locale.ENGLISH, "**Kanal Çözünürlüğü:** %.0f MHz Dar Aralıklar\n\n", step));

        List<BandOccupancyItem> activeBands = bands.stream()
            .filter(b -> b.totalOccupancyPercent() > 0.0 || b.totalEmissionCount() > 0)
            .toList();

        if (activeBands.isEmpty()) {
            sb.append("> **Bilgilendirme:** Belirtilen zaman penceresinde ve teşhis kimliğinde tespit edilmiş aktif radar emisyonu bulunmamaktadır.\n");
            return sb.toString();
        }

        if (identities.size() == 1) {
            String singleId = identities.get(0);
            sb.append("| Sabit Frekans Aralığı | Taktik Bant & Görev | Net Kapsanan (MHz) | Kanal Genişliği (MHz) | Spektrum Doluluk Oranı (%) | Yayın Sayısı | Tespit Edilen Başlıca Radarlar |\n");
            sb.append("| :--- | :--- | :---: | :---: | :---: | :---: | :--- |\n");

            for (BandOccupancyItem b : activeBands) {
                IdentityBandDetail det = b.identityDetails().get(singleId);
                double occMhz = (det != null) ? det.occupiedMhz() : 0.0;
                double occPct = (det != null) ? det.occupancyPercent() : 0.0;
                long count = (det != null) ? det.emissionCount() : 0L;
                String radars = (det != null && !det.topRadars().isEmpty()) ? String.join(", ", det.topRadars()) : "-";

                sb.append(String.format(Locale.ENGLISH,
                    "| **%s** | %s | %.1f MHz | %.1f MHz | **%%%s** | %d | %s |\n",
                    b.bandName(), b.bandDescription(), occMhz, b.totalBandwidthMhz(), formatPct(occPct), count, radars
                ));
            }
        } else {
            // Çoklu Teşhis Durumunda Detaylı Tablo
            sb.append("| Sabit Frekans Aralığı | Taktik Bant | Teşhis Kimliği | Net Kapsanan (MHz) | Doluluk Oranı (%) | Yayın Sayısı | Başlıca Radarlar |\n");
            sb.append("| :--- | :--- | :--- | :---: | :---: | :---: | :--- |\n");

            for (BandOccupancyItem b : activeBands) {
                boolean first = true;
                for (String id : identities) {
                    IdentityBandDetail det = b.identityDetails().get(id);
                    double occMhz = (det != null) ? det.occupiedMhz() : 0.0;
                    double occPct = (det != null) ? det.occupancyPercent() : 0.0;
                    long count = (det != null) ? det.emissionCount() : 0L;
                    String radars = (det != null && !det.topRadars().isEmpty()) ? String.join(", ", det.topRadars()) : "-";

                    sb.append(String.format(Locale.ENGLISH,
                        "| %s | %s | `%s` | %.1f MHz | **%%%s** | %d | %s |\n",
                        first ? "**" + b.bandName() + "**" : "\"",
                        first ? b.bandDescription() : "\"",
                        id, occMhz, formatPct(occPct), count, radars
                    ));
                    first = false;
                }
            }
        }

        sb.append(String.format(Locale.ENGLISH,
            "\n> **Taktik Bilgilendirme:** Frekans aralıkları **%.0f MHz** yüksek çözünürlüklü dar kanallara bölünmüştür. Eşzamanlı olarak sağ panelde interaktif **Radar Frekans Spektrum Doluluk Bar Grafiği** açılmıştır. Kanal çözünürlüğü ve teşhis kırılımları çubuk grafikten incelenebilir.\n",
            step
        ));
        return sb.toString();
    }

    private String formatPct(double pct) {
        return String.format(Locale.ENGLISH, "%.1f", pct).replace(".", ",");
    }

    private Instant getRadarRefTime() {
        try {
            Instant max = emissionRepo.findMaxSonTespitZamani();
            if (max != null) return max;
        } catch (Exception ignored) {}
        return Instant.now();
    }
}
