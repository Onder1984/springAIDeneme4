package com.c2.ew.copilot.dto;

import java.util.List;

/**
 * Taktik Kategorik Dağılım Analiz DTO'su (Pie / Donut Chart için)
 */
public record TacticalDistributionDto(
    String source,           // "RADAR" | "COMINT"
    String criterion,        // "POLARIZASYON", "MODULASYON", "TESHIS", "HABERLESME_SEKLI", "LISAN", "HEDEF_YER", vb.
    String criterionLabel,   // "Polarizasyon Dağılımı", "Lisan Dağılımı", vb.
    String timeRange,        // "SON_1_SAAT", "SON_24_SAAT", "ALL"
    long totalCount,         // Toplam kayıt adedi
    List<Item> items         // Pasta dilimleri
) {
    public record Item(
        String name,         // Kategori / Dilim Adı (örn: "DIKEY", "SES", "Rusça")
        long value,          // Kayıt adedi
        double percentage    // Yüzde payı (0 - 100)
    ) {}
}
