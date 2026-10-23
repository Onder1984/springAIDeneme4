package com.c2.ew.emission.infrastructure.streamer;

import com.c2.ew.emission.domain.enums.*;
import com.c2.ew.emission.domain.model.*;
import com.c2.ew.emission.infrastructure.entity.TacticalComintEntity;
import com.c2.ew.emission.infrastructure.entity.TacticalEmissionEntity;
import com.c2.ew.emission.infrastructure.repository.TacticalComintJpaRepository;
import com.c2.ew.emission.infrastructure.repository.TacticalEmissionJpaRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Canlı Taktik Veri Akışı ve Hiyerarşi Simülatörü (Live Tactical Streamer)
 * Sahadan düzenli aralıklarla hem Çocuklu Elipsler (Bağlı LOB'lu), hem Çocuksuz Elipsler (Link-16),
 * hem de bağımsız Başıboş LOB'lar üreterek gerçekçi bir SIGINT/C2 piramidi oluşturur.
 */
@Service
@ConditionalOnProperty(prefix = "tactical.streamer", name = "enabled", havingValue = "true", matchIfMissing = true)
public class TacticalLiveStreamerService {

    private static final Logger log = LoggerFactory.getLogger(TacticalLiveStreamerService.class);

    
    private static final String[] RADAR_CHILDLESS_NOTES = {
        "Link-16 J3.2 Hava Resmi Taktik Veri Bağı İzi (Çocuksuz Kestirim)",
        "İHA/Hava Taktik Gözetleme İntikali (Çocuksuz Kestirim)",
        "Doğrudan TDOA Kestirim Sistemi (Ham LOB Kayıtsız)",
        "Müşterek C2 Harekat Merkezi İntikali (Çocuksuz Kestirim)"
    };

    private static final String[] COMINT_CHILDLESS_NOTES = {
        "AWACS / Hava Platformu COMINT İntikali (Çocuksuz Kestirim)",
        "Link-16 J3.5 Telsiz Muhabere İzi (Çocuksuz Kestirim)",
        "Doğrudan TDOA/FDOA Kestirim Sistemi (Ham LOB Kayıtsız)",
        "Saha Dinleme Karakolu Doğrudan Kestirim İntikali (Çocuksuz Kestirim)"
    };

    private final TacticalEmissionJpaRepository emissionRepo;
    private final TacticalComintJpaRepository comintRepo;
    private final ObjectMapper objectMapper;

    @Value("${tactical.streamer.batch-size:6}")
    private int batchSize;

    private final AtomicLong totalEmissionsStreamed = new AtomicLong(0);
    private final AtomicLong totalComintsStreamed = new AtomicLong(0);
    private final AtomicLong tickCounter = new AtomicLong(0);

    private static final String[][] CALLSIGN_TEMPLATES = {
        {"VOLGA-01", "VOLGA-04", "Rusça", "DUSMAN", "VHF", "DMR_MOBIL", "KRIPTO"},
        {"VOLGA-04", "VOLGA-HQ", "Rusça", "DUSMAN", "HF", "STANAG_5065_MSK", "SES"},
        {"VOLGA-HQ", "VOLGA-01", "Rusça", "DUSMAN", "VHF", "DMR_BAZ", "KRIPTO"},
        {"KARTAL-1", "SHADOW-03", "Türkçe", "DOST", "VHF", "DMR_MOBIL", "KRIPTO"},
        {"KARTAL-1", "HQ-KOMUTA", "Türkçe", "DOST", "HF", "LINK_11_CLEW", "VERI"},
        {"SHADOW-03", "HQ-KOMUTA", "Türkçe", "DOST", "VHF", "DMR_BAZ_DMR_MOBIL", "KRIPTO"},
        {"OMAR-11", "EAGLE-LEAD", "Arapça", "DUSMAN", "VHF", "DMR_MOBIL", "SES"},
        {"OMAR-11", "GRIFFIN-2", "Arapça", "DUSMAN", "VHF", "D_STAR", "DARBE_PATLAMA"},
        {"NATO-TASK-09", "MED-AIR-CONTROL", "İngilizce", "DOST", "VHF", "DMR_MOBIL", "SES"},
        {"NATO-TASK-09", "VOLGA-01", "İngilizce", "MUHTEMEL_DUSMAN", "HF", "STANAG_4529", "VERI"},
        {"HERMES-4", "ATHENS-RELAY", "Yunanca", "DUSMAN", "VHF", "DMR_BAZ", "KRIPTO"},
        {"HERMES-4", "KARTAL-1", "Yunanca", "DUSMAN", "HF", "MIL_188_141A", "VERI"},
        {"TASK-SLEW-01", "AEGEAN-TASK-HQ", "İngilizce", "DUSMAN", "HF", "LINK_11_SLEW", "VERI"},
        {"PACTOR-NET-5", "ISLAND-NODE", "İngilizce", "MUHTEMEL_DUSMAN", "HF", "PACTOR_3", "VERI"},
        {"FUSION-MOBILE-9", "NORTH-RELAY", "Rusça", "DUSMAN", "VHF", "FUSION", "SES"},
        {"JAM-ALPHA-01", "SECTOR-JAM-NET", "Tanımsız", "DUSMAN", "VHF", "TANIMSIZ", "KARISTIRMA_GAYDA"},
        {"GRIFFIN-2", "SHADOW-03", "İngilizce", "MUHTEMEL_DUSMAN", "VHF", "DMR_MOBIL", "SES"}
    };

    private static final String[] RADAR_MODELS = {
        "92N6E Grave Stone (S-400)", "96L6E Çift Taraflı Arama Radarı", "Pantsir-S1 1RS2-1",
        "AN/MPQ-53 (Patriot PAC-3)", "AN/MPQ-64 Sentinel", "Nebo-M VHF/L/S Radar Kompleksi",
        "AN/SPY-1D Aegis Fırkateyn", "KORAL ED/ET Elektronik Harp Sistemi", "Hisar-O+ Arama/Atış Kontrol",
        "Buk-M2 9S36 Angara", "Tor-M2 Uçaksavar Arama Radarı"
    };

    private static final String[] LOCATIONS = {
        "Ege Denizi / Sakız Batısı", "Ege Denizi / Midilli Doğusu", "Doğu Akdeniz / Rodos Açıkları",
        "Doğu Akdeniz / Kıbrıs Kuzeyi", "Marmara / Bandırma Hava Sahası", "Karadeniz / Kırım Güneyi",
        "Suriye Hududu / Tel Rıfat Sektörü", "Güneydoğu / Şırnak Hudut Hattı", "Akdeniz / Girit Açıkları"
    };

    public TacticalLiveStreamerService(
        TacticalEmissionJpaRepository emissionRepo,
        TacticalComintJpaRepository comintRepo,
        ObjectMapper objectMapper
    ) {
        this.emissionRepo = emissionRepo;
        this.comintRepo = comintRepo;
        this.objectMapper = objectMapper;
    }

    @Scheduled(fixedRateString = "${tactical.streamer.interval-ms:5000}")
    public void streamLiveTacticalData() {
        try {
            ThreadLocalRandom rnd = ThreadLocalRandom.current();
            Instant now = Instant.now();

            List<TacticalEmissionEntity> newEmissions = new ArrayList<>();
            List<TacticalComintEntity> newComints = new ArrayList<>();

            // ============================================================
            // 1. RADAR EMİSYONLARI ÜRETİMİ (Çocuklu Elips + Bağlı LOB + Çocuksuz Elips + Başıboş LOB)
            // ============================================================
            // A) Çocuklu Kestirim Elipsi ve ona bağlı 2 çocuk LOB
            TacticalEmissionEntity radarChilded = createRadarEllipse(now, rnd, false);
            newEmissions.add(radarChilded);
            newEmissions.add(createRadarChildLob(now, rnd, radarChilded.getId(), radarChilded.getYayinEnlem(), radarChilded.getYayinBoylam(), "KORAL-1 HAVA SAVUNMA ED", 38.2, 28.5));
            newEmissions.add(createRadarChildLob(now, rnd, radarChilded.getId(), radarChilded.getYayinEnlem(), radarChilded.getYayinBoylam(), "KORAL-2 HAVA SAVUNMA ED", 39.1, 27.8));

            // B) Çocuksuz Kestirim Elipsi (Link-16 / Harici İntikal)
            TacticalEmissionEntity radarChildless = createRadarEllipse(now, rnd, true);
            newEmissions.add(radarChildless);

            // C) Başıboş LOB (Tek istasyon anlık tespit, henüz kestirilemedi)
            newEmissions.add(createRadarChildLob(now, rnd, null, null, null, "REDET-I MOBIL ED UNSURU", 37.5, 30.2));

            // ============================================================
            // 2. COMINT TELSİZ YAYINLARI ÜRETİMİ (Çocuklu Elips + Bağlı LOB + Çocuksuz Elips + Başıboş LOB)
            // ============================================================
            // A) Çocuklu Telsiz Kestirim Elipsi ve ona bağlı 2 çocuk LOB
            TacticalComintEntity comintChilded = createComintEllipse(now, rnd, false);
            newComints.add(comintChilded);
            newComints.add(createComintChildLob(now, rnd, comintChilded.getId(), comintChilded.getYayinEnlem(), comintChilded.getYayinBoylam(), "REDET-II Alfa Dinleme İstasyonu", 38.0, 29.0));
            newComints.add(createComintChildLob(now, rnd, comintChilded.getId(), comintChilded.getYayinEnlem(), comintChilded.getYayinBoylam(), "REDET-II Bravo Dinleme İstasyonu", 38.8, 30.5));

            // B) Çocuksuz Telsiz Kestirim Elipsi (Link-16 / AWACS İntikali)
            TacticalComintEntity comintChildless = createComintEllipse(now, rnd, true);
            newComints.add(comintChildless);

            // C) Başıboş Telsiz LOB'u (Tek istasyon DF kerterizi)
            newComints.add(createComintChildLob(now, rnd, null, null, null, "1. Taktik DF Dinleme Postası", 37.8, 32.1));

            emissionRepo.saveAll(newEmissions);
            comintRepo.saveAll(newComints);

            long totalE = totalEmissionsStreamed.addAndGet(newEmissions.size());
            long totalC = totalComintsStreamed.addAndGet(newComints.size());
            long ticks = tickCounter.incrementAndGet();

            if (ticks % 12 == 0) {
                log.info("📡 [CANLI AKIŞ & HİYERARŞİ] Simülatör çalışıyor: Son 1 dakikada +{} Radar, +{} COMINT (Çocuklu/Çocuksuz Elips ve Bağlı/Başıboş LOB) eklendi. (Toplam Canlı: Radar={}, COMINT={}, Zaman: {})",
                    newEmissions.size() * 12, newComints.size() * 12, totalE, totalC, now);
            }

        } catch (Exception e) {
            log.error("Canlı taktik veri akışı sırasında hata:", e);
        }
    }

    private TacticalEmissionEntity createRadarEllipse(Instant now, ThreadLocalRandom rnd, boolean isLink16) {
        TacticalEmissionEntity e = new TacticalEmissionEntity();
        String uid = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ENGLISH);
        e.setId("EMI-LIVE-" + uid);
        e.setVeriKaynagi(isLink16 ? VeriKaynagi.GEN_KUR : VeriKaynagi.values()[rnd.nextInt(VeriKaynagi.values().length)]);
        e.setKuvvetSiraNo("KUV-R-LIV-" + uid);
        e.setUstBirlikRef(isLink16 ? "Müşterek Hava Harekât Merkezi (CAOC)" : "1. Taktik Elektronik Destek Taburu");
        e.setEhUnsuru(isLink16 ? "LINK-16 Taktik Veri Terminali" : "KORAL-1 HAVA SAVUNMA ED");

        double ehLat = 37.0 + rnd.nextDouble() * 4.0;
        double ehLon = 27.0 + rnd.nextDouble() * 8.0;
        e.setEhUnsuruEnlem(ehLat);
        e.setEhUnsuruBoylam(ehLon);
        e.setEhUnsuruIrtifa(100.0 + rnd.nextInt(3000));
        e.setYon(rnd.nextDouble() * 360.0);

        String model = RADAR_MODELS[rnd.nextInt(RADAR_MODELS.length)];
        e.setRadarAdi(model);
        e.setRadarGorevi(model.contains("Pantsir") || model.contains("92N6E") ? "Atış Kontrol" : (model.contains("Nebo") ? "Erken İhbar" : "Takip/Arama"));
        e.setHedefYerBilgisi(LOCATIONS[rnd.nextInt(LOCATIONS.length)]);
        e.setHedefKaynagi(isLink16 ? HedefKaynagi.ED_TOPLAMA : HedefKaynagi.ED_TOPLAMA);
        e.setGenlikDbm(-75.0 + rnd.nextDouble() * 35.0);
        e.setModulasyon(Modulasyon.values()[rnd.nextInt(Modulasyon.values().length)]);
        e.setPulseCw(rnd.nextBoolean() ? PulseCw.PULSE : PulseCw.CW);
        e.setSpotNo(String.format("SP%03d", rnd.nextInt(90) + 10));

        TeshisKimlik teshis = model.contains("S-400") || model.contains("Pantsir") || model.contains("Buk") || model.contains("Tor")
            ? TeshisKimlik.DUSMAN
            : (model.contains("KORAL") || model.contains("Hisar") ? TeshisKimlik.DOST : TeshisKimlik.MUHTEMEL_DUSMAN);
        e.setTeshisKimlik(teshis);
        e.setPlatformOrtami(PlatformOrtami.values()[rnd.nextInt(PlatformOrtami.values().length)]);
        e.setPlatformTipi(PlatformTipi.values()[rnd.nextInt(PlatformTipi.values().length)]);
        e.setEtUygulamaDurumu(rnd.nextInt(10) > 7 ? EtUygulamaDurumu.UYGULANIYOR : EtUygulamaDurumu.UYGULANMIYOR);
        e.setTaciz(teshis == TeshisKimlik.DUSMAN && rnd.nextBoolean());

        e.setSonTespitZamani(now);
        e.setIlkTespitZamani(now.minusSeconds(rnd.nextInt(180) + 10));
        e.setSureSn(60.0 + rnd.nextInt(600));

        e.setKayitTipi(KayitTipi.KESTIRIM_ELIPS);
        e.setYayinEnlem(ehLat + (rnd.nextDouble() - 0.5) * 0.8);
        e.setYayinBoylam(ehLon + (rnd.nextDouble() - 0.5) * 0.8);
        e.setYayinSemiMajorMeters(1500.0 + rnd.nextDouble() * 3000.0);
        e.setYayinSemiMinorMeters(800.0 + rnd.nextDouble() * 1200.0);
        e.setYayinOrientationDegrees(rnd.nextDouble() * 180.0);

        double baseFreq = model.contains("92N6E") || model.contains("Pantsir") ? 9200.0 : (model.contains("Nebo") ? 180.0 : 4500.0);
        e.setMinFrekansMhz(baseFreq);
        e.setMaxFrekansMhz(baseFreq + rnd.nextInt(80) + 20);
        e.setMinPriMicroSec(600.0 + rnd.nextInt(500));
        e.setMaxPriMicroSec(e.getMinPriMicroSec() + 150.0);
        e.setMinPwMicroSec(0.8 + rnd.nextDouble() * 2.0);
        e.setMaxPwMicroSec(e.getMinPwMicroSec() + 1.0);

        try {
            e.setFrekansListesiJson(objectMapper.writeValueAsString(List.of(new FrequencyEntry("SABIT", e.getMinFrekansMhz(), e.getMaxFrekansMhz()))));
            e.setPriListesiJson(objectMapper.writeValueAsString(List.of(new PriEntry("SABIT", e.getMinPriMicroSec(), e.getMaxPriMicroSec()))));
            e.setPwListesiJson(objectMapper.writeValueAsString(List.of(new PwEntry("SABIT", e.getMinPwMicroSec(), e.getMaxPwMicroSec()))));
        } catch (Exception ignored) {}

        if (isLink16) {
            e.setOperatorNotu(RADAR_CHILDLESS_NOTES[rnd.nextInt(RADAR_CHILDLESS_NOTES.length)]);
            e.setOnaylayanKullanici("CAOC Müşterek Hava Harekât Merkezi");
            e.setOnayNotu("Dış kaynaktan taktik veri bağı entegrasyonu onaylandı.");
        } else {
            e.setOperatorNotu("Çoklu algılayıcı kestirimiyle doğrulanmış taktik hedef elipsi (Çocuklu)");
            e.setOnaylayanKullanici("Füzyon & Kestirim Motoru");
            e.setOnayNotu("LOB kesişim kestirimi onaylandı.");
        }

        return e;
    }

    private TacticalEmissionEntity createRadarChildLob(Instant now, ThreadLocalRandom rnd, String parentKestirimId, Double targetLat, Double targetLon, String sensorName, Double sLat, Double sLon) {
        TacticalEmissionEntity lob = new TacticalEmissionEntity();
        String uid = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ENGLISH);
        lob.setId("EMI-LIVE-" + uid);
        lob.setVeriKaynagi(VeriKaynagi.KARA);
        lob.setKuvvetSiraNo("KUV-R-LOB-" + uid);
        lob.setUstBirlikRef("1. Taktik Elektronik Destek Taburu");
        lob.setEhUnsuru(sensorName);

        lob.setEhUnsuruEnlem(sLat);
        lob.setEhUnsuruBoylam(sLon);
        lob.setEhUnsuruIrtifa(150.0 + rnd.nextInt(500));

        // Hedefe doğru kerteriz açısı hesapla veya rastgele
        double angle;
        if (targetLat != null && targetLon != null) {
            double dLon = targetLon - sLon;
            double y = Math.sin(Math.toRadians(dLon)) * Math.cos(Math.toRadians(targetLat));
            double x = Math.cos(Math.toRadians(sLat)) * Math.sin(Math.toRadians(targetLat)) -
                       Math.sin(Math.toRadians(sLat)) * Math.cos(Math.toRadians(targetLat)) * Math.cos(Math.toRadians(dLon));
            angle = (Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0;
        } else {
            angle = rnd.nextDouble() * 360.0;
        }
        lob.setYon(angle);

        lob.setKayitTipi(KayitTipi.HAM_LOB);
        lob.setKestirimId(parentKestirimId);
        // HAM_LOB için hedef koordinatları ve hata elipsi kesinlikle null!
        lob.setYayinEnlem(null);
        lob.setYayinBoylam(null);
        lob.setYayinSemiMajorMeters(null);
        lob.setYayinSemiMinorMeters(null);
        lob.setYayinOrientationDegrees(null);

        lob.setRadarAdi(parentKestirimId != null ? "92N6E (Kestirim Sinyali)" : "Tanımsız X-Band Emisyonu");
        lob.setRadarGorevi("Erken İhbar / Takip");
        lob.setHedefYerBilgisi("Kerteriz Yönü: " + Math.round(angle) + "°");
        lob.setHedefKaynagi(HedefKaynagi.ED_TOPLAMA);
        lob.setGenlikDbm(-82.0 + rnd.nextDouble() * 30.0);
        lob.setTeshisKimlik(parentKestirimId != null ? TeshisKimlik.DUSMAN : TeshisKimlik.BILINMEYEN);
        lob.setSonTespitZamani(now);
        lob.setIlkTespitZamani(now.minusSeconds(15));
        lob.setSureSn(15.0);

        lob.setMinFrekansMhz(9200.0);
        lob.setMaxFrekansMhz(9280.0);
        lob.setMinPriMicroSec(750.0);
        lob.setMaxPriMicroSec(850.0);

        if (parentKestirimId != null) {
            lob.setOperatorNotu("Kestirime katkı sunan tekil ED kerteriz hattı (Bağlı LOB - Ref: " + parentKestirimId + ")");
            lob.setOnayNotu("Kestirim korelasyonu doğrulandı.");
        } else {
            lob.setOperatorNotu("Tek istasyon anlık tespit, henüz başka ED unsuru duymadı (Başıboş LOB)");
            lob.setOnayNotu("Tekil kerteriz kuyruğunda bekliyor.");
        }
        lob.setOnaylayanKullanici("Otomatik Sinyal İzleyici");

        return lob;
    }

    private TacticalComintEntity createComintEllipse(Instant now, ThreadLocalRandom rnd, boolean isLink16) {
        TacticalComintEntity c = new TacticalComintEntity();
        String uid = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ENGLISH);
        c.setId("COM-LIVE-" + uid);
        c.setVeriKaynagi(isLink16 ? VeriKaynagi.HAVA : VeriKaynagi.values()[rnd.nextInt(VeriKaynagi.values().length)]);
        c.setKuvvetSiraNo("KUV-C-LIV-" + uid);
        c.setGoreviIcraEdenUstBirlik(isLink16 ? "Hava Kuvvetleri Komuta Merkezi" : "1. Taktik Muhabere Tabur Komutanlığı");
        c.setGoreviIcraEdenEhUnsuru(isLink16 ? "AWACS HİK Muhabere Entegratörü" : "REDET-II Alfa Dinleme İstasyonu");

        double ehLat = 37.0 + rnd.nextDouble() * 4.0;
        double ehLon = 27.0 + rnd.nextDouble() * 8.0;
        c.setEhUnsuruEnlem(ehLat);
        c.setEhUnsuruBoylam(ehLon);
        c.setEhUnsuruIrtifa(50.0 + rnd.nextInt(2000));
        c.setYon(rnd.nextDouble() * 360.0);

        String[] template = CALLSIGN_TEMPLATES[rnd.nextInt(CALLSIGN_TEMPLATES.length)];
        c.setCagriAdi(template[0]);
        c.setKarsiCagriAdi(template[1]);
        c.setLisan(template[2]);
        c.setTeshisKimlik(TeshisKimlik.valueOf(template[3]));
        c.setTip(MuhabereBantTipi.valueOf(template[4]));

        try {
            c.setProtokol(MuhabereProtokol.valueOf(template[5]));
        } catch (Exception ignored) {
            c.setProtokol(MuhabereProtokol.TANIMSIZ);
        }

        try {
            c.setHaberlesmeSekli(HaberlesmeSekli.valueOf(template[6]));
        } catch (Exception ignored) {
            c.setHaberlesmeSekli(HaberlesmeSekli.SES);
        }

        c.setModulasyon(MuhabereModulasyon.FM);
        c.setHedefYerBilgisi(LOCATIONS[rnd.nextInt(LOCATIONS.length)]);
        c.setHedefKaynagi(isLink16 ? HedefKaynagi.ED_TOPLAMA : HedefKaynagi.ED_TOPLAMA);
        c.setGenlikDbm(-80.0 + rnd.nextDouble() * 40.0);
        c.setMti(rnd.nextBoolean());

        double baseFreq = c.getTip() == MuhabereBantTipi.HF ? (5.0 + rnd.nextDouble() * 20.0) : (140.0 + rnd.nextDouble() * 150.0);
        c.setMinFrekansMhz(baseFreq);
        c.setMaxFrekansMhz(baseFreq + 0.025);
        c.setBantGenisligiHz(12500.0);

        c.setSonTespitZamani(now);
        c.setIlkTespitZamani(now.minusSeconds(rnd.nextInt(120) + 10));
        c.setSureSn(30.0 + rnd.nextInt(300));

        c.setKayitTipi(KayitTipi.KESTIRIM_ELIPS);
        c.setYayinEnlem(ehLat + (rnd.nextDouble() - 0.5) * 0.7);
        c.setYayinBoylam(ehLon + (rnd.nextDouble() - 0.5) * 0.7);
        c.setYayinSemiMajorMeters(1200.0 + rnd.nextDouble() * 2000.0);
        c.setYayinSemiMinorMeters(600.0 + rnd.nextDouble() * 900.0);
        c.setYayinOrientationDegrees(rnd.nextDouble() * 180.0);

        if (isLink16) {
            c.setOperatorNotu(COMINT_CHILDLESS_NOTES[rnd.nextInt(COMINT_CHILDLESS_NOTES.length)]);
            c.setOnaylayanKullanici("Müşterek Muhabere Karargahı");
            c.setOnayNotu("Harici telsiz kestirimi onaylandı.");
        } else {
            c.setOperatorNotu("Çoklu DF istasyonu kesişimiyle kestirilen telsiz yayın elipsi (Çocuklu)");
            c.setOnaylayanKullanici("Otomatik CESM Füzyon Motoru");
            c.setOnayNotu("Telsiz LOB füzyonu onaylandı.");
        }

        return c;
    }

    private TacticalComintEntity createComintChildLob(Instant now, ThreadLocalRandom rnd, String parentKestirimId, Double targetLat, Double targetLon, String sensorName, Double sLat, Double sLon) {
        TacticalComintEntity lob = new TacticalComintEntity();
        String uid = UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ENGLISH);
        lob.setId("COM-LIVE-" + uid);
        lob.setVeriKaynagi(VeriKaynagi.KARA);
        lob.setKuvvetSiraNo("KUV-C-LOB-" + uid);
        lob.setGoreviIcraEdenUstBirlik("1. Taktik Muhabere Tabur Komutanlığı");
        lob.setGoreviIcraEdenEhUnsuru(sensorName);

        lob.setEhUnsuruEnlem(sLat);
        lob.setEhUnsuruBoylam(sLon);
        lob.setEhUnsuruIrtifa(80.0 + rnd.nextInt(300));

        double angle;
        if (targetLat != null && targetLon != null) {
            double dLon = targetLon - sLon;
            double y = Math.sin(Math.toRadians(dLon)) * Math.cos(Math.toRadians(targetLat));
            double x = Math.cos(Math.toRadians(sLat)) * Math.sin(Math.toRadians(targetLat)) -
                       Math.sin(Math.toRadians(sLat)) * Math.cos(Math.toRadians(targetLat)) * Math.cos(Math.toRadians(dLon));
            angle = (Math.toDegrees(Math.atan2(y, x)) + 360.0) % 360.0;
        } else {
            angle = rnd.nextDouble() * 360.0;
        }
        lob.setYon(angle);

        lob.setKayitTipi(KayitTipi.HAM_LOB);
        lob.setKestirimId(parentKestirimId);
        lob.setYayinEnlem(null);
        lob.setYayinBoylam(null);
        lob.setYayinSemiMajorMeters(null);
        lob.setYayinSemiMinorMeters(null);
        lob.setYayinOrientationDegrees(null);

        lob.setCagriAdi(parentKestirimId != null ? "VOLGA-01" : "OMAR-11");
        lob.setKarsiCagriAdi(parentKestirimId != null ? "VOLGA-04" : "EAGLE-LEAD");
        lob.setLisan(parentKestirimId != null ? "Rusça" : "Arapça");
        lob.setTeshisKimlik(TeshisKimlik.DUSMAN);
        lob.setTip(MuhabereBantTipi.VHF);
        lob.setModulasyon(MuhabereModulasyon.FM);
        lob.setHaberlesmeSekli(HaberlesmeSekli.SES);
        lob.setMinFrekansMhz(156.8);
        lob.setMaxFrekansMhz(156.825);
        lob.setBantGenisligiHz(12500.0);
        lob.setGenlikDbm(-85.0 + rnd.nextDouble() * 25.0);

        lob.setSonTespitZamani(now);
        lob.setIlkTespitZamani(now.minusSeconds(10));
        lob.setSureSn(10.0);

        if (parentKestirimId != null) {
            lob.setOperatorNotu("Telsiz kestirimine katkı sunan DF kerteriz çizgisi (Bağlı LOB - Ref: " + parentKestirimId + ")");
            lob.setOnayNotu("DF kerteriz korelasyonu tamamlandı.");
        } else {
            lob.setOperatorNotu("Tek istasyon anlık telsiz kerterizi, ikinci bir istasyon duymadı (Başıboş LOB)");
            lob.setOnayNotu("Telsiz LOB kuyruğunda bekliyor.");
        }
        lob.setOnaylayanKullanici("Otomatik CESM Dinleme Postası");

        return lob;
    }
}
