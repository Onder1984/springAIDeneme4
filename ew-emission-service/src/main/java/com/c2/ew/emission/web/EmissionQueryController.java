package com.c2.ew.emission.web;

import com.c2.ew.emission.dto.*;
import com.c2.ew.emission.service.TacticalComintQueryService;
import com.c2.ew.emission.service.TacticalEmissionQueryService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/emissions")
public class EmissionQueryController {

    private final TacticalEmissionQueryService emissionQueryService;
    private final TacticalComintQueryService comintQueryService;

    public EmissionQueryController(
        TacticalEmissionQueryService emissionQueryService,
        TacticalComintQueryService comintQueryService
    ) {
        this.emissionQueryService = emissionQueryService;
        this.comintQueryService = comintQueryService;
    }

    /**
     * AI Copilot @Tool çağrıları için Radar sorgulama endpoint'i
     */
    @PostMapping("/radar/query")
    public ResponseEntity<TacticalQueryResult<TacticalEmissionSummaryDto>> queryRadarEmissions(
        @RequestBody RadarFilterQueryDto query
    ) {
        TacticalQueryResult<TacticalEmissionSummaryDto> result = emissionQueryService.queryEmissions(
            query.spotNo(),
            query.teshisKimlik(),
            query.veriKaynagi(),
            query.radarAdi(),
            query.radarGorevi(),
            query.hss(),
            query.bolge(),
            query.taciz(),
            query.etUygulamaDurumu(),
            query.platformOrtami(),
            query.kayitTipi(),
            query.minFrekansMhz(),
            query.maxFrekansMhz(),
            query.frekansTipi(),
            query.minPriMicroSec(),
            query.maxPriMicroSec(),
            query.minPwMicroSec(),
            query.maxPwMicroSec(),
            query.minAtpMicroSec(),
            query.maxAtpMicroSec(),
            query.minGenlikDbm(),
            query.maxGenlikDbm(),
            null,
            null,
            query.baslangicZamani() != null ? query.baslangicZamani() : query.zamanAraligi(),
            query.bitisZamani(),
            query.limit() != null ? query.limit() : 25
        );
        return ResponseEntity.ok(result);
    }

    /**
     * AI Copilot @Tool çağrıları için Muhabere (COMINT) sorgulama endpoint'i
     */
    @PostMapping("/comint/query")
    public ResponseEntity<TacticalQueryResult<TacticalComintSummaryDto>> queryComintEmissions(
        @RequestBody ComintFilterQueryDto query
    ) {
        TacticalQueryResult<TacticalComintSummaryDto> result = comintQueryService.queryComintEmissions(
            query.teshisKimlik(),
            null,
            query.lisan(),
            query.tip(),
            query.haberlesme(),
            query.protokol(),
            null,
            query.cagriAdi(),
            query.bolge(),
            null,
            query.kayitTipi(),
            query.minFrekansMhz(),
            query.maxFrekansMhz(),
            query.minGenlikDbm(),
            query.maxGenlikDbm(),
            null,
            null,
            query.zamanAraligi(),
            null,
            query.limit() != null ? query.limit() : 25
        );
        return ResponseEntity.ok(result);
    }

    /**
     * AI Copilot @Tool çağrıları için Radar net kayıt adedi (COUNT) endpoint'i
     */
    @PostMapping("/radar/count")
    public ResponseEntity<TacticalCountResultDto> countRadarEmissions(@RequestBody RadarFilterQueryDto query) {
        long count = emissionQueryService.countEmissions(
            query.spotNo(),
            query.teshisKimlik(),
            query.veriKaynagi(),
            query.radarAdi(),
            query.radarGorevi(),
            query.hss(),
            query.bolge(),
            query.taciz(),
            query.etUygulamaDurumu(),
            query.platformOrtami(),
            query.kayitTipi(),
            query.minFrekansMhz(),
            query.maxFrekansMhz(),
            query.frekansTipi(),
            query.minPriMicroSec(),
            query.maxPriMicroSec(),
            query.minPwMicroSec(),
            query.maxPwMicroSec(),
            query.minAtpMicroSec(),
            query.maxAtpMicroSec(),
            query.minGenlikDbm(),
            query.maxGenlikDbm(),
            null,
            null,
            query.baslangicZamani() != null ? query.baslangicZamani() : query.zamanAraligi(),
            query.bitisZamani()
        );

        StringBuilder sb = new StringBuilder();
        if (query.spotNo() != null && !query.spotNo().isBlank()) sb.append("Spot No: ").append(query.spotNo()).append(" ");
        if (query.teshisKimlik() != null && !query.teshisKimlik().isBlank()) sb.append("Teşhis: ").append(query.teshisKimlik()).append(" ");
        if (query.radarAdi() != null && !query.radarAdi().isBlank()) sb.append("Radar: ").append(query.radarAdi()).append(" ");
        if (query.bolge() != null && !query.bolge().isBlank()) sb.append("Bölge: ").append(query.bolge()).append(" ");
        if (query.kayitTipi() != null && !query.kayitTipi().isBlank()) sb.append("Kayıt Tipi: ").append(query.kayitTipi()).append(" ");
        String kriter = sb.length() > 0 ? sb.toString().trim() : "Genel Taktik Radar Filtresi";
        String aciklama = String.format("Veritabanında %s kriterine uyan toplam %d adet kayıt tespit edilmiştir.", kriter, count);

        return ResponseEntity.ok(new TacticalCountResultDto(count, kriter, aciklama));
    }

