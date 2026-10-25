/**
 * 🗺️ TAKTIK HARİTA & HATA ELİPSİ MATEMATİK KÜTÜPHANESİ
 * 
 * Askeri SIGINT/ELINT doktrini doğrultusunda:
 * - Kestirim Hata Elipsi (CEP / SemiMajor, SemiMinor, Azimuth) poligon üretimi
 * - Tek Yön Kerteriz (HAM_LOB) ışın vektörü üretimi
 * - O(1) Analitik Grid Örnekleme & 2D Bivariate Gaussian Olasılık Isı Haritası Puanlaması
 */

const METERS_PER_DEGREE_LAT = 111320.0;

/**
 * Derece cinsinden açıyı radyana çevirir
 */
export function toRadians(deg) {
  return (deg * Math.PI) / 180.0;
}

/**
 * Radyan cinsinden açıyı dereceye çevirir
 */
export function toDegrees(rad) {
  return (rad * 180.0) / Math.PI;
}

/**
 * Askeri Kestirim Elipsi için GeoJSON Polygon koordinatları üretir.
 * 
 * @param {number} lat - Merkez Enlem (Derece)
 * @param {number} lon - Merkez Boylam (Derece)
 * @param {number} semiMajor - Yarı Büyük Eksen (Metre)
 * @param {number} semiMinor - Yarı Küçük Eksen (Metre)
 * @param {number} azimuthDeg - Kuzeyden saat yönünde açı (Derece, 0-360)
 * @param {number} pointsCount - Poligon köşe sayısı (Varsayılan: 48)
 * @returns {Array<[number, number]>} GeoJSON formatında [[lon, lat], ...] halkası
 */
export function generateEllipsePolygon(lat, lon, semiMajor, semiMinor, azimuthDeg = 0, pointsCount = 48) {
  if (!lat || !lon || !semiMajor || !semiMinor) return null;

  const a = Math.max(semiMajor, 50.0);
  const b = Math.max(semiMinor, 25.0);
  const theta = toRadians(azimuthDeg);
  const cosTheta = Math.cos(theta);
  const sinTheta = Math.sin(theta);

  const latRad = toRadians(lat);
  const metersPerDegreeLon = METERS_PER_DEGREE_LAT * Math.cos(latRad);

  const coordinates = [];

  for (let i = 0; i <= pointsCount; i++) {
    const t = (2 * Math.PI * i) / pointsCount;
    // Yerel elips koordinatları (SemiMajor kuzey eksenli askeri elips)
    const localNorth = a * Math.cos(t);
    const localEast = b * Math.sin(t);

    // Azimut açısı kadar saat yönünde döndürme
    const rotEast = localEast * cosTheta + localNorth * sinTheta;
    const rotNorth = -localEast * sinTheta + localNorth * cosTheta;

    // Metrik yer değiştirmeyi dereceye dönüştürme
    const ptLat = lat + rotNorth / METERS_PER_DEGREE_LAT;
    const ptLon = lon + rotEast / metersPerDegreeLon;

    coordinates.push([ptLon, ptLat]);
  }

  return coordinates;
}

/**
 * Tek Yönlü Kerteriz (HAM_LOB) için sensörden hedefe doğru ışın çizgisi üretir.
 * 
 * @param {number} sensorLat - Sensör Enlemi
 * @param {number} sensorLon - Sensör Boylamı
 * @param {number} bearingDeg - Kerteriz Açısı (0-360 derece)
 * @param {number} distanceKm - Işın uzunluğu (Varsayılan: 75 km)
 * @returns {Array<[number, number]>} [[sensorLon, sensorLat], [targetLon, targetLat]]
 */
export function generateLobRay(sensorLat, sensorLon, bearingDeg, distanceKm = 75) {
  if (!sensorLat || !sensorLon || bearingDeg === undefined || bearingDeg === null) return null;

  const theta = toRadians(bearingDeg);
  const distMeters = distanceKm * 1000.0;

  const dNorth = distMeters * Math.cos(theta);
  const dEast = distMeters * Math.sin(theta);

  const latRad = toRadians(sensorLat);
  const metersPerDegreeLon = METERS_PER_DEGREE_LAT * Math.cos(latRad);

  const targetLat = sensorLat + dNorth / METERS_PER_DEGREE_LAT;
  const targetLon = sensorLon + dEast / metersPerDegreeLon;

  return [
    [sensorLon, sensorLat],
    [targetLon, targetLat]
  ];
}



/**
 * 2D Vektör Çapraz Çarpımı (Cross Product)
 */
function crossProduct(a, b, c) {
  return (b[0] - a[0]) * (c[1] - a[1]) - (b[1] - a[1]) * (c[0] - a[0]);
}

/**
 * Andrew's Monotone Chain Dış Bükey Kabuk (Convex Hull) Algoritması
 * Verilen koordinat kümesini çevreleyen en dış sınır poligonunu O(N log N) ile üretir.
 * 
 * @param {Array<[number, number]>} points - [[lon, lat], ...]
 * @returns {Array<[number, number]>} Dış sınır poligon köşeleri
 */
