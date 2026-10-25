# 📋 Taktik EH Komuta Kontrol Sistemi - Geliştirme ve İyileştirme TODO Listesi

Bu doküman, sistemde daha sonra ele alınmak üzere beklemeye alınan teknik tespitleri, kök neden analizlerini ve planlanan çözüm adımlarını içerir.

---

## 📌 TODO-01 (Madde 4): Sinyal Genliği (dBm) Sorguları ve LLM Eşleme İyileştirmesi

### 1. Belirtilen Sorun
Operatör taktik asistana *"genliği -80 dBm'den küçük kaç tane radar var"* sorusunu yönelttiğinde, veritabanında gerçekte **11 adet** kayıt olmasına rağmen asistan *"5 adet tespit edilmiştir"* şeklinde eksik yanıt vermektedir.

### 2. Kök Neden Analizi
- **LLM Parametre Eşleyememe Sorunu:** `RadarEmissionTools.queryEmissions` aracında `minGenlikDbm` ve `maxGenlikDbm` parametreleri tanımlıdır. Ancak LLM, negatif desibel (dBm) mantığında "-80 dBm'den küçük/zayıf" ifadesinin matematikte `maxGenlikDbm = -80.0` anlamına geldiğini çıkaramamıştır.
- **Numune Üzerinden Sayma:** LLM bu parametreyi araca geçiremeyince `queryEmissions(limit=10)` çağrısı yaparak filtre koymadan ilk 10 kaydı çekmiş; gelen bu 10 kayıttan tesadüfen 5 tanesinin genliği -80'den küçük olduğu için veritabanının tamamı yerine elindeki 10 kaydı sayarak "5 adet" demiştir.

### 3. Planlanan Çözüm Adımları
1. **Açıklayıcı ToolParam Etiketleri:**
   `RadarEmissionTools` ve `ComintEmissionTools` içindeki genlik parametrelerine açık `@ToolParam` açıklamaları eklenecek:
   - `minGenlikDbm`: *"Sinyal gücü alt sınırı (dBm). Örneğin '-50 dBm'den güçlü/büyük sinyaller' istendiğinde minGenlikDbm = -50.0 verilmelidir."*
   - `maxGenlikDbm`: *"Sinyal gücü üst sınırı (dBm). Örneğin '-80 dBm'den küçük/zayıf sinyaller' istendiğinde maxGenlikDbm = -80.0 verilmelidir."*
2. **Sistem Prompt (Direktif) Kuralı:**
   `TacticalAgentService` sistem yönergesine negatif dBm filtreleme kuralı eklenerek LLM'in genel sayaç sorgularında `queryEmissions` genlik parametrelerini doğru doldurması garanti altına alınacak.
3. **Makro İstatistik Genişletmesi:**
   Gerektiğinde `getTacticalMacroStats` içerisine genlik seviye dağılımı (Zayıf: <-80 dBm, Orta: -80..-60 dBm, Güçlü: >-60 dBm) eklenecek.

---

## ✅ TAMAMLANDI - Madde 6: Tablolarda Frekans Sıralama Mantığı ve Düzeltmesi

### 1. Durum: **ÇÖZÜLDÜ VE DOĞRULANDI**
Operatörün *"Tabloda frekansa göre sıralama neye göre yapılıyor?"* sorusu yanıtlanmış ve sıralama altyapısı eksiksiz çalışır hale getirilmiştir.

### 2. Yapılan İşlemler ve Çözüm:
1. **Sıralama Kriteri Netleştirildi:** Radarlar frekans aralığına sahip olduğundan, sıralamanın yayının başlangıç frekansı olan **`minFrekansMhz`** değerine göre yapıldığı tespit edildi ve doğrulandı.
2. **Kolon dataIndex Eşleştirmesi:** Arayüzdeki (React/AntD) Frekans kolonuna `dataIndex: 'minFrekansMhz'` ve `sorter: true` tanımlandı.
3. **Backend Dinamik JPA Sort:** `RadarEmissionTools.getEmissionsPage` metodunda `minFrekansMhz` alanı JPA `Sort` kriterlerine dinamik olarak bağlandı.
4. **E2E Doğrulaması (TC-02):** Canlı tarayıcı testinde (TC-02) Artan (ASC: 152.0 MHz - Nebo-M) ve Azalan (DESC: 10998.0 MHz - AN/APG-81) sıralamanın hatasız çalıştığı doğrulanmıştır.

---