    /**
     * AI Copilot @Tool çağrıları için Muhabere (COMINT) net kayıt adedi (COUNT) endpoint'i
     */
    @PostMapping("/comint/count")
    public ResponseEntity<TacticalCountResultDto> countComintEmissions(@RequestBody ComintFilterQueryDto query) {
        long count = comintQueryService.countComintEmissions(
            query.teshisKimlik(),
            null,
            query.lisan(),
            query.tip(),
            query.haberlesme(),
            query.protokol(),
            null,
            query.cagriAdi(),
            query.bolge(),
            null,
            query.kayitTipi(),
            query.minFrekansMhz(),
            query.maxFrekansMhz(),
            query.minGenlikDbm(),
            query.maxGenlikDbm(),
            null,
            null,
            query.zamanAraligi(),
            null
        );

        StringBuilder sb = new StringBuilder();
        if (query.cagriAdi() != null && !query.cagriAdi().isBlank()) sb.append("Çağrı Adı: ").append(query.cagriAdi()).append(" ");
        if (query.teshisKimlik() != null && !query.teshisKimlik().isBlank()) sb.append("Teşhis: ").append(query.teshisKimlik()).append(" ");
        if (query.lisan() != null && !query.lisan().isBlank()) sb.append("Lisan: ").append(query.lisan()).append(" ");
        if (query.tip() != null && !query.tip().isBlank()) sb.append("Bant/Tip: ").append(query.tip()).append(" ");
        if (query.bolge() != null && !query.bolge().isBlank()) sb.append("Bölge: ").append(query.bolge()).append(" ");
        if (query.kayitTipi() != null && !query.kayitTipi().isBlank()) sb.append("Kayıt Tipi: ").append(query.kayitTipi()).append(" ");
        String kriter = sb.length() > 0 ? sb.toString().trim() : "Genel Muhabere (COMINT) Filtresi";
        String aciklama = String.format("Veritabanında %s kriterine uyan toplam %d adet muhabere yayını tespit edilmiştir.", kriter, count);

        return ResponseEntity.ok(new TacticalCountResultDto(count, kriter, aciklama));
    }

    /**
     * Radar Makro Durum İstatistikleri
     */
    @GetMapping("/radar/macro-stats")
    public ResponseEntity<Map<String, Object>> getRadarMacroStats() {
        return ResponseEntity.ok(emissionQueryService.getTacticalMacroStats());
    }

    /**
     * Öncelikli Düşman Radar Tehditleri
     */
    @GetMapping("/radar/priority-threats")
    public ResponseEntity<List<TacticalEmissionSummaryDto>> getRadarPriorityThreats(
        @RequestParam(defaultValue = "15") Integer limit
    ) {
        return ResponseEntity.ok(emissionQueryService.queryPriorityThreats(limit));
    }

    /**
     * Muhabere Makro Durum İstatistikleri
     */
    @GetMapping("/comint/macro-stats")
    public ResponseEntity<Map<String, Object>> getComintMacroStats() {
        return ResponseEntity.ok(comintQueryService.getComintMacroStats());
    }

    /**
     * Öncelikli Düşman Muhabere Tehditleri
     */
    @GetMapping("/comint/priority-threats")
    public ResponseEntity<List<TacticalComintSummaryDto>> getComintPriorityThreats(
        @RequestParam(defaultValue = "15") Integer limit
    ) {
        return ResponseEntity.ok(comintQueryService.queryPriorityComintThreats(limit));
    }

