package com.c2.ew.emission.infrastructure.seeder;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;

@Service
public class MassDataSeederService {

    private static final Logger log = LoggerFactory.getLogger(MassDataSeederService.class);

    private final JdbcTemplate jdbcTemplate;

    public MassDataSeederService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Map<String, Object> seedMassiveData(int targetRadarCount, int targetComintCount) {
        long startTotal = System.currentTimeMillis();
        log.info("🚀 [MASS SEEDER] Başlatılıyor: {} Radar Emisyonu + {} COMINT Telsiz Kaydı", targetRadarCount, targetComintCount);

        long startRadar = System.currentTimeMillis();
        int insertedRadars = seedRadarsInBatches(targetRadarCount, 5000);
        long durationRadar = System.currentTimeMillis() - startRadar;

        long startComint = System.currentTimeMillis();
        int insertedComints = seedComintsInBatches(targetComintCount, 5000);
        long durationComint = System.currentTimeMillis() - startComint;

        long totalDuration = System.currentTimeMillis() - startTotal;
        log.info("✅ [MASS SEEDER TAMAMLANDI] Toplam: {} Radar ({:.2f} sn), {} COMINT ({:.2f} sn) -> Genel Süre: {:.2f} sn",
            insertedRadars, durationRadar / 1000.0, insertedComints, durationComint / 1000.0, totalDuration / 1000.0);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("status", "SUCCESS");
        result.put("insertedRadarCount", insertedRadars);
        result.put("radarDurationSec", durationRadar / 1000.0);
        result.put("insertedComintCount", insertedComints);
        result.put("comintDurationSec", durationComint / 1000.0);
        result.put("totalDurationSec", totalDuration / 1000.0);
        return result;
    }