## 📌 TODO-03 (Madde 9): Radar VHF/UHF ve Frekans Bant Tipine Göre Adet/Filtre Sorguları

### 1. Belirtilen Sorun
Asistana *"VHF radarlarının adedi"* veya *"X-band radar sayısı"* sorulduğunda hatalı yanıt vermektedir. Toolda bu parametrenin eksik olduğu gözlemlenmiştir.

### 2. Kök Neden Analizi
- **Veri Modeli Farkı:** Muhabere (COMINT) tablosunda `tip` kolonu enum olarak (`HF`, `VHF`) doğrudan mevcuttur. Ancak Radar tablosunda bant tipi bir metin kolonu olarak değil, MHz cinsinden sayısal frekans aralığı (`minFrekansMhz` - `maxFrekansMhz`) olarak tutulmaktadır.
- Bu nedenle LLM radarları sorgularken `tip=VHF` gibi bir parametreyi `queryEmissions` aracında bulamamaktadır.

### 3. Planlanan Çözüm Adımları
1. **Radar Tool'una Bant Parametresi Eklenmesi:**
   `RadarEmissionTools.queryEmissions` metoduna doğrudan `frekansBandi` (`VHF`, `UHF`, `L_BAND`, `S_BAND`, `C_BAND`, `X_BAND`, `KU_BAND`) parametresi eklenecek.
2. **Otomatik Frekans Aralığı Çeviricisi:**
   Tool katmanında girilen bant adı otomatik olarak standart askeri/NATO frekans karşılıklarına dönüştürülecek:
   - `VHF` -> 30 MHz - 300 MHz
   - `UHF` -> 300 MHz - 1000 MHz
   - `L-Band` -> 1000 MHz - 2000 MHz
   - `S-Band` -> 2000 MHz - 4000 MHz
   - `C-Band` -> 4000 MHz - 8000 MHz
   - `X-Band` -> 8000 MHz - 12000 MHz
   - `Ku-Band` -> 12000 MHz - 18000 MHz
3. **Makro İstatistiğe Bant Dağılımı Eklenmesi:**
   `getTacticalMacroStats` çıktısına frekans bantlarına göre radar sayıları eklenerek asistanın tek sorguda tüm bant adetlerini doğru söylemesi sağlanacak.

---

## 📌 TODO-04: LLM Tarafından Uydurulan / Karıştırılan Radar ID (Hallucination) Tespiti ve Önleme Mekanizması

### 1. Belirtilen Sorun
Operatör Copilot'a ardışık filtreleme sorguları yönelttiğinde (örn. *"Dwell-switch PRI kullanan ve frekansı 10 GHz üstü olan radarları listele"*, ardından *"emin misin"*), Copilot yanıtta **`EMI-E48A2C91B87F4A12`** gibi bir ID listelemektedir. Ancak bu ID radar tablosunda aratıldığında bulunamamakta ve doğrudan API çağrısında `500 Internal Server Error (Emisyon bulunamadı)` hatası alınmaktadır.

### 2. Kök Neden Analizi
- **Karmaşık Hex UUID Formatı:** 200.000 kayıt eklendiğinde `MassDataSeederService` tarafından üretilen ID'ler 16 karakterli rasgele hex dizileridir (örn. `EMI-54F460E240034833`).
- **LLM Bağlam Karışması & Halüsinasyon:** LLM (Gemini), ardışık sorgularda onlarca emisyon detayını bağlam penceresine alıp işlerken, gerçekte veritabanında var olan bir ID'nin (örn. `EMI-E48A8D9D5C9B428A`) karakterlerini belleğinde karıştırmış ve desen benzerliğinden ötürü veritabanında fiziksel olarak var olmayan hayali bir ID (`EMI-E48A2C91B87F4A12`) üretmiştir.
- **Hata Yönetimi (Status 500):** Olmayan bir ID arandığında backend'in `404 Not Found` yerine `500 Internal Server Error` fırlatması arayüzde sessiz hataya veya kilitlenmeye neden olmaktadır.

### 3. Planlanan Çözüm Adımları
1. **Agent Sistem Yönergesi (Prompt Guardrail):**
   `TacticalAgentService.java` içindeki `SYSTEM_DIRECTIVE` determinizm maddesine katı bir kural eklenecek:
   > *"ASLA kafandan, tahminden veya benzerlikten `EMI-xxxx` veya `COM-xxxx` kimliği türetme/uydurma! Yalnızca sana tool'ların döndürdüğü JSON nesnelerindeki tam 'id' değerlerini birebir kopyalayarak yanıtta kullan."*