export function convexHull(points) {
  if (points.length <= 2) return points;
  const sorted = points.slice().sort((a, b) => (a[0] === b[0] ? a[1] - b[1] : a[0] - b[0]));
  const lower = [];
  for (let p of sorted) {
    while (lower.length >= 2 && crossProduct(lower[lower.length - 2], lower[lower.length - 1], p) <= 0) {
      lower.pop();
    }
    lower.push(p);
  }
  const upper = [];
  for (let i = sorted.length - 1; i >= 0; i--) {
    const p = sorted[i];
    while (upper.length >= 2 && crossProduct(upper[upper.length - 2], upper[upper.length - 1], p) <= 0) {
      upper.pop();
    }
    upper.push(p);
  }
  upper.pop();
  lower.pop();
  return lower.concat(upper);
}

/**
 * Frekans Bandı İsimlendirmesi (NATO/IEEE ve Muhabere Standartları)
 */
export function getFrequencyBandName(freqMhz, discipline = 'RADAR') {
  const freq = freqMhz || 3000.0;
  const isComint = discipline === 'MUHABERE' || discipline === 'COMINT';
  if (isComint) {
    if (freq < 30.0) return "HF Taktik Muhabere Telsiz Ağı (1.5 - 30.0 MHz)";
    if (freq < 300.0) return "VHF Taktik Komuta Telsiz Ağı (30 - 300 MHz)";
    if (freq < 1000.0) return "UHF Taktik Telsiz & Link Ağı (300 - 1000 MHz)";
    return "SHF Uydu & Mikrodalga Muhabere Linki (> 1.0 GHz)";
  }
  if (freq < 1000.0) return "VHF/UHF Erken İhbar Radarları (< 1000 MHz)";
  if (freq < 2000.0) return "L-Bant Gözetleme & Arama Radarları (1.0 - 2.0 GHz)";
  if (freq < 4000.0) return "S-Bant Orta/Uzun Menzil Arama Radarları (2.0 - 4.0 GHz)";
  if (freq < 8000.0) return "C-Bant Çok Fonksiyonlu Arama Radarları (4.0 - 8.0 GHz)";
  if (freq < 12000.0) return "X-Bant Atış Kontrol & Aydınlatma Radarları (8.0 - 12.0 GHz)";
  return "Ku/K-Bant Hassas Güdüm & Yakın Savunma Radarları (12.0+ GHz)";
}

/**
 * İki yayın arasındaki çok kriterli RF benzerlik fonksiyonu
 */
export function calculatePairSimilarity(a, b) {
  const aMin = a.minFrekansMhz || 3000.0;
  const aMax = a.maxFrekansMhz || (aMin + 30.0);
  const bMin = b.minFrekansMhz || 3000.0;
  const bMax = b.maxFrekansMhz || (bMin + 30.0);

  const intersect = Math.max(0.0, Math.min(aMax, bMax) - Math.max(aMin, bMin));
  const union = Math.max(1.0, Math.max(aMax, bMax) - Math.min(aMin, bMin));
  let sFreq = Math.min(1.0, intersect / union);
  if (sFreq === 0.0 && union > 0) {
    const gap = Math.abs(aMin - bMin);
    const scale = (a.discipline === 'MUHABERE' || a.discipline === 'COMINT') ? 50.0 : 1000.0;
    sFreq = Math.max(0.2, 1.0 - (gap / scale));
  }

  let sPri;
  if (a.priMicroSec != null && b.priMicroSec != null) {
    const maxPri = Math.max(a.priMicroSec, b.priMicroSec);
    sPri = maxPri > 0 ? Math.max(0.0, 1.0 - Math.abs(a.priMicroSec - b.priMicroSec) / maxPri) : 0.8;
  } else if (a.priMicroSec == null && b.priMicroSec == null) {
    sPri = 0.95;
  } else {
    sPri = 0.40;
  }

  let sPw;
  if (a.pwMicroSec != null && b.pwMicroSec != null) {
    const maxPw = Math.max(a.pwMicroSec, b.pwMicroSec);
    sPw = maxPw > 0 ? Math.max(0.0, 1.0 - Math.abs(a.pwMicroSec - b.pwMicroSec) / maxPw) : 0.85;
  } else if (a.pwMicroSec == null && b.pwMicroSec == null) {
    sPw = 0.95;
  } else {
    sPw = 0.40;
  }

  let bonus = 0.0;
  if (a.hss && b.hss && a.hss.toLowerCase() === b.hss.toLowerCase()) {
    bonus += 0.05;
  }
  if (a.discipline && b.discipline && a.discipline === b.discipline) {
    bonus += 0.03;
  }

  const score = (0.50 * sFreq + 0.28 * sPri + 0.18 * sPw + bonus) * 100.0;
  return Math.min(100.0, Math.max(10.0, score));
}

/**
 * Grup İçi Benzerlik Puanı Hesaplama (Geriye Dönük Uyumluluk)
 */
