package com.c2.ew.emission.domain.enums;

/**
 * Elektronik Harp / SIGINT Kayıt Niteliği.
 * KESTIRIM_ELIPS: Birden fazla sensör LOB'unun kesişimiyle hesaplanmış, tam hata elipsine sahip hedef kestirimi.
 * HAM_LOB: Tek sensör tarafından tespit edilmiş kerteriz/yön bildirimi (Line of Bearing). Yayın elipsi (enlem/boylam) boştur (NULL).
 */
public enum KayitTipi {
    KESTIRIM_ELIPS("Kestirim (Hata Elipsi)"),
    HAM_LOB("Ham LOB (Yön Bildirimi / Kerteriz)");

    private final String aciklama;

    KayitTipi(String aciklama) {
        this.aciklama = aciklama;
    }

    public String getAciklama() {
        return aciklama;
    }
}