2. **API Hata Yönetimi (`404 Not Found` & Global Exception Handler):**
   `TacticalEmissionEntity` veya `TacticalComintEntity` bulunamadığında `IllegalArgumentException` yerine `ResponseStatusException(HttpStatus.NOT_FOUND)` veya `@ControllerAdvice` ile temiz HTTP 404 dönülmesi; arayüzün kullanıcıya "Kayıt veritabanında bulunamadı" uyarısı göstermesi sağlanacak.
3. **Format Standartlaştırması:**
   Büyük ölçekli veri üretimlerinde LLM'in akılda tutmasını ve token üretimini kolaylaştıracak daha okunabilir/yapısal ID desenleri (örn: `EMI-T-XXXXXX`) değerlendirilecek.

---

## ✅ TAMAMLANDI - Madde 10: SIGINT/EW Doktrini - Ham LOB (Kerteriz) ve Kestirim (Hata Elipsi) Mimarisi

### 1. Durum: **MİMARİ VE UYGULAMA TAMAMLANDI, CANLI VERİDE DOĞRULANDI**
Operatörün belirttiği gerçekçi Elektronik Harp ve SIGINT doktrini sisteme kazandırılmıştır:
- Tüm radar ve muhabere yayınları **`KESTIRIM_ELIPS` (Cross-Fix / Elips)** veya **`HAM_LOB` (Line of Bearing / Kerteriz)** olarak sınıflandırılmıştır.
- **`HAM_LOB` Kayıtları:** Tekil DF algılayıcı yön kestirimidir. Hedef elips koordinatları (`yayin_enlem`, `yayin_boylam`, elips eksenleri ve yönelim) KESİNLİKLE `NULL` yapılmıştır.
- **İlişkili LOB:** Bir kestirimin hesabında kullanılan LOB'lar `kestirim_id` üzerinden o kestirime (`EMI-xxxx` veya `COM-xxxx`) bağlanmıştır.
- **Başıboş (Orphan) LOB:** Henüz herhangi bir kestirim algoritmasına girmemiş, kestirilmeyi bekleyen münferit yön ölçümlerinde `kestirim_id` değeri `NULL` olarak tutulmaktadır.
- **`KESTIRIM_ELIPS` Kayıtları:** Tam hata elipsi koordinatlarına sahiptir ve arka planda kendisine katkı sunan tüm bağlı LOB kimliklerini (`iliskiliLobIdListesi`) barındırır.

### 2. Yapılan İşlemler ve Doğrulama
1. **Veritabanı Şeması & Migrasyon:**
   - PostgreSQL `tactical_ew_db_3` üzerinde `kayit_tipi` ve `kestirim_id` kolonları ile B-Tree indeksleri oluşturuldu.
   - 402.280 taktik kaydın tamamında LOB kayıtlarının elips koordinatları sıfırlandı; demo ve operasyonel zincirler (`EMI-1001` <- `EMI-1002`, `EMI-1003` vb.) kuruldu.
2. **Backend & Tooling:**
   - `KayitTipi` enum'ı oluşturuldu. `TacticalEmissionEntity`, `TacticalComintEntity`, JPA Repository'leri ve 4 adet DTO güncellendi.
   - `RadarEmissionTools` ve `ComintEmissionTools` üzerinden `kayitTipi` filtresi, sıralama ve çift yönlü detay sorguları eklendi.
   - `TacticalController` REST uç noktaları (`/emissions`, `/comints`, `/emissions/{id}`, `/comint/{id}`) güncellendi.
3. **Frontend (React/AntD):**
   - Radar ve COMINT tablolarına `[KESTİRİM]` ve `[HAM LOB]` etiketleri ile `Kayıt Niteliği` filtre açılır menüsü eklendi.
   - Detay çekmecesinde (`DetailDrawer`) doktrin panelleri geliştirildi: Kestirim incelenirken katkı veren LOB'lar tıklanabilir etiketler olarak listelenmekte; bir LOB incelenirken bağlı olduğu kestirime doğrudan tıklanarak geçiş yapılabilmektedir.

---

## ✅ TAMAMLANDI - TODO-05: Türkçe Karakter (Collation) Mimarisi ve ICU Desteği

