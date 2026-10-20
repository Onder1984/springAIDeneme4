package com.c2.ew.copilot.dto;

import java.util.List;
import java.util.Map;

/**
 * Radar Frekans Spektrum Doluluk & Kullanım Yoğunluğu Analiz DTO'su (ECharts Bar Chart & Taktik Tablo için)
 */
public record RadarSpectrumOccupancyDto(
    String timeRange,                   // "SON_1_SAAT", "SON_24_SAAT", "SON_1_HAFTA", "ALL"
    String timeRangeLabel,              // "Son 24 Saat (1 Gün)", "Son 1 Hafta", vb.
    List<String> selectedIdentities,    // ["DUSMAN"], ["DUSMAN", "MUHTEMEL_DUSMAN"]
    List<String> availableIdentities,   // ["DUSMAN", "MUHTEMEL_DUSMAN", "BILINMEYEN", "DOST", "TARAFSIZ"]
    List<BandOccupancyItem> bands,      // Sabit frekans dilimleri
    String summaryMarkdown              // Kokpit için formatlanmış Markdown tablosu ve özet
) {
    public record BandOccupancyItem(
        String bandId,                  // "BAND_100_500", "BAND_1000_2000"
        String bandName,                // "100 - 500 MHz"
        String bandDescription,         // "VHF / UHF (Erken İhbar)"
        double minFreqMhz,              // 100.0
        double maxFreqMhz,              // 500.0
        double totalBandwidthMhz,       // 400.0
        double totalOccupiedMhz,        // Seçili tüm teşhis tiplerinin birleşik kapsama genişliği (Union)
        double totalOccupancyPercent,   // (totalOccupiedMhz / totalBandwidthMhz) * 100
        long totalEmissionCount,        // Toplam kesişen yayın sayısı
        Map<String, IdentityBandDetail> identityDetails // Teşhis bazlı kırılım: "DUSMAN" -> IdentityBandDetail
    ) {}

    public record IdentityBandDetail(
        String identity,                // "DUSMAN"
        double occupiedMhz,             // Bu teşhise ait birleşik net kapsanan MHz
        double occupancyPercent,        // Yüzde doluluk oranı (0 - 100)
        long emissionCount,             // Bu teşhise ait yayın adedi
        List<String> topRadars          // Bu aralıkta tespit edilen radar modelleri (örn: ["Pantsir-S1", "S-300PMU2"])
    ) {}
}
