import React, { useState, useEffect, useRef } from 'react';
import {
  Card,
  Space,
  Button,
  Segmented,
  Select,
  Switch,
  Typography,
  Tag,
  Tooltip,
  Spin,
  Modal,
  Badge,
  Empty
} from 'antd';
import {
  GlobalOutlined,
  FullscreenOutlined,
  FullscreenExitOutlined,
  ReloadOutlined,
  CloseOutlined,
  RadarChartOutlined,
  CompassOutlined,
  InfoCircleOutlined,
  CaretRightOutlined,
  PauseOutlined,
  StepForwardOutlined,
  StepBackwardOutlined,
  AimOutlined
} from '@ant-design/icons';
import maplibregl from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import axios from 'axios';
import dayjs from 'dayjs';
import {
  generateEllipsePolygon,
  generateLobRay,
  calculateTacticalIntersectionZones,
  generateTrackGeoJson
} from '../utils/tacticalMapMath';

const { Text } = Typography;

// Koyu Taktik Askeri Harita Stili (ESRI World Dark Gray Canvas - Tamamen Ücretsiz, API Key Gerektirmez)
const TACTICAL_DARK_STYLE = {
  version: 8,
  sources: {
    'esri-dark-base': {
      type: 'raster',
      tiles: [
        'https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Base/MapServer/tile/{z}/{y}/{x}'
      ],
      tileSize: 256,
      attribution: '© Esri, DeLorme, NAVTEQ, TomTom'
    },
    'esri-dark-ref': {
      type: 'raster',
      tiles: [
        'https://server.arcgisonline.com/ArcGIS/rest/services/Canvas/World_Dark_Gray_Reference/MapServer/tile/{z}/{y}/{x}'
      ],
      tileSize: 256,
      attribution: ''
    }
  },
  layers: [
    {
      id: 'esri-dark-base-layer',
      type: 'raster',
      source: 'esri-dark-base',
      minzoom: 0,
      maxzoom: 20
    },
    {
      id: 'esri-dark-ref-layer',
      type: 'raster',
      source: 'esri-dark-ref',
      minzoom: 0,
      maxzoom: 20
    }
  ]
};

// Sektör Konfigürasyonları ve Koordinat Merkezleri (Taktik Sınır Kutuları ile)
const SECTOR_CONFIG = {
  ALL: { label: '🌍 Tüm Harekât Sahası', name: 'TÜM SAHA (MÜŞTEREK HAREKÂT ALANI)', center: [34.0, 39.0], zoom: 5.6 },
  EGE: {
    label: '🌊 Ege / Çeşme Odak',
    name: 'EGE DENİZİ / ÇEŞME-SAKIZ AÇIKLARI',
    center: [26.38, 38.35],
    zoom: 9.6,
    bounds: { minLat: 38.15, maxLat: 38.55, minLon: 26.15, maxLon: 26.65, res: 50 }
  },
  AKDENIZ: {
    label: '⚓ Doğu Akdeniz Odak',
    name: 'DOĞU AKDENİZ / KIBRIS KUZEYİ',
    center: [33.25, 35.82],
    zoom: 9.6,
    bounds: { minLat: 35.60, maxLat: 36.05, minLon: 33.00, maxLon: 33.50, res: 50 }
  },
  MARMARA: {
    label: '🌉 Marmara / Boğazlar',
    name: 'KUZEY EGE / ÇANAKKALE BOĞAZI',
    center: [26.20, 40.12],
    zoom: 9.6,
    bounds: { minLat: 39.90, maxLat: 40.35, minLon: 25.95, maxLon: 26.45, res: 50 }
  },
  KARADENIZ: {
    label: '🚢 Karadeniz Odak',
    name: 'KARADENİZ / KIRIM GÜNEYİ',
    center: [34.50, 42.45],
    zoom: 8.8,
    bounds: { minLat: 42.20, maxLat: 42.70, minLon: 34.25, maxLon: 34.75, res: 50 }
  },
  GUNEYDOGU: {
    label: '🛡️ Güneydoğu Hudut',
    name: 'GÜNEYDOĞU HUDUT HATTI',
    center: [42.15, 37.15],
    zoom: 9.6,
    bounds: { minLat: 36.95, maxLat: 37.35, minLon: 41.90, maxLon: 42.40, res: 50 }
  }
};