export function calculateGroupSimilarity(group) {
  if (group.length <= 1) return 100.0;
  let totalSim = 0.0;
  let pairCount = 0;

  for (let i = 0; i < group.length; i++) {
    for (let j = i + 1; j < group.length; j++) {
      totalSim += calculatePairSimilarity(group[i], group[j]);
      pairCount++;
    }
  }

  return pairCount > 0 ? (totalSim / pairCount) : 100.0;
}

/**
 * Kesişim bölgesindeki radarları ve muhabere yayınlarını frekans aralıklarına göre gruplar,
 * her bir yayın için yayın bazında küme uyum/benzerlik puanını hesaplar ve grup detaylarını üretir.
 */
export function groupAndScoreFrequencies(emitters) {
  const bandMap = new Map();

  emitters.forEach(em => {
    const freq = em.minFrekansMhz || 3000.0;
    const band = getFrequencyBandName(freq, em.discipline);
    if (!bandMap.has(band)) {
      bandMap.set(band, []);
    }
    bandMap.get(band).push(em);
  });

  const result = [];
  for (const [bandName, groupEmitters] of bandMap.entries()) {
    let minF = Infinity;
    let maxF = -Infinity;
    groupEmitters.forEach(e => {
      const mn = e.minFrekansMhz || 3000;
      const mx = e.maxFrekansMhz || mn;
      if (mn < minF) minF = mn;
      if (mx > maxF) maxF = mx;
    });
    if (minF === Infinity) minF = 0;
    if (maxF === -Infinity) maxF = minF;

    const names = Array.from(new Set(groupEmitters.map(e => (e.name || e.id) + (e.hss ? ` (${e.hss})` : ''))));

    const isComint = (groupEmitters[0]?.discipline || '').toUpperCase() === 'MUHABERE';

    // 1. KÜME REFERANS DEĞERLERİNİ (CENTROID & HÂKİM PROFİL) HESAPLA
    const avgMinF = groupEmitters.reduce((sum, e) => sum + (e.minFrekansMhz || minF), 0) / groupEmitters.length;
    const avgMaxF = groupEmitters.reduce((sum, e) => sum + (e.maxFrekansMhz || maxF), 0) / groupEmitters.length;
    const refFreqMid = Math.round(((avgMinF + avgMaxF) / 2.0) * 10) / 10;
    const refFreqRange = `${Math.round(avgMinF)} - ${Math.round(avgMaxF)} MHz`;

    // PRI Zarfı
    const priEmitters = groupEmitters.filter(e => e.minPriMicroSec != null || e.priMicroSec != null);
    let avgMinPri = null;
    let avgMaxPri = null;
    let refPriRange = '-';
    let refPriMid = null;
    if (priEmitters.length > 0) {
      avgMinPri = priEmitters.reduce((sum, e) => sum + (e.minPriMicroSec != null ? e.minPriMicroSec : e.priMicroSec), 0) / priEmitters.length;
      avgMaxPri = priEmitters.reduce((sum, e) => sum + (e.maxPriMicroSec != null ? e.maxPriMicroSec : (e.minPriMicroSec != null ? e.minPriMicroSec : e.priMicroSec)), 0) / priEmitters.length;
      refPriMid = Math.round(((avgMinPri + avgMaxPri) / 2.0) * 10) / 10;
      refPriRange = `${Math.round(avgMinPri)} - ${Math.round(avgMaxPri)} µs`;
    }

    // PW Zarfı
    const pwEmitters = groupEmitters.filter(e => e.minPwMicroSec != null || e.pwMicroSec != null);
    let avgMinPw = null;
    let avgMaxPw = null;
    let refPwRange = '-';
    let refPwMid = null;
    if (pwEmitters.length > 0) {
      avgMinPw = pwEmitters.reduce((sum, e) => sum + (e.minPwMicroSec != null ? e.minPwMicroSec : e.pwMicroSec), 0) / pwEmitters.length;
      avgMaxPw = pwEmitters.reduce((sum, e) => sum + (e.maxPwMicroSec != null ? e.maxPwMicroSec : (e.minPwMicroSec != null ? e.minPwMicroSec : e.pwMicroSec)), 0) / pwEmitters.length;
      refPwMid = Math.round(((avgMinPw + avgMaxPw) / 2.0) * 100) / 100;
      refPwRange = `${avgMinPw.toFixed(2)} - ${avgMaxPw.toFixed(2)} µs`;
    }

    // Hâkim Modülasyon, Platform ve Teşhis
    const refMod = findModeVal(groupEmitters.map(e => e.modulasyon), 'PULSE_DOPPLER');
    const refPulse = findModeVal(groupEmitters.map(e => e.pulseCw), 'PULSE');
    const refPol = findModeVal(groupEmitters.map(e => e.polarizasyon), 'YATAY');
    const refPlat = findModeVal(groupEmitters.map(e => e.platformOrtami), 'KARA');
    const refTeshis = findModeVal(groupEmitters.map(e => e.teshisKimlik), 'DUSMAN');
    const refGorev = findModeVal(groupEmitters.map(e => e.radarGorevi), '-');

    // Ortalama Tespit Zamanı
    const timeTimestamps = groupEmitters.map(e => e.sonTespitZamani ? new Date(e.sonTespitZamani).getTime() : null).filter(t => t != null && !isNaN(t));
    const avgEpoch = timeTimestamps.length > 0 ? (timeTimestamps.reduce((s, t) => s + t, 0) / timeTimestamps.length) : Date.now();

    // 2. HER YAYIN İÇİN 100 PUANLIK ÇOK KRİTERLİ MATRİS HESABI
    let totalGroupSim = 0.0;
    const emitterDetails = groupEmitters.map((cur) => {
      const eMinF = cur.minFrekansMhz || minF;
      const eMaxF = cur.maxFrekansMhz || eMinF;
      const eFreqStr = `${Math.round(eMinF)} - ${Math.round(eMaxF)} MHz`;

      const eMinP = cur.minPriMicroSec != null ? cur.minPriMicroSec : cur.priMicroSec;
      const eMaxP = cur.maxPriMicroSec != null ? cur.maxPriMicroSec : eMinP;
      const ePriStr = (eMinP != null) ? `${Math.round(eMinP)} - ${Math.round(eMaxP)} µs` : '-';

      const eMinW = cur.minPwMicroSec != null ? cur.minPwMicroSec : cur.pwMicroSec;
      const eMaxW = cur.maxPwMicroSec != null ? cur.maxPwMicroSec : eMinW;
      const ePwStr = (eMinW != null) ? `${eMinW.toFixed(2)} - ${eMaxW.toFixed(2)} µs` : '-';

      let freqScore;
      let priScore = null;
      let pwScore = null;
      let waveformScore = 0;
      let timeScore = 0;
      let tacticalScore = 0;

      if (!isComint) {
        // --- RADAR 100 PUANLIK MATRİSİ ---
        // 1. Frekans Zarfı IoU (25 Puan)
        const iouF = calcIntervalIoU(eMinF, eMaxF, avgMinF, avgMaxF);
        freqScore = Math.round(iouF * 250) / 10;

        // 2. PRI Zarfı IoU (15 Puan)
        if (eMinP != null && avgMinPri != null) {
          const iouP = calcIntervalIoU(eMinP, eMaxP, avgMinPri, avgMaxPri);
          priScore = Math.round(iouP * 150) / 10;
        }

        // 3. PW Zarfı IoU (10 Puan)
        if (eMinW != null && avgMinPw != null) {
          const iouW = calcIntervalIoU(eMinW, eMaxW, avgMinPw, avgMaxPw);
          pwScore = Math.round(iouW * 100) / 10;
        }

        // 4. Dalga Biçimi & Modülasyon (15 Puan)
        const sMop = (cur.modulasyon && cur.modulasyon.toUpperCase() === refMod) ? 7.0 : 0.0;
        const sPulse = (cur.pulseCw && cur.pulseCw.toUpperCase() === refPulse) ? 5.0 : 0.0;
        const sPol = (cur.polarizasyon && cur.polarizasyon.toUpperCase() === refPol) ? 3.0 : 0.0;
        waveformScore = Math.round((sMop + sPulse + sPol) * 10) / 10;

        // 5. Zaman Yakınlığı (15 Puan)
        timeScore = Math.round(calcTimeScore(cur.sonTespitZamani, avgEpoch, 15.0) * 10) / 10;

        // 6. Taktiksel & Platform Özellikleri (20 Puan)
        const sTeshis = (cur.teshisKimlik && cur.teshisKimlik.toUpperCase() === refTeshis) ? 8.0 : 0.0;
        const sPlat = (cur.platformOrtami && cur.platformOrtami.toUpperCase() === refPlat) ? 7.0 : 0.0;
        const sGorev = (cur.radarGorevi && cur.radarGorevi.toUpperCase() === refGorev) ? 5.0 : 0.0;
        tacticalScore = Math.round((sTeshis + sPlat + sGorev) * 10) / 10;

      } else {
        // --- COMINT (MUHABERE) 100 PUANLIK MATRİSİ ---
        const iouF = calcIntervalIoU(eMinF, eMaxF, avgMinF, avgMaxF);
        freqScore = Math.round(iouF * 400) / 10;
        waveformScore = (cur.modulasyon && cur.modulasyon.toUpperCase() === refMod) ? 20.0 : 5.0;
        timeScore = Math.round(calcTimeScore(cur.sonTespitZamani, avgEpoch, 20.0) * 10) / 10;
        const sTeshis = (cur.teshisKimlik && cur.teshisKimlik.toUpperCase() === refTeshis) ? 10.0 : 0.0;
        const sPlat = (cur.platformOrtami && cur.platformOrtami.toUpperCase() === refPlat) ? 10.0 : 0.0;
        tacticalScore = Math.round((sTeshis + sPlat) * 10) / 10;
      }

      let totalScore = freqScore
        + (priScore != null ? priScore : 0.0)
        + (pwScore != null ? pwScore : 0.0)
        + waveformScore
        + timeScore
        + tacticalScore;
      totalScore = Math.round(Math.min(100.0, Math.max(10.0, totalScore)) * 10) / 10;
      totalGroupSim += totalScore;

      return {
        id: cur.id,
        name: cur.name || cur.id,
        discipline: cur.discipline || 'RADAR',
        freqRange: eFreqStr,
        priRange: ePriStr,
        pwRange: ePwStr,
        minFrekansMhz: cur.minFrekansMhz,
        maxFrekansMhz: cur.maxFrekansMhz,
        minPriMicroSec: eMinP,
        maxPriMicroSec: eMaxP,
        minPwMicroSec: eMinW,
        maxPwMicroSec: eMaxW,
        modulasyon: cur.modulasyon || '-',
        platformOrtami: cur.platformOrtami || '-',
        teshisKimlik: cur.teshisKimlik || '-',
        sonTespitZamaniFormatted: cur.sonTespitZamaniFormatted || '-',
        freqScore,
        priScore,
        pwScore,
        waveformScore,
        timeScore,
        tacticalScore,
        similarityScore: totalScore,
        hss: cur.hss
      };
    });

    emitterDetails.sort((a, b) => b.similarityScore - a.similarityScore);

    const avgSimilarity = groupEmitters.length > 0
      ? Math.round((totalGroupSim / groupEmitters.length) * 10) / 10
      : 100.0;

    result.push({
      bandName,
      freqRange: `${Math.round(minF)} - ${Math.round(maxF)} MHz`,
      count: groupEmitters.length,
      refFreqMhz: refFreqMid,
      refPriMicroSec: refPriMid,
      refPwMicroSec: refPwMid,
      refFreqRange,
      refPriRange,
      refPwRange,
      refModulasyon: refMod,
      refPlatform: refPlat,
      refTeshis,
      radars: names,
      emitterDetails,
      similarityScore: avgSimilarity
    });
  }

  return result;
}

