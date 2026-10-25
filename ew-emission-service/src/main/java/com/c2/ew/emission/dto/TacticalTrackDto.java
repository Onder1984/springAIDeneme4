package com.c2.ew.emission.dto;

import java.time.Instant;
import java.util.List;

/**
 * Taktik Kinematik Hedef İntikal ve Konum Geçmişi İzi DTO'su.
 * Hedefin kronolojik kestirim elipslerini, vektörel intikal rotasını,
 * anlık kerteriz/hız değişimlerini ve toplam kinematik özetini taşır.
 */
public record TacticalTrackDto(
    String source,                 // "RADAR" | "COMINT"
    String targetKey,              // "SP072", "VOLGA-01", vb.
    String targetLabel,            // "Tor-M2 Uçaksavar Arama Radarı [SP072]"
    int totalPoints,               // Toplam kestirim noktası adedi
    Double totalDistanceKm,        // Toplam kat edilen mesafe (km)
    Double timeSpanHours,          // Gözlem süresi (saat)
    Double avgSpeedKmh,            // Ortalama intikal sürati (km/s)
    Double overallHeadingDeg,      // İlk noktadan son noktaya genel intikal yönü (derece)
    String overallHeadingCardinal, // Yön pusula adı: "Kuzeydoğu (NE)"
    String firstDetectedFormatted, // İlk tespit zamanı
    String lastDetectedFormatted,  // Son tespit zamanı
    List<TrackPoint> points,       // Kronolojik kestirim noktaları
    List<TacticalWaveformTimelineDto.AvailableTarget> availableTargets // Hızlı seçim listesi
) {
    public record TrackPoint(
        String id,
        int sequenceNo,
        Double enlem,
        Double boylam,
        Double semiMajor,
        Double semiMinor,
        Double azimuth,
        Instant timestamp,
        String timestampFormatted,
        String teshisKimlik,
        String hedefAdi,
        String spotNoOrCallsign,
        Double minFrekansMhz,
        Double maxFrekansMhz,
        Double priMicroSec,
        Double pwMicroSec,
        String modulasyon,
        Double distanceFromPrevKm,
        Double bearingFromPrevDeg,
        Double speedFromPrevKmh,
        String mevziBilgisi
    ) {}
}
