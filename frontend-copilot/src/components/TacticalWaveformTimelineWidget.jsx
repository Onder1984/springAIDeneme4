import React, { useState, useEffect, useRef } from 'react';
import {
  Card,
  Select,
  Radio,
  Button,
  Space,
  Tag,
  Typography,
  Tooltip,
  Modal,
  Spin,
  Empty,
  Row,
  Col
} from 'antd';
import {
  LineChartOutlined,
  FullscreenOutlined,
  CloseOutlined,
  ReloadOutlined,
  ThunderboltOutlined,
  FieldTimeOutlined,
  CompassOutlined,
  RadarChartOutlined
} from '@ant-design/icons';
import axios from 'axios';
import * as echarts from 'echarts';

const { Text, Title } = Typography;

export default function TacticalWaveformTimelineWidget({
  initialSource = 'RADAR',
  initialTargetKey = 'SP048',
  onClose
}) {
  const [source, setSource] = useState(initialSource); // 'RADAR' | 'COMINT'
  const [targetKey, setTargetKey] = useState(initialTargetKey);
  const [metric, setMetric] = useState('FREKANS'); // 'FREKANS' | 'PRI' | 'PW'
  const [timeRange, setTimeRange] = useState('ALL'); // 'ALL' | 'SON_24_SAAT' | 'SON_6_SAAT' | 'SON_1_SAAT'
  const [data, setData] = useState(null);
  const [availableTargets, setAvailableTargets] = useState([]);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);

  const chartRef = useRef(null);
  const modalChartRef = useRef(null);
  const chartInstance = useRef(null);
  const modalChartInstance = useRef(null);

  // Veri Çekme
  const fetchTimeline = async (s = source, key = targetKey, tRange = timeRange) => {
    setLoading(true);
    try {
      const res = await axios.get('/api/v1/tactical/analytics/waveform-timeline', {
        params: {
          source: s,
          targetKey: key || undefined,
          timeRange: tRange,
          limit: 250
        }
      });
      if (res.data) {
        setData(res.data);
        if (res.data.availableTargets && res.data.availableTargets.length > 0) {
          setAvailableTargets(res.data.availableTargets);
        }
        if (!key && res.data.targetKey) {
          setTargetKey(res.data.targetKey);
        }
      }
    } catch (err) {
      console.error('Dalga formu zaman serisi alınamadı:', err);
    } finally {
      setLoading(false);
    }
  };

  // Prop senkronizasyonu
  useEffect(() => {
    if (initialSource) setSource(initialSource);
    if (initialTargetKey) setTargetKey(initialTargetKey);
  }, [initialSource, initialTargetKey]);

  // İlk yükleme ve kaynak değişimi
  useEffect(() => {
    fetchTimeline(source, targetKey, timeRange);
  }, [source, targetKey, timeRange]);

  // Kaynak değiştiğinde varsayılan metrik ve hedef kontrolü
  const handleSourceChange = (newSource) => {
    setSource(newSource);
    setMetric('FREKANS');
    setTargetKey(''); // servisin en aktif hedefi seçmesi için boşalt
  };

  // ECharts Konfigürasyonu (intraday-breaks-2 esintili çift grid)
  const getChartOption = (dto, selectedMetric) => {
    if (!dto || !dto.points || dto.points.length === 0) return {};

    const points = dto.points;
    const timestamps = points.map(p => p.timestampFormatted || '');

    // Seçilen metriğe göre değerleri hazırla
    let minValues = [];
    let rangeSpan = [];
    let centerValues = [];
    let unit = 'MHz';
    let metricLabel = 'Frekans';
    let primaryColor = '#38bdf8'; // Sky blue
    let areaColor = 'rgba(56, 189, 248, 0.22)';

    if (selectedMetric === 'PRI') {
      unit = 'µs';
      metricLabel = 'Darbe Tekrarlama Aralığı (PRI)';
      primaryColor = '#f59e0b'; // Amber
      areaColor = 'rgba(245, 158, 11, 0.22)';
      minValues = points.map(p => p.minPriMicroSec != null ? p.minPriMicroSec : p.centerPriMicroSec);
      rangeSpan = points.map(p => {
        if (p.maxPriMicroSec != null && p.minPriMicroSec != null) {
          return Math.max(0, p.maxPriMicroSec - p.minPriMicroSec);
        }
        return 0;
      });
      centerValues = points.map(p => p.centerPriMicroSec);
    } else if (selectedMetric === 'PW') {
      unit = 'µs';
      metricLabel = 'Darbe Genişliği (PW)';
      primaryColor = '#10b981'; // Emerald
      areaColor = 'rgba(16, 185, 129, 0.22)';
      minValues = points.map(p => p.minPwMicroSec != null ? p.minPwMicroSec : p.centerPwMicroSec);
      rangeSpan = points.map(p => {
        if (p.maxPwMicroSec != null && p.minPwMicroSec != null) {
          return Math.max(0, p.maxPwMicroSec - p.minPwMicroSec);
        }
        return 0;
      });
      centerValues = points.map(p => p.centerPwMicroSec);
    } else {
      // FREKANS
      unit = 'MHz';
      metricLabel = 'Frekans (RF)';
      primaryColor = '#38bdf8';
      areaColor = 'rgba(56, 189, 248, 0.22)';
      minValues = points.map(p => p.minFrekansMhz != null ? p.minFrekansMhz : p.centerFrekansMhz);
      rangeSpan = points.map(p => {
        if (p.maxFrekansMhz != null && p.minFrekansMhz != null) {
          return Math.max(0, p.maxFrekansMhz - p.minFrekansMhz);
        }
        return 0;
      });
      centerValues = points.map(p => p.centerFrekansMhz);
    }

    // Sinyal Genliği (dBm) - Alt Grid Bar verisi
    const amplitudes = points.map(p => p.genlikDbm != null ? p.genlikDbm : -90);

    return {
      backgroundColor: 'transparent',
      animation: true,
      animationDuration: 750,
      title: {
        show: false
      },
      tooltip: {
        trigger: 'axis',
        axisPointer: {
          type: 'cross',
          link: [{ xAxisIndex: 'all' }],
          lineStyle: {
            color: '#94a3b8',
            type: 'dashed',
            width: 1.2
          }
        },
        backgroundColor: '#0f172a',
        borderColor: '#334155',
        borderWidth: 1.5,
        textStyle: { color: '#f8fafc', fontSize: 12 },
        formatter: (params) => {
          if (!params || params.length === 0) return '';
          const idx = params[0].dataIndex;
          const pt = points[idx];
          if (!pt) return '';

          return `
            <div style="font-family: Inter, sans-serif; min-width: 230px; line-height: 1.5;">
              <div style="font-weight: 700; color: #38bdf8; border-bottom: 1px solid #334155; padding-bottom: 4px; margin-bottom: 6px; display: flex; justify-content: space-between;">
                <span>⏱️ ${pt.timestampFormatted || '-'}</span>
                <span style="color: #94a3b8; font-size: 11px;">[${pt.id || '-'}]</span>
              </div>
              <div style="margin-bottom: 3px;">
                <strong style="color: ${primaryColor};">⚡ Frekans:</strong>
                ${pt.minFrekansMhz != null ? `${pt.minFrekansMhz.toFixed(1)} - ${pt.maxFrekansMhz?.toFixed(1)} MHz` : '-'}
                <span style="color: #94a3b8; font-size: 11px;">(Merkez: ${pt.centerFrekansMhz?.toFixed(1) || '-'})</span>
              </div>
              ${pt.centerPriMicroSec != null ? `
                <div style="margin-bottom: 3px;">
                  <strong style="color: #f59e0b;">⏱️ PRI:</strong> ${pt.minPriMicroSec?.toFixed(1)} - ${pt.maxPriMicroSec?.toFixed(1)} µs
                </div>
              ` : ''}
              ${pt.centerPwMicroSec != null ? `
                <div style="margin-bottom: 3px;">
                  <strong style="color: #10b981;">📏 PW:</strong> ${pt.minPwMicroSec?.toFixed(2)} - ${pt.maxPwMicroSec?.toFixed(2)} µs
                </div>
              ` : ''}
              <div style="margin-bottom: 3px;">
                <strong style="color: #cbd5e1;">📶 Sinyal Gücü:</strong> <span style="color: #f43f5e; font-weight: 600;">${pt.genlikDbm != null ? `${pt.genlikDbm.toFixed(1)} dBm` : '-'}</span>
                ${pt.sureSn != null ? ` | Süre: <span style="color: #cbd5e1;">${pt.sureSn} sn</span>` : ''}
              </div>
              <div style="border-top: 1px solid #1e293b; padding-top: 4px; margin-top: 4px; font-size: 11px; color: #94a3b8;">
                <div>📍 Sektör: <span style="color: #e2e8f0;">${pt.yer || '-'}</span></div>
                ${pt.mevzi ? `<div>🏰 Mevzi: <span style="color: #e2e8f0;">${pt.mevzi}</span></div>` : ''}
                <div>🛡️ Teşhis: <span style="color: ${pt.teshis === 'DUSMAN' ? '#ef4444' : '#38bdf8'}; font-weight: 600;">${pt.teshis || '-'}</span></div>
              </div>
            </div>
          `;
        }
      },
      legend: {
        data: [`Merkez ${metricLabel}`, 'Zarf Aralığı (Min-Max Bandı)', 'Sinyal Gücü (dBm)'],
        top: 4,
        right: 16,
        textStyle: { color: '#cbd5e1', fontSize: 11 },
        icon: 'roundRect'
      },
      axisPointer: {
        link: [{ xAxisIndex: 'all' }],
        label: {
          backgroundColor: '#334155'
        }
      },
      grid: [
        // Üst Grid: Frekans / PRI / PW Dalga Formu Zarfı
        {
          left: 55,
          right: 25,
          top: 36,
          height: '52%'
        },
        // Alt Grid: Sinyal Gücü (Genlik dBm) Çubukları
        {
          left: 55,
          right: 25,
          top: '68%',
          height: '18%'
        }
      ],
      xAxis: [
        // Üst X Ekseni (Zaman)
        {
          type: 'category',
          data: timestamps,
          gridIndex: 0,
          scale: true,
          boundaryGap: false,
          axisLine: { lineStyle: { color: '#334155' } },
          axisLabel: {
            color: '#94a3b8',
            fontSize: 10,
            formatter: (val) => {
              if (!val) return '';
              const parts = val.split(' ');
              return parts.length > 1 ? parts[1] : val;
            }
          },
          splitLine: {
            show: true,
            lineStyle: { color: 'rgba(51, 65, 85, 0.4)', type: 'dashed' }
          }
        },
        // Alt X Ekseni (Senkronize Zaman)
        {
          type: 'category',
          data: timestamps,
          gridIndex: 1,
          boundaryGap: false,
          axisLine: { lineStyle: { color: '#334155' } },
          axisLabel: { show: false },
          splitLine: {
            show: true,
            lineStyle: { color: 'rgba(51, 65, 85, 0.2)', type: 'dashed' }
          }
        }
      ],
      yAxis: [
        // Üst Y Ekseni (Metrik Değeri: MHz veya µs)
        {
          scale: true,
          gridIndex: 0,
          name: `${metricLabel} (${unit})`,
          nameTextStyle: { color: primaryColor, fontSize: 11, padding: [0, 0, 4, 0] },
          splitNumber: 4,
          axisLine: { lineStyle: { color: '#334155' } },
          axisLabel: {
            color: '#94a3b8',
            fontSize: 10,
            formatter: (v) => `${Math.round(v)}`
          },
          splitLine: {
            lineStyle: { color: 'rgba(51, 65, 85, 0.5)' }
          }
        },
        // Alt Y Ekseni (Sinyal Gücü: dBm)
        {
          scale: true,
          gridIndex: 1,
          name: 'Genlik (dBm)',
          nameTextStyle: { color: '#f43f5e', fontSize: 10, padding: [0, 0, 2, 0] },
          splitNumber: 2,
          axisLine: { lineStyle: { color: '#334155' } },
          axisLabel: {
            color: '#94a3b8',
            fontSize: 9,
            formatter: (v) => `${Math.round(v)}`
          },
          splitLine: {
            lineStyle: { color: 'rgba(51, 65, 85, 0.3)' }
          }
        }
      ],
      dataZoom: [
        {
          type: 'inside',
          xAxisIndex: [0, 1],
          start: 0,
          end: 100
        },
        {
          type: 'slider',
          xAxisIndex: [0, 1],
          bottom: 6,
          height: 18,
          borderColor: '#334155',
          fillerColor: 'rgba(56, 189, 248, 0.15)',
          textStyle: { color: '#64748b', fontSize: 9 },
          handleStyle: { color: '#38bdf8' }
        }
      ],
      series: [
        // 1. Zarfın Tabanı (Görünmez Stack)
        {
          name: 'Min Sınır',
          type: 'line',
          data: minValues,
          xAxisIndex: 0,
          yAxisIndex: 0,
          stack: 'range-envelope',
          lineStyle: { opacity: 0 },
          symbol: 'none'
        },
        // 2. Zarfın Yüksekliği (Renkli Area)
        {
          name: 'Zarf Aralığı (Min-Max Bandı)',
          type: 'line',
          data: rangeSpan,
          xAxisIndex: 0,
          yAxisIndex: 0,
          stack: 'range-envelope',
          lineStyle: { opacity: 0 },
          symbol: 'none',
          areaStyle: {
            color: areaColor
          }
        },
        // 3. Merkez Frekans / Değer Çizgisi
        {
          name: `Merkez ${metricLabel}`,
          type: 'line',
          data: centerValues,
          xAxisIndex: 0,
          yAxisIndex: 0,
          showSymbol: true,
          symbolSize: 6,
          itemStyle: {
            color: primaryColor,
            borderColor: '#0f172a',
            borderWidth: 1.5
          },
          lineStyle: {
            width: 2.2,
            color: primaryColor
          }
        },
        // 4. Alt Grid: Sinyal Gücü (dBm) Çubukları
        {
          name: 'Sinyal Gücü (dBm)',
          type: 'bar',
          data: amplitudes,
          xAxisIndex: 1,
          yAxisIndex: 1,
          barMaxWidth: 14,
          itemStyle: {
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: '#f43f5e' },
              { offset: 1, color: '#be123c' }
            ]),
            borderRadius: [3, 3, 0, 0]
          }
        }
      ]
    };
  };

  // Chart Rendering
  useEffect(() => {
    if (chartRef.current && data && data.points && data.points.length > 0) {
      if (!chartInstance.current) {
        chartInstance.current = echarts.init(chartRef.current, 'dark');
      }
      const option = getChartOption(data, metric);
      chartInstance.current.setOption(option, true);
      setTimeout(() => {
        chartInstance.current?.resize();
      }, 50);
    }
  }, [data, metric]);

  // Modal Chart Render Fonksiyonu
  const renderModalChart = () => {
    if (!modalChartRef.current || !data || !data.points) return;
    try {
      let inst = echarts.getInstanceByDom(modalChartRef.current);
      if (!inst) {
        inst = echarts.init(modalChartRef.current, 'dark');
      }
      modalChartInstance.current = inst;
      const option = getChartOption(data, metric);
      inst.clear();
      inst.setOption(option, { notMerge: true, replaceMerge: ['series', 'xAxis', 'yAxis', 'grid'] });
      inst.resize();
    } catch (err) {
      console.error('Modal dalga formu grafik render hatası:', err);
    }
  };

  // Modal Görünürlüğü ve Metrik Değişiminde Tetikleme
  useEffect(() => {
    if (!modalVisible || !data || !data.points) return;
    renderModalChart();
    const t1 = setTimeout(renderModalChart, 50);
    const t2 = setTimeout(renderModalChart, 200);
    const t3 = setTimeout(renderModalChart, 400);
    return () => {
      clearTimeout(t1);
      clearTimeout(t2);
      clearTimeout(t3);
    };
  }, [modalVisible, data, metric]);

  // Window Resize
  useEffect(() => {
    const handleResize = () => {
      chartInstance.current?.resize();
      modalChartInstance.current?.resize();
    };
    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, []);

  return (
    <Card
      style={{
        backgroundColor: '#0b1329',
        borderColor: '#1e293b',
        borderRadius: 12,
        marginBottom: 16,
        boxShadow: '0 8px 24px rgba(0, 0, 0, 0.45)'
      }}
      styles={{ body: { padding: '14px 16px' } }}
    >
      {/* Üst Bar: Başlık, Hedef Bilgisi ve Kontroller */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 12, flexWrap: 'wrap', gap: 8 }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: 10 }}>
          <LineChartOutlined style={{ fontSize: 18, color: '#38bdf8' }} />
          <div>
            <Title level={5} style={{ margin: 0, color: '#f8fafc', fontSize: 14 }}>
              📈 Taktik Dalga Formu & Zamansal Değişim Analizi
            </Title>
            <div style={{ fontSize: 11, color: '#94a3b8', marginTop: 2 }}>
              {data?.targetLabel || (source === 'RADAR' ? 'Radar Spot No Takibi' : 'Muhabere Çağrı Adı Takibi')}
              {data?.totalCount ? ` (${data.totalCount} Temas / Intraday Kayıt)` : ''}
            </div>
          </div>
        </div>

        <Space size={6}>
          <Tooltip title="Veriyi Yenile">
            <Button
              size="small"
              icon={<ReloadOutlined />}
              onClick={() => fetchTimeline()}
              loading={loading}
              style={{ backgroundColor: '#1e293b', borderColor: '#334155', color: '#cbd5e1' }}
            />
          </Tooltip>
          <Tooltip title="Büyüt / Detaylı İncele">
            <Button
              size="small"
              icon={<FullscreenOutlined />}
              onClick={() => setModalVisible(true)}
              style={{ backgroundColor: '#1e293b', borderColor: '#334155', color: '#cbd5e1' }}
            />
          </Tooltip>
          {onClose && (
            <Tooltip title="Kapat">
              <Button
                size="small"
                icon={<CloseOutlined />}
                onClick={onClose}
                style={{ backgroundColor: '#1e293b', borderColor: '#334155', color: '#ef4444' }}
              />
            </Tooltip>
          )}
        </Space>
      </div>

      {/* Kontrol Şeridi: Kaynak, Hedef Seçici, Metrik Seçici ve Zaman */}
      <div
        style={{
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          backgroundColor: '#0f172a',
          padding: '8px 12px',
          borderRadius: 8,
          marginBottom: 12,
          border: '1px solid #1e293b',
          flexWrap: 'wrap',
          gap: 10
        }}
      >
        <Space size={12} wrap>
          {/* Kaynak Seçimi */}
          <Radio.Group
            size="small"
            value={source}
            onChange={(e) => handleSourceChange(e.target.value)}
            buttonStyle="solid"
          >
            <Radio.Button value="RADAR">📡 RADAR (Spot No)</Radio.Button>
            <Radio.Button value="COMINT">📻 COMINT (Çağrı Adı)</Radio.Button>
          </Radio.Group>

          {/* Hedef Seçici Dropdown */}
          <Select
            size="small"
            showSearch
            value={targetKey}
            onChange={(val) => setTargetKey(val)}
            placeholder={source === 'RADAR' ? 'Spot No Seçin...' : 'Çağrı Adı Seçin...'}
            style={{ minWidth: 220 }}
            options={availableTargets.map(t => ({
              label: t.label,
              value: t.key
            }))}
            filterOption={(input, option) =>
              (option?.label ?? '').toLowerCase().includes(input.toLowerCase())
            }
          />

          {/* Metrik Seçici (Radar için Frekans/PRI/PW, Comint için Frekans) */}
          <Radio.Group
            size="small"
            value={metric}
            onChange={(e) => setMetric(e.target.value)}
          >
            <Radio.Button value="FREKANS">⚡ Frekans (MHz)</Radio.Button>
            {source === 'RADAR' && <Radio.Button value="PRI">⏱️ PRI (µs)</Radio.Button>}
            {source === 'RADAR' && <Radio.Button value="PW">📏 PW (µs)</Radio.Button>}
          </Radio.Group>
        </Space>

        {/* Zaman Filtresi */}
        <Space size={6}>
          <Button
            size="small"
            type={timeRange === 'ALL' ? 'primary' : 'default'}
            onClick={() => setTimeRange('ALL')}
            style={{ fontSize: 11 }}
          >
            Tümü
          </Button>
          <Button
            size="small"
            type={timeRange === 'SON_24_SAAT' ? 'primary' : 'default'}
            onClick={() => setTimeRange('SON_24_SAAT')}
            style={{ fontSize: 11 }}
          >
            24 Saat
          </Button>
          <Button
            size="small"
            type={timeRange === 'SON_6_SAAT' ? 'primary' : 'default'}
            onClick={() => setTimeRange('SON_6_SAAT')}
            style={{ fontSize: 11 }}
          >
            6 Saat
          </Button>
          <Button
            size="small"
            type={timeRange === 'SON_1_SAAT' ? 'primary' : 'default'}
            onClick={() => setTimeRange('SON_1_SAAT')}
            style={{ fontSize: 11 }}
          >
            1 Saat
          </Button>
        </Space>
      </div>

      {/* Grafik Alanı */}
      <Spin spinning={loading}>
        <div
          ref={chartRef}
          style={{
            width: '100%',
            height: 320,
            display: data && data.points && data.points.length > 0 ? 'block' : 'none'
          }}
        />
        {(!data || !data.points || data.points.length === 0) && (
          <Empty
            description={
              <span style={{ color: '#94a3b8' }}>
                Seçilen hedef ({targetKey || 'Bilinmeyen'}) için belirtilen zaman aralığında temas kaydı bulunamadı.
              </span>
            }
            style={{ padding: '40px 0' }}
          />
        )}
      </Spin>

      {/* Alt Açıklama / Operasyonel Rehber */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginTop: 6, fontSize: 11, color: '#64748b' }}>
        <span>
          💡 <strong>İpucu:</strong> Üst grafikteki gölgeli alan hedefin <em>Min-Max bant genişliğini</em>, ortadaki çizgi ise <em>merkez frekansını</em> temsil eder.
        </span>
        <span>
          Alt panel: <strong>Sinyal Gücü (dBm)</strong> | Fırça ile zaman aralığı kaydırılabilir.
        </span>
      </div>

      {/* Büyütülmüş Modal Görünümü */}
      <Modal
        title={
          <span style={{ color: '#f8fafc' }}>
            📈 Taktik Dalga Formu & Zamansal Değişim Detayı: {data?.targetLabel}
          </span>
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
        footer={null}
        width={1100}
        styles={{
          content: { backgroundColor: '#0f172a', borderColor: '#334155' },
          header: { backgroundColor: '#0f172a', borderBottom: '1px solid #1e293b' }
        }}
      >
        <div style={{ marginBottom: 12, display: 'flex', gap: 12 }}>
          <Radio.Group
            size="small"
            value={metric}
            onChange={(e) => setMetric(e.target.value)}
          >
            <Radio.Button value="FREKANS">⚡ Frekans (MHz)</Radio.Button>
            {source === 'RADAR' && <Radio.Button value="PRI">⏱️ PRI (µs)</Radio.Button>}
            {source === 'RADAR' && <Radio.Button value="PW">📏 PW (µs)</Radio.Button>}
          </Radio.Group>
        </div>
        <div
          ref={(el) => {
            modalChartRef.current = el;
            if (el && modalVisible && data && data.points) {
              renderModalChart();
              setTimeout(renderModalChart, 60);
            }
          }}
          style={{ width: '100%', height: 500 }}
        />
      </Modal>
    </Card>
  );
}
