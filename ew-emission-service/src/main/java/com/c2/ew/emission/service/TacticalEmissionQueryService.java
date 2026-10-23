package com.c2.ew.emission.service;

import com.c2.ew.emission.dto.HierarchyAnalysisResultDto;

import com.c2.ew.emission.dto.TacticalEmissionDetailDto;
import com.c2.ew.emission.dto.TacticalEmissionSummaryDto;
import com.c2.ew.emission.dto.TacticalMapItemDto;
import com.c2.ew.emission.dto.TacticalQueryResult;
import com.c2.ew.emission.dto.TablePageResponse;
import com.c2.ew.emission.domain.enums.*;
import com.c2.ew.emission.domain.model.AtpEntry;
import com.c2.ew.emission.domain.model.FrequencyEntry;
import com.c2.ew.emission.domain.model.PriEntry;
import com.c2.ew.emission.domain.model.PwEntry;
import com.c2.ew.emission.infrastructure.entity.TacticalEmissionEntity;
import com.c2.ew.emission.infrastructure.repository.TacticalEmissionJpaRepository;
import com.c2.ew.emission.infrastructure.entity.TacticalComintEntity;
import com.c2.ew.emission.infrastructure.repository.TacticalComintJpaRepository;
import com.c2.ew.emission.util.GeoUtils;
import com.c2.ew.emission.domain.model.GeoPoint;

import com.c2.ew.emission.dto.TacticalDistributionDto;
import com.c2.ew.emission.dto.TacticalWaveformTimelineDto;
import com.c2.ew.emission.dto.RadarSpectrumOccupancyDto;
import com.c2.ew.emission.dto.WeekdayRadarDistributionDto;
import com.c2.ew.emission.service.RadarCorrelationService;
import com.c2.ew.emission.service.RadarSpectrumOccupancyService;
import com.c2.ew.emission.service.TacticalDistributionService;
import com.c2.ew.emission.service.TacticalWaveformTimelineService;
import com.c2.ew.emission.service.TacticalWeekdayRadarService;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
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
public class TacticalEmissionQueryService {

    private static final Logger log = LoggerFactory.getLogger(TacticalEmissionQueryService.class);
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss").withZone(ZoneId.of("Europe/Istanbul"));

    public static String formatInstant(Instant instant) {
        if (instant == null) return "-";
        return DATE_FORMATTER.format(instant);
    }

    private static final ThreadLocal<List<String>> CALL_LOGS = ThreadLocal.withInitial(ArrayList::new);
    private static final Locale TR_LOCALE = Locale.forLanguageTag("tr-TR");

    private String toTrLower(String val) {
        if (val == null) return "";
        return val.trim().toLowerCase(TR_LOCALE);
    }

    public static List<String> drainCallLogs() {
        List<String> logs = new ArrayList<>(CALL_LOGS.get());
        CALL_LOGS.get().clear();
        return logs;
    }

    private final TacticalEmissionJpaRepository emissionRepo;
    private final TacticalComintJpaRepository comintRepo;
    private final ObjectMapper objectMapper;
    private final TacticalDistributionService distributionService;
    private final TacticalWaveformTimelineService timelineService;
    private final RadarCorrelationService correlationService;
    private final RadarSpectrumOccupancyService spectrumOccupancyService;
    private final TacticalWeekdayRadarService weekdayRadarService;

