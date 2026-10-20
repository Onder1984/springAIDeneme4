# ⚔️ Taktik Elektronik Harp Copilot: Doğal Dil Sorguları ve Araç (Tool) Doktrin Rehberi

**Doküman Versiyonu:** 3.0.0 (Çift Mikroservis, Skaler Sayım & Görselleştirme Filtrelemeli Güncel Sürüm)  
**Sistem:** Taktik Elektronik Harp (EH / C2 / SIGINT) Karar Destek ve Analiz Copilotu  
**Hedef Kitle:** Elektronik Harp Subayları (EWO), C2 Harekât Operatörleri ve Sistem Geliştiricileri  

---

## 1. GİRİŞ VE ÇALIŞMA PRENSİBİ

Bu rehber, **Spring AI (Google Gemini 2.5 / Flash-Lite)** destekli Taktik C2 Copilot sistemimize yöneltilebilecek tüm doğal dil soru kalıplarını, bu sorular sorulduğunda arka planda tetiklenen **Spring AI Araçlarını (`@Tool`)**, araçların **parametre yapılarını**, **JPA/SQL filtreleme mantıklarını**, **skaler sayım (COUNT) vs. detay listeleme (LIST) ayrımını** ve karar mekanizmasının arkasındaki **askeri doktrin gerekçelerini** eksiksiz olarak açıklar.

Sistem, **`ew-copilot-service` (Port 8082)** ve **`ew-emission-service` (Port 8081)** olmak üzere çift mikroservis mimarisinde çalışır. Operatör doğal dilde bir soru sorduğunda akış şu şekilde işler:

```text
┌─────────────────────────────────┐      ┌──────────────────────────────────┐      ┌──────────────────────────────────┐
│   OPERATÖR DOĞAL DİL SORGUSU    │ ───> │        ew-copilot-service        │ ───> │       ew-emission-service        │
│   "SP040 spot no lu kaç         │      │ 🧠 Gemini Niyeti Çözer           │      │ 🔍 JPA Criteria / PostGIS        │
│    kayıt var?"                  │      │ 🛠️ countRadarEmissions Seçer     │      │ ⚡ SELECT COUNT(*) İndeksli      │
└─────────────────────────────────┘      └──────────────────────────────────┘      └──────────────────────────────────┘
                                                          │                                          │
                                                          ▼                                          ▼
                                         ┌──────────────────────────────────┐      ┌──────────────────────────────────┐
                                         │       TAKTİK KONSOL (REACT)      │ <─── │        KESİN SKALER METRİK       │
                                         │ 🎯 Net Durum Kartı (Tablo YOK)   │      │ TacticalCountResultDto           │
                                         │ 🚫 Grafik Panel KAPALI (Sade UI) │      │ { toplamKayitSayisi: 6234 }      │
                                         └──────────────────────────────────┘      └──────────────────────────────────┘
```

---

## 2. NİYET (INTENT) SINIFLANDIRMA SİSTEMİ: SAYIM VS. LİSTELEME VS. GÖRSELLEŞTİRME

Sistemde operatörün sorduğu sorular üç temel niyet sınıfına ayrılır:

```text
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                              OPERATÖR SORGUSUNUN NİYETİ                                │
├────────────────────────────┬─────────────────────────────┬─────────────────────────────┤
│   1. SKALER SAYIM (COUNT)  │   2. DETAY LİSTELEME (LIST) │ 3. GÖRSELLEŞTİRME (VISUAL)  │
├────────────────────────────┼─────────────────────────────┼─────────────────────────────┤
│ "Kaç kayıt var?"           │ "Hedefleri listele / dök"   │ "Dalga formu grafiğini aç"  │
│ "Toplam sayısı nedir?"     │ "Kayıtları göster"          │ "Örümcek ağı dağılımı"      │
│ "Kaç adet düşman radarı?"  │ "Örnek kayıt ver"           │ "Spektrum doluluk çizelgesi"│
│ "Kaç telsiz yayını var?"   │ "Düşman radarları hangileri"│ "Çağrı adı ısı haritası"    │
├────────────────────────────┼─────────────────────────────┼─────────────────────────────┤
│ 🛠️ countRadarEmissions     │ 🛠️ queryRadarEmissions      │ 🛠️ getTacticalWaveformTimeline│
│ 🛠️ countComintEmissions    │ 🛠️ queryComintEmissions     │ 🛠️ getWeekdayEmissionDistrib│
│ ❌ Tablo DÖKÜLMEZ          │ ✅ Formatlı Markdown Tablosu│ 🛠️ getRadarSpectrumOccupancy │
│ ❌ Grafik AÇILMAZ          │ ❌ Grafik AÇILMAZ           │ ✅ Sağ ECharts Paneli AÇILIR │
│ 🎯 1-2 Cümlelik Net Skaler │ 📋 İlk N Kayıt + Sekme Yönl.│ 📊 İnteraktif Grafik Çizilir│
└────────────────────────────┴─────────────────────────────┴─────────────────────────────┘
```