function calcIntervalIoU(min1, max1, min2, max2) {
  if (min1 == null || max1 == null || min2 == null || max2 == null) return 0.5;
  const aMin = Math.min(min1, max1);
  const aMax = Math.max(min1, max1);
  const bMin = Math.min(min2, max2);
  const bMax = Math.max(min2, max2);

  const interMin = Math.max(aMin, bMin);
  const interMax = Math.min(aMax, bMax);
  const intersection = Math.max(0.0, interMax - interMin);
  const union = Math.max(1e-6, Math.max(aMax, bMax) - Math.min(aMin, bMin));

  if (intersection > 0.0) {
    return Math.min(1.0, intersection / union);
  } else {
    const aMid = (aMin + aMax) / 2.0;
    const bMid = (bMin + bMax) / 2.0;
    const gap = Math.abs(aMid - bMid);
    const spread = Math.max(union, 1.0);
    return Math.max(0.15, 1.0 - (gap / (spread * 2.0)));
  }
}

function findModeVal(arr, fallback) {
  if (!arr || arr.length === 0) return fallback;
  const counts = {};
  for (const v of arr) {
    if (v && v.trim && v.trim() !== '-') {
      const k = v.trim().toUpperCase();
      counts[k] = (counts[k] || 0) + 1;
    }
  }
  let best = fallback;
  let maxC = 0;
  for (const [k, c] of Object.entries(counts)) {
    if (c > maxC) {
      maxC = c;
      best = k;
    }
  }
  return best;
}

