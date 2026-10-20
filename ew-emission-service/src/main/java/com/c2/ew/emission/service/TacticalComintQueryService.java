package com.c2.ew.emission.service;

import com.c2.ew.emission.dto.HierarchyAnalysisResultDto;

import com.c2.ew.emission.dto.CallsignPairStatsDto;
import com.c2.ew.emission.dto.TacticalComintDetailDto;
import com.c2.ew.emission.dto.TacticalComintSummaryDto;
import com.c2.ew.emission.dto.TacticalQueryResult;
import com.c2.ew.emission.dto.TablePageResponse;
import com.c2.ew.emission.domain.enums.*;
import com.c2.ew.emission.domain.model.MuhabereFrekansEntry;
import com.c2.ew.emission.infrastructure.entity.TacticalComintEntity;
import com.c2.ew.emission.infrastructure.repository.TacticalComintJpaRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Component
public class TacticalComintQueryService {

    private static final Logger log = LoggerFactory.getLogger(TacticalComintQueryService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZoneId.of("Europe/Istanbul"));

    public static String formatInstant(Instant instant) {
        if (instant == null) return "-";
        return DATE_FORMATTER.format(instant);
    }

    private static final ThreadLocal<List<String>> COMINT_CALL_LOGS = ThreadLocal.withInitial(ArrayList::new);

    public static List<String> drainCallLogs() {
        List<String> logs = new ArrayList<>(COMINT_CALL_LOGS.get());
        COMINT_CALL_LOGS.get().clear();
        return logs;
    }

    private static final Locale TR_LOCALE = Locale.forLanguageTag("tr-TR");

    private String toTrLower(String val) {
        if (val == null) return "";
        return val.trim().toLowerCase(TR_LOCALE);
    }

    private final TacticalComintJpaRepository comintRepo;
    private final ObjectMapper objectMapper;

    public TacticalComintQueryService(TacticalComintJpaRepository comintRepo, ObjectMapper objectMapper) {
        this.comintRepo = comintRepo;
        this.objectMapper = objectMapper;
    }

    private String cleanParam(String val) {
        if (val == null) return null;
        String trimmed = val.trim();
        if (trimmed.isEmpty() ||
            trimmed.equalsIgnoreCase("ALL") ||
            trimmed.equalsIgnoreCase("ANY") ||
            trimmed.equalsIgnoreCase("NULL") ||
            trimmed.equalsIgnoreCase("NONE") ||
            trimmed.equalsIgnoreCase("*") ||
            trimmed.equalsIgnoreCase("TÜMÜ") ||
            trimmed.equalsIgnoreCase("HEPSİ") ||
            trimmed.equalsIgnoreCase("TUMU") ||
            trimmed.equalsIgnoreCase("HEPSI") ||
            trimmed.equalsIgnoreCase("FARKETMEZ")) {
            return null;
        }
        return trimmed;
    }

    private TeshisKimlik normalizeTeshis(String raw) {
        String clean = cleanParam(raw);
        if (clean == null) return null;
        String upper = clean.toUpperCase(Locale.ENGLISH);

        if (clean.equalsIgnoreCase("D\u00DC\u015EMAN") || upper.contains("DUSMAN") || upper.contains("ENEMY") || upper.contains("D\u00DC\u015E")) {
            if (upper.contains("MUHTEMEL") || upper.contains("PROBABLE") || upper.contains("SUSPECT")) {
                return TeshisKimlik.MUHTEMEL_DUSMAN;
            }
            return TeshisKimlik.DUSMAN;
        }
        if (clean.equalsIgnoreCase("DOST") || upper.contains("DOST") || upper.contains("FRIEND")) {
            if (upper.contains("MUHTEMEL") || upper.contains("PROBABLE")) {
                return TeshisKimlik.MUHTEMEL_DOST;
            }
            return TeshisKimlik.DOST;
        }
        if (upper.contains("TARAFSIZ") || upper.contains("NEUTRAL")) {
            return TeshisKimlik.TARAFSIZ;
        }
        if (upper.contains("BILINMEYEN") || upper.contains("UNKNOWN") || clean.equalsIgnoreCase("B\u0130L\u0130NMEYEN")) {
            return TeshisKimlik.BILINMEYEN;
        }
        try {
            return TeshisKimlik.valueOf(upper);
        } catch (Exception e) {
            return null;
        }
    }

    private MuhabereBantTipi normalizeTip(String raw) {
        String clean = cleanParam(raw);
        if (clean == null) return null;
        String upper = clean.toUpperCase(Locale.ENGLISH);
        if (upper.contains("HF") && !upper.contains("VHF")) return MuhabereBantTipi.HF;
        if (upper.contains("VHF")) return MuhabereBantTipi.VHF;
        try {
            return MuhabereBantTipi.valueOf(upper);
        } catch (Exception e) {
            return null;
        }
    }

    private HaberlesmeSekli normalizeHaberlesmeSekli(String raw) {
        String clean = cleanParam(raw);
        if (clean == null) return null;
        String upper = clean.toUpperCase(Locale.ENGLISH);
        if (upper.contains("KRIPTO") || upper.contains("SIFRE") || upper.contains("\u015E\u0130FRE")) return HaberlesmeSekli.KRIPTO;
        if (upper.contains("DARBE") || upper.contains("BURST") || upper.contains("PATLAMA")) return HaberlesmeSekli.DARBE_PATLAMA;
        if (upper.contains("GAYDA")) return HaberlesmeSekli.KARISTIRMA_GAYDA;
        if (upper.contains("GURULTU") || upper.contains("G\u00DCR\u00DCLT\u00DC") || upper.contains("NOISE")) return HaberlesmeSekli.KARISTIRMA_GURULTU;
        if (upper.contains("VERI") || upper.contains("DATA") || upper.contains("VER\u0130")) return HaberlesmeSekli.VERI;
        if (upper.contains("SES") || upper.contains("VOICE") || upper.contains("AUDIO")) return HaberlesmeSekli.SES;
        try {
            return HaberlesmeSekli.valueOf(upper);
        } catch (Exception e) {
            return null;
        }
    }

            private volatile Map<String, Object> cachedComintMacroStats = null;
    private volatile long lastComintMacroCacheTime = 0L;

    public Map<String, Object> queryComintMacroStats() {
        return getComintMacroStats();
    }

    public Map<String, Object> getComintMacroStats() {
        COMINT_CALL_LOGS.get().add("Tool Çağrısı: getComintMacroStats() çalıştırıldı.");

        long now = System.currentTimeMillis();
        if (cachedComintMacroStats != null && (now - lastComintMacroCacheTime) < 30_000L) {
            return cachedComintMacroStats;
        }

        Map<String, Object> stats = new LinkedHashMap<>();

        long totalCount = comintRepo.count();
        long kestirimCount = comintRepo.countByKayitTipi(KayitTipi.KESTIRIM_ELIPS);
        long hamLobCount = comintRepo.countByKayitTipi(KayitTipi.HAM_LOB);
        long bagliLobCount = comintRepo.countByKayitTipiAndKestirimIdIsNotNull(KayitTipi.HAM_LOB);
        long basibosLobCount = comintRepo.countByKayitTipiAndKestirimIdIsNull(KayitTipi.HAM_LOB);

        stats.put("toplamMuhabereKaydi", totalCount);
        stats.put("kestirimSayisi", kestirimCount);
        stats.put("hamLobSayisi", hamLobCount);
        stats.put("bagliLobSayisi", bagliLobCount);
        stats.put("basibosLobSayisi", basibosLobCount);
        stats.put("dusmanMuhabereSayisi", comintRepo.countByTeshisKimlik(TeshisKimlik.DUSMAN));
        stats.put("kriptoluYayinSayisi", comintRepo.countByHaberlesmeSekli(HaberlesmeSekli.KRIPTO));
        stats.put("darbePatlamaSayisi", comintRepo.countByHaberlesmeSekli(HaberlesmeSekli.DARBE_PATLAMA));
        stats.put("karistirmaGaydaSayisi", comintRepo.countByHaberlesmeSekli(HaberlesmeSekli.KARISTIRMA_GAYDA));
        stats.put("karistirmaGurultuSayisi", comintRepo.countByHaberlesmeSekli(HaberlesmeSekli.KARISTIRMA_GURULTU));
        stats.put("hfBantSayisi", comintRepo.countByTip(MuhabereBantTipi.HF));
        stats.put("vhfBantSayisi", comintRepo.countByTip(MuhabereBantTipi.VHF));

        Map<String, Long> kayitTipiMap = new LinkedHashMap<>();
        kayitTipiMap.put("KESTIRIM_ELIPS", kestirimCount);
        kayitTipiMap.put("HAM_LOB", hamLobCount);
        kayitTipiMap.put("BAGLI_LOB", bagliLobCount);
        kayitTipiMap.put("BASIBOS_LOB", basibosLobCount);
        stats.put("kayitTipiDagilimi", kayitTipiMap);

        stats.put("teshisDagilimi", mapDistribution(comintRepo.getTeshisDistribution()));
        stats.put("haberlesmeSekliDagilimi", mapDistribution(comintRepo.getHaberlesmeSekliDistribution()));
        stats.put("tipDagilimi", mapDistribution(comintRepo.getTipDistribution()));
        stats.put("lisanDagilimi", mapDistribution(comintRepo.getLisanDistribution()));
        stats.put("veriKaynagiDagilimi", mapDistribution(comintRepo.getVeriKaynagiDistribution()));

        cachedComintMacroStats = stats;
        lastComintMacroCacheTime = now;
        return stats;
    }

