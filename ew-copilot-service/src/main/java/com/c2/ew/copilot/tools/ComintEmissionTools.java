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
public class ComintEmissionTools {

    private static final Logger log = LoggerFactory.getLogger(ComintEmissionTools.class);
    private static final ThreadLocal<List<String>> COMINT_CALL_LOGS = ThreadLocal.withInitial(ArrayList::new);

    public static List<String> drainCallLogs() {
        List<String> logs = new ArrayList<>(COMINT_CALL_LOGS.get());
        COMINT_CALL_LOGS.get().clear();
        return logs;
    }

    private final EmissionRestClient restClient;

    public ComintEmissionTools(EmissionRestClient restClient) {
        this.restClient = restClient;
    }

    @Tool(description = "Elektronik Harp Muhabere (COMINT) telsiz ve ses yayınlarını çok kriterli filtrelerle sorgular. " +
        "Frekans aralığı (minFrekansMhz, maxFrekansMhz), Teşhis ('DÜŞMAN', 'DOST'), Lisan ('Türkçe', 'Rusça', 'Arapça'), " +
        "Tip ('HF', 'VHF'), Haberleşme Şekli ('SES', 'KRIPTO', 'DARBE_PATLAMA'), Çağrı Adı, " +
        "Protokol ('STANAG_5065_MSK', 'STANAG_5065_FSK', 'STANAG_4529', 'MIL_188_141A', 'MIL_188_141B', 'LINK_11_CLEW', 'LINK_11_SLEW', 'PACTOR_2', 'PACTOR_3', 'CLOVER_2000', 'CLOVER_2500', 'FUSION', 'DMR_MOBIL', 'DMR_BAZ') ve Zaman Aralığı destekler.")
    public TacticalQueryResult<TacticalComintSummaryDto> queryComintEmissions(
        String teshisKimlik,
        String tip,
        String lisan,
        String haberlesmeSekli,
        Boolean kripto,
        Double minFrekansMhz,
        Double maxFrekansMhz,
        Double minGenlikDbm,
        Double maxGenlikDbm,
        String bolge,
        String protokol,
        String cagriAdi,
        String karsiCagriAdi,
        String kayitTipi,
        String zamanAraligi,
        Integer limit
    ) {
        COMINT_CALL_LOGS.get().add(String.format("Tool: queryComintEmissions(teshis=%s, tip=%s, cagri=%s, limit=%s)", teshisKimlik, tip, cagriAdi, limit));
        log.info("ComintEmissionTools.queryComintEmissions çağrıldı: teshis={}, tip={}, cagri={}", teshisKimlik, tip, cagriAdi);

        ComintFilterQueryDto filter = new ComintFilterQueryDto(
            teshisKimlik, tip, lisan, haberlesmeSekli, kripto,
            minFrekansMhz, maxFrekansMhz, minGenlikDbm, maxGenlikDbm,
            bolge, protokol, cagriAdi, karsiCagriAdi, kayitTipi,
            zamanAraligi, limit != null ? limit : 15
        );

        return restClient.queryComintEmissions(filter);
    }

    @Tool(description = "Sahadaki Elektronik Harp Muhabere (COMINT) telsiz ve ses yayınlarının sadece net toplam adet/sayısını (COUNT) sorgular. " +
        "Kullanıcı 'kaç adet telsiz yayını var?', 'kaç kayıt var?', 'sayısı kaç?', 'toplam sayısı nedir?', 'kaç tane var?', 'kaç adet kriptolu yayın var?', 'kaç adet VHF yayını var?' gibi sadece sayı/adet sorduğunda BU ARACI ÇAĞIRIN. " +
        "Teşhis ('DÜŞMAN', 'DOST'), Lisan ('Türkçe', 'Rusça', 'Arapça'), Tip ('HF', 'VHF'), Haberleşme Şekli ('SES', 'KRIPTO', 'DARBE_PATLAMA'), Çağrı Adı, Protokol, Bölge, Kayıt Tipi ve Zaman Aralığı destekler. " +
        "Bu araç liste döndürmez; doğrudan veritabanındaki kesin toplam kayıt adedini döner.")
    public TacticalCountResultDto countComintEmissions(
        String teshisKimlik,
        String tip,
        String lisan,
        String haberlesmeSekli,
        Boolean kripto,
        Double minFrekansMhz,
        Double maxFrekansMhz,
        Double minGenlikDbm,
        Double maxGenlikDbm,
        String bolge,
        String protokol,
        String cagriAdi,
        String karsiCagriAdi,
        String kayitTipi,
        String zamanAraligi
    ) {
        COMINT_CALL_LOGS.get().add(String.format("Tool: countComintEmissions(teshis=%s, tip=%s, cagri=%s, bolge=%s)", teshisKimlik, tip, cagriAdi, bolge));
        log.info("ComintEmissionTools.countComintEmissions çağrıldı: teshis={}, tip={}, cagri={}", teshisKimlik, tip, cagriAdi);

        ComintFilterQueryDto filter = new ComintFilterQueryDto(
            teshisKimlik, tip, lisan, haberlesmeSekli, kripto,
            minFrekansMhz, maxFrekansMhz, minGenlikDbm, maxGenlikDbm,
            bolge, protokol, cagriAdi, karsiCagriAdi, kayitTipi,
            zamanAraligi, 1
        );

        return restClient.countComintEmissions(filter);
    }