    private int seedRadarsInBatches(int total, int batchSize) {
        String sql = """
            INSERT INTO tactical_emissions (
                emission_id, veri_kaynagi, kuvvet_sira_no, ust_birlik_ref, eh_unsuru, yon,
                eh_unsuru_enlem, eh_unsuru_boylam, eh_unsuru_irtifa, yayin_enlem, yayin_boylam,
                yayin_semi_major_meters, yayin_semi_minor_meters, yayin_orientation_degrees,
                hedef_yer_bilgisi, hedef_mevzi_bilgisi, teshis_kimlik, elint_notasyonu,
                hedef_kaynagi, polarizasyon, spot_no, pulse_cw, radar_gorevi, radar_adi,
                platform_ortami, platform_tipi, genlik_dbm, modulasyon, et_uygulama_durumu,
                ilk_tespit_zamani, son_tespit_zamani, sure_sn, hss, taciz, radar_kesit_alani,
                radar_iz_numarasi, ucak_kuyruk_numarasi, min_frekans_mhz, max_frekans_mhz,
                min_pri_micro_sec, max_pri_micro_sec, min_pw_micro_sec, max_pw_micro_sec,
                min_atp_micro_sec, max_atp_micro_sec, frekans_listesi_json, pri_listesi_json,
                pw_listesi_json, atp_listesi_json, operator_notu, onaylayan_kullanici, onay_notu
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        String[] radarModels = {
            "Pantsir-S1 1RS2-1E", "S-400 92N6E Grave Stone", "Patriot AN/MPQ-53",
            "Nebo-M VHF/L", "KORAL-EW Jammer", "AN/APG-77 AESA", "AN/APG-81 AESA",
            "Sea Sparrow Mk-95", "Aegis SPY-1D", "Buk-M2 9S36", "Tor-M2 9A331",
            "S-300PMU2 Flap Lid", "Barış Kartalı MESA", "Aselsan EIRS", "Kalkan Hava Savunma Radarı"
        };

        String[] locations = {
            "Ege Denizi / Midilli Doğusu", "Ege Denizi / Sakız Batısı", "Ege Denizi / Rodos Açıkları",
            "Doğu Akdeniz / Kıbrıs Kuzeyi", "Akdeniz / Girit Açıkları", "Kıbrıs / Baf Açıkları",
            "Karadeniz / Kırım Güneyi", "Karadeniz / Sivastopol Önleri",
            "Güneydoğu / Şırnak Hudut Hattı", "Suriye Hududu / Tel Rıfat Sektörü",
            "Marmara / Bandırma Hava Sahası", "İç Anadolu / Konya Atış Sahası"
        };

        String[] sensorUnits = {"KORAL-1 EH", "KORAL-2 EH", "HAVA-SOJ-01", "İHA-DF-Pod-1", "BARBAROS-F244-ESM", "TCG-BURAK-RADNET", "DF-BATARYA-03"};
        String[] vkList = {"KARA", "HAVA", "DENIZ"};
        String[] teshisList = {"DUSMAN", "DUSMAN", "MUHTEMEL_DUSMAN", "DOST", "TARAFSIZ"};
        String[] ortamList = {"HAVA", "KARA", "SU_USTU"};
        String[] platformTypeList = {"KARA", "HAVA", "SU_USTU", "BILINMEYEN"};
        String[] etDurumlari = {"UYGULANMIYOR", "UYGULANIYOR", "SUSTURDU"};
        String[] hedefKaynaklari = {"TEROR", "TATBIKAT", "EGITIM", "ED_TOPLAMA"};
        String[] polarizasyonlar = {"YATAY", "DIKEY", "DAIRESEL"};
        String[] modulasyonlar = {"LINEAR_FM", "MULTI_FREQUENCY", "NON_LINEAR_MODULATION", "YOK_NO_MOP"};

        Random rnd = new Random(777);
        int inserted = 0;
        Instant now = Instant.now();

        while (inserted < total) {
            int currentBatchSize = Math.min(batchSize, total - inserted);
            List<Object[]> batchArgs = new ArrayList<>(currentBatchSize);

            for (int i = 0; i < currentBatchSize; i++) {
                String id = "EMI-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
                int idNum = inserted + i + 1;
                String vk = vkList[rnd.nextInt(vkList.length)];
                String kuvNo = String.format("KUV-%07d", idNum);
                String ustBirlik = "2. Taktik EH Tabur Komutanlığı";
                String sensor = sensorUnits[rnd.nextInt(sensorUnits.length)];
                double bearing = rnd.nextDouble() * 360.0;

                double ehLat = 36.0 + rnd.nextDouble() * 5.5;
                double ehLon = 26.0 + rnd.nextDouble() * 15.0;
                double targetLat = ehLat + (rnd.nextDouble() - 0.5) * 1.5;
                double targetLon = ehLon + (rnd.nextDouble() - 0.5) * 1.5;

                double major = 1500.0 + rnd.nextInt(8000);
                double minor = 600.0 + rnd.nextInt(3000);
                double orient = rnd.nextInt(180);

                String loc = locations[rnd.nextInt(locations.length)];
                String mevzi = "Mevzi-" + (rnd.nextInt(25) + 1);

                String teshis = teshisList[rnd.nextInt(teshisList.length)];
                String elnot = String.format("ELN-%02X%02X", rnd.nextInt(255), rnd.nextInt(255));
                String hedefKaynak = hedefKaynaklari[rnd.nextInt(hedefKaynaklari.length)];
                String pol = polarizasyonlar[rnd.nextInt(polarizasyonlar.length)];
                String spotNo = String.format("SP%03d", idNum % 100);
                String pulseCw = rnd.nextDouble() < 0.85 ? "PULSE" : "CW";

                String model = radarModels[rnd.nextInt(radarModels.length)];
                String gorev;
                if (model.contains("Pantsir") || model.contains("92N6E") || model.contains("MPQ-53") || model.contains("Flap Lid")) {
                    gorev = "Atış Kontrol";
                } else if (model.contains("Nebo-M") || model.contains("EIRS")) {
                    gorev = "Erken İhbar";
                } else {
                    gorev = "Hedef Takip";
                }

                String ortam = ortamList[rnd.nextInt(ortamList.length)];
                String platformType = platformTypeList[rnd.nextInt(platformTypeList.length)];
                double amp = -90.0 + rnd.nextDouble() * 60.0;
                String mod = modulasyonlar[rnd.nextInt(modulasyonlar.length)];
                String etDurumu = etDurumlari[rnd.nextInt(etDurumlari.length)];

                Instant sonTespit = now.minus(rnd.nextInt(168), ChronoUnit.HOURS);
                Instant ilkTespit = sonTespit.minus(rnd.nextInt(1800) + 120, ChronoUnit.SECONDS);
                double sureSn = 60.0 + rnd.nextInt(1800);

                String hss = model.contains("S-400") ? "S-400 Triumf" : (model.contains("Patriot") ? "Patriot PAC-3" : (model.contains("S-300") ? "S-300PMU2" : "Stand-alone"));
                boolean taciz = (teshis.equals("DUSMAN") || teshis.equals("MUHTEMEL_DUSMAN")) && (rnd.nextDouble() < 0.18);
                double rcs = 1.0 + rnd.nextDouble() * 10.0;
                String trackNo = String.format("TRK-%07d", idNum);
                String tailNo = ortam.equals("HAVA") ? String.format("TU-%03d", rnd.nextInt(900) + 100) : null;

                double baseFreq;
                if (model.contains("Nebo-M")) baseFreq = 150.0 + rnd.nextInt(250);
                else if (model.contains("Pantsir") || model.contains("92N6E") || model.contains("MPQ-53") || model.contains("Flap Lid")) baseFreq = 8500.0 + rnd.nextInt(2500);
                else if (model.contains("SPY-1D")) baseFreq = 3100.0 + rnd.nextInt(400);
                else baseFreq = 5000.0 + rnd.nextInt(4000);

                double minFreq = baseFreq;
                double maxFreq = baseFreq + (rnd.nextInt(120) + 10);

                double basePri;
                if (gorev.equals("Atış Kontrol") && taciz) {
                    basePri = 80.0 + rnd.nextInt(150);
                } else if (gorev.equals("Hedef Takip")) {
                    basePri = 200.0 + rnd.nextInt(400);
                } else {
                    basePri = 800.0 + rnd.nextInt(1600);
                }
                double minPri = basePri;
                double maxPri = basePri + (rnd.nextInt(100) + 10);

                double basePw;
                if (gorev.equals("Atış Kontrol") && taciz) {
                    basePw = 0.2 + rnd.nextDouble() * 0.9;
                } else if (gorev.equals("Hedef Takip")) {
                    basePw = 0.8 + rnd.nextDouble() * 1.5;
                } else {
                    basePw = 3.0 + rnd.nextDouble() * 20.0;
                }
                double minPw = basePw;
                double maxPw = basePw + rnd.nextDouble() * 0.5;

                double minAtp = 2000000.0 + rnd.nextInt(4000000);
                double maxAtp = minAtp + 500000.0;

                String jsonStub = "[]";
                String opNotu = taciz ? "Kritik kilitlenme tespit edildi." : "Rutin taktik izleme.";
                String onayKullanici = "Sistem Otomatik Entegrasyonu";
                String onayNotu = "Otomatik Taktik Kayıt Doğrulandı.";

                batchArgs.add(new Object[]{
                    id, vk, kuvNo, ustBirlik, sensor, bearing,
                    ehLat, ehLon, 150.0, targetLat, targetLon,
                    major, minor, orient,
                    loc, mevzi, teshis, elnot,
                    hedefKaynak, pol, spotNo, pulseCw, gorev, model,
                    ortam, platformType, amp, mod, etDurumu,
                    Timestamp.from(ilkTespit), Timestamp.from(sonTespit), sureSn, hss, taciz, rcs,
                    trackNo, tailNo, minFreq, maxFreq,
                    minPri, maxPri, minPw, maxPw,
                    minAtp, maxAtp, jsonStub, jsonStub,
                    jsonStub, jsonStub, opNotu, onayKullanici, onayNotu
                });
            }

            jdbcTemplate.batchUpdate(sql, batchArgs);
            inserted += currentBatchSize;

            if (inserted % 25000 == 0 || inserted == total) {
                log.info("📊 [RADAR SEEDER PROGRESS] {} / {} kayıt tamamlandı ({:.1f}%)", inserted, total, (inserted * 100.0) / total);
            }
        }
        return inserted;
    }

    private int seedComintsInBatches(int total, int batchSize) {
        String sql = """
            INSERT INTO tactical_comint (
                comint_id, veri_kaynagi, kuvvet_sira_no, ust_birlik_ref, eh_unsuru, yon,
                eh_unsuru_enlem, eh_unsuru_boylam, eh_unsuru_irtifa, yayin_enlem, yayin_boylam,
                yayin_semi_major_meters, yayin_semi_minor_meters, yayin_orientation_degrees,
                hedef_yer_bilgisi, hedef_kaynagi, veri_girisi_yapan_birlik, lisan, protokol,
                bant_genisligi_hz, modulasyon, mti, tip, genlik_dbm, haberlesme_sekli, calisma_sekli,
                alt_esik_seviyesi_dbm, cagri_adi, karsi_cagri_adi, ilk_tespit_zamani, son_tespit_zamani,
                sure_sn, teshis_kimlik, min_frekans_mhz, max_frekans_mhz, frekans_listesi_json,
                operator_notu, onaylayan_kullanici, onay_notu
            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
        """;

        String[] locations = {
            "Ege Denizi / Midilli Doğusu", "Ege Denizi / Sakız Batısı", "Ege Denizi / Rodos Açıkları",
            "Doğu Akdeniz / Kıbrıs Kuzeyi", "Akdeniz / Girit Açıkları", "Kıbrıs / Baf Açıkları",
            "Karadeniz / Kırım Güneyi", "Karadeniz / Sivastopol Önleri",
            "Güneydoğu / Şırnak Hudut Hattı", "Suriye Hududu / Tel Rıfat Sektörü",
            "Marmara / Bandırma Hava Sahası", "İç Anadolu / Konya Atış Sahası"
        };

        String[] sensorUnits = {"KORAL-1 EH", "KORAL-2 EH", "HAVA-SOJ-01", "İHA-DF-Pod-1", "DF-KARA-BATARYA-03"};
        String[] vkList = {"KARA", "HAVA", "DENIZ"};
        String[] teshisList = {"DUSMAN", "DUSMAN", "MUHTEMEL_DUSMAN", "DOST", "TARAFSIZ"};
        String[] hedefKaynaklari = {"TEROR", "TATBIKAT", "EGITIM", "ED_TOPLAMA"};
        String[] lisanList = {"Türkçe", "Rusça", "İngilizce", "Arapça", "Yunanca"};
        String[] protokolList = {"DMR_MOBIL", "DMR_BAZ", "BELIRSIZ", "TANIMSIZ"};
        String[] modList = {"AM", "FM", "LSB", "USB", "FSK8", "BELIRSIZ"};
        String[] tipList = {"VHF", "HF"};
        String[] haberlesmeSekilleri = {"KRIPTO", "SES", "VERI", "DARBE_PATLAMA"};
        String[] calismaSekilleri = {"HALF_DUPLEX", "DUPLEX", "ROLE"};
        String[] callsigns = {"VOLGA-01", "VOLGA-04", "VOLGA-HQ", "NATO-TASK-09", "HERMES-4", "OMAR-11", "GRIFFIN-2", "EAGLE-LEAD", "SHADOW-03", "KARTAL-1"};

        Random rnd = new Random(888);
        int inserted = 0;
        Instant now = Instant.now();

        while (inserted < total) {
            int currentBatchSize = Math.min(batchSize, total - inserted);
            List<Object[]> batchArgs = new ArrayList<>(currentBatchSize);

            for (int i = 0; i < currentBatchSize; i++) {
                String id = "COM-" + UUID.randomUUID().toString().replace("-", "").substring(0, 16).toUpperCase();
                int idNum = inserted + i + 1;
                String vk = vkList[rnd.nextInt(vkList.length)];
                String kuvNo = String.format("KUV-C-%07d", idNum);
                String ustBirlik = "1. Taktik Muhabere Taburu";
                String sensor = sensorUnits[rnd.nextInt(sensorUnits.length)];
                double bearing = rnd.nextDouble() * 360.0;

                double ehLat = 36.0 + rnd.nextDouble() * 5.5;
                double ehLon = 26.0 + rnd.nextDouble() * 15.0;
                double targetLat = ehLat + (rnd.nextDouble() - 0.5) * 1.5;
                double targetLon = ehLon + (rnd.nextDouble() - 0.5) * 1.5;

                double major = 1200.0 + rnd.nextInt(6000);
                double minor = 500.0 + rnd.nextInt(2500);
                double orient = rnd.nextInt(180);

                String loc = locations[rnd.nextInt(locations.length)];
                String hedefKaynak = hedefKaynaklari[rnd.nextInt(hedefKaynaklari.length)];
                String veriGiren = "1. EH Timi";
                String lisan = lisanList[rnd.nextInt(lisanList.length)];
                String protokol = protokolList[rnd.nextInt(protokolList.length)];
                double bw = (rnd.nextInt(20) + 5) * 1000.0;
                String mod = modList[rnd.nextInt(modList.length)];
                boolean mti = rnd.nextBoolean();
                String tip = tipList[rnd.nextInt(tipList.length)];
                double amp = -95.0 + rnd.nextDouble() * 55.0;

                String haberlesmeSekli = haberlesmeSekilleri[rnd.nextInt(haberlesmeSekilleri.length)];
                String calismaSekli = calismaSekilleri[rnd.nextInt(calismaSekilleri.length)];
                double altEsik = -105.0;

                String cagri = callsigns[rnd.nextInt(callsigns.length)];
                String karsiCagri = callsigns[rnd.nextInt(callsigns.length)];
                if (cagri.equals(karsiCagri)) karsiCagri = "HQ-KOMUTA";

                Instant sonTespit = now.minus(rnd.nextInt(168), ChronoUnit.HOURS);
                Instant ilkTespit = sonTespit.minus(rnd.nextInt(900) + 60, ChronoUnit.SECONDS);
                double sureSn = 30.0 + rnd.nextInt(600);

                String teshis = teshisList[rnd.nextInt(teshisList.length)];

                double baseFreq = tip.equals("HF") ? (3.0 + rnd.nextDouble() * 27.0) : (130.0 + rnd.nextDouble() * 250.0);
                double minFreq = baseFreq;
                double maxFreq = baseFreq + 0.05;

                String jsonStub = "[]";
                String opNotu = haberlesmeSekli.equals("KRIPTO") ? "Kriptolu askeri telsiz ağı tespit edildi." : "Rutin sesli muhabere.";
                String onayKullanici = "Sistem Otomatik Entegrasyonu";
                String onayNotu = "Otomatik Taktik COMINT Kaydı Onaylandı.";

                batchArgs.add(new Object[]{
                    id, vk, kuvNo, ustBirlik, sensor, bearing,
                    ehLat, ehLon, 120.0, targetLat, targetLon,
                    major, minor, orient,
                    loc, hedefKaynak, veriGiren, lisan, protokol,
                    bw, mod, mti, tip, amp, haberlesmeSekli, calismaSekli,
                    altEsik, cagri, karsiCagri, Timestamp.from(ilkTespit), Timestamp.from(sonTespit),
                    sureSn, teshis, minFreq, maxFreq, jsonStub,
                    opNotu, onayKullanici, onayNotu
                });
            }

            jdbcTemplate.batchUpdate(sql, batchArgs);
            inserted += currentBatchSize;

            if (inserted % 25000 == 0 || inserted == total) {
                log.info("📊 [COMINT SEEDER PROGRESS] {} / {} kayıt tamamlandı ({:.1f}%)", inserted, total, (inserted * 100.0) / total);
            }
        }
        return inserted;
    }
}