    public List<TacticalComintSummaryDto> queryPriorityComintThreats(Integer limit) {
        int effectiveLimit = (limit != null && limit > 0) ? Math.min(limit, 50) : 15;
        COMINT_CALL_LOGS.get().add(String.format("Tool Çağrısı: queryPriorityComintThreats(limit=%d) çalıştırıldı.", effectiveLimit));

        Specification<TacticalComintEntity> spec = (root, query, cb) -> {
            Predicate isDusman = cb.equal(root.get("teshisKimlik"), TeshisKimlik.DUSMAN);
            Predicate isMuhtemelDusman = cb.equal(root.get("teshisKimlik"), TeshisKimlik.MUHTEMEL_DUSMAN);
            Predicate isKripto = cb.equal(root.get("haberlesmeSekli"), HaberlesmeSekli.KRIPTO);
            Predicate isDarbe = cb.equal(root.get("haberlesmeSekli"), HaberlesmeSekli.DARBE_PATLAMA);
            Predicate isGayda = cb.equal(root.get("haberlesmeSekli"), HaberlesmeSekli.KARISTIRMA_GAYDA);
            Predicate isGurultu = cb.equal(root.get("haberlesmeSekli"), HaberlesmeSekli.KARISTIRMA_GURULTU);

            return cb.or(isDusman, isMuhtemelDusman, isKripto, isDarbe, isGayda, isGurultu);
        };

        Page<TacticalComintEntity> page = comintRepo.findAll(spec, PageRequest.of(0, effectiveLimit, Sort.by(Sort.Direction.DESC, "sonTespitZamani")));
        List<TacticalComintSummaryDto> dtos = page.getContent().stream().map(this::toSummaryDto).collect(Collectors.toList());
        return dtos;
    }

    private Instant getTacticalReferenceTime() {
        try {
            Instant maxDb = comintRepo.findMaxSonTespitZamani();
            if (maxDb != null) {
                return maxDb;
            }
        } catch (Exception ignored) {}
        return Instant.now();
    }

    private Instant parseTime(String val, boolean isStart) {
        if (val == null || val.isBlank()) return null;
        String v = val.trim().toUpperCase(Locale.ENGLISH).replace(" ", "_");
        if (v.equalsIgnoreCase("ALL") || v.equalsIgnoreCase("NULL") || v.equalsIgnoreCase("*") || v.equalsIgnoreCase("NONE")) return null;

        Instant refTime = getTacticalReferenceTime();

        // Dinamik "SON_X_SAAT" regex kontrolü
        if (v.matches(".*SON_?(\\d+)_?SAAT.*")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("SON_?(\\d+)_?SAAT").matcher(v);
            if (m.find()) {
                long hours = Long.parseLong(m.group(1));
                return isStart ? refTime.minus(Duration.ofHours(hours)) : refTime;
            }
        }
        if (v.matches(".*SON_?(\\d+)_?(DAKIKA|DK).*")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("SON_?(\\d+)_?(DAKIKA|DK)").matcher(v);
            if (m.find()) {
                long mins = Long.parseLong(m.group(1));
                return isStart ? refTime.minus(Duration.ofMinutes(mins)) : refTime;
            }
        }
        if (v.matches(".*SON_?(\\d+)_?(GUN|GÜN).*")) {
            java.util.regex.Matcher m = java.util.regex.Pattern.compile("SON_?(\\d+)_?(GUN|GÜN)").matcher(v);
            if (m.find()) {
                long days = Long.parseLong(m.group(1));
                return isStart ? refTime.minus(Duration.ofDays(days)) : refTime;
            }
        }

        if (v.contains("SON_1_SAAT") || v.contains("1_SAAT")) return isStart ? refTime.minus(Duration.ofHours(1)) : refTime;
        if (v.contains("SON_6_SAAT") || v.contains("6_SAAT")) return isStart ? refTime.minus(Duration.ofHours(6)) : refTime;
        if (v.contains("SON_12_SAAT") || v.contains("12_SAAT")) return isStart ? refTime.minus(Duration.ofHours(12)) : refTime;
        if (v.contains("SON_24_SAAT") || v.contains("24_SAAT") || v.contains("1_GUN")) return isStart ? refTime.minus(Duration.ofDays(1)) : refTime;
        if (v.contains("SON_48_SAAT") || v.contains("48_SAAT") || v.contains("2_GUN")) return isStart ? refTime.minus(Duration.ofDays(2)) : refTime;
        if (v.contains("SON_1_HAFTA") || v.contains("7_GUN") || v.contains("1_HAFTA") || v.contains("GECEN_HAFTA") || v.contains("HAFTA")) return isStart ? refTime.minus(Duration.ofDays(7)) : refTime;

        LocalDate refDate = refTime.atZone(ZoneId.systemDefault()).toLocalDate();
        if (v.contains("BUGUN") || v.contains("TODAY")) {
            Instant startOfToday = refDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
            return isStart ? startOfToday : refTime;
        }
        if (v.contains("NOW") || v.contains("SU_AN") || v.contains("SIMDI")) return refTime;
        if (v.contains("DUN") || v.contains("YESTERDAY")) {
            Instant startOfYesterday = refDate.minusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant();
            Instant endOfYesterday = refDate.atStartOfDay(ZoneId.systemDefault()).toInstant();
            return isStart ? startOfYesterday : endOfYesterday;
        }

        // Türkçe format (GG.AA.YYYY SS:dd:ss veya GG.AA.YYYY)
        try {
            java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
            return java.time.LocalDateTime.parse(val.trim(), dtf).atZone(ZoneId.systemDefault()).toInstant();
        } catch (Exception ignored) {}
        try {
            java.time.format.DateTimeFormatter dtf = java.time.format.DateTimeFormatter.ofPattern("dd.MM.yyyy");
            LocalDate ld = LocalDate.parse(val.trim(), dtf);
            return isStart ? ld.atStartOfDay(ZoneId.systemDefault()).toInstant() : ld.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().minusMillis(1);
        } catch (Exception ignored) {}

        // Standart ISO
        try { return Instant.parse(val.trim()); } catch (Exception e1) {
            try {
                LocalDate ld = LocalDate.parse(val.trim());
                return isStart ? ld.atStartOfDay(ZoneId.systemDefault()).toInstant() : ld.plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().minusMillis(1);
            } catch (Exception e2) { return null; }
        }
    }

    /**
     * Tüm muhabere / COMINT kayıtlarını (120 adet) doğrudan getirir (Ant Design Muhabere Tablosu için).
     */
    public List<TacticalComintSummaryDto> getAllComints() {
        return comintRepo.findAll(PageRequest.of(0, 100, Sort.by(Sort.Direction.DESC, "sonTespitZamani")))
            .getContent()
            .stream()
            .map(this::toSummaryDto)
            .collect(Collectors.toList());
    }

