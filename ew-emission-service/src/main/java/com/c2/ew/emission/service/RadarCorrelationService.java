package com.c2.ew.emission.service;

import com.c2.ew.emission.domain.model.AtpEntry;
import com.c2.ew.emission.domain.model.FrequencyEntry;
import com.c2.ew.emission.domain.model.PriEntry;
import com.c2.ew.emission.domain.model.PwEntry;
import com.c2.ew.emission.infrastructure.entity.TacticalEmissionEntity;
import com.c2.ew.emission.infrastructure.repository.TacticalEmissionJpaRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
public class RadarCorrelationService {

    private static final Logger log = LoggerFactory.getLogger(RadarCorrelationService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZoneId.of("Europe/Istanbul"));

    private final TacticalEmissionJpaRepository emissionRepo;
    private final ObjectMapper objectMapper;
    private final NamedParameterJdbcTemplate jdbcTemplate;

    public RadarCorrelationService(
        TacticalEmissionJpaRepository emissionRepo,
        ObjectMapper objectMapper,
        NamedParameterJdbcTemplate jdbcTemplate
    ) {
        this.emissionRepo = emissionRepo;
        this.objectMapper = objectMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    public record SimilarRadarCandidate(
        int rank,
        String id,
        String radarAdi,
        String teshisKimlik,
        String kayitTipi,
        double totalScore,
        double rfScore,
        double timeScore,
        double timeOverlapScore,
        double timeProximityScore,
        double tacticalScore,
        double freqIoU,
        double priIoU,
        double pwIoU,
        double timeIoU,
        String timeProximityDesc,
        String sonTespitZamaniFormatted,
        String spotNo,
        String hedefYerBilgisi,
        String hedefMevziBilgisi,
        String matchSummary
    ) {}

    public record IntervalMatch(
        boolean hasOverlap,
        double matchRatio, // 0.0 - 1.0 (Kesisim / Birlesim)
        double intersectionLength,
        double unionLength,
        Double gapDistance, // Ayrık ise aralarındaki fark
        String description
    ) {}

    public record SubtypeMatch(
        String tip,
        boolean tipOrtak,
        IntervalMatch intervalMatch,
        String range1Str,
        String range2Str
    ) {}

    public Map<String, Object> compareEmissions(String id1, String id2) {
        log.info("🎯 [RADAR KARŞILAŞTIRMA] Kıyaslama başlatıldı: {} <-> {}", id1, id2);

        String cleanId1 = cleanId(id1);
        String cleanId2 = cleanId(id2);

        Map<String, Object> report = new LinkedHashMap<>();
        if (cleanId1 == null || cleanId2 == null) {
            report.put("durum", "HATA");
            report.put("mesaj", "Karşılaştırma için geçerli iki radar/LOB ID'si girilmelidir.");
            return report;
        }

        Optional<TacticalEmissionEntity> opt1 = emissionRepo.findById(cleanId1);
        Optional<TacticalEmissionEntity> opt2 = emissionRepo.findById(cleanId2);

        if (opt1.isEmpty() || opt2.isEmpty()) {
            report.put("durum", "HATA");
            report.put("mesaj", String.format("Radar kayıtları bulunamadı: [%s: %s], [%s: %s]",
                cleanId1, opt1.isPresent() ? "MEVCUT" : "BULUNAMADI",
                cleanId2, opt2.isPresent() ? "MEVCUT" : "BULUNAMADI"));
            return report;
        }

        TacticalEmissionEntity e1 = opt1.get();
        TacticalEmissionEntity e2 = opt2.get();

        // 1. ZAMAN UYUMU
        IntervalMatch zamanUyumu = compareTimeIntervals(e1.getIlkTespitZamani(), e1.getSonTespitZamani(), e2.getIlkTespitZamani(), e2.getSonTespitZamani());

        // 2. FREKANS UYUMU (Zarf + Alt Tipler)
        IntervalMatch frekansZarfUyumu = compareNumericInterval(e1.getMinFrekansMhz(), e1.getMaxFrekansMhz(), e2.getMinFrekansMhz(), e2.getMaxFrekansMhz(), "MHz");
        List<SubtypeMatch> frekansAltTipler = compareFrequencySubtypes(e1.getFrekansListesiJson(), e2.getFrekansListesiJson());

        // 3. PRI UYUMU (Zarf + Alt Tipler)
        IntervalMatch priZarfUyumu = compareNumericInterval(e1.getMinPriMicroSec(), e1.getMaxPriMicroSec(), e2.getMinPriMicroSec(), e2.getMaxPriMicroSec(), "µs");
        List<SubtypeMatch> priAltTipler = comparePriSubtypes(e1.getPriListesiJson(), e2.getPriListesiJson());

        // 4. PW UYUMU (Zarf + Alt Tipler)
        IntervalMatch pwZarfUyumu = compareNumericInterval(e1.getMinPwMicroSec(), e1.getMaxPwMicroSec(), e2.getMinPwMicroSec(), e2.getMaxPwMicroSec(), "µs");
        List<SubtypeMatch> pwAltTipler = comparePwSubtypes(e1.getPwListesiJson(), e2.getPwListesiJson());

        // 5. ATP UYUMU (Zarf + Alt Tipler)
        IntervalMatch atpZarfUyumu = compareNumericInterval(e1.getMinAtpMicroSec(), e1.getMaxAtpMicroSec(), e2.getMinAtpMicroSec(), e2.getMaxAtpMicroSec(), "µs");
        List<SubtypeMatch> atpAltTipler = compareAtpSubtypes(e1.getAtpListesiJson(), e2.getAtpListesiJson());

        // 6. METİNSEL & COĞRAFİ UYUM (Bire bir aynılık üzerinden)
        boolean yerUyumlu = exactMatch(e1.getHedefYerBilgisi(), e2.getHedefYerBilgisi());
        boolean mevziUyumlu = exactMatch(e1.getHedefMevziBilgisi(), e2.getHedefMevziBilgisi());

        // 7. TEŞHİS UYUMU (Bire bir aynılık üzerinden)
        boolean teshisUyumlu = (e1.getTeshisKimlik() != null && e2.getTeshisKimlik() != null && e1.getTeshisKimlik() == e2.getTeshisKimlik());

        // 8. RADAR ADI VE GÖREVİ (Bire bir aynılık üzerinden)
        boolean radarAdiUyumlu = exactMatch(e1.getRadarAdi(), e2.getRadarAdi());
        boolean radarGoreviUyumlu = exactMatch(e1.getRadarGorevi(), e2.getRadarGorevi());

        // 9. PLATFORM ORTAMI VE TİPİ (Bire bir aynılık üzerinden)
        boolean platformOrtamiUyumlu = (e1.getPlatformOrtami() != null && e2.getPlatformOrtami() != null && e1.getPlatformOrtami() == e2.getPlatformOrtami());
        boolean platformTipiUyumlu = (e1.getPlatformTipi() != null && e2.getPlatformTipi() != null && e1.getPlatformTipi() == e2.getPlatformTipi());

        // 10. MODÜLASYON, PULSECW VE POLARİZASYON UYUMU
        boolean modulasyonUyumlu = (e1.getModulasyon() != null && e2.getModulasyon() != null && e1.getModulasyon() == e2.getModulasyon());
        boolean pulseCwUyumlu = (e1.getPulseCw() != null && e2.getPulseCw() != null && e1.getPulseCw() == e2.getPulseCw());
        boolean polarizasyonUyumlu = (e1.getPolarizasyon() != null && e2.getPolarizasyon() != null && e1.getPolarizasyon() == e2.getPolarizasyon());

        // KAPSAMLI MARKDOWN TABLO RAPORU ÜRET
        String markdownTable = buildMarkdownTableReport(
            e1, e2,
            zamanUyumu,
            frekansZarfUyumu, frekansAltTipler,
            priZarfUyumu, priAltTipler,
            pwZarfUyumu, pwAltTipler,
            atpZarfUyumu, atpAltTipler,
            yerUyumlu, mevziUyumlu,
            teshisUyumlu,
            radarAdiUyumlu, radarGoreviUyumlu,
            platformOrtamiUyumlu, platformTipiUyumlu,
            modulasyonUyumlu, pulseCwUyumlu, polarizasyonUyumlu
        );

        // JSON DTO Yanıtını hazırla
        report.put("durum", "BASARILI");
        report.put("hedef1_Id", e1.getId());
        report.put("hedef2_Id", e2.getId());
        report.put("radar1_Adi", e1.getRadarAdi() != null ? e1.getRadarAdi() : "-");
        report.put("radar2_Adi", e2.getRadarAdi() != null ? e2.getRadarAdi() : "-");
        report.put("kayitTipi1", e1.getKayitTipi() != null ? e1.getKayitTipi().name() : "-");
        report.put("kayitTipi2", e2.getKayitTipi() != null ? e2.getKayitTipi().name() : "-");
        
        report.put("zamanUyumu", zamanUyumu);
        report.put("frekansZarfUyumu", frekansZarfUyumu);
        report.put("frekansAltTipler", frekansAltTipler);
        report.put("priZarfUyumu", priZarfUyumu);
        report.put("priAltTipler", priAltTipler);
        report.put("pwZarfUyumu", pwZarfUyumu);
        report.put("pwAltTipler", pwAltTipler);
        report.put("atpZarfUyumu", atpZarfUyumu);
        report.put("atpAltTipler", atpAltTipler);

        Map<String, Object> kategorik = new LinkedHashMap<>();
        kategorik.put("hedefYerUyumlu", yerUyumlu);
        kategorik.put("hedefMevziUyumlu", mevziUyumlu);
        kategorik.put("teshisUyumlu", teshisUyumlu);
        kategorik.put("radarAdiUyumlu", radarAdiUyumlu);
        kategorik.put("radarGoreviUyumlu", radarGoreviUyumlu);
        kategorik.put("platformOrtamiUyumlu", platformOrtamiUyumlu);
        kategorik.put("platformTipiUyumlu", platformTipiUyumlu);
        kategorik.put("modulasyonUyumlu", modulasyonUyumlu);
        kategorik.put("pulseCwUyumlu", pulseCwUyumlu);
        kategorik.put("polarizasyonUyumlu", polarizasyonUyumlu);
        report.put("kategorikUyum", kategorik);

        report.put("markdownRaporu", markdownTable);

        return report;
    }

    // --- MATEMATİKSEL ARALIK VE KESİŞİM / BİRLEŞİM (IoU) HESAPLAMA MOTORU ---

    public IntervalMatch compareNumericInterval(Double min1, Double max1, Double min2, Double max2, String unit) {
        if (min1 == null || max1 == null || min2 == null || max2 == null) {
            return new IntervalMatch(false, 0.0, 0.0, 0.0, null, "Değer(ler) tanımsız/eksik");
        }

        double aMin = Math.min(min1, max1);
        double aMax = Math.max(min1, max1);
        double bMin = Math.min(min2, max2);
        double bMax = Math.max(min2, max2);

        double unionMin = Math.min(aMin, bMin);
        double unionMax = Math.max(aMax, bMax);
        double union = unionMax - unionMin;

        boolean aIsPoint = Math.abs(aMax - aMin) <= 1e-9;
        boolean bIsPoint = Math.abs(bMax - bMin) <= 1e-9;

        // 1. Durum: Her iki değer de sıfır genişlikli tekil nokta değer
        if (aIsPoint && bIsPoint) {
            if (Math.abs(aMin - bMin) <= 1e-4) {
                return new IntervalMatch(true, 1.0, 0.0, 0.0, null,
                    String.format(Locale.ROOT, "Her iki radar da aynı tekil nokta değerde (%.2f %s, %%100 Uyum)", aMin, unit));
            } else {
                double gap = Math.abs(aMin - bMin);
                return new IntervalMatch(false, 0.0, 0.0, gap, gap,
                    String.format(Locale.ROOT, "Ayrık tekil nokta değerler (Fark: %.2f %s)", gap, unit));
            }
        }

        // 2. Durum: Biri tekil nokta değer, diğeri bir aralık
        if (aIsPoint || bIsPoint) {
            double pointVal = aIsPoint ? aMin : bMin;
            double rangeMin = aIsPoint ? bMin : aMin;
            double rangeMax = aIsPoint ? bMax : aMax;

            if (pointVal >= rangeMin && pointVal <= rangeMax) {
                return new IntervalMatch(true, 1.0, 0.0, (rangeMax - rangeMin), null,
                    String.format(Locale.ROOT, "Tekil nokta değer (%.2f %s), karşı aralığın ([%.2f - %.2f %s]) TAM İÇİNDE yer almaktadır",
                        pointVal, unit, rangeMin, rangeMax, unit));
            } else {
                double gap = pointVal < rangeMin ? (rangeMin - pointVal) : (pointVal - rangeMax);
                return new IntervalMatch(false, 0.0, 0.0, union, gap,
                    String.format(Locale.ROOT, "Tekil nokta değer aralığın dışındadır (Uzaklık / Boşluk: %.2f %s)", gap, unit));
            }
        }

        // 3. Durum: Standart iki sayısal aralık
        double interMin = Math.max(aMin, bMin);
        double interMax = Math.min(aMax, bMax);
        double intersection = Math.max(0.0, interMax - interMin);

        if (intersection > 0.0) {
            double ratio = union > 0 ? (intersection / union) : 1.0;
            return new IntervalMatch(true, ratio, intersection, union, null,
                String.format(Locale.ROOT, "Kesişim: %.2f %s, Birleşim: %.2f %s (Uyum Oranı: %%%.1f)",
                    intersection, unit, union, unit, ratio * 100.0));
        } else {
            double gap = interMin - interMax;
            return new IntervalMatch(false, 0.0, 0.0, union, gap,
                String.format(Locale.ROOT, "Ayrık aralıklar (Çakışma yok, Aralarındaki mesafe: %.2f %s)", gap, unit));
        }
    }

    public IntervalMatch compareTimeIntervals(Instant start1, Instant end1, Instant start2, Instant end2) {
        if (start1 == null || end1 == null || start2 == null || end2 == null) {
            return new IntervalMatch(false, 0.0, 0.0, 0.0, null, "Zaman bilgileri eksik/tanımsız");
        }

        long aStart = start1.toEpochMilli();
        long aEnd = end1.toEpochMilli();
        long bStart = start2.toEpochMilli();
        long bEnd = end2.toEpochMilli();

        long minA = Math.min(aStart, aEnd);
        long maxA = Math.max(aStart, aEnd);
        long minB = Math.min(bStart, bEnd);
        long maxB = Math.max(bStart, bEnd);

        long unionMin = Math.min(minA, minB);
        long unionMax = Math.max(maxA, maxB);
        long unionMillis = unionMax - unionMin;

        long interMin = Math.max(minA, minB);
        long interMax = Math.min(maxA, maxB);
        long interMillis = Math.max(0L, interMax - interMin);

        if (interMillis > 0) {
            double ratio = unionMillis > 0 ? ((double) interMillis / unionMillis) : 1.0;
            double interSec = interMillis / 1000.0;
            double unionSec = unionMillis / 1000.0;
            return new IntervalMatch(true, ratio, interSec, unionSec, null,
                String.format(Locale.ROOT, "Eşzamanlı aktif (Kesişim: %.1f sn, Birleşim: %.1f sn, Zaman Uyum Oranı: %%%.1f)",
                    interSec, unionSec, ratio * 100.0));
        } else {
            long gapMillis = interMin - interMax;
            String gapFormatted = formatDuration(gapMillis);
            return new IntervalMatch(false, 0.0, 0.0, unionMillis / 1000.0, gapMillis / 1000.0,
                String.format(Locale.ROOT, "Eşzamanlı değil (Ayrık). İki yayın arasındaki zaman farkı / yakınlık: %s", gapFormatted));
        }
    }

    // --- ÇOKLU LİSTE KARŞILAŞTIRMA (FREKANS, PRI, PW, ATP) ---

    private List<SubtypeMatch> compareFrequencySubtypes(String json1, String json2) {
        List<FrequencyEntry> list1 = parseList(json1, new TypeReference<List<FrequencyEntry>>() {});
        List<FrequencyEntry> list2 = parseList(json2, new TypeReference<List<FrequencyEntry>>() {});

        Map<String, FrequencyEntry> map1 = new LinkedHashMap<>();
        for (FrequencyEntry f : list1) {
            if (f.frekansTipi() != null) map1.put(f.frekansTipi().toUpperCase(Locale.ROOT), f);
        }

        Map<String, FrequencyEntry> map2 = new LinkedHashMap<>();
        for (FrequencyEntry f : list2) {
            if (f.frekansTipi() != null) map2.put(f.frekansTipi().toUpperCase(Locale.ROOT), f);
        }

        Set<String> allTypes = new LinkedHashSet<>();
        allTypes.addAll(map1.keySet());
        allTypes.addAll(map2.keySet());

        List<SubtypeMatch> results = new ArrayList<>();
        for (String type : allTypes) {
            FrequencyEntry f1 = map1.get(type);
            FrequencyEntry f2 = map2.get(type);

            if (f1 != null && f2 != null) {
                IntervalMatch im = compareNumericInterval(f1.minFrekansMhz(), f1.maxFrekansMhz(), f2.minFrekansMhz(), f2.maxFrekansMhz(), "MHz");
                results.add(new SubtypeMatch(type, true, im,
                    String.format("[%.1f - %.1f MHz]", f1.minFrekansMhz(), f1.maxFrekansMhz()),
                    String.format("[%.1f - %.1f MHz]", f2.minFrekansMhz(), f2.maxFrekansMhz())));
            } else if (f1 != null) {
                results.add(new SubtypeMatch(type, false, new IntervalMatch(false, 0.0, 0, 0, null, "Sadece Radar 1'de mevcut"),
                    String.format("[%.1f - %.1f MHz]", f1.minFrekansMhz(), f1.maxFrekansMhz()), "-"));
            } else if (f2 != null) {
                results.add(new SubtypeMatch(type, false, new IntervalMatch(false, 0.0, 0, 0, null, "Sadece Radar 2'de mevcut"),
                    "-", String.format("[%.1f - %.1f MHz]", f2.minFrekansMhz(), f2.maxFrekansMhz())));
            }
        }
        return results;
    }

    private List<SubtypeMatch> comparePriSubtypes(String json1, String json2) {
        List<PriEntry> list1 = parseList(json1, new TypeReference<List<PriEntry>>() {});
        List<PriEntry> list2 = parseList(json2, new TypeReference<List<PriEntry>>() {});

        Map<String, PriEntry> map1 = new LinkedHashMap<>();
        for (PriEntry p : list1) if (p.priTipi() != null) map1.put(p.priTipi().toUpperCase(Locale.ROOT), p);

        Map<String, PriEntry> map2 = new LinkedHashMap<>();
        for (PriEntry p : list2) if (p.priTipi() != null) map2.put(p.priTipi().toUpperCase(Locale.ROOT), p);

        Set<String> allTypes = new LinkedHashSet<>();
        allTypes.addAll(map1.keySet());
        allTypes.addAll(map2.keySet());

        List<SubtypeMatch> results = new ArrayList<>();
        for (String type : allTypes) {
            PriEntry p1 = map1.get(type);
            PriEntry p2 = map2.get(type);

            if (p1 != null && p2 != null) {
                IntervalMatch im = compareNumericInterval(p1.minPriMicroSec(), p1.maxPriMicroSec(), p2.minPriMicroSec(), p2.maxPriMicroSec(), "µs");
                results.add(new SubtypeMatch(type, true, im,
                    String.format("[%.1f - %.1f µs]", p1.minPriMicroSec(), p1.maxPriMicroSec()),
                    String.format("[%.1f - %.1f µs]", p2.minPriMicroSec(), p2.maxPriMicroSec())));
            } else if (p1 != null) {
                results.add(new SubtypeMatch(type, false, new IntervalMatch(false, 0.0, 0, 0, null, "Sadece Radar 1'de mevcut"),
                    String.format("[%.1f - %.1f µs]", p1.minPriMicroSec(), p1.maxPriMicroSec()), "-"));
            } else if (p2 != null) {
                results.add(new SubtypeMatch(type, false, new IntervalMatch(false, 0.0, 0, 0, null, "Sadece Radar 2'de mevcut"),
                    "-", String.format("[%.1f - %.1f µs]", p2.minPriMicroSec(), p2.maxPriMicroSec())));
            }
        }
        return results;
    }

    private List<SubtypeMatch> comparePwSubtypes(String json1, String json2) {
        List<PwEntry> list1 = parseList(json1, new TypeReference<List<PwEntry>>() {});
        List<PwEntry> list2 = parseList(json2, new TypeReference<List<PwEntry>>() {});

        Map<String, PwEntry> map1 = new LinkedHashMap<>();
        for (PwEntry p : list1) if (p.pwTipi() != null) map1.put(p.pwTipi().toUpperCase(Locale.ROOT), p);

        Map<String, PwEntry> map2 = new LinkedHashMap<>();
        for (PwEntry p : list2) if (p.pwTipi() != null) map2.put(p.pwTipi().toUpperCase(Locale.ROOT), p);

        Set<String> allTypes = new LinkedHashSet<>();
        allTypes.addAll(map1.keySet());
        allTypes.addAll(map2.keySet());

        List<SubtypeMatch> results = new ArrayList<>();
        for (String type : allTypes) {
            PwEntry p1 = map1.get(type);
            PwEntry p2 = map2.get(type);

            if (p1 != null && p2 != null) {
                IntervalMatch im = compareNumericInterval(p1.minPwMicroSec(), p1.maxPwMicroSec(), p2.minPwMicroSec(), p2.maxPwMicroSec(), "µs");
                results.add(new SubtypeMatch(type, true, im,
                    String.format("[%.2f - %.2f µs]", p1.minPwMicroSec(), p1.maxPwMicroSec()),
                    String.format("[%.2f - %.2f µs]", p2.minPwMicroSec(), p2.maxPwMicroSec())));
            } else if (p1 != null) {
                results.add(new SubtypeMatch(type, false, new IntervalMatch(false, 0.0, 0, 0, null, "Sadece Radar 1'de mevcut"),
                    String.format("[%.2f - %.2f µs]", p1.minPwMicroSec(), p1.maxPwMicroSec()), "-"));
            } else if (p2 != null) {
                results.add(new SubtypeMatch(type, false, new IntervalMatch(false, 0.0, 0, 0, null, "Sadece Radar 2'de mevcut"),
                    "-", String.format("[%.2f - %.2f µs]", p2.minPwMicroSec(), p2.maxPwMicroSec())));
            }
        }
        return results;
    }

    private List<SubtypeMatch> compareAtpSubtypes(String json1, String json2) {
        List<AtpEntry> list1 = parseList(json1, new TypeReference<List<AtpEntry>>() {});
        List<AtpEntry> list2 = parseList(json2, new TypeReference<List<AtpEntry>>() {});

        Map<String, AtpEntry> map1 = new LinkedHashMap<>();
        for (AtpEntry a : list1) if (a.atpTipi() != null) map1.put(a.atpTipi().toUpperCase(Locale.ROOT), a);

        Map<String, AtpEntry> map2 = new LinkedHashMap<>();
        for (AtpEntry a : list2) if (a.atpTipi() != null) map2.put(a.atpTipi().toUpperCase(Locale.ROOT), a);

        Set<String> allTypes = new LinkedHashSet<>();
        allTypes.addAll(map1.keySet());
        allTypes.addAll(map2.keySet());

        List<SubtypeMatch> results = new ArrayList<>();
        for (String type : allTypes) {
            AtpEntry a1 = map1.get(type);
            AtpEntry a2 = map2.get(type);

            if (a1 != null && a2 != null) {
                IntervalMatch im = compareNumericInterval(a1.atpOrtMicroSec(), a1.atpOrtMicroSec(), a2.atpOrtMicroSec(), a2.atpOrtMicroSec(), "µs");
                results.add(new SubtypeMatch(type, true, im,
                    String.format("%.1f µs", a1.atpOrtMicroSec()),
                    String.format("%.1f µs", a2.atpOrtMicroSec())));
            } else if (a1 != null) {
                results.add(new SubtypeMatch(type, false, new IntervalMatch(false, 0.0, 0, 0, null, "Sadece Radar 1'de mevcut"),
                    String.format("%.1f µs", a1.atpOrtMicroSec()), "-"));
            } else if (a2 != null) {
                results.add(new SubtypeMatch(type, false, new IntervalMatch(false, 0.0, 0, 0, null, "Sadece Radar 2'de mevcut"),
                    "-", String.format("%.1f µs", a2.atpOrtMicroSec())));
            }
        }
        return results;
    }

    // --- YARDIMCI METODLAR ---

    private boolean isBlankOrDash(String s) {
        return s == null || s.trim().isEmpty() || s.trim().equals("-");
    }

    private boolean exactMatch(String s1, String s2) {
        if (isBlankOrDash(s1) || isBlankOrDash(s2)) return false;
        return s1.trim().equalsIgnoreCase(s2.trim());
    }

    private String cleanId(String raw) {
        if (raw == null) return null;
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private <T> List<T> parseList(String json, TypeReference<List<T>> typeRef) {
        if (json == null || json.isBlank()) return Collections.emptyList();
        try {
            return objectMapper.readValue(json, typeRef);
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    private String formatDuration(long millis) {
        Duration d = Duration.ofMillis(millis);
        long hours = d.toHours();
        long minutes = d.toMinutesPart();
        long seconds = d.toSecondsPart();

        StringBuilder sb = new StringBuilder();
        if (hours > 0) sb.append(hours).append(" saat ");
        if (minutes > 0 || hours > 0) sb.append(minutes).append(" dakika ");
        sb.append(seconds).append(" saniye");
        return sb.toString().trim();
    }

    // --- MARKDOWN TABLO RAPORU ÜRETİCİSİ ---

    private String buildMarkdownTableReport(
        TacticalEmissionEntity e1, TacticalEmissionEntity e2,
        IntervalMatch zaman,
        IntervalMatch freqZarf, List<SubtypeMatch> freqTipler,
        IntervalMatch priZarf, List<SubtypeMatch> priTipler,
        IntervalMatch pwZarf, List<SubtypeMatch> pwTipler,
        IntervalMatch atpZarf, List<SubtypeMatch> atpTipler,
        boolean yerUyumlu, boolean mevziUyumlu,
        boolean teshisUyumlu,
        boolean radarAdiUyumlu, boolean radarGoreviUyumlu,
        boolean platformOrtamiUyumlu, boolean platformTipiUyumlu,
        boolean modulasyonUyumlu, boolean pulseCwUyumlu, boolean polarizasyonUyumlu
    ) {
        StringBuilder sb = new StringBuilder();

        sb.append("### 🎯 RADAR (ELINT / LOB) KARŞILAŞTIRMA VE KORELASYON DEĞERLENDİRME RAPORU\n\n");
        sb.append(String.format("**Hedef 1:** `%s` (%s - %s) | **Hedef 2:** `%s` (%s - %s)\n\n",
            e1.getId(), e1.getRadarAdi() != null ? e1.getRadarAdi() : "-", e1.getKayitTipi(),
            e2.getId(), e2.getRadarAdi() != null ? e2.getRadarAdi() : "-", e2.getKayitTipi()));

        sb.append("| Karşılaştırılan Parametre | Radar 1 (`").append(e1.getId()).append("`) | Radar 2 (`").append(e2.getId()).append("`) | Uyum Durumu | Uyum Oranı (IoU) / Detay |\n");
        sb.append("| :--- | :--- | :--- | :---: | :--- |\n");

        // 1. ZAMAN
        String z1 = e1.getIlkTespitZamani() != null ? DATE_FORMATTER.format(e1.getIlkTespitZamani()) + " - " + DATE_FORMATTER.format(e1.getSonTespitZamani()) : "-";
        String z2 = e2.getIlkTespitZamani() != null ? DATE_FORMATTER.format(e2.getIlkTespitZamani()) + " - " + DATE_FORMATTER.format(e2.getSonTespitZamani()) : "-";
        sb.append(String.format("| **⏰ Zaman Uyumu** | %s | %s | %s | %s |\n",
            z1, z2,
            zaman.hasOverlap() ? "✅ EVET" : "❌ HAYIR",
            zaman.hasOverlap() ? String.format("%%% .1f Uyum (Kesişim/Birleşim)", zaman.matchRatio() * 100.0) : zaman.description()));

        // 2. FREKANS ZARFI
        String f1 = String.format("%.1f - %.1f MHz", e1.getMinFrekansMhz() != null ? e1.getMinFrekansMhz() : 0, e1.getMaxFrekansMhz() != null ? e1.getMaxFrekansMhz() : 0);
        String f2 = String.format("%.1f - %.1f MHz", e2.getMinFrekansMhz() != null ? e2.getMinFrekansMhz() : 0, e2.getMaxFrekansMhz() != null ? e2.getMaxFrekansMhz() : 0);
        sb.append(String.format("| **📶 Frekans Zarfı** | %s | %s | %s | %s |\n",
            f1, f2,
            freqZarf.hasOverlap() ? "✅ EVET" : "❌ HAYIR",
            freqZarf.hasOverlap() ? String.format("%%% .1f IoU (%s)", freqZarf.matchRatio() * 100.0, freqZarf.description()) : freqZarf.description()));

        // 2.1 FREKANS TİP LİSTESİ
        boolean ortakFreqTipVar = freqTipler.stream().anyMatch(s -> s.tipOrtak());
        sb.append(String.format("| └─ *Frekans Liste Tipleri* | %s | %s | %s | %s |\n",
            summarizeTypes(freqTipler, true), summarizeTypes(freqTipler, false),
            ortakFreqTipVar ? "✅ ORTAK TİP VAR" : "❌ ORTAK TİP YOK",
            buildSubtypeDetailText(freqTipler)));

        // 3. PRI ZARFI
        String p1 = String.format("%.1f - %.1f µs", e1.getMinPriMicroSec() != null ? e1.getMinPriMicroSec() : 0, e1.getMaxPriMicroSec() != null ? e1.getMaxPriMicroSec() : 0);
        String p2 = String.format("%.1f - %.1f µs", e2.getMinPriMicroSec() != null ? e2.getMinPriMicroSec() : 0, e2.getMaxPriMicroSec() != null ? e2.getMaxPriMicroSec() : 0);
        sb.append(String.format("| **⚡ PRI Zarfı** | %s | %s | %s | %s |\n",
            p1, p2,
            priZarf.hasOverlap() ? "✅ EVET" : "❌ HAYIR",
            priZarf.hasOverlap() ? String.format("%%% .1f IoU (%s)", priZarf.matchRatio() * 100.0, priZarf.description()) : priZarf.description()));

        // 3.1 PRI TİP LİSTESİ
        boolean ortakPriTipVar = priTipler.stream().anyMatch(s -> s.tipOrtak());
        sb.append(String.format("| └─ *PRI Liste Tipleri* | %s | %s | %s | %s |\n",
            summarizeTypes(priTipler, true), summarizeTypes(priTipler, false),
            ortakPriTipVar ? "✅ ORTAK TİP VAR" : "❌ ORTAK TİP YOK",
            buildSubtypeDetailText(priTipler)));

        // 4. PW ZARFI
        String pw1 = String.format("%.2f - %.2f µs", e1.getMinPwMicroSec() != null ? e1.getMinPwMicroSec() : 0, e1.getMaxPwMicroSec() != null ? e1.getMaxPwMicroSec() : 0);
        String pw2 = String.format("%.2f - %.2f µs", e2.getMinPwMicroSec() != null ? e2.getMinPwMicroSec() : 0, e2.getMaxPwMicroSec() != null ? e2.getMaxPwMicroSec() : 0);
        sb.append(String.format("| **📏 PW (Darbe) Zarfı** | %s | %s | %s | %s |\n",
            pw1, pw2,
            pwZarf.hasOverlap() ? "✅ EVET" : "❌ HAYIR",
            pwZarf.hasOverlap() ? String.format("%%% .1f IoU (%s)", pwZarf.matchRatio() * 100.0, pwZarf.description()) : pwZarf.description()));

        // 4.1 PW TİP LİSTESİ
        boolean ortakPwTipVar = pwTipler.stream().anyMatch(s -> s.tipOrtak());
        sb.append(String.format("| └─ *PW Liste Tipleri* | %s | %s | %s | %s |\n",
            summarizeTypes(pwTipler, true), summarizeTypes(pwTipler, false),
            ortakPwTipVar ? "✅ ORTAK TİP VAR" : "❌ ORTAK TİP YOK",
            buildSubtypeDetailText(pwTipler)));

        // 5. ATP ZARFI
        String a1 = String.format("%.1f - %.1f µs", e1.getMinAtpMicroSec() != null ? e1.getMinAtpMicroSec() : 0, e1.getMaxAtpMicroSec() != null ? e1.getMaxAtpMicroSec() : 0);
        String a2 = String.format("%.1f - %.1f µs", e2.getMinAtpMicroSec() != null ? e2.getMinAtpMicroSec() : 0, e2.getMaxAtpMicroSec() != null ? e2.getMaxAtpMicroSec() : 0);
        sb.append(String.format("| **🔄 ATP (Tarama) Zarfı** | %s | %s | %s | %s |\n",
            a1, a2,
            atpZarf.hasOverlap() ? "✅ EVET" : "❌ HAYIR",
            atpZarf.hasOverlap() ? String.format("%%% .1f IoU (%s)", atpZarf.matchRatio() * 100.0, atpZarf.description()) : atpZarf.description()));

        // 5.1 ATP TİP LİSTESİ
        boolean ortakAtpTipVar = atpTipler.stream().anyMatch(s -> s.tipOrtak());
        sb.append(String.format("| └─ *ATP Liste Tipleri* | %s | %s | %s | %s |\n",
            summarizeTypes(atpTipler, true), summarizeTypes(atpTipler, false),
            ortakAtpTipVar ? "✅ ORTAK TİP VAR" : "❌ ORTAK TİP YOK",
            buildSubtypeDetailText(atpTipler)));

        // 6. HEDEF YER & MEVZİ (Bire bir)
        boolean yerTanimsiz = isBlankOrDash(e1.getHedefYerBilgisi()) && isBlankOrDash(e2.getHedefYerBilgisi());
        sb.append(String.format("| **📍 Hedef Yer Bilgisi** | %s | %s | %s | %s |\n",
            !isBlankOrDash(e1.getHedefYerBilgisi()) ? e1.getHedefYerBilgisi() : "-",
            !isBlankOrDash(e2.getHedefYerBilgisi()) ? e2.getHedefYerBilgisi() : "-",
            yerTanimsiz ? "⚠️ TANIMSIZ" : (yerUyumlu ? "✅ UYUMLU" : "❌ FARKLI"),
            yerTanimsiz ? "Her iki hedefte de yer bilgisi girilmemiş" : (yerUyumlu ? "Bire bir aynı coğrafi yer (" + e1.getHedefYerBilgisi() + ")" : "Farklı sektörler")));

        boolean mevziTanimsiz = isBlankOrDash(e1.getHedefMevziBilgisi()) && isBlankOrDash(e2.getHedefMevziBilgisi());
        sb.append(String.format("| **🏰 Hedef Mevzi Bilgisi** | %s | %s | %s | %s |\n",
            !isBlankOrDash(e1.getHedefMevziBilgisi()) ? e1.getHedefMevziBilgisi() : "-",
            !isBlankOrDash(e2.getHedefMevziBilgisi()) ? e2.getHedefMevziBilgisi() : "-",
            mevziTanimsiz ? "⚠️ TANIMSIZ" : (mevziUyumlu ? "✅ UYUMLU" : "❌ FARKLI"),
            mevziTanimsiz ? "Her iki hedefte de mevzi bilgisi girilmemiş" : (mevziUyumlu ? "Bire bir aynı taktik mevzi (" + e1.getHedefMevziBilgisi() + ")" : "Farklı mevziler")));

        // 7. TEŞHİS (Bire bir)
        boolean teshisTanimsiz = e1.getTeshisKimlik() == null && e2.getTeshisKimlik() == null;
        sb.append(String.format("| **🛡️ Teşhis / Kimlik** | %s | %s | %s | %s |\n",
            e1.getTeshisKimlik() != null ? e1.getTeshisKimlik().name() : "-",
            e2.getTeshisKimlik() != null ? e2.getTeshisKimlik().name() : "-",
            teshisTanimsiz ? "⚠️ TANIMSIZ" : (teshisUyumlu ? "✅ UYUMLU" : "❌ FARKLI"),
            teshisTanimsiz ? "Teşhis kimliği atanmamış" : (teshisUyumlu ? "Aynı teşhis sınıfı (" + e1.getTeshisKimlik() + ")" : "Farklı teşhis kimlikleri")));

        // 8. RADAR ADI & GÖREVİ (Bire bir)
        boolean radarAdiTanimsiz = isBlankOrDash(e1.getRadarAdi()) && isBlankOrDash(e2.getRadarAdi());
        sb.append(String.format("| **📡 Radar Adı** | %s | %s | %s | %s |\n",
            !isBlankOrDash(e1.getRadarAdi()) ? e1.getRadarAdi() : "-",
            !isBlankOrDash(e2.getRadarAdi()) ? e2.getRadarAdi() : "-",
            radarAdiTanimsiz ? "⚠️ TANIMSIZ" : (radarAdiUyumlu ? "✅ UYUMLU" : "❌ FARKLI"),
            radarAdiTanimsiz ? "Radar adı girilmemiş" : (radarAdiUyumlu ? "Bire bir aynı radar modeli (" + e1.getRadarAdi() + ")" : "Farklı radar sistemleri")));

        boolean radarGoreviTanimsiz = isBlankOrDash(e1.getRadarGorevi()) && isBlankOrDash(e2.getRadarGorevi());
        sb.append(String.format("| **🎯 Radar Görevi** | %s | %s | %s | %s |\n",
            !isBlankOrDash(e1.getRadarGorevi()) ? e1.getRadarGorevi() : "-",
            !isBlankOrDash(e2.getRadarGorevi()) ? e2.getRadarGorevi() : "-",
            radarGoreviTanimsiz ? "⚠️ TANIMSIZ" : (radarGoreviUyumlu ? "✅ UYUMLU" : "❌ FARKLI"),
            radarGoreviTanimsiz ? "Radar görevi girilmemiş" : (radarGoreviUyumlu ? "Bire bir aynı operasyonel görev (" + e1.getRadarGorevi() + ")" : "Farklı radar görevleri")));

        // 9. PLATFORM ORTAMI & TİPİ (Bire bir)
        boolean platformOrtamiTanimsiz = e1.getPlatformOrtami() == null && e2.getPlatformOrtami() == null;
        sb.append(String.format("| **🌍 Platform Ortamı** | %s | %s | %s | %s |\n",
            e1.getPlatformOrtami() != null ? e1.getPlatformOrtami().name() : "-",
            e2.getPlatformOrtami() != null ? e2.getPlatformOrtami().name() : "-",
            platformOrtamiTanimsiz ? "⚠️ TANIMSIZ" : (platformOrtamiUyumlu ? "✅ UYUMLU" : "❌ FARKLI"),
            platformOrtamiTanimsiz ? "Platform ortamı belirtilmemiş" : (platformOrtamiUyumlu ? "Aynı platform ortamı (" + e1.getPlatformOrtami() + ")" : "Farklı platform ortamları")));

        boolean platformTipiTanimsiz = e1.getPlatformTipi() == null && e2.getPlatformTipi() == null;
        sb.append(String.format("| **🚜 Platform Tipi** | %s | %s | %s | %s |\n",
            e1.getPlatformTipi() != null ? e1.getPlatformTipi().name() : "-",
            e2.getPlatformTipi() != null ? e2.getPlatformTipi().name() : "-",
            platformTipiTanimsiz ? "⚠️ TANIMSIZ" : (platformTipiUyumlu ? "✅ UYUMLU" : "❌ FARKLI"),
            platformTipiTanimsiz ? "Platform tipi belirtilmemiş" : (platformTipiUyumlu ? "Aynı platform taşıyıcı tipi (" + e1.getPlatformTipi() + ")" : "Farklı taşıyıcı platformlar")));

        // 10. MODÜLASYON, PULSECW VE POLARİZASYON
        List<String> uyumluRf = new ArrayList<>();
        if (modulasyonUyumlu) uyumluRf.add("Modülasyon (" + e1.getModulasyon() + ")");
        if (pulseCwUyumlu) uyumluRf.add("Pulse/CW (" + e1.getPulseCw() + ")");
        if (polarizasyonUyumlu) uyumluRf.add("Polarizasyon (" + e1.getPolarizasyon() + ")");

        boolean modulasyonTanimsiz = e1.getModulasyon() == null && e2.getModulasyon() == null;
        sb.append(String.format("| **📻 Modülasyon** | %s | %s | %s | %s |\n",
            e1.getModulasyon() != null ? e1.getModulasyon().name() : "-",
            e2.getModulasyon() != null ? e2.getModulasyon().name() : "-",
            modulasyonTanimsiz ? "⚠️ TANIMSIZ" : (modulasyonUyumlu ? "✅ UYUMLU" : "❌ FARKLI"),
            modulasyonTanimsiz ? "Modülasyon bilgisi girilmemiş" : (modulasyonUyumlu ? "Bire bir aynı MOP (" + e1.getModulasyon() + ")" : "Farklı modülasyon tipi")));

        boolean pulseCwTanimsiz = e1.getPulseCw() == null && e2.getPulseCw() == null;
        sb.append(String.format("| **⚡ Pulse / CW** | %s | %s | %s | %s |\n",
            e1.getPulseCw() != null ? e1.getPulseCw().name() : "-",
            e2.getPulseCw() != null ? e2.getPulseCw().name() : "-",
            pulseCwTanimsiz ? "⚠️ TANIMSIZ" : (pulseCwUyumlu ? "✅ UYUMLU" : "❌ FARKLI"),
            pulseCwTanimsiz ? "Pulse/CW bilgisi girilmemiş" : (pulseCwUyumlu ? "Aynı darbe dalga yapısı (" + e1.getPulseCw() + ")" : "Farklı CW/Pulse türü")));

        boolean polarizasyonTanimsiz = e1.getPolarizasyon() == null && e2.getPolarizasyon() == null;
        sb.append(String.format("| **📶 Polarizasyon** | %s | %s | %s | %s |\n",
            e1.getPolarizasyon() != null ? e1.getPolarizasyon().name() : "-",
            e2.getPolarizasyon() != null ? e2.getPolarizasyon().name() : "-",
            polarizasyonTanimsiz ? "⚠️ TANIMSIZ" : (polarizasyonUyumlu ? "✅ UYUMLU" : "❌ FARKLI"),
            polarizasyonTanimsiz ? "Polarizasyon bilgisi girilmemiş" : (polarizasyonUyumlu ? "Aynı anten polarizasyonu (" + e1.getPolarizasyon() + ")" : "Farklı anten polarizasyonu")));

        // ÖZET DOKTRİNEL DEĞERLENDİRME ÇIKTISI
        sb.append("\n### 📋 TAKTİK UYUM VE KORELASYON DEĞERLENDİRME ÖZETİ\n");
        sb.append(String.format("- **⏰ Zaman Uyumu:** %s\n",
            zaman.hasOverlap() ? String.format("Var (Uyum Oranı: %%%.1f)", zaman.matchRatio() * 100.0) : "Yok (" + zaman.description() + ")"));

        sb.append(String.format("- **📶 Frekans Uyumu:** %s | Alt Tipler: %s\n",
            freqZarf.hasOverlap() ? String.format("Zarf Uyum Oranı: %%%.1f", freqZarf.matchRatio() * 100.0) : "Zarf Uyumu Yok",
            buildSubtypeDetailText(freqTipler)));

        sb.append(String.format("- **⚡ PRI Uyumu:** %s | Alt Tipler: %s\n",
            priZarf.hasOverlap() ? String.format("Zarf Uyum Oranı: %%%.1f", priZarf.matchRatio() * 100.0) : "Zarf Uyumu Yok",
            buildSubtypeDetailText(priTipler)));

        sb.append(String.format("- **📏 PW Uyumu:** %s | Alt Tipler: %s\n",
            pwZarf.hasOverlap() ? String.format("Zarf Uyum Oranı: %%%.1f", pwZarf.matchRatio() * 100.0) : "Zarf Uyumu Yok",
            buildSubtypeDetailText(pwTipler)));

        sb.append(String.format("- **🔄 ATP Uyumu:** %s | Alt Tipler: %s\n",
            atpZarf.hasOverlap() ? String.format("Zarf Uyum Oranı: %%%.1f", atpZarf.matchRatio() * 100.0) : "Zarf Uyumu Yok",
            buildSubtypeDetailText(atpTipler)));

        List<String> yerMevziList = new ArrayList<>();
        if (yerUyumlu) yerMevziList.add("Hedef Yer (" + e1.getHedefYerBilgisi() + ")");
        if (mevziUyumlu) yerMevziList.add("Hedef Mevzi (" + e1.getHedefMevziBilgisi() + ")");
        sb.append(String.format("- **📍 Hedef Yer ve Hedef Mevzi Uyumu:** %s\n",
            yerMevziList.isEmpty() ? "Uyumlu olan yok" : "Uyumlu olan(lar): " + String.join(", ", yerMevziList)));

        sb.append(String.format("- **🛡️ Teşhis Uyumu:** %s\n",
            teshisUyumlu ? "Uyumlu (" + e1.getTeshisKimlik() + ")" : "Uyumsuz (" + (e1.getTeshisKimlik() != null ? e1.getTeshisKimlik() : "-") + " vs " + (e2.getTeshisKimlik() != null ? e2.getTeshisKimlik() : "-") + ")"));

        List<String> radarList = new ArrayList<>();
        if (radarAdiUyumlu) radarList.add("Radar Adı (" + e1.getRadarAdi() + ")");
        if (radarGoreviUyumlu) radarList.add("Radar Görevi (" + e1.getRadarGorevi() + ")");
        sb.append(String.format("- **📡 Radar Adı ve Görevi Uyumu:** %s\n",
            radarList.isEmpty() ? "Uyumlu olan yok" : "Uyumlu olan(lar): " + String.join(", ", radarList)));

        List<String> platformList = new ArrayList<>();
        if (platformOrtamiUyumlu) platformList.add("Platform Ortamı (" + e1.getPlatformOrtami() + ")");
        if (platformTipiUyumlu) platformList.add("Platform Tipi (" + e1.getPlatformTipi() + ")");
        sb.append(String.format("- **🌍 Platform Ortamı ve Platform Tipi Uyumu:** %s\n",
            platformList.isEmpty() ? "Uyumlu olan yok" : "Uyumlu olan(lar): " + String.join(", ", platformList)));

        sb.append(String.format("- **📻 Modülasyon, Pulse/CW ve Polarizasyon Uyumu:** %s\n",
            uyumluRf.isEmpty() ? "Hiçbiri uyumlu değil" : "Uyumlu olan(lar): " + String.join(", ", uyumluRf)));

        return sb.toString();
    }

    private String summarizeTypes(List<SubtypeMatch> list, boolean isFirst) {
        if (list.isEmpty()) return "-";
        List<String> types = new ArrayList<>();
        for (SubtypeMatch s : list) {
            String range = isFirst ? s.range1Str() : s.range2Str();
            if (!range.equals("-")) {
                types.add(s.tip());
            }
        }
        return types.isEmpty() ? "-" : String.join(", ", types);
    }

    private String buildSubtypeDetailText(List<SubtypeMatch> list) {
        List<String> matches = new ArrayList<>();
        for (SubtypeMatch s : list) {
            if (s.tipOrtak()) {
                matches.add(String.format("`%s`: %%%.1f IoU", s.tip(), s.intervalMatch().matchRatio() * 100.0));
            }
        }
        if (matches.isEmpty()) {
            return "Ortak çalışma tipi tespit edilemedi";
        }
        return String.join(" | ", matches);
    }

    // =========================================================================
    // 🎯 1-TO-N BENZERLİK VE KORELASYON MOTORU (100 PUANLIK ÇOK KRİTERLİ MATRİS)
    // =========================================================================

    public Map<String, Object> findSimilarRadarEmissions(String targetId, String timeRange, Integer limit) {
        log.info("🎯 [1-TO-N BENZERLİK ARAMASI] Hedef: {}, Zaman Aralığı: {}, Limit: {}", targetId, timeRange, limit);

        String cleanTargetId = cleanId(targetId);
        int maxLimit = (limit != null && limit > 0 && limit <= 50) ? limit : 5;
        String cleanTime = (timeRange != null && !timeRange.isBlank()) ? timeRange.trim().toUpperCase(Locale.ROOT) : "SON_24_SAAT";

        Map<String, Object> response = new LinkedHashMap<>();
        if (cleanTargetId == null) {
            response.put("durum", "HATA");
            response.put("mesaj", "Arama için geçerli bir referans radar/LOB ID'si girilmelidir.");
            return response;
        }

        Optional<TacticalEmissionEntity> targetOpt = emissionRepo.findById(cleanTargetId);
        if (targetOpt.isEmpty()) {
            response.put("durum", "HATA");
            response.put("mesaj", "Referans radar kaydı bulunamadı: " + cleanTargetId);
            return response;
        }

        TacticalEmissionEntity target = targetOpt.get();
        Instant targetTime = target.getSonTespitZamani();
        Instant refTime = emissionRepo.findMaxSonTespitZamani();
        if (targetTime == null) {
            targetTime = (refTime != null) ? refTime : Instant.now();
        }

        // Zaman pencerelerini hesapla
        Instant start = null;
        Instant end = null;
        if (cleanTime.contains("1_SAAT")) {
            start = targetTime.minus(Duration.ofHours(1));
            end = targetTime.plus(Duration.ofHours(1));
        } else if (cleanTime.contains("6_SAAT")) {
            start = targetTime.minus(Duration.ofHours(6));
            end = targetTime.plus(Duration.ofHours(6));
        } else if (cleanTime.contains("24_SAAT") || cleanTime.contains("BUGUN")) {
            start = targetTime.minus(Duration.ofHours(24));
            end = targetTime.plus(Duration.ofHours(24));
        } else if (cleanTime.contains("7_GUN") || cleanTime.contains("HAFTA")) {
            start = targetTime.minus(Duration.ofDays(7));
            end = targetTime.plus(Duration.ofDays(7));
        } else if (cleanTime.equalsIgnoreCase("ALL")) {
            start = null;
            end = null;
        } else {
            start = targetTime.minus(Duration.ofHours(24));
            end = targetTime.plus(Duration.ofHours(24));
        }

        // Aday filtreleme sorgusu (Gating: Kesişmesi muhtemel olanları hızla seç)
        MapSqlParameterSource params = new MapSqlParameterSource();
        params.addValue("targetId", cleanTargetId);
        params.addValue("fetchLimit", 500);

        StringBuilder whereSql = new StringBuilder(" WHERE emission_id != :targetId ");

        if (start != null && end != null) {
            whereSql.append(" AND son_tespit_zamani >= :start AND son_tespit_zamani <= :end ");
            params.addValue("start", Timestamp.from(start));
            params.addValue("end", Timestamp.from(end));
        }

        if (target.getMinFrekansMhz() != null && target.getMaxFrekansMhz() != null) {
            double gateMargin = 300.0;
            whereSql.append(" AND (max_frekans_mhz >= :minF AND min_frekans_mhz <= :maxF) ");
            params.addValue("minF", Math.max(0.0, target.getMinFrekansMhz() - gateMargin));
            params.addValue("maxF", target.getMaxFrekansMhz() + gateMargin);
        }

        String sql = "SELECT emission_id FROM tactical_emissions " + whereSql + " ORDER BY son_tespit_zamani DESC LIMIT :fetchLimit";
        List<String> candidateIds = jdbcTemplate.queryForList(sql, params, String.class);

        // Eğer frekans filtreli sorguda aday bulunamadıysa (dar bant vs), sadece zaman pencereli fallback yap
        if (candidateIds.isEmpty() && target.getMinFrekansMhz() != null) {
            StringBuilder fallbackWhere = new StringBuilder(" WHERE emission_id != :targetId ");
            if (start != null && end != null) {
                fallbackWhere.append(" AND son_tespit_zamani >= :start AND son_tespit_zamani <= :end ");
            }
            String fallbackSql = "SELECT emission_id FROM tactical_emissions " + fallbackWhere + " ORDER BY son_tespit_zamani DESC LIMIT :fetchLimit";
            candidateIds = jdbcTemplate.queryForList(fallbackSql, params, String.class);
        }

        if (candidateIds.isEmpty()) {
            response.put("durum", "UYARI");
            response.put("mesaj", "Belirtilen zaman aralığında (" + cleanTime + ") karşılaştırılacak aday radar kaydı bulunamadı.");
            response.put("referansHedefId", target.getId());
            response.put("referansRadarAdi", target.getRadarAdi());
            response.put("bulunanAdaySayisi", 0);
            return response;
        }

        List<TacticalEmissionEntity> candidates = emissionRepo.findAllById(candidateIds);
        List<SimilarRadarCandidate> scoredCandidates = new ArrayList<>();

        for (TacticalEmissionEntity c : candidates) {
            scoredCandidates.add(scoreCandidate(target, c));
        }

        // Toplam benzerlik skoruna göre azalan sırada sırala
        scoredCandidates.sort(Comparator.comparingDouble((SimilarRadarCandidate sc) -> sc.totalScore()).reversed());

        // İlk maxLimit adayı al ve sıra numarası ata
        List<SimilarRadarCandidate> topCandidates = new ArrayList<>();
        int rank = 1;
        for (SimilarRadarCandidate sc : scoredCandidates) {
            if (topCandidates.size() >= maxLimit) break;
            topCandidates.add(new SimilarRadarCandidate(
                rank++,
                sc.id(),
                sc.radarAdi(),
                sc.teshisKimlik(),
                sc.kayitTipi(),
                sc.totalScore(),
                sc.rfScore(),
                sc.timeScore(),
                sc.timeOverlapScore(),
                sc.timeProximityScore(),
                sc.tacticalScore(),
                sc.freqIoU(),
                sc.priIoU(),
                sc.pwIoU(),
                sc.timeIoU(),
                sc.timeProximityDesc(),
                sc.sonTespitZamaniFormatted(),
                sc.spotNo(),
                sc.hedefYerBilgisi(),
                sc.hedefMevziBilgisi(),
                sc.matchSummary()
            ));
        }

        String markdownTable = build1ToNMarkdownReport(target, cleanTime, candidates.size(), topCandidates);

        response.put("durum", "BASARILI");
        response.put("referansHedefId", target.getId());
        response.put("referansRadarAdi", target.getRadarAdi() != null ? target.getRadarAdi() : "-");
        response.put("referansKayitTipi", target.getKayitTipi() != null ? target.getKayitTipi().name() : "-");
        response.put("zamanAraligi", cleanTime);
        response.put("tarananAdaySayisi", candidates.size());
        response.put("getirilenAdaySayisi", topCandidates.size());
        response.put("benzerRadarListesi", topCandidates);
        response.put("markdownRaporu", markdownTable);

        return response;
    }

    private SimilarRadarCandidate scoreCandidate(TacticalEmissionEntity t, TacticalEmissionEntity c) {
        // 1. FREKANS ZARFI (10 P)
        IntervalMatch fZarf = compareNumericInterval(t.getMinFrekansMhz(), t.getMaxFrekansMhz(), c.getMinFrekansMhz(), c.getMaxFrekansMhz(), "MHz");
        double fZarfScore = fZarf.hasOverlap() ? (fZarf.matchRatio() * 10.0) : 0.0;

        // 2. FREKANS ALT TİPLERİ (8 P)
        List<SubtypeMatch> fTipler = compareFrequencySubtypes(t.getFrekansListesiJson(), c.getFrekansListesiJson());
        List<SubtypeMatch> commonFTypes = fTipler.stream().filter(s -> s.tipOrtak()).toList();
        double fSubtypeScore = 0.0;
        if (!commonFTypes.isEmpty()) {
            double avgIoU = commonFTypes.stream().mapToDouble(s -> s.intervalMatch().matchRatio()).average().orElse(0.0);
            fSubtypeScore = avgIoU * 8.0;
        }

        // 3. PRI ZARFI (8 P)
        IntervalMatch pZarf = compareNumericInterval(t.getMinPriMicroSec(), t.getMaxPriMicroSec(), c.getMinPriMicroSec(), c.getMaxPriMicroSec(), "µs");
        double pZarfScore = pZarf.hasOverlap() ? (pZarf.matchRatio() * 8.0) : 0.0;

        // 4. PRI ALT TİPLERİ (6 P)
        List<SubtypeMatch> pTipler = comparePriSubtypes(t.getPriListesiJson(), c.getPriListesiJson());
        List<SubtypeMatch> commonPTypes = pTipler.stream().filter(s -> s.tipOrtak()).toList();
        double pSubtypeScore = 0.0;
        if (!commonPTypes.isEmpty()) {
            double avgIoU = commonPTypes.stream().mapToDouble(s -> s.intervalMatch().matchRatio()).average().orElse(0.0);
            pSubtypeScore = avgIoU * 6.0;
        }

        // 5. PW ZARFI (5 P)
        IntervalMatch pwZarf = compareNumericInterval(t.getMinPwMicroSec(), t.getMaxPwMicroSec(), c.getMinPwMicroSec(), c.getMaxPwMicroSec(), "µs");
        double pwZarfScore = pwZarf.hasOverlap() ? (pwZarf.matchRatio() * 5.0) : 0.0;

        // 6. PW ALT TİPLERİ (3 P)
        List<SubtypeMatch> pwTipler = comparePwSubtypes(t.getPwListesiJson(), c.getPwListesiJson());
        List<SubtypeMatch> commonPwTypes = pwTipler.stream().filter(s -> s.tipOrtak()).toList();
        double pwSubtypeScore = 0.0;
        if (!commonPwTypes.isEmpty()) {
            double avgIoU = commonPwTypes.stream().mapToDouble(s -> s.intervalMatch().matchRatio()).average().orElse(0.0);
            pwSubtypeScore = avgIoU * 3.0;
        }

        // 7. RF MODÜLASYON, PULSE/CW, POLARİZASYON (5 P)
        double mopScore = (t.getModulasyon() != null && c.getModulasyon() != null && t.getModulasyon() == c.getModulasyon()) ? 2.0 : 0.0;
        double pulseCwScore = (t.getPulseCw() != null && c.getPulseCw() != null && t.getPulseCw() == c.getPulseCw()) ? 2.0 : 0.0;
        double polScore = (t.getPolarizasyon() != null && c.getPolarizasyon() != null && t.getPolarizasyon() == c.getPolarizasyon()) ? 1.0 : 0.0;
        double rfWaveScore = mopScore + pulseCwScore + polScore;

        double totalRfScore = fZarfScore + fSubtypeScore + pZarfScore + pSubtypeScore + pwZarfScore + pwSubtypeScore + rfWaveScore;

        // 8. ZAMAN KESİŞİM UYUMU (IoU) (10 P)
        IntervalMatch timeMatch = compareTimeIntervals(t.getIlkTespitZamani(), t.getSonTespitZamani(), c.getIlkTespitZamani(), c.getSonTespitZamani());
        double timeOverlapScore = timeMatch.hasOverlap() ? (timeMatch.matchRatio() * 10.0) : 0.0;

        // 9. ZAMAN YAKINLIĞI (PROXIMITY) (10 P)
        double timeProximityScore = 0.0;
        String proximityDesc;
        if (timeMatch.hasOverlap()) {
            timeProximityScore = 10.0;
            proximityDesc = "Eşzamanlı (0 sn)";
        } else {
            Double gapSec = timeMatch.gapDistance();
            if (gapSec != null) {
                if (gapSec <= 300.0) {
                    timeProximityScore = 9.0;
                    proximityDesc = String.format(Locale.ROOT, "%.0f sn (≤ 5 dk)", gapSec);
                } else if (gapSec <= 1800.0) {
                    timeProximityScore = 7.0;
                    proximityDesc = String.format(Locale.ROOT, "%.1f dk (≤ 30 dk)", gapSec / 60.0);
                } else if (gapSec <= 7200.0) {
                    timeProximityScore = 5.0;
                    proximityDesc = String.format(Locale.ROOT, "%.1f sa (≤ 2 saat)", gapSec / 3600.0);
                } else if (gapSec <= 21600.0) {
                    timeProximityScore = 3.0;
                    proximityDesc = String.format(Locale.ROOT, "%.1f sa (≤ 6 saat)", gapSec / 3600.0);
                } else if (gapSec <= 86400.0) {
                    timeProximityScore = 1.0;
                    proximityDesc = String.format(Locale.ROOT, "%.1f sa (≤ 24 saat)", gapSec / 3600.0);
                } else {
                    timeProximityScore = 0.0;
                    proximityDesc = String.format(Locale.ROOT, "%.0f gün (> 24 saat)", gapSec / 86400.0);
                }
            } else {
                proximityDesc = "Tanımsız";
            }
        }
        double totalTimeScore = timeOverlapScore + timeProximityScore;

        // 10. HEDEF YER VE MEVZİ (10 P)
        double yerScore = exactMatch(t.getHedefYerBilgisi(), c.getHedefYerBilgisi()) ? 6.0 : 0.0;
        double mevziScore = exactMatch(t.getHedefMevziBilgisi(), c.getHedefMevziBilgisi()) ? 4.0 : 0.0;

        // 11. RADAR ADI VE GÖREVİ (12 P)
        double radarAdiScore = exactMatch(t.getRadarAdi(), c.getRadarAdi()) ? 8.0 : 0.0;
        double radarGoreviScore = exactMatch(t.getRadarGorevi(), c.getRadarGorevi()) ? 4.0 : 0.0;

        // 12. TEŞHİS KİMLİĞİ (8 P)
        double teshisScore = (t.getTeshisKimlik() != null && c.getTeshisKimlik() != null && t.getTeshisKimlik() == c.getTeshisKimlik()) ? 8.0 : 0.0;

        // 13. PLATFORM ORTAMI VE TİPİ (5 P)
        double platformOrtamiScore = (t.getPlatformOrtami() != null && c.getPlatformOrtami() != null && t.getPlatformOrtami() == c.getPlatformOrtami()) ? 3.0 : 0.0;
        double platformTipiScore = (t.getPlatformTipi() != null && c.getPlatformTipi() != null && t.getPlatformTipi() == c.getPlatformTipi()) ? 2.0 : 0.0;

        double totalTacticalScore = yerScore + mevziScore + radarAdiScore + radarGoreviScore + teshisScore + platformOrtamiScore + platformTipiScore;

        double totalScore = Math.min(100.0, Math.max(0.0, totalRfScore + totalTimeScore + totalTacticalScore));

        List<String> keyMatches = new ArrayList<>();
        if (radarAdiScore > 0) keyMatches.add("Aynı Radar Modeli");
        if (fZarfScore >= 7.0) keyMatches.add("Yüksek Frekans Örtüşmesi");
        if (pZarfScore >= 6.0) keyMatches.add("Uyumlu PRI");
        if (timeMatch.hasOverlap()) keyMatches.add("Eşzamanlı Aktif");
        else if (timeProximityScore >= 7.0) keyMatches.add("Yakın Zamanlı");
        if (yerScore > 0) keyMatches.add("Aynı Sektör");
        String summary = keyMatches.isEmpty() ? "Düşük/kısmi korelasyon" : String.join(", ", keyMatches);

        String sonZaman = c.getSonTespitZamani() != null ? DATE_FORMATTER.format(c.getSonTespitZamani()) : "-";

        return new SimilarRadarCandidate(
            0,
            c.getId(),
            c.getRadarAdi() != null ? c.getRadarAdi() : "-",
            c.getTeshisKimlik() != null ? c.getTeshisKimlik().name() : "-",
            c.getKayitTipi() != null ? c.getKayitTipi().name() : "-",
            Math.round(totalScore * 10.0) / 10.0,
            Math.round(totalRfScore * 10.0) / 10.0,
            Math.round(totalTimeScore * 10.0) / 10.0,
            Math.round(timeOverlapScore * 10.0) / 10.0,
            Math.round(timeProximityScore * 10.0) / 10.0,
            Math.round(totalTacticalScore * 10.0) / 10.0,
            Math.round(fZarf.matchRatio() * 1000.0) / 10.0,
            Math.round(pZarf.matchRatio() * 1000.0) / 10.0,
            Math.round(pwZarf.matchRatio() * 1000.0) / 10.0,
            Math.round(timeMatch.matchRatio() * 1000.0) / 10.0,
            proximityDesc,
            sonZaman,
            c.getSpotNo() != null ? c.getSpotNo() : "-",
            c.getHedefYerBilgisi() != null ? c.getHedefYerBilgisi() : "-",
            c.getHedefMevziBilgisi() != null ? c.getHedefMevziBilgisi() : "-",
            summary
        );
    }

    private String build1ToNMarkdownReport(
        TacticalEmissionEntity target,
        String timeRange,
        int totalScanned,
        List<SimilarRadarCandidate> topCandidates
    ) {
        StringBuilder sb = new StringBuilder();
        sb.append("### 🎯 1-TO-N RADAR BENZERLİK VE KORELASYON ANALİZİ RAPORU\n\n");
        sb.append(String.format(Locale.ROOT, "**Referans Hedef:** `%s` (%s - %s) | **Analiz Penceresi:** `%s` | **Taranan Aday:** %d adet\n\n",
            target.getId(),
            target.getRadarAdi() != null ? target.getRadarAdi() : "-",
            target.getKayitTipi() != null ? target.getKayitTipi().name() : "-",
            timeRange,
            totalScanned));

        if (topCandidates.isEmpty()) {
            sb.append("*Belirtilen kriterlerde benzer radar kaydı bulunamadı.*\n");
            return sb.toString();
        }

        sb.append("| Sıra | Aday Radar ID | Radar Adı | Teşhis | Toplam Benzerlik | Zaman Kesişimi | Zaman Yakınlığı | Frekans IoU | PRI IoU | PW IoU | Taktik Yer / Sektör |\n");
        sb.append("| :---: | :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :--- |\n");

        for (SimilarRadarCandidate c : topCandidates) {
            String timeOverlapStr = c.timeOverlapScore() > 0 ? String.format(Locale.ROOT, "%%% .1f (%.1f P)", c.timeIoU(), c.timeOverlapScore()) : "0 P (Ayrık)";
            String timeProxStr = String.format(Locale.ROOT, "%s (%.0f P)", c.timeProximityDesc(), c.timeProximityScore());
            String freqStr = String.format(Locale.ROOT, "%%% .1f", c.freqIoU());
            String priStr = String.format(Locale.ROOT, "%%% .1f", c.priIoU());
            String pwStr = String.format(Locale.ROOT, "%%% .1f", c.pwIoU());

            sb.append(String.format(Locale.ROOT, "| **#%d** | `%s` | %s | %s | **%%% .1f** | %s | %s | %s | %s | %s | %s |\n",
                c.rank(),
                c.id(),
                c.radarAdi(),
                c.teshisKimlik(),
                c.totalScore(),
                timeOverlapStr,
                timeProxStr,
                freqStr,
                priStr,
                pwStr,
                c.hedefYerBilgisi()));
        }

        SimilarRadarCandidate best = topCandidates.get(0);
        sb.append(String.format(Locale.ROOT, "\n#### 📋 EN YÜKSEK BENZERLİK GÖSTEREN HEDEF DETAY PUAN KARTI (`%s`)\n", best.id()));
        sb.append(String.format(Locale.ROOT, "- **Toplam Benzerlik Skoru:** **%%% .1f / 100 Puan** (%s)\n", best.totalScore(), best.matchSummary()));
        sb.append(String.format(Locale.ROOT, "  - 📶 **RF Sinyal İmzası:** `%.1f / 45 Puan` (Frekans IoU: %%% .1f, PRI IoU: %%% .1f, PW IoU: %%% .1f)\n",
            best.rfScore(), best.freqIoU(), best.priIoU(), best.pwIoU()));
        sb.append(String.format(Locale.ROOT, "  - ⏰ **Zaman Analizi:** `%.1f / 20 Puan` (Kesişim Uyumu: `%.1f P`, Zaman Yakınlığı: `%s` -> `%.0f P`)\n",
            best.timeScore(), best.timeOverlapScore(), best.timeProximityDesc(), best.timeProximityScore()));
        sb.append(String.format(Locale.ROOT, "  - 🛡️ **Coğrafya & Taktik Kimlik:** `%.1f / 35 Puan` (Radar: %s, Teşhis: %s, Yer: %s)\n",
            best.tacticalScore(), best.radarAdi(), best.teshisKimlik(), best.hedefYerBilgisi()));

        return sb.toString();
    }
}