    /**
     * Telsiz Çağrı Adı İkili İrtibat Matrisi
     */
    @GetMapping("/comint/callsign-pairs")
    public ResponseEntity<List<CallsignPairStatsDto>> getCallsignPairs(
        @RequestParam(required = false) String cagriAdi,
        @RequestParam(required = false) String karsiCagriAdi,
        @RequestParam(required = false) String baslangicZamani,
        @RequestParam(required = false) String bitisZamani,
        @RequestParam(defaultValue = "15") Integer limit
    ) {
        return ResponseEntity.ok(comintQueryService.getCallsignPairInteractionStats(
            cagriAdi, karsiCagriAdi, baslangicZamani, bitisZamani, limit
        ));
    }

    /**
     * En Aktif Telsiz Çağrı Adları
     */
    @GetMapping("/comint/most-active-callsigns")
    public ResponseEntity<Map<String, Long>> getMostActiveCallsigns(
        @RequestParam(required = false) String baslangicZamani,
        @RequestParam(required = false) String bitisZamani,
        @RequestParam(defaultValue = "15") Integer limit
    ) {
        return ResponseEntity.ok(comintQueryService.getMostActiveCallsigns(baslangicZamani, bitisZamani, limit));
    }

    /**
     * Telsiz Trafik Trendi / Yoğunluk Analizi
     */
    @GetMapping("/comint/traffic-trend")
    public ResponseEntity<Map<String, Object>> getComintTrafficTrend(
        @RequestParam(required = false) String cagriAdi,
        @RequestParam(required = false) String baslangicZamani,
        @RequestParam(required = false) String bitisZamani
    ) {
        return ResponseEntity.ok(comintQueryService.getComintTrafficTrend(cagriAdi, baslangicZamani, bitisZamani));
    }

    /**
     * Kategorik Taktik Dağılım Analizi
     */
    @GetMapping("/distribution")
    public ResponseEntity<TacticalDistributionDto> getTacticalDistribution(
        @RequestParam(defaultValue = "RADAR") String source,
        @RequestParam(defaultValue = "TESHIS") String criterion,
        @RequestParam(defaultValue = "ALL") String timeRange
    ) {
        return ResponseEntity.ok(emissionQueryService.getTacticalDistribution(source, criterion, timeRange));
    }

    /**
     * Radar Frekans Spektrum Doluluk Oranı
     */
    @GetMapping("/radar/spectrum-occupancy")
    public ResponseEntity<RadarSpectrumOccupancyDto> getRadarSpectrumOccupancy(
        @RequestParam(defaultValue = "SON_24_SAAT") String timeRange,
        @RequestParam(defaultValue = "DUSMAN") String teshisKimlik
    ) {
        return ResponseEntity.ok(emissionQueryService.getRadarSpectrumOccupancy(timeRange, teshisKimlik));
    }

    /**
     * Tekil Radar Emisyon Detayı
     */
    @GetMapping("/radar/{id}")
    public ResponseEntity<TacticalEmissionDetailDto> getRadarEmissionDetail(@PathVariable String id) {
        return ResponseEntity.ok(emissionQueryService.getEmissionDetails(id));
    }

    /**
     * Tekil Comint Yayını Detayı
     */
    @GetMapping("/comint/{id}")
    public ResponseEntity<TacticalComintDetailDto> getComintDetail(@PathVariable String id) {
        return ResponseEntity.ok(comintQueryService.getComintDetails(id));
    }

    /**
     * Tüm Radar Emisyonları
     */
    @GetMapping("/radar/all")
    public ResponseEntity<List<TacticalEmissionSummaryDto>> getAllRadarEmissions() {
        return ResponseEntity.ok(emissionQueryService.getAllEmissions());
    }

    /**
     * Tüm Muhabere Yayınları
     */
    @GetMapping("/comint/all")
    public ResponseEntity<List<TacticalComintSummaryDto>> getAllComintEmissions() {
        return ResponseEntity.ok(comintQueryService.getAllComints());
    }

    /**
     * 1-to-N Benzerlik Karşılaştırma Raporu
     */
    @GetMapping("/radar/{id}/similar")
    public ResponseEntity<Map<String, Object>> findSimilarRadarEmissions(
        @PathVariable String id,
        @RequestParam(defaultValue = "SON_24_SAAT") String timeRange,
        @RequestParam(defaultValue = "5") Integer limit
    ) {
        return ResponseEntity.ok(emissionQueryService.findSimilarRadarEmissions(id, timeRange, limit));
    }

    /**
     * İki Radar Karşılaştırma Raporu
     */
    @GetMapping("/radar/compare")
    public ResponseEntity<Map<String, Object>> compareRadarEmissions(
        @RequestParam String id1,
        @RequestParam String id2
    ) {
        return ResponseEntity.ok(emissionQueryService.compareRadarEmissions(id1, id2));
    }
}
