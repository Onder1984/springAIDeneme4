package com.c2.ew.copilot.dto;

import java.time.Instant;
import java.util.List;

/**
 * Taktik Dalga Formu & Zamansal Değişim DTO'su (ECharts Intraday-Breaks Uyumlu)
 * Radar: spotNo bazında Frekans, PRI, PW ve Genlik zaman serisi
 * COMINT: cagriAdi bazında Frekans ve Genlik zaman serisi
 */
public record TacticalWaveformTimelineDto(
    String source,               // "RADAR" | "COMINT"
    String targetKey,            // "SP048" veya "VOLGA-01"
    String targetLabel,          // "Patriot AN/MPQ-53 [SP048]"
    String timeRange,            // "ALL" | "SON_24_SAAT" | "SON_6_SAAT" | "SON_1_SAAT"
    int totalCount,
    List<TimelinePoint> points,
    List<AvailableTarget> availableTargets
) {
    public record TimelinePoint(
        String id,
        Instant timestamp,
        String timestampFormatted,
        Double minFrekansMhz,
        Double maxFrekansMhz,
        Double centerFrekansMhz,
        Double minPriMicroSec,
        Double maxPriMicroSec,
        Double centerPriMicroSec,
        Double minPwMicroSec,
        Double maxPwMicroSec,
        Double centerPwMicroSec,
        Double genlikDbm,
        Double sureSn,
        String teshis,
        String radarAdi,
        String gorev,
        String modulasyon,
        String yer,
        String mevzi
    ) {}

    public record AvailableTarget(
        String key,
        String label,
        long count
    ) {}
}
