package com.c2.ew.copilot.dto;

import java.util.List;

/**
 * Haftanın Günlerine Göre LOB ve Kestirim Tespit Dağılımı DTO'su (ECharts Örümcek Ağı / Spider Radar Grafiği)
 */
public record WeekdayRadarDistributionDto(
    String source,               // "RADAR" | "COMINT" | "TUMU"
    String recordType,           // "TUMU" | "SADECE_LOB" | "SADECE_KESTIRIM"
    String timeRange,            // "ALL" | "SON_7_GUN" | "SON_30_GUN"
    long totalRadarCount,        // Filtreye uyan toplam radar tespiti
    long totalComintCount,       // Filtreye uyan toplam muhabere tespiti
    long grandTotal,             // Toplam tespit sayısı
    long maxIndicatorValue,      // ECharts radar indicator max ölçek tavanı
    List<WeekdayItem> days       // 7 günlük dizi (Pazartesi .. Pazar)
) {
    public record WeekdayItem(
        int dayOfWeek,           // 1 (Pazartesi) .. 7 (Pazar) - ISO DOW
        String dayName,          // "Pazartesi", "Salı", "Çarşamba", "Perşembe", "Cuma", "Cumartesi", "Pazar"
        String shortName,        // "Pzt", "Sal", "Çar", "Per", "Cum", "Cmt", "Paz"
        long radarLobCount,      // Radar Ham LOB sayısı
        long radarKestirimCount, // Radar Kestirim Elipsi sayısı
        long radarTotalCount,    // Radar Toplam (LOB + Kestirim)
        long comintLobCount,     // Muhabere Ham LOB sayısı
        long comintKestirimCount,// Muhabere Kestirim Elipsi sayısı
        long comintTotalCount,   // Muhabere Toplam (LOB + Kestirim)
        long activeCount         // Seçili source ve recordType filtresine karşılık gelen değer
    ) {}
}