### 1. Belirtilen Sorun ve Kök Neden
* **Sorun:** Radar filtresinde veya aramalarda *"Erken İhbar"* seçildiğinde, veritabanında yüz binlerce kayıt olmasına rağmen 0 kayıt dönmesi.
* **Kök Neden:** 
  1. PostgreSQL veritabanı Docker'da varsayılan collation (`en_US.utf8`) ile açıldığı ve tablolardaki metin kolonlarında özel bir collation tanımlanmadığı için `lower('I')` ve `lower('İ')` dönüşümlerinin Türk alfabesine uymaması.
  2. Java'nın varsayılan İngilizce Locale altında `"İ"` harfini küçük harfe çevirirken Unicode decomposed combining dot (`i` + `\u0307`) üretmesi, bunun da veritabanındaki tekil `i` karakteriyle bayt düzeyinde uyuşmaması.

### 2. Uygulanan Çözüm (PostgreSQL ICU Collation + JPA Entity + Java Normalizasyon)
* PostgreSQL yerleşik ICU motoru kullanılarak `tactical_emissions` ve `tactical_comint` tablolarındaki tüm metin kolonları `COLLATE "tr-TR-x-icu"` ile güncellendi.
* `TacticalEmissionEntity` ve `TacticalComintEntity` JPA entity sınıflarında ilgili `@Column` alanlarına `columnDefinition = "... collate \"tr-TR-x-icu\""` tanımlandı.
* Java tarafında (`RadarEmissionTools` ve `ComintEmissionTools`) Türkçe arama normalizasyonu (`Locale.forLanguageTag("tr-TR")`) eklendi.
* **Canlı Test Sonucu:** Arayüzden *"Erken İhbar"* seçildiğinde **337.244 adet kayıt**, *"Atış Kontrol"* seçildiğinde **90.038 adet kayıt** eksiksiz ve anında listelenmektedir. Tüm büyük/küçük harf varyasyonları (%100) doğrulanmıştır.

### 3. İleride Tekrar Değerlendirilecek Mimari Kararlar:
1. **Yol A (Savunma Sanayii / NATO Standardı - Enum Dönüşümü):**
   - Taktik görevlerin serbest String (`"Erken İhbar"`) yerine `RadarGorevi` enum'ına (`ERKEN_IHBAR`, `ATIS_KONTROL`, `HEDEF_TAKIP`, `ARAMA`) dönüştürülmesi.
   - Veritabanında `VARCHAR` enum kodu tutulup, ekranda operatöre Türkçe başlık gösterilmesi.
   - **Avantajı:** İşletim sistemi (Windows/Linux), Docker, PostgreSQL collation ve harf dönüştürme mekanizmalarından tamamen bağımsız, %100 deterministik ve hatasız mimari.
2. **Yol B (Mevcut ICU Collation Devamı):**
   - `tr-TR-x-icu` collation'ının büyük veri hacimlerinde (500K+ kayıt) B-Tree indeks performansı ve bellek kullanımının izlenmesi.

---

## ✅ TAMAMLANDI - TODO-06: Callsign (Çağrı Adı) İkili Konuşma Matrisi ve Isı Haritası (ECharts Adjacency Matrix)

### 1. Durum: **MİMARİ VE UYGULAMA TAMAMLANDI, CANLI VERİDE DOĞRULANDI**
Operatörün telsiz çağrı adı ikililerini matrisel ve görsel ısı haritası olarak inceleme ihtiyacı eksiksiz karşılanmıştır:
- **Backend Matris Servisi:** `/api/v1/tactical/comint/callsign-matrix` uç noktası üzerinden `SON_1_SAAT`, `SON_6_SAAT`, `SON_24_SAAT` ve `ALL` zaman aralıklarında en aktif 15, 25 veya 40 çağrı çiftinin toplam konuşma sayısı, süre, VHF/HF frekans oranları ve kripto baskınlığı hesaplanmaktadır.
- **Frontend ECharts Cartesian Isı Haritası (`CallsignHeatmapWidget.jsx`):**
  - **İki Farklı Renk Skalası:** Aşırı değer sapmalarını engelleyen 5 katmanlı akıllı kademe (`PIECEWISE` Quintile) ve kesintisiz termal geçiş (`CONTINUOUS`).
  - **Kalıcı DOM ve Boyutlandırma:** `minHeight: 340px` ve geçikmeli `resize` (50ms, 250ms) yaşam döngüsüyle panel geçiş animasyonunda sıfır piksel hatası giderilmiştir.
  - **Büyütülmüş Modal İnceleme:** Tam ekran modal (`afterOpenChange` ve callback ref) ile 40x40 çiftlik devasa ağ matrisi donmadan incelenebilmektedir.

---

