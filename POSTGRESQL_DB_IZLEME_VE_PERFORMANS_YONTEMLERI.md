# 🛡️ PostgreSQL / PostGIS Performans İzleme, Sorgu Profilleme ve Optimizasyon Rehberi

**Doküman Versiyonu:** 1.0.0  
**Tarih:** 2026-10-25  
**Kapsam:** Elektronik Harp (ELINT / COMINT) Komuta Kontrol (C2) Sistemi Veritabanı Katmanı  
**Hedef Kitle:** Veritabanı Mimarları, Backend Geliştiricileri, C2 Sistem Operatörleri  

---

## 1. GİRİŞ VE PROBLEM TANIMI

Taktik komuta kontrol ve Elektronik Harp (EH) sistemlerinde sahadan sürekli veri akışı (`tactical.streamer` veya sensör linkleri) gerçekleşirken, veritabanında **milyonlarca kayıt** birikir. Mevcut sistemimizde:
- **1.170.000+ Radar (ELINT)** kaydı,
- **1.170.000+ Muhabere (COMINT)** kaydı (toplamda **2.340.000+** taktik kayıt) bulunmaktadır.

Böylesi yüksek hacimli ortamlarda:
1. Tek bir eksik indeks,
2. Fark edilmeyen bir **N+1 sorgu problemi**,
3. Sayfalama sırasında otomatik atılan gereksiz `COUNT(*)` sorguları,
4. Yetersiz bellek konfigürasyonları,

saniyenin altında olması gereken karar destek ve brifing sürelerini saniyeler mertebesine çıkarabilir. Bu doküman; **koda minimum müdahaleyle veritabanı performansını izleme yöntemlerini**, kurduğumuz yapıyı ve tespit edilen `COUNT` darboğazının optimizasyon yollarını özetler.

---

## 2. VERİTABANI İZLEME İÇİN 4 TEMEL YAKLAŞIM

Veritabanı sorgularının ne kadar sürdüğünü, hangi parametrelerle çalıştığını ve nerede darboğaz oluştuğunu izlemek için 4 temel mimari yaklaşım mevcuttur:

```text
┌─────────────────────────────────────────────────────────────────────────────────┐
│                     VERİTABANI İZLEME MİMARİ SEVİYELERİ                         │
└─────────────────────────────────────────────────────────────────────────────────┘

 1. VERİTABANI MOTORU SEVİYESİ (PostgreSQL pg_stat_statements & log_min_duration)
 ┌───────────────────────────────────────────────────────────────────────────────┐
 │ Avantaj: KOD DEĞİŞİKLİĞİ SIFIRDIR. En düşük overhead (%1-2). Doğrudan DB      │
 │          belleğinde kümülatif karne tutar.                                    │
 └──────────────────────────────────────┬────────────────────────────────────────┘
                                        │
 2. JDBC / CONNECTION POOL PROXY SEVİYESİ (p6spy, datasource-proxy)
 ┌──────────────────────────────────────▼────────────────────────────────────────┐
 │ Avantaj: Sadece tek satır bağımlılık veya driver url değişimi ile gerçek       │
 │          bağlanan parametreleri ($1, $2 yerine 'EMI-1001') loglar.             │
 └──────────────────────────────────────┬────────────────────────────────────────┘
                                        │
 3. ORM / HIBERNATE & SPRING AOP SEVİYESİ (Hibernate Statistics / Custom Interceptor)
 ┌──────────────────────────────────────▼────────────────────────────────────────┐
 │ Avantaj: Entity seviyesinde L2 Cache hit oranları, session süreleri ve        │
 │          Repository metotlarının sürelerini ölçer.                             │
 └──────────────────────────────────────┬────────────────────────────────────────┘
                                        │
 4. APM & METRİK GÖRSELLEŞTİRME SEVİYESİ (Micrometer, Actuator, Prometheus, Grafana)
 ┌──────────────────────────────────────▼────────────────────────────────────────┐
 │ Avantaj: HikariCP connection pool doygunluğu, JDBC execution time ve          │
 │          uzun vadeli zaman serisi trendlerini kurumsal dashboard'a döker.    │
 └───────────────────────────────────────────────────────────────────────────────┘
```

---

### Seviye 1: Doğrudan Veritabanı Motorunda Çözüm (PostgreSQL - Sıfır Kod)
*Uygulama koduna hiç dokunmadan, doğrudan veritabanı motorunun kendi telemetrisini kullanma yöntemidir.*

#### A. `pg_stat_statements` (Global SQL Röntgencisi - Sistemimizde Kurulan Yöntem)
- **Nasıl Çalışır:** PostgreSQL'in yerleşik resmi eklentisidir. DB'ye gelen tüm sorguları normalize eder (sabit değerleri ve parametreleri soyutlar) ve her SQL şablonu için:
  - Kaç defa çalıştı (`calls`)?
  - Toplam ve ortalama çalışma süresi (`total_exec_time`, `mean_exec_time`) nedir?
  - Maksimum/Pik süresi (`max_exec_time`) nedir?
  - Kaç satır etkiledi/döndü (`rows`)?
  - RAM'den mi okudu (`shared_blks_hit`) yoksa diske mi gitti (`shared_blks_read`)?
  bilgilerini tutar.
