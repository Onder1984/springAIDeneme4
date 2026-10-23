# 🛡️ Taktik Elektronik Harp (EH / C2) Sistemi: Çift Mikroservis, Çift Bağımsız Web Uygulaması ve Spring AI Mimari Akış Dokümanı

**Doküman Versiyonu:** 4.0.0 (Dikey Ayrık Tam Mikroservis & Çift Bağımsız Web Uygulaması Sürümü)  
**Tarih:** 2026-10-21  
**Hedef Kitle:** Yazılım Mimarları, Sistem Mühendisleri, Elektronik Harp ve C2 Karar Destek Geliştiricileri  

---

## 1. YÖNETİCİ ÖZETİ VE MİMARİ VİZYON

Bu doküman; **Taktik Elektronik Harp (EH / SIGINT / C2)** harekât sahasında görev yapan karar vericiler ve operatörler için geliştirilmiş olan **yapay zekâ tabanlı sorgulama, karar destek ve durumsal farkındalık platformunun** çift mikroservis ve **çift bağımsız web uygulaması (Self-Contained Systems - SCS)** mimarisini, veri akışını ve Spring AI orkestrasyonunu en ince teknik ayrıntısına kadar açıklar.

Sistem; geleneksel monolitik yapay zekâ uygulamalarının getirdiği performans darboğazlarını, gereksiz bundle yükünü ve ön yüzdeki state karmaşasını aşmak amacıyla **iki bağımsız backend mikroservisi** ve bunlara doğrudan bağlı **iki bağımsız ön yüz web uygulaması** olarak dikey kesitler (Vertical Slices) halinde yapılandırılmıştır:

```text
┌──────────────────────────────────────────────┐       ┌──────────────────────────────────────────────┐
│        TAKTIK YZ COPILOT KOKPİTİ             │       │      TAKTIK EH TELEMETRİ KONSOLU             │
│        frontend-copilot (Port 3002)          │       │      frontend-emission (Port 3001)           │
│                                              │       │                                              │
│ 🤖 Spring AI & Google Gemini Chat            │       │ 📡 Radar (ELINT) Sanal Tablo                 │
│ 📊 ECharts Analitik Dağılım Widget'ları      │       │ 📻 Muhabere (COMINT) Sanal Tablo             │
│ 🛡️ Sabit Adımlı Haftalık Örümcek Ağı Grafiği │       │ 🔍 LOB / Kestirim Detay Çekmecesi (Drawer)   │
└──────────────────────┬───────────────────────┘       └──────────────────────┬───────────────────────┘
                       │                                                      │
                       │ REST / JSON                                          │ REST / JSON
                       │ Port 8082 (Proxy)                                    │ Port 8081 (Proxy)
                       ▼                                                      ▼
┌──────────────────────────────────────────────┐       ┌──────────────────────────────────────────────┐
│             ew-copilot-service               │       │             ew-emission-service              │
│                (Port 8082)                   │       │                 (Port 8081)                  │
│                                              │       │                                              │
│ 🤖 Spring AI 1.x                             │       │ 🛡️ PostgreSQL 18 + PostGIS Veri Deposu       │
│ 🧠 Google Gemini AI Engine                   │  REST │ ⚡ 521.000+ Radar, 519.000+ COMINT Kaydı     │
│ 🛠️ @Tool Fonksiyonları (Radar / Comint Tools) ├──────>│ 🔍 JPA Specification & Dinamik Criteria API  │
│ 🎯 Skaler / Liste Niyet Ayrıştırma           │       │ 📊 SQL COUNT(*) ve Agregasyon Motoru         │
│ 💬 Chat Memory (Oturum Yönetimi)             │       │ 📈 Korelasyon, Spektrum & Dağılım Servisleri │
└──────────────────────────────────────────────┘       └──────────────────────────────────────────────┘
```

---

## 2. SERVİS SINIRLARI VE SORUMLULUK MATRİSİ (BOUNDED CONTEXTS)

