# 📡 Muhabere (COMINT) Taktik Trafik Analizi: Çağrı Adı Bağımsız Sabit ve Frekans Atlamalı (FHSS) Haberleşme Patern Doktrini

**Doküman Versiyonu:** 1.0.0  
**Tarih:** 2026-10-25  
**Kapsam:** Taktik Elektronik Harp (EH / C2), Sinyal İstihbaratı (SIGINT / COMINT)  
**Hedef Kitle:** Sinyal İstihbarat Analistleri, Muhabere Elektronik Harp Operatörleri, Yazılım Mimarları  

---

## 1. YÖNETİCİ ÖZETİ VE PROBLEM TANIMI

Geleneksel ve amatör analiz sistemleri, muhabere ağlarını çözümlerken çoğunlukla açık telsiz çağrı kodlarına (`cagri_adi`, `karsi_cagri_adi`) dayanır. Ancak **gerçek bir harekât sahasında ve Elektronik Harp doktrininde bu yaklaşım tamamen geçersizdir**:

1. **Radyo Disiplini ve Aldatma (Spoofing):** Düşman unsurları açık telsiz çağrı adı kullanmaz, operasyon boyunca sürekli kod değiştirir veya yanıltıcı sahte çağrı kodları kullanır.
2. **Kriptolu ve Sayısal Dalga Şekilleri:** Modern askeri telsizler (Aselsan 9661 V/UHF, CNR, PR4G, SINCGARS, DMR, MIL-STD) analog ses yerine saliseler süren sayısal kriptolu paketler aktarır; operatörün kulağına bir isim çalınmaz ve veritabanında çağrı adı alanı **boş (`NULL`) veya tanımsızdır**.
3. **Frekans Atlama (FHSS / ECCM):** Telsizler tek bir frekansta sabit kalmayıp saniyede onlarca/yüzlerce kez zıpladığından tekil bir frekans kanalı üzerinden çağrı adı takibi yapılamaz.

**Doktrinel Çözüm:**  
Gerçek bir COMINT analiz motoru çağrı adlarına **asla güvenmez**. Bunun yerine **fiziksel elektromanyetik parametreler (RF), zamansal ardışıklık ($\Delta t$), spektral atlama havuzu (Hopset) ve mekânsal (geospatial) kestirim koordinatları** üzerinden konuşma paternlerini ve komuta ağlarını çözer.

---

## 2. SABİT FREKANS (FIXED FREQUENCY) ÇAĞRI ADI DIŞI PATERNLER

Sabit frekans haberleşmesinde taşıyıcı tek bir frekans kanalı veya bilinen bir ofset çifti üzerinden yayın yapar (`min_frekans_mhz ≈ max_frekans_mhz`).

```text
       SABİT FREKANS: ÇAPRAZ DİYALOG (PING-PONG) VE MANDALLAMA ZİNCİRİ

       Unsur A (Mevki 1)                       Unsur B (Mevki 2)
       [ Yayın 1: 142.250 MHz ] 
       Süre: 8.4 sn (FM, 25kHz)
       Bitiş Zamanı: T1
              │
              │  Δt = 1.2 sn (Telsiz operatörünün dinleyip mandallama süresi)
              ▼
                                        [ Yayın 2: 142.250 MHz ]
                                        Süre: 4.1 sn (FM, 25kHz)
                                        Başlangıç Zamanı: T1 + 1.2 sn
```

### A. Zamansal Mandallama Zinciri (Push-to-Talk / Ping-Pong Dialog)
* **Kullanılan Parametreler:** `min_frekans_mhz`, `ilk_tespit_zamani`, `son_tespit_zamani`, `sure_sn`, `modulasyon`, `bant_genisligi_hz`, `yayin_enlem`, `yayin_boylam`
* **Patern Eşleme Mantığı:**
  1. **Frekans Uyumu:** $|f_1 - f_2| < 12.5\text{ kHz}$ (Kanal adımı toleransı).
  2. **Dalga Şekli Uyumu:** `modulasyon` aynı (örn. `FM` veya `FSK`), `bant_genisligi_hz` aynı (örn. 25.000 Hz).
  3. **İnsan Mandallama Boşluğu ($\Delta t$):** 1. yayının bitişi (`son_tespit_zamani`) ile 2. yayının başlangıcı (`ilk_tespit_zamani`) arasında:
     $$0.3\text{ sn} \le \Delta t \le 4.0\text{ sn}$$
     aralığında zaman farkı bulunur. *(Askeri personelin karşı tarafın sözünü bitirmesini bekleyip mandala basma reaksiyon süresi).*
  4. **Mekânsal Ayrışma (İki Farklı Mevzi):** İki yayının kestirim koordinatları veya kerteriz yönleri farklıdır ($d > 500\text{ m}$ ve $d < 35\text{ km}$ - VHF görüş menzili sınırları içinde).