function calcTimeScore(tStr, refEpoch, maxPoints) {
  if (!tStr) return maxPoints * 0.5;
  const t = new Date(tStr).getTime();
  if (isNaN(t)) return maxPoints * 0.5;
  const diffSec = Math.abs(t - refEpoch) / 1000.0;
  if (diffSec <= 300) return maxPoints;
  if (diffSec <= 1800) return maxPoints * 0.85;
  if (diffSec <= 7200) return maxPoints * 0.65;
  if (diffSec <= 21600) return maxPoints * 0.45;
  if (diffSec <= 86400) return maxPoints * 0.25;
  return maxPoints * 0.10;
}

/**
 * Kestirim elipslerinin kesiştiği ortak bölgeleri (Intersection Zones)
 * analitik olarak hesaplar, dış sınır poligonlarını (Convex Hull) üretir,
 * deterministik olarak sıralayıp dinamik numaralandırır (BÖLGE #1, BÖLGE #2...)
 * ve frekans gruplama ile grup içi benzerlik puanlarını hesaplar.
 * 
 * @param {Array<Object>} ellipses - [{ id, name, lat, lon, semiMajor, semiMinor, azimuth, minFrekansMhz, maxFrekansMhz, priMicroSec, pwMicroSec, hss, radarGorevi }]
 * @param {number} minThreshold - Minimum elips kesişim eşiği (Varsayılan: 2, kritik odaklar için: 3)
 * @returns {{ zoneFeatures: Array<Object>, centerFeatures: Array<Object>, totalZones: number, maxOverlaps: number, candidateZones: Array<Object> }}
 */
