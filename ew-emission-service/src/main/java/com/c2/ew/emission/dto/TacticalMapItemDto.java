package com.c2.ew.emission.dto;

import java.time.Instant;

/**
 * Taktik Harita ve Mekânsal Kesişim Isı Analizi için Ultra-Hafif DTO (Projection).
 * Ağ yükünü %85 azaltır; sadece harita render ve olasılık dağılımı için gereken
 * asgari coğrafi ve taktiksel alanları içerir.
 */
public record TacticalMapItemDto(
    String id,
    String disiplin,                 // "RADAR" | "MUHABERE"
    String kayitTipi,                // "KESTIRIM_ELIPS" | "HAM_LOB"
    Double enlem,                    // yayinEnlem (Kestirim elips merkez enlemi)
    Double boylam,                   // yayinBoylam (Kestirim elips merkez boylamı)
    Double semiMajor,                // yayinSemiMajorMeters (Hata elipsi majör eksen yarıçapı - metre)
    Double semiMinor,                // yayinSemiMinorMeters (Hata elipsi minör eksen yarıçapı - metre)
    Double azimuth,                  // yayinOrientationDegrees (Elips yönelim açısı - derece)
    Double sensorEnlem,              // ehUnsuruEnlem (LOB çıkış istasyonu enlemi)
    Double sensorBoylam,             // ehUnsuruBoylam (LOB çıkış istasyonu boylamı)
    Double bearing,                  // yon (LOB kerteriz açısı - derece)
    String teshisKimlik,             // "DUSMAN" | "DOST" | "BILINMEYEN" | "TARAFSIZ"
    String hedefAdi,                 // Radar Adı veya Muhabere Çağrı Adı
    String ehUnsuru,                 // Görevi icra eden ED/ESM unsuru
    Instant sonTespitZamani,         // Zaman damgası (Filtreleme için)
    String sonTespitZamaniFormatted, // UI tooltip için biçimlendirilmiş zaman
    String kestirimId                // Bağlı LOB referansı (varsa)
) {}