* **Taktik Çıkarım:** Çağrı adı olmasa dahi bu iki yayının **aynı taktik kanalda karşılıklı konuşan iki ayrı askeri unsur** olduğu %100 kesinlikle kanıtlanır.

### B. Röle / Tekrarlayıcı Frekans Çifti (Duplex Repeater Offset) Paterni
* **Kullanılan Parametreler:** `min_frekans_mhz`, `ilk_tespit_zamani`, `sure_sn`, `calisma_sekli`
* **Patern Eşleme Mantığı:**
  - İki yayın farklı frekanstadır ancak frekans farkı standart askeri/taktik röle ofsetine eşittir (Örn: $\Delta f = 600\text{ kHz}$ veya $5.0\text{ MHz}$).
  - Başlangıç zamanları neredeyse eşzamanlıdır ($|\Delta t_{baslangic}| < 0.2\text{ sn}$) ve yayın süreleri birebir aynıdır (`sure_sn_1 ≈ sure_sn_2`).
* **Taktik Çıkarım:** Bölgedeki **Taktik Telsiz Rölesi / Tekrarlayıcısı** ve kapsama alanı tespit edilir.

### C. Mandallama Süresi ve Konuşma Disiplini (Burstiness & Protocol Signatures)
* **Kullanılan Parametreler:** `sure_sn`, `haberlesme_sekli`
* **Patern Eşleme Mantığı:**
  - **Kısa Teyit Mandallaması (1 - 3 sn):** "Anlaşıldı / Tamam / Devam" teyidi.
  - **Durum Raporu / Emir (5 - 15 sn):** Standart taktik brifing veya temas bildirimi.
  - **Veri / Mesaj Aktarımı (20 - 60 sn):** Sayısal telsiz veri paketi aktarımı.

---

## 3. FREKANS ATLAMA (FHSS) ÇAĞRI ADI DIŞI PATERNLER

Frekans atlamalı telsizler saniyede yüzlerce kanal değiştirir. COMINT sensörleri bu yayını tekil frekansta değil; **geniş frekans zarfı, atlama listesi veya mikro darbe-patlamalar** olarak kaydeder.

```text
       FREKANS ATLAMA (FHSS): ÇOKLU KANAL HAVUZU & EŞZAMANLI DÖNGÜ

       Yayın 1: [30.0 - 87.975 MHz Zarfı]       Yayın 2: [30.0 - 87.975 MHz Zarfı]
       Frekans Havuzu: {f3, f8, f12, f25...}    Frekans Havuzu: {f3, f8, f12, f25...}
       Modülasyon: FSK (Darbe-Patlama)           Modülasyon: FSK (Darbe-Patlama)
       
       ───> İki yayın farklı saatlerde ve farklı noktalardan yapılsa bile, 
            kullandıkları FREKANS ATLAMA HAVUZU (%75+ Jaccard Örtüşmesi) aynıdır!
```

### A. Ortak Atlama Havuzu Korelasyonu (Hopset / Pool Overlap)
* **Kullanılan Parametreler:** `min_frekans_mhz`, `max_frekans_mhz`, `frekans_listesi_json`, `haberlesme_sekli = DARBE_PATLAMA`, `modulasyon`
* **Patern Eşleme Mantığı:**
  - İki yayının `frekansListesiJson` içindeki taşıyıcı frekans kümeleri ($Pool_A$ ve $Pool_B$) **Jaccard Benzerlik İndeksi** ile karşılaştırılır:
    $$J(Pool_A, Pool_B) = \frac{|Pool_A \cap Pool_B|}{|Pool_A \cup Pool_B|} \ge 0.75$$
  - Frekans alt ve üst sınırları (`min_frekans_mhz`, `max_frekans_mhz`) aynı bant dilimine oturur (örn. 30.0 – 87.975 MHz taktik VHF atlama bandı).
* **Taktik Çıkarım:** Çağrı adı olmasa bile iki yayının **aynı kriptolu Frekans Atlamalı Telsiz Ağına (FHSS Net)** dahil olduğu ve aynı atlama tablosunu (Hopset) paylaştığı matematiksel olarak ispatlanır.

### B. FHSS Ağında Sıralı Mandallama (Time-Separated Hopset Dialogue)
* **Kullanılan Parametreler:** `frekans_listesi_json`, `son_tespit_zamani`, `ilk_tespit_zamani`, `yayin_enlem`, `yayin_boylam`
* **Patern Eşleme Mantığı:**
  - Hopset benzerliği `%75+` olan iki FHSS yayını, zaman ekseninde birbirini takip eder ($A$ yayını biter, 1-3 saniye sonra $B$ yayını başlar).
  - İki yayının CEP elips merkezleri ($Enlem_1, Boylam_1$ ve $Enlem_2, Boylam_2$) birbirinden 3-20 km uzaktadır.
* **Taktik Çıkarım:** Çağrı adı olmadan **Frekans Atlamalı Kriptolu Taktik Ağ Üyeleri** ve konuşma sırası ortaya çıkarılır.

