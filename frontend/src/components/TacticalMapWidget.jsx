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
  FireOutlined,
  AimOutlined,
  CloseOutlined,
  RadarChartOutlined,
  EyeOutlined,
  CompassOutlined,
  InfoCircleOutlined
} from '@ant-design/icons';
import maplibregl from 'maplibre-gl';
import 'maplibre-gl/dist/maplibre-gl.css';
import axios from 'axios';
import dayjs from 'dayjs';
import {
  generateEllipsePolygon,
  generateLobRay,
  calculateTacticalHeatmapGrid
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

// Sektör Konfigürasyonları ve Koordinat Merkezleri
const SECTOR_CONFIG = {
  ALL: { label: '🌍 Tüm Harekât Sahası', name: 'TÜM SAHA (MÜŞTEREK HAREKÂT ALANI)', center: [34.0, 39.0], zoom: 5.6 },
  EGE: { label: '🌊 Ege / Çeşme Odak', name: 'EGE DENİZİ / ÇEŞME-SAKIZ AÇIKLARI', center: [26.38, 38.35], zoom: 9.6 },
  AKDENIZ: { label: '⚓ Doğu Akdeniz Odak', name: 'DOĞU AKDENİZ / KIBRIS KUZEYİ', center: [33.25, 35.82], zoom: 9.6 },
  MARMARA: { label: '🌉 Marmara / Boğazlar', name: 'KUZEY EGE / ÇANAKKALE BOĞAZI', center: [26.20, 40.12], zoom: 9.6 },
  KARADENIZ: { label: '🚢 Karadeniz Odak', name: 'KARADENİZ / KIRIM GÜNEYİ', center: [34.50, 42.45], zoom: 8.8 },
  GUNEYDOGU: { label: '🛡️ Güneydoğu Hudut', name: 'GÜNEYDOĞU HUDUT HATTI', center: [42.15, 37.15], zoom: 9.6 }
};

export default function TacticalMapWidget({
  onClose,
  onSelectTarget,
  initialData = null,
  embeddedInModal = false
}) {
  // Filtre State'leri
  const [timeRange, setTimeRange] = useState('SON_24_SAAT'); // 'SON_1_SAAT' | 'SON_6_SAAT' | 'SON_24_SAAT' | 'TUMU'
  const [discipline, setDiscipline] = useState('TUMU'); // 'TUMU' | 'RADAR' | 'MUHABERE'
  const [recordType, setRecordType] = useState('TUMU'); // 'TUMU' | 'KESTIRIM_ELIPS' | 'HAM_LOB'
  const [sectorFocus, setSectorFocus] = useState('ALL'); // 'ALL' | 'EGE' | 'AKDENIZ' | 'MARMARA' | 'KARADENIZ' | 'GUNEYDOGU'

  // Katman Aç/Kapa State'leri
  const [showEllipses, setShowEllipses] = useState(true);
  const [showHeatmap, setShowHeatmap] = useState(true);
  const [showLobs, setShowLobs] = useState(true);

  // Veri ve Hesaplama State'leri
  const [loading, setLoading] = useState(false);
  const [radarEmissions, setRadarEmissions] = useState([]);
  const [comintEmissions, setComintEmissions] = useState([]);
  const [filteredItems, setFilteredItems] = useState([]);
  const [heatmapStats, setHeatmapStats] = useState({
    ellipseCount: 0,
    lobCount: 0,
    maxOverlaps: 0,
    peakCoord: null,
    peakScore: 0
  });

  // Modal (Büyüt / Tam Ekran) State'i
  const [modalVisible, setModalVisible] = useState(false);

  // DOM ve Map Referansları
  const mapContainerRef = useRef(null);
  const mapInstanceRef = useRef(null);
  const modalMapContainerRef = useRef(null);
  const modalMapInstanceRef = useRef(null);

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
          lat: item._lat,
          lon: item._lon,
          semiMajor: item._semiMajor,
          semiMinor: item._semiMinor,
          azimuth: item._azimuth
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

    // B. O(1) Analitik Grid Isı Haritası Hesaplama
    const heatmapResult = calculateTacticalHeatmapGrid(ellipseCandidates, 65);

    setHeatmapStats({
      ellipseCount: ellipseCandidates.length,
      lobCount: lobFeatures.length,
      maxOverlaps: heatmapResult.maxOverlaps,
      peakCoord: heatmapResult.peakCoordinate,
      peakScore: heatmapResult.peakScore
    });

    // C. Zirve Hedef Noktası (Hotspot Peak Pin)
    const peakFeatures = [];
    if (heatmapResult.peakCoordinate) {
      peakFeatures.push({
        type: 'Feature',
        geometry: {
          type: 'Point',
          coordinates: heatmapResult.peakCoordinate
        },
        properties: {
          title: 'Zirve Hedef Odağı (Hotspot Peak)',
          overlaps: heatmapResult.maxOverlaps
        }
      });
    }

    // D. MapLibre GeoJSON Kaynaklarını Güncelle
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

    const heatmapSource = map.getSource('tactical-heatmap-points');
    if (heatmapSource) {
      heatmapSource.setData({
        type: 'FeatureCollection',
        features: heatmapResult.features
      });
    }

    const peakSource = map.getSource('tactical-peak');
    if (peakSource) {
      peakSource.setData({ type: 'FeatureCollection', features: peakFeatures });
    }

    // E. Katman Görünürlüklerini Ayarla
    if (map.getLayer('tactical-ellipses-fill')) {
      map.setLayoutProperty('tactical-ellipses-fill', 'visibility', showEllipses ? 'visible' : 'none');
      map.setLayoutProperty('tactical-ellipses-line', 'visibility', showEllipses ? 'visible' : 'none');
      map.setLayoutProperty('tactical-ellipse-centers-circle', 'visibility', showEllipses ? 'visible' : 'none');
    }

    if (map.getLayer('tactical-heatmap-layer')) {
      map.setLayoutProperty('tactical-heatmap-layer', 'visibility', showHeatmap ? 'visible' : 'none');
      map.setLayoutProperty('tactical-peak-circle', 'visibility', showHeatmap ? 'visible' : 'none');
      map.setLayoutProperty('tactical-peak-pulse', 'visibility', showHeatmap ? 'visible' : 'none');
    }

    if (map.getLayer('tactical-lobs-line')) {
      map.setLayoutProperty('tactical-lobs-line', 'visibility', showLobs ? 'visible' : 'none');
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
      // 1. Isı Haritası Noktaları Kaynağı (WebGL Heatmap)
      map.addSource('tactical-heatmap-points', {
        type: 'geojson',
        data: { type: 'FeatureCollection', features: [] }
      });

      map.addLayer({
        id: 'tactical-heatmap-layer',
        type: 'heatmap',
        source: 'tactical-heatmap-points',
        maxzoom: 14,
        paint: {
          'heatmap-weight': ['get', 'weight'],
          'heatmap-intensity': ['interpolate', ['linear'], ['zoom'], 4, 1.2, 10, 3.5],
          'heatmap-color': [
            'interpolate',
            ['linear'],
            ['heatmap-density'],
            0.0, 'rgba(0,0,0,0)',
            0.15, 'rgba(34, 211, 238, 0.45)', // Neon Cyan
            0.35, 'rgba(56, 189, 248, 0.70)', // Light Sky Blue
            0.60, 'rgba(250, 204, 21, 0.85)', // Tactical Yellow
            0.80, 'rgba(249, 115, 22, 0.92)', // Warning Orange
            1.0, 'rgba(239, 68, 68, 0.98)'    // Hotspot Red
          ],
          'heatmap-radius': ['interpolate', ['linear'], ['zoom'], 4, 18, 9, 38],
          'heatmap-opacity': 0.82
        }
      });

      // 2. Kestirim Elipsleri Kaynağı & Katmanları
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

      // 5. Zirve Hedef Odağı (Peak Hotspot) Kaynağı
      map.addSource('tactical-peak', {
        type: 'geojson',
        data: { type: 'FeatureCollection', features: [] }
      });

      map.addLayer({
        id: 'tactical-peak-pulse',
        type: 'circle',
        source: 'tactical-peak',
        paint: {
          'circle-radius': 14,
          'circle-color': 'rgba(239, 68, 68, 0.25)',
          'circle-stroke-width': 2,
          'circle-stroke-color': '#ef4444'
        }
      });

      map.addLayer({
        id: 'tactical-peak-circle',
        type: 'circle',
        source: 'tactical-peak',
        paint: {
          'circle-radius': 6,
          'circle-color': '#ef4444',
          'circle-stroke-width': 2,
          'circle-stroke-color': '#ffffff'
        }
      });

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

  // Filtreler veya Veri Değiştiğinde Haritayı Güncelle
  useEffect(() => {
    if (mapInstanceRef.current) {
      updateMapLayers(mapInstanceRef.current);
    }
    if (modalMapInstanceRef.current) {
      updateMapLayers(modalMapInstanceRef.current);
    }
  }, [filteredItems, showEllipses, showHeatmap, showLobs]);

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
            🗺️ TAKTİK KESTİRİM & ISI HARİTASI ANALİZİ
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

        {/* Katman Switch'leri */}
        <Space size={12} style={{ marginLeft: 'auto' }}>
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
              checked={showHeatmap}
              onChange={setShowHeatmap}
              style={{ backgroundColor: showHeatmap ? '#ef4444' : '#334155' }}
            />
            <Text style={{ color: '#cbd5e1', fontSize: 11 }}>🔥 Isı Haritası</Text>
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
        </Space>
      </div>

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
            {filteredItems.length} Emisyon / {heatmapStats.ellipseCount} Kestirim Elipsi (Zirve: {heatmapStats.maxOverlaps} Kesişim)
          </div>
        </div>
      </div>

      {/* 4. Alt Taktiksel Özet HUD */}
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
          <Tag color="#0284c7" style={{ borderRadius: 10 }}>
            🎯 Aktif Elips: {heatmapStats.ellipseCount}
          </Tag>
          <Tag color="#f59e0b" style={{ borderRadius: 10 }}>
            📡 Aktif LOB: {heatmapStats.lobCount}
          </Tag>
          <Tag color="#ef4444" style={{ borderRadius: 10 }}>
            🔥 Zirve Kesişim: {heatmapStats.maxOverlaps} Elips
          </Tag>
        </Space>

        {heatmapStats.peakCoord && (
          <Space size={6}>
            <AimOutlined style={{ color: '#ef4444', fontSize: 14 }} />
            <Text style={{ color: '#f87171', fontSize: 11, fontWeight: 600 }}>
              Zirve Odak (Hotspot Peak): {heatmapStats.peakCoord[1].toFixed(3)}°N, {heatmapStats.peakCoord[0].toFixed(3)}°E
            </Text>
          </Space>
        )}
      </div>

      {/* 5. Tam Ekran Genişletme Modalı */}
      <Modal
        title={
          <div style={{ display: 'flex', alignItems: 'center', gap: 8, color: '#f8fafc' }}>
            <RadarChartOutlined style={{ color: '#38bdf8' }} />
            <span>GENİŞLETİLMİŞ TAKTİK KESTİRİM & ISI HARİTASI (COP GÖRÜNÜMÜ)</span>
          </div>
        }
        open={modalVisible}
        onCancel={() => setModalVisible(false)}
        width="95vw"
        style={{ top: 20 }}
        footer={null}
        styles={{ body: { backgroundColor: '#090e1a', padding: 12 } }}
      >
        <div style={{ width: '100%', height: '80vh', borderRadius: 6, overflow: 'hidden' }}>
          <div ref={modalMapContainerRef} style={{ width: '100%', height: '100%' }} />
        </div>
      </Modal>
    </Card>
  );
}
