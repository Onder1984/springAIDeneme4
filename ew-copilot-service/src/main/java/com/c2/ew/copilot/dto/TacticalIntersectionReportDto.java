package com.c2.ew.copilot.dto;

import java.util.List;

public record TacticalIntersectionReportDto(
    int totalZones,
    int minOverlapsRequested,
    Integer queriedZoneNumber,
    List<ZoneDetailDto> zones
) {
    public record ZoneDetailDto(
        int zoneNumber,
        String zoneLabel,
        int overlapCount,
        double centerLat,
        double centerLon,
        int totalEmitters,
        List<FrequencyGroupDto> frequencyGroups
    ) {}

    public record FrequencyGroupDto(
        String bandName,
        String frequencyRange,
        int emitterCount,
        Double refFreqMhz,
        Double refPriMicroSec,
        Double refPwMicroSec,
        String refFreqRange,
        String refPriRange,
        String refPwRange,
        String refModulasyon,
        String refPlatform,
        String refTeshis,
        List<String> emitters,
        List<EmitterDetailDto> emitterDetails,
        double avgSimilarityPercent
    ) {
        public FrequencyGroupDto(
            String bandName,
            String frequencyRange,
            int emitterCount,
            Double refFreqMhz,
            Double refPriMicroSec,
            Double refPwMicroSec,
            List<String> emitters,
            List<EmitterDetailDto> emitterDetails,
            double avgSimilarityPercent
        ) {
            this(bandName, frequencyRange, emitterCount, refFreqMhz, refPriMicroSec, refPwMicroSec,
                 null, null, null, null, null, null, emitters, emitterDetails, avgSimilarityPercent);
        }
    }

    public record EmitterDetailDto(
        String id,
        String name,
        String discipline,
        String freqRange,
        String priRange,
        String pwRange,
        Double minFrekansMhz,
        Double maxFrekansMhz,
        Double minPriMicroSec,
        Double maxPriMicroSec,
        Double minPwMicroSec,
        Double maxPwMicroSec,
        String modulasyon,
        String platformOrtami,
        String teshisKimlik,
        String sonTespitZamaniFormatted,
        Double freqScore,      // 25 P (IoU)
        Double priScore,       // 15 P (IoU)
        Double pwScore,        // 10 P (IoU)
        Double waveformScore,  // 15 P (MOP 7P, Pulse/CW 5P, Pol 3P)
        Double timeScore,      // 15 P (Küme Zaman Yakınlığı)
        Double tacticalScore,  // 20 P (Teşhis 8P, Platform 7P, Görev 5P)
        double similarityScore, // 100 P Toplam
        String hss
    ) {}
}