Sistem Domain-Driven Design (DDD) ve Self-Contained Systems (SCS) ilkeleri doğrultusunda tam ayrık dikey katmanlara bölünmüştür:

| Boyut / Yetkinlik | `ew-emission-service` (Veri Servisi) | `frontend-emission` (Telemetri Konsolu) | `ew-copilot-service` (AI Karar Destek) | `frontend-copilot` (AI Kokpiti) |
| :--- | :--- | :--- | :--- | :--- |
| **Rol & Amaç** | Ham telemetri ve feda edilemez taktik verinin kalıcılığı, indekslenmesi, PostGIS geometrisi ve hızlı sorgulanması. | Algılayıcı ve veri operatörlerine hızlı, hafif ve odaklı Radar/COMINT telemetri tabloları ve detay çekmecesi sunma. | Doğal dil girdisini anlama, askeri doktrine göre niyet sınıflandırma, @Tool çağrıları üretme ve taktik brifing sentezleme. | Karar verici ve komutanlara sadece yapay zekâ sohbeti, analiz widget'ları ve taktik brifingler sunma. |
| **Çalışma Portu** | `http://localhost:8081` | `http://localhost:3001` | `http://localhost:8082` | `http://localhost:3002` |
| **Temel Teknolojiler** | Spring Boot 3.3+, Spring Data JPA, Hibernate 6, PostgreSQL 18, PostGIS, HikariCP. | React 19, Vite 8, Ant Design 6 (MIL-Dark), Axios, Dayjs. | Spring Boot 3.3+, Spring AI 1.x, Google Gemini (Flash-Lite / Pro), RestClient. | React 19, Vite 8, Ant Design 6, Apache ECharts, Marked, Axios. |
| **Ön Yüz Bundle Yükü** | - | **~1.1 MB** (Çok Hafif, ECharts/Marked yükü YOK). | - | **~2.3 MB** (AI Markdown ve ECharts görselleştirme motorları). |
| **Hedef Kullanıcı** | Backend API tüketicileri | Algılayıcı / Radar / Telsiz Operatörleri | Yapay Zekâ İstemcileri | Taktik Karar Vericiler / EH Komutanları |

---

## 3. BACKEND MİMARİSİ VE SERVİSLER ARASI VERİ AKIŞI

### 3.1. `ew-emission-service` (Yayın ve Veri Servisi) Mimarisi

`ew-emission-service`, harekât sahasında konuşlu yerel algılayıcılardan (KORAL, REDET vb.) ve harici taktik veri linklerinden (Link-16, Link-22, JREAP) akan telemetriyi yönetir.

```text
ew-emission-service
├── web/
│   ├── EmissionQueryController.java     <-- AI Copilot @Tool REST uçları (/radar/query, /radar/count vb.)
│   ├── TacticalController.java          <-- UI Tabloları için sayfalı REST uçları (/api/v1/tactical/emissions)
│   └── TacticalAnalysisController.java  <-- Makro analiz, korelasyon ve dağılım uçları
├── service/
│   ├── TacticalEmissionQueryService.java<-- Radar JPA Criteria Builder, sayfalama ve count motoru
│   ├── TacticalComintQueryService.java  <-- Muhabere JPA Criteria Builder ve çağrı analizi
│   ├── RadarCorrelationService.java     <-- 1-to-N benzerlik, eşleşme ve RF zarfı IoU hesaplayıcı
│   ├── TacticalDistributionService.java <-- Pasta/Donut dağılım agregasyonları
│   └── TacticalWaveformTimelineService.java <-- Frekans, PRI, PW zaman serisi dalga formu motoru
└── infrastructure/
    ├── entity/TacticalEmissionEntity.java<-- PostGIS ST_Point / ST_Polygon elips geometrisi
    ├── entity/TacticalComintEntity.java  <-- Telsiz frekans, modülasyon ve çağrı kayıtları
    └── repository/                      <-- JpaSpecificationExecutor genişletmeli indeksli repolar
```