## ✅ TAMAMLANDI - TODO-07: Spektrum Doluluk & Taktik Görselleştirici Widget Entegrasyonu

### 1. Durum: **GELİŞTİRİLDİ, SİSTEME ENTEGRE EDİLDİ VE DOĞRULANDI**
Karar vericinin taktik resme anında hakim olması için çekirdek görselleştiriciler Copilot arayüzüne entegre edilmiştir:
1. **🔥 Callsign Heatmap Widget:** COMINT komuta ağı ve telsiz konuşma yoğunluğu matrisi.
2. **🥧 Tactical Distribution Pie Widget:** Radar ve Muhabere verilerinde 11+ kriterde pasta, halka (donut) ve gül (Nightingale rose) dağılımları. ECharts pie-simple standardına uygun dış etiketler ve sayfalara ayrılmış yatay lejant.
3. **📈 Tactical Waveform Timeline Widget:** Hedef bazlı zaman serisi; frekans/PRI/PW min-max bant zarfı ve sinyal gücü (dBm) çift eksenli senkron grafiği.
4. **📊 Radar Spectrum Occupancy Widget:** 50 MHz, 100 MHz ve 200 MHz sabit frekans dilimlerinde radar yayınlarının örtüşme (Union) analizi, çoklu teşhis (Düşman, Dost, Tarafsız, Bilinmeyen) yığılmış çubuk (stacked bar) ve sıralanabilir taktik tablo görünümü.
5. **🧭 Sola Yanaşık Taktik Menü Çubuğu:** Taktik operasyon ergonomisine uygun olarak analiz butonları en sola yanaşık hizalanmış; sağında ayraç ve hızlı eylem butonları konumlandırılmıştır.

---

## ✅ TAMAMLANDI - TODO-08: Haftanın Günlerine Göre LOB ve Kestirim Dağılımı (Örümcek Ağı / Radar Grafiği)

### 1. Durum: **MİMARİ VE UYGULAMA TAMAMLANDI, E2E DOĞRULANDI**
Operatörün haftalık LOB ve kestirim sayılarını 7 kollu örümcek ağı / radar grafiğinde (`https://echarts.apache.org/examples/en/editor.html?c=radar`) inceleme talebi eksiksiz gerçekleştirilmiştir:
1. **Veri Modeli & Servis Mimarisi (`WeekdayRadarDistributionDto` & `TacticalWeekdayRadarService`):**
   - PostgreSQL üzerinde `EXTRACT(ISODOW FROM COALESCE(ilk_tespit_zamani, son_tespit_zamani))::int` kullanılarak 1 (Pazartesi) .. 7 (Pazar) indeksleme yapıldı.
   - Tek geçişte koşullu toplama (`COUNT(*) FILTER (WHERE kayit_tipi = 'HAM_LOB')` ve `COUNT(*) FILTER (WHERE kayit_tipi = 'KESTIRIM_ELIPS')`) ile 940.000+ kayıt üzerinden mikrosaniye seviyesinde yüksek performanslı analitik sorgu üretildi.
   - Tavan eksen ölçeği (`maxIndicatorValue`) dinamik olarak `1.15x` ile yuvarlanarak grafik poligonlarının taşması engellendi.
2. **REST API Uç Noktası:**
   - `GET /api/v1/tactical/analytics/weekday-radar?source=TUMU&recordType=TUMU&timeRange=ALL` eklendi.
   - Bağımsız boyut filtreleri desteklendi: `source` (`TUMU`, `RADAR`, `COMINT`), `recordType` (`TUMU`, `SADECE_LOB`, `SADECE_KESTIRIM`), `timeRange` (`ALL`, `SON_7_GUN`, `SON_30_GUN`).
3. **Spring AI Agent Araç Çağrısı (`@Tool` & Directives):**
   - `RadarEmissionTools.getWeekdayEmissionDistribution` aracı eklendi.
   - `TacticalAgentService` sistem yönergesine Kural 13 eklendi.
   - Operatör *"haftanın günlerine göre radar ya da muhabere kestirim/ LOB sayısını grafiğini ver"* sorduğunda Copilot deterministik olarak bu aracı tetiklemekte ve brifing sunmaktadır.