---

## 3. DETAYLI KATEGORİ VE ARAÇ (TOOL) REHBERİ

---

### KATEGORİ 1: Skaler Sayım ve Net Adet Sorguları (COUNT Niyeti)

#### 1.1. Radar ve LOB Net Kayıt Sayısı (COUNT)
* 💬 **Operatör Doğal Dil Soruları:**
  * *"SP040 spot no'lu kaç kayıt var?"*
  * *"Sahada toplam kaç adet düşman radarı tespit edildi?"*
  * *"Kilis bölgesinde konuşlu kaç adet hava savunma radarı var?"*
  * *"X-bandında çalışan kaç tane erken ihbar radarı kaydedildi?"*
  * *"Bugün tespit edilen toplam başıboş (kestirimsiz) LOB sayısı kaçtır?"*
* 🛠️ **Çağrılan Tool:** `countRadarEmissions(spotNo, teshisKimlik, veriKaynagi, radarAdi, radarGorevi, hss, bolge, taciz, etUygulamaDurumu, platformOrtami, kayitTipi, minFrekansMhz, maxFrekansMhz, frekansTipi, minPriMicroSec, maxPriMicroSec, minPwMicroSec, maxPwMicroSec, minAtpMicroSec, maxAtpMicroSec, minGenlikDbm, maxGenlikDbm, zamanAraligi, baslangicZamani, bitisZamani)`
* 📍 **Kaynak Kod Konumu:** [`RadarEmissionTools.java`](file:///c:/Users/onderproje/Desktop/AntigravitySpringAIDeneme4/ew-copilot-service/src/main/java/com/c2/ew/copilot/tools/RadarEmissionTools.java) & [`TacticalEmissionQueryService.java`](file:///c:/Users/onderproje/Desktop/AntigravitySpringAIDeneme4/ew-emission-service/src/main/java/com/c2/ew/emission/service/TacticalEmissionQueryService.java)
* ⚙️ **Koddaki Filtreleme ve SQL Mantığı:**
  `SELECT COUNT(e) FROM TacticalEmissionEntity e WHERE ...`  
  Entity nesnesi yüklemeden, DTO mapping yapmadan doğrudan veritabanı indeksleri üzerinden çalışır. `TacticalCountResultDto` döner.
* 🎖️ **Askeri Doktrin ve Taktik Anlamı:**
  Operatör harekât anında sayısal durumu öğrenmek istediğinde gereksiz 15-20 satırlık tablo gürültüsünden kurtulur, karar döngüsü (OODA Loop - Observe, Orient, Decide, Act) hızlanır.

---

#### 1.2. Muhabere (COMINT / Telsiz) Net Yayın Sayısı (COUNT)
* 💬 **Operatör Doğal Dil Soruları:**
  * *"VOLGA-01 çağrı adlı kaç telsiz kaydı var?"*
  * *"Sahada toplam kaç adet kriptolu muhabere yayını tespit edildi?"*
  * *"VHF bandında Rusça konuşulan kaç ses yayını var?"*
  * *"Suriye hudut hattında kaç adet düşman telsizi temas sağladı?"*
  * *"Bugün kaydedilen başıboş telsiz kerteriz hattı sayısı nedir?"*
* 🛠️ **Çağrılan Tool:** `countComintEmissions(teshisKimlik, tip, lisan, haberlesmeSekli, kripto, minFrekansMhz, maxFrekansMhz, minGenlikDbm, maxGenlikDbm, bolge, protokol, cagriAdi, karsiCagriAdi, kayitTipi, zamanAraligi)`
* 📍 **Kaynak Kod Konumu:** [`ComintEmissionTools.java`](file:///c:/Users/onderproje/Desktop/AntigravitySpringAIDeneme4/ew-copilot-service/src/main/java/com/c2/ew/copilot/tools/ComintEmissionTools.java) & [`TacticalComintQueryService.java`](file:///c:/Users/onderproje/Desktop/AntigravitySpringAIDeneme4/ew-emission-service/src/main/java/com/c2/ew/emission/service/TacticalComintQueryService.java)
* ⚙️ **Koddaki Filtreleme ve SQL Mantığı:**
  `SELECT COUNT(c) FROM TacticalComintEntity c WHERE ...`  
  Telsiz kayıtları üzerinde tek geçişli SQL sayımı yapar.

---

### KATEGORİ 2: Makro Durumsal Farkındalık ve Dağılım İstatistikleri

#### 2.1. Radar (ELINT) Makro Durum Analizi
* 💬 **Operatör Doğal Dil Soruları:**
  * *"Sahadaki genel radar durumunu ve makro istatistikleri özetle."*
  * *"Platform ortamlarına göre (hava, deniz, kara) radar dağılımı nedir?"*
  * *"Sensör veri kaynaklarına göre (Kara, Deniz, Hava, Genelkurmay) radar dağılımını ver."*
  * *"Sahada toplam kaç kestirim elipsi ve kaç kerteriz hattı (LOB) takip ediliyor?"*
* 🛠️ **Çağrılan Tool:** `getTacticalMacroStats()`
* 📍 **Kaynak Kod Konumu:** [`RadarEmissionTools.java`](file:///c:/Users/onderproje/Desktop/AntigravitySpringAIDeneme4/ew-copilot-service/src/main/java/com/c2/ew/copilot/tools/RadarEmissionTools.java)
* ⚙️ **Koddaki Filtreleme ve SQL Mantığı:**
  ```sql
  SELECT teshis_kimlik, COUNT(*) FROM tactical_emissions GROUP BY teshis_kimlik;
  SELECT platform_ortami, COUNT(*) FROM tactical_emissions GROUP BY platform_ortami;
  SELECT veri_kaynagi, COUNT(*) FROM tactical_emissions GROUP BY veri_kaynagi;
  SELECT kayit_tipi, COUNT(*) FROM tactical_emissions GROUP BY kayit_tipi;
  ```
* 🎖️ **Askeri Doktrin ve Taktik Anlamı:**
  C2 harekât merkezinde video-wall ekranına yansıtılan üst düzey durumsal farkındalık tablosudur. Harekât sahasındaki kuvvet çarpanını, dost/hasım kuvvet oranını ve sensör kapsama dengesini gösterir.

---

#### 2.2. Muhabere (COMINT) Makro Durum Analizi
* 💬 **Operatör Doğal Dil Soruları:**
  * *"Telsiz trafiğindeki genel durumu özetle."*
  * *"Genel telsiz haberleşme trafiğinde kripto ve karıştırma oranları nedir?"*
  * *"Sahada hangi dillerde (Rusça, Arapça, Türkçe vb.) konuşuluyor ve HF/VHF dağılımı nasıl?"*
* 🛠️ **Çağrılan Tool:** `getComintMacroStats()`
* 📍 **Kaynak Kod Konumu:** [`ComintEmissionTools.java`](file:///c:/Users/onderproje/Desktop/AntigravitySpringAIDeneme4/ew-copilot-service/src/main/java/com/c2/ew/copilot/tools/ComintEmissionTools.java)
* ⚙️ **Koddaki Filtreleme ve SQL Mantığı:**
  `SELECT haberlesme_sekli, COUNT(*) FROM tactical_comint GROUP BY haberlesme_sekli;`  
  `SELECT tip, COUNT(*) FROM tactical_comint GROUP BY tip;`

---

### KATEGORİ 3: Kritik Tehdit ve Öncelikli Hedef Sorguları

#### 3.1. Radar (ELINT) Öncelikli Tehdit Analizi
* 💬 **Operatör Doğal Dil Soruları:**
  * *"Şu anki en kritik düşman radar tehditlerini listele."*
  * *"Radar tacizi yapan (kilitlenen/aydınlatan) veya HSS içeren öncelikli hava savunma radarları hangileri?"*
  * *"Acil angajman riski taşıyan düşman radarları nelerdir?"*
* 🛠️ **Çağrılan Tool:** `queryPriorityThreats(Integer limit)`
* 📍 **Kaynak Kod Konumu:** [`RadarEmissionTools.java`](file:///c:/Users/onderproje/Desktop/AntigravitySpringAIDeneme4/ew-copilot-service/src/main/java/com/c2/ew/copilot/tools/RadarEmissionTools.java)
* ⚙️ **Koddaki Filtreleme ve SQL Mantığı:**
  `WHERE taciz = TRUE OR (teshis_kimlik IN ('DUSMAN', 'MUHTEMEL_DUSMAN') AND hss IS NOT NULL) ORDER BY son_tespit_zamani DESC LIMIT 15`
* 🎖️ **Askeri Doktrin ve Taktik Anlamı:**
  * **Aktif Taciz (`taciz = true`):** Radar arama modundan çıkmış, monopulse kilitlenme ile dar darbe ($PW \le 1.0\,\mu s$) ve yüksek PRF ile dost unsura kilitlenmiştir. NATO Kill-Chain'de **ENGAGE (Ateşleme)** safhasıdır; reaksiyon süresi **< 45 saniyedir**.

---

#### 3.2. Muhabere (COMINT) Kritik Düşman Yayınları
* 💬 **Operatör Doğal Dil Soruları:**
  * *"Öncelikli düşman muhabere yayınlarını ve kritik telsizleri getir."*
  * *"Kriptolu konuşan veya darbe patlama (burst) yapan düşman telsiz ağları var mı?"*
  * *"Telsiz karıştırması uygulayan düşman istasyonları hangileri?"*
* 🛠️ **Çağrılan Tool:** `queryPriorityComintThreats(Integer limit)`
* 📍 **Kaynak Kod Konumu:** [`ComintEmissionTools.java`](file:///c:/Users/onderproje/Desktop/AntigravitySpringAIDeneme4/ew-copilot-service/src/main/java/com/c2/ew/copilot/tools/ComintEmissionTools.java)

---

### KATEGORİ 4: Çok Kriterli Parametrik Filtreleme ve Listeleme (LIST Niyeti)

#### 4.1. Çok Kriterli Parametrik Radar Sorgusu (Spot No Destekli)
* 💬 **Operatör Doğal Dil Soruları:**
  * *"SP040 spot no'lu ilk 5 kaydı listele."*
  * *"X-Bandında (8000 - 12000 MHz) çalışan, PRI değeri 200 ile 800 mikro saniye arasındaki düşman radarlarını listele."*
  * *"Ege bölgesinde konuşlu atış kontrol radarlarını dök."*
* 🛠️ **Çağrılan Tool:** `queryRadarEmissions(spotNo, teshisKimlik, veriKaynagi, radarAdi, radarGorevi, hss, bolge, taciz, etUygulamaDurumu, platformOrtami, kayitTipi, minFrekansMhz, maxFrekansMhz, frekansTipi, minPriMicroSec, maxPriMicroSec, minPwMicroSec, maxPwMicroSec, minAtpMicroSec, maxAtpMicroSec, minGenlikDbm, maxGenlikDbm, zamanAraligi, baslangicZamani, bitisZamani, limit)`
* 📍 **Kaynak Kod Konumu:** [`RadarEmissionTools.java`](file:///c:/Users/onderproje/Desktop/AntigravitySpringAIDeneme4/ew-copilot-service/src/main/java/com/c2/ew/copilot/tools/RadarEmissionTools.java)
* ⚙️ **Koddaki Filtreleme ve SQL Mantığı:**
  JPA Criteria Specification üzerinde `cb.like(cb.upper(root.get("spotNo")), "%" + spotNo.toUpperCase() + "%")` ve parametrik aralık kesişimleri uygulanır.
  Sonuç: İlk N kayıt `TacticalQueryResult` DTO'su içinde döner, yanıtta Markdown tablosu üretilir ve operatöre tam liste için üst sekme yönlendirmesi yapılır.

---

#### 4.2. Çok Kriterli Parametrik Muhabere (COMINT) Sorgusu
* 💬 **Operatör Doğal Dil Soruları:**
  * *"VHF bandında Rusça konuşulan, kriptosuz düşman ses yayınlarını listele."*
  * *"DMR veya D-Star protokolü kullanan düşman telsiz yayınları hangileri?"*
  * *"VOLGA-01 çağrı adlı istasyonun son yayınlarını dök."*
* 🛠️ **Çağrılan Tool:** `queryComintEmissions(teshisKimlik, tip, lisan, haberlesmeSekli, kripto, minFrekansMhz, maxFrekansMhz, minGenlikDbm, maxGenlikDbm, bolge, protokol, cagriAdi, karsiCagriAdi, kayitTipi, zamanAraligi, limit)`
* 📍 **Kaynak Kod Konumu:** [`ComintEmissionTools.java`](file:///c:/Users/onderproje/Desktop/AntigravitySpringAIDeneme4/ew-copilot-service/src/main/java/com/c2/ew/copilot/tools/ComintEmissionTools.java)

---

### KATEGORİ 5: Çağrı Adı (Callsign) ve Telsiz Link-Ağ Analizleri

#### 5.1. Link-Pair İkili Temas Adetleri (Kim Kiminle Konuştu?)
* 💬 **Operatör Doğal Dil Soruları:**
  * *"Telsiz ağında kim kiminle konuştu? En çok temas kuran çağrı adı çiftlerini listele."*
  * *"KARTAL-1 çağrı adı ile ATMACA-4 arasında kaç defa görüşme yapılmış?"*
  * *"Son 24 saatte telsiz çiftleri ve konuşma adetleri nedir?"*
* 🛠️ **Çağrılan Tool:** `getCallsignPairInteractionStats(cagriAdi, karsiCagriAdi, baslangicZamani, bitisZamani, limit)`
* 📍 **Kaynak Kod Konumu:** [`ComintEmissionTools.java`](file:///c:/Users/onderproje/Desktop/AntigravitySpringAIDeneme4/ew-copilot-service/src/main/java/com/c2/ew/copilot/tools/ComintEmissionTools.java)
* 🖥️ **Arayüz (Frontend) Entegrasyonu:** Operatör bu analizi istediğinde sağ analiz panelinde **`CALLSIGN_HEATMAP` (Telsiz Etkileşim Isı Haritası)** otomatik açılır.

---

#### 5.2. En Aktif Çağrı Adları Sıralaması
* 💬 **Operatör Doğal Dil Soruları:**
  * *"Sahada en çok konuşan ilk 10 telsiz çağrı adını ve konuşma adetlerini getir."*
  * *"Haberleşme ağında en aktif istasyonlar hangileri?"*
* 🛠️ **Çağrılan Tool:** `getMostActiveCallsigns(baslangicZamani, bitisZamani, limit)`
* 📍 **Kaynak Kod Konumu:** [`ComintEmissionTools.java`](file:///c:/Users/onderproje/Desktop/AntigravitySpringAIDeneme4/ew-copilot-service/src/main/java/com/c2/ew/copilot/tools/ComintEmissionTools.java)

---

### KATEGORİ 6: Hiyerarşi & LOB Doktrini (Çocuklu/Çocuksuz Elips vs. Başıboş LOB)

#### 6.1. Radar (ELINT) Hiyerarşi ve LOB Analizi
* 💬 **Operatör Doğal Dil Soruları:**
  * *"Radar kestirim elipslerinden hangileri çocuklu, hangileri çocuksuz?"*
  * *"Henüz bir kestirime dahil olmamış başıboş (stray) radar LOB'larını listele."*
  * *"Doğrudan/harici kestirimler ile algılayıcı kestirimleri arasındaki oran nedir?"*
* 🛠️ **Çağrılan Tool:** `analyzeRadarHierarchy(zaman, tip, durum)`
* 📍 **Kaynak Kod Konumu:** [`RadarEmissionTools.java`](file:///c:/Users/onderproje/Desktop/AntigravitySpringAIDeneme4/ew-copilot-service/src/main/java/com/c2/ew/copilot/tools/RadarEmissionTools.java)
* 🎖️ **Askeri Doktrin ve Taktik Anlamı:**
  * **Çocuklu Elips (Childed):** Sahadaki 2 veya daha fazla yerel algılayıcının kerteriz kesişimiyle (AOA) doğrulanmış çoklu istasyon kestirimidir.
  * **Çocuksuz Elips (Childless):** Bağlı ham LOB kaydı bulunmayan, Taktik Veri Linki (Link-16/Link-22/JREAP) veya TDOA uydusundan doğrudan aktarılan kestirimlerdir.
  * **Başıboş LOB (Stray):** Kestirime dahil olmamış tekil algılayıcı yön çizgisidir (erken ihbar / kör nokta hattı).

---

### KATEGORİ 7: Görselleştirme ve Grafik Çizelgeleri (VISUAL Niyeti)

#### 7.1. Dalga Formu ve Zamansal Değişim (Waveform Timeline)
* 💬 **Operatör Doğal Dil Soruları:**
  * *"SP048 radarının dalga formu zamansal değişimini göster."*
  * *"SP040 hedefinin frekans ve PRI değişim grafiğini aç."*
  * *"VOLGA-01 telsizinin zaman içindeki frekans hareketini çiz."*
* 🛠️ **Çağrılan Tool:** `getTacticalWaveformTimeline(source, targetKey, timeRange)`
* 📍 **Kaynak Kod Konumu:** [`RadarEmissionTools.java`](file:///c:/Users/onderproje/Desktop/AntigravitySpringAIDeneme4/ew-copilot-service/src/main/java/com/c2/ew/copilot/tools/RadarEmissionTools.java)
* 🖥️ **Arayüz (Frontend) Entegrasyonu:** Sağ panelde **`WAVEFORM_TIMELINE` (ECharts Zaman Serisi)** widget'ı açılır; frekans, PRI ve PW değişimleri eşzamanlı görselleştirilir. *(Kullanıcı açıkça dalga formu veya grafik istemedikçe açılmaz).*

---

#### 7.2. Haftanın Günlerine Göre Dağılım (Örümcek Ağı / Spider Web Grafiği)
* 💬 **Operatör Doğal Dil Soruları:**
  * *"Haftanın günlerine göre LOB tespit sayısı örümcek ağı grafiğini ver."*
  * *"Günlere göre radar ve telsiz yayın dağılımını radar grafiğinde göster."*
  * *"Haftalık LOB ve kestirim elipsi dağılımı nedir?"*
* 🛠️ **Çağrılan Tool:** `getWeekdayEmissionDistribution(source, recordType, timeRange)`
* 📍 **Kaynak Kod Konumu:** [`RadarEmissionTools.java`](file:///c:/Users/onderproje/Desktop/AntigravitySpringAIDeneme4/ew-copilot-service/src/main/java/com/c2/ew/copilot/tools/RadarEmissionTools.java)
* 🖥️ **Arayüz (Frontend) Entegrasyonu:** Sağ panelde **`WEEKDAY_RADAR` (7 Kollu Apache ECharts Örümcek Ağı)** widget'ı açılır. Pazartesi-Pazar döngüsünde düşmanın operasyonel temposunu ortaya koyar.

---

#### 7.3. Sabit Frekans Dilimleri Spektrum Doluluk Oranı (Spectrum Occupancy)
* 💬 **Operatör Doğal Dil Soruları:**
  * *"Düşman radarlarının frekans spektrum doluluk tablosunu ve bar grafiğini getir."*
  * *"Son 24 saatte spektrumun hangi frekans dilimleri ne oranda işgal edilmiş?"*
* 🛠️ **Çağrılan Tool:** `getRadarSpectrumOccupancy(timeRange, teshisKimlik)`
* 📍 **Kaynak Kod Konumu:** [`RadarEmissionTools.java`](file:///c:/Users/onderproje/Desktop/AntigravitySpringAIDeneme4/ew-copilot-service/src/main/java/com/c2/ew/copilot/tools/RadarEmissionTools.java)
* 🖥️ **Arayüz (Frontend) Entegrasyonu:** Sağ panelde **`SPECTRUM_OCCUPANCY` (ECharts Bar Grafiği)** açılır.

---

#### 7.4. Kategorik Dağılım ve Taktik Pasta/Donut Grafiği
* 💬 **Operatör Doğal Dil Soruları:**
  * *"Radarların görev tiplerine göre dağılım pasta grafiğini çıkar."*
  * *"Telsiz lisan dağılımını halka (donut) grafik olarak göster."*
  * *"Düşman radarlarının polarizasyon dağılımı oranları nedir?"*
* 🛠️ **Çağrılan Tool:** `getTacticalDistribution(source, criterion, timeRange)`
* 📍 **Kaynak Kod Konumu:** [`RadarEmissionTools.java`](file:///c:/Users/onderproje/Desktop/AntigravitySpringAIDeneme4/ew-copilot-service/src/main/java/com/c2/ew/copilot/tools/RadarEmissionTools.java)
* 🖥️ **Arayüz (Frontend) Entegrasyonu:** Sağ panelde **`PIE_DISTRIBUTION` (ECharts Pasta/Donut Grafiği)** açılır.

---

### KATEGORİ 8: Benzerlik, Korelasyon ve 1-to-N Karşılaştırma

#### 8.1. İki Emisyonun Karşılaştırılması (1-to-1)
* 💬 **Operatör Doğal Dil Soruları:**
  * *"EMI-1001 ile EMI-1002 radarlarını karşılaştır."*
  * *"İki radar LOB'u arasındaki örtüşme oranı nedir?"*
* 🛠️ **Çağrılan Tool:** `compareRadarEmissions(id1, id2)`
* 📍 **Kaynak Kod Konumu:** [`RadarEmissionTools.java`](file:///c:/Users/onderproje/Desktop/AntigravitySpringAIDeneme4/ew-copilot-service/src/main/java/com/c2/ew/copilot/tools/RadarEmissionTools.java)

---

#### 8.2. Sahada En Çok Benzeyen Hedefleri Bulma (1-to-N)
* 💬 **Operatör Doğal Dil Soruları:**
  * *"Bu radara (EMI-1001) sahada en çok benzeyen ilk 5 hedefi listele."*
* 🛠️ **Çağrılan Tool:** `findSimilarRadarEmissions(targetId, timeRange, limit)`
* 📍 **Kaynak Kod Konumu:** [`RadarEmissionTools.java`](file:///c:/Users/onderproje/Desktop/AntigravitySpringAIDeneme4/ew-copilot-service/src/main/java/com/c2/ew/copilot/tools/RadarEmissionTools.java)
* ⚙️ **Koddaki Filtreleme ve Puanlama:**
  RF İmzası (%45) + Zaman Yakınlığı (%20) + Taktik Coğrafya (%35) formülüyle 100 puan üzerinden benzerlik skoru hesaplar.

---

## 4. GÜNCEL HIZLI REFERANS MATRİSİ (TAM LİSTE)

| # | Kategori | Tipik Operatör Sorusu | Tetiklenen Tool | Çıktı Tipi | Sağ Panel Widget'ı |
|---|---|---|---|---|---|
| 1 | **Skaler Radar Sayımı** | *"SP040 spot no lu kaç kayıt var?"* | `countRadarEmissions` | `TacticalCountResultDto` | **KAPALI (null)** |
| 2 | **Skaler Telsiz Sayımı** | *"Kaç adet kriptolu telsiz yayını var?"* | `countComintEmissions` | `TacticalCountResultDto` | **KAPALI (null)** |
| 3 | **Radar Makro Durum** | *"Genel radar istatistiklerini özetle"* | `getTacticalMacroStats` | Map İstatistik | **KAPALI (null)** |
| 4 | **Telsiz Makro Durum** | *"Telsiz trafiği özetini çıkar"* | `getComintMacroStats` | Map İstatistik | **KAPALI (null)** |
| 5 | **Kritik Radar Tehdidi** | *"En kritik düşman radarlarını listele"* | `queryPriorityThreats` | Liste (SummaryDto) | **KAPALI (null)** |
| 6 | **Kritik Telsiz Tehdidi** | *"Kritik düşman telsiz ağlarını getir"* | `queryPriorityComintThreats` | Liste (SummaryDto) | **KAPALI (null)** |
| 7 | **Parametrik Radar (List)**| *"SP040 olan kayıtları listele"* | `queryRadarEmissions` | `TacticalQueryResult` | **KAPALI (null)** |
| 8 | **Parametrik Telsiz (List)**| *"VHF bandında Rusça ses yayınlarını dök"* | `queryComintEmissions` | `TacticalQueryResult` | **KAPALI (null)** |
| 9 | **Link-Pair Analizi** | *"Kim kiminle konuştu? Temas adetleri"* | `getCallsignPairInteractionStats`| Liste (PairStatsDto) | **AÇIK (`CALLSIGN_HEATMAP`)** |
| 10 | **En Aktif Çağrı Adı** | *"En çok konuşan telsiz istasyonları"* | `getMostActiveCallsigns` | Map Sıralama | **AÇIK (`CALLSIGN_HEATMAP`)** |
| 11 | **Trafik Trendi / Surge**| *"Telsiz trafiğinde ani artış var mı?"*| `getComintTrafficTrend` | Trend İstatistik | **KAPALI (null)** |
| 12 | **Radar Hiyerarşisi** | *"Çocuklu elipsler ve başıboş LOB'lar"*| `analyzeRadarHierarchy` | Hiyerarşi Özeti | **KAPALI (null)** |
| 13 | **Telsiz Hiyerarşisi** | *"Başıboş telsiz LOB'ları hangileri?"* | `analyzeComintHierarchy` | Hiyerarşi Özeti | **KAPALI (null)** |
| 14 | **Dalga Formu Çizelgesi**| *"SP048 radarının dalga formu değişimi"* | `getTacticalWaveformTimeline` | Zaman Serisi DTO | **AÇIK (`WAVEFORM_TIMELINE`)** |
| 15 | **Haftalık Örümcek Ağı** | *"Haftanın günlerine göre LOB dağılımı"*| `getWeekdayEmissionDistribution`| Hafta Dağılım DTO | **AÇIK (`WEEKDAY_RADAR`)** |
| 16 | **Spektrum Doluluk** | *"Düşman radarlarının frekans doluluk oranı"* | `getRadarSpectrumOccupancy` | Spektrum DTO | **AÇIK (`SPECTRUM_OCCUPANCY`)** |
| 17 | **Taktik Pasta Dağılımı**| *"Radar görev tiplerinin pasta dağılımı"* | `getTacticalDistribution` | Dağılım DTO | **AÇIK (`PIE_DISTRIBUTION`)** |
| 18 | **1-to-1 Karşılaştırma** | *"EMI-1001 ile EMI-1002'yi karşılaştır"* | `compareRadarEmissions` | Korelasyon Raporu | **KAPALI (null)** |
| 19 | **1-to-N Benzerlik** | *"Bu radara sahada en çok benzeyen 5 hedef"*| `findSimilarRadarEmissions` | Top-K Sıralama | **KAPALI (null)** |
| 20 | **Tekil Radar Kartı** | *"RAD-0005 radarının tüm teknik detayları"* | `getEmissionDetails` | Detay Kartı DTO | **KAPALI (null)** |
| 21 | **Tekil Telsiz Kartı** | *"COM-0018 telsizinin tüm teknik detayları"*| `getComintDetails` | Detay Kartı DTO | **KAPALI (null)** |
| 22 | **Detaylı Karıştırma** | *"ET etkinlik ve karıştırma durumu raporu"*| `queryElectronicAttackAndHarassment` | Durum Raporu | **KAPALI (null)** |