- **Overhead:** Yalnızca **%1 - %2**. Üretim (Production) ortamlarında 7/24 açık kalması standarttır.

#### B. `log_min_duration_statement` (Yavaş Sorgu Eşiği)
- PostgreSQL konfigürasyonunda `log_min_duration_statement = '50ms'` ayarlandığında, 50 ms'yi aşan her sorgu çalıştırıldığı **tam parametre değerleriyle birlikte** PostgreSQL log dosyasına düşer.

---

### Seviye 2: JDBC / Connection Pool Proxy Seviyesi (Parametre Değerlerini Yakalama)
*Eğer sorgulardaki `?` yerine tam olarak hangi filtre değerlerinin veya ID'lerin bağlandığını görmek gerekirse.*

- **Kullanılan Araçlar:** `com.github.gavlyukovskiy:datasource-proxy-spring-boot-starter` veya `p6spy`.
- **Nasıl Çalışır:** `application.yml` dosyasına eklenen 1 satırla HikariCP ile uygulama arasına şeffaf bir proxy yerleşir.
- **Çıktısı:**
  ```text
  HikariPool - 2.4 ms | statement | connection 1 | 
  SELECT * FROM tactical_emissions WHERE radar_adi = 'AN/MPQ-64' AND min_frekans_mhz >= 8000
  ```
- **Kullanım Yeri:** Geliştirme (DEV) ve Test ortamlarında parametre seviyesinde hata ayıklama.

---

### Seviye 3: Hibernate İstatistikleri ve Spring AOP Seviyesi
*Eğer sorunun veritabanında mı yoksa Java tarafındaki entity dönüşümünde mi olduğunu anlamak gerekirse.*

- **Hibernate Statistics:**
  `spring.jpa.properties.hibernate.generate_statistics=true`
  Her transaction sonunda kaç JDBC statement çalıştı, kaç milisaniye sürdü özet rapor basar. N+1 sorgularını yakalamak için idealdir.
- **Custom Spring AOP (`@Around`):**
  Repository metotlarının üzerine bir interceptor koyarak 100 ms'den uzun süren metot çağrılarını loglar.

---

### Seviye 4: Kurumsal APM ve Metrik Görselleştirme (Micrometer / Grafana)
*Eğer mikroservis ölçeğinde merkezi bir izleme altyapısı hedefleniyorsa.*

