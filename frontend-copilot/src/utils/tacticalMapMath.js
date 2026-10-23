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
 * Birden fazla kestirim elipsi için Bounding Box ve Grid Örneklemesi üzerinden
 * O(1) Analitik Bivariate Gaussian Isı Haritası Ağırlık Puanlarını hesaplar.
 * 
 * @param {Array<Object>} ellipses - [{ id, lat, lon, semiMajor, semiMinor, azimuth }]
 * @param {number} gridResolution - Izgara çözünürlüğü (Varsayılan: 65x65)
 * @returns {{ features: Array<Object>, peakCoordinate: [number, number]|null, peakScore: number, maxOverlaps: number }}
 */
export function calculateTacticalHeatmapGrid(ellipses, gridResolution = 65) {
  if (!ellipses || ellipses.length === 0) {
    return { features: [], peakCoordinate: null, peakScore: 0, maxOverlaps: 0 };
  }

  // 1. Sınırlayıcı Kutu (Bounding Box) Tespiti
  let minLat = Infinity;
  let maxLat = -Infinity;
  let minLon = Infinity;
  let maxLon = -Infinity;

  const validEllipses = [];

  for (const el of ellipses) {
    if (!el.lat || !el.lon || !el.semiMajor || !el.semiMinor) continue;
    const a = Math.max(el.semiMajor, 50.0);
    const latDelta = (a * 1.3) / METERS_PER_DEGREE_LAT;
    const lonDelta = (a * 1.3) / (METERS_PER_DEGREE_LAT * Math.cos(toRadians(el.lat)));

    minLat = Math.min(minLat, el.lat - latDelta);
    maxLat = Math.max(maxLat, el.lat + latDelta);
    minLon = Math.min(minLon, el.lon - lonDelta);
    maxLon = Math.max(maxLon, el.lon + lonDelta);

    const theta = toRadians(el.azimuth || 0);
    validEllipses.push({
      ...el,
      a,
      b: Math.max(el.semiMinor, 25.0),
      theta,
      cosTheta: Math.cos(theta),
      sinTheta: Math.sin(theta),
      metersPerDegreeLon: METERS_PER_DEGREE_LAT * Math.cos(toRadians(el.lat))
    });
  }

  if (validEllipses.length === 0) {
    return { features: [], peakCoordinate: null, peakScore: 0, maxOverlaps: 0 };
  }

  // %10 Emniyet Payı
  const latSpan = maxLat - minLat || 0.1;
  const lonSpan = maxLon - minLon || 0.1;
  minLat -= latSpan * 0.1;
  maxLat += latSpan * 0.1;
  minLon -= lonSpan * 0.1;
  maxLon += lonSpan * 0.1;

  const latStep = (maxLat - minLat) / gridResolution;
  const lonStep = (maxLon - minLon) / gridResolution;

  const points = [];
  let maxScore = 0;
  let maxOverlaps = 0;
  let peakCoordinate = null;

  // 2. Grid Noktalarını Tara & O(1) Analitik Hesaplama Yap
  for (let i = 0; i <= gridResolution; i++) {
    const pLat = minLat + i * latStep;
    for (let j = 0; j <= gridResolution; j++) {
      const pLon = minLon + j * lonStep;

      let overlapCount = 0;
      let gaussianSum = 0;

      for (let k = 0; k < validEllipses.length; k++) {
        const el = validEllipses[k];

        // Noktanın elips merkezine göre metrik uzaklığı
        const dNorth = (pLat - el.lat) * METERS_PER_DEGREE_LAT;
        const dEast = (pLon - el.lon) * el.metersPerDegreeLon;

        // Elipsin yerel eksenlerine izdüşüm (Ters rotasyon)
        const localNorth = dNorth * el.cosTheta + dEast * el.sinTheta;
        const localEast = -dNorth * el.sinTheta + dEast * el.cosTheta;

        // Normalize edilmiş Mahalanobis/Öklid mesafesi karesi D^2
        const normDistSq = (localNorth / el.a) ** 2 + (localEast / el.b) ** 2;

        if (normDistSq <= 1.0) {
          overlapCount++;
          // Bivariate Gaussian merkeze yakınlık katkısı
          const wGauss = Math.exp(-0.5 * normDistSq);
          gaussianSum += wGauss;
        }
      }

      if (overlapCount > 0) {
        // Kesişen elips sayısını ve Gaussian merkeze yakınlığı çarpan olarak puanla
        const score = gaussianSum * (1.0 + 0.75 * (overlapCount - 1));

        if (score > maxScore) {
          maxScore = score;
          peakCoordinate = [pLon, pLat];
        }
        if (overlapCount > maxOverlaps) {
          maxOverlaps = overlapCount;
        }

        points.push({
          lon: pLon,
          lat: pLat,
          rawScore: score,
          overlaps: overlapCount
        });
      }
    }
  }

  // 3. Puanları 0.0 - 1.0 Arasında Normalize Edip GeoJSON Feature Koleksiyonu Üret
  const features = points.map((pt) => {
    const normalizedWeight = maxScore > 0 ? pt.rawScore / maxScore : 0.5;
    return {
      type: 'Feature',
      geometry: {
        type: 'Point',
        coordinates: [pt.lon, pt.lat]
      },
      properties: {
        weight: normalizedWeight,
        overlaps: pt.overlaps,
        rawScore: pt.rawScore
      }
    };
  });

  return {
    features,
    peakCoordinate,
    peakScore: maxScore,
    maxOverlaps
  };
}