export function calculateTacticalIntersectionZones(ellipses, minThreshold = 2) {
  if (!ellipses || ellipses.length === 0) {
    return { zoneFeatures: [], centerFeatures: [], totalZones: 0, maxOverlaps: 0, candidateZones: [] };
  }

  const valid = [];
  for (const el of ellipses) {
    if (!el.lat || !el.lon || !el.semiMajor || !el.semiMinor) continue;
    const a = Math.max(el.semiMajor, 50.0);
    const b = Math.max(el.semiMinor, 25.0);
    const theta = toRadians(el.azimuth || 0);
    valid.push({
      id: el.id,
      name: el.name || el.hedefAdi || el.radarAdi || el.cagriAdi || el.id,
      discipline: el.discipline || el._discipline || 'RADAR',
      lat: el.lat,
      lon: el.lon,
      a,
      b,
      theta,
      cosTheta: Math.cos(theta),
      sinTheta: Math.sin(theta),
      metersPerDegreeLon: METERS_PER_DEGREE_LAT * Math.cos(toRadians(el.lat)),
      minFrekansMhz: el.minFrekansMhz || el.min_frekans_mhz,
      maxFrekansMhz: el.maxFrekansMhz || el.max_frekans_mhz,
      priMicroSec: el.priMicroSec || el.min_pri_micro_sec,
      pwMicroSec: el.pwMicroSec || el.min_pw_micro_sec,
      hss: el.hss,
      radarGorevi: el.radarGorevi || el.radar_gorevi
    });
  }

  if (valid.length < 2) {
    return { zoneFeatures: [], centerFeatures: [], totalZones: 0, maxOverlaps: 0, candidateZones: [] };
  }

  // 1. Kesişme ihtimali olan elipsleri kümele (Connected Components via BFS)
  const clusters = [];
  const visited = new Set();

  for (let i = 0; i < valid.length; i++) {
    if (visited.has(i)) continue;
    const cl = [valid[i]];
    visited.add(i);

    const queue = [valid[i]];
    while (queue.length > 0) {
      const cur = queue.shift();
      for (let j = 0; j < valid.length; j++) {
        if (visited.has(j)) continue;
        const other = valid[j];
        const dN = (cur.lat - other.lat) * METERS_PER_DEGREE_LAT;
        const dE = (cur.lon - other.lon) * cur.metersPerDegreeLon;
        const centerDist = Math.sqrt(dN * dN + dE * dE);

        // Merkezler arası mesafe (a1 + a2) değerinden küçükse kesişme potansiyeli vardır
        if (centerDist <= (cur.a + other.a)) {
          visited.add(j);
          cl.push(other);
          queue.push(other);
        }
      }
    }

    if (cl.length >= 2) {
      clusters.push(cl);
    }
  }

  const rawCandidates = [];
  let globalMaxOverlaps = 0;

  // 2. Her küme için minThreshold+ elipsin kesiştiği alt bölgeyi örnekle ve dış sınır poligonunu (Convex Hull) çıkar
  clusters.forEach((cl) => {
    let minLat = Infinity, maxLat = -Infinity, minLon = Infinity, maxLon = -Infinity;
    cl.forEach((el) => {
      const deltaLat = (el.a * 1.1) / METERS_PER_DEGREE_LAT;
      const deltaLon = (el.a * 1.1) / el.metersPerDegreeLon;
      minLat = Math.min(minLat, el.lat - deltaLat);
      maxLat = Math.max(maxLat, el.lat + deltaLat);
      minLon = Math.min(minLon, el.lon - deltaLon);
      maxLon = Math.max(maxLon, el.lon + deltaLon);
    });

    const res = 45;
    const latStep = (maxLat - minLat) / res;
    const lonStep = (maxLon - minLon) / res;
    const overlapPoints = [];
    let maxClusterOverlap = 0;
    const hitEmitterMap = new Map();

    for (let i = 0; i <= res; i++) {
      const pLat = minLat + i * latStep;
      for (let j = 0; j <= res; j++) {
        const pLon = minLon + j * lonStep;
        let count = 0;
        const currentHitEmitters = [];

        for (const el of cl) {
          const dN = (pLat - el.lat) * METERS_PER_DEGREE_LAT;
          const dE = (pLon - el.lon) * el.metersPerDegreeLon;
          const locN = dN * el.cosTheta + dE * el.sinTheta;
          const locE = -dN * el.sinTheta + dE * el.cosTheta;
          if ((locN / el.a) ** 2 + (locE / el.b) ** 2 <= 1.0) {
            count++;
            currentHitEmitters.push(el);
          }
        }

        if (count >= minThreshold) {
          overlapPoints.push([pLon, pLat]);
          if (count > maxClusterOverlap) maxClusterOverlap = count;
          currentHitEmitters.forEach((em) => hitEmitterMap.set(em.id, em));
        }
      }
    }

    if (overlapPoints.length >= 4 && maxClusterOverlap >= minThreshold) {
      const hull = convexHull(overlapPoints);
      if (hull.length >= 3) {
        hull.push([hull[0][0], hull[0][1]]); // Poligon halkasını kapat
        if (maxClusterOverlap > globalMaxOverlaps) globalMaxOverlaps = maxClusterOverlap;

        const avgLon = overlapPoints.reduce((s, p) => s + p[0], 0) / overlapPoints.length;
        const avgLat = overlapPoints.reduce((s, p) => s + p[1], 0) / overlapPoints.length;
        const coveredEmitters = Array.from(hitEmitterMap.values());

        rawCandidates.push({
          hull,
          overlapCount: maxClusterOverlap,
          centerLon: avgLon,
          centerLat: avgLat,
          coveredEmitters
        });
      }
    }
  });

  // 3. Deterministik Sıralama: En yüksek kesişim sayısı DESC, ardından batıdan doğuya boylam ASC
  // Bu sıralama Backend (ew-emission-service ve Spring AI Copilot) ile %100 BİREBİR AYNI dinamik numaralandırmayı sağlar!
  rawCandidates.sort((a, b) => (b.overlapCount - a.overlapCount) || (a.centerLon - b.centerLon));

  const zoneFeatures = [];
  const centerFeatures = [];

  rawCandidates.forEach((cand, idx) => {
    const zoneNumber = idx + 1;
    const zoneLabel = `BÖLGE #${zoneNumber}`;
    const zoneId = `INTERSECT-ZONE-${zoneNumber}`;
    const targetsList = cand.coveredEmitters.map(e => e.name || e.id);
    const freqGroups = groupAndScoreFrequencies(cand.coveredEmitters);

    const avgSimilarity = freqGroups.length > 0
      ? Math.round((freqGroups.reduce((s, g) => s + g.similarityScore, 0) / freqGroups.length) * 10) / 10
      : 100.0;

    const mainBand = freqGroups.length > 0 ? freqGroups[0].bandName.split(' ')[0] : 'Çoklu Bant';

    const featureProps = {
      id: zoneId,
      zoneNumber,
      zoneLabel,
      name: `🎯 ${zoneLabel}`,
      title: `🎯 ${zoneLabel} (${cand.overlapCount} Kesişen Elips)`,
      overlapCount: cand.overlapCount,
      targetCount: targetsList.length,
      targetsSummary: targetsList.slice(0, 4).join(', ') + (targetsList.length > 4 ? `... (+${targetsList.length - 4})` : ''),
      targets: JSON.stringify(targetsList),
      frequencyGroupsJson: JSON.stringify(freqGroups),
      avgSimilarity,
      mainBand,
      centerLon: cand.centerLon,
      centerLat: cand.centerLat
    };

    zoneFeatures.push({
      type: 'Feature',
      id: zoneId,
      geometry: {
        type: 'Polygon',
        coordinates: [cand.hull]
      },
      properties: featureProps
    });

    centerFeatures.push({
      type: 'Feature',
      id: `${zoneId}-CENTER`,
      geometry: {
        type: 'Point',
        coordinates: [cand.centerLon, cand.centerLat]
      },
      properties: featureProps
    });
  });

  return {
    zoneFeatures,
    centerFeatures,
    totalZones: zoneFeatures.length,
    maxOverlaps: globalMaxOverlaps,
    candidateZones: rawCandidates
  };
}