export default function TacticalMapWidget({
  onClose,
  onSelectTarget,
  initialMinIntersectionFilter = 3,
  initialDiscipline = 'TUMU',
  initialTimeRange = 'SON_24_SAAT',
  focusZoneNumber = null,
  initialTrackKey = null,
  initialTrackSource = 'AUTO',
  initialData = null,
  embeddedInModal = false
}) {
  // Filtre State'leri (Props ile dinamik başlatma)
  const [timeRange, setTimeRange] = useState(initialTimeRange); // 'SON_1_SAAT' | 'SON_6_SAAT' | 'SON_24_SAAT' | 'TUMU'
  const [discipline, setDiscipline] = useState(initialDiscipline); // 'TUMU' | 'RADAR' | 'MUHABERE'
  const [recordType, setRecordType] = useState('TUMU'); // 'TUMU' | 'KESTIRIM_ELIPS' | 'HAM_LOB'
  const [sectorFocus, setSectorFocus] = useState('ALL'); // 'ALL' | 'EGE' | 'AKDENIZ' | 'MARMARA' | 'KARADENIZ' | 'GUNEYDOGU'

  // Katman Aç/Kapa State'leri
  const [showIntersectionZones, setShowIntersectionZones] = useState(true);
  const [minIntersectionFilter, setMinIntersectionFilter] = useState(initialMinIntersectionFilter); // 3: Kritik Kesişim Odakları (6 Bölge), 2: Tümü
  const [showEllipses, setShowEllipses] = useState(true);
  const [showLobs, setShowLobs] = useState(true);

  // Hedef Kinematik İntikal İzi State'leri
  const [activeTrack, setActiveTrack] = useState(null);
  const [trackLoading, setTrackLoading] = useState(false);
  const [availableTrackTargets, setAvailableTrackTargets] = useState([]);
  const [selectedTrackKey, setSelectedTrackKey] = useState(initialTrackKey || null);
  const [trackPlaybackIdx, setTrackPlaybackIdx] = useState(null);
  const [isPlaying, setIsPlaying] = useState(false);
  const playIntervalRef = useRef(null);

  // Props güncellemelerini dinamik olarak filtre state'lerine yansıt
  useEffect(() => {
    if (initialMinIntersectionFilter != null) setMinIntersectionFilter(initialMinIntersectionFilter);
  }, [initialMinIntersectionFilter]);

  useEffect(() => {
    if (initialDiscipline != null) setDiscipline(initialDiscipline);
  }, [initialDiscipline]);

  useEffect(() => {
    if (initialTimeRange != null) setTimeRange(initialTimeRange);
  }, [initialTimeRange]);

  // Veri ve Hesaplama State'leri
  const [loading, setLoading] = useState(false);
  const [radarEmissions, setRadarEmissions] = useState([]);
  const [comintEmissions, setComintEmissions] = useState([]);
  const [filteredItems, setFilteredItems] = useState([]);
  const [intersectionStats, setIntersectionStats] = useState({
    totalZones: 0,
    maxOverlaps: 0,
    ellipseCount: 0,
    lobCount: 0
  });

  // Modal (Büyüt / Tam Ekran) State'i
  const [modalVisible, setModalVisible] = useState(false);

  // DOM ve Map Referansları
  const mapContainerRef = useRef(null);
  const mapInstanceRef = useRef(null);
  const modalMapContainerRef = useRef(null);
  const modalMapInstanceRef = useRef(null);

  // Hedef İntikal İzi Çekme Fonksiyonu
  const fetchTrackData = async (targetKey, src = 'AUTO') => {
    if (!targetKey) {
      clearTrack();
      return null;
    }
    setTrackLoading(true);
    try {
      const res = await axios.get('/api/v1/tactical/track', {
        params: { targetKey, source: src, limit: 100 }
      });
      if (res.data && res.data.points) {
        setActiveTrack(res.data);
        if (res.data.availableTargets && res.data.availableTargets.length > 0) {
          setAvailableTrackTargets(res.data.availableTargets);
        }
        return res.data;
      }
    } catch (err) {
      console.error('Hedef intikal izi çekilemedi:', err);
    } finally {
      setTrackLoading(false);
    }
    return null;
  };

  const handleSelectTrackTarget = async (key) => {
    setSelectedTrackKey(key);
    if (!key) {
      clearTrack();
      return;
    }
    const data = await fetchTrackData(key, 'AUTO');
    if (data && data.points && data.points.length > 0) {
      const geo = generateTrackGeoJson(data.points, data.source);
      if (geo.bounds) {
        mapInstanceRef.current?.fitBounds(geo.bounds, { padding: 90, duration: 1200 });
        modalMapInstanceRef.current?.fitBounds(geo.bounds, { padding: 90, duration: 1200 });
      }
    }
  };

  const clearTrack = () => {
    setActiveTrack(null);
    setSelectedTrackKey(null);
    setTrackPlaybackIdx(null);
    setIsPlaying(false);
  };

  // initialTrackKey prop ile geldiğinde otomatik yükle
  useEffect(() => {
    if (initialTrackKey) {
      handleSelectTrackTarget(initialTrackKey);
    }
  }, [initialTrackKey, initialTrackSource]);

  // Otomatik oynatma efekti
  useEffect(() => {
    if (isPlaying && activeTrack && activeTrack.points && activeTrack.points.length > 0) {
      playIntervalRef.current = setInterval(() => {
        setTrackPlaybackIdx((prev) => {
          const cur = prev === null ? 0 : prev;
          if (cur >= activeTrack.points.length - 1) {
            setIsPlaying(false);
            return null;
          }
          return cur + 1;
        });
      }, 750);
    } else {
      if (playIntervalRef.current) {
        clearInterval(playIntervalRef.current);
        playIntervalRef.current = null;
      }
    }
    return () => {
      if (playIntervalRef.current) {
        clearInterval(playIntervalRef.current);
      }
    };
  }, [isPlaying, activeTrack]);

  // Sayfa açılışında popüler hedefleri seçiciye yükle
  useEffect(() => {
    axios.get('/api/v1/tactical/track', { params: { limit: 1 } })
      .then(res => {
        if (res.data?.availableTargets) {
          setAvailableTrackTargets(res.data.availableTargets);
        }
      })
      .catch(() => {});
  }, []);

  // 1. Backend'den Veri Çekme (Ultra-Hafif Taktik Projeksiyon Endpoint'i - 1200 Kayıt)
  const fetchTacticalData = async () => {
    setLoading(true);
    try {
      const [resRadar, resComint] = await Promise.all([
        axios.get('/api/v1/tactical/map/emissions', { params: { limit: 1200 } }).catch(() => ({ data: [] })),
        axios.get('/api/v1/tactical/map/comints', { params: { limit: 1200 } }).catch(() => ({ data: [] }))
      ]);

      const radars = Array.isArray(resRadar.data)
        ? resRadar.data
        : (resRadar.data?.content || []);
      const comints = Array.isArray(resComint.data)
        ? resComint.data
        : (resComint.data?.content || []);

      setRadarEmissions(radars);
      setComintEmissions(comints);
    } catch (err) {
      console.error('Taktik harita verisi çekilemedi:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchTacticalData();
  }, []);

  // 2. Filtreleme ve Birleştirme Mantığı
  useEffect(() => {
    let combined = [];

    if (discipline === 'TUMU' || discipline === 'RADAR') {
      combined.push(
        ...radarEmissions.map((r) => ({
          ...r,
          _discipline: r.disiplin || 'RADAR',
          _lat: r.enlem !== undefined ? r.enlem : r.yayinEnlem,
          _lon: r.boylam !== undefined ? r.boylam : r.yayinBoylam,
          _semiMajor: r.semiMajor !== undefined ? r.semiMajor : r.yayinSemiMajorMeters,
          _semiMinor: r.semiMinor !== undefined ? r.semiMinor : r.yayinSemiMinorMeters,
          _azimuth: r.azimuth !== undefined ? r.azimuth : (r.yayinOrientationDegrees || 0),
          _sensorLat: r.sensorEnlem !== undefined ? r.sensorEnlem : r.ehUnsuruEnlem,
          _sensorLon: r.sensorBoylam !== undefined ? r.sensorBoylam : r.ehUnsuruBoylam,
          _bearing: r.bearing !== undefined ? r.bearing : r.yon,
          _name: r.hedefAdi || r.radarAdi || r.id,
          _timestamp: r.sonTespitZamani
        }))
      );
    }

    if (discipline === 'TUMU' || discipline === 'MUHABERE') {
      combined.push(
        ...comintEmissions.map((c) => ({
          ...c,
          _discipline: c.disiplin || 'MUHABERE',
          _lat: c.enlem !== undefined ? c.enlem : c.yayinEnlem,
          _lon: c.boylam !== undefined ? c.boylam : c.yayinBoylam,
          _semiMajor: c.semiMajor !== undefined ? c.semiMajor : c.yayinSemiMajorMeters,
          _semiMinor: c.semiMinor !== undefined ? c.semiMinor : c.yayinSemiMinorMeters,
          _azimuth: c.azimuth !== undefined ? c.azimuth : (c.yayinOrientationDegrees || 0),
          _sensorLat: c.sensorEnlem !== undefined ? c.sensorEnlem : c.ehUnsuruEnlem,
          _sensorLon: c.sensorBoylam !== undefined ? c.sensorBoylam : c.ehUnsuruBoylam,
          _bearing: c.bearing !== undefined ? c.bearing : c.yon,
          _name: c.hedefAdi || c.cagriAdi || c.id,
          _timestamp: c.sonTespitZamani
        }))
      );
    }

    // Zaman Süzgeci
    if (timeRange !== 'TUMU') {
      const now = dayjs();
      let hours = 24;
      if (timeRange === 'SON_1_SAAT') hours = 1;
      else if (timeRange === 'SON_6_SAAT') hours = 6;
      else if (timeRange === 'SON_24_SAAT') hours = 24;

      const cutoff = now.subtract(hours, 'hour');
      combined = combined.filter((item) => {
        if (!item._timestamp) return true; // Zaman damgası yoksa koru
        const d = dayjs(item._timestamp);
        return d.isValid() ? d.isAfter(cutoff) : true;
      });
    }

    // Kayıt Türü Süzgeci (KESTIRIM_ELIPS vs HAM_LOB)
    if (recordType !== 'TUMU') {
      combined = combined.filter((item) => {
        const kt = item.kayitTipi || (item._lat ? 'KESTIRIM_ELIPS' : 'HAM_LOB');
        return kt === recordType;
      });
    }

    setFilteredItems(combined);
  }, [radarEmissions, comintEmissions, discipline, timeRange, recordType]);

  // 3. Harita Katmanlarını Güncelleme Fonksiyonu
  const updateMapLayers = (map) => {
    if (!map || !map.isStyleLoaded()) return;

    // A. Elipsleri ve LOB'ları Ayıkla
    const ellipseCandidates = [];
    const lobFeatures = [];
    const ellipseFeatures = [];
    const ellipseCenterFeatures = [];

    filteredItems.forEach((item) => {
      const sLat = item._sensorLat || item.ehUnsuruEnlem;
      const sLon = item._sensorLon || item.ehUnsuruBoylam;
      const sBearing = item._bearing !== undefined ? item._bearing : item.yon;
      const isLob = item.kayitTipi === 'HAM_LOB' || (!item._lat && sLat);

      if (isLob && sLat && sLon && sBearing !== undefined) {
        // LOB Kerteriz Işını
        const rayCoords = generateLobRay(sLat, sLon, sBearing, 75);
        if (rayCoords) {
          lobFeatures.push({
            type: 'Feature',
            geometry: {
              type: 'LineString',
              coordinates: rayCoords
            },
            properties: {
              id: item.id,
              discipline: item._discipline,
              bearing: sBearing,
              ehUnsuru: item.ehUnsuru || item.goreviIcraEdenEhUnsuru || 'Sensör'
            }
          });
        }
      } else if (item._lat && item._lon && item._semiMajor && item._semiMinor) {
        // Kestirim Elipsi
        ellipseCandidates.push({
          id: item.id,
          name: item._name || item.hedefAdi || item.radarAdi || item.cagriAdi || item.id,
          lat: item._lat,
          lon: item._lon,
          semiMajor: item._semiMajor,
          semiMinor: item._semiMinor,
          azimuth: item._azimuth,
          minFrekansMhz: item.minFrekansMhz || item.min_frekans_mhz,
          maxFrekansMhz: item.maxFrekansMhz || item.max_frekans_mhz,
          priMicroSec: item.priMicroSec || item.min_pri_micro_sec || item.pri_micro_sec,
          pwMicroSec: item.pwMicroSec || item.min_pw_micro_sec || item.pw_micro_sec,
          hss: item.hss,
          radarGorevi: item.radarGorevi || item.radar_gorevi,
          discipline: item._discipline
        });

        const polyCoords = generateEllipsePolygon(
          item._lat,
          item._lon,
          item._semiMajor,
          item._semiMinor,
          item._azimuth
        );

        if (polyCoords) {
          ellipseFeatures.push({
            type: 'Feature',
            geometry: {
              type: 'Polygon',
              coordinates: [polyCoords]
            },
            properties: {
              id: item.id,
              discipline: item._discipline,
              name: item._name || item.hedefAdi || item.radarAdi || item.cagriAdi || item.id,
              freq: item.minFrekansMhz ? `${item.minFrekansMhz}-${item.maxFrekansMhz} MHz` : '-',
              semiMajor: Math.round(item._semiMajor),
              semiMinor: Math.round(item._semiMinor),
              azimuth: Math.round(item._azimuth),
              teshis: item.teshisKimlik || 'BILINMEYEN',
              time: item.sonTespitZamaniFormatted || ''
            }
          });

          ellipseCenterFeatures.push({
            type: 'Feature',
            geometry: {
              type: 'Point',
              coordinates: [item._lon, item._lat]
            },
            properties: {
              id: item.id,
              discipline: item._discipline,
              name: item.radarAdi || item.cagriAdi || item.id
            }
          });
        }
      }
    });

    // B. Kesişme Bölgeleri (Dinamik Numaralandırma & Frekans Benzerlik Analizi)
    const intersectionResult = calculateTacticalIntersectionZones(ellipseCandidates, minIntersectionFilter);
    setIntersectionStats({
      totalZones: intersectionResult.totalZones,
      maxOverlaps: intersectionResult.maxOverlaps,
      ellipseCount: ellipseCandidates.length,
      lobCount: lobFeatures.length
    });

    // C. MapLibre GeoJSON Kaynaklarını Güncelle
    const zoneSource = map.getSource('tactical-intersection-zones');
    if (zoneSource) {
      zoneSource.setData({
        type: 'FeatureCollection',
        features: intersectionResult.zoneFeatures
      });
    }

    const zoneCenterSource = map.getSource('tactical-intersection-centers');
    if (zoneCenterSource) {
      zoneCenterSource.setData({
        type: 'FeatureCollection',
        features: intersectionResult.centerFeatures
      });
    }

    // Kokpit'ten odaklanılacak bir bölge numarası (focusZoneNumber) geldiyse haritayı oraya uçur
    if (focusZoneNumber != null) {
      const targetFeat = intersectionResult.zoneFeatures.find(f => f.properties.zoneNumber === focusZoneNumber);
      if (targetFeat && targetFeat.properties.centerLon != null && targetFeat.properties.centerLat != null) {
        map.flyTo({
          center: [Number(targetFeat.properties.centerLon), Number(targetFeat.properties.centerLat)],
          zoom: 10.5,
          duration: 1200
        });
      }
    }

    const lobSource = map.getSource('tactical-lobs');
    if (lobSource) {
      lobSource.setData({ type: 'FeatureCollection', features: lobFeatures });
    }

    const ellipseSource = map.getSource('tactical-ellipses');
    if (ellipseSource) {
      ellipseSource.setData({ type: 'FeatureCollection', features: ellipseFeatures });
    }

    const ellipseCenterSource = map.getSource('tactical-ellipse-centers');
    if (ellipseCenterSource) {
      ellipseCenterSource.setData({ type: 'FeatureCollection', features: ellipseCenterFeatures });
    }

    // D. Katman Görünürlüklerini Ayarla
    if (map.getLayer('tactical-intersection-zones-fill')) {
      map.setLayoutProperty('tactical-intersection-zones-fill', 'visibility', showIntersectionZones ? 'visible' : 'none');
      map.setLayoutProperty('tactical-intersection-zones-line', 'visibility', showIntersectionZones ? 'visible' : 'none');
      map.setLayoutProperty('tactical-intersection-centers-circle', 'visibility', showIntersectionZones ? 'visible' : 'none');
      map.setLayoutProperty('tactical-intersection-centers-pulse', 'visibility', showIntersectionZones ? 'visible' : 'none');
      if (map.getLayer('tactical-intersection-centers-badge')) {
        map.setLayoutProperty('tactical-intersection-centers-badge', 'visibility', showIntersectionZones ? 'visible' : 'none');
      }
      if (map.getLayer('tactical-intersection-centers-label')) {
        map.setLayoutProperty('tactical-intersection-centers-label', 'visibility', showIntersectionZones ? 'visible' : 'none');
      }
    }

    if (map.getLayer('tactical-ellipses-fill')) {
      map.setLayoutProperty('tactical-ellipses-fill', 'visibility', showEllipses ? 'visible' : 'none');
      map.setLayoutProperty('tactical-ellipses-line', 'visibility', showEllipses ? 'visible' : 'none');
      map.setLayoutProperty('tactical-ellipse-centers-circle', 'visibility', showEllipses ? 'visible' : 'none');
    }

    if (map.getLayer('tactical-lobs-line')) {
      map.setLayoutProperty('tactical-lobs-line', 'visibility', showLobs ? 'visible' : 'none');
    }

    // E. Taktik Kinematik Hedef İntikal İzi (Track) Katmanlarını Güncelle
    if (activeTrack && activeTrack.points && activeTrack.points.length > 0) {
      const pts = trackPlaybackIdx !== null
        ? activeTrack.points.slice(0, trackPlaybackIdx + 1)
        : activeTrack.points;

      const trackGeo = generateTrackGeoJson(pts, activeTrack.source);

      map.getSource('tactical-track-lines')?.setData({
        type: 'FeatureCollection',
        features: trackGeo.trackLineFeatures
      });

      map.getSource('tactical-track-arrows')?.setData({
        type: 'FeatureCollection',
        features: trackGeo.trackArrowFeatures
      });

      map.getSource('tactical-track-ellipses')?.setData({
        type: 'FeatureCollection',
        features: trackGeo.trackEllipseFeatures
      });

      map.getSource('tactical-track-points')?.setData({
        type: 'FeatureCollection',
        features: trackGeo.trackPointFeatures
      });

      ['tactical-track-lines-glow', 'tactical-track-lines-main', 'tactical-track-arrows-symbol',
       'tactical-track-ellipses-fill', 'tactical-track-ellipses-line', 'tactical-track-points-pulse',
       'tactical-track-points-circle', 'tactical-track-points-badge'].forEach(layerId => {
        if (map.getLayer(layerId)) {
          map.setLayoutProperty(layerId, 'visibility', 'visible');
        }
      });
    } else {
      const emptyFc = { type: 'FeatureCollection', features: [] };
      map.getSource('tactical-track-lines')?.setData(emptyFc);
      map.getSource('tactical-track-arrows')?.setData(emptyFc);
      map.getSource('tactical-track-ellipses')?.setData(emptyFc);
      map.getSource('tactical-track-points')?.setData(emptyFc);

      ['tactical-track-lines-glow', 'tactical-track-lines-main', 'tactical-track-arrows-symbol',
       'tactical-track-ellipses-fill', 'tactical-track-ellipses-line', 'tactical-track-points-pulse',
       'tactical-track-points-circle', 'tactical-track-points-badge'].forEach(layerId => {
        if (map.getLayer(layerId)) {
          map.setLayoutProperty(layerId, 'visibility', 'none');
        }
      });
    }
  };

  // Sektör Değiştirme ve Kamerayı Uçurma (FlyTo / FitBounds)
  const handleSectorChange = (sec) => {
    setSectorFocus(sec);
    const target = SECTOR_CONFIG[sec];
    if (!target) return;

    if (sec === 'ALL') {
      fitAllEllipses(mapInstanceRef.current);
      fitAllEllipses(modalMapInstanceRef.current);
    } else {
      mapInstanceRef.current?.flyTo({
        center: target.center,
        zoom: target.zoom,
        essential: true,
        duration: 1200
      });
      modalMapInstanceRef.current?.flyTo({
        center: target.center,
        zoom: target.zoom,
        essential: true,
        duration: 1200
      });
    }
  };

  const fitAllEllipses = (map) => {
    if (!map) return;
    const validCoords = filteredItems
      .filter((it) => it._lat && it._lon)
      .map((it) => [it._lon, it._lat]);

    if (validCoords.length > 0) {
      let minX = Infinity, minY = Infinity, maxX = -Infinity, maxY = -Infinity;
      validCoords.forEach(([x, y]) => {
        minX = Math.min(minX, x);
        maxX = Math.max(maxX, x);
        minY = Math.min(minY, y);
        maxY = Math.max(maxY, y);
      });
      if (minX !== Infinity && maxX !== -Infinity && minX !== maxX) {
        map.fitBounds([[minX, minY], [maxX, maxY]], {
          padding: 45,
          maxZoom: 10.5,
          duration: 1000
        });
      }
    }
  };

  // 4. Harita Örneği Başlatma (Initialize MapLibre)
  const initMap = (containerEl) => {
    if (!containerEl) return null;

    const map = new maplibregl.Map({
      container: containerEl,
      style: TACTICAL_DARK_STYLE,
      center: SECTOR_CONFIG.ALL.center,
      zoom: SECTOR_CONFIG.ALL.zoom,
      attributionControl: false
    });

    map.addControl(new maplibregl.NavigationControl({ showCompass: true }), 'top-right');

    map.on('load', () => {
      // 1. Kestirim Elipsleri Kaynağı & Katmanları
      map.addSource('tactical-ellipses', {
        type: 'geojson',
        data: { type: 'FeatureCollection', features: [] }
      });

      map.addLayer({
        id: 'tactical-ellipses-fill',
        type: 'fill',
        source: 'tactical-ellipses',
        paint: {
          'fill-color': [
            'case',
            ['==', ['get', 'discipline'], 'RADAR'],
            '#0284c7', // Radar: Deniz Mavisi
            '#9333ea'  // Muhabere: Mor
          ],
          'fill-opacity': 0.18
        }
      });

      map.addLayer({
        id: 'tactical-ellipses-line',
        type: 'line',
        source: 'tactical-ellipses',
        paint: {
          'line-color': [
            'case',
            ['==', ['get', 'discipline'], 'RADAR'],
            '#38bdf8', // Neon Sky Blue
            '#c084fc'  // Neon Mor
          ],
          'line-width': 1.8,
          'line-dasharray': [4, 2]
        }
      });

      // 2.1 2 ve Daha Fazla Elipsin Kesiştiği Ortak Dış Bölgeler (Kullanıcı İsteği)
      map.addSource('tactical-intersection-zones', {
        type: 'geojson',
        data: { type: 'FeatureCollection', features: [] }
      });

      map.addSource('tactical-intersection-centers', {
        type: 'geojson',
        data: { type: 'FeatureCollection', features: [] }
      });

      map.addLayer({
        id: 'tactical-intersection-zones-fill',
        type: 'fill',
        source: 'tactical-intersection-zones',
        paint: {
          'fill-color': [
            'case',
            ['>=', ['get', 'overlapCount'], 5], '#ef4444',
            ['>=', ['get', 'overlapCount'], 3], '#f97316',
            '#eab308'
          ],
          'fill-opacity': [
            'case',
            ['>=', ['get', 'overlapCount'], 5], 0.38,
            ['>=', ['get', 'overlapCount'], 3], 0.28,
            0.20
          ]
        }
      });

      map.addLayer({
        id: 'tactical-intersection-zones-line',
        type: 'line',
        source: 'tactical-intersection-zones',
        paint: {
          'line-color': [
            'case',
            ['>=', ['get', 'overlapCount'], 5], '#ef4444',
            ['>=', ['get', 'overlapCount'], 3], '#f97316',
            '#facc15'
          ],
          'line-width': 2.8,
          'line-dasharray': [4, 2]
        }
      });

      map.addLayer({
        id: 'tactical-intersection-centers-pulse',
        type: 'circle',
        source: 'tactical-intersection-centers',
        paint: {
          'circle-radius': 16,
          'circle-color': 'rgba(239, 68, 68, 0.25)',
          'circle-stroke-width': 1.8,
          'circle-stroke-color': '#ef4444'
        }
      });

      map.addLayer({
        id: 'tactical-intersection-centers-circle',
        type: 'circle',
        source: 'tactical-intersection-centers',
        paint: {
          'circle-radius': 9.5,
          'circle-color': [
            'case',
            ['>=', ['get', 'overlapCount'], 5], '#ef4444',
            ['>=', ['get', 'overlapCount'], 3], '#f97316',
            '#eab308'
          ],
          'circle-stroke-width': 2,
          'circle-stroke-color': '#ffffff'
        }
      });

      // 🎯 DİNAMİK NUMARALANDIRMA ROZETİ (Çember içi 1, 2, 3...)
      map.addLayer({
        id: 'tactical-intersection-centers-badge',
        type: 'symbol',
        source: 'tactical-intersection-centers',
        layout: {
          'text-field': ['to-string', ['get', 'zoneNumber']],
          'text-font': ['Open Sans Bold', 'Arial Unicode MS Bold'],
          'text-size': 11,
          'text-allow-overlap': true,
          'text-ignore-placement': true
        },
        paint: {
          'text-color': '#ffffff'
        }
      });

      // 🎯 HARİTADA GÖRÜNÜR BÖLGE VE RADAR SAYISI ETİKETİ (BÖLGE #1 ...)
      map.addLayer({
        id: 'tactical-intersection-centers-label',
        type: 'symbol',
        source: 'tactical-intersection-centers',
        layout: {
          'text-field': ['concat', ['get', 'zoneLabel'], ' (', ['to-string', ['get', 'overlapCount']], ' Radar)'],
          'text-font': ['Open Sans Bold', 'Arial Unicode MS Bold'],
          'text-size': 11,
          'text-offset': [0, 1.8],
          'text-anchor': 'top',
          'text-allow-overlap': false
        },
        paint: {
          'text-color': '#f8fafc',
          'text-halo-color': '#090e1d',
          'text-halo-width': 2.5
        }
      });

      // 3. Elips Merkezleri Kaynağı
      map.addSource('tactical-ellipse-centers', {
        type: 'geojson',
        data: { type: 'FeatureCollection', features: [] }
      });

      map.addLayer({
        id: 'tactical-ellipse-centers-circle',
        type: 'circle',
        source: 'tactical-ellipse-centers',
        paint: {
          'circle-radius': 4.5,
          'circle-color': '#38bdf8',
          'circle-stroke-width': 1.5,
          'circle-stroke-color': '#0f172a'
        }
      });

      // 4. LOB (Kerteriz Işınları) Kaynağı
      map.addSource('tactical-lobs', {
        type: 'geojson',
        data: { type: 'FeatureCollection', features: [] }
      });

      map.addLayer({
        id: 'tactical-lobs-line',
        type: 'line',
        source: 'tactical-lobs',
        paint: {
          'line-color': '#f59e0b', // Amber/Sarı LOB
          'line-width': 2.2,
          'line-dasharray': [5, 3]
        }
      });

      // 5. Kinematik Hedef İntikal İzi Kaynakları & Katmanları
      map.addSource('tactical-track-lines', {
        type: 'geojson',
        data: { type: 'FeatureCollection', features: [] }
      });
      map.addSource('tactical-track-arrows', {
        type: 'geojson',
        data: { type: 'FeatureCollection', features: [] }
      });
      map.addSource('tactical-track-ellipses', {
        type: 'geojson',
        data: { type: 'FeatureCollection', features: [] }
      });
      map.addSource('tactical-track-points', {
        type: 'geojson',
        data: { type: 'FeatureCollection', features: [] }
      });

      // İntikal Rotası - Neon Dış Parlama Katmanı
      map.addLayer({
        id: 'tactical-track-lines-glow',
        type: 'line',
        source: 'tactical-track-lines',
        paint: {
          'line-color': '#00f0ff',
          'line-width': 7.5,
          'line-opacity': 0.35,
          'line-blur': 3
        }
      });

      // İntikal Rotası - Ana Kesikli Taktik Vektör Çizgisi
      map.addLayer({
        id: 'tactical-track-lines-main',
        type: 'line',
        source: 'tactical-track-lines',
        paint: {
          'line-color': '#00f0ff',
          'line-width': 3.2,
          'line-dasharray': [3, 2]
        }
      });

      // İntikal Rotası - Segment Üzeri Kerteriz/Yön Okları
      map.addLayer({
        id: 'tactical-track-arrows-symbol',
        type: 'symbol',
        source: 'tactical-track-arrows',
        layout: {
          'text-field': '▶',
          'text-font': ['Open Sans Bold', 'Arial Unicode MS Bold'],
          'text-size': 14,
          'text-rotate': ['get', 'bearing'],
          'text-rotation-alignment': 'map',
          'text-allow-overlap': true,
          'text-ignore-placement': true
        },
        paint: {
          'text-color': '#38bdf8',
          'text-halo-color': '#030712',
          'text-halo-width': 2.5
        }
      });

      // Zamansal Solma Efektli Geçmiş Kestirim Elipsleri
      map.addLayer({
        id: 'tactical-track-ellipses-fill',
        type: 'fill',
        source: 'tactical-track-ellipses',
        paint: {
          'fill-color': [
            'case',
            ['boolean', ['get', 'isLatest'], false], '#00f0ff',
            '#38bdf8'
          ],
          'fill-opacity': ['get', 'fillOpacity']
        }
      });

      map.addLayer({
        id: 'tactical-track-ellipses-line',
        type: 'line',
        source: 'tactical-track-ellipses',
        paint: {
          'line-color': [
            'case',
            ['boolean', ['get', 'isLatest'], false], '#ffffff',
            '#00f0ff'
          ],
          'line-width': [
            'case',
            ['boolean', ['get', 'isLatest'], false], 2.8,
            1.5
          ],
          'line-opacity': ['get', 'lineOpacity'],
          'line-dasharray': [4, 2]
        }
      });

      // İntikal Kestirim Noktaları - En Son Konum Halka Darbesi
      map.addLayer({
        id: 'tactical-track-points-pulse',
        type: 'circle',
        source: 'tactical-track-points',
        filter: ['==', ['get', 'isLatest'], true],
        paint: {
          'circle-radius': 16,
          'circle-color': 'rgba(0, 240, 255, 0.25)',
          'circle-stroke-width': 2,
          'circle-stroke-color': '#00f0ff'
        }
      });

      // İntikal Kestirim Noktaları - Merkez Daire
      map.addLayer({
        id: 'tactical-track-points-circle',
        type: 'circle',
        source: 'tactical-track-points',
        paint: {
          'circle-radius': [
            'case',
            ['boolean', ['get', 'isLatest'], false], 7.5,
            5
          ],
          'circle-color': [
            'case',
            ['boolean', ['get', 'isLatest'], false], '#00f0ff',
            '#0284c7'
          ],
          'circle-stroke-width': 2,
          'circle-stroke-color': '#ffffff'
        }
      });

      // İntikal Kestirim Noktaları - Kronolojik Sıra Rozeti (#1, #2 ... #N Son)
      map.addLayer({
        id: 'tactical-track-points-badge',
        type: 'symbol',
        source: 'tactical-track-points',
        layout: {
          'text-field': ['get', 'badgeLabel'],
          'text-font': ['Open Sans Bold', 'Arial Unicode MS Bold'],
          'text-size': 11,
          'text-offset': [0, 1.8],
          'text-anchor': 'top',
          'text-allow-overlap': false
        },
        paint: {
          'text-color': '#f8fafc',
          'text-halo-color': '#090e1a',
          'text-halo-width': 2.5
        }
      });

      // İntikal noktasına tıklama pop-up'ı
      map.on('click', 'tactical-track-points-circle', (e) => {
        if (!e.features || !e.features[0]) return;
        const p = e.features[0].properties;
        const coords = e.features[0].geometry.coordinates.slice();
        const html = `
          <div style="background:#090e1a; color:#f8fafc; font-family:sans-serif; padding:10px 12px; border-radius:6px; min-width:210px; font-size:12px; border:1px solid #00f0ff; box-shadow:0 6px 20px rgba(0,0,0,0.6);">
            <div style="font-weight:700; color:#00f0ff; margin-bottom:4px; font-size:13px;">
              🎯 Kestirim Noktası #${p.sequenceNo} ${p.isLatest ? '<span style="color:#22c55e;">(Son Konum)</span>' : ''}
            </div>
            <div style="color:#94a3b8; font-size:11px; margin-bottom:6px;">⏱️ ${p.time}</div>
            <div style="border-top:1px solid #1e293b; padding-top:6px; display:flex; flex-direction:column; gap:4px;">
              <div><strong>Hedef:</strong> ${p.targetName}</div>
              ${p.distanceFromPrev ? `<div><strong>Önceki Noktadan Mesafe:</strong> ${p.distanceFromPrev} km</div>` : ''}
              ${p.speedFromPrev ? `<div><strong>İntikal Sürati:</strong> ${p.speedFromPrev} km/s</div>` : ''}
            </div>
          </div>
        `;
        new maplibregl.Popup({ closeButton: true, closeOnClick: true, offset: 8 })
          .setLngLat(coords)
          .setHTML(html)
          .addTo(map);
      });
      map.on('mouseenter', 'tactical-track-points-circle', () => { map.getCanvas().style.cursor = 'pointer'; });
      map.on('mouseleave', 'tactical-track-points-circle', () => { map.getCanvas().style.cursor = ''; });

      // Tıklama Olayı (Hedef Seçimi)
      map.on('click', 'tactical-ellipses-fill', (e) => {
        if (e.features && e.features[0]) {
          const props = e.features[0].properties;
          if (onSelectTarget && props.id) {
            onSelectTarget(props.id, props.discipline, props);
          }
        }
      });

      // Cursor efekti
      map.on('mouseenter', 'tactical-ellipses-fill', () => {
        map.getCanvas().style.cursor = 'pointer';
      });
      map.on('mouseleave', 'tactical-ellipses-fill', () => {
        map.getCanvas().style.cursor = '';
      });

      // 0. Çoklu Elips Kesişim Bölgesine Tıklama (Ortak Bölge Detay HUD Pop-Up)
      const handleZoneClick = (e) => {
        if (!e.features || !e.features[0]) return;
        const feat = e.features[0];
        const props = feat.properties;
        const coordinates = feat.geometry.type === 'Point'
          ? feat.geometry.coordinates.slice()
          : [Number(props.centerLon), Number(props.centerLat)];

        const zoneNum = props.zoneNumber || 1;
        const zoneLabel = props.zoneLabel || `BÖLGE #${zoneNum}`;
        const overlapCount = props.overlapCount || 2;
        const badgeColor = overlapCount >= 5 ? '#ef4444' : (overlapCount >= 3 ? '#f97316' : '#eab308');

        let freqGroups = [];
        try {
          freqGroups = typeof props.frequencyGroupsJson === 'string'
            ? JSON.parse(props.frequencyGroupsJson)
            : (props.frequencyGroupsJson || []);
        } catch {
          freqGroups = [];
        }

        let targets = [];
        try {
          targets = typeof props.targets === 'string' ? JSON.parse(props.targets) : (props.targets || []);
        } catch {
          targets = props.targetsSummary ? [props.targetsSummary] : ['Kesişen Hedefler'];
        }

        const groupsHtml = freqGroups.map(g => {
          const emitters = g.emitterDetails || [];
          const refProfileText = `🎯 Küme Referansı: F: ${g.refFreqRange || (g.refFreqMhz + ' MHz')}`
            + (g.refPriRange && g.refPriRange !== '-' ? ` | PRI: ${g.refPriRange}` : '')
            + (g.refPwRange && g.refPwRange !== '-' ? ` | PW: ${g.refPwRange}` : '')
            + (g.refModulasyon && g.refModulasyon !== '-' ? ` | Mod: ${g.refModulasyon}` : '')
            + (g.refPlatform && g.refPlatform !== '-' ? ` | Plat: ${g.refPlatform}` : '');

          const emittersHtml = emitters.length > 0 ? emitters.map(em => {
            const isRadar = (em.discipline || 'RADAR').toUpperCase() !== 'MUHABERE';
            return `
            <div style="background:#0b1329; border:1px solid #1e293b; border-radius:5px; padding:7px 9px; margin-bottom:6px;">
              <div style="display:flex; justify-content:space-between; align-items:center;">
                <div style="display:flex; align-items:center; gap:6px; flex-wrap:wrap;">
                  <span style="background:${!isRadar ? 'rgba(168,85,247,0.2)' : 'rgba(56,189,248,0.2)'}; color:${!isRadar ? '#c084fc' : '#38bdf8'}; font-size:9px; font-weight:700; padding:1px 5px; border-radius:3px; border:1px solid ${!isRadar ? '#a855f7' : '#0284c7'};">
                    ${!isRadar ? 'COMINT' : 'RADAR'}
                  </span>
                  <span style="font-family:monospace; font-weight:700; color:#38bdf8; font-size:11px;">
                    ${em.id}
                  </span>
                  <span style="color:#f8fafc; font-weight:600; font-size:11px;">
                    ${em.name}
                  </span>
                </div>
                <span style="background:${em.similarityScore >= 85 ? '#065f46' : (em.similarityScore >= 70 ? '#78350f' : '#334155')}; color:${em.similarityScore >= 85 ? '#34d399' : (em.similarityScore >= 70 ? '#fde047' : '#cbd5e1')}; font-size:10.5px; font-weight:700; padding:2px 7px; border-radius:3px; border:1px solid ${em.similarityScore >= 85 ? '#059669' : (em.similarityScore >= 70 ? '#d97706' : '#475569')};">
                  %${em.similarityScore} Uyum
                </span>
              </div>

              <!-- 100 Puanlık Kriter Kırılımı -->
              <div style="display:flex; flex-direction:column; gap:3px; font-size:9.5px; color:#cbd5e1; margin-top:5px; background:#080d1a; padding:5px 7px; border-radius:3px; border-left:2px solid #38bdf8;">
                <div style="display:flex; flex-wrap:wrap; gap:6px;">
                  <span>📡 <strong>RF Zarfları:</strong> F: <code>${em.freqRange}</code> <strong style="color:#38bdf8;">(${em.freqScore}P)</strong></span>
                  ${em.priScore != null ? `<span>| PRI: <code>${em.priRange}</code> <strong style="color:#f59e0b;">(${em.priScore}P)</strong></span>` : ''}
                  ${em.pwScore != null ? `<span>| PW: <code>${em.pwRange}</code> <strong style="color:#a78bfa;">(${em.pwScore}P)</strong></span>` : ''}
                </div>
                <div style="display:flex; flex-wrap:wrap; gap:6px; color:#94a3b8;">
                  <span>⚡ <strong>Dalga/Mod:</strong> ${em.modulasyon || '-'} <strong style="color:#34d399;">(${em.waveformScore}P)</strong></span>
                  <span>| ⏰ <strong>Zaman:</strong> <strong style="color:#e2e8f0;">(${em.timeScore}P)</strong></span>
                  <span>| 🛡️ <strong>Taktik/Platform:</strong> ${em.platformOrtami || '-'}/${em.teshisKimlik || '-'} <strong style="color:#f43f5e;">(${em.tacticalScore}P)</strong></span>
                </div>
              </div>
            </div>
          `;
          }).join('') : `
            <div style="font-size:10px; color:#cbd5e1; margin-bottom:2px;">
              ${g.radars.slice(0, 4).join(', ')}${g.radars.length > 4 ? ` (+${g.radars.length - 4})` : ''}
            </div>
          `;

          return `
            <div style="background:rgba(15,23,42,0.75); border:1px solid #334155; border-radius:5px; padding:7px 9px; margin-bottom:8px;">
              <div style="display:flex; justify-content:space-between; align-items:center; margin-bottom:4px; border-bottom:1px solid #1e293b; padding-bottom:4px;">
                <span style="font-weight:700; color:#38bdf8; font-size:11px;">📡 ${g.bandName}</span>
                <span style="background:${g.similarityScore >= 80 ? '#10b981' : (g.similarityScore >= 65 ? '#f59e0b' : '#64748b')}; color:#fff; font-size:9.5px; font-weight:bold; padding:1px 6px; border-radius:3px;">
                  Küme Ort: %${g.similarityScore}
                </span>
              </div>
              <div style="font-size:9.5px; color:#94a3b8; margin-bottom:5px; font-family:monospace; background:rgba(30,41,59,0.5); padding:3px 6px; border-radius:3px;">
                ${refProfileText}
              </div>
              <div style="margin-top:4px;">
                ${emittersHtml}
              </div>
            </div>
          `;
        }).join('');

        const targetsListHtml = targets.map((t) => `
          <div style="display:flex; align-items:center; gap:6px; margin-bottom:3px; color:#cbd5e1; font-size:10.5px;">
            <span style="color:#ef4444; font-size:10px;">⚡</span>
            <span style="font-weight:500;">${t}</span>
          </div>
        `).join('');

        // Dinamik Çok Kriterli Taktik Özet Hesaplama
        const allTimes = freqGroups.flatMap(g => (g.emitterDetails || []).map(e => e.sonTespitZamani ? new Date(e.sonTespitZamani).getTime() : null)).filter(Boolean);
        let timeEval = 'Eşzamanlı (Son 30 dk içinde aktif)';
        if (allTimes.length >= 2) {
          const maxDiffSec = (Math.max(...allTimes) - Math.min(...allTimes)) / 1000;
          if (maxDiffSec <= 300) timeEval = 'Eşzamanlı / Tam Senkron (Fark < 5 dk)';
          else if (maxDiffSec <= 1800) timeEval = 'Yüksek Zamansal Çakışma (Fark < 30 dk)';
          else if (maxDiffSec <= 7200) timeEval = 'Orta Zamansal Çakışma (Fark < 2 saat)';
          else timeEval = 'Ayrık Farklı Zamanlar (Fark > 2 saat)';
        }

        let rfEval;
        if (freqGroups.length > 1) {
          rfEval = `Ayrık Farklı Bantlar (${freqGroups.map(g => g.bandName.split(' ')[0]).join(' + ')})`;
        } else {
          rfEval = `Uyumlu Tek Bant (${freqGroups[0]?.bandName.split(' ')[0] || 'X-Bant'})`;
        }

        const allIdentities = Array.from(new Set(freqGroups.flatMap(g => (g.emitterDetails || []).map(e => (e.teshisKimlik || '').toUpperCase()))));
        const hasFriendly = allIdentities.some(id => id.includes('DOST'));
        const hasHostile = allIdentities.some(id => id.includes('DUSMAN'));
        let tacticalEval;
        if (hasFriendly && hasHostile) {
          tacticalEval = 'Karşıt / Heterojen (DOST vs DÜŞMAN - Temas / IFF Teyidi Gerekli)';
        } else if (hasHostile) {
          tacticalEval = 'Homojen DÜŞMAN Unsurları';
        } else if (hasFriendly) {
          tacticalEval = 'Homojen DOST Unsurlar';
        } else {
          tacticalEval = 'Karma / Tanımsız';
        }

        const html = `
          <div style="background:#090e1d; color:#f8fafc; padding:12px 14px; border:1.5px solid ${badgeColor}; border-radius:8px; font-family:'Inter',sans-serif; min-width:340px; max-width:440px; box-shadow:0 12px 35px rgba(0,0,0,0.95);">
            <div style="display:flex; align-items:center; justify-content:space-between; border-bottom:1px solid #1e293b; padding-bottom:7px; margin-bottom:8px;">
              <div style="display:flex; align-items:center; gap:6px;">
                <span style="background:${badgeColor}; color:#fff; width:22px; height:22px; display:inline-flex; align-items:center; justify-content:center; border-radius:50%; font-weight:800; font-size:11px;">#${zoneNum}</span>
                <span style="font-weight:800; color:#f87171; font-size:13px; letter-spacing:0.5px;">${zoneLabel} KESİŞİM ODAĞI</span>
              </div>
              <span style="background:rgba(239,68,68,0.15); border:1px solid ${badgeColor}; color:${badgeColor}; font-size:10px; font-weight:bold; padding:2px 7px; border-radius:4px;">${overlapCount} Kesişen Elips</span>
            </div>

            <!-- Dinamik Çok Kriterli Taktik Özet -->
            <div style="background:rgba(15,23,42,0.85); border:1px solid #3b82f6; border-radius:5px; padding:6px 9px; margin-bottom:8px; font-size:10px; line-height:1.45;">
              <div style="font-weight:700; color:#60a5fa; margin-bottom:3px; text-transform:uppercase; font-size:9px; letter-spacing:0.5px;">🎯 Çok Kriterli Taktik Değerlendirme Özeti:</div>
              <div style="color:#cbd5e1; margin-bottom:1px;">📍 <strong>Mekânsal & Zamansal:</strong> <span style="color:#fde047;">${timeEval}</span></div>
              <div style="color:#cbd5e1; margin-bottom:1px;">📡 <strong>RF Zarfı & Dalga:</strong> <span style="color:#38bdf8;">${rfEval}</span></div>
              <div style="color:#cbd5e1;">🛡️ <strong>Taktik Teşhis:</strong> <span style="color:${hasFriendly && hasHostile ? '#f87171' : '#34d399'}; font-weight:700;">${tacticalEval}</span></div>
            </div>

            <!-- Doktrin Bilgi Notu: Matematiksel Benzerlik Skoru Açıklaması -->
            <div style="background:rgba(56,189,248,0.08); border-left:3px solid #38bdf8; border-radius:3px; padding:5px 8px; margin-bottom:8px; font-size:10px; color:#93c5fd; line-height:1.35;">
              <strong>ℹ️ Kanıtlanabilir 100 Puanlık Model:</strong> Her yayının skoru; RF Zarfları (50P: Frekans 25P, PRI 15P, PW 10P), Dalga Biçimi (15P), Zaman Uyumu (15P) ve Taktik/Platform (20P) bileşenlerinin küme referansına göre kanıtlanabilir toplamıdır.
            </div>

            <div style="display:flex; justify-content:space-between; margin-bottom:4px; font-size:10.5px;">
              <span style="color:#94a3b8;">📍 Merkez Koordinat:</span>
              <span style="font-family:monospace; color:#e2e8f0; font-weight:600;">${Number(props.centerLat || coordinates[1]).toFixed(4)}° K, ${Number(props.centerLon || coordinates[0]).toFixed(4)}° D</span>
            </div>

            <div style="display:flex; justify-content:space-between; margin-bottom:6px; font-size:10.5px;">
              <span style="color:#94a3b8;">🛡️ Kapsanan Unsurlar:</span>
              <span style="font-weight:700; color:#facc15;">${targets.length} Adet Yayın / Sensör</span>
            </div>

            ${freqGroups.length > 0 ? `
              <div style="margin-top:6px; margin-bottom:6px;">
                <div style="font-size:10px; font-weight:700; color:#94a3b8; text-transform:uppercase; margin-bottom:4px; letter-spacing:0.5px;">📊 Frekans Bantları & Yayın Bazında Uyum:</div>
                <div style="max-height:220px; overflow-y:auto; padding-right:2px;">
                  ${groupsHtml}
                </div>
              </div>
            ` : `
              <div style="margin-top:6px; padding-top:6px; border-top:1px dashed #334155; max-height:130px; overflow-y:auto;">
                ${targetsListHtml}
              </div>
            `}

            <div style="margin-top:8px; padding:6px 8px; background:rgba(30,58,138,0.25); border:1px solid #1d4ed8; border-radius:4px; font-size:10px; color:#93c5fd; display:flex; align-items:center; gap:6px;">
              <span>💬</span>
              <span><strong>Kokpit Ajanına Sorun:</strong> <em>"${zoneNum}. kesişim bölgesini analiz et"</em></span>
            </div>
          </div>
        `;

        new maplibregl.Popup({ closeButton: true, closeOnClick: true, offset: 10 })
          .setLngLat(coordinates)
          .setHTML(html)
          .addTo(map);
      };

      map.on('click', 'tactical-intersection-zones-fill', handleZoneClick);
      map.on('click', 'tactical-intersection-centers-circle', handleZoneClick);
      map.on('click', 'tactical-intersection-centers-badge', handleZoneClick);
      map.on('mouseenter', 'tactical-intersection-zones-fill', () => { map.getCanvas().style.cursor = 'pointer'; });
      map.on('mouseleave', 'tactical-intersection-zones-fill', () => { map.getCanvas().style.cursor = ''; });
      map.on('mouseenter', 'tactical-intersection-centers-circle', () => { map.getCanvas().style.cursor = 'pointer'; });
      map.on('mouseleave', 'tactical-intersection-centers-circle', () => { map.getCanvas().style.cursor = ''; });
      map.on('mouseenter', 'tactical-intersection-centers-badge', () => { map.getCanvas().style.cursor = 'pointer'; });
      map.on('mouseleave', 'tactical-intersection-centers-badge', () => { map.getCanvas().style.cursor = ''; });

      // Katmanları doldur
      updateMapLayers(map);
    });

    return map;
  };

  // Haritayı Mount Etme (Ana Kart)
  useEffect(() => {
    if (!mapInstanceRef.current && mapContainerRef.current) {
      mapInstanceRef.current = initMap(mapContainerRef.current);
    }

    return () => {
      if (mapInstanceRef.current) {
        mapInstanceRef.current.remove();
        mapInstanceRef.current = null;
      }
    };
  }, []);

  // Filtreler, Sektör veya Veri Değiştiğinde Haritayı Güncelle
  useEffect(() => {
    if (mapInstanceRef.current) {
      updateMapLayers(mapInstanceRef.current);
    }
    if (modalMapInstanceRef.current) {
      updateMapLayers(modalMapInstanceRef.current);
    }
  }, [filteredItems, sectorFocus, showEllipses, showIntersectionZones, showLobs, minIntersectionFilter, activeTrack, trackPlaybackIdx]);

  // Modal Açıldığında Modal Haritasını Mount Et
  useEffect(() => {
    if (modalVisible) {
      setTimeout(() => {
        if (!modalMapInstanceRef.current && modalMapContainerRef.current) {
          modalMapInstanceRef.current = initMap(modalMapContainerRef.current);
        } else if (modalMapInstanceRef.current) {
          modalMapInstanceRef.current.resize();
          updateMapLayers(modalMapInstanceRef.current);
        }
      }, 150);
    } else {
      if (modalMapInstanceRef.current) {
        modalMapInstanceRef.current.remove();
        modalMapInstanceRef.current = null;
      }
    }
  }, [modalVisible]);

  // Ortak Filtre & Kontrol Çubuğu (Hem widget hem de büyütülmüş Modal için)
  const renderFilterBar = (isModal = false) => (
    <div
      style={{
        display: 'flex',
        flexWrap: 'wrap',
        gap: 10,
        alignItems: 'center',
        backgroundColor: '#0d1527',
        padding: '8px 12px',
        borderRadius: 6,
        border: '1px solid #1e293b',
        marginBottom: 10
      }}
    >
      {/* Sektör Odaklama */}
      <Space size={4}>
        <Text style={{ color: '#94a3b8', fontSize: 11 }}>📍 Sektör:</Text>
        <Select
          size="small"
          value={sectorFocus}
          onChange={handleSectorChange}
          style={{ width: 175 }}
          options={[
            { label: '🌍 Tüm Harekât Sahası', value: 'ALL' },
            { label: '🌊 Ege / Çeşme Odak', value: 'EGE' },
            { label: '⚓ Doğu Akdeniz Odak', value: 'AKDENIZ' },
            { label: '🚢 Karadeniz Odak', value: 'KARADENIZ' },
            { label: '🛡️ Güneydoğu Hudut', value: 'GUNEYDOGU' },
            { label: '🌉 Marmara / Boğazlar', value: 'MARMARA' }
          ]}
        />
      </Space>

      {/* Zaman Süzgeci */}
      <Space size={4}>
        <Text style={{ color: '#94a3b8', fontSize: 11 }}>⏱️ Zaman:</Text>
        <Segmented
          size="small"
          value={timeRange}
          onChange={setTimeRange}
          options={[
            { label: '1 Saat', value: 'SON_1_SAAT' },
            { label: '6 Saat', value: 'SON_6_SAAT' },
            { label: '24 Saat', value: 'SON_24_SAAT' },
            { label: 'Tümü', value: 'TUMU' }
          ]}
          style={{ backgroundColor: '#1e293b', color: '#cbd5e1' }}
        />
      </Space>

      {/* Disiplin Süzgeci */}
      <Space size={4}>
        <Text style={{ color: '#94a3b8', fontSize: 11 }}>📡 Disiplin:</Text>
        <Select
          size="small"
          value={discipline}
          onChange={setDiscipline}
          style={{ width: 140 }}
          options={[
            { label: 'Tümü (Radar + Muh.)', value: 'TUMU' },
            { label: 'Radar (ELINT)', value: 'RADAR' },
            { label: 'Muhabere (COMINT)', value: 'MUHABERE' }
          ]}
        />
      </Space>

      {/* Kayıt Türü */}
      <Space size={4}>
        <Text style={{ color: '#94a3b8', fontSize: 11 }}>🎯 Tür:</Text>
        <Select
          size="small"
          value={recordType}
          onChange={setRecordType}
          style={{ width: 145 }}
          options={[
            { label: 'Tümü', value: 'TUMU' },
            { label: 'Kestirim Elipsi', value: 'KESTIRIM_ELIPS' },
            { label: 'Tek Yön LOB', value: 'HAM_LOB' }
          ]}
        />
      </Space>

      {/* Hedef İntikal İzi Seçici */}
      <Space size={4}>
        <Text style={{ color: '#00f0ff', fontSize: 11, fontWeight: 700 }}>🎯 İntikal İzi:</Text>
        <Select
          size="small"
          showSearch
          allowClear
          placeholder="Hedef İntikal İzi Seçin..."
          value={selectedTrackKey}
          onChange={handleSelectTrackTarget}
          loading={trackLoading}
          style={{ width: 195 }}
          options={availableTrackTargets.map(t => ({
            label: t.label || t.key,
            value: t.key
          }))}
          filterOption={(input, option) =>
            (option?.label ?? '').toLowerCase().includes(input.toLowerCase()) ||
            (option?.value ?? '').toLowerCase().includes(input.toLowerCase())
          }
        />
      </Space>

      {/* Katman Switch'leri ve Kesişim Eşiği */}
      <Space size={12} style={{ marginLeft: 'auto' }}>
        <Space size={4}>
          <Switch
            size="small"
            checked={showIntersectionZones}
            onChange={setShowIntersectionZones}
            style={{ backgroundColor: showIntersectionZones ? '#ef4444' : '#334155' }}
          />
          <Text style={{ color: '#cbd5e1', fontSize: 11, fontWeight: 600 }}>🎯 Kesişim Bölgeleri</Text>
          {showIntersectionZones && (
            <Select
              size="small"
              value={minIntersectionFilter}
              onChange={setMinIntersectionFilter}
              style={{ width: 145 }}
              options={[
                { label: '≥ 3 Elips (6 Odak)', value: 3 },
                { label: '≥ 2 Elips (Tümü)', value: 2 },
                { label: '≥ 4 Elips (Kritik)', value: 4 }
              ]}
            />
          )}
        </Space>

        <Space size={4}>
          <Switch
            size="small"
            checked={showEllipses}
            onChange={setShowEllipses}
            style={{ backgroundColor: showEllipses ? '#0284c7' : '#334155' }}
          />
          <Text style={{ color: '#cbd5e1', fontSize: 11 }}>📐 Elipsler</Text>
        </Space>

        <Space size={4}>
          <Switch
            size="small"
            checked={showLobs}
            onChange={setShowLobs}
            style={{ backgroundColor: showLobs ? '#f59e0b' : '#334155' }}
          />
          <Text style={{ color: '#cbd5e1', fontSize: 11 }}>📡 LOB</Text>
        </Space>

        {isModal && (
          <Tooltip title="Verileri Yeniden Sorgula">
            <Button
              type="text"
              size="small"
              icon={<ReloadOutlined style={{ color: '#38bdf8' }} />}
              loading={loading}
              onClick={fetchTacticalData}
            />
          </Tooltip>
        )}
      </Space>
    </div>
  );

  // 🎯 Kinematik Hedef İntikal ve Konum Geçmişi İzi HUD Kartı
  const renderTrackHud = () => {
    if (!activeTrack) return null;
    return (
      <div
        style={{
          position: 'absolute',
          top: 62,
          left: 10,
          width: 300,
          backgroundColor: 'rgba(9, 14, 26, 0.94)',
          backdropFilter: 'blur(8px)',
          border: '1px solid #00f0ff',
          borderRadius: 8,
          padding: '10px 12px',
          zIndex: 15,
          boxShadow: '0 8px 24px rgba(0, 240, 255, 0.25)',
          fontFamily: 'sans-serif'
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'flex-start', marginBottom: 6 }}>
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, flexWrap: 'wrap' }}>
              <Tag color="#00f0ff" style={{ color: '#090e1a', fontWeight: 800, margin: 0, fontSize: 10, borderRadius: 4 }}>
                🎯 İNTİKAL İZİ
              </Tag>
              <Tag color={activeTrack.source === 'RADAR' ? 'blue' : 'purple'} style={{ margin: 0, fontSize: 10, borderRadius: 4 }}>
                {activeTrack.source}
              </Tag>
            </div>
            <div style={{ color: '#ffffff', fontWeight: 700, fontSize: 12, marginTop: 4, lineHeight: '1.3' }}>
              {activeTrack.targetLabel}
            </div>
          </div>
          <Button
            type="text"
            size="small"
            icon={<CloseOutlined style={{ color: '#94a3b8', fontSize: 11 }} />}
            onClick={clearTrack}
            style={{ width: 20, height: 20, padding: 0 }}
          />
        </div>

        {/* Metrikler Izgarası */}
        <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 6, backgroundColor: '#050a15', padding: '6px 8px', borderRadius: 6, border: '1px solid #1e293b', marginBottom: 8, fontSize: 11 }}>
          <div>
            <span style={{ color: '#64748b', fontSize: 10 }}>Toplam Mesafe:</span>
            <div style={{ color: '#38bdf8', fontWeight: 700 }}>{activeTrack.totalDistanceKm ? `${activeTrack.totalDistanceKm} km` : '-'}</div>
          </div>
          <div>
            <span style={{ color: '#64748b', fontSize: 10 }}>Gözlem Süresi:</span>
            <div style={{ color: '#facc15', fontWeight: 700 }}>{activeTrack.timeSpanHours ? `${activeTrack.timeSpanHours} sa` : '-'}</div>
          </div>
          <div>
            <span style={{ color: '#64748b', fontSize: 10 }}>Ort. Sürat:</span>
            <div style={{ color: '#4ade80', fontWeight: 700 }}>{activeTrack.avgSpeedKmh ? `${activeTrack.avgSpeedKmh} km/s` : '-'}</div>
          </div>
          <div>
            <span style={{ color: '#64748b', fontSize: 10 }}>Genel Yön:</span>
            <div style={{ color: '#c084fc', fontWeight: 700 }}>
              {activeTrack.overallHeadingDeg != null ? `${activeTrack.overallHeadingDeg}° ${activeTrack.overallHeadingCardinal}` : '-'}
            </div>
          </div>
        </div>

        {/* Zaman Aralığı */}
        <div style={{ display: 'flex', justifyContent: 'space-between', color: '#94a3b8', fontSize: 10, marginBottom: 8 }}>
          <span>⏱️ {activeTrack.firstDetectedFormatted?.slice(11)}</span>
          <span style={{ color: '#00f0ff' }}>➔ {activeTrack.totalPoints} Kestirim</span>
          <span>⏱️ {activeTrack.lastDetectedFormatted?.slice(11)}</span>
        </div>

        {/* Oynatma ve Adım Kontrol Çubuğu */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 4, borderTop: '1px solid #1e293b', paddingTop: 6 }}>
          <Button
            size="small"
            icon={<StepBackwardOutlined />}
            disabled={trackPlaybackIdx === 0}
            onClick={() => setTrackPlaybackIdx(prev => Math.max(0, (prev ?? activeTrack.totalPoints - 1) - 1))}
            style={{ backgroundColor: '#1e293b', borderColor: '#334155', color: '#cbd5e1', fontSize: 11, padding: '0 6px' }}
          >
            Önceki
          </Button>

          <Button
            size="small"
            type={isPlaying ? 'default' : 'primary'}
            icon={isPlaying ? <PauseOutlined /> : <CaretRightOutlined />}
            onClick={() => setIsPlaying(!isPlaying)}
            style={{
              backgroundColor: isPlaying ? '#eab308' : '#0284c7',
              borderColor: isPlaying ? '#facc15' : '#38bdf8',
              color: '#ffffff',
              fontWeight: 700,
              fontSize: 11,
              flex: 1
            }}
          >
            {isPlaying ? 'Durdur' : 'Oynat'}
          </Button>

          <Button
            size="small"
            icon={<StepForwardOutlined />}
            disabled={trackPlaybackIdx === activeTrack.totalPoints - 1 || trackPlaybackIdx === null}
            onClick={() => setTrackPlaybackIdx(prev => Math.min(activeTrack.totalPoints - 1, (prev ?? 0) + 1))}
            style={{ backgroundColor: '#1e293b', borderColor: '#334155', color: '#cbd5e1', fontSize: 11, padding: '0 6px' }}
          >
            Sonraki
          </Button>

          <Button
            size="small"
            onClick={() => { setTrackPlaybackIdx(null); setIsPlaying(false); }}
            style={{ backgroundColor: '#1e293b', borderColor: '#334155', color: '#38bdf8', fontSize: 10, padding: '0 6px' }}
          >
            Tümü
          </Button>
        </div>

        {trackPlaybackIdx !== null && (
          <div style={{ textAlign: 'center', color: '#00f0ff', fontSize: 10, marginTop: 4 }}>
            📍 Gösterilen Adım: #{trackPlaybackIdx + 1} / {activeTrack.totalPoints}
          </div>
        )}
      </div>
    );
  };

  // Ortak Taktiksel Alt İstatistik Barı
  const renderStatsBar = () => (
    <div
      style={{
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        backgroundColor: '#0d1527',
        padding: '8px 12px',
        borderRadius: 6,
        border: '1px solid #1e293b',
        marginTop: 10
      }}
    >
      <Space size={14}>
        <Tag color="#ef4444" style={{ borderRadius: 10, fontWeight: 700 }}>
          🎯 Kesişim Bölgesi: {intersectionStats.totalZones} Alan
        </Tag>
        <Tag color="#f97316" style={{ borderRadius: 10, fontWeight: 700 }}>
          🔥 Zirve Örtüşme: {intersectionStats.maxOverlaps} Elips
        </Tag>
        <Tag color="#0284c7" style={{ borderRadius: 10 }}>
          📐 Aktif Elips: {intersectionStats.ellipseCount || 0}
        </Tag>
        <Tag color="#f59e0b" style={{ borderRadius: 10 }}>
          📡 Aktif LOB: {intersectionStats.lobCount || 0}
        </Tag>
      </Space>
    </div>
  );

  return (
    <Card
      style={{
        backgroundColor: '#090e1a',
        borderColor: '#1e293b',
        borderRadius: 8,
        boxShadow: '0 8px 24px rgba(0,0,0,0.6)',
        overflow: 'hidden',
        marginBottom: 16
      }}
      bodyStyle={{ padding: 12 }}
    >
      {/* 1. Üst Başlık & Eylem Çubuğu */}
      <div
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          borderBottom: '1px solid #1e293b',
          paddingBottom: 10,
          marginBottom: 10
        }}
      >
        <Space size={8}>
          <RadarChartOutlined style={{ color: '#38bdf8', fontSize: 18 }} />
          <Text strong style={{ color: '#f8fafc', fontSize: 14, letterSpacing: '0.04em' }}>
            🗺️ TAKTİK KESTİRİM & KESİŞİM BÖLGELERİ ANALİZİ
          </Text>
          <Tag color="cyan" style={{ borderRadius: 10, fontSize: 11, marginLeft: 6 }}>
            MapLibre GL + WebGL
          </Tag>
          <Tag color="blue" style={{ borderRadius: 10, fontSize: 11 }}>
            ⚡ İnce Projeksiyon DTO (%85 Hızlı)
          </Tag>
        </Space>

        <Space size={6}>
          <Tooltip title="Haritayı Tam Ekrana Genişlet">
            <Button
              type="text"
              size="small"
              icon={<FullscreenOutlined style={{ color: '#38bdf8' }} />}
              onClick={() => setModalVisible(true)}
            />
          </Tooltip>
          <Tooltip title="Verileri Yeniden Sorgula">
            <Button
              type="text"
              size="small"
              icon={<ReloadOutlined style={{ color: '#94a3b8' }} />}
              loading={loading}
              onClick={fetchTacticalData}
            />
          </Tooltip>
          {onClose && (
            <Tooltip title="Widget'ı Kapat">
              <Button
                type="text"
                size="small"
                icon={<CloseOutlined style={{ color: '#ef4444' }} />}
                onClick={onClose}
              />
            </Tooltip>
          )}
        </Space>
      </div>

      {/* 2. İnteraktif Taktiksel HUD Filtre Çubuğu */}
      {renderFilterBar(false)}

      {/* 3. Harita Sahnesi (MapLibre Container) */}
      <div style={{ position: 'relative', width: '100%', height: 420, borderRadius: 6, overflow: 'hidden' }}>
        <div ref={mapContainerRef} style={{ width: '100%', height: '100%' }} />

        {/* Yükleniyor Göstergesi */}
        {loading && (
          <div
            style={{
              position: 'absolute',
              inset: 0,
              backgroundColor: 'rgba(9, 14, 26, 0.65)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              zIndex: 10
            }}
          >
            <Spin tip="Taktik Kestirim Verileri Yükleniyor..." />
          </div>
        )}

        {/* Sol Üst Mini Pusula & Dinamik Sektör Rozeti */}
        <div
          style={{
            position: 'absolute',
            top: 10,
            left: 10,
            backgroundColor: 'rgba(15, 23, 42, 0.88)',
            border: '1px solid #1e293b',
            borderRadius: 6,
            padding: '6px 12px',
            pointerEvents: 'none',
            zIndex: 5,
            boxShadow: '0 4px 12px rgba(0,0,0,0.5)'
          }}
        >
          <div style={{ color: '#38bdf8', fontSize: 11, fontWeight: 700, letterSpacing: '0.04em' }}>
            🧭 {SECTOR_CONFIG[sectorFocus]?.name || 'TÜM HAREKÂT SAHASI'}
          </div>
          <div style={{ color: '#94a3b8', fontSize: 10, marginTop: 2 }}>
            {filteredItems.length} Emisyon / {intersectionStats.totalZones} Ortak Kesişim Bölgesi (Zirve: {intersectionStats.maxOverlaps} Kesişen Elips)
          </div>
        </div>

        {/* 🎯 Kinematik Hedef İntikal İzi HUD Kartı */}
        {renderTrackHud()}
      </div>

      {/* 4. Alt Taktiksel Özet HUD */}
      {renderStatsBar()}

      {/* 5. Tam Ekran Genişletme Modalı (Genişletilmiş Görünümde de Tüm Fonksiyonlar Aktif) */}
      <Modal
        title={
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', width: '100%', paddingRight: 24, color: '#f8fafc' }}>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <RadarChartOutlined style={{ color: '#38bdf8', fontSize: 16 }} />
              <span style={{ fontWeight: 700, letterSpacing: '0.04em' }}>
                GENİŞLETİLMİŞ TAKTİK KESTİRİM & KESİŞİM BÖLGELERİ (COP GÖRÜNÜMÜ)
              </span>
            </div>
            <Space size={8}>
              <Tag color="cyan" style={{ borderRadius: 10, fontSize: 11 }}>
                Canlı Taktik Ortak Resim
              </Tag>
            </Space>
          </div>
        }
        open={modalVisible}
        onCancel={() => setModalVisible(false)}
        width="96vw"
        style={{ top: 15 }}
        footer={null}
        styles={{ body: { backgroundColor: '#090e1a', padding: 14 } }}
      >
        {/* Modal İçi Filtre & Fonksiyonalite Çubuğu */}
        {renderFilterBar(true)}

        {/* Modal İçi Harita Sahnesi */}
        <div style={{ position: 'relative', width: '100%', height: 'calc(80vh - 90px)', minHeight: 520, borderRadius: 6, overflow: 'hidden' }}>
          <div ref={modalMapContainerRef} style={{ width: '100%', height: '100%' }} />

          {/* Yükleniyor Göstergesi */}
          {loading && (
            <div
              style={{
                position: 'absolute',
                inset: 0,
                backgroundColor: 'rgba(9, 14, 26, 0.65)',
                display: 'flex',
                alignItems: 'center',
                justifyContent: 'center',
                zIndex: 10
              }}
            >
              <Spin tip="Taktik Kestirim Verileri Yükleniyor..." />
            </div>
          )}

          {/* Sol Üst Mini Pusula & Dinamik Sektör Rozeti */}
          <div
            style={{
              position: 'absolute',
              top: 10,
              left: 10,
              backgroundColor: 'rgba(15, 23, 42, 0.88)',
              border: '1px solid #1e293b',
              borderRadius: 6,
              padding: '6px 12px',
              pointerEvents: 'none',
              zIndex: 5,
              boxShadow: '0 4px 12px rgba(0,0,0,0.5)'
            }}
          >
            <div style={{ color: '#38bdf8', fontSize: 11, fontWeight: 700, letterSpacing: '0.04em' }}>
              🧭 {SECTOR_CONFIG[sectorFocus]?.name || 'TÜM HAREKÂT SAHASI'}
            </div>
            <div style={{ color: '#94a3b8', fontSize: 10, marginTop: 2 }}>
              {filteredItems.length} Emisyon / {intersectionStats.totalZones} Ortak Kesişim Bölgesi (Zirve: {intersectionStats.maxOverlaps} Kesişen Elips)
            </div>
          </div>

          {/* 🎯 Kinematik Hedef İntikal İzi HUD Kartı (Modal COP) */}
          {renderTrackHud()}
        </div>

        {/* Modal İçi Alt Taktiksel Özet HUD */}
        {renderStatsBar()}
      </Modal>
    </Card>
  );
}