- `spring-boot-starter-actuator` + `micrometer-registry-prometheus`.
- HikariCP bağlantı havuzu metrikleri:
  - `hikaricp.connections.active` (O an kullanımda olan bağlantılar)
  - `hikaricp.connections.idle` (Boşta bekleyen bağlantılar)
  - `hikaricp.connections.pending` (Bağlantı sırası bekleyen thread'ler - Darboğaz sinyali!)

---

## 3. ORACLE DB TARAFI KARŞILIKLARI (1-e-1 EŞLEŞTİRME)

Kurumsal projelerde Oracle DB kullanıldığında aynı izleme fonksiyonlarının karşılıkları şunlardır:

| Özellik / İhtiyaç | PostgreSQL Karşılığı | Oracle DB Karşılığı | Açıklama |
| :--- | :--- | :--- | :--- |
| **Kümülatif SQL İstatistikleri** | `pg_stat_statements` | **`V$SQLSTATS`** / **`V$SQL`** / **`V$SQLAREA`** | Hangi sorgu kaç defa çalıştı, CPU ve Elapsed time nedir, disk/buffer okumaları. |
| **Parametre Değerlerini Görme** | `log_min_duration_statement` | **`V$SQL_BIND_CAPTURE`** / `DBA_HIST_SQLBIND` | Bind variable (`:b1`, `:b2`) yerine hangi değerlerin bağlandığını yakalar. |
| **Canlı Yavaş Sorgu İzleme** | `log_min_duration_statement` | **Real-Time SQL Monitoring** (`V$SQL_MONITOR`) | 5 saniyeden uzun süren veya paralel çalışan SQL'leri execution plan ile canlı izler. |
| **Tablo ve İndeks Taramaları** | `pg_stat_user_tables` | **`V$SEGMENT_STATISTICS`** & `ALL_TABLES` | Full Table Scan (FTS) vs Index Scan sayıları. |
| **Sayaç Sıfırlama** | `pg_stat_statements_reset()` | `ALTER SYSTEM FLUSH SHARED_POOL;` (Test ortamında) | Hafızadaki SQL istatistiklerini temizler. |

---

## 4. PROJEMİZDE HAYATA GEÇİRİLEN İZLEME MİMARİSİ

Projemizde **1. Seviye (PostgreSQL Motoru)** yaklaşımı seçilmiş ve kullanıcıya tam görsel kontrol sağlayan taktik bir panel inşa edilmiştir:

### A. Backend Mimarisi (`ew-emission-service` - Port 8081)
- **`DatabaseMetricsDto.java`:** Özet KPI'lar, tekil sorgular, tablo taramaları ve indeks kullanım modelleri.
- **`DatabaseMonitoringService.java`:** `JdbcTemplate` ile doğrudan `pg_stat_statements`, `pg_stat_user_tables`, `pg_stat_user_indexes` ve `pg_stat_activity` görünümlerini sorgular.
- **REST Uç Noktaları:**
  - `GET /api/v1/tactical/db-monitor/stats?limit=25&sortBy=mean_time`
  - `POST /api/v1/tactical/db-monitor/reset`

### B. Frontend Mimarisi (React 19 & Ant Design MIL-Dark)
- **`DatabaseMonitorWidget.jsx`:**
  1. **KPI Kartları:** Toplam Yürütülen Çağrı, Ortalama Yürütme Süresi (ms), 50ms Üzeri Yavaş Sorgular, Buffer Cache Hit Oranı (%) ve Aktif DB Bağlantısı.
  2. **Tab 1 - Sorgu Performansı:** Renk kodlu ortalama süreler (<10ms Yeşil, 10-50ms Turuncu, >50ms Kırmızı), pik süre, satır adedi ve tıklanabilir SQL şablonu detay modalı.
  3. **Tab 2 - Tablo Taramaları & İndeks Verimliliği:** Seq Scan vs Index Scan oranı ve `%95+` verimlilik kontrolü.
  4. **Tab 3 - İndeks Kullanım İstatistikleri:** Hangi B-Tree ve PostGIS GiST indeksinin kaç kez devreye girdiği ve disk boyutları.
- **Erişim:**
  - Ortak MFE Composer (`http://localhost:3000`): Üst menüde **`⚡ DB & Sorgu İzleme`**.
  - Bağımsız Konsol (`http://localhost:3001`): 3. sekme olarak **`⚡ DB & Sorgu İzleme`**.

---

## 5. TESPİT EDİLEN KRİTİK DARBOĞAZ: `SELECT COUNT(...)` ANALİZİ

Sorgu monitöründe en uzun süren (112 - 242 ms) sorgunun şu olduğu tespit edilmiştir:
```sql
SELECT count(tee1_0.emission_id) FROM tactical_emissions tee1_0;
```

### Kök Neden Analizi:
1. **Spring Data JPA Sayfalama Mekanizması:**
   Arayüzdeki tablonun sayfa çubuğunu ("Sayfa 1 / 58.520, Toplam 1.170.412 Kayıt") çizebilmek için Spring Data JPA her istekte `findAll(spec, pageable)` üzerinden bu sorguyu otomatik atar.
2. **PostgreSQL MVCC Mimarisi:**
   PostgreSQL toplam satır sayısını bir sayaçta saklamaz. Her transaction için 1.17 milyon satırın tamamının görünür (visible) olduğunu teyit etmek için **790.388 shared buffer bloğunu (~6.3 GB RAM)** baştan sona tarar (`Parallel Index Only Scan`).
3. **Docker `/dev/shm` Bellek Sıkışması:**
   Docker varsayılanında paylaşımlı bellek boyutu (`shm_size`) yalnızca **64 MB** olduğu için paralel worker'lar bellek yetersizliği hatası (`could not resize shared memory segment`) ile karşılaşmaktadır.

---

## 6. GELECEKTE UYGULANABİLECEK OPTİMİZASYON YOL HARİTASI

İlerleyen aşamalarda devreye alınmak üzere hazır bekletilen çözüm adımları:

### Adım 1: Filtresiz Sayfalamada "Estimated Count" (0.04 ms - 2800x Hızlanma)
Kullanıcı herhangi bir filtre seçmediğinde kesin sayıyı hesaplamak yerine PostgreSQL sistem katalogundaki tahmini sayı kullanılır:
```sql
SELECT reltuples::bigint AS estimate FROM pg_class WHERE relname = 'tactical_emissions';
```
- **Klasik Count:** `112.7 ms`
- **Estimated Count:** **`0.04 ms`** (2800 kat daha hızlı).

### Adım 2: Toplam Sayıyı RAM'de Önbelleğe Alma (Caching)
Spring Boot tarafında `@Cacheable(value = "emission_count", sync = true)` veya hafif bir `Caffeine Cache` tanımlanarak toplam kayıt sayısı 5-10 dakika önbellekte tutulur. Sayfa geçişlerinde count süresi **0 ms** olur.

### Adım 3: `Page<T>` Yerine `Slice<T>` Geçişi
Eğer operatörün 58.000 sayfa numarası arasında zıplaması gerekmiyorsa, sadece "Önceki / Sonraki Sayfa" gezintisi için `Slice<T>` kullanılır. `Slice`, `count` sorgusunu **tamamen ortadan kaldırır**.

### Adım 4: Docker Altyapı Düzeltmesi (`shm_size: 1g`)
`docker-compose.yml` içinde postgres servisine `shm_size: '1g'` eklenerek PostgreSQL parallel aggregate worker'larının rahat çalışması sağlanır.