/**
 * İki coğrafi koordinat arasındaki kerteriz / azimut açısını (0-360 derece) hesaplar
 */
export function calculateBearingDeg(lat1, lon1, lat2, lon2) {
  const phi1 = toRadians(lat1);
  const phi2 = toRadians(lat2);
  const deltaLambda = toRadians(lon2 - lon1);
  const y = Math.sin(deltaLambda) * Math.cos(phi2);
  const x = Math.cos(phi1) * Math.sin(phi2) -
            Math.sin(phi1) * Math.cos(phi2) * Math.cos(deltaLambda);
  const theta = Math.atan2(y, x);
  return (toDegrees(theta) + 360.0) % 360.0;
}

/**
 * 🎯 Taktik Kinematik Hedef İntikal ve Konum Geçmişi İzi GeoJSON Üreticisi
 * 
 * Verilen kronolojik noktalar dizisinden:
 * 1. Kronolojik intikal çizgisi (LineString)
 * 2. Segmentler üzerine yerleştirilmiş yön/kerteriz oklu noktalar (Directional Arrow Points)
 * 3. Zamansal solma (time-decay alpha) efektli geçmiş kestirim elipsleri (Polygons)
 * 4. Numaralandırılmış kestirim merkezleri (#1, #2, ... #N Son)
 * 5. Harita kamera sınırları (bounds)
 */
