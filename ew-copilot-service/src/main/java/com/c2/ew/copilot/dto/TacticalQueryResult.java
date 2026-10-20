package com.c2.ew.copilot.dto;

import java.util.List;

/**
 * Taktik sorgulama sonucu: Toplam eşleşen kayıt sayısı, listelenen ilk kayıtlar (preview)
 * ve kullanıcının tüm listeyi Ant Design tablosunda inceleyebilmesi için yönlendirme bilgisi.
 */
public record TacticalQueryResult<T>(
    List<T> kayitlar,
    long toplamKayitSayisi,
    int listelenenKayitSayisi,
    boolean dahasiVarMi,
    String onerilenSekme,
    String yonlendirmeNotu
) {
    public static <T> TacticalQueryResult<T> of(List<T> kayitlar, long toplamKayitSayisi, String sekmeAdi) {
        int listelenen = kayitlar != null ? kayitlar.size() : 0;
        boolean dahasiVar = toplamKayitSayisi > listelenen;
        String not = dahasiVar
            ? String.format("Toplam %d kayıt bulundu. İlk %d tanesi özetlenmiştir. Tüm listeyi ve detaylı filtrelemeyi '%s' sekmesinden inceleyebilirsiniz.", toplamKayitSayisi, listelenen, sekmeAdi)
            : String.format("Toplam %d kayıt bulundu ve listelendi.", toplamKayitSayisi);
        return new TacticalQueryResult<>(kayitlar, toplamKayitSayisi, listelenen, dahasiVar, sekmeAdi, not);
    }
}
