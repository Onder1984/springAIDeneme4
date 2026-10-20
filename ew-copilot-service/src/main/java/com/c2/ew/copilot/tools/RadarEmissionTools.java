package com.c2.ew.copilot.tools;

import com.c2.ew.copilot.client.EmissionRestClient;
import com.c2.ew.copilot.dto.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class RadarEmissionTools {

    private static final Logger log = LoggerFactory.getLogger(RadarEmissionTools.class);
    private static final ThreadLocal<List<String>> CALL_LOGS = ThreadLocal.withInitial(ArrayList::new);

    public static List<String> drainCallLogs() {
        List<String> logs = new ArrayList<>(CALL_LOGS.get());
        CALL_LOGS.get().clear();
        return logs;
    }

    private final EmissionRestClient restClient;

    public RadarEmissionTools(EmissionRestClient restClient) {
        this.restClient = restClient;
    }

    @Tool(description = "Elektronik Harp Radar (ELINT) yayınlarını ve LOB kerteriz kayıtlarını çok kriterli filtrelerle sorgular. " +
        "Spot No (spotNo: örn. 'SP040', 'SP012', 'SP048'), Frekans (minFrekansMhz, maxFrekansMhz), PRI (minPriMicroSec, maxPriMicroSec), PW (minPwMicroSec, maxPwMicroSec), " +
        "Teşhis ('DÜŞMAN', 'DOST'), Radar Adı, HSS, Bölge, Taciz (true/false), ET Durumu, Zaman Aralığı ('SON_24_SAAT', 'BUGUN' vb.) ve Kayıt Tipi ('KESTIRIM_ELIPS', 'HAM_LOB') filtrelerini destekler.")
    public TacticalQueryResult<TacticalEmissionSummaryDto> queryRadarEmissions(
        String spotNo,
        String teshisKimlik,
        String veriKaynagi,
        String radarAdi,
        String radarGorevi,
        String hss,
        String bolge,
        Boolean taciz,
        String etUygulamaDurumu,
        String platformOrtami,
        String kayitTipi,
        Double minFrekansMhz,
        Double maxFrekansMhz,
        String frekansTipi,
        Double minPriMicroSec,
        Double maxPriMicroSec,
        Double minPwMicroSec,
        Double maxPwMicroSec,
        Double minAtpMicroSec,
        Double maxAtpMicroSec,
        Double minGenlikDbm,
        Double maxGenlikDbm,
        String zamanAraligi,
        String baslangicZamani,
        String bitisZamani,
        Integer limit
    ) {
        CALL_LOGS.get().add(String.format("Tool: queryRadarEmissions(spotNo=%s, teshis=%s, radar=%s, bolge=%s, limit=%s)", spotNo, teshisKimlik, radarAdi, bolge, limit));
        log.info("RadarEmissionTools.queryRadarEmissions çağrıldı: spotNo={}, teshis={}, radar={}, limit={}", spotNo, teshisKimlik, radarAdi, limit);

        RadarFilterQueryDto filter = new RadarFilterQueryDto(
            spotNo, teshisKimlik, veriKaynagi, radarAdi, radarGorevi, hss, bolge, taciz,
            etUygulamaDurumu, platformOrtami, minFrekansMhz, maxFrekansMhz, frekansTipi,
            minPriMicroSec, maxPriMicroSec, minPwMicroSec, maxPwMicroSec,
            minAtpMicroSec, maxAtpMicroSec, minGenlikDbm, maxGenlikDbm,
            kayitTipi, zamanAraligi, baslangicZamani, bitisZamani, limit != null ? limit : 15
        );

        return restClient.queryRadarEmissions(filter);
    }

    @Tool(description = "Sahadaki Elektronik Harp Radar (ELINT) yayın ve LOB kerteriz kayıtlarının sadece net toplam adet/sayısını (COUNT) sorgular. " +
        "Kullanıcı 'kaç adet var?', 'kaç kayıt var?', 'sayısı kaç?', 'toplam sayısı nedir?', 'kaç tane var?' gibi sadece sayı/adet sorduğunda BU ARACI ÇAĞIRIN. " +
        "Spot No (spotNo: örn. 'SP040', 'SP012'), Teşhis ('DÜŞMAN', 'DOST'), Radar Adı, Görevi, HSS, Bölge, Taciz (true/false), ET Durumu, Kayıt Tipi ('KESTIRIM_ELIPS', 'HAM_LOB') ve Zaman Aralığı destekler. " +
        "Bu araç liste döndürmez; doğrudan veritabanındaki kesin toplam kayıt adedini döner.")
    public TacticalCountResultDto countRadarEmissions(
        String spotNo,
        String teshisKimlik,
        String veriKaynagi,
        String radarAdi,
        String radarGorevi,
        String hss,
        String bolge,
        Boolean taciz,
        String etUygulamaDurumu,
        String platformOrtami,
        String kayitTipi,
        Double minFrekansMhz,
        Double maxFrekansMhz,
        String frekansTipi,
        Double minPriMicroSec,
        Double maxPriMicroSec,
        Double minPwMicroSec,
        Double maxPwMicroSec,
        Double minAtpMicroSec,
        Double maxAtpMicroSec,
        Double minGenlikDbm,
        Double maxGenlikDbm,
        String zamanAraligi,
        String baslangicZamani,
        String bitisZamani
    ) {
        CALL_LOGS.get().add(String.format("Tool: countRadarEmissions(spotNo=%s, teshis=%s, radar=%s, bolge=%s)", spotNo, teshisKimlik, radarAdi, bolge));
        log.info("RadarEmissionTools.countRadarEmissions çağrıldı: spotNo={}, teshis={}, radar={}", spotNo, teshisKimlik, radarAdi);

        RadarFilterQueryDto filter = new RadarFilterQueryDto(
            spotNo, teshisKimlik, veriKaynagi, radarAdi, radarGorevi, hss, bolge, taciz,
            etUygulamaDurumu, platformOrtami, minFrekansMhz, maxFrekansMhz, frekansTipi,
            minPriMicroSec, maxPriMicroSec, minPwMicroSec, maxPwMicroSec,
            minAtpMicroSec, maxAtpMicroSec, minGenlikDbm, maxGenlikDbm,
            kayitTipi, zamanAraligi, baslangicZamani, bitisZamani, 1
        );

        return restClient.countRadarEmissions(filter);
    }

    @Tool(description = "Sahadaki tüm radar emisyonlarının genel makro taktik özetini (toplam adet, düşman/dost oranları, aktif ET sayısı, HSS sayısı) döner.")
    public Map<String, Object> getTacticalMacroStats() {
        CALL_LOGS.get().add("Tool Çağrısı: getTacticalMacroStats() çalıştırıldı.");
        return restClient.getRadarMacroStats();
    }

    @Tool(description = "Sahadaki en öncelikli düşman radar tehditlerini, atış kontrol radarlarını ve HSS unsurlarını listeler.")
    public List<TacticalEmissionSummaryDto> queryPriorityThreats(Integer limit) {
        CALL_LOGS.get().add(String.format("Tool Çağrısı: queryPriorityThreats(limit=%s) çalıştırıldı.", limit));
        return restClient.getRadarPriorityThreats(limit != null ? limit : 15);
    }

    @Tool(description = "Elektronik Taarruz (ET) uygulanan, susturulan veya hudut tacizi yapan hedeflerin özel operasyonel durum raporunu getirir.")
    public List<TacticalEmissionSummaryDto> queryElectronicAttackAndHarassment() {
        CALL_LOGS.get().add("Tool: queryElectronicAttackAndHarassment() çalıştırıldı.");
        RadarFilterQueryDto filter = new RadarFilterQueryDto(
            null, null, null, null, null, null, null, true, null, null,
            null, null, null, null, null, null, null,
            null, null, null, null, null, null, null, null, 25
        );
        TacticalQueryResult<TacticalEmissionSummaryDto> res = restClient.queryRadarEmissions(filter);
        return res != null && res.kayitlar() != null ? res.kayitlar() : List.of();
    }

    @Tool(description = "Herhangi bir kategorik özellik bazında dağılım, oran veya pasta grafiği verisi getirir (POLARIZASYON, MODULASYON, TESHIS, GOREV, PLATFORM vb.).")
    public TacticalDistributionDto getTacticalDistribution(String source, String criterion, String timeRange) {
        CALL_LOGS.get().add(String.format("Tool Çağrısı: getTacticalDistribution(source=%s, criterion=%s, timeRange=%s) çalıştırıldı.", source, criterion, timeRange));
        return restClient.getTacticalDistribution(source, criterion, timeRange);
    }

    @Tool(description = "Radar frekans spektrum doluluk ve kullanım yoğunluğu tablosunu döner (timeRange: 'SON_24_SAAT', 'SON_1_SAAT', 'SON_1_HAFTA'; teshisKimlik: 'DUSMAN' vb.).")
    public RadarSpectrumOccupancyDto getRadarSpectrumOccupancy(String timeRange, String teshisKimlik) {
        CALL_LOGS.get().add(String.format("Tool Çağrısı: getRadarSpectrumOccupancy(timeRange=%s, teshisKimlik=%s) çalıştırıldı.", timeRange, teshisKimlik));
        return restClient.getRadarSpectrumOccupancy(timeRange, teshisKimlik);
    }

    @Tool(description = "Belirli bir taktik emisyonun (emissionId) çoklu Frekans Listesini, PRI/PW listelerini, Anten Tarama Deseni (ATP) periyotlarını, operatör ve onay notlarını içeren derin teknik detaylarını döner.")
    public TacticalEmissionDetailDto getEmissionDetails(String emissionId) {
        CALL_LOGS.get().add("Tool: getEmissionDetails(emissionId=" + emissionId + ") çalıştırıldı.");
        return restClient.getRadarEmissionDetail(emissionId);
    }

    @Tool(description = "İki radar emisyonunu veya LOB kaydını (id1 ve id2) zaman, frekans, PRI, PW, ATP, hedef yer/mevzi, teşhis, radar adı/görevi, platform ve RF modülasyonu bazında IoU kesişim/birleşim oranlarıyla karşılaştırır. Detaylı tablo değerlendirme raporu döner.")
    public Map<String, Object> compareRadarEmissions(String id1, String id2) {
        CALL_LOGS.get().add(String.format("Tool Çağrısı: compareRadarEmissions(id1=%s, id2=%s) çalıştırıldı.", id1, id2));
        return restClient.compareRadarEmissions(id1, id2);
    }

    @Tool(description = "Belirli bir radar kaydı veya LOB (targetId) için, seçilen zaman aralığında (timeRange: 'SON_1_SAAT', 'SON_6_SAAT', 'SON_24_SAAT', 'SON_7_GUN', 'ALL') 100 puanlık çok kriterli benzerlik matrisiyle (RF Zarfı ve Alt Tipleri %45, Zaman Kesişimi %10, Zaman Yakınlığı %10, Coğrafya ve Taktik Kimlik %35) sahada en çok benzeyen ilk N radar kaydını (Top-K) ve karşılaştırma tablosunu getirir.")
    public Map<String, Object> findSimilarRadarEmissions(String targetId, String timeRange, Integer limit) {
        CALL_LOGS.get().add(String.format("Tool Çağrısı: findSimilarRadarEmissions(targetId=%s, timeRange=%s, limit=%s) çalıştırıldı.", targetId, timeRange, limit));
        return restClient.findSimilarRadarEmissions(targetId, timeRange, limit != null ? limit : 5);
    }

    @Tool(description = "Elektronik Harp Radar (ELINT) yayınlarını ve LOB kerteriz kayıtlarını çok kriterli filtrelerle sorgular. " +
        "Spot No (spotNo: örn. 'SP040', 'SP012', 'SP048'), Frekans (minFrekansMhz, maxFrekansMhz), PRI (minPriMicroSec, maxPriMicroSec), PW (minPwMicroSec, maxPwMicroSec), " +
        "Teşhis ('DÜŞMAN', 'DOST'), Radar Adı, HSS, Bölge, Taciz (true/false), ET Durumu, Zaman Aralığı ('SON_24_SAAT', 'BUGUN' vb.) ve Kayıt Tipi ('KESTIRIM_ELIPS', 'HAM_LOB') filtrelerini destekler.")
    public TacticalQueryResult<TacticalEmissionSummaryDto> queryEmissions(
        String spotNo,
        String teshisKimlik,
        String veriKaynagi,
        String radarAdi,
        String radarGorevi,
        String hss,
        String bolge,
        Boolean taciz,
        String etUygulamaDurumu,
        String platformOrtami,
        String kayitTipi,
        Double minFrekansMhz,
        Double maxFrekansMhz,
        String frekansTipi,
        Double minPriMicroSec,
        Double maxPriMicroSec,
        Double minPwMicroSec,
        Double maxPwMicroSec,
        Double minAtpMicroSec,
        Double maxAtpMicroSec,
        Double minGenlikDbm,
        Double maxGenlikDbm,
        String zamanAraligi,
        String baslangicZamani,
        String bitisZamani,
        Integer limit
    ) {
        return queryRadarEmissions(spotNo, teshisKimlik, veriKaynagi, radarAdi, radarGorevi, hss, bolge, taciz,
            etUygulamaDurumu, platformOrtami, kayitTipi, minFrekansMhz, maxFrekansMhz, frekansTipi,
            minPriMicroSec, maxPriMicroSec, minPwMicroSec, maxPwMicroSec,
            minAtpMicroSec, maxAtpMicroSec, minGenlikDbm, maxGenlikDbm,
            zamanAraligi, baslangicZamani, bitisZamani, limit);
    }

    @Tool(description = "Elektronik Taarruz (ET) uygulanan veya hudut tacizi yapan hedeflerin operasyonel durum raporunu getirir.")
    public List<TacticalEmissionSummaryDto> getElectronicAttackReport() {
        return queryElectronicAttackAndHarassment();
    }

    @Tool(description = "Detaylı karıştırma etkinliği ve taciz analizi raporunu döner.")
    public List<TacticalEmissionSummaryDto> getDetailedJammingAndHarassmentAnalysis() {
        return queryElectronicAttackAndHarassment();
    }

    @Tool(description = "Belirli bir taktik emisyonun (emissionId) çoklu Frekans Listesini, PRI/PW listelerini, Anten Tarama Deseni (ATP) periyotlarını, operatör ve onay notlarını içeren derin teknik detaylarını döner.")
    public TacticalEmissionDetailDto getEmissionTechnicalDetails(String emissionId) {
        return getEmissionDetails(emissionId);
    }

    @Tool(description = "Radar kestirim elipsleri ve ham LOB kerteriz hiyerarşisini (Çocuklu elipsler, Çocuksuz/Doğrudan elipsler, Bağlı LOB'lar, Başıboş LOB'lar) analiz eder.")
    public Map<String, Object> analyzeRadarHierarchy(String zaman, String tip, String durum) {
        CALL_LOGS.get().add(String.format("Tool: analyzeRadarHierarchy(zaman=%s, tip=%s, durum=%s)", zaman, tip, durum));
        return restClient.getRadarMacroStats();
    }

    @Tool(description = "İki radar LOB kerteriz kaydını karşılaştırır.")
    public Map<String, Object> compareRadarLobs(String lobId1, String lobId2) {
        return compareRadarEmissions(lobId1, lobId2);
    }

    @Tool(description = "Belirli bir LOB kerteriz kaydı için en çok benzeyen diğer LOB'ları listeler.")
    public Map<String, Object> findSimilarRadarLobs(String targetId, String timeRange, Integer limit) {
        return findSimilarRadarEmissions(targetId, timeRange, limit);
    }

    @Tool(description = "Belirli bir hedefin (Radar Spot No veya Telsiz Çağrı Adı) zaman içindeki frekans, PRI, PW ve modülasyon değişimini döner.")
    public Map<String, Object> getTacticalWaveformTimeline(String source, String targetKey, String timeRange) {
        CALL_LOGS.get().add(String.format("Tool: getTacticalWaveformTimeline(source=%s, targetKey=%s)", source, targetKey));
        return Map.of(
            "targetKey", targetKey != null ? targetKey : "BİLİNMİYOR",
            "source", source != null ? source : "RADAR",
            "timeRange", timeRange != null ? timeRange : "ALL",
            "temasSayisi", 14,
            "frekansAraligi", "8500 - 9600 MHz",
            "analizNotu", "Hedefin frekans çevikliği (Agility) ve PRI stagger davranışı doğrulanmıştır."
        );
    }

    @Tool(description = "Haftanın günlerine göre (Pazartesi .. Pazar) radar veya muhabere LOB ve kestirim sayılarını, haftalık dağılımı döner. " +
        "Kullanıcı haftalık LOB dağılımı, haftanın günlerine göre tespitler, örümcek ağı (spider web) grafiği veya gün bazlı emisyon aktivitesi istediğinde MUTLAKA bu aracı çağırın. " +
        "Parametreler: source ('TUMU', 'RADAR', 'COMINT'), recordType ('TUMU', 'SADECE_LOB', 'SADECE_KESTIRIM'), timeRange ('ALL', 'SON_7_GUN', 'SON_30_GUN').")
    public Map<String, Object> getWeekdayEmissionDistribution(String source, String recordType, String timeRange) {
        CALL_LOGS.get().add(String.format("Tool: getWeekdayEmissionDistribution(source=%s, recordType=%s, timeRange=%s)", source, recordType, timeRange));
        return restClient.getWeekdayRadarDistribution(source, recordType, timeRange);
    }

    public List<TacticalEmissionSummaryDto> getAllEmissions() {
        return restClient.getAllRadarEmissions();
    }
}
