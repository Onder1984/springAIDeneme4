package com.c2.ew.emission.web;

import com.c2.ew.emission.dto.*;
import com.c2.ew.emission.service.*;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping({"/api/v1/tactical", "/api/tactical"})
public class TacticalController {

    private final TacticalEmissionQueryService emissionQueryService;
    private final TacticalComintQueryService comintQueryService;
    private final TacticalDistributionService distributionService;
    private final TacticalWaveformTimelineService timelineService;
    private final RadarSpectrumOccupancyService spectrumOccupancyService;
    private final TacticalWeekdayRadarService weekdayRadarService;

    public TacticalController(
        TacticalEmissionQueryService emissionQueryService,
        TacticalComintQueryService comintQueryService,
        TacticalDistributionService distributionService,
        TacticalWaveformTimelineService timelineService,
        RadarSpectrumOccupancyService spectrumOccupancyService,
        TacticalWeekdayRadarService weekdayRadarService
    ) {
        this.emissionQueryService = emissionQueryService;
        this.comintQueryService = comintQueryService;
        this.distributionService = distributionService;
        this.timelineService = timelineService;
        this.spectrumOccupancyService = spectrumOccupancyService;
        this.weekdayRadarService = weekdayRadarService;
    }

    /**
     * Radar Emisyonları Sayfalı ve Filtreli Sorgulama (Ant Design Table Server-Side Pagination)
     */
    @GetMapping("/emissions")
    public ResponseEntity<TablePageResponse<TacticalEmissionSummaryDto>> getEmissions(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "15") int size,
        @RequestParam(required = false) String search,
        @RequestParam(required = false) String teshis,
        @RequestParam(required = false) String gorev,
        @RequestParam(required = false) String platform,
        @RequestParam(required = false) Boolean taciz,
        @RequestParam(required = false) String et,
        @RequestParam(required = false) Double minFrekans,
        @RequestParam(required = false) Double maxFrekans,
        @RequestParam(required = false) Double minPri,
        @RequestParam(required = false) Double maxPri,
        @RequestParam(required = false) Double minPw,
        @RequestParam(required = false) Double maxPw,
        @RequestParam(required = false) Double minGenlik,
        @RequestParam(required = false) Double maxGenlik,
        @RequestParam(required = false) String bolge,
        @RequestParam(required = false) String hss,
        @RequestParam(required = false) String sortField,
        @RequestParam(required = false) String sortOrder,
        @RequestParam(required = false) String kayitTipi
    ) {
        return ResponseEntity.ok(emissionQueryService.getEmissionsPage(
            page, size, search, teshis, gorev, platform, taciz, et,
            minFrekans, maxFrekans, minPri, maxPri, minPw, maxPw, minGenlik, maxGenlik, bolge, hss,
            sortField, sortOrder, kayitTipi
        ));
    }

    /**
     * Tüm Radar Emisyonları (İstemci tarafı tam liste talepleri için)
     */
    @GetMapping("/emissions/all")
    public ResponseEntity<List<TacticalEmissionSummaryDto>> getAllEmissions() {
        return ResponseEntity.ok(emissionQueryService.getAllEmissions());
    }

    /**
     * Muhabere (COMINT) Yayınları Sayfalı ve Filtreli Sorgulama (Ant Design Table Server-Side Pagination)
     */
    @GetMapping({"/comints", "/comint/emissions", "/comint"})
    public ResponseEntity<TablePageResponse<TacticalComintSummaryDto>> getComints(
        @RequestParam(defaultValue = "1") int page,
        @RequestParam(defaultValue = "15") int size,
        @RequestParam(required = false) String search,
        @RequestParam(required = false) String teshis,
        @RequestParam(required = false) String tip,
        @RequestParam(required = false) String lisan,
        @RequestParam(required = false) String haberlesme,
        @RequestParam(required = false) Boolean kripto,
        @RequestParam(required = false) Double minFrekans,
        @RequestParam(required = false) Double maxFrekans,
        @RequestParam(required = false) Double minGenlik,
        @RequestParam(required = false) Double maxGenlik,
        @RequestParam(required = false) String bolge,
        @RequestParam(required = false) String protokol,
        @RequestParam(required = false) String cagriAdi,
        @RequestParam(required = false) String karsiCagriAdi,
        @RequestParam(required = false) String sortField,
        @RequestParam(required = false) String sortOrder,
        @RequestParam(required = false) String kayitTipi,
        @RequestParam(required = false) String zamanAraligi
    ) {
        return ResponseEntity.ok(comintQueryService.getComintsPage(
            page, size, search, teshis, tip, lisan, haberlesme, kripto,
            minFrekans, maxFrekans, minGenlik, maxGenlik, bolge, protokol,
            cagriAdi, karsiCagriAdi,
            sortField, sortOrder, kayitTipi, zamanAraligi
        ));
    }

    /**
     * Tüm Muhabere Yayınları
     */
    @GetMapping({"/comints/all", "/comint/emissions/all", "/comint/all"})
    public ResponseEntity<List<TacticalComintSummaryDto>> getAllComints() {
        return ResponseEntity.ok(comintQueryService.getAllComints());
    }

    /**
     * Radar Emisyonu Detay Analizi ([EMI-xxxx] Tıklama Modalı / Drawer)
     */
    @GetMapping("/emissions/{id}")
    public ResponseEntity<TacticalEmissionDetailDto> getEmissionDetails(@PathVariable String id) {
        return ResponseEntity.ok(emissionQueryService.getEmissionDetails(id));
    }

    /**
     * Muhabere (COMINT) Yayını Detay Analizi ([COM-xxxx] Tıklama Modalı / Drawer)
     */
    @GetMapping({"/comint/emissions/{id}", "/comint/{id}"})
    public ResponseEntity<TacticalComintDetailDto> getComintDetails(@PathVariable String id) {
        return ResponseEntity.ok(comintQueryService.getComintDetails(id));
    }

    /**
     * İki Radar Emisyonu Karşılaştırma (IoU)
     */
    @GetMapping({"/emissions/compare", "/emissions/correlation"})
    public ResponseEntity<Map<String, Object>> compareRadarEmissions(
        @RequestParam String id1,
        @RequestParam String id2
    ) {
        return ResponseEntity.ok(emissionQueryService.compareRadarEmissions(id1, id2));
    }

    /**
     * 1-to-N Benzerlik Arama
     */
    @GetMapping({"/emissions/{id}/similar", "/emissions/similar"})
    public ResponseEntity<Map<String, Object>> findSimilarRadarEmissions(
        @PathVariable(required = false) String id,
        @RequestParam(required = false) String targetId,
        @RequestParam(required = false, defaultValue = "SON_24_SAAT") String timeRange,
        @RequestParam(required = false, defaultValue = "5") Integer limit
    ) {
        String effectiveId = (id != null && !id.isBlank()) ? id : targetId;
        return ResponseEntity.ok(emissionQueryService.findSimilarRadarEmissions(effectiveId, timeRange, limit));
    }

    /**
     * Haftalık LOB & Kestirim Örümcek Ağı Dağılım Analizi
     */
    @GetMapping({"/analytics/weekday-radar", "/weekday-radar"})
    public ResponseEntity<WeekdayRadarDistributionDto> getWeekdayRadar(
        @RequestParam(defaultValue = "TUMU") String source,
        @RequestParam(defaultValue = "TUMU") String recordType,
        @RequestParam(defaultValue = "ALL") String timeRange
    ) {
        return ResponseEntity.ok(weekdayRadarService.calculateWeekdayDistribution(source, recordType, timeRange));
    }

    /**
     * Dalga Formu ve Zamansal Değişim (Waveform Timeline)
     */
    @GetMapping({"/analytics/waveform-timeline", "/waveform-timeline"})
    public ResponseEntity<TacticalWaveformTimelineDto> getWaveformTimeline(
        @RequestParam(defaultValue = "RADAR") String source,
        @RequestParam(required = false) String targetKey,
        @RequestParam(defaultValue = "ALL") String timeRange,
        @RequestParam(defaultValue = "200") Integer limit
    ) {
        return ResponseEntity.ok(timelineService.getTimeline(source, targetKey, timeRange, limit));
    }

    /**
     * Kategorik Taktik Dağılım Analizi (Pasta / Donut)
     */
    @GetMapping({"/analytics/distribution", "/distribution"})
    public ResponseEntity<TacticalDistributionDto> getDistribution(
        @RequestParam(defaultValue = "RADAR") String source,
        @RequestParam(defaultValue = "TESHIS") String criterion,
        @RequestParam(defaultValue = "ALL") String timeRange,
        @RequestParam(defaultValue = "8") Integer limit
    ) {
        return ResponseEntity.ok(distributionService.getDistribution(source, criterion, timeRange, limit));
    }

    /**
     * Radar Frekans Spektrum Doluluk & Bant Yoğunluk Analizi
     */
    @GetMapping({"/analytics/spectrum-occupancy", "/spectrum-occupancy"})
    public ResponseEntity<RadarSpectrumOccupancyDto> getSpectrumOccupancy(
        @RequestParam(defaultValue = "SON_24_SAAT") String timeRange,
        @RequestParam(defaultValue = "DUSMAN") String teshisKimlik
    ) {
        return ResponseEntity.ok(spectrumOccupancyService.calculateOccupancy(timeRange, teshisKimlik));
    }

    /**
     * Telsiz Çağrı Adı İletişim Matrisi (Isı Haritası)
     */
    @GetMapping({"/comint/callsign-matrix", "/callsign-matrix"})
    public ResponseEntity<List<CallsignPairStatsDto>> getCallsignMatrix(
        @RequestParam(required = false) String cagriAdi,
        @RequestParam(required = false) String karsiCagriAdi,
        @RequestParam(required = false) String baslangicZamani,
        @RequestParam(required = false) String bitisZamani,
        @RequestParam(defaultValue = "25") Integer limit
    ) {
        return ResponseEntity.ok(comintQueryService.getCallsignPairInteractionStats(
            cagriAdi, karsiCagriAdi, baslangicZamani, bitisZamani, limit
        ));
    }

    /**
     * Genel Radar İstatistikleri (HeaderBar)
     */
    @GetMapping({"/stats", "/emissions/stats"})
    public ResponseEntity<Map<String, Object>> getRadarStats() {
        return ResponseEntity.ok(emissionQueryService.getTacticalMacroStats());
    }

    /**
     * Genel Muhabere İstatistikleri (HeaderBar)
     */
    @GetMapping({"/comint/stats", "/comints/stats"})
    public ResponseEntity<Map<String, Object>> getComintStats() {
        return ResponseEntity.ok(comintQueryService.getComintMacroStats());
    }
}