4. **Frontend Apache ECharts Radar Bileşeni (`WeekdayRadarDistributionWidget.jsx`):**
   - Ant Design Segmented kontrolleri ile Kaynak (Tüm Veriler, Radar, Muhabere), Kayıt Niteliği (Tümü, Sadece LOB, Sadece Kestirim) ve Zaman filtresi eklendi.
   - ECharts Radar bileşeni ile 7 günün kolları (Pazartesi .. Pazar), çift renkli poligonlar (Radar: Cyan `#38bdf8`, Muhabere: Mor `#c084fc`, yarı saydam taktik dolgular) tasarlandı.
   - Sayısal KPI kartları (Toplam Radar, Toplam Muhabere, Genel Toplam) ve tablo/grafik görünüm geçişi sağlandı.
   - Tam ekran projeksiyon modalı entegre edildi.
---

## ✅ TAMAMLANDI - TODO-09: Canlı Veritabanı ve Sorgu Performans İzleme Paneli (pg_stat_statements Profiler)

### 1. Durum: **MİMARİ VE UYGULAMA TAMAMLANDI, DOĞRULANDI**
Operatörün ve yöneticinin uygulama koduna müdahale etmeden PostgreSQL/PostGIS sorgu performansını, tam tablo taramalarını (Seq Scan), indeks kullanım oranlarını ve yavaş sorguları gerçek zamanlı izleme ihtiyacı eksiksiz karşılanmıştır:

1. **Backend Servis & API Katmanı (`ew-emission-service`):**
   - [DatabaseMetricsDto.java](file:///c:/Users/onderproje/Desktop/AntigravitySpringAIDeneme4/ew-emission-service/src/main/java/com/c2/ew/emission/dto/DatabaseMetricsDto.java): Kapsamlı özet KPI'lar, tekil SQL şablonları, tablo taramaları ve indeks kullanım istatistikleri.
   - [DatabaseMonitoringService.java](file:///c:/Users/onderproje/Desktop/AntigravitySpringAIDeneme4/ew-emission-service/src/main/java/com/c2/ew/emission/service/DatabaseMonitoringService.java): `pg_stat_statements`, `pg_stat_user_tables`, `pg_stat_user_indexes` ve `pg_stat_activity` görünümlerinden canlı profil toplayan ve `SELECT pg_stat_statements_reset()` ile sayaç sıfırlayan servis.
   - [DatabaseMonitoringController.java](file:///c:/Users/onderproje/Desktop/AntigravitySpringAIDeneme4/ew-emission-service/src/main/java/com/c2/ew/emission/web/DatabaseMonitoringController.java): 
     - `GET /api/v1/tactical/db-monitor/stats` (limit ve dinamik sıralama: `mean_time`, `total_time`, `calls`, `max_time`)
     - `POST /api/v1/tactical/db-monitor/reset` (sayaç sıfırlama)
2. **Frontend Taktik Görselleştirici Bileşeni (`DatabaseMonitorWidget.jsx`):**
   - **Özet KPI Kartları:** Toplam Yürütülen Çağrı, Ortalama Yürütme Süresi (ms), 50ms Üzeri Yavaş Sorgu Sayısı, Buffer Cache Hit Oranı (%) ve Aktif DB Bağlantısı.
   - **Tab 1 - Sorgu Performansı:** Normalize edilmiş SQL şablonları, çağrı adetleri, ortalama süre (Yeşil < 10ms, Turuncu 10-50ms, Kırmızı > 50ms), pik süre, satır sayısı ve cache hit progress barları. SQL şablonuna tıklandığında açılan tam detay modalı ve panoya kopyalama özelliği.
   - **Tab 2 - Tablo Taramaları ve İndeks Verimliliği:** Seq Scan vs Index Scan karşılaştırması ve `%95+` verimlilik doğrulaması.
   - **Tab 3 - İndeks Kullanım İstatistikleri:** Hangi B-Tree ve PostGIS GiST indeksinin kaç kez tarandığı ve disk boyutları (`pg_size_pretty`).
   - **Kontroller:** Otomatik yenileme switch'i (5s), canlı yenile butonu, sıralama ve limit filtreleri, güvenli sıfırlama popconfirm'i.
3. **MFE ve Telemetri Entegrasyonu:**
   - Hem ortak kabukta ([ComposerLayout.jsx](file:///c:/Users/onderproje/Desktop/AntigravitySpringAIDeneme4/frontend/src/composer/ComposerLayout.jsx)) ana menüde **`⚡ DB & Sorgu İzleme`**,
   - Hem de bağımsız telemetri konsolunda ([App.jsx](file:///c:/Users/onderproje/Desktop/AntigravitySpringAIDeneme4/frontend-emission/src/App.jsx)) 3. sekme olarak operatörün kullanımına sunulmuştur.





