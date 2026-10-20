package com.c2.ew.emission.dto;

import java.util.List;
import java.util.Map;

/**
 * Taktik Kestirim ve LOB Hiyerarşi Analizi Sonuç DTO'su
 * Hem Kestirim Elipslerini (Çocuklu vs Çocuksuz) hem de LOB'ları (Dahil Olmuş vs Başıboş)
 * zaman kısıtlı (Son 1 saat, Son 24 saat vb.) olarak sunar.
 */
public record HierarchyAnalysisResultDto(
    String domain,
    String zamanAraligi,
    String kategori,
    String durumFiltresi,
    long toplamKestirimSayisi,
    long cocukluKestirimSayisi,
    long cocuksuzKestirimSayisi,
    long toplamHamLobSayisi,
    long bagliLobSayisi,
    long basibosLobSayisi,
    double ortalamaCocukLobSayisi,
    double basibosLobOraniYuzde,
    List<Map<String, Object>> detayListesi,
    String taktikDegerlendirme
) {}
