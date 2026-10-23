import React, { useState, useEffect, useRef } from 'react';
import { Card, Segmented, Select, Button, Space, Typography, Spin, Tooltip, Empty, Tag, Modal } from 'antd';
import {
  FullscreenOutlined,
  ReloadOutlined,
  FireOutlined,
  ClockCircleOutlined,
  BarChartOutlined,
  AppstoreOutlined,
  BgColorsOutlined,
  CloseOutlined
} from '@ant-design/icons';
import * as echarts from 'echarts';
import axios from 'axios';

const { Text } = Typography;

// Metrik formatlayıcı yardımcı
function formatMetricValue(val, isDuration) {
  if (val === null || val === undefined || val === '-' || val === 0) return '0';
  if (isDuration) {
    if (val >= 3600) return `${(val / 3600).toFixed(1)} sa`;
    if (val >= 60) return `${(val / 60).toFixed(0)} dk`;
    return `${Math.round(val)}s`;
  }
  if (val >= 1000) return `${(val / 1000).toFixed(1)}k`;
  return `${val}`;
}

export default function CallsignHeatmapWidget({
  onClose,
  isMaximized,
  onToggleMaximize,
  initialData = null
}) {
  const [metric, setMetric] = useState('COUNT'); // 'COUNT' | 'DURATION'
  const [scaleType, setScaleType] = useState('PIECEWISE'); // 'PIECEWISE' | 'CONTINUOUS'
  const [timeRange, setTimeRange] = useState('SON_24_SAAT');
  const [limit, setLimit] = useState(15);
  const [data, setData] = useState(initialData || []);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);

  const chartRef = useRef(null);
  const modalChartRef = useRef(null);
  const chartInstance = useRef(null);
  const modalChartInstance = useRef(null);

  // Veri Çekme (REST API)
  const fetchMatrixData = async () => {
    setLoading(true);
    try {
      let baslangic = null;
      let bitis = null;
      if (timeRange === 'SON_1_SAAT') {
        baslangic = 'SON_1_SAAT';
      } else if (timeRange === 'SON_6_SAAT') {
        baslangic = 'SON_6_SAAT';
      } else if (timeRange === 'SON_24_SAAT') {
        baslangic = 'SON_24_SAAT';
      } else if (timeRange === 'ALL') {
        baslangic = 'ALL';
      }

      const params = { limit };
      if (baslangic) params.baslangicZamani = baslangic;
      if (bitis) params.bitisZamani = bitis;

      const res = await axios.get('/api/v1/tactical/comint/callsign-matrix', { params });
      if (Array.isArray(res.data)) {
        setData(res.data);
      }
    } catch (err) {
      console.error('Callsign matris verisi alınamadı:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchMatrixData();
  }, [timeRange, limit]);

  // Akıllı Kademeli Dilim Hesaplayıcı (Aşırı değer sapmasını önleyen quintile algoritması)
  const calculatePieces = (values, isDuration) => {
    if (!values || values.length === 0) {
      return [{ min: 0.1, label: 'Veri Yok', color: '#1e293b' }];
    }

    const sorted = [...values].sort((a, b) => a - b);
    const unique = Array.from(new Set(sorted));

    if (unique.length === 1) {
      return [{ min: unique[0], label: formatMetricValue(unique[0], isDuration), color: '#ef4444' }];
    }

    if (unique.length <= 3) {
      return [
        { min: unique[unique.length - 1], label: `Kritik (${formatMetricValue(unique[unique.length - 1], isDuration)})`, color: '#ef4444' },
        { min: unique[0], max: unique[unique.length - 1], label: `Standart (${formatMetricValue(unique[0], isDuration)})`, color: '#0284c7' }
      ];
    }

    // 5 Dilimli Taktik Kademeler (P20, P40, P60, P80)
    const p20 = sorted[Math.floor(sorted.length * 0.2)];
    const p40 = sorted[Math.floor(sorted.length * 0.4)];
    const p60 = sorted[Math.floor(sorted.length * 0.6)];
    const p80 = sorted[Math.floor(sorted.length * 0.8)];

    return [
      { min: p80, label: `Kritik Tepe (${formatMetricValue(p80, isDuration)}+)`, color: '#ef4444' },
      { min: p60, max: p80, label: `Yüksek (${formatMetricValue(p60, isDuration)} - ${formatMetricValue(p80, isDuration)})`, color: '#f97316' },
      { min: p40, max: p60, label: `Orta (${formatMetricValue(p40, isDuration)} - ${formatMetricValue(p60, isDuration)})`, color: '#eab308' },
      { min: p20, max: p40, label: `Rutin (${formatMetricValue(p20, isDuration)} - ${formatMetricValue(p40, isDuration)})`, color: '#0284c7' },
      { min: 0.1, max: p20, label: `Düşük (<${formatMetricValue(p20, isDuration)})`, color: '#6366f1' }
    ];
  };

  // ECharts Konfigürasyonu Üretici
  const buildChartOption = (sourceData, activeMetric, activeScaleType) => {
    if (!sourceData || sourceData.length === 0) return null;

    const isDuration = activeMetric === 'DURATION';

    // Tekil Çağrı Adları
    const cagriSet = new Set();
    const karsiSet = new Set();
    const activeValues = [];

    sourceData.forEach((item) => {
      if (item.cagriAdi) cagriSet.add(item.cagriAdi);
      if (item.karsiCagriAdi) karsiSet.add(item.karsiCagriAdi);

      const val = isDuration ? (item.toplamSureSn || 0) : item.konusmaAdedi;
      if (val > 0) activeValues.push(val);
    });

    const yAxisData = Array.from(cagriSet); // Arayan (Kaynak)
    const xAxisData = Array.from(karsiSet); // Karşı (Hedef)

    const map = new Map();
    let minVal = activeValues.length > 0 ? Math.min(...activeValues) : 1;
    let maxVal = activeValues.length > 0 ? Math.max(...activeValues) : 10;
    if (minVal === maxVal) minVal = 0;

    sourceData.forEach((item) => {
      const key = `${item.cagriAdi}__${item.karsiCagriAdi}`;
      map.set(key, item);
    });

    // Series Data: [xIndex, yIndex, value, record]
    // DİKKAT: İletişim olmayan hücrelere '-' verilir ki visualMap onları renklendirmesin!
    const seriesData = [];
    xAxisData.forEach((karsi, xIdx) => {
      yAxisData.forEach((cagri, yIdx) => {
        const key = `${cagri}__${karsi}`;
        const record = map.get(key);
        if (record) {
          const val = isDuration ? (record.toplamSureSn || 0) : record.konusmaAdedi;
          seriesData.push([xIdx, yIdx, val, record]);
        } else {
          seriesData.push([xIdx, yIdx, '-', null]);
        }
      });
    });

    // visualMap Konfigürasyonu
    let visualMapConfig;
    if (activeScaleType === 'PIECEWISE') {
      const pieces = calculatePieces(activeValues, isDuration);
      visualMapConfig = {
        type: 'piecewise',
        dimension: 2, // 3. eleman (val) üzerinden eşleştir
        orient: 'horizontal',
        left: 'center',
        bottom: 4,
        itemWidth: 16,
        itemHeight: 12,
        itemGap: 8,
        textStyle: { color: '#cbd5e1', fontSize: 11, fontWeight: 500 },
        pieces: pieces,
        outOfRange: {
          color: 'rgba(30, 41, 59, 0.25)' // Veri olmayan hücreler saydam koyu gri
        }
      };
    } else {
      // Sürekli (Continuous Gradient) Mod
      visualMapConfig = {
        type: 'continuous',
        dimension: 2, // 3. eleman (val) üzerinden eşleştir
        min: minVal,
        max: maxVal,
        calculable: true,
        orient: 'horizontal',
        left: 'center',
        bottom: 8,
        itemWidth: 14,
        itemHeight: 200,
        text: ['Yoğun', 'Düşük'],
        textStyle: { color: '#cbd5e1', fontSize: 11, fontWeight: 600 },
        inRange: {
          // Askeri C2 canlı termal spektrum (Koyu siyahtan başlamaz, açık mavi/indigo ile başlar!)
          color: ['#4338ca', '#0284c7', '#0d9488', '#eab308', '#f97316', '#ef4444']
        },
        outOfRange: {
          color: 'rgba(30, 41, 59, 0.25)'
        }
      };
    }

    return {
      backgroundColor: 'transparent',
      tooltip: {
        position: 'top',
        backgroundColor: 'rgba(15, 23, 42, 0.96)',
        borderColor: '#38bdf8',
        borderWidth: 1,
        textStyle: { color: '#f8fafc', fontSize: 12 },
        formatter: (params) => {
          const raw = params.data[3];
          if (!raw || params.data[2] === '-' || params.data[2] === 0) {
            return `<div style="font-size:11px; color:#94a3b8; padding:2px;">Etkileşim / Temas Yok</div>`;
          }

          const total = raw.konusmaAdedi || 1;
          const totalMin = (raw.toplamSureSn / 60).toFixed(1);
          const avgSec = (raw.toplamSureSn / total).toFixed(1);

          const vhfPct = ((raw.vhfAdet || 0) * 100 / total).toFixed(1);
          const hfPct = ((raw.hfAdet || 0) * 100 / total).toFixed(1);

          const kriptoPct = ((raw.kriptoAdet || 0) * 100 / total).toFixed(1);
          const sesPct = ((raw.sesAdet || 0) * 100 / total).toFixed(1);
          const veriPct = ((raw.veriAdet || 0) * 100 / total).toFixed(1);

          const isHeavyCrypto = (raw.kriptoAdet || 0) > (raw.sesAdet || 0);
          const isHeavyHf = (raw.hfAdet || 0) > (raw.vhfAdet || 0);

          return `
            <div style="font-weight:bold; font-size:13px; color:#38bdf8; margin-bottom:6px; border-bottom:1px solid #334155; padding-bottom:4px; display:flex; justify-content:space-between; align-items:center;">
              <span>📡 ${raw.cagriAdi} ➔ ${raw.karsiCagriAdi}</span>
              <span style="font-size:11px; color:#a5b4fc; font-weight:normal;">🗣️ ${raw.baskinLisan || '-'}</span>
            </div>
            <div style="display:flex; justify-content:space-between; gap:16px; margin-bottom:3px;">
              <span style="color:#cbd5e1;">📊 Toplam Temas:</span>
              <strong style="color:#f8fafc;">${raw.konusmaAdedi.toLocaleString()} görüşme</strong>
            </div>
            <div style="display:flex; justify-content:space-between; gap:16px; margin-bottom:3px;">
              <span style="color:#cbd5e1;">⏱️ Toplam Süre:</span>
              <strong style="color:#fbbf24;">${totalMin} dk (${Math.round(raw.toplamSureSn).toLocaleString()} sn)</strong>
            </div>
            <div style="display:flex; justify-content:space-between; gap:16px; margin-bottom:6px;">
              <span style="color:#cbd5e1;">⚡ Ort. Süre:</span>
              <strong style="color:#a7f3d0;">${avgSec} sn / temas</strong>
            </div>

            <!-- Frekans Bandı Dağılımı -->
            <div style="background:rgba(30,41,59,0.7); border-radius:4px; padding:6px 8px; margin-bottom:6px; font-size:11px;">
              <div style="color:#94a3b8; font-weight:600; margin-bottom:2px;">📻 Frekans Bandı Dağılımı:</div>
              <div style="display:flex; justify-content:space-between;">
                <span style="color:#38bdf8;">• VHF: <b>${(raw.vhfAdet || 0).toLocaleString()}</b> (%${vhfPct})</span>
                <span style="color:#e2e8f0;">• HF: <b>${(raw.hfAdet || 0).toLocaleString()}</b> (%${hfPct})</span>
              </div>
              ${isHeavyHf ? '<div style="color:#fbbf24; font-size:10px; margin-top:2px;">🌐 Uzak Mesafe HF Muhaberesi Baskın</div>' : ''}
            </div>

            <!-- Haberleşme Şekli Dağılımı -->
            <div style="background:rgba(30,41,59,0.7); border-radius:4px; padding:6px 8px; font-size:11px;">
              <div style="color:#94a3b8; font-weight:600; margin-bottom:2px;">🔒 Haberleşme Modu Dağılımı:</div>
              <div style="display:flex; justify-content:space-between; gap:8px;">
                <span style="color:${isHeavyCrypto ? '#ef4444' : '#f87171'};">🔐 KRİPTO: <b>${(raw.kriptoAdet || 0).toLocaleString()}</b> (%${kriptoPct})</span>
                <span style="color:#34d399;">🎙️ SES: <b>${(raw.sesAdet || 0).toLocaleString()}</b> (%${sesPct})</span>
                <span style="color:#60a5fa;">💾 VERİ: <b>${(raw.veriAdet || 0).toLocaleString()}</b> (%${veriPct})</span>
              </div>
              ${isHeavyCrypto ? '<div style="color:#ef4444; font-size:10px; font-weight:bold; margin-top:3px;">🔴 [AĞIRLIKLI KRİPTOLU - YÜKSEK GİZLİLİK]</div>' : ''}
            </div>
          `;
        }
      },
      grid: {
        top: 25,
        bottom: 85,
        left: 110,
        right: 40
      },
      xAxis: {
        type: 'category',
        data: xAxisData,
        splitArea: { show: true, areaStyle: { color: ['rgba(30, 41, 59, 0.25)', 'rgba(15, 23, 42, 0.35)'] } },
        axisLabel: {
          color: '#cbd5e1',
          rotate: xAxisData.length > 6 ? 35 : 0,
          fontSize: 11,
          fontWeight: 600
        },
        axisLine: { lineStyle: { color: '#334155' } }
      },
      yAxis: {
        type: 'category',
        data: yAxisData,
        splitArea: { show: true, areaStyle: { color: ['rgba(30, 41, 59, 0.25)', 'rgba(15, 23, 42, 0.35)'] } },
        axisLabel: {
          color: '#38bdf8',
          fontSize: 11,
          fontWeight: 600
        },
        axisLine: { lineStyle: { color: '#334155' } }
      },
      visualMap: visualMapConfig,
      series: [
        {
          name: isDuration ? 'Konuşma Süresi' : 'Görüşme Adedi',
          type: 'heatmap',
          coordinateSystem: 'cartesian2d',
          data: seriesData,
          encode: {
            x: 0,
            y: 1,
            value: 2,
            tooltip: [0, 1, 2]
          },
          itemStyle: {
            borderWidth: 1.5,
            borderColor: '#0f172a',
            borderRadius: 3
          },
          label: {
            show: true,
            color: '#ffffff',
            fontWeight: 600,
            fontSize: 10,
            formatter: (params) => {
              const val = params.data[2];
              if (val === '-' || !val || val === 0) return '';
              return formatMetricValue(val, isDuration);
            }
          },
          emphasis: {
            itemStyle: {
              shadowBlur: 10,
              shadowColor: 'rgba(255, 255, 255, 0.8)',
              borderColor: '#ffffff',
              borderWidth: 2
            }
          }
        }
      ]
    };
  };

  // Ana Kart ECharts Başlatma ve Güncelleme
  useEffect(() => {
    if (!chartRef.current || !data || data.length === 0) return;

    if (!chartInstance.current) {
      chartInstance.current = echarts.init(chartRef.current, 'dark');
    }

    const option = buildChartOption(data, metric, scaleType);
    if (option) {
      chartInstance.current.setOption(option, true);
      chartInstance.current.resize();
      setTimeout(() => { chartInstance.current?.resize(); }, 50);
      setTimeout(() => { chartInstance.current?.resize(); }, 250);
    } else {
      chartInstance.current.clear();
    }

    const handleResize = () => {
      chartInstance.current?.resize();
    };

    window.addEventListener('resize', handleResize);
    const ro = new ResizeObserver(() => {
      chartInstance.current?.resize();
    });
    ro.observe(chartRef.current);

    return () => {
      window.removeEventListener('resize', handleResize);
      ro.disconnect();
    };
  }, [data, metric, scaleType]);

  // Modal ECharts Başlatma ve Güncelleme (Büyütülmüş Görünüm)
  const renderModalChart = () => {
    if (!modalChartRef.current || !data) return;
    try {
      let inst = echarts.getInstanceByDom(modalChartRef.current);
      if (!inst) {
        inst = echarts.init(modalChartRef.current);
      }
      modalChartInstance.current = inst;
      const option = buildChartOption(data, metric, scaleType);
      if (option) {
        inst.clear();
        inst.setOption(option, { notMerge: true, replaceMerge: ['series', 'xAxis', 'yAxis', 'visualMap'] });
        inst.resize();
      }
    } catch (err) {
      console.error('Modal ısı haritası render hatası:', err);
    }
  };

  useEffect(() => {
    if (!modalVisible || !data) return;
    renderModalChart();
    const t1 = setTimeout(renderModalChart, 50);
    const t2 = setTimeout(renderModalChart, 200);
    const t3 = setTimeout(renderModalChart, 400);
    return () => {
      clearTimeout(t1);
      clearTimeout(t2);
      clearTimeout(t3);
    };
  }, [modalVisible, data, metric, scaleType]);

  return (
    <Card
      style={{
        backgroundColor: '#0f172a',
        borderColor: '#1e293b',
        borderRadius: 8,
        display: 'flex',
        flexDirection: 'column',
        height: '100%',
        boxShadow: '0 4px 12px rgba(0,0,0,0.3)'
      }}
      bodyStyle={{
        padding: '12px 16px',
        display: 'flex',
        flexDirection: 'column',
        flex: 1,
        minHeight: 0
      }}
    >
      {/* Widget Üst Başlık ve Kontrol Çubuğu */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12, flexWrap: 'wrap', gap: 8 }}>
        <Space size={8} align="center">
          <FireOutlined style={{ color: '#ef4444', fontSize: 16 }} />
          <Text strong style={{ color: '#f8fafc', fontSize: 14 }}>
            Telsiz İletişim Isı Haritası
          </Text>
          <Tag color="cyan" style={{ fontSize: 11, padding: '0 6px' }}>
            COMINT Matrix
          </Tag>
        </Space>

        <Space size={6} wrap>
          {/* Metrik Seçimi: Adet vs Süre */}
          <Segmented
            size="small"
            value={metric}
            onChange={setMetric}
            options={[
              {
                label: (
                  <span style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '0 4px' }}>
                    <BarChartOutlined style={{ color: metric === 'COUNT' ? '#38bdf8' : '#94a3b8' }} />
                    Görüşme Adedi
                  </span>
                ),
                value: 'COUNT'
              },
              {
                label: (
                  <span style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '0 4px' }}>
                    <ClockCircleOutlined style={{ color: metric === 'DURATION' ? '#fbbf24' : '#94a3b8' }} />
                    Toplam Süre
                  </span>
                ),
                value: 'DURATION'
              }
            ]}
            style={{ backgroundColor: '#1e293b', border: '1px solid #334155' }}
          />

          {/* Skala Modu: Kademeli vs Sürekli */}
          <Segmented
            size="small"
            value={scaleType}
            onChange={setScaleType}
            options={[
              {
                label: (
                  <Tooltip title="Aşırı değer sapmalarını önleyen 5 katmanlı akıllı renk dilimi">
                    <span style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '0 4px' }}>
                      <AppstoreOutlined style={{ color: scaleType === 'PIECEWISE' ? '#4ade80' : '#94a3b8' }} />
                      Kademeli
                    </span>
                  </Tooltip>
                ),
                value: 'PIECEWISE'
              },
              {
                label: (
                  <Tooltip title="Kesintisiz termal renk geçişi (Doğrusal)">
                    <span style={{ display: 'flex', alignItems: 'center', gap: 4, padding: '0 4px' }}>
                      <BgColorsOutlined style={{ color: scaleType === 'CONTINUOUS' ? '#f472b6' : '#94a3b8' }} />
                      Sürekli
                    </span>
                  </Tooltip>
                ),
                value: 'CONTINUOUS'
              }
            ]}
            style={{ backgroundColor: '#1e293b', border: '1px solid #334155' }}
          />

          {/* Zaman Filtresi */}
          <Select
            size="small"
            value={timeRange}
            onChange={setTimeRange}
            style={{ width: 110 }}
            options={[
              { label: 'Son 24 Saat', value: 'SON_24_SAAT' },
              { label: 'Son 6 Saat', value: 'SON_6_SAAT' },
              { label: 'Son 1 Saat', value: 'SON_1_SAAT' },
              { label: 'Tüm Zamanlar', value: 'ALL' }
            ]}
          />

          {/* Yenile */}
          <Tooltip title="Verileri Yenile">
            <Button
              size="small"
              icon={<ReloadOutlined />}
              onClick={fetchMatrixData}
              loading={loading}
              style={{ backgroundColor: '#1e293b', borderColor: '#334155', color: '#cbd5e1' }}
            />
          </Tooltip>

          {/* Görünümü Büyüt (Modal Fullscreen) */}
          <Tooltip title="Görünümü Büyüt / Tam Ekran İncele">
            <Button
              size="small"
              type="primary"
              ghost
              icon={<FullscreenOutlined />}
              onClick={() => setModalVisible(true)}
              style={{ borderColor: '#38bdf8', color: '#38bdf8' }}
            >
              Büyüt
            </Button>
          </Tooltip>

          {/* Paneli Kapat */}
          {onClose && (
            <Tooltip title="Grafik Panelini Kapat (Tam Ekrana Dön)">
              <Button
                size="small"
                icon={<CloseOutlined />}
                onClick={onClose}
                style={{ backgroundColor: '#1e293b', borderColor: '#334155', color: '#94a3b8' }}
              />
            </Tooltip>
          )}
        </Space>
      </div>

      {/* Alt Bilgi / İpucu Notu */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6, fontSize: 11, color: '#94a3b8' }}>
        <span>
          Y Ekseni: <span style={{ color: '#38bdf8', fontWeight: 600 }}>Arayan Çağrı Adı</span> | X Ekseni: <span style={{ color: '#cbd5e1', fontWeight: 600 }}>Karşı Çağrı Adı</span>
        </span>
        <span style={{ fontStyle: 'italic' }}>
          * Detaylı döküm, frekans ve modülasyon oranları için imleci hücrelerin üzerine getirebilirsiniz.
        </span>
      </div>

      {/* Grafik Çizim Alanı */}
      <div style={{ flex: 1, position: 'relative', minHeight: 320, width: '100%' }}>
        {loading && (
          <div style={{
            position: 'absolute',
            inset: 0,
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            backgroundColor: 'rgba(15, 23, 42, 0.65)',
            zIndex: 10,
            borderRadius: 6
          }}>
            <Spin tip="Telsiz iletişim matrisi hesaplanıyor..." />
          </div>
        )}

        <div
          ref={chartRef}
          style={{
            width: '100%',
            height: '100%',
            minHeight: 340,
            display: data && data.length > 0 ? 'block' : 'none'
          }}
        />

        {(!data || data.length === 0) && !loading && (
          <div style={{ height: 340, display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <Empty description={<span style={{ color: '#94a3b8' }}>Seçili zaman aralığında çağrı etkileşim verisi bulunamadı</span>} />
          </div>
        )}
      </div>

      {/* Büyütülmüş Tam Ekran Modal Görünümü */}
      <Modal
        title={
          <Space>
            <FireOutlined style={{ color: '#ef4444' }} />
            <span style={{ color: '#f8fafc' }}>
              Taktik Telsiz İletişim Isı Haritası (Genişletilmiş Analiz Ekranı)
            </span>
            <Tag color="cyan">COMINT Adjacency Matrix</Tag>
          </Space>
        }
        open={modalVisible}
        afterOpenChange={(open) => {
          if (open) {
            renderModalChart();
            setTimeout(renderModalChart, 50);
            setTimeout(renderModalChart, 250);
          }
        }}
        onCancel={() => setModalVisible(false)}
        width="92vw"
        footer={[
          <Button key="close" type="primary" onClick={() => setModalVisible(false)}>
            Kapat
          </Button>
        ]}
        styles={{
          content: { backgroundColor: '#0f172a', borderColor: '#1e293b' },
          header: { backgroundColor: '#0f172a', borderBottom: '1px solid #1e293b' }
        }}
      >
        <div style={{ marginBottom: 12, display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Space>
            <Segmented
              size="middle"
              value={metric}
              onChange={setMetric}
              options={[
                { label: '📊 Görüşme Adedi (Temas Sayısı)', value: 'COUNT' },
                { label: '⏱️ Toplam Konuşma Süresi (Dakika/Saniye)', value: 'DURATION' }
              ]}
              style={{ backgroundColor: '#1e293b' }}
            />
            <Segmented
              size="middle"
              value={scaleType}
              onChange={setScaleType}
              options={[
                { label: '📊 Kademeli (Piecewise)', value: 'PIECEWISE' },
                { label: '🌈 Sürekli (Gradient)', value: 'CONTINUOUS' }
              ]}
              style={{ backgroundColor: '#1e293b' }}
            />
            <Select
              size="middle"
              value={limit}
              onChange={setLimit}
              style={{ width: 140 }}
              options={[
                { label: 'İlk 15 Çift', value: 15 },
                { label: 'İlk 25 Çift', value: 25 },
                { label: 'İlk 40 Çift', value: 40 }
              ]}
            />
          </Space>
          <Text type="secondary" style={{ fontSize: 12 }}>
            💡 Büyük ölçekli komuta hiyerarşisi ve muhabere yoğunluk düğümleri
          </Text>
        </div>
        <div
          ref={(el) => {
            modalChartRef.current = el;
            if (el && modalVisible && data) {
              renderModalChart();
              setTimeout(renderModalChart, 60);
            }
          }}
          style={{ width: '100%', height: '70vh', minHeight: 480 }}
        />
      </Modal>
    </Card>
  );
}
