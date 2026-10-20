package com.c2.ew.copilot.dto;

/**
 * Taktik Telsiz Çağrı Adı İletişim & Matris İstatistiği DTO'su.
 * İki istasyon arasındaki tüm telsiz temaslarının tam dökümünü içerir:
 * - Toplam konuşma adedi ve toplam süre
 * - Tam VHF / HF frekans bandı dağılımı
 * - Tam KRİPTO / SES / VERİ haberleşme şekli adetleri
 * - Baskın istihbarat lisanı
 */
public record CallsignPairStatsDto(
    String cagriAdi,
    String karsiCagriAdi,
    long konusmaAdedi,
    double toplamSureSn,
    long vhfAdet,
    long hfAdet,
    long kriptoAdet,
    long sesAdet,
    long veriAdet,
    String baskinLisan
) {}