export function generateTrackGeoJson(points = [], discipline = 'RADAR') {
  if (!Array.isArray(points) || points.length === 0) {
    return {
      trackLineFeatures: [],
      trackArrowFeatures: [],
      trackEllipseFeatures: [],
      trackPointFeatures: [],
      bounds: null
    };
  }

  const validPoints = points.filter(p => p.enlem != null && p.boylam != null);
  if (validPoints.length === 0) {
    return {
      trackLineFeatures: [],
      trackArrowFeatures: [],
      trackEllipseFeatures: [],
      trackPointFeatures: [],
      bounds: null
    };
  }

  const N = validPoints.length;
  let minLon = 180, maxLon = -180, minLat = 90, maxLat = -90;

  const lineCoords = [];
  const trackArrowFeatures = [];
  const trackEllipseFeatures = [];
  const trackPointFeatures = [];

  validPoints.forEach((pt, idx) => {
    const lon = Number(pt.boylam);
    const lat = Number(pt.enlem);

    if (lon < minLon) minLon = lon;
    if (lon > maxLon) maxLon = lon;
    if (lat < minLat) minLat = lat;
    if (lat > maxLat) maxLat = lat;

    lineCoords.push([lon, lat]);

    // Zamansal solma (time-decay alpha)
    const ratio = N > 1 ? idx / (N - 1) : 1.0;
    const isLatest = idx === N - 1;
    const fillOpacity = isLatest ? 0.45 : (0.10 + ratio * 0.28);
    const lineOpacity = isLatest ? 1.0 : (0.30 + ratio * 0.55);

    // Kestirim Elipsi Poligonu
    if (pt.semiMajor && pt.semiMinor) {
      const polyCoords = generateEllipsePolygon(
        lat,
        lon,
        pt.semiMajor,
        pt.semiMinor,
        pt.azimuth || 0
      );
      if (polyCoords) {
        trackEllipseFeatures.push({
          type: 'Feature',
          id: `track-ellipse-${pt.id || idx}`,
          geometry: {
            type: 'Polygon',
            coordinates: [polyCoords]
          },
          properties: {
            id: pt.id,
            sequenceNo: pt.sequenceNo || (idx + 1),
            isLatest,
            fillOpacity,
            lineOpacity,
            time: pt.timestampFormatted || '',
            targetName: pt.hedefAdi || pt.spotNoOrCallsign || 'Hedef',
            teshis: pt.teshisKimlik || 'DUSMAN',
            freq: pt.minFrekansMhz ? `${pt.minFrekansMhz}-${pt.maxFrekansMhz} MHz` : '',
            distanceFromPrev: pt.distanceFromPrevKm || 0,
            speedFromPrev: pt.speedFromPrevKmh || 0
          }
        });
      }
    }

    // Merkez Noktası ve Kronolojik Rozet
    trackPointFeatures.push({
      type: 'Feature',
      id: `track-point-${pt.id || idx}`,
      geometry: {
        type: 'Point',
        coordinates: [lon, lat]
      },
      properties: {
        id: pt.id,
        sequenceNo: pt.sequenceNo || (idx + 1),
        isLatest,
        badgeLabel: isLatest ? `#${pt.sequenceNo || (idx + 1)} (Son)` : `#${pt.sequenceNo || (idx + 1)}`,
        shortBadge: `#${pt.sequenceNo || (idx + 1)}`,
        time: pt.timestampFormatted || '',
        speedFromPrev: pt.speedFromPrevKmh || 0,
        distanceFromPrev: pt.distanceFromPrevKm || 0,
        targetName: pt.hedefAdi || pt.spotNoOrCallsign || 'Hedef'
      }
    });

    // Yön Oku (Segment üzerine yerleştirme)
    if (idx > 0) {
      const prev = validPoints[idx - 1];
      const prevLon = Number(prev.boylam);
      const prevLat = Number(prev.enlem);

      // Oku segmentin %65'ine yerleştir
      const arrowLon = prevLon + (lon - prevLon) * 0.65;
      const arrowLat = prevLat + (lat - prevLat) * 0.65;

      const bearing = pt.bearingFromPrevDeg != null
        ? pt.bearingFromPrevDeg
        : calculateBearingDeg(prevLat, prevLon, lat, lon);

      trackArrowFeatures.push({
        type: 'Feature',
        id: `track-arrow-${idx}`,
        geometry: {
          type: 'Point',
          coordinates: [arrowLon, arrowLat]
        },
        properties: {
          segmentIndex: idx,
          bearing: Math.round(bearing),
          distanceKm: pt.distanceFromPrevKm || 0,
          speedKmh: pt.speedFromPrevKmh || 0,
          time: pt.timestampFormatted || ''
        }
      });
    }
  });

  const trackLineFeatures = [];
  if (lineCoords.length >= 2) {
    trackLineFeatures.push({
      type: 'Feature',
      id: 'tactical-track-main-line',
      geometry: {
        type: 'LineString',
        coordinates: lineCoords
      },
      properties: {
        discipline,
        totalPoints: N,
        totalDistanceKm: validPoints[N - 1]?.distanceFromPrevKm || 0
      }
    });
  }

  const bounds = minLon <= maxLon && minLat <= maxLat ? [[minLon, minLat], [maxLon, maxLat]] : null;

  return {
    trackLineFeatures,
    trackArrowFeatures,
    trackEllipseFeatures,
    trackPointFeatures,
    bounds
  };
}