    @Tool(description = "Sahadaki tüm muhabere (COMINT / telsiz) yayınlarının genel makro taktik özetini (toplam yayın adedi, lisan dağılımı, kriptolu oranı, VHF/HF dağılımı) döner.")
    public Map<String, Object> getComintMacroStats() {
        COMINT_CALL_LOGS.get().add("Tool Çağrısı: getComintMacroStats() çalıştırıldı.");
        return restClient.getComintMacroStats();
    }

    @Tool(description = "Sahadaki en öncelikli düşman muhabere tehditlerini, kriptolu yayınları ve karıştırma faaliyetlerini listeler.")
    public List<TacticalComintSummaryDto> queryPriorityComintThreats(Integer limit) {
        COMINT_CALL_LOGS.get().add(String.format("Tool Çağrısı: queryPriorityComintThreats(limit=%s) çalıştırıldı.", limit));
        return restClient.getComintPriorityThreats(limit != null ? limit : 15);
    }

    @Tool(description = "Telsiz çağrı adı ve karşı çağrı adı ikililerini, aralarındaki görüşme sıklığını, toplam sürelerini ve link ilişkilerini döner.")
    public List<CallsignPairStatsDto> getCallsignPairInteractionStats(
        String cagriAdi, String karsiCagriAdi, String baslangicZamani, String bitisZamani, Integer limit
    ) {
        COMINT_CALL_LOGS.get().add(String.format("Tool Çağrısı: getCallsignPairInteractionStats(cagri=%s, karsi=%s)", cagriAdi, karsiCagriAdi));
        return restClient.getCallsignPairInteractionStats(cagriAdi, karsiCagriAdi, baslangicZamani, bitisZamani, limit != null ? limit : 15);
    }

    @Tool(description = "En çok konuşan tekil telsiz çağrı adları sıralamasını döner.")
    public Map<String, Long> getMostActiveCallsigns(String baslangicZamani, String bitisZamani, Integer limit) {
        COMINT_CALL_LOGS.get().add(String.format("Tool Çağrısı: getMostActiveCallsigns(zaman=[%s-%s])", baslangicZamani, bitisZamani));
        return restClient.getMostActiveCallsigns(baslangicZamani, bitisZamani, limit != null ? limit : 15);
    }

    @Tool(description = "Telsiz trafiğinde hareketlenme, ani artış veya saatlik yoğunluk/surge analizini döner.")
    public Map<String, Object> getComintTrafficTrend(String cagriAdi, String baslangicZamani, String bitisZamani) {
        COMINT_CALL_LOGS.get().add(String.format("Tool Çağrısı: getComintTrafficTrend(cagri=%s)", cagriAdi));
        return restClient.getComintTrafficTrend(cagriAdi, baslangicZamani, bitisZamani);
    }

    @Tool(description = "Belirli bir Muhabere (COMINT) yayınının frekans listesini, kripto durumunu, operatör notlarını içeren derin teknik detaylarını döner.")
    public TacticalComintDetailDto getComintDetails(String comintId) {
        COMINT_CALL_LOGS.get().add("Tool: getComintDetails(comintId=" + comintId + ") çalıştırıldı.");
        return restClient.getComintDetail(comintId);
    }

    @Tool(description = "Belirli bir çağrı adının (cagriAdi) karşı çağrı adlarıyla yaptığı muhabere irtibatlarını ve telsiz ağını listeler.")
    public List<TacticalComintSummaryDto> queryComintCallsignNetwork(String cagriAdi, String karsiCagriAdi, Integer limit) {
        COMINT_CALL_LOGS.get().add(String.format("Tool: queryComintCallsignNetwork(cagri=%s, karsi=%s)", cagriAdi, karsiCagriAdi));
        ComintFilterQueryDto filter = new ComintFilterQueryDto(
            null, null, null, null, null, null, null, null, null,
            null, null, cagriAdi, karsiCagriAdi, null, null, limit != null ? limit : 20
        );
        TacticalQueryResult<TacticalComintSummaryDto> res = restClient.queryComintEmissions(filter);
        return res != null && res.kayitlar() != null ? res.kayitlar() : List.of();
    }

    @Tool(description = "Telsiz karıştırma (gayda veya gürültü), kripto, darbe patlama (burst) ve haberleşme güvenliği (COMSEC) taktik raporunu getirir.")
    public List<TacticalComintSummaryDto> getComsecAndTransmissionTactics() {
        COMINT_CALL_LOGS.get().add("Tool: getComsecAndTransmissionTactics() çalıştırıldı.");
        ComintFilterQueryDto filter = new ComintFilterQueryDto(
            null, null, null, null, true, null, null, null, null, null, null, null, null, null, null, 25
        );
        TacticalQueryResult<TacticalComintSummaryDto> res = restClient.queryComintEmissions(filter);
        return res != null && res.kayitlar() != null ? res.kayitlar() : List.of();
    }

    @Tool(description = "Belirli bir Muhabere (COMINT) yayınının tüm 30 parametresini ve operatör notlarını döner.")
    public TacticalComintDetailDto getComintEmissionDetails(String comintId) {
        return getComintDetails(comintId);
    }

    @Tool(description = "Muhabere (COMINT) kestirim elipsleri ve telsiz LOB kerteriz hiyerarşisini analiz eder.")
    public Map<String, Object> analyzeComintHierarchy(String zaman, String tip, String durum) {
        COMINT_CALL_LOGS.get().add(String.format("Tool: analyzeComintHierarchy(zaman=%s, tip=%s, durum=%s)", zaman, tip, durum));
        return restClient.getComintMacroStats();
    }

    public List<TacticalComintSummaryDto> getAllComints() {
        return restClient.getAllComintEmissions();
    }
}