### C. Verici Parmak İzi (Transmitter RF Fingerprint) ve Sinyal Gücü Ayrıştırma
* **Kullanılan Parametreler:** `genlik_dbm`, `eh_unsuru_enlem`, `eh_unsuru_boylam`, `yon` (Kerteriz Açısı)
* **Patern Eşleme Mantığı:**
  - Aynı dinleme istasyonuna (`eh_unsuru`), aynı kerteriz açısından (`yon ± 2°`) ulaşan ve genlik seviyesi neredeyse sabit (`genlik_dbm ± 2 dBm`) olan yayınlar kümelenir.
* **Taktik Sonuç:** Çağrı adı değişse veya hiç olmasa bile bu yayınların **aynı fiziksel telsiz cihazından (tekil verici)** çıktığı teyit edilir.

---

## 4. ÇAĞRI ADI BAĞIMSIZ PATERN MATRİSİ

| Patern Türü | Mod | Kullanılan RF & Taktik Parametreler | Patern Mantığı & Formülü |
| :--- | :--- | :--- | :--- |
| **Diyalog (Soru-Cevap)** | Sabit Freq | `min_frekans`, $\Delta t$ ($0.5-4\text{s}$), `sure_sn`, `modulasyon` | Aynı frekans, ardışık mandallama, iki farklı coğrafi mevzi. |
| **Röle / Aktarma** | Sabit Freq | $\Delta f = 600\text{kHz} / 5\text{MHz}$, $\Delta t \approx 0$, `sure_sn` | Frekans ofseti, eşzamanlı başlama, aynı yayın süresi. |
| **FHSS Taktik Ağı** | Atlamalı | `frekans_listesi_json`, `min/max_frekans`, `modulasyon` | Frekans atlama havuzlarının kesişimi ($Jaccard > 0.75$). |
| **FHSS Karşılıklı Trafik** | Atlamalı | Ortak Hopset, $\Delta t$ ardışıklık, `yayin_enlem/boylam` | Aynı atlama tablosunu kullanan iki farklı mevzinin konuşması. |
| **Verici Kimlikleme** | Her İkisi | `yon` (LOB açısı), `genlik_dbm`, `eh_unsuru` mevkii | Tekil cihaz RF parmak izi (fiziksel telsiz eşleme). |
| **Trafik Patlaması (Surge)**| Her İkisi | Zaman penceresi frekansı, `sure_sn < 4\text{s}`, `DARBE_PATLAMA` | Belirli bir bantta aniden yükselen kısa süreli yayın sıklığı ($3\sigma$ anomali). |

---

## 5. VERİ MODELİ EŞLEŞTİRMESİ (`TacticalComintEntity`)

Mevcut veritabanı şemamızda bu analitiği doğrudan besleyen kolonlar:

```text
┌─────────────────────────────────────────────────────────────────────────────┐
│                 TacticalComintEntity (tactical_comint)                      │
├──────────────────────────────┬──────────────────────────────────────────────┤
│ RF Parametreleri             │ min_frekans_mhz, max_frekans_mhz,            │
│                              │ bant_genisligi_hz, frekans_listesi_json,     │
│                              │ tip (HF, VHF)                                │
├──────────────────────────────┼──────────────────────────────────────────────┤
│ Dalga Şekli & Protokol       │ modulasyon (FM, FSK, PSK),                   │
│                              │ protokol (STANAG_5065, DMR, MIL_188...),     │
│                              │ haberlesme_sekli (SES, VERI, KRIPTO, DARBE)  │
├──────────────────────────────┼──────────────────────────────────────────────┤
│ Zaman Parametreleri          │ ilk_tespit_zamani, son_tespit_zamani,        │
│                              │ sure_sn (Dwell Time / Yayın Süresi)          │
├──────────────────────────────┼──────────────────────────────────────────────┤
│ Mekânsal & Yön               │ yayin_enlem, yayin_boylam (CEP Elipsi),      │
│                              │ yon (Kerteriz), eh_unsuru_enlem / boylam     │
├──────────────────────────────┼──────────────────────────────────────────────┤
│ Sinyal Seviyesi              │ genlik_dbm, alt_esik_seviyesi_dbm            │
└──────────────────────────────┴──────────────────────────────────────────────┘
```

---

## 6. GELECEK GELİŞTİRME YOL HARİTASI

1. **`ComintRfPatternEngine` (Backend Analitik Servisi):**
   - Çağrı adını tamamen devreden çıkarıp, `tactical_comint` üzerinde `min_frekans`, `zaman`, `hopset` ve `konum` bazlı SQL ve bellek içi korelasyon yürüten servis katmanı.
2. **Görsel Taktik Ağ ve Şelale Grafiği (Waterfall / Spectrogram & Graph):**
   - Ekranda frekans-zaman ekseninde mandallama bloklarının şelale akışı ve karşılıklı konuşan mevziler arasına çizilen vektörel taktik diyalog okları.