#### İndeksleme ve Performans Güvencesi:
- **Radar Tablosu:** `idx_emi_spot_no`, `idx_emi_teshis`, `idx_emi_radar_adi`, `idx_emi_freq_range`, `idx_emi_son_tespit_zamani`, `idx_emi_kayit_tipi`.
- **PostGIS GiST İndeksi:** Kestirim elipsleri ve yön kerteriz (LOB) çizgileri üzerinde mikro-saniye düzeyinde `ST_DWithin` ve mekânsal kesişim sorgusu sağlar.
- **Skaler Sayım Başarımı:** `countEmissions` metodu entity yüklemesi veya DTO dönüşümü yapmaksızın doğrudan JPA `emissionRepo.count(spec)` çalıştırarak **2.3 milyondan fazla kayıt üzerinde 1-4 milisaniye** içinde cevap üretir.

---

### 3.2. `ew-copilot-service` (Spring AI Bilişsel Muhakeme Servisi) Mimarisi

`ew-copilot-service`, operatörün doğal dil sorgusunu karşılayan, askeri terminolojiyi çözümleyen ve arkadaki veri servisini orkestre eden yapay zekâ beynidir.

```text
ew-copilot-service
├── client/
│   └── EmissionRestClient.java          <-- ew-emission-service'e giden HTTP REST istemcisi
├── tools/
│   ├── RadarEmissionTools.java          <-- @Tool queryRadarEmissions, countRadarEmissions, hierarchy vb.
│   └── ComintEmissionTools.java         <-- @Tool queryComintEmissions, countComintEmissions, callsign vb.
├── dto/
│   ├── TacticalCountResultDto.java      <-- Net sayım sonucu (toplamKayitSayisi, kriter, aciklama)
│   ├── TacticalQueryResult.java         <-- Liste sonucu (kayitlar, toplamKayitSayisi, sekme, not)
│   ├── RadarFilterQueryDto.java         <-- Veri servisi filtre payload'ı
│   └── ComintFilterQueryDto.java        <-- Muhabere filtre payload'ı
└── service/
    └── TacticalAgentService.java        <-- Spring AI ChatClient, Prompt Şablonları & Call Log Drenajı
```

#### Spring AI Fluent ChatClient ve Tool Entegrasyonu:
`TacticalAgentService`, Spring AI'ın `ChatClient.Builder` yapısını kullanarak çalışır:
```java
ChatResponse response = chatClient.prompt()
    .system(buildSystemPrompt()) // Askeri EH doktrini, niyet ayrımı kuralları
    .user(userPrompt)            // Operatörün doğal dildeki taktik sorusu
    .advisors(new MessageChatMemoryAdvisor(chatMemory, conversationId, 10))
    .tools(radarTools, comintTools) // Spring AI @Tool metotları Gemini'ye JSON-Schema olarak iletilir
    .call()
    .chatResponse();
```

---

## 4. DETAYLI UÇTAN UCA ÇALIŞMA AKIŞI (SEQUENCE DIAGRAM)

Aşağıdaki diyagram, operatörün *"SP040 spot no'lu kaç kayıt var?"* veya *"Düşman radarlarını listele"* sorgusu yaptığında iki mikroservis arasındaki tam etkileşimi gösterir:

```mermaid
sequenceDiagram
    autonumber
    actor Operator as 🎖️ Operatör (Browser)
    participant FE as 🖥️ Copilot UI (React:3002)
    participant Copilot as 🧠 ew-copilot-service (8082)
    participant Gemini as 🤖 Google Gemini 2.5
    participant Emission as 🛡️ ew-emission-service (8081)
    participant DB as 🗄️ PostgreSQL 18 (PostGIS)

    Operator->>FE: "SP040 spot no lu kaç kayıt var?"
    FE->>FE: detectWidgetForQuery() çalışır (isCountPrompt=true -> widget=null)
    FE->>Copilot: POST /api/v1/tactical/analyze { userPrompt, conversationId }
    
    activate Copilot
    Copilot->>Gemini: Prompt + Askeri Kurallar + @Tool Şemaları (countRadar, queryRadar vb.)
    activate Gemini
    Gemini-->>Copilot: ToolCall: countRadarEmissions(spotNo="SP040")
    deactivate Gemini
    
    Copilot->>Copilot: RadarEmissionTools.countRadarEmissions(spotNo="SP040")
    Copilot->>Emission: POST /api/v1/emissions/radar/count { spotNo: "SP040" }
    
    activate Emission
    Emission->>Emission: TacticalEmissionQueryService.countEmissions(spotNo="SP040")
    Emission->>DB: SELECT count(e) FROM TacticalEmissionEntity e WHERE UPPER(e.spotNo) LIKE '%SP040%'
    DB-->>Emission: 6234 (Tek skaler sayı, 2 ms)
    Emission-->>Copilot: TacticalCountResultDto { toplamKayitSayisi: 6234, kriterOzeti: "Spot No: SP040" }
    deactivate Emission
    
    Copilot->>Gemini: Tool Cevabı: { toplamKayitSayisi: 6234 }
    activate Gemini
    Gemini-->>Copilot: "🎯 Sahada Spot No 'SP040' etiketine sahip toplam 6.234 adet kayıt bulunmaktadır..."
    deactivate Gemini
    
    Copilot-->>FE: JSON { operationalBriefing, executionLogs: ["Tool: countRadarEmissions(...)"] }
    deactivate Copilot
    
    FE->>Operator: Temiz Sohbet Yanıtı (Tablo YOK, Sağ Panel Kapalı, Tam Genişlik)
```

---

## 5. SKALER SAYIM (COUNT) VE LİSTELEME (LIST) NİYET AYRIMI MANTIĞI

Askeri karar destek sistemlerinde en sık yapılan hata; kullanıcının sadece tek bir sayı istediği durumda ekrana devasa tablolar yığarak operatörün karar alma hızını yavaşlatmaktır.

Sistemde bu problem **üç katmanlı bir savunma hattıyla** çözülmüştür:

```text
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                        KULLANICI SORGUSU: "SP040 kaç kayıt var?"                       │
└──────────────────────────────────────────┬─────────────────────────────────────────────┘
                                           │
                                           ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│ KATMAN 1: FRONTEND REGEX VE NİYET TESPİTİ (CopilotChat.jsx)                             │
│ - isCountPrompt = true (ka[çc]|adet|sayı kelimeleri var, görsel anahtar yok)          │
│ - Karar: SAĞ PANELİ AÇMA (activeWidget = null), EKRANI TAM GENİŞLİKTE TUT              │
└──────────────────────────────────────────┬─────────────────────────────────────────────┘
                                           │
                                           ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│ KATMAN 2: LLM AJAN YÖNERGESİ VE FUNCTION CALLING (TacticalAgentService.java)           │
│ - Kural: Sayım/Adet sorularında queryRadarEmissions DEĞİL, countRadarEmissions çağır!  │
│ - Kural: Asla Markdown tablosu üretme, tek cümlelik KPI brifingi ver!                  │
└──────────────────────────────────────────┬─────────────────────────────────────────────┘
                                           │
                                           ▼
┌────────────────────────────────────────────────────────────────────────────────────────┐
│ KATMAN 3: VERİ SERVİSİ VE VERİTABANI İNTİFASI (TacticalEmissionQueryService.java)       │
│ - emissionRepo.count(spec) çalışır. Liste çekilmez, DTO nesnesi üretilmez.             │
│ - 6234 sayısı hafif TacticalCountResultDto ile döner.                                  │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

### Karşılaştırmalı Niyet Davranış Tablosu:

| Kullanıcı İfadesi | Hedef Niyet | Çağrılan Tool | Dönen DTO Tipi | Üretilen UI Çıktısı | Sağ Panel Widget'ı |
| :--- | :--- | :--- | :--- | :--- | :--- |
| *"SP040 spot no'lu kaç kayıt var?"* | **Skaler Sayım** | `countRadarEmissions` | `TacticalCountResultDto` | Tek satır durum kartı: *"🎯 Sahada Spot No 'SP040' etiketine sahip toplam 6.234 kayıt bulunmaktadır."* | **KAPALI (null)** |
| *"SP040 spot no'lu kayıtları listele"* | **Detay Listeleme** | `queryRadarEmissions` | `TacticalQueryResult` | Formatlı Markdown Tablosu + Üst Sekme Yönlendirme Notu | **KAPALI (null)** |
| *"SP048 dalga formu zamansal değişimini göster"* | **Görselleştirme** | `getTacticalWaveformTimeline` | `TacticalWaveformTimelineDto` | E-posta/Brifing Özeti + ECharts Zamansal Dalga Formu | **AÇIK (`WAVEFORM_TIMELINE`)** |
| *"Kriptolu kaç adet telsiz yayını var?"* | **Skaler Sayım** | `countComintEmissions` | `TacticalCountResultDto` | *"🎯 Sahada tespit edilen kriptolu muhabere yayını sayısı: 412.300"* | **KAPALI (null)** |
| *"Haftalık LOB dağılım grafiğini aç"* | **Görselleştirme** | `getWeekdayEmissionDistribution`| `WeekdayRadarDistributionDto` | Gün bazlı taktik brifing + ECharts Radar Örümcek Ağı | **AÇIK (`WEEKDAY_RADAR`)** |

---

## 6. ÇİFT BAĞIMSIZ WEB UYGULAMASI (SELF-CONTAINED SYSTEMS - SCS) MİMARİSİ

Sistemde ön yüz katmanı, monolitik tek bir portal yerine **kullanıcı rolü**, **hata izolasyonu** ve **kaynak tüketimi** kriterleri gözetilerek **iki tamamen bağımsız web uygulaması** olarak ayrılmıştır:

```text
EWSpringAIDeneme2/
├── frontend-emission/               <-- 📡 Taktik Telemetri Konsolu (Port 3001)
│   ├── vite.config.js               <-- Proxy: /api -> http://127.0.0.1:8081
│   ├── src/
│   │   ├── App.jsx                  <-- Radar & COMINT Sekmeli Harekât Konsolu
│   │   └── components/
│   │       ├── RadarTable.jsx       <-- 521K Radar Kaydı için Sayfalı Sanal Tablo
│   │       ├── ComintTable.jsx      <-- 519K Telsiz Kaydı için Sayfalı Sanal Tablo
│   │       └── DetailDrawer.jsx     <-- LOB & Kestirim Elips Detay Çekmecesi
│
└── frontend-copilot/                <-- 🧠 Taktik YZ Copilot Kokpiti (Port 3002)
    ├── vite.config.js               <-- Proxy: /api/v1/tactical/analyze -> 8082, /api -> 8081
    ├── src/
    │   ├── App.jsx                  <-- AI Sohbet & Analitik Kokpit Kabuğu
    │   └── components/
    │       ├── CopilotChat.jsx      <-- Bilişsel YZ Sohbeti & Markdown Brifing
    │       ├── WeekdayRadarDistributionWidget.jsx <-- Sabit Adımlı (50K) Haftalık Örümcek Ağı
    │       ├── TacticalDistributionPieWidget.jsx  <-- Polarizasyon / Teşhis Donut Grafiği
    │       ├── CallsignHeatmapWidget.jsx          <-- Telsiz Çağrı Çiftleri Isı Haritası
    │       ├── RadarSpectrumOccupancyWidget.jsx   <-- NATO Frekans Spektrum Doluluğu
    │       └── TacticalWaveformTimelineWidget.jsx <-- Dalga Formu (PRI/PW/Frekans) Çizelgesi