    public TacticalEmissionQueryService(
        TacticalEmissionJpaRepository emissionRepo,
        TacticalComintJpaRepository comintRepo,
        ObjectMapper objectMapper,
        TacticalDistributionService distributionService,
        TacticalWaveformTimelineService timelineService,
        RadarCorrelationService correlationService,
        RadarSpectrumOccupancyService spectrumOccupancyService,
        TacticalWeekdayRadarService weekdayRadarService
    ) {
        this.emissionRepo = emissionRepo;
        this.comintRepo = comintRepo;
        this.objectMapper = objectMapper;
        this.distributionService = distributionService;
        this.timelineService = timelineService;
        this.correlationService = correlationService;
        this.spectrumOccupancyService = spectrumOccupancyService;
        this.weekdayRadarService = weekdayRadarService;
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
            trimmed.equalsIgnoreCase("HEPSI")) {
            return null;
        }
        return trimmed;
    }

            private volatile Map<String, Object> cachedMacroStats = null;
    private volatile long lastMacroStatsCacheTime = 0L;

    public Map<String, Object> queryTacticalMacroStats() {
        return getTacticalMacroStats();
    }

    public Map<String, Object> getTacticalMacroStats() {
        CALL_LOGS.get().add("Tool Çağrısı: getTacticalMacroStats() çalıştırıldı.");

        long now = System.currentTimeMillis();
        if (cachedMacroStats != null && (now - lastMacroStatsCacheTime) < 30_000L) {
            return cachedMacroStats;
        }

        long totalCount = emissionRepo.count();
        long tacizCount = emissionRepo.countByTacizTrue();
        long etUygulaniyorCount = emissionRepo.countByEtUygulamaDurumu(EtUygulamaDurumu.UYGULANIYOR);
        long etSusturduCount = emissionRepo.countByEtUygulamaDurumu(EtUygulamaDurumu.SUSTURDU);
        long kestirimCount = emissionRepo.countByKayitTipi(KayitTipi.KESTIRIM_ELIPS);
        long hamLobCount = emissionRepo.countByKayitTipi(KayitTipi.HAM_LOB);
        long bagliLobCount = emissionRepo.countByKayitTipiAndKestirimIdIsNotNull(KayitTipi.HAM_LOB);
        long basibosLobCount = emissionRepo.countByKayitTipiAndKestirimIdIsNull(KayitTipi.HAM_LOB);

        Map<String, Long> teshisMap = new LinkedHashMap<>();
        for (Object[] row : emissionRepo.getTeshisDistribution()) {
            teshisMap.put(String.valueOf(row[0]), (Long) row[1]);
        }

        Map<String, Long> veriKaynagiMap = new LinkedHashMap<>();
        for (Object[] row : emissionRepo.getVeriKaynagiDistribution()) {
            veriKaynagiMap.put(String.valueOf(row[0]), (Long) row[1]);
        }

        Map<String, Long> platformMap = new LinkedHashMap<>();
        for (Object[] row : emissionRepo.getPlatformOrtamiDistribution()) {
            platformMap.put(String.valueOf(row[0]), (Long) row[1]);
        }

        Map<String, Long> kayitTipiMap = new LinkedHashMap<>();
        kayitTipiMap.put("KESTIRIM_ELIPS", kestirimCount);
        kayitTipiMap.put("HAM_LOB", hamLobCount);
        kayitTipiMap.put("BAGLI_LOB", bagliLobCount);
        kayitTipiMap.put("BASIBOS_LOB", basibosLobCount);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("toplamEmisyonSayisi", totalCount);
        result.put("kestirimSayisi", kestirimCount);
        result.put("hamLobSayisi", hamLobCount);
        result.put("bagliLobSayisi", bagliLobCount);
        result.put("basibosLobSayisi", basibosLobCount);
        result.put("tacizYapanHedefSayisi", tacizCount);
        result.put("aktifEtUygulananSayisi", etUygulaniyorCount);
        result.put("susturulanHedefSayisi", etSusturduCount);
        result.put("kayitTipiDagilimi", kayitTipiMap);
        result.put("teshisDagilimi", teshisMap);
        result.put("veriKaynagiDagilimi", veriKaynagiMap);
        result.put("platformOrtamiDagilimi", platformMap);

        cachedMacroStats = result;
        lastMacroStatsCacheTime = now;
        return result;
    }

        public List<TacticalEmissionSummaryDto> queryPriorityThreats(Integer limit) {
        int max = (limit == null || limit <= 0) ? 15 : Math.min(limit, 30);
        CALL_LOGS.get().add("Tool Çağrısı: queryPriorityThreats(limit=" + max + ") çalıştırıldı.");

        Specification<TacticalEmissionEntity> spec = (root, query, cb) -> {
            Predicate isEnemy = root.get("teshisKimlik").in(TeshisKimlik.DUSMAN, TeshisKimlik.MUHTEMEL_DUSMAN);
            Predicate isTaciz = cb.isTrue(root.get("taciz"));
            Predicate hasHss = cb.isNotNull(root.get("hss"));
            return cb.or(isTaciz, cb.and(isEnemy, hasHss));
        };

        Page<TacticalEmissionEntity> page = emissionRepo.findAll(
            spec,
            PageRequest.of(0, max, Sort.by(Sort.Direction.DESC, "sonTespitZamani"))
        );

        List<TacticalEmissionSummaryDto> dtoList = page.getContent().stream().map(this::toSummaryDto).collect(Collectors.toList());
        return dtoList;
    }

    /**
     * Tüm radar emisyon kayıtlarını (160 adet) doğrudan getirir (Ant Design Radar Tablosu için).
     */
    public List<TacticalEmissionSummaryDto> getAllEmissions() {
        return emissionRepo.findAll(PageRequest.of(0, 500, Sort.by(Sort.Direction.DESC, "sonTespitZamani")))
            .getContent()
            .stream()
            .map(this::toSummaryDto)
            .collect(Collectors.toList());
    }

    /**
     * Taktik Durumsal Farkındalık Haritası ve Kesişim Isı Analizi için Ultra-Hafif DTO Listesi.
     * Sadece harita ve analiz için gerekli 15 kolonu çeker, ağ yükünü %85 azaltır.
     */
    public List<TacticalMapItemDto> getMapEmissions(int limit) {
        int max = Math.min(Math.max(limit, 50), 3000);
        List<Object[]> rows = emissionRepo.findMapEmissionsNative(max);
        List<TacticalMapItemDto> items = new ArrayList<>(rows.size());

        for (Object[] r : rows) {
            Instant sonTespit = toInstantHelper(r[13]);
            items.add(new TacticalMapItemDto(
                toStringValHelper(r[0]),               // id
                "RADAR",                               // disiplin
                toStringValHelper(r[1]),               // kayitTipi
                toDoubleHelper(r[2]),                  // enlem (yayin_enlem)
                toDoubleHelper(r[3]),                  // boylam (yayin_boylam)
                toDoubleHelper(r[4]),                  // semiMajor
                toDoubleHelper(r[5]),                  // semiMinor
                toDoubleHelper(r[6]),                  // azimuth
                toDoubleHelper(r[7]),                  // sensorEnlem
                toDoubleHelper(r[8]),                  // sensorBoylam
                toDoubleHelper(r[9]),                  // bearing
                toStringValHelper(r[10]),              // teshisKimlik
                toStringValHelper(r[11]),              // hedefAdi (radar_adi)
                toStringValHelper(r[12]),              // ehUnsuru
                sonTespit,                             // sonTespitZamani
                formatTimestampObj(r[13]),             // sonTespitZamaniFormatted
                toStringValHelper(r[14])               // kestirimId
            ));
        }
        return items;
    }

    private Instant toInstantHelper(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Instant ins) return ins;
        if (obj instanceof java.sql.Timestamp ts) return ts.toInstant();
        if (obj instanceof java.time.LocalDateTime ldt) return ldt.atZone(ZoneId.of("Europe/Istanbul")).toInstant();
        try {
            return Instant.parse(obj.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private Double toDoubleHelper(Object obj) {
        if (obj == null) return null;
        if (obj instanceof Number num) return num.doubleValue();
        try {
            return Double.parseDouble(obj.toString());
        } catch (Exception e) {
            return null;
        }
    }

    private String toStringValHelper(Object obj) {
        return obj != null ? obj.toString() : null;
    }

    /**
     * Ant Design Tablosu için Sunucu Taraflı Sayfalama ve Filtreleme (Server-Side Pagination).
     */
    public TablePageResponse<TacticalEmissionSummaryDto> getEmissionsPage(
        int page,
        int size,
        String search,
        String teshis,
        String gorev,
        String platform,
        Boolean taciz,
        String et,
        Double minFrekans,
        Double maxFrekans,
        Double minPri,
        Double maxPri,
        Double minPw,
        Double maxPw,
        Double minGenlik,
        Double maxGenlik,
        String bolge,
        String hss,
        String sortField,
        String sortOrder,
        String kayitTipi
    ) {
        int pageIdx = Math.max(0, page - 1);
        int pageSize = (size <= 0) ? 15 : Math.min(size, 100);

        Sort.Direction direction = "ascend".equalsIgnoreCase(sortOrder) ? Sort.Direction.ASC : Sort.Direction.DESC;
        String field = "sonTespitZamani";
        if (sortField != null) {
            String sf = sortField.trim();
            if ("frekans".equalsIgnoreCase(sf)) sf = "minFrekansMhz";
            if ("pri".equalsIgnoreCase(sf)) sf = "minPriMicroSec";
            if ("pw".equalsIgnoreCase(sf)) sf = "minPwMicroSec";
            Set<String> validFields = Set.of(
                "id", "spotNo", "teshisKimlik", "radarAdi", "radarGorevi", "hedefYerBilgisi", 
                "hedefMevziBilgisi", "hss", "platformOrtami", "platformTipi", 
                "minFrekansMhz", "maxFrekansMhz", "minPriMicroSec", "maxPriMicroSec", 
                "minPwMicroSec", "maxPwMicroSec", "genlikDbm", "sureSn", "sonTespitZamani", "taciz", "etUygulamaDurumu", "kayitTipi"
            );
            if (validFields.contains(sf)) {
                field = sf;
            }
        }

        Specification<TacticalEmissionEntity> spec = (root, query, cb) -> {
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
                Predicate matchSpot = cb.like(cb.lower(root.get("spotNo")), pattern);
                Predicate matchRadar = cb.like(cb.lower(root.get("radarAdi")), pattern);
                Predicate matchMevzi = cb.like(cb.lower(root.get("hedefMevziBilgisi")), pattern);
                Predicate matchYer = cb.like(cb.lower(root.get("hedefYerBilgisi")), pattern);
                Predicate matchHss = cb.like(cb.lower(root.get("hss")), pattern);
                Predicate matchGorev = cb.like(cb.lower(root.get("radarGorevi")), pattern);
                predicates.add(cb.or(matchId, matchSpot, matchRadar, matchMevzi, matchYer, matchHss, matchGorev));
            }

            TeshisKimlik tk = normalizeTeshis(teshis);
            if (tk != null) {
                predicates.add(cb.equal(root.get("teshisKimlik"), tk));
            }

            if (gorev != null && !gorev.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("radarGorevi")), "%" + toTrLower(gorev) + "%"));
            }

            PlatformOrtami po = normalizePlatformOrtami(platform);
            if (po != null) {
                predicates.add(cb.equal(root.get("platformOrtami"), po));
            }

            if (taciz != null) {
                predicates.add(cb.equal(root.get("taciz"), taciz));
            }

            EtUygulamaDurumu etDurum = normalizeEtDurumu(et);
            if (etDurum != null) {
                predicates.add(cb.equal(root.get("etUygulamaDurumu"), etDurum));
            }

            // Gelişmiş filtreler
            if (minFrekans != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("maxFrekansMhz"), minFrekans));
            }
            if (maxFrekans != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("minFrekansMhz"), maxFrekans));
            }
            if (minPri != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("maxPriMicroSec"), minPri));
            }
            if (maxPri != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("minPriMicroSec"), maxPri));
            }
            if (minPw != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("maxPwMicroSec"), minPw));
            }
            if (maxPw != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("minPwMicroSec"), maxPw));
            }
            if (minGenlik != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("genlikDbm"), minGenlik));
            }
            if (maxGenlik != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("genlikDbm"), maxGenlik));
            }
            if (bolge != null && !bolge.isBlank()) {
                String bp = "%" + toTrLower(bolge) + "%";
                predicates.add(cb.or(
                    cb.like(cb.lower(root.get("hedefYerBilgisi")), bp),
                    cb.like(cb.lower(root.get("hedefMevziBilgisi")), bp)
                ));
            }
            if (hss != null && !hss.isBlank()) {
                predicates.add(cb.like(cb.lower(root.get("hss")), "%" + toTrLower(hss) + "%"));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        Page<TacticalEmissionEntity> springPage = emissionRepo.findAll(spec, PageRequest.of(pageIdx, pageSize, Sort.by(direction, field)));
        List<TacticalEmissionSummaryDto> dtos = springPage.getContent().stream().map(this::toSummaryDto).collect(Collectors.toList());
        return new TablePageResponse<>(dtos, page, pageSize, springPage.getTotalElements(), springPage.getTotalPages());
    }

    public TablePageResponse<TacticalEmissionSummaryDto> getEmissionsPage(
        int page,
        int size,
        String search,
        String teshis,
        String gorev,
        String platform,
        Boolean taciz,
        String et,
        Double minFrekans,
        Double maxFrekans,
        Double minPri,
        Double maxPri,
        Double minPw,
        Double maxPw,
        Double minGenlik,
        Double maxGenlik,
        String bolge,
        String hss,
        String sortField,
        String sortOrder
    ) {
        return getEmissionsPage(page, size, search, teshis, gorev, platform, taciz, et,
            minFrekans, maxFrekans, minPri, maxPri, minPw, maxPw, minGenlik, maxGenlik, bolge, hss,
            sortField, sortOrder, null);
    }

    public TablePageResponse<TacticalEmissionSummaryDto> getEmissionsPage(
        int page,
        int size,
        String search,
        String teshis,
        String gorev,
        String platform,
        Boolean taciz,
        String et,
        String sortField,
        String sortOrder
    ) {
        return getEmissionsPage(page, size, search, teshis, gorev, platform, taciz, et,
            null, null, null, null, null, null, null, null, null, null, sortField, sortOrder, null);
    }

    public TacticalQueryResult<TacticalEmissionSummaryDto> queryEmissions(
        String spotNo,
        String teshisKimlik,
        String veriKaynagi,
        String radarAdi,
        String radarGorevi,
        String hss,
        String bolge,
        Boolean taciz,
        String etUygulamaDurumu,
        String platformOrtami,
        String kayitTipi,
        Double minFrekansMhz,
        Double maxFrekansMhz,
        String frekansTipi,
        Double minPriMicroSec,
        Double maxPriMicroSec,
        Double minPwMicroSec,
        Double maxPwMicroSec,
        Double minAtpMicroSec,
        Double maxAtpMicroSec,
        Double minGenlikDbm,
        Double maxGenlikDbm,
        Double minSureSn,
        Double maxSureSn,
        String baslangicZamani,
        String bitisZamani,
        Integer limit
    ) {
        String cleanSpot = cleanParam(spotNo);
        String cleanTeshis = cleanParam(teshisKimlik);
        String cleanKaynak = cleanParam(veriKaynagi);
        String cleanRadar = cleanParam(radarAdi);
        String cleanGorev = cleanParam(radarGorevi);
        String cleanHss = cleanParam(hss);
        String cleanBolge = cleanParam(bolge);
        String cleanEt = cleanParam(etUygulamaDurumu);
        String cleanPlatform = cleanParam(platformOrtami);
        String cleanKayitTipi = cleanParam(kayitTipi);
        String cleanFreqType = cleanParam(frekansTipi);
        String cleanBaslangic = cleanParam(baslangicZamani);
        String cleanBitis = cleanParam(bitisZamani);

        int maxLimit = (limit == null || limit <= 0) ? 10 : Math.min(limit, 50);

        log.info("queryEmissions: spotNo={}, teshis={}, kaynak={}, radar={}, gorev={}, hss={}, bolge={}, taciz={}, et={}, kayitTipi={}, freq=[{}-{}], time=[{}-{}], limit={}",
            cleanSpot, cleanTeshis, cleanKaynak, cleanRadar, cleanGorev, cleanHss, cleanBolge, taciz, cleanEt, cleanKayitTipi,
            minFrekansMhz, maxFrekansMhz, cleanBaslangic, cleanBitis, maxLimit);
        CALL_LOGS.get().add(String.format("Tool: queryEmissions(spotNo=%s, teshis=%s, radar=%s, bolge=%s, gorev=%s, kayitTipi=%s, freq=[%s-%s], zaman=[%s-%s], taciz=%s, limit=%d)",
            cleanSpot, cleanTeshis, cleanRadar, cleanBolge, cleanGorev, cleanKayitTipi, minFrekansMhz, maxFrekansMhz, cleanBaslangic, cleanBitis, taciz, maxLimit));

        Specification<TacticalEmissionEntity> spec = createSpecification(
            cleanSpot, cleanTeshis, cleanKaynak, cleanRadar, cleanGorev, cleanHss, cleanBolge, taciz, cleanEt, cleanPlatform, cleanKayitTipi,
            minFrekansMhz, maxFrekansMhz, cleanFreqType,
            minPriMicroSec, maxPriMicroSec,
            minPwMicroSec, maxPwMicroSec,
            minAtpMicroSec, maxAtpMicroSec,
            minGenlikDbm, maxGenlikDbm,
            minSureSn, maxSureSn,
            cleanBaslangic, cleanBitis
        );

        Page<TacticalEmissionEntity> pageResult = emissionRepo.findAll(
            spec,
            PageRequest.of(0, maxLimit, Sort.by(Sort.Direction.DESC, "sonTespitZamani"))
        );

        List<TacticalEmissionSummaryDto> dtos = pageResult.getContent().stream().map(this::toSummaryDto).collect(Collectors.toList());
        return TacticalQueryResult.of(dtos, pageResult.getTotalElements(), "📡 Radar (ELINT) Tablosu");
    }

    public long countEmissions(
        String spotNo,
        String teshisKimlik,
        String veriKaynagi,
        String radarAdi,
        String radarGorevi,
        String hss,
        String bolge,
        Boolean taciz,
        String etUygulamaDurumu,
        String platformOrtami,
        String kayitTipi,
        Double minFrekansMhz,
        Double maxFrekansMhz,
        String frekansTipi,
        Double minPriMicroSec,
        Double maxPriMicroSec,
        Double minPwMicroSec,
        Double maxPwMicroSec,
        Double minAtpMicroSec,
        Double maxAtpMicroSec,
        Double minGenlikDbm,
        Double maxGenlikDbm,
        Double minSureSn,
        Double maxSureSn,
        String baslangicZamani,
        String bitisZamani
    ) {
        String cleanSpot = cleanParam(spotNo);
        String cleanTeshis = cleanParam(teshisKimlik);
        String cleanKaynak = cleanParam(veriKaynagi);
        String cleanRadar = cleanParam(radarAdi);
        String cleanGorev = cleanParam(radarGorevi);
        String cleanHss = cleanParam(hss);
        String cleanBolge = cleanParam(bolge);
        String cleanEt = cleanParam(etUygulamaDurumu);
        String cleanPlatform = cleanParam(platformOrtami);
        String cleanKayitTipi = cleanParam(kayitTipi);
        String cleanFreqType = cleanParam(frekansTipi);
        String cleanBaslangic = cleanParam(baslangicZamani);
        String cleanBitis = cleanParam(bitisZamani);

        log.info("countEmissions: spotNo={}, teshis={}, radar={}, bolge={}", cleanSpot, cleanTeshis, cleanRadar, cleanBolge);
        CALL_LOGS.get().add(String.format("Tool: countEmissions(spotNo=%s, teshis=%s, radar=%s, bolge=%s)", cleanSpot, cleanTeshis, cleanRadar, cleanBolge));

        Specification<TacticalEmissionEntity> spec = createSpecification(
            cleanSpot, cleanTeshis, cleanKaynak, cleanRadar, cleanGorev, cleanHss, cleanBolge, taciz, cleanEt, cleanPlatform, cleanKayitTipi,
            minFrekansMhz, maxFrekansMhz, cleanFreqType,
            minPriMicroSec, maxPriMicroSec,
            minPwMicroSec, maxPwMicroSec,
            minAtpMicroSec, maxAtpMicroSec,
            minGenlikDbm, maxGenlikDbm,
            minSureSn, maxSureSn,
            cleanBaslangic, cleanBitis
        );

        return emissionRepo.count(spec);
    }

    // Geriye dönük uyumluluk için spotNo içermeyen 26 parametreli overload
    public TacticalQueryResult<TacticalEmissionSummaryDto> queryEmissions(
        String teshisKimlik,
        String veriKaynagi,
        String radarAdi,
        String radarGorevi,
        String hss,
        String bolge,
        Boolean taciz,
        String etUygulamaDurumu,
        String platformOrtami,
        String kayitTipi,
        Double minFrekansMhz,
        Double maxFrekansMhz,
        String frekansTipi,
        Double minPriMicroSec,
        Double maxPriMicroSec,
        Double minPwMicroSec,
        Double maxPwMicroSec,
        Double minAtpMicroSec,
        Double maxAtpMicroSec,
        Double minGenlikDbm,
        Double maxGenlikDbm,
        Double minSureSn,
        Double maxSureSn,
        String baslangicZamani,
        String bitisZamani,
        Integer limit
    ) {
        return queryEmissions(
            null, teshisKimlik, veriKaynagi, radarAdi, radarGorevi, hss, bolge, taciz,
            etUygulamaDurumu, platformOrtami, kayitTipi, minFrekansMhz, maxFrekansMhz,
            frekansTipi, minPriMicroSec, maxPriMicroSec, minPwMicroSec, maxPwMicroSec,
            minAtpMicroSec, maxAtpMicroSec, minGenlikDbm, maxGenlikDbm, minSureSn, maxSureSn,
            baslangicZamani, bitisZamani, limit
        );
    }

    // Geriye dönük uyumluluk için kayitTipi içermeyen 25 parametreli overload
    public TacticalQueryResult<TacticalEmissionSummaryDto> queryEmissions(
        String teshisKimlik,
        String veriKaynagi,
        String radarAdi,
        String radarGorevi,
        String hss,
        String bolge,
        Boolean taciz,
        String etUygulamaDurumu,
        String platformOrtami,
        Double minFrekansMhz,
        Double maxFrekansMhz,
        String frekansTipi,
        Double minPriMicroSec,
        Double maxPriMicroSec,
        Double minPwMicroSec,
        Double maxPwMicroSec,
        Double minAtpMicroSec,
        Double maxAtpMicroSec,
        Double minGenlikDbm,
        Double maxGenlikDbm,
        Double minSureSn,
        Double maxSureSn,
        String baslangicZamani,
        String bitisZamani,
        Integer limit
    ) {
        return queryEmissions(
            teshisKimlik, veriKaynagi, radarAdi, radarGorevi, hss, bolge, taciz, etUygulamaDurumu, platformOrtami,
            null, minFrekansMhz, maxFrekansMhz, frekansTipi, minPriMicroSec, maxPriMicroSec, minPwMicroSec, maxPwMicroSec,
            minAtpMicroSec, maxAtpMicroSec, minGenlikDbm, maxGenlikDbm, minSureSn, maxSureSn, baslangicZamani, bitisZamani, limit
        );
    }

    // Java çağrıları ve testler için aşırı yükleme (overload) metodu
    public TacticalQueryResult<TacticalEmissionSummaryDto> queryEmissions(
        String teshisKimlik, String veriKaynagi, String radarAdi, String hss, Boolean taciz,
        String etUygulamaDurumu, String platformOrtami,
        Double minFrekansMhz, Double maxFrekansMhz, String frekansTipi,
        Double minPriMicroSec, Double maxPriMicroSec,
        Double minPwMicroSec, Double maxPwMicroSec,
        Double minAtpMicroSec, Double maxAtpMicroSec,
        Integer sayfaNo, Integer sayfaBoyutu
    ) {
        return queryEmissions(
            teshisKimlik, veriKaynagi, radarAdi, null, hss, null, taciz, etUygulamaDurumu, platformOrtami,
            minFrekansMhz, maxFrekansMhz, frekansTipi, minPriMicroSec, maxPriMicroSec, minPwMicroSec, maxPwMicroSec,
            minAtpMicroSec, maxAtpMicroSec, null, null, null, null, null, null, sayfaBoyutu
        );
    }


    public List<TacticalEmissionSummaryDto> queryElectronicAttackAndHarassment() {
        CALL_LOGS.get().add("Tool: queryElectronicAttackAndHarassment() çalıştırıldı.");

        Specification<TacticalEmissionEntity> spec = (root, query, cb) -> {
            Predicate etActive = cb.equal(root.get("etUygulamaDurumu"), EtUygulamaDurumu.UYGULANIYOR);
            Predicate etSusturdu = cb.equal(root.get("etUygulamaDurumu"), EtUygulamaDurumu.SUSTURDU);
            Predicate isTaciz = cb.isTrue(root.get("taciz"));
            return cb.or(etActive, etSusturdu, isTaciz);
        };

        List<TacticalEmissionEntity> list = emissionRepo.findAll(
            spec,
            Sort.by(Sort.Direction.DESC, "sonTespitZamani")
        );

        List<TacticalEmissionSummaryDto> dtos = list.stream().map(this::toSummaryDto).collect(Collectors.toList());
        return dtos;
    }

    public TacticalEmissionDetailDto getEmissionDetails(String emissionId) {
        CALL_LOGS.get().add("Tool: getEmissionDetails(emissionId=" + emissionId + ") çalıştırıldı.");

        TacticalEmissionEntity e = emissionRepo.findById(emissionId)
            .orElseThrow(() -> new IllegalArgumentException("Emisyon bulunamadı: " + emissionId));

        List<FrequencyEntry> freqList = Collections.emptyList();
        List<PriEntry> priList = Collections.emptyList();
        List<PwEntry> pwList = Collections.emptyList();
        List<AtpEntry> atpList = Collections.emptyList();

        try {
            if (e.getFrekansListesiJson() != null) {
                freqList = objectMapper.readValue(e.getFrekansListesiJson(), new TypeReference<>() {});
            }
            if (e.getPriListesiJson() != null) {
                priList = objectMapper.readValue(e.getPriListesiJson(), new TypeReference<>() {});
            }
            if (e.getPwListesiJson() != null) {
                pwList = objectMapper.readValue(e.getPwListesiJson(), new TypeReference<>() {});
            }
            if (e.getAtpListesiJson() != null) {
                atpList = objectMapper.readValue(e.getAtpListesiJson(), new TypeReference<>() {});
            }
        } catch (Exception ex) {
            log.error("JSON parse hatası: {}", ex.getMessage());
        }

        List<String> iliskiliLobIdListesi = Collections.emptyList();
        if (e.getKayitTipi() == KayitTipi.KESTIRIM_ELIPS) {
            List<TacticalEmissionEntity> childLobs = emissionRepo.findByKestirimId(e.getId());
            if (childLobs != null && !childLobs.isEmpty()) {
                iliskiliLobIdListesi = childLobs.stream().map(c -> c.getId()).toList();
            }
        }

        return new TacticalEmissionDetailDto(
            e.getId(),
            e.getVeriKaynagi() != null ? e.getVeriKaynagi().name() : null,
            e.getKuvvetSiraNo(),
            e.getUstBirlikRef(),
            e.getEhUnsuru(),
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
            e.getHedefMevziBilgisi(),
            e.getTeshisKimlik() != null ? e.getTeshisKimlik().name() : null,
            e.getElintNotasyonu(),
            e.getHedefKaynagi() != null ? e.getHedefKaynagi().name() : null,
            e.getPolarizasyon() != null ? e.getPolarizasyon().name() : null,
            e.getSpotNo(),
            e.getPulseCw() != null ? e.getPulseCw().name() : null,
            e.getRadarGorevi(),
            e.getRadarAdi(),
            e.getPlatformOrtami() != null ? e.getPlatformOrtami().name() : null,
            e.getPlatformTipi() != null ? e.getPlatformTipi().name() : null,
            e.getGenlikDbm(),
            e.getModulasyon() != null ? e.getModulasyon().name() : null,
            e.getEtUygulamaDurumu() != null ? e.getEtUygulamaDurumu().name() : null,
            e.getIlkTespitZamani(),
            e.getSonTespitZamani(),
            e.getSureSn(),
            e.getHss(),
            e.getTaciz(),
            e.getRadarKesitAlani(),
            e.getRadarIzNumarasi(),
            e.getUcakKuyrukNumarasi(),
            e.getMinFrekansMhz(),
            e.getMaxFrekansMhz(),
            e.getMinPriMicroSec(),
            e.getMaxPriMicroSec(),
            e.getMinPwMicroSec(),
            e.getMaxPwMicroSec(),
            e.getMinAtpMicroSec(),
            e.getMaxAtpMicroSec(),
            freqList,
            priList,
            pwList,
            atpList,
            e.getOperatorNotu(),
            e.getOnaylayanKullanici(),
            e.getOnayNotu(),
            e.getKayitTipi() != null ? e.getKayitTipi().name() : null,
            e.getKestirimId(),
            iliskiliLobIdListesi
        );
    }

    public Map<String, Object> compareRadarEmissions(String id1, String id2) {
        CALL_LOGS.get().add(String.format("Tool Çağrısı: compareRadarEmissions(id1=%s, id2=%s) çalıştırıldı.", id1, id2));
        return correlationService.compareEmissions(id1, id2);
    }

    public Map<String, Object> compareRadarLobs(String lobId1, String lobId2) {
        return compareRadarEmissions(lobId1, lobId2);
    }

    public Map<String, Object> findSimilarRadarEmissions(String targetId, String timeRange, Integer limit) {
        CALL_LOGS.get().add(String.format("Tool Çağrısı: findSimilarRadarEmissions(targetId=%s, timeRange=%s, limit=%s) çalıştırıldı.", targetId, timeRange, limit));
        return correlationService.findSimilarRadarEmissions(targetId, timeRange, limit);
    }

    public Map<String, Object> findSimilarRadarEmissions(String targetId, String timeRange) {
        return findSimilarRadarEmissions(targetId, timeRange, 5);
    }

    public Map<String, Object> findSimilarRadarEmissions(String targetId) {
        return findSimilarRadarEmissions(targetId, "SON_24_SAAT", 5);
    }

    public Map<String, Object> findSimilarRadarLobs(String lobId, String timeRange, Integer limit) {
        return findSimilarRadarEmissions(lobId, timeRange, limit);
    }

    private TeshisKimlik normalizeTeshis(String val) {
        if (val == null) return null;
        String v = val.trim().toUpperCase(Locale.ROOT)
            .replace('ı', 'I').replace('İ', 'I')
            .replace('ö', 'O').replace('Ö', 'O')
            .replace('ü', 'U').replace('Ü', 'U')
            .replace('ş', 'S').replace('Ş', 'S')
            .replace('ç', 'C').replace('Ç', 'C')
            .replace('ğ', 'G').replace('Ğ', 'G');
        if (v.contains("MUHTEMEL") && (v.contains("DUS") || v.contains("HOSTILE"))) return TeshisKimlik.MUHTEMEL_DUSMAN;
        if (v.contains("MUHTEMEL") && (v.contains("DOST") || v.contains("FRIEND"))) return TeshisKimlik.MUHTEMEL_DOST;
        if (v.contains("DUS") || v.contains("TEHDIT") || v.contains("HOSTILE") || v.contains("ENEMY") || v.contains("D?")) return TeshisKimlik.DUSMAN;
        if (v.contains("DOST") || v.contains("FRIEND")) return TeshisKimlik.DOST;
        if (v.contains("BILIN") || v.contains("UNKNOWN")) return TeshisKimlik.BILINMEYEN;
        if (v.contains("TARAF") || v.contains("NEUTRAL")) return TeshisKimlik.TARAFSIZ;
        try { return TeshisKimlik.valueOf(v); } catch (Exception ignored) { return null; }
    }

    private VeriKaynagi normalizeVeriKaynagi(String val) {
        if (val == null) return null;
        String v = val.toUpperCase().replace("İ", "I");
        if (v.contains("DENIZ")) return VeriKaynagi.DENIZ;
        if (v.contains("KARA")) return VeriKaynagi.KARA;
        if (v.contains("HAVA")) return VeriKaynagi.HAVA;
        if (v.contains("GEN")) return VeriKaynagi.GEN_KUR;
        try { return VeriKaynagi.valueOf(v); } catch (Exception ignored) { return null; }
    }

    private PlatformOrtami normalizePlatformOrtami(String val) {
        if (val == null) return null;
        String v = val.toUpperCase().replace("İ", "I").replace("Ü", "U");
        if (v.contains("HAVA")) return PlatformOrtami.HAVA;
        if (v.contains("KARA")) return PlatformOrtami.KARA;
        if (v.contains("DENIZ") || v.contains("SU_USTU") || v.contains("SU USTU")) return PlatformOrtami.SU_USTU;
        if (v.contains("SU_ALTI") || v.contains("SU ALTI")) return PlatformOrtami.SU_ALTI;
        try { return PlatformOrtami.valueOf(v); } catch (Exception ignored) { return null; }
    }

    private EtUygulamaDurumu normalizeEtDurumu(String val) {
        if (val == null) return null;
        String v = val.toUpperCase().replace("İ", "I").replace("Ş", "S");
        if (v.contains("SUSTUR")) return EtUygulamaDurumu.SUSTURDU;
        if (v.contains("UYGULAN") || v.contains("AKTIF")) return EtUygulamaDurumu.UYGULANIYOR;
        if (v.contains("YOK") || v.contains("UYGULANMIYOR")) return EtUygulamaDurumu.UYGULANMIYOR;
        try { return EtUygulamaDurumu.valueOf(v); } catch (Exception ignored) { return null; }
    }

    private Instant getTacticalReferenceTime() {
        try {
            Instant maxDb = emissionRepo.findMaxSonTespitZamani();
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

    private KayitTipi normalizeKayitTipi(String val) {
        if (val == null) return null;
        String v = val.toUpperCase(Locale.ENGLISH).replace("İ", "I");
        if (v.contains("LOB") || v.contains("KERTERIZ") || v.contains("STROBE")) return KayitTipi.HAM_LOB;
        if (v.contains("KESTIRIM") || v.contains("ELIPS") || v.contains("FIX")) return KayitTipi.KESTIRIM_ELIPS;
        try { return KayitTipi.valueOf(v); } catch (Exception ignored) { return null; }
    }

    private Specification<TacticalEmissionEntity> createSpecification(
        String spotNo,
        String teshisKimlik, String veriKaynagi, String radarAdi, String radarGorevi, String hss, String bolge, Boolean taciz,
        String etUygulamaDurumu, String platformOrtami, String kayitTipi,
        Double minFreq, Double maxFreq, String freqType,
        Double minPri, Double maxPri,
        Double minPw, Double maxPw,
        Double minAtp, Double maxAtp,
        Double minGenlik, Double maxGenlik,
        Double minSure, Double maxSure,
        String baslangicZamani, String bitisZamani
    ) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (spotNo != null && !spotNo.isBlank()) {
                predicates.add(cb.like(cb.upper(root.get("spotNo")), "%" + spotNo.trim().toUpperCase(Locale.ROOT) + "%"));
            }

            KayitTipi kt = normalizeKayitTipi(kayitTipi);
            if (kt != null) {
                predicates.add(cb.equal(root.get("kayitTipi"), kt));
            }

            TeshisKimlik tk = normalizeTeshis(teshisKimlik);
            if (tk != null) {
                predicates.add(cb.equal(root.get("teshisKimlik"), tk));
            }

            VeriKaynagi vk = normalizeVeriKaynagi(veriKaynagi);
            if (vk != null) {
                predicates.add(cb.equal(root.get("veriKaynagi"), vk));
            }

            PlatformOrtami po = normalizePlatformOrtami(platformOrtami);
            if (po != null) {
                predicates.add(cb.equal(root.get("platformOrtami"), po));
            }

            EtUygulamaDurumu et = normalizeEtDurumu(etUygulamaDurumu);
            if (et != null) {
                predicates.add(cb.equal(root.get("etUygulamaDurumu"), et));
            }

            if (radarAdi != null) {
                predicates.add(cb.like(cb.lower(root.get("radarAdi")), "%" + toTrLower(radarAdi) + "%"));
            }

            if (radarGorevi != null) {
                predicates.add(cb.like(cb.lower(root.get("radarGorevi")), "%" + toTrLower(radarGorevi) + "%"));
            }

            if (hss != null) {
                predicates.add(cb.like(cb.lower(root.get("hss")), "%" + toTrLower(hss) + "%"));
            }

            if (bolge != null) {
                String b = "%" + toTrLower(bolge) + "%";
                Predicate inYer = cb.like(cb.lower(root.get("hedefYerBilgisi")), b);
                Predicate inMevzi = cb.like(cb.lower(root.get("hedefMevziBilgisi")), b);
                predicates.add(cb.or(inYer, inMevzi));
            }

            if (Boolean.TRUE.equals(taciz)) {
                predicates.add(cb.isTrue(root.get("taciz")));
            }

            if (etUygulamaDurumu != null) {
                try {
                    predicates.add(cb.equal(root.get("etUygulamaDurumu"), EtUygulamaDurumu.valueOf(etUygulamaDurumu.toUpperCase())));
                } catch (IllegalArgumentException ignored) {}
            }

            if (platformOrtami != null) {
                try {
                    predicates.add(cb.equal(root.get("platformOrtami"), PlatformOrtami.valueOf(platformOrtami.toUpperCase())));
                } catch (IllegalArgumentException ignored) {}
            }

            // Dummy / Halüsinasyon sınır temizliği
            Double effectiveMinFreq = (minFreq != null && minFreq > 1.0) ? minFreq : null;
            Double effectiveMaxFreq = (maxFreq != null && maxFreq > 1.0 && maxFreq < 50000.0) ? maxFreq : null;
            Double effectiveMinPri = (minPri != null && minPri > 1.0) ? minPri : null;
            Double effectiveMaxPri = (maxPri != null && maxPri > 1.0 && maxPri < 50000.0) ? maxPri : null;
            Double effectiveMinPw = (minPw != null && minPw > 0.01) ? minPw : null;
            Double effectiveMaxPw = (maxPw != null && maxPw > 0.01 && maxPw < 5000.0) ? maxPw : null;
            Double effectiveMinAtp = (minAtp != null && minAtp > 10000.0) ? minAtp : null;
            Double effectiveMaxAtp = (maxAtp != null && maxAtp > 1000000.0 && maxAtp < 50000000.0) ? maxAtp : null;

            // 📡 Frekans Aralığı Kesişim Mantığı: target.min <= query.max AND target.max >= query.min
            if (effectiveMinFreq != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("maxFrekansMhz"), effectiveMinFreq));
            }
            if (effectiveMaxFreq != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("minFrekansMhz"), effectiveMaxFreq));
            }
            if (freqType != null) {
                predicates.add(cb.like(cb.lower(root.get("frekansListesiJson")), "%" + freqType.toLowerCase() + "%"));
            }

            // ⏱️ PRI Aralığı Kesişim Mantığı
            if (effectiveMinPri != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("maxPriMicroSec"), effectiveMinPri));
            }
            if (effectiveMaxPri != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("minPriMicroSec"), effectiveMaxPri));
            }

            // 📏 PW Aralığı Kesişim Mantığı
            if (effectiveMinPw != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("maxPwMicroSec"), effectiveMinPw));
            }
            if (effectiveMaxPw != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("minPwMicroSec"), effectiveMaxPw));
            }

            // 🔄 ATP Aralığı Kesişim Mantığı
            if (effectiveMinAtp != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("maxAtpMicroSec"), effectiveMinAtp));
            }
            if (effectiveMaxAtp != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("minAtpMicroSec"), effectiveMaxAtp));
            }

            // 📶 Genlik (dBm) Aralığı
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

    public TacticalEmissionSummaryDto toSummaryDto(TacticalEmissionEntity e) {
        return toStaticDto(e);
    }

    public static TacticalEmissionSummaryDto toStaticDto(TacticalEmissionEntity e) {
        return new TacticalEmissionSummaryDto(
            e.getId(),
            e.getVeriKaynagi() != null ? e.getVeriKaynagi().name() : null,
            e.getKuvvetSiraNo(),
            e.getEhUnsuru(),
            e.getYon(),
            e.getEhUnsuruEnlem(),
            e.getEhUnsuruBoylam(),
            e.getTeshisKimlik() != null ? e.getTeshisKimlik().name() : null,
            e.getRadarAdi(),
            e.getRadarGorevi(),
            e.getHss(),
            e.getTaciz(),
            e.getEtUygulamaDurumu() != null ? e.getEtUygulamaDurumu().name() : null,
            e.getPlatformOrtami() != null ? e.getPlatformOrtami().name() : null,
            e.getPlatformTipi() != null ? e.getPlatformTipi().name() : null,
            e.getHedefYerBilgisi(),
            e.getHedefMevziBilgisi(),
            e.getMinFrekansMhz(),
            e.getMaxFrekansMhz(),
            e.getMinPriMicroSec(),
            e.getMaxPriMicroSec(),
            e.getMinPwMicroSec(),
            e.getMaxPwMicroSec(),
            e.getMinAtpMicroSec(),
            e.getMaxAtpMicroSec(),
            e.getSonTespitZamani(),
            e.getYayinEnlem(),
            e.getYayinBoylam(),
            e.getYayinSemiMajorMeters(),
            e.getYayinSemiMinorMeters(),
            e.getYayinOrientationDegrees(),
            e.getSpotNo(),
            e.getElintNotasyonu(),
            e.getPolarizasyon() != null ? e.getPolarizasyon().name() : null,
            e.getPulseCw() != null ? e.getPulseCw().name() : null,
            e.getModulasyon() != null ? e.getModulasyon().name() : null,
            e.getGenlikDbm(),
            e.getSureSn(),
            e.getUcakKuyrukNumarasi(),
            e.getOperatorNotu(),
            e.getOnaylayanKullanici(),
            e.getOnayNotu(),
            formatInstant(e.getSonTespitZamani()),
            e.getKayitTipi() != null ? e.getKayitTipi().name() : null,
            e.getKestirimId()
        );
    }

    public Map<String, Object> getCrossDomainCorrelations(Double maxDistanceKm, Integer limit) {
        double thresholdKm = (maxDistanceKm != null && maxDistanceKm > 0) ? maxDistanceKm : 5.0;
        int maxResults = (limit != null && limit > 0) ? limit : 20;

        log.info("Tool Cagrisi: getCrossDomainCorrelations(maxDistanceKm={}, limit={}) calistirildi.", thresholdKm, maxResults);
        CALL_LOGS.get().add("Tool Cagrisi: getCrossDomainCorrelations() ile Hata Elipsi (CEP) geometrik örtüşme ve füzyon analizi yapıldı.");

        List<TacticalEmissionEntity> radars = emissionRepo.findAll();
        List<TacticalComintEntity> comints = comintRepo.findAll();

        List<Map<String, Object>> correlations = new ArrayList<>();
        int totalOverlappingCount = 0;
        int totalStatisticalCoLocatedCount = 0;

        for (TacticalEmissionEntity r : radars) {
            if (r.getYayinEnlem() == null || r.getYayinBoylam() == null) continue;
            GeoPoint rPoint = new GeoPoint(r.getYayinEnlem(), r.getYayinBoylam(), 0.0);
            double rMajor = r.getYayinSemiMajorMeters() != null ? r.getYayinSemiMajorMeters() : 500.0;
            double rMinor = r.getYayinSemiMinorMeters() != null ? r.getYayinSemiMinorMeters() : 250.0;
            double rOrient = r.getYayinOrientationDegrees() != null ? r.getYayinOrientationDegrees() : 0.0;

            for (TacticalComintEntity c : comints) {
                if (c.getYayinEnlem() == null || c.getYayinBoylam() == null) continue;
                GeoPoint cPoint = new GeoPoint(c.getYayinEnlem(), c.getYayinBoylam(), 0.0);
                double cMajor = c.getYayinSemiMajorMeters() != null ? c.getYayinSemiMajorMeters() : 3000.0;
                double cMinor = c.getYayinSemiMinorMeters() != null ? c.getYayinSemiMinorMeters() : 1000.0;
                double cOrient = c.getYayinOrientationDegrees() != null ? c.getYayinOrientationDegrees() : 0.0;

                GeoUtils.EllipseSpatialRelation relation = GeoUtils.calculateEllipseSpatialRelation(
                    rPoint, rMajor, rMinor, rOrient,
                    cPoint, cMajor, cMinor, cOrient
                );

                double boundaryKm = relation.boundaryDistanceMeters() / 1000.0;
                double centerKm = relation.centerDistanceMeters() / 1000.0;

                // Elipsler kesisiyorsa VEYA ceperden cepere mesafe esigin altindaysa eslesme kabul edilir!
                if (relation.isOverlapping() || boundaryKm <= thresholdKm) {
                    if (relation.isOverlapping()) {
                        totalOverlappingCount++;
                    }
                    if (relation.isStatisticallyCoLocated95()) {
                        totalStatisticalCoLocatedCount++;
                    }

                    Map<String, Object> pair = new LinkedHashMap<>();
                    pair.put("ceperMesafeKm", Math.round(boundaryKm * 100.0) / 100.0);
                    pair.put("merkezMesafeKm", Math.round(centerKm * 100.0) / 100.0);
                    pair.put("elipslerOrtusuyorMu", relation.isOverlapping());
                    pair.put("ortusmeMetre", Math.round(relation.overlapMeters()));
                    pair.put("ortusmeYuzdesi", Math.round(relation.overlapPercentage() * 10.0) / 10.0);
                    pair.put("istatistikselAyniMevzi95", relation.isStatisticallyCoLocated95());
                    pair.put("mahalanobisSkoru", Math.round(relation.mahalanobisDistanceSq() * 100.0) / 100.0);

                    String fusionType;
                    if (relation.isOverlapping() && relation.isStatisticallyCoLocated95()) {
                        fusionType = "TAM_ORTUSME_VE_STATISTIKSEL_AYNI_MEVZI";
                    } else if (relation.isOverlapping()) {
                        fusionType = "ELIPSLER_KESISIYOR_ORTUSME_VAR";
                    } else {
                        fusionType = "YAKIN_KOMSU_MEVZI";
                    }
                    pair.put("fuzyonTipi", fusionType);

                    pair.put("radarId", r.getId());
                    pair.put("radarAdi", r.getRadarAdi() != null ? r.getRadarAdi() : "Bilinmeyen Radar");
                    pair.put("radarTeshis", r.getTeshisKimlik() != null ? r.getTeshisKimlik().name() : "BILINMEYEN");
                    pair.put("hss", r.getHss() != null ? r.getHss() : "-");
                    pair.put("radarMevzi", r.getHedefYerBilgisi() != null ? r.getHedefYerBilgisi() : "-");
                    pair.put("radarCepMajorMetre", Math.round(rMajor));
                    pair.put("radarCepMinorMetre", Math.round(rMinor));

                    pair.put("comintId", c.getId());
                    pair.put("cagriAdi", c.getCagriAdi() != null ? c.getCagriAdi() : "-");
                    pair.put("karsiCagriAdi", c.getKarsiCagriAdi() != null ? c.getKarsiCagriAdi() : "-");
                    pair.put("lisan", c.getLisan() != null ? c.getLisan() : "-");
                    pair.put("protokol", c.getProtokol() != null ? c.getProtokol().name() : "-");
                    pair.put("haberlesmeSekli", c.getHaberlesmeSekli() != null ? c.getHaberlesmeSekli().name() : "-");
                    pair.put("comintCepMajorMetre", Math.round(cMajor));
                    pair.put("comintCepMinorMetre", Math.round(cMinor));

                    String tacticalEstimate = "Ortak Taktik Mevzi";
                    if (r.getHss() != null && !r.getHss().isBlank()) {
                        tacticalEstimate = "Entegre HSS Bataryasi & Ates Idare Telsiz Postasi (" + r.getHss() + ")";
                    } else if (r.getRadarGorevi() != null && r.getRadarGorevi().contains("ERKEN_UYARI")) {
                        tacticalEstimate = "Erken Uyari Radar Mevzii & Bolge Muhabere Postasi";
                    }
                    pair.put("taktikRolTahmini", tacticalEstimate);

                    correlations.add(pair);

                }
            }
        }

        correlations.sort((a, b) -> {
            boolean aOver = (boolean) a.get("elipslerOrtusuyorMu");
            boolean bOver = (boolean) b.get("elipslerOrtusuyorMu");
            if (aOver && !bOver) return -1;
            if (!aOver && bOver) return 1;
            if (aOver && bOver) {
                return Double.compare((double) b.get("ortusmeMetre"), (double) a.get("ortusmeMetre"));
            }
            return Double.compare((double) a.get("ceperMesafeKm"), (double) b.get("ceperMesafeKm"));
        });

        List<Map<String, Object>> limitedList = correlations.stream().limit(maxResults).collect(Collectors.toList());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("kriterMesafeKm", thresholdKm);
        response.put("toplamEslesenCiftSayisi", correlations.size());
        response.put("elipsleriBirebirOrtusenCiftSayisi", totalOverlappingCount);
        response.put("istatistiksel95AyniMevziSayisi", totalStatisticalCoLocatedCount);
        response.put("listelenenCiftler", limitedList);
        response.put("ozetDegerlendirme", "Hata elipsi (CEP) geometrisi hesaba katildiginda, sahada " + totalOverlappingCount +
            " ciftin elipslerinin BIREBIR KESISIP ORTUSTUGU ve " + totalStatisticalCoLocatedCount +
            " ciftin %95 guvenle ayni operasyonel mevzide konuslandigi tespit edilmistir. Sadece merkez mesafesine bakilmayip elips sinirlari ve olasilik dagilimi entegre edilmistir.");

        return response;
    }

    public Map<String, Object> getHarassmentAndElectronicAttackAssessment() {
        log.info("Tool Cagrisi: getHarassmentAndElectronicAttackAssessment() calistirildi.");
        CALL_LOGS.get().add("Tool Cagrisi: getHarassmentAndElectronicAttackAssessment() ile ET ve Taciz Durumu cikarildi.");

        long susturulan = emissionRepo.countByEtUygulamaDurumu(EtUygulamaDurumu.SUSTURDU);
        long uygulaniyor = emissionRepo.countByEtUygulamaDurumu(EtUygulamaDurumu.UYGULANIYOR);
        long uygulanmiyor = emissionRepo.countByEtUygulamaDurumu(EtUygulamaDurumu.UYGULANMIYOR);
        long taciz = emissionRepo.countByTacizTrue();
        long total = emissionRepo.count();

        long etTotalTarget = susturulan + uygulaniyor;
        double etSuccessRate = etTotalTarget > 0 ? Math.round((susturulan * 100.0) / etTotalTarget) : 0.0;

        List<TacticalEmissionEntity> all = emissionRepo.findAll();
        List<Map<String, Object>> tacizList = new ArrayList<>();
        List<Map<String, Object>> jammedList = new ArrayList<>();

        for (TacticalEmissionEntity e : all) {
            if (Boolean.TRUE.equals(e.getTaciz())) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", e.getId());
                item.put("radarAdi", e.getRadarAdi());
                item.put("teshis", e.getTeshisKimlik() != null ? e.getTeshisKimlik().name() : "BILINMEYEN");
                item.put("platformOrtami", e.getPlatformOrtami() != null ? e.getPlatformOrtami().name() : "-");
                item.put("kuyrukNo", e.getUcakKuyrukNumarasi() != null ? e.getUcakKuyrukNumarasi() : "-");
                item.put("rcs", e.getRadarKesitAlani() != null ? e.getRadarKesitAlani() : "-");
                item.put("hss", e.getHss() != null ? e.getHss() : "-");
                item.put("mevzi", e.getHedefYerBilgisi() != null ? e.getHedefYerBilgisi() : "-");
                tacizList.add(item);
            }
            if (e.getEtUygulamaDurumu() == EtUygulamaDurumu.SUSTURDU || e.getEtUygulamaDurumu() == EtUygulamaDurumu.UYGULANIYOR) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", e.getId());
                item.put("radarAdi", e.getRadarAdi());
                item.put("etDurumu", e.getEtUygulamaDurumu().name());
                item.put("genlikDbm", e.getGenlikDbm());
                item.put("spotNo", e.getSpotNo());
                jammedList.add(item);
            }
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("toplamRadarSayisi", total);
        res.put("aktifEtUygulananSayisi", uygulaniyor);
        res.put("susturulanHedefSayisi", susturulan);
        res.put("etUygulanmayanSayisi", uygulanmiyor);
        res.put("etBasariOraniYuzde", etSuccessRate);
        res.put("tacizYapanHedefSayisi", taciz);
        res.put("ornekTacizHedefleri", tacizList.stream().limit(8).collect(Collectors.toList()));
        res.put("ornekEtHedefleri", jammedList.stream().limit(8).collect(Collectors.toList()));

        return res;
    }

    public Map<String, Object> getRadarAgilityAndSignatureAnalysis() {
        log.info("Tool Cagrisi: getRadarAgilityAndSignatureAnalysis() calistirildi.");
        CALL_LOGS.get().add("Tool Cagrisi: getRadarAgilityAndSignatureAnalysis() ile Radar Ceviklik Analizi yapildi.");

        List<TacticalEmissionEntity> all = emissionRepo.findAll();
        int tier1Cevik = 0;
        int tier2Orta = 0;
        int tier3Standart = 0;

        List<Map<String, Object>> agileRadars = new ArrayList<>();

        for (TacticalEmissionEntity e : all) {
            double bw = 0.0;
            if (e.getMinFrekansMhz() != null && e.getMaxFrekansMhz() != null) {
                bw = e.getMaxFrekansMhz() - e.getMinFrekansMhz();
            }
            double priSpread = 0.0;
            if (e.getMinPriMicroSec() != null && e.getMaxPriMicroSec() != null) {
                priSpread = e.getMaxPriMicroSec() - e.getMinPriMicroSec();
            }

            boolean freqAgile = bw >= 80.0 || (e.getFrekansListesiJson() != null && e.getFrekansListesiJson().contains(","));
            boolean priAgile = priSpread >= 20.0 || (e.getPriListesiJson() != null && e.getPriListesiJson().contains(","));

            String tier;
            if (freqAgile && priAgile) {
                tier = "TIER_1_YUKSEK_CEVIK";
                tier1Cevik++;
            } else if (freqAgile || priAgile) {
                tier = "TIER_2_ORTA_CEVIK";
                tier2Orta++;
            } else {
                tier = "TIER_3_KONVANSIYONEL";
                tier3Standart++;
            }

            if (freqAgile || priAgile) {
                Map<String, Object> rad = new LinkedHashMap<>();
                rad.put("id", e.getId());
                rad.put("radarAdi", e.getRadarAdi());
                rad.put("teshis", e.getTeshisKimlik() != null ? e.getTeshisKimlik().name() : "BILINMEYEN");
                rad.put("frekansBandwidthMhz", Math.round(bw * 10.0) / 10.0);
                rad.put("priSpreadMicroSec", Math.round(priSpread * 10.0) / 10.0);
                rad.put("modulasyon", e.getModulasyon() != null ? e.getModulasyon().name() : "SABIT");
                rad.put("pulseCw", e.getPulseCw() != null ? e.getPulseCw().name() : "PULSE");
                rad.put("ceviklikSinifi", tier);
                agileRadars.add(rad);
            }
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("toplamIncelenenRadar", all.size());
        res.put("tier1YuksekCevikSayisi", tier1Cevik);
        res.put("tier2OrtaCevikSayisi", tier2Orta);
        res.put("tier3KonvansiyonelSayisi", tier3Standart);
        res.put("cevikRadarOrnekleri", agileRadars.stream().limit(10).collect(Collectors.toList()));

        return res;
    }

    public Map<String, Object> getElectronicOrderOfBattleSummary() {
        log.info("Tool Cagrisi: getElectronicOrderOfBattleSummary() calistirildi.");
        CALL_LOGS.get().add("Tool Cagrisi: getElectronicOrderOfBattleSummary() ile EOB Hiyerarsisi cikarildi.");

        List<TacticalEmissionEntity> all = emissionRepo.findAll();
        Map<String, Long> gorevCount = new LinkedHashMap<>();
        Map<String, Long> hssCount = new LinkedHashMap<>();
        Map<String, Long> platformCount = new LinkedHashMap<>();
        Map<String, Long> teshisCount = new LinkedHashMap<>();

        for (TacticalEmissionEntity e : all) {
            String g = e.getRadarGorevi() != null ? e.getRadarGorevi() : "TANIMSIZ";
            gorevCount.put(g, gorevCount.getOrDefault(g, 0L) + 1);

            if (e.getHss() != null && !e.getHss().isBlank()) {
                hssCount.put(e.getHss(), hssCount.getOrDefault(e.getHss(), 0L) + 1);
            }

            String p = e.getPlatformOrtami() != null ? e.getPlatformOrtami().name() : "KARA";
            platformCount.put(p, platformCount.getOrDefault(p, 0L) + 1);

            String t = e.getTeshisKimlik() != null ? e.getTeshisKimlik().name() : "BILINMEYEN";
            teshisCount.put(t, teshisCount.getOrDefault(t, 0L) + 1);
        }

        Map<String, Object> res = new LinkedHashMap<>();
        res.put("toplamRadarEmisyonu", all.size());
        res.put("teshisDagilimi", teshisCount);
        res.put("platformOrtamiDagilimi", platformCount);
        res.put("radarGorevDagilimi", gorevCount);
        res.put("hssHavaSavunmaBataryalari", hssCount);

        return res;
    }

    private String formatTimestampObj(Object obj) {
        if (obj == null) return "-";
        if (obj instanceof Instant ins) return formatInstant(ins);
        if (obj instanceof java.sql.Timestamp ts) return formatInstant(ts.toInstant());
        if (obj instanceof java.time.LocalDateTime ldt) return formatInstant(ldt.atZone(ZoneId.systemDefault()).toInstant());
        return obj.toString();
    }

    public HierarchyAnalysisResultDto analyzeRadarHierarchy(
        String zaman,
        String kategori,
        String durum,
        Integer limit
    ) {
        CALL_LOGS.get().add("Tool Cagrisi: analyzeRadarHierarchy(zaman=" + zaman + ", kategori=" + kategori + ", durum=" + durum + ", limit=" + limit + ") calistirildi.");

        Instant baslangic = parseTime(zaman, true);
        Instant bitis = parseTime(zaman, false);
        if (baslangic == null) {
            baslangic = Instant.EPOCH;
        }
        if (bitis == null) {
            bitis = Instant.now().plus(Duration.ofHours(24));
        }

        String zamanLabel = (zaman != null && !zaman.isBlank()) ? zaman : "TUM_ZAMANLAR";

        Object[] rawCounts = emissionRepo.getRadarHierarchyCountsWithTimeRange(baslangic, bitis);
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
                lobRows = emissionRepo.getRadarUnassociatedLobsWithTimeRange(baslangic, bitis, maxRows);
            } else if (dur.contains("BAGLI")) {
                lobRows = emissionRepo.getRadarAssociatedLobsWithTimeRange(baslangic, bitis, maxRows);
            } else {
                lobRows = emissionRepo.getRadarAllLobsWithTimeRange(baslangic, bitis, maxRows);
            }

            for (Object[] r : lobRows) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", r[0]);
                item.put("tip", "HAM_LOB");
                item.put("radarAdi", r[1]);
                item.put("teshis", r[2]);
                item.put("ehUnsuru", r[3]);
                item.put("ehUnsuruKonum", (r[4] != null && r[5] != null) ? String.format(Locale.ENGLISH, "%.2f K, %.2f D", ((Number) r[4]).doubleValue(), ((Number) r[5]).doubleValue()) : "-");
                item.put("yonDerece", r[6] != null ? Math.round(((Number) r[6]).doubleValue() * 10.0) / 10.0 : null);
                item.put("frekansMhz", r[7] != null ? Math.round(((Number) r[7]).doubleValue()) : null);
                item.put("sonTespitZamani", formatTimestampObj(r[8]));
                item.put("kestirimId", r[10]);
                item.put("durum", r[10] != null ? "BAGLI_LOB (Hedef Kestirimi: " + r[10] + ")" : "BASIBOS_LOB (Tekil Hat / Kestirilemedi)");
                item.put("operatorNotu", r[9]);
                detaylar.add(item);
            }
        } else {
            List<Object[]> elipsRows;
            if (dur.contains("COCUKLU")) {
                elipsRows = emissionRepo.getRadarChildedEllipsesWithTimeRange(baslangic, bitis, maxRows);
            } else if (dur.contains("COCUKSUZ")) {
                elipsRows = emissionRepo.getRadarChildlessEllipsesWithTimeRange(baslangic, bitis, maxRows);
            } else {
                elipsRows = emissionRepo.getRadarAllEllipsesWithTimeRange(baslangic, bitis, maxRows);
            }

            for (Object[] r : elipsRows) {
                Map<String, Object> item = new LinkedHashMap<>();
                item.put("id", r[0]);
                item.put("tip", "KESTIRIM_ELIPS");
                item.put("radarAdi", r[1]);
                item.put("teshis", r[2]);
                item.put("ehUnsuru", r[3]);
                item.put("hedefKonum", (r[5] != null && r[6] != null) ? String.format(Locale.ENGLISH, "%.2f K, %.2f D", ((Number) r[5]).doubleValue(), ((Number) r[6]).doubleValue()) : "-");
                item.put("hataElipsiMeters", (r[7] != null && r[8] != null) ? String.format(Locale.ENGLISH, "%.0f m x %.0f m", ((Number) r[7]).doubleValue(), ((Number) r[8]).doubleValue()) : "-");
                item.put("sonTespitZamani", formatTimestampObj(r[9]));
                long cc = extractLong(r, 11);
                item.put("cocukLobSayisi", cc);
                item.put("durum", cc > 0 ? "COCUKLU_ELIPS (" + cc + " LOB Fuzyonu)" : "COCUKSUZ_ELIPS (Ham LOB\'suz / Dogrudan Kestirim)");
                item.put("operatorNotu", r[10]);
                detaylar.add(item);
            }
        }

        String taktikDegerlendirme = String.format(
            "Radar EH Hiyerarsi Ozeti (%s): Toplam %d Kestirim Elipsinin %d tanesi yerel coklu istasyon fuzyonuyla (cocuklu, ort. %.1f LOB/kestirim), " +
            "%d tanesi ham LOB kayitsiz dogrudan/harici (cocuksuz) izdir. Sahadaki %d Ham LOB'un %d tanesi bir kestirime baglanmis, %d tanesi ise (%%%.1f) " +
            "henuz diger istasyonlarca teyit edilmemis basibos erken ihbar hatlaridir.",
            zamanLabel, totalElips, childedElips, avgChild, childlessElips, totalLob, bagliLob, basibosLob, basibosRate
        );

        return new HierarchyAnalysisResultDto(
            "RADAR",
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

    public HierarchyAnalysisResultDto analyzeRadarHierarchy(String zaman, String durum) {
        return analyzeRadarHierarchy(zaman, null, durum, 15);
    }

    public HierarchyAnalysisResultDto analyzeRadarHierarchy(String zaman) {
        return analyzeRadarHierarchy(zaman, null, null, 15);
    }

    public HierarchyAnalysisResultDto analyzeRadarHierarchy() {
        return analyzeRadarHierarchy(null, null, null, 15);
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

    public TacticalDistributionDto getTacticalDistribution(String source, String criterion, String timeRange) {
        CALL_LOGS.get().add(String.format("Tool Çağrısı: getTacticalDistribution(source=%s, criterion=%s, timeRange=%s) çalıştırıldı.", source, criterion, timeRange));
        return distributionService.getDistribution(source, criterion, timeRange, 8);
    }

    public TacticalDistributionDto getTacticalDistribution(String source, String criterion) {
        return getTacticalDistribution(source, criterion, "ALL");
    }

    public TacticalDistributionDto getTacticalDistribution(String criterion) {
        return getTacticalDistribution("RADAR", criterion, "ALL");
    }

    public TacticalWaveformTimelineDto getTacticalWaveformTimeline(String source, String targetKey, String timeRange) {
        CALL_LOGS.get().add(String.format("Tool Çağrısı: getTacticalWaveformTimeline(source=%s, targetKey=%s, timeRange=%s) çalıştırıldı.", source, targetKey, timeRange));
        return timelineService.getTimeline(source, targetKey, timeRange, 200);
    }

    public TacticalWaveformTimelineDto getTacticalWaveformTimeline(String source, String targetKey) {
        return getTacticalWaveformTimeline(source, targetKey, "ALL");
    }

    public TacticalWaveformTimelineDto getTacticalWaveformTimeline(String targetKey) {
        return getTacticalWaveformTimeline("RADAR", targetKey, "ALL");
    }

    public RadarSpectrumOccupancyDto getRadarSpectrumOccupancy(String timeRange, String teshisKimlik) {
        CALL_LOGS.get().add(String.format("Tool Çağrısı: getRadarSpectrumOccupancy(timeRange=%s, teshisKimlik=%s) çalıştırıldı.", timeRange, teshisKimlik));
        return spectrumOccupancyService.calculateOccupancy(timeRange, teshisKimlik, 50.0, true);
    }

    public RadarSpectrumOccupancyDto getRadarSpectrumOccupancy(String timeRange) {
        return getRadarSpectrumOccupancy(timeRange, "DUSMAN");
    }

    public RadarSpectrumOccupancyDto getRadarSpectrumOccupancy() {
        return getRadarSpectrumOccupancy("SON_24_SAAT", "DUSMAN");
    }

    public WeekdayRadarDistributionDto getWeekdayEmissionDistribution(String source, String recordType, String timeRange) {
        CALL_LOGS.get().add(String.format("Tool Çağrısı: getWeekdayEmissionDistribution(source=%s, recordType=%s, timeRange=%s) çalıştırıldı.", source, recordType, timeRange));
        return weekdayRadarService.calculateWeekdayDistribution(source, recordType, timeRange);
    }

    public WeekdayRadarDistributionDto getWeekdayEmissionDistribution(String source, String recordType) {
        return getWeekdayEmissionDistribution(source, recordType, "ALL");
    }

    public WeekdayRadarDistributionDto getWeekdayEmissionDistribution(String source) {
        return getWeekdayEmissionDistribution(source, "TUMU", "ALL");
    }

    public WeekdayRadarDistributionDto getWeekdayEmissionDistribution() {
        return getWeekdayEmissionDistribution("TUMU", "TUMU", "ALL");
    }
}

