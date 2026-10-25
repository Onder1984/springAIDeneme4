package com.c2.ew.copilot.client;

import com.c2.ew.copilot.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Veri Sağlayıcı Servis (ew-emission-service) ile haberleşen REST İstemcisi.
 * AI Copilot servisi doğrudan veritabanına bağlı DEĞİLDİR; tüm yayın verilerini
 * bu istemci üzerinden DTO sözleşmeleriyle çeker.
 */
@Component
public class EmissionRestClient {

    private static final Logger log = LoggerFactory.getLogger(EmissionRestClient.class);
    private final RestClient restClient;

    public EmissionRestClient(
        RestClient.Builder restClientBuilder,
        @Value("${emission.service.url:http://localhost:8081}") String emissionServiceUrl
    ) {
        log.info("📡 [EmissionRestClient] Hedef Veri Servisi URL: {}", emissionServiceUrl);
        var requestFactory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(15_000);
        requestFactory.setReadTimeout(60_000);

        this.restClient = restClientBuilder
            .requestFactory(requestFactory)
            .baseUrl(emissionServiceUrl)
            .build();
    }

    /**
     * Filtreli Radar Emisyonu Sorgusu
     */
    public TacticalQueryResult<TacticalEmissionSummaryDto> queryRadarEmissions(RadarFilterQueryDto filter) {
        try {
            return restClient.post()
                .uri("/api/v1/emissions/radar/query")
                .contentType(MediaType.APPLICATION_JSON)
                .body(filter)
                .retrieve()
                .body(new ParameterizedTypeReference<TacticalQueryResult<TacticalEmissionSummaryDto>>() {});
        } catch (Exception e) {
            log.error("Radar emisyonu sorgusu hatası: {}", e.getMessage());
            return TacticalQueryResult.of(Collections.emptyList(), 0, "Radar Tablosu");
        }
    }

    /**
     * Filtreli Muhabere (COMINT) Yayını Sorgusu
     */
    public TacticalQueryResult<TacticalComintSummaryDto> queryComintEmissions(ComintFilterQueryDto filter) {
        try {
            return restClient.post()
                .uri("/api/v1/emissions/comint/query")
                .contentType(MediaType.APPLICATION_JSON)
                .body(filter)
                .retrieve()
                .body(new ParameterizedTypeReference<TacticalQueryResult<TacticalComintSummaryDto>>() {});
        } catch (Exception e) {
            log.error("Muhabere yayını sorgusu hatası: {}", e.getMessage());
            return TacticalQueryResult.of(Collections.emptyList(), 0, "Muhabere Tablosu");
        }
    }

    /**
     * Net Radar Kayıt Sayısı (COUNT)
     */
    public TacticalCountResultDto countRadarEmissions(RadarFilterQueryDto filter) {
        try {
            return restClient.post()
                .uri("/api/v1/emissions/radar/count")
                .contentType(MediaType.APPLICATION_JSON)
                .body(filter)
                .retrieve()
                .body(TacticalCountResultDto.class);
        } catch (Exception e) {
            log.error("countRadarEmissions hatası: {}", e.getMessage());
            return new TacticalCountResultDto(0L, "Hata", "Sorgulama hatası: " + e.getMessage());
        }
    }

    /**
     * Net Muhabere (COMINT) Kayıt Sayısı (COUNT)
     */
    public TacticalCountResultDto countComintEmissions(ComintFilterQueryDto filter) {
        try {
            return restClient.post()
                .uri("/api/v1/emissions/comint/count")
                .contentType(MediaType.APPLICATION_JSON)
                .body(filter)
                .retrieve()
                .body(TacticalCountResultDto.class);
        } catch (Exception e) {
            log.error("countComintEmissions hatası: {}", e.getMessage());
            return new TacticalCountResultDto(0L, "Hata", "Sorgulama hatası: " + e.getMessage());
        }
    }

    /**
     * Radar Makro Durum İstatistikleri
     */
    public Map<String, Object> getRadarMacroStats() {
        try {
            return restClient.get()
                .uri("/api/v1/emissions/radar/macro-stats")
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("Radar makro istatistik hatası: {}", e.getMessage());
            return Map.of("durum", "HATA", "mesaj", e.getMessage());
        }
    }