```

---

## 7. NEDEN İKİ AYRI WEB UYGULAMASI TERCİH EDİLDİ? (MİMARİ KARAR GEREKÇELERİ)

1. **Ön Yüzde Ortak Canlı State İhtiyacının Bulunmaması (Zero State Coupling):**
   Her iki servisin çalışma alanları bağımsızdır. `frontend-emission` doğrudan `ew-emission-service` (Port 8081) ile ham telemetri alışverişi yaparken, `frontend-copilot` bağımsız olarak `ew-copilot-service` (Port 8082) ile yapay zekâ analiz oturumu yürütür. İki ekran arasında paylaşılan bir Redux/Zustand durum yönetim ihtiyacı bulunmadığından, ayrım hiçbir senkronizasyon karmaşası üretmez.

2. **Kullanıcı Rolü ve Persona Ayrımı (Persona Isolation):**
   - **Taktik Telemetri Operatörü (`:3001`):** Sahadaki radarları, telsiz frekanslarını, LOB kestirimlerini ve coğrafi mevzileri izler. Ekranında yapay zekâ sohbet modülüne veya markdown derleyicilerine ihtiyaç duymaz.
   - **Taktik Karar Verici / Komutan (`:3002`):** Milyonlarca satırlık ham telemetri tablolarıyla zaman kaybetmez; doğrudan doğal dille taktik durumu sorgular, Gemini tarafından üretilen özet brifingleri ve görsel analiz widget'larını değerlendirir.

3. **Bundle Boyutu ve Bellek Tasarrufu:**
   - `frontend-emission` sadece temel Ant Design ve veri tablolarını içerir; bundle boyutu **~1.1 MB**'a düşürülmüştür.
   - `frontend-copilot` ise görselleştirme motorları (ECharts) ve Markdown derleyicisini barındırır.
   Kullanıcı sadece ihtiyaç duyduğu uygulamayı yükler; tarayıcı belleği gereksiz kütüphanelerle şişirilmez.

4. **Sıfır Hata Yayılımı (Zero Blast Radius):**
   Yapay zekâ motorunda yaşanabilecek geçici bir API kotası aşımı veya analiz yoğunluğu, sahadaki telemetri masasını zerre kadar etkilemez. İki uygulama birbirinden fiziksel duvarlarla izoledir.

---

## 8. HATA TOLERANSI VE DAYANIKLILIK (RESILIENCE)

1. **Uygulama İzolasyonu:** `frontend-copilot` veya `ew-copilot-service` kapalı olsa bile operatör `http://localhost:3001` üzerinden radar ve telsiz yayınlarını sıfır kesintiyle izlemeye devam eder.
2. **REST İstemci Geri Çekilme (Fallback):** `EmissionRestClient` içinde her REST çağrısı `try-catch` bloklarıyla korunur. Veri servisi geçici olarak yanıt veremezse `TacticalCountResultDto(0L, "Hata", "Veri servisine erişilemedi")` dönerek yapay zekanın çökmesi engellenir.
3. **Veri Bütünlüğü ve Kalıcılık:** Servislerin veya ön yüzlerin yeniden başlatılması PostgreSQL'deki 1 milyondan fazla taktik kaydı kesinlikle etkilemez (`DataInitializer` kalıcılık kontrolü ile veriyi korur).

---

## 9. SONUÇ

Bu dikey ayrık çift mikroservis ve çift bağımsız web uygulaması mimarisi; yüksek hacimli taktik telemetri yönetimini (`ew-emission-service` + `frontend-emission:3001`) ile yapay zekâ bilişsel muhakeme kokpitini (`ew-copilot-service` + `frontend-copilot:3002`) birbirinden tam olarak izole etmiştir. Her iki uygulama kendi bağımsız URL'leri üzerinden, minimum kaynak tüketimiyle ve rol odaklı olarak taktik sahanın emrine sunulmuştur.