    /**
     * Ant Design Tablosu için Sunucu Taraflı Sayfalama ve Filtreleme (Server-Side Pagination).
     */
    public TablePageResponse<TacticalComintSummaryDto> getComintsPage(
        int page,
        int size,
        String search,
        String teshis,
        String tip,
        String lisan,
        String haberlesme,
        Boolean kripto,
        Double minFrekans,
        Double maxFrekans,
        Double minGenlik,
        Double maxGenlik,
        String bolge,
        String protokol,
        String cagriAdi,
        String karsiCagriAdi,
        String sortField,
        String sortOrder,
        String kayitTipi,
        String zamanAraligi
    ) {
        int pageIdx = Math.max(0, page - 1);
        int pageSize = (size <= 0) ? 15 : Math.min(size, 100);

        Sort.Direction direction = "ascend".equalsIgnoreCase(sortOrder) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String field = "sonTespitZamani";
        if (sortField != null) {
            String sf = sortField.trim();
            if ("frekans".equalsIgnoreCase(sf)) sf = "minFrekansMhz";
            Set<String> validFields = Set.of(
                "id", "cagriAdi", "karsiCagriAdi", "teshisKimlik", "tip", "lisan", 
                "protokol", "haberlesmeSekli", "calismaSekli", "minFrekansMhz", "maxFrekansMhz", 
                "genlikDbm", "sureSn", "sonTespitZamani", "hedefYerBilgisi", "veriKaynagi", "kayitTipi"
            );
            if (validFields.contains(sf)) {
                field = sf;
            }
        }

        Specification<TacticalComintEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (kayitTipi != null && !kayitTipi.isBlank()) {
                try {
                    KayitTipi kt = KayitTipi.valueOf(kayitTipi.trim().toUpperCase(Locale.ROOT));
                    predicates.add(cb.equal(root.get("kayitTipi"), kt));
                } catch (Exception ignored) {}
            }

            if (search != null && !search.isBlank()) {
                String pattern = "%" + toTrLower(search) + "%";
                Predicate matchId = cb.like(cb.lower(root.get("id")), pattern);
                Predicate matchCagri = cb.like(cb.lower(root.get("cagriAdi")), pattern);
                Predicate matchKarsi = cb.like(cb.lower(root.get("karsiCagriAdi")), pattern);
                Predicate matchLisan = cb.like(cb.lower(root.get("lisan")), pattern);
                Predicate matchYer = cb.like(cb.lower(root.get("hedefYerBilgisi")), pattern);
                predicates.add(cb.or(matchId, matchCagri, matchKarsi, matchLisan, matchYer));
            }

            if (cagriAdi != null && !cagriAdi.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("cagriAdi")), "%" + toTrLower(cagriAdi) + "%"));
            }

            if (karsiCagriAdi != null && !karsiCagriAdi.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("karsiCagriAdi")), "%" + toTrLower(karsiCagriAdi) + "%"));
            }

            TeshisKimlik tk = normalizeTeshis(teshis);
            if (tk != null) {
                predicates.add(cb.equal(root.get("teshisKimlik"), tk));
            }

            if (tip != null && !tip.isBlank()) {
                try {
                    predicates.add(cb.equal(root.get("tip"), MuhabereBantTipi.valueOf(tip.trim().toUpperCase())));
                } catch (IllegalArgumentException ignored) {}
            }

            if (lisan != null && !lisan.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("lisan")), "%" + toTrLower(lisan) + "%"));
            }

            HaberlesmeSekli hs = normalizeHaberlesmeSekli(haberlesme);
            if (hs != null) {
                predicates.add(cb.equal(root.get("haberlesmeSekli"), hs));
            }

            if (kripto != null) {
                if (Boolean.TRUE.equals(kripto)) {
                    predicates.add(cb.equal(root.get("haberlesmeSekli"), HaberlesmeSekli.KRIPTO));
                } else {
                    predicates.add(cb.notEqual(root.get("haberlesmeSekli"), HaberlesmeSekli.KRIPTO));
                }
            }

            // Gelişmiş filtreler
            if (minFrekans != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("maxFrekansMhz"), minFrekans));
            }
            if (maxFrekans != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("minFrekansMhz"), maxFrekans));
            }
            if (minGenlik != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("genlikDbm"), minGenlik));
            }
            if (maxGenlik != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("genlikDbm"), maxGenlik));
            }
            if (bolge != null && !bolge.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("hedefYerBilgisi")), "%" + toTrLower(bolge) + "%"));
            }
            if (protokol != null && !protokol.isBlank()) {
                try {
                    predicates.add(cb.equal(root.get("protokol"), MuhabereProtokol.valueOf(protokol.trim().toUpperCase())));
                } catch (IllegalArgumentException ignored) {}
            }

            if (zamanAraligi != null && !zamanAraligi.isBlank()) {
                Instant start = parseTime(zamanAraligi, true);
                Instant rawEnd = parseTime(zamanAraligi, false);
                Instant end = (start != null && rawEnd == null) ? getTacticalReferenceTime() : rawEnd;
                if (start != null) {
                    predicates.add(cb.greaterThanOrEqualTo(root.get("sonTespitZamani"), start));
                }
                if (end != null) {
                    predicates.add(cb.lessThanOrEqualTo(root.get("sonTespitZamani"), end));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<TacticalComintEntity> springPage = comintRepo.findAll(spec, PageRequest.of(pageIdx, pageSize, Sort.by(direction, field)));
        List<TacticalComintSummaryDto> dtos = springPage.getContent().stream().map(this::toSummaryDto).collect(Collectors.toList());
        return new TablePageResponse<>(dtos, page, pageSize, springPage.getTotalElements(), springPage.getTotalPages());
    }

    public TablePageResponse<TacticalComintSummaryDto> getComintsPage(
        int page,
        int size,
        String search,
        String teshis,
        String tip,
        String lisan,
        String haberlesme,
        Boolean kripto,
        Double minFrekans,
        Double maxFrekans,
        Double minGenlik,
        Double maxGenlik,
        String bolge,
        String protokol,
        String cagriAdi,
        String karsiCagriAdi,
        String sortField,
        String sortOrder,
        String kayitTipi
    ) {
        return getComintsPage(page, size, search, teshis, tip, lisan, haberlesme, kripto,
            minFrekans, maxFrekans, minGenlik, maxGenlik, bolge, protokol, cagriAdi, karsiCagriAdi, sortField, sortOrder, kayitTipi, null);
    }

    public TablePageResponse<TacticalComintSummaryDto> getComintsPage(
        int page,
        int size,
        String search,
        String teshis,
        String tip,
        String lisan,
        String haberlesme,
        Boolean kripto,
        Double minFrekans,
        Double maxFrekans,
        Double minGenlik,
        Double maxGenlik,
        String bolge,
        String protokol,
        String sortField,
        String sortOrder,
        String kayitTipi
    ) {
        return getComintsPage(page, size, search, teshis, tip, lisan, haberlesme, kripto,
            minFrekans, maxFrekans, minGenlik, maxGenlik, bolge, protokol, null, null, sortField, sortOrder, kayitTipi, null);
    }

    public TablePageResponse<TacticalComintSummaryDto> getComintsPage(
        int page,
        int size,
        String search,
        String teshis,
        String tip,
        String lisan,
        String haberlesme,
        Boolean kripto,
        Double minFrekans,
        Double maxFrekans,
        Double minGenlik,
        Double maxGenlik,
        String bolge,
        String protokol,
        String sortField,
        String sortOrder
    ) {
        return getComintsPage(page, size, search, teshis, tip, lisan, haberlesme, kripto,
            minFrekans, maxFrekans, minGenlik, maxGenlik, bolge, protokol, sortField, sortOrder, null);
    }

    public TablePageResponse<TacticalComintSummaryDto> getComintsPage(
        int page,
        int size,
        String search,
        String teshis,
        String tip,
        String lisan,
        String haberlesme,
        Boolean kripto,
        String sortField,
        String sortOrder
    ) {
        return getComintsPage(page, size, search, teshis, tip, lisan, haberlesme, kripto,
            null, null, null, null, null, null, sortField, sortOrder, null);
    }

    public TacticalQueryResult<TacticalComintSummaryDto> queryComintEmissions(
        String teshisKimlik,
        String veriKaynagi,
        String lisan,
        String tip,
        String haberlesmeSekli,
        String protokol,
        String modulasyon,
        String cagriAdi,
        String bolge,
        Boolean mti,
        String kayitTipi,
        Double minFrekansMhz,
        Double maxFrekansMhz,
        Double minGenlikDbm,
        Double maxGenlikDbm,
        Double minSureSn,
        Double maxSureSn,
        String baslangicZamani,
        String bitisZamani,
        Integer limit
    ) {
        String cleanTeshis = cleanParam(teshisKimlik);
        String cleanKaynak = cleanParam(veriKaynagi);
        String cleanLisan = cleanParam(lisan);
        String cleanTip = cleanParam(tip);
        String cleanHaberlesme = cleanParam(haberlesmeSekli);
        String cleanProtokol = cleanParam(protokol);
        String cleanModulasyon = cleanParam(modulasyon);
        String cleanCagri = cleanParam(cagriAdi);
        String cleanBolge = cleanParam(bolge);
        String cleanKayitTipi = cleanParam(kayitTipi);
        String cleanBaslangic = cleanParam(baslangicZamani);
        String cleanBitis = cleanParam(bitisZamani);

        // Dummy LLM aralıklarını filtrele
        Double effMinFreq = (minFrekansMhz != null && minFrekansMhz > 1.0) ? minFrekansMhz : null;
        Double effMaxFreq = (maxFrekansMhz != null && maxFrekansMhz > 1.0 && maxFrekansMhz < 100000.0) ? maxFrekansMhz : null;

        int maxLimit = (limit == null || limit <= 0) ? 10 : Math.min(limit, 50);

        log.info("queryComintEmissions called with teshis={}, lisan={}, tip={}, bolge={}, kayitTipi={}, freq=[{}-{}], time=[{}-{}], limit={}",
            cleanTeshis, cleanLisan, cleanTip, cleanBolge, cleanKayitTipi, effMinFreq, effMaxFreq, cleanBaslangic, cleanBitis, maxLimit);

        COMINT_CALL_LOGS.get().add(String.format("Tool: queryComintEmissions(teshis=%s, lisan=%s, tip=%s, bolge=%s, kayitTipi=%s, freq=[%s-%s], zaman=[%s-%s], limit=%d)",
            cleanTeshis, cleanLisan, cleanTip, cleanBolge, cleanKayitTipi, effMinFreq, effMaxFreq, cleanBaslangic, cleanBitis, maxLimit));

        Specification<TacticalComintEntity> spec = createComintSpecification(
            cleanTeshis, cleanKaynak, cleanLisan, cleanTip, cleanHaberlesme, cleanProtokol, cleanModulasyon, cleanCagri, cleanBolge, mti, cleanKayitTipi,
            effMinFreq, effMaxFreq, minGenlikDbm, maxGenlikDbm, minSureSn, maxSureSn, cleanBaslangic, cleanBitis
        );

        Page<TacticalComintEntity> resultPage = comintRepo.findAll(spec, PageRequest.of(0, maxLimit, Sort.by(Sort.Direction.DESC, "sonTespitZamani")));

        List<TacticalComintSummaryDto> dtos = resultPage.getContent().stream().map(this::toSummaryDto).collect(Collectors.toList());
        return TacticalQueryResult.of(dtos, resultPage.getTotalElements(), "📻 Muhabere (COMINT) Tablosu");
    }

    public long countComintEmissions(
        String teshisKimlik,
        String veriKaynagi,
        String lisan,
        String tip,
        String haberlesmeSekli,
        String protokol,
        String modulasyon,
        String cagriAdi,
        String bolge,
        Boolean mti,
        String kayitTipi,
        Double minFrekansMhz,
        Double maxFrekansMhz,
        Double minGenlikDbm,
        Double maxGenlikDbm,
        Double minSureSn,
        Double maxSureSn,
        String baslangicZamani,
        String bitisZamani
    ) {
        String cleanTeshis = cleanParam(teshisKimlik);
        String cleanKaynak = cleanParam(veriKaynagi);
        String cleanLisan = cleanParam(lisan);
        String cleanTip = cleanParam(tip);
        String cleanHaberlesme = cleanParam(haberlesmeSekli);
        String cleanProtokol = cleanParam(protokol);
        String cleanModulasyon = cleanParam(modulasyon);
        String cleanCagri = cleanParam(cagriAdi);
        String cleanBolge = cleanParam(bolge);
        String cleanKayitTipi = cleanParam(kayitTipi);
        String cleanBaslangic = cleanParam(baslangicZamani);
        String cleanBitis = cleanParam(bitisZamani);

        Double effMinFreq = (minFrekansMhz != null && minFrekansMhz > 1.0) ? minFrekansMhz : null;
        Double effMaxFreq = (maxFrekansMhz != null && maxFrekansMhz > 1.0 && maxFrekansMhz < 100000.0) ? maxFrekansMhz : null;

        log.info("countComintEmissions called with teshis={}, lisan={}, tip={}, cagri={}, bolge={}", cleanTeshis, cleanLisan, cleanTip, cleanCagri, cleanBolge);
        COMINT_CALL_LOGS.get().add(String.format("Tool: countComintEmissions(teshis=%s, lisan=%s, tip=%s, cagri=%s, bolge=%s)", cleanTeshis, cleanLisan, cleanTip, cleanCagri, cleanBolge));

        Specification<TacticalComintEntity> spec = createComintSpecification(
            cleanTeshis, cleanKaynak, cleanLisan, cleanTip, cleanHaberlesme, cleanProtokol, cleanModulasyon, cleanCagri, cleanBolge, mti, cleanKayitTipi,
            effMinFreq, effMaxFreq, minGenlikDbm, maxGenlikDbm, minSureSn, maxSureSn, cleanBaslangic, cleanBitis
        );

        return comintRepo.count(spec);
    }

    // Geriye dönük uyumluluk için kayitTipi içermeyen 19 parametreli overload
    public TacticalQueryResult<TacticalComintSummaryDto> queryComintEmissions(
        String teshisKimlik,
        String veriKaynagi,
        String lisan,
        String tip,
        String haberlesmeSekli,
        String protokol,
        String modulasyon,
        String cagriAdi,
        String bolge,
        Boolean mti,
        Double minFrekansMhz,
        Double maxFrekansMhz,
        Double minGenlikDbm,
        Double maxGenlikDbm,
        Double minSureSn,
        Double maxSureSn,
        String baslangicZamani,
        String bitisZamani,
        Integer limit
    ) {
        return queryComintEmissions(
            teshisKimlik, veriKaynagi, lisan, tip, haberlesmeSekli, protokol, modulasyon,
            cagriAdi, bolge, mti, null, minFrekansMhz, maxFrekansMhz, minGenlikDbm, maxGenlikDbm,
            minSureSn, maxSureSn, baslangicZamani, bitisZamani, limit
        );
    }

    // Java çağrıları ve testler için geriye uyumluluk overload metodu
    public TacticalQueryResult<TacticalComintSummaryDto> queryComintEmissions(
        String teshisKimlik,
        String veriKaynagi,
        String lisan,
        String tip,
        String haberlesmeSekli,
        String protokol,
        String modulasyon,
        String cagriAdi,
        Boolean mti,
        Double minFrekansMhz,
        Double maxFrekansMhz,
        Double minGenlikDbm,
        Double maxGenlikDbm,
        Integer sayfaNo,
        Integer sayfaBoyutu
    ) {
        return queryComintEmissions(
            teshisKimlik, veriKaynagi, lisan, tip, haberlesmeSekli, protokol, modulasyon, cagriAdi,
            null, mti, minFrekansMhz, maxFrekansMhz, minGenlikDbm, maxGenlikDbm, null, null, null, null,
            sayfaBoyutu
        );
    }

    public List<CallsignPairStatsDto> getCallsignPairInteractionStats(
        String cagriAdi,
        String karsiCagriAdi,
        String baslangicZamani,
        String bitisZamani,
        Integer limit
    ) {
        String cleanCagri = cleanParam(cagriAdi);
        String cleanKarsi = cleanParam(karsiCagriAdi);
        String cleanBaslangic = cleanParam(baslangicZamani);
        String cleanBitis = cleanParam(bitisZamani);

        Instant start = (cleanBaslangic != null && !cleanBaslangic.equalsIgnoreCase("ALL"))
            ? parseTime(cleanBaslangic, true)
            : null;
        Instant end = (cleanBitis != null && !cleanBitis.equalsIgnoreCase("ALL"))
            ? parseTime(cleanBitis, false)
            : null;
        if (start != null && end == null) {
            end = getTacticalReferenceTime();
        }

        int max = (limit == null || limit <= 0) ? 15 : Math.min(limit, 50);
        COMINT_CALL_LOGS.get().add(String.format("Tool Çağrısı: getCallsignPairInteractionStats(cagriAdi=%s, karsiCagriAdi=%s, zaman=[%s-%s], limit=%d) çalıştırıldı.",
            cleanCagri, cleanKarsi, cleanBaslangic, cleanBitis, max));

        Pageable pageable = PageRequest.of(0, max);
        List<Object[]> rows;
        if (cleanCagri != null || cleanKarsi != null) {
            rows = (start != null && end != null)
                ? comintRepo.getFilteredCallsignInteractionMatrixWithTimeRange(cleanCagri, cleanKarsi, start, end, pageable)
                : comintRepo.getFilteredCallsignInteractionMatrix(cleanCagri, cleanKarsi, pageable);
        } else {
            rows = (start != null && end != null)
                ? comintRepo.getCallsignInteractionMatrixWithTimeRange(start, end, pageable)
                : comintRepo.getCallsignInteractionMatrix(pageable);
        }

        List<CallsignPairStatsDto> result = new ArrayList<>();
        for (Object[] row : rows) {
            String cagri = row[0] != null ? row[0].toString() : "-";
            String karsi = row[1] != null ? row[1].toString() : "-";
            long count = row[2] != null ? ((Number) row[2]).longValue() : 0L;
            double sure = row[3] != null ? ((Number) row[3]).doubleValue() : 0.0;
            long vhf = row[4] != null ? ((Number) row[4]).longValue() : 0L;
            long hf = row[5] != null ? ((Number) row[5]).longValue() : 0L;
            long kripto = row[6] != null ? ((Number) row[6]).longValue() : 0L;
            long ses = row[7] != null ? ((Number) row[7]).longValue() : 0L;
            long veri = row[8] != null ? ((Number) row[8]).longValue() : 0L;
            String baskinLisan = row[9] != null ? row[9].toString() : "-";
            result.add(new CallsignPairStatsDto(cagri, karsi, count, sure, vhf, hf, kripto, ses, veri, baskinLisan));
        }
        return result;
    }

    public List<CallsignPairStatsDto> getCallsignPairInteractionStats(String cagriAdi, String karsiCagriAdi, Integer limit) {
        return getCallsignPairInteractionStats(cagriAdi, karsiCagriAdi, null, null, limit);
    }

    public List<CallsignPairStatsDto> getCallsignPairInteractionStats(String cagriAdi, String karsiCagriAdi) {
        return getCallsignPairInteractionStats(cagriAdi, karsiCagriAdi, null, null, 15);
    }

    public List<CallsignPairStatsDto> getCallsignPairInteractionStats(Integer limit) {
        return getCallsignPairInteractionStats(null, null, null, null, limit);
    }

    public List<CallsignPairStatsDto> getCallsignPairInteractionStats() {
        return getCallsignPairInteractionStats(null, null, null, null, 15);
    }

    public Map<String, Long> getMostActiveCallsigns(String baslangicZamani, String bitisZamani, Integer limit) {
        String cleanBaslangic = cleanParam(baslangicZamani);
        String cleanBitis = cleanParam(bitisZamani);
        Instant start = (cleanBaslangic != null && !cleanBaslangic.equalsIgnoreCase("ALL"))
            ? parseTime(cleanBaslangic, true)
            : null;
        Instant end = (cleanBitis != null && !cleanBitis.equalsIgnoreCase("ALL"))
            ? parseTime(cleanBitis, false)
            : null;
        if (start != null && end == null) {
            end = getTacticalReferenceTime();
        }

        int max = (limit == null || limit <= 0) ? 10 : Math.min(limit, 30);
        COMINT_CALL_LOGS.get().add(String.format("Tool Çağrısı: getMostActiveCallsigns(zaman=[%s-%s], limit=%d) çalıştırıldı.", cleanBaslangic, cleanBitis, max));

        Pageable pageable = PageRequest.of(0, max);
        List<Object[]> rows = (start != null && end != null)
            ? comintRepo.getTopCallsignsDistributionWithTimeRange(start, end, pageable)
            : comintRepo.getTopCallsignsDistribution(pageable);
        Map<String, Long> result = new LinkedHashMap<>();
        for (Object[] row : rows) {
            result.put(String.valueOf(row[0]), ((Number) row[1]).longValue());
        }
        return result;
    }

    public Map<String, Long> getMostActiveCallsigns(Integer limit) {
        return getMostActiveCallsigns(null, null, limit);
    }

    public Map<String, Long> getMostActiveCallsigns() {
        return getMostActiveCallsigns(null, null, 10);
    }

    public List<TacticalComintSummaryDto> queryComintCallsignNetwork(
        String cagriAdi,
        String karsiCagriAdi,
        String baslangicZamani,
        String bitisZamani,
        Integer limit
    ) {
        String clean = cleanParam(cagriAdi);
        String cleanKarsi = cleanParam(karsiCagriAdi);
        String cleanBaslangic = cleanParam(baslangicZamani);
        String cleanBitis = cleanParam(bitisZamani);
        if (clean == null && cleanKarsi == null) return List.of();

        Instant start = parseTime(cleanBaslangic, true);
        Instant end = parseTime(cleanBitis, false);
        final Instant finalEnd = (start != null && end == null) ? getTacticalReferenceTime() : end;
        final Instant finalStart = start;

        int max = (limit == null || limit <= 0) ? 15 : Math.min(limit, 30);
        COMINT_CALL_LOGS.get().add(String.format("Tool Çağrısı: queryComintCallsignNetwork(cagriAdi=%s, karsiCagriAdi=%s, zaman=[%s-%s], limit=%d) çalıştırıldı.",
            clean, cleanKarsi, cleanBaslangic, cleanBitis, max));

        Pageable pageable = PageRequest.of(0, max, Sort.by(Sort.Direction.DESC, "sonTespitZamani"));
        Specification<TacticalComintEntity> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (clean != null && cleanKarsi != null) {
                Predicate matchForward = cb.and(
                    cb.like(cb.lower(root.get("cagriAdi")), "%" + clean.toLowerCase() + "%"),
                    cb.like(cb.lower(root.get("karsiCagriAdi")), "%" + cleanKarsi.toLowerCase() + "%")
                );
                Predicate matchReverse = cb.and(
                    cb.like(cb.lower(root.get("cagriAdi")), "%" + cleanKarsi.toLowerCase() + "%"),
                    cb.like(cb.lower(root.get("karsiCagriAdi")), "%" + clean.toLowerCase() + "%")
                );
                predicates.add(cb.or(matchForward, matchReverse));
            } else {
                String searchCall = clean != null ? clean : cleanKarsi;
                if (searchCall != null) {
                    predicates.add(cb.or(
                        cb.like(cb.lower(root.get("cagriAdi")), "%" + searchCall.toLowerCase() + "%"),
                        cb.like(cb.lower(root.get("karsiCagriAdi")), "%" + searchCall.toLowerCase() + "%")
                    ));
                }
            }
            if (finalStart != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("sonTespitZamani"), finalStart));
            }
            if (finalEnd != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("sonTespitZamani"), finalEnd));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        };

        List<TacticalComintEntity> list = comintRepo.findAll(spec, pageable).getContent();
        return list.stream().map(this::toSummaryDto).collect(Collectors.toList());
    }

    public List<TacticalComintSummaryDto> queryComintCallsignNetwork(String cagriAdi, String karsiCagriAdi, Integer limit) {
        return queryComintCallsignNetwork(cagriAdi, karsiCagriAdi, null, null, limit);
    }

    public List<TacticalComintSummaryDto> queryComintCallsignNetwork(String cagriAdi, Integer limit) {
        return queryComintCallsignNetwork(cagriAdi, null, null, null, limit);
    }

    public List<TacticalComintSummaryDto> queryComintCallsignNetwork(String cagriAdi) {
        return queryComintCallsignNetwork(cagriAdi, null, null, null, 15);
    }

    public Map<String, Object> getComintTrafficTrend(String cagriAdi, String baslangicZamani, String bitisZamani) {
        String cleanCagri = cleanParam(cagriAdi);
        String cleanBaslangic = cleanParam(baslangicZamani);
        String cleanBitis = cleanParam(bitisZamani);

        if (cleanBaslangic == null) {
            cleanBaslangic = "SON_24_SAAT";
        }

        Instant start = parseTime(cleanBaslangic, true);
        Instant end = parseTime(cleanBitis, false);
        if (start == null) {
            start = getTacticalReferenceTime().minus(Duration.ofHours(24));
        }
        if (end == null) {
            end = getTacticalReferenceTime();
        }

        COMINT_CALL_LOGS.get().add(String.format("Tool Çağrısı: getComintTrafficTrend(cagriAdi=%s, zaman=[%s-%s]) çalıştırıldı.",
            cleanCagri, cleanBaslangic, cleanBitis));

        List<Object[]> rows = comintRepo.getHourlyTrafficTrend(cleanCagri, start, end);
        Map<String, Long> saatlikAktivite = new LinkedHashMap<>();
        long totalCount = 0;
        long maxCount = 0;
        String peakHour = "-";

        for (Object[] row : rows) {
            String saat = row[0] != null ? row[0].toString() : "-";
            long count = row[1] != null ? ((Number) row[1]).longValue() : 0L;
            saatlikAktivite.put(saat, count);
            totalCount += count;
            if (count > maxCount) {
                maxCount = count;
                peakHour = saat + " (" + count + " yayın)";
            }
        }

        double avg = saatlikAktivite.isEmpty() ? 0 : (double) totalCount / saatlikAktivite.size();
        String hareketlenmeDurumu;
        if (saatlikAktivite.isEmpty()) {
            hareketlenmeDurumu = "BELİRTİLEN ZAMAN ARALIĞINDA HABERLEŞME TESPİT EDİLMEDİ (SESSİZLİK)";
        } else if (maxCount > avg * 1.5) {
            hareketlenmeDurumu = String.format("BELİRGİN HAREKETLENME TESPİT EDİLDİ (Pik saatte ortalamanın %.1f katı yoğunluk)", (double) maxCount / (avg > 0 ? avg : 1));
        } else {
            hareketlenmeDurumu = "STABİL VE DÜZENLİ TAKTİK TRAFİK";
        }

        DateTimeFormatter dtf = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZoneId.systemDefault());
        Map<String, Object> response = new LinkedHashMap<>();
        response.put("hedefCagriAdi", cleanCagri != null ? cleanCagri : "TÜM HABERLEŞME AĞI");
        response.put("analizPenceresi", cleanBaslangic);
        response.put("baslangicZamani", start != null ? dtf.format(start) : "-");
        response.put("bitisZamani", end != null ? dtf.format(end) : "-");
        response.put("toplamYayinAdedi", totalCount);
        response.put("aktifSaatSayisi", saatlikAktivite.size());
        response.put("ortalamaSaatlikYayin", Math.round(avg));
        response.put("enYogunSaat", peakHour);
        response.put("hareketlenmeDurumu", hareketlenmeDurumu);
        response.put("saatlikAktiviteDagilimi", saatlikAktivite);

        return response;
    }

    public Map<String, Object> getComintTrafficTrend(String cagriAdi) {
        return getComintTrafficTrend(cagriAdi, "SON_24_SAAT", null);
    }

    public Map<String, Object> getComintTrafficTrend() {
        return getComintTrafficTrend(null, "SON_24_SAAT", null);
    }

    public TacticalComintDetailDto getComintDetails(String comintId) {
        String cleanId = cleanParam(comintId);
        if (cleanId == null) return null;
        COMINT_CALL_LOGS.get().add(String.format("Tool Çağrısı: getComintDetails(comintId=%s) çalıştırıldı.", cleanId));

        return comintRepo.findById(cleanId).map(this::toDetailDto).orElse(null);
    }

    private KayitTipi normalizeKayitTipi(String val) {
        if (val == null) return null;
        String v = val.toUpperCase(Locale.ENGLISH).replace("İ", "I");
        if (v.contains("LOB") || v.contains("KERTERIZ") || v.contains("STROBE")) return KayitTipi.HAM_LOB;
        if (v.contains("KESTIRIM") || v.contains("ELIPS") || v.contains("FIX")) return KayitTipi.KESTIRIM_ELIPS;
        try { return KayitTipi.valueOf(v); } catch (Exception ignored) { return null; }
    }

    private Specification<TacticalComintEntity> createComintSpecification(
        String teshis, String kaynak, String lisan, String tip, String haberlesme, String protokol, String modulasyon, String cagri, String bolge, Boolean mti, String kayitTipi,
        Double minFreq, Double maxFreq, Double minGenlik, Double maxGenlik, Double minSure, Double maxSure, String baslangicZamani, String bitisZamani
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            KayitTipi normKayit = normalizeKayitTipi(kayitTipi);
            if (normKayit != null) {
                predicates.add(cb.equal(root.get("kayitTipi"), normKayit));
            }

            if (teshis != null) {
                TeshisKimlik normTeshis = normalizeTeshis(teshis);
                if (normTeshis != null) {
                    predicates.add(cb.equal(root.get("teshisKimlik"), normTeshis));
                }
            }

            if (kaynak != null) {
                try {
                    predicates.add(cb.equal(root.get("veriKaynagi"), VeriKaynagi.valueOf(kaynak.toUpperCase())));
                } catch (Exception ignored) {}
            }

            if (lisan != null) {
                predicates.add(cb.like(cb.lower(root.get("lisan")), "%" + lisan.toLowerCase(Locale.ENGLISH) + "%"));
            }

            if (tip != null) {
                MuhabereBantTipi normTip = normalizeTip(tip);
                if (normTip != null) {
                    predicates.add(cb.equal(root.get("tip"), normTip));
                }
            }

            if (haberlesme != null) {
                HaberlesmeSekli normHaberlesme = normalizeHaberlesmeSekli(haberlesme);
                if (normHaberlesme != null) {
                    predicates.add(cb.equal(root.get("haberlesmeSekli"), normHaberlesme));
                }
            }

            if (protokol != null) {
                try {
                    predicates.add(cb.equal(root.get("protokol"), MuhabereProtokol.valueOf(protokol.toUpperCase())));
                } catch (Exception ignored) {}
            }

            if (modulasyon != null) {
                try {
                    predicates.add(cb.equal(root.get("modulasyon"), MuhabereModulasyon.valueOf(modulasyon.toUpperCase())));
                } catch (Exception ignored) {}
            }

            if (cagri != null) {
                Predicate byCagri = cb.like(cb.lower(root.get("cagriAdi")), "%" + toTrLower(cagri) + "%");
                Predicate byKarsi = cb.like(cb.lower(root.get("karsiCagriAdi")), "%" + toTrLower(cagri) + "%");
                predicates.add(cb.or(byCagri, byKarsi));
            }

            if (bolge != null) {
                predicates.add(cb.like(cb.lower(root.get("hedefYerBilgisi")), "%" + toTrLower(bolge) + "%"));
            }

            if (mti != null) {
                predicates.add(cb.equal(root.get("mti"), mti));
            }

            // Interval Overlap (Aralık Kesişimi) Sorguları:
            // Hedefin [minFrekansMhz, maxFrekansMhz] aralığı kullanıcının aradığı [minFreq, maxFreq] ile kesişmeli
            if (minFreq != null && maxFreq != null) {
                predicates.add(cb.and(
                    cb.lessThanOrEqualTo(root.get("minFrekansMhz"), maxFreq),
                    cb.greaterThanOrEqualTo(root.get("maxFrekansMhz"), minFreq)
                ));
            } else if (minFreq != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("maxFrekansMhz"), minFreq));
            } else if (maxFreq != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("minFrekansMhz"), maxFreq));
            }

            if (minGenlik != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("genlikDbm"), minGenlik));
            }
            if (maxGenlik != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("genlikDbm"), maxGenlik));
            }

            // ⏳ Süre (sn) Aralığı
            if (minSure != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("sureSn"), minSure));
            }
            if (maxSure != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("sureSn"), maxSure));
            }

            // 📅 Tarih / Zaman Aralığı (sonTespitZamani)
            Instant startTime = parseTime(baslangicZamani, true);
            Instant endTime = parseTime(bitisZamani, false);
            if (startTime != null && endTime == null) {
                endTime = getTacticalReferenceTime();
            }
            if (startTime != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("sonTespitZamani"), startTime));
            }
            if (endTime != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("sonTespitZamani"), endTime));
            }

            return predicates.isEmpty() ? cb.conjunction() : cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    public TacticalComintSummaryDto toSummaryDto(TacticalComintEntity e) {
        return toStaticDto(e);
    }

    public static TacticalComintSummaryDto toStaticDto(TacticalComintEntity e) {
        return new TacticalComintSummaryDto(
            e.getId(),
            e.getVeriKaynagi() != null ? e.getVeriKaynagi().name() : null,
            e.getKuvvetSiraNo(),
            e.getGoreviIcraEdenUstBirlik(),
            e.getGoreviIcraEdenEhUnsuru(),
            e.getYon(),
            e.getEhUnsuruEnlem(),
            e.getEhUnsuruBoylam(),
            e.getYayinEnlem(),
            e.getYayinBoylam(),
            e.getYayinSemiMajorMeters(),
            e.getYayinSemiMinorMeters(),
            e.getYayinOrientationDegrees(),
            e.getHedefYerBilgisi(),
            e.getHedefKaynagi() != null ? e.getHedefKaynagi().name() : null,
            e.getLisan(),
            e.getProtokol() != null ? e.getProtokol().name() : null,
            e.getBantGenisligiHz(),
            e.getModulasyon() != null ? e.getModulasyon().name() : null,
            e.getMti(),
            e.getTip() != null ? e.getTip().name() : null,
            e.getGenlikDbm(),
            e.getHaberlesmeSekli() != null ? e.getHaberlesmeSekli().name() : null,
            e.getCalismaSekli() != null ? e.getCalismaSekli().name() : null,
            e.getAltEsikSeviyesiDbm(),
            e.getCagriAdi(),
            e.getKarsiCagriAdi(),
            e.getSonTespitZamani(),
            e.getSureSn(),
            e.getTeshisKimlik() != null ? e.getTeshisKimlik().name() : null,
            e.getMinFrekansMhz(),
            e.getMaxFrekansMhz(),
            e.getOperatorNotu(),
            e.getOnaylayanKullanici(),
            e.getOnayNotu(),
            formatInstant(e.getSonTespitZamani()),
            e.getKayitTipi() != null ? e.getKayitTipi().name() : null,
            e.getKestirimId()
        );
    }

    private TacticalComintDetailDto toDetailDto(TacticalComintEntity e) {
        List<MuhabereFrekansEntry> frekansList = Collections.emptyList();
        try {
            if (e.getFrekansListesiJson() != null && !e.getFrekansListesiJson().isBlank()) {
                frekansList = objectMapper.readValue(e.getFrekansListesiJson(), new TypeReference<List<MuhabereFrekansEntry>>() {});
            }
        } catch (Exception ex) {
            log.warn("Frekans JSON parse hatası [{}]: {}", e.getId(), ex.getMessage());
        }

        List<String> iliskiliLobIdListesi = Collections.emptyList();
        if (e.getKayitTipi() == KayitTipi.KESTIRIM_ELIPS) {
            List<TacticalComintEntity> childLobs = comintRepo.findByKestirimId(e.getId());
            if (childLobs != null && !childLobs.isEmpty()) {
                iliskiliLobIdListesi = childLobs.stream().map(c -> c.getId()).toList();
            }
        }

        return new TacticalComintDetailDto(
            e.getId(),
            e.getVeriKaynagi() != null ? e.getVeriKaynagi().name() : null,
            e.getKuvvetSiraNo(),
            e.getGoreviIcraEdenUstBirlik(),
            e.getGoreviIcraEdenEhUnsuru(),
            e.getYon(),
            e.getEhUnsuruEnlem(),
            e.getEhUnsuruBoylam(),
            e.getEhUnsuruIrtifa(),
            e.getYayinEnlem(),
            e.getYayinBoylam(),
            e.getYayinSemiMajorMeters(),
            e.getYayinSemiMinorMeters(),
            e.getYayinOrientationDegrees(),
            e.getHedefYerBilgisi(),
            e.getHedefKaynagi() != null ? e.getHedefKaynagi().name() : null,
            e.getVeriGirisiYapanBirlik(),
            e.getLisan(),
            e.getProtokol() != null ? e.getProtokol().name() : null,
            e.getBantGenisligiHz(),
            e.getModulasyon() != null ? e.getModulasyon().name() : null,
            e.getMti(),
            e.getTip() != null ? e.getTip().name() : null,
            e.getGenlikDbm(),
            e.getHaberlesmeSekli() != null ? e.getHaberlesmeSekli().name() : null,
            e.getCalismaSekli() != null ? e.getCalismaSekli().name() : null,
            e.getAltEsikSeviyesiDbm(),
            e.getCagriAdi(),
            e.getKarsiCagriAdi(),
            e.getIlkTespitZamani(),
            e.getSonTespitZamani(),
            e.getSureSn(),
            e.getTeshisKimlik() != null ? e.getTeshisKimlik().name() : null,
            e.getMinFrekansMhz(),
            e.getMaxFrekansMhz(),
            frekansList,
            e.getOperatorNotu(),
            e.getOnaylayanKullanici(),
            e.getOnayNotu(),
            e.getKayitTipi() != null ? e.getKayitTipi().name() : null,
            e.getKestirimId(),
            iliskiliLobIdListesi
        );
    }


    public Map<String, Object> getComsecAndTransmissionTactics() {
        log.info("Tool Cagrisi: getComsecAndTransmissionTactics() calistirildi.");
        COMINT_CALL_LOGS.get().add("Tool Cagrisi: getComsecAndTransmissionTactics() calistirildi.");

        Map<String, Object> res = new LinkedHashMap<>();
        long kripto = comintRepo.countByHaberlesmeSekli(HaberlesmeSekli.KRIPTO);
        long burst = comintRepo.countByHaberlesmeSekli(HaberlesmeSekli.DARBE_PATLAMA);
        long gayda = comintRepo.countByHaberlesmeSekli(HaberlesmeSekli.KARISTIRMA_GAYDA);
        long gurultu = comintRepo.countByHaberlesmeSekli(HaberlesmeSekli.KARISTIRMA_GURULTU);
        long total = comintRepo.count();

        res.put("toplamMuhabereKaydi", total);
        res.put("kriptoluYayinSayisi", kripto);
        res.put("darbePatlamaBurstSayisi", burst);
        res.put("karistirmaGaydaSayisi", gayda);
        res.put("karistirmaGurultuSayisi", gurultu);
        res.put("kriptoOraniYuzde", total > 0 ? Math.round((kripto * 100.0) / total) : 0);
        res.put("taktikEmare", burst > 0 ? "Darbe Patlama (Burst) tespit edildi! Dusman acil taktik veri/hedef aktarimi yapiyor olabilir." : "Standart kriptolu ve ses trafigi mevcut.");

        return res;
    }

    private Map<String, Long> mapDistribution(List<Object[]> rows) {
        Map<String, Long> map = new LinkedHashMap<>();
        if (rows != null) {
            for (Object[] r : rows) {
                if (r[0] != null) {
                    map.put(r[0].toString(), ((Number) r[1]).longValue());
                }
            }
        }
        return map;
    }

    private String formatTimestampObj(Object obj) {
        if (obj == null) return "-";
        if (obj instanceof Instant ins) return formatInstant(ins);
        if (obj instanceof java.sql.Timestamp ts) return formatInstant(ts.toInstant());
        if (obj instanceof java.time.LocalDateTime ldt) return formatInstant(ldt.atZone(ZoneId.systemDefault()).toInstant());
        return obj.toString();
    }

    public HierarchyAnalysisResultDto analyzeComintHierarchy(
        String zaman,
        String kategori,
        String durum,
        Integer limit
    ) {
        COMINT_CALL_LOGS.get().add("Tool Cagrisi: analyzeComintHierarchy(zaman=" + zaman + ", kategori=" + kategori + ", durum=" + durum + ", limit=" + limit + ") calistirildi.");

        Instant baslangic = parseTime(zaman, true);
        Instant bitis = parseTime(zaman, false);
        if (baslangic == null) {
            baslangic = Instant.EPOCH;
        }
        if (bitis == null) {
            bitis = Instant.now().plus(Duration.ofHours(24));
        }

        String zamanLabel = (zaman != null && !zaman.isBlank()) ? zaman : "TUM_ZAMANLAR";

        Object[] rawCounts = comintRepo.getComintHierarchyCountsWithTimeRange(baslangic, bitis);
        Object[] counts = rawCounts;
        if (rawCounts != null && rawCounts.length > 0 && rawCounts[0] instanceof Object[]) {
            counts = (Object[]) rawCounts[0];
        }
        long totalElips = extractLong(counts, 0);
        long childedElips = extractLong(counts, 1);
        long childlessElips = extractLong(counts, 2);
        long totalLob = extractLong(counts, 3);
        long bagliLob = extractLong(counts, 4);
        long basibosLob = extractLong(counts, 5);

        double avgChild = childedElips > 0 ? (double) Math.round(((double) bagliLob / childedElips) * 10.0) / 10.0 : 0.0;
        double basibosRate = totalLob > 0 ? (double) Math.round(((double) basibosLob / totalLob * 100.0) * 10.0) / 10.0 : 0.0;

        int maxRows = (limit != null && limit > 0) ? Math.min(limit, 50) : 15;
        String kat = (kategori != null) ? kategori.trim().toUpperCase(Locale.ENGLISH) : "TUMU";
        String dur = (durum != null) ? durum.trim().toUpperCase(Locale.ENGLISH) : "TUMU";

        boolean isLobQuery = kat.contains("LOB") || dur.contains("LOB") || dur.equals("BASIBOS") || dur.equals("BAGLI");
        List<Map<String, Object>> detaylar = new ArrayList<>();

        if (isLobQuery) {
            List<Object[]> lobRows;
            if (dur.contains("BASIBOS")) {
                lobRows = comintRepo.getComintUnassociatedLobsWithTimeRange(baslangic, bitis, maxRows);
            } else if (dur.contains("BAGLI")) {
                lobRows = comintRepo.getComintAssociatedLobsWithTimeRange(baslangic, bitis, maxRows);
            } else {
                lobRows = comintRepo.getComintAllLobsWithTimeRange(baslangic, bitis, maxRows);
            }

            for (Object[] r : lobRows) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", r[0]);
                item.put("tip", "HAM_LOB");
                item.put("cagriAdi", r[1]);
                item.put("karsiCagriAdi", r[2]);
                item.put("teshis", r[3]);
                item.put("ehUnsuru", r[4]);
                item.put("ehUnsuruKonum", (r[5] != null && r[6] != null) ? String.format(Locale.ENGLISH, "%.2f K, %.2f D", ((Number) r[5]).doubleValue(), ((Number) r[6]).doubleValue()) : "-");
                item.put("yonDerece", r[7] != null ? Math.round(((Number) r[7]).doubleValue() * 10.0) / 10.0 : null);
                item.put("frekansMhz", r[8] != null ? Math.round(((Number) r[8]).doubleValue() * 1000.0) / 1000.0 : null);
                item.put("sonTespitZamani", formatTimestampObj(r[9]));
                item.put("lisan", r[10]);
                item.put("haberlesmeSekli", r[11]);
                item.put("kestirimId", r[13]);
                item.put("durum", r[13] != null ? "BAGLI_LOB (Hedef Kestirimi: " + r[13] + ")" : "BASIBOS_LOB (Tekil Hat / Kestirilemedi)");
                item.put("operatorNotu", r[12]);
                detaylar.add(item);
            }
        } else {
            List<Object[]> elipsRows;
            if (dur.contains("COCUKLU")) {
                elipsRows = comintRepo.getComintChildedEllipsesWithTimeRange(baslangic, bitis, maxRows);
            } else if (dur.contains("COCUKSUZ")) {
                elipsRows = comintRepo.getComintChildlessEllipsesWithTimeRange(baslangic, bitis, maxRows);
            } else {
                elipsRows = comintRepo.getComintAllEllipsesWithTimeRange(baslangic, bitis, maxRows);
            }

            for (Object[] r : elipsRows) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", r[0]);
                item.put("tip", "KESTIRIM_ELIPS");
                item.put("cagriAdi", r[1]);
                item.put("karsiCagriAdi", r[2]);
                item.put("teshis", r[3]);
                item.put("ehUnsuru", r[4]);
                item.put("hedefKonum", (r[6] != null && r[7] != null) ? String.format(Locale.ENGLISH, "%.2f K, %.2f D", ((Number) r[6]).doubleValue(), ((Number) r[7]).doubleValue()) : "-");
                item.put("hataElipsiMeters", (r[8] != null && r[9] != null) ? String.format(Locale.ENGLISH, "%.0f m x %.0f m", ((Number) r[8]).doubleValue(), ((Number) r[9]).doubleValue()) : "-");
                item.put("sonTespitZamani", formatTimestampObj(r[10]));
                item.put("lisan", r[11]);
                item.put("haberlesmeSekli", r[12]);
                long cc = extractLong(r, 14);
                item.put("cocukLobSayisi", cc);
                item.put("durum", cc > 0 ? "COCUKLU_ELIPS (" + cc + " LOB Fuzyonu)" : "COCUKSUZ_ELIPS (Ham LOB\'suz / Dogrudan Kestirim)");
                item.put("operatorNotu", r[13]);
                detaylar.add(item);
            }
        }

        String taktikDegerlendirme = String.format(
            "Muhabere (COMINT) Hiyerarsi Ozeti (%s): Toplam %d Telsiz Kestirim Elipsinin %d tanesi yerel coklu DF istasyonu fuzyonuyla (cocuklu, ort. %.1f LOB/kestirim), " +
            "%d tanesi ham LOB kayitsiz dogrudan/harici (cocuksuz) izdir. Sahadaki %d Ham LOB'un %d tanesi bir kestirime baglanmis, %d tanesi ise (%%%.1f) " +
            "henuz diger istasyonlarca teyit edilmemis basibos telsiz kerterizleridir.",
            zamanLabel, totalElips, childedElips, avgChild, childlessElips, totalLob, bagliLob, basibosLob, basibosRate
        );

        return new HierarchyAnalysisResultDto(
            "MUHABERE",
            zamanLabel,
            isLobQuery ? "HAM_LOBLAR" : "KESTIRIM_ELIPSLERI",
            dur,
            totalElips,
            childedElips,
            childlessElips,
            totalLob,
            bagliLob,
            basibosLob,
            avgChild,
            basibosRate,
            detaylar,
            taktikDegerlendirme
        );
    }

    public HierarchyAnalysisResultDto analyzeComintHierarchy(String zaman, String durum) {
        return analyzeComintHierarchy(zaman, null, durum, 15);
    }

    public HierarchyAnalysisResultDto analyzeComintHierarchy(String zaman) {
        return analyzeComintHierarchy(zaman, null, null, 15);
    }

    public HierarchyAnalysisResultDto analyzeComintHierarchy() {
        return analyzeComintHierarchy(null, null, null, 15);
    }

    private long extractLong(Object[] arr, int index) {
        if (arr == null || index >= arr.length || arr[index] == null) {
            return 0L;
        }
        if (arr[index] instanceof Number num) {
            return num.longValue();
        }
        try {
            return Long.parseLong(arr[index].toString());
        } catch (Exception e) {
            return 0L;
        }
    }

}