    /**
     * Öncelikli Düşman Radar Tehditleri
     */
    public List<TacticalEmissionSummaryDto> getRadarPriorityThreats(Integer limit) {
        try {
            List<TacticalEmissionSummaryDto> list = restClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/api/v1/emissions/radar/priority-threats")
                    .queryParam("limit", limit != null ? limit : 15)
                    .build()
                )
                .retrieve()
                .body(new ParameterizedTypeReference<List<TacticalEmissionSummaryDto>>() {});
            return list != null ? list : Collections.emptyList();
        } catch (Exception e) {
            log.error("Öncelikli radar tehditleri hatası: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Muhabere Makro Durum İstatistikleri
     */
    public Map<String, Object> getComintMacroStats() {
        try {
            return restClient.get()
                .uri("/api/v1/emissions/comint/macro-stats")
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("Muhabere makro istatistik hatası: {}", e.getMessage());
            return Map.of("durum", "HATA", "mesaj", e.getMessage());
        }
    }

    /**
     * Öncelikli Düşman Muhabere Tehditleri
     */
    public List<TacticalComintSummaryDto> getComintPriorityThreats(Integer limit) {
        try {
            List<TacticalComintSummaryDto> list = restClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/api/v1/emissions/comint/priority-threats")
                    .queryParam("limit", limit != null ? limit : 15)
                    .build()
                )
                .retrieve()
                .body(new ParameterizedTypeReference<List<TacticalComintSummaryDto>>() {});
            return list != null ? list : Collections.emptyList();
        } catch (Exception e) {
            log.error("Öncelikli muhabere tehditleri hatası: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Telsiz Çağrı Adı İkili İrtibat Matrisi
     */
    public List<CallsignPairStatsDto> getCallsignPairInteractionStats(
        String cagriAdi, String karsiCagriAdi, String baslangicZamani, String bitisZamani, Integer limit
    ) {
        try {
            List<CallsignPairStatsDto> list = restClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/api/v1/emissions/comint/callsign-pairs")
                    .queryParam("cagriAdi", cagriAdi)
                    .queryParam("karsiCagriAdi", karsiCagriAdi)
                    .queryParam("baslangicZamani", baslangicZamani)
                    .queryParam("bitisZamani", bitisZamani)
                    .queryParam("limit", limit != null ? limit : 15)
                    .build()
                )
                .retrieve()
                .body(new ParameterizedTypeReference<List<CallsignPairStatsDto>>() {});
            return list != null ? list : Collections.emptyList();
        } catch (Exception e) {
            log.error("Çağrı adı irtibat analizi hatası: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * En Aktif Telsiz Çağrı Adları
     */
    public Map<String, Long> getMostActiveCallsigns(String baslangicZamani, String bitisZamani, Integer limit) {
        try {
            return restClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/api/v1/emissions/comint/most-active-callsigns")
                    .queryParam("baslangicZamani", baslangicZamani)
                    .queryParam("bitisZamani", bitisZamani)
                    .queryParam("limit", limit != null ? limit : 15)
                    .build()
                )
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Long>>() {});
        } catch (Exception e) {
            log.error("En aktif çağrı adları hatası: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Telsiz Trafik Trendi
     */
    public Map<String, Object> getComintTrafficTrend(String cagriAdi, String baslangicZamani, String bitisZamani) {
        try {
            return restClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/api/v1/emissions/comint/traffic-trend")
                    .queryParam("cagriAdi", cagriAdi)
                    .queryParam("baslangicZamani", baslangicZamani)
                    .queryParam("bitisZamani", bitisZamani)
                    .build()
                )
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("Telsiz trafik trendi hatası: {}", e.getMessage());
            return Map.of("durum", "HATA", "mesaj", e.getMessage());
        }
    }

    /**
     * Kategorik Taktik Dağılım
     */
    public TacticalDistributionDto getTacticalDistribution(String source, String criterion, String timeRange) {
        try {
            return restClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/api/v1/emissions/distribution")
                    .queryParam("source", source != null ? source : "RADAR")
                    .queryParam("criterion", criterion != null ? criterion : "TESHIS")
                    .queryParam("timeRange", timeRange != null ? timeRange : "ALL")
                    .build()
                )
                .retrieve()
                .body(TacticalDistributionDto.class);
        } catch (Exception e) {
            log.error("Taktik dağılım hatası: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Radar Frekans Spektrum Doluluk
     */
    public RadarSpectrumOccupancyDto getRadarSpectrumOccupancy(String timeRange, String teshisKimlik) {
        try {
            return restClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/api/v1/emissions/radar/spectrum-occupancy")
                    .queryParam("timeRange", timeRange != null ? timeRange : "SON_24_SAAT")
                    .queryParam("teshisKimlik", teshisKimlik != null ? teshisKimlik : "DUSMAN")
                    .build()
                )
                .retrieve()
                .body(RadarSpectrumOccupancyDto.class);
        } catch (Exception e) {
            log.error("Spektrum doluluk hatası: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Tekil Radar Detayı
     */
    public TacticalEmissionDetailDto getRadarEmissionDetail(String id) {
        try {
            return restClient.get()
                .uri("/api/v1/emissions/radar/{id}", id)
                .retrieve()
                .body(TacticalEmissionDetailDto.class);
        } catch (Exception e) {
            log.error("Radar detayı alma hatası (id={}): {}", id, e.getMessage());
            return null;
        }
    }

    /**
     * Tekil Muhabere (COMINT) Detayı
     */
    public TacticalComintDetailDto getComintDetail(String id) {
        try {
            return restClient.get()
                .uri("/api/v1/emissions/comint/{id}", id)
                .retrieve()
                .body(TacticalComintDetailDto.class);
        } catch (Exception e) {
            log.error("Muhabere detayı alma hatası (id={}): {}", id, e.getMessage());
            return null;
        }
    }

    /**
     * Tüm Radar Emisyonları
     */
    public List<TacticalEmissionSummaryDto> getAllRadarEmissions() {
        try {
            List<TacticalEmissionSummaryDto> list = restClient.get()
                .uri("/api/v1/emissions/radar/all")
                .retrieve()
                .body(new ParameterizedTypeReference<List<TacticalEmissionSummaryDto>>() {});
            return list != null ? list : Collections.emptyList();
        } catch (Exception e) {
            log.error("Tüm radar emisyonları alma hatası: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * Tüm Muhabere Yayınları
     */
    public List<TacticalComintSummaryDto> getAllComintEmissions() {
        try {
            List<TacticalComintSummaryDto> list = restClient.get()
                .uri("/api/v1/emissions/comint/all")
                .retrieve()
                .body(new ParameterizedTypeReference<List<TacticalComintSummaryDto>>() {});
            return list != null ? list : Collections.emptyList();
        } catch (Exception e) {
            log.error("Tüm muhabere yayınları alma hatası: {}", e.getMessage());
            return Collections.emptyList();
        }
    }

    /**
     * 1-to-N Benzerlik Karşılaştırması
     */
    public Map<String, Object> findSimilarRadarEmissions(String targetId, String timeRange, Integer limit) {
        try {
            return restClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/api/v1/emissions/radar/{id}/similar")
                    .queryParam("timeRange", timeRange != null ? timeRange : "SON_24_SAAT")
                    .queryParam("limit", limit != null ? limit : 5)
                    .build(targetId)
                )
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("Benzer radar arama hatası: {}", e.getMessage());
            return Map.of("durum", "HATA", "mesaj", e.getMessage());
        }
    }

    /**
     * İki Radar Karşılaştırma (IoU)
     */
    public Map<String, Object> compareRadarEmissions(String id1, String id2) {
        try {
            return restClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/api/v1/emissions/radar/compare")
                    .queryParam("id1", id1)
                    .queryParam("id2", id2)
                    .build()
                )
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("Radar karşılaştırma hatası: {}", e.getMessage());
            return Map.of("durum", "HATA", "mesaj", e.getMessage());
        }
    }

    /**
     * Haftalık LOB Dağılımı (Örümcek Ağı / Spider Radar)
     */
    public Map<String, Object> getWeekdayRadarDistribution(String source, String recordType, String timeRange) {
        try {
            return restClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/api/v1/tactical/analytics/weekday-radar")
                    .queryParam("source", source != null ? source : "TUMU")
                    .queryParam("recordType", recordType != null ? recordType : "TUMU")
                    .queryParam("timeRange", timeRange != null ? timeRange : "ALL")
                    .build()
                )
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("Haftalık LOB dağılımı sorgu hatası: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Taktik Kesişim Bölgeleri (≥3 Elips) Frekans Gruplama ve Grup İçi Benzerlik Analizi
     */
    public TacticalIntersectionReportDto getIntersectionAnalysis(Integer minOverlaps, Integer zoneNumber) {
        try {
            return restClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path("/api/v1/tactical/intersection-analysis");
                    if (minOverlaps != null) builder.queryParam("minOverlaps", minOverlaps);
                    if (zoneNumber != null) builder.queryParam("zoneNumber", zoneNumber);
                    return builder.build();
                })
                .retrieve()
                .body(TacticalIntersectionReportDto.class);
        } catch (Exception e) {
            log.error("Kesişim bölgesi analizi sorgu hatası: {}", e.getMessage());
            return new TacticalIntersectionReportDto(0, minOverlaps != null ? minOverlaps : 3, zoneNumber, Collections.emptyList());
        }
    }

    /**
     * Taktik Kinematik Hedef İntikal ve Konum Geçmişi İzi
     */
    public Map<String, Object> getTargetTrack(String source, String targetKey, String timeRange, Integer limit) {
        try {
            return restClient.get()
                .uri(uriBuilder -> {
                    var builder = uriBuilder.path("/api/v1/tactical/track");
                    if (source != null) builder.queryParam("source", source);
                    if (targetKey != null) builder.queryParam("targetKey", targetKey);
                    if (timeRange != null) builder.queryParam("timeRange", timeRange);
                    if (limit != null) builder.queryParam("limit", limit);
                    return builder.build();
                })
                .retrieve()
                .body(new ParameterizedTypeReference<Map<String, Object>>() {});
        } catch (Exception e) {
            log.error("Hedef intikal izi sorgu hatası: {}", e.getMessage());
            return Collections.emptyMap();
        }
    }
}
