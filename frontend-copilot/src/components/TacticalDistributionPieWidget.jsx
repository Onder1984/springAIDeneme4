import React, { useState, useEffect, useRef } from 'react';
import { Card, Segmented, Select, Button, Space, Typography, Spin, Tooltip, Empty, Tag, Modal } from 'antd';
import {
  FullscreenOutlined,
  ReloadOutlined,
  PieChartOutlined,
  CloseOutlined,
  RadarChartOutlined,
  SecurityScanOutlined,
  DotChartOutlined
} from '@ant-design/icons';
import * as echarts from 'echarts';
import axios from 'axios';

const { Text } = Typography;

const TACTICAL_COLORS = [
  '#38bdf8', // Canlı Camgöbeği
  '#ef4444', // Tehdit Kırmızı
  '#10b981', // Taktik Zümrüt
  '#f59e0b', // Uyarı Amber
  '#8b5cf6', // Mor / Kripto
  '#ec4899', // Pembe / Jitter
  '#06b6d4', // Turkuaz
  '#14b8a6', // Teal
  '#f97316', // Turuncu
  '#64748b'  // Diğer / Nötr Gri
];

export default function TacticalDistributionPieWidget({
  initialSource = 'RADAR',
  initialCriterion = 'TESHIS',
  onClose
}) {
  const [source, setSource] = useState(initialSource); // 'RADAR' | 'COMINT'
  const [criterion, setCriterion] = useState(initialCriterion);
  const [timeRange, setTimeRange] = useState('ALL'); // 'ALL' | 'SON_24_SAAT' | 'SON_6_SAAT' | 'SON_1_SAAT'
  const [chartMode, setChartMode] = useState('PIE'); // 'PIE' | 'DONUT' | 'ROSE'
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);

  const chartRef = useRef(null);
  const modalChartRef = useRef(null);
  const chartInstance = useRef(null);
  const modalChartInstance = useRef(null);

  // Prop senkronizasyonu
  useEffect(() => {
    if (initialSource) setSource(initialSource);
    if (initialCriterion) setCriterion(initialCriterion);
  }, [initialSource, initialCriterion]);

  // Kriter Seçenekleri
  const radarCriteria = [
    { label: '🎯 Radar Spot No (Takip No)', value: 'SPOT_NO' },
    { label: '🛡️ Teşhis / Tehdit Durumu', value: 'TESHIS' },
    { label: '🎯 Radar Görevi', value: 'GOREV' },
    { label: '📡 Polarizasyon', value: 'POLARIZASYON' },
    { label: '⚡ Modülasyon (MOP)', value: 'MODULASYON' },
    { label: '🌍 Platform Ortamı', value: 'PLATFORM' },
    { label: '📍 Hedef Yer / Sektör', value: 'HEDEF_YER' },
    { label: '🏰 Hedef Mevzi Bilgisi', value: 'MEVZI' },
    { label: '📻 NATO Frekans Bandı', value: 'FREKANS_BANDI' },
    { label: '🕵️ Hedef Kaynağı', value: 'HEDEF_KAYNAGI' },
    { label: '🏢 Veri Kaynağı (Kuvvet)', value: 'VERI_KAYNAGI' }
  ];

  const comintCriteria = [
    { label: '🗣️ Telsiz Çağrı Adı (Callsign)', value: 'CAGRI_ADI' },
    { label: '📻 Haberleşme Şekli', value: 'HABERLESME_SEKLI' },
    { label: '🗣️ Lisan Dağılımı', value: 'LISAN' },
    { label: '📶 Telsiz Bandı (VHF/HF)', value: 'BANT' },
    { label: '🛡️ Teşhis / Tehdit Durumu', value: 'TESHIS' },
    { label: '⚡ Modülasyon', value: 'MODULASYON' },
    { label: '📡 Protokol (DMR/D-Star)', value: 'PROTOKOL' },
    { label: '🔄 Çalışma Şekli (Duplex/Röle)', value: 'CALISMA_SEKLI' },
    { label: '📍 Hedef Yer / Sektör', value: 'HEDEF_YER' },
    { label: '🕵️ Hedef Kaynağı', value: 'HEDEF_KAYNAGI' },
    { label: '🏢 Veri Kaynağı (Kuvvet)', value: 'VERI_KAYNAGI' }
  ];

  // Kaynak değiştiğinde kriteri uyarla
  useEffect(() => {
    if (source === 'RADAR') {
      const valid = radarCriteria.some(c => c.value === criterion);
      if (!valid) setCriterion('TESHIS');
    } else {
      const valid = comintCriteria.some(c => c.value === criterion);
      if (!valid) setCriterion('HABERLESME_SEKLI');
    }
  }, [source]);

  // Veri Çekme
  const fetchDistribution = async () => {
    setLoading(true);
    try {
      const res = await axios.get('/api/v1/tactical/analytics/distribution', {
        params: {
          source,
          criterion,
          timeRange,
          limit: 8
        }
      });
      if (res.data) {
        setData(res.data);
      }
    } catch (err) {
      console.error('Dağılım verisi alınamadı:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchDistribution();
  }, [source, criterion, timeRange]);

  // ECharts Konfigürasyonu
  const getChartOption = (dto, isModal = false) => {
    if (!dto || !dto.items || dto.items.length === 0) return {};

    const pieData = dto.items.map(item => ({
      name: item.name,
      value: item.value,
      percentage: item.percentage
    }));

    const isRose = chartMode === 'ROSE';
    const isDonut = chartMode === 'DONUT';
    const isPie = chartMode === 'PIE';

    return {
      backgroundColor: 'transparent',
      tooltip: {
        trigger: 'item',
        backgroundColor: '#0f172a',
        borderColor: '#334155',
        borderWidth: 1,
        textStyle: { color: '#f8fafc', fontSize: 12 },
        formatter: (params) => {
          const count = params.value ? Number(params.value).toLocaleString('tr-TR') : 0;
          const pct = params.data.percentage || params.percent;
          return `
            <div style="padding: 4px 6px; min-width: 150px;">
              <div style="font-weight: 600; color: #38bdf8; margin-bottom: 4px; border-bottom: 1px solid #1e293b; padding-bottom: 2px;">
                ${params.name}
              </div>
              <div style="display: flex; justify-content: space-between; gap: 12px; margin-bottom: 2px;">
                <span style="color: #94a3b8;">Kayıt Adedi:</span>
                <span style="font-weight: 600; color: #f8fafc;">${count}</span>
              </div>
              <div style="display: flex; justify-content: space-between; gap: 12px;">
                <span style="color: #94a3b8;">Oran:</span>
                <span style="font-weight: 700; color: #4ade80;">%${pct}</span>
              </div>
            </div>
          `;
        }
      },
      legend: {
        type: 'scroll',
        orient: 'horizontal',
        bottom: 0,
        textStyle: { color: '#cbd5e1', fontSize: 11 },
        pageTextStyle: { color: '#94a3b8' },
        pageIconColor: '#38bdf8',
        pageIconInactiveColor: '#475569'
      },
      color: TACTICAL_COLORS,
      series: [
        {
          name: dto.criterionLabel,
          type: 'pie',
          radius: isRose
            ? (isModal ? ['20%', '70%'] : ['20%', '62%'])
            : (isDonut ? (isModal ? ['35%', '65%'] : ['35%', '60%']) : (isModal ? '60%' : '52%')),
          center: ['50%', '46%'],
          roseType: isRose ? 'radius' : false,
          avoidLabelOverlap: true,
          itemStyle: {
            borderRadius: isDonut ? 6 : 4,
            borderColor: '#0f172a',
            borderWidth: 2
          },
          label: {
            show: true,
            position: 'outside',
            formatter: (params) => {
              const count = params.value ? Number(params.value).toLocaleString('tr-TR') : 0;
              const pct = params.percent != null ? params.percent : (params.data?.percentage || 0);
              return `${params.name}\n${count} (%${pct})`;
            },
            color: '#cbd5e1',
            fontSize: isModal ? 12 : 10,
            fontWeight: 500,
            lineHeight: 14
          },
          labelLine: {
            show: true,
            length: isModal ? 15 : 10,
            length2: isModal ? 12 : 8,
            smooth: true,
            lineStyle: { color: '#475569', width: 1 }
          },
          emphasis: {
            label: {
              show: true,
              fontSize: 13,
              fontWeight: 'bold',
              color: '#38bdf8'
            },
            itemStyle: {
              shadowBlur: 10,
              shadowOffsetX: 0,
              shadowColor: 'rgba(56, 189, 248, 0.5)'
            }
          },
          data: pieData
        }
      ],
      // Ortada Toplam Sayaç Grafiği (Yalnızca Donut modunda)
      graphic: isDonut ? [
        {
          type: 'text',
          left: 'center',
          top: '38%',
          style: {
            text: dto.totalCount ? (dto.totalCount >= 1000 ? `${(dto.totalCount / 1000).toFixed(1)}k` : dto.totalCount) : '0',
            fill: '#f8fafc',
            fontSize: isModal ? 26 : 20,
            fontWeight: 'bold',
            textAlign: 'center'
          }
        },
        {
          type: 'text',
          left: 'center',
          top: isModal ? '48%' : '47%',
          style: {
            text: 'Toplam',
            fill: '#94a3b8',
            fontSize: 11,
            textAlign: 'center'
          }
        }
      ] : []
    };
  };

  // Render & Resize chart
  useEffect(() => {
    if (!chartRef.current || !data) return;

    if (!chartInstance.current) {
      chartInstance.current = echarts.init(chartRef.current, 'dark');
    }

    const option = getChartOption(data, false);
    chartInstance.current.clear();
    chartInstance.current.setOption(option, { notMerge: true, replaceMerge: ['series', 'graphic', 'legend'] });
    setTimeout(() => {
      chartInstance.current?.resize();
    }, 50);

    const handleResize = () => chartInstance.current?.resize();
    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, [data, chartMode]);

  // Modal Chart Render Fonksiyonu
  const renderModalChart = () => {
    if (!modalChartRef.current || !data) return;
    try {
      let inst = echarts.getInstanceByDom(modalChartRef.current);
      if (!inst) {
        inst = echarts.init(modalChartRef.current, 'dark');
      }
      modalChartInstance.current = inst;
      const option = getChartOption(data, true);
      inst.clear();
      inst.setOption(option, { notMerge: true, replaceMerge: ['series', 'graphic', 'legend'] });
      inst.resize();
    } catch (err) {
      console.error('Modal dağılım grafiği render hatası:', err);
    }
  };

  // Modal Görünürlüğü ve Veri Değişiminde Tetikleme
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
  }, [modalVisible, data, chartMode]);

  return (
    <Card
      style={{
        backgroundColor: '#0f172a',
        borderColor: '#1e293b',
        borderRadius: 8,
        height: '100%',
        display: 'flex',
        flexDirection: 'column'
      }}
      bodyStyle={{
        padding: 12,
        flex: 1,
        display: 'flex',
        flexDirection: 'column',
        minHeight: 0
      }}
    >
      {/* Üst Başlık & Kontrol Çubuğu */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 10, flexWrap: 'wrap', gap: 8 }}>
        <Space size={6}>
          <PieChartOutlined style={{ color: '#38bdf8', fontSize: 16 }} />
          <Text strong style={{ color: '#f8fafc', fontSize: 14 }}>
            Taktik Dağılım Analizi
          </Text>
          <Tag color={source === 'RADAR' ? 'blue' : 'purple'} style={{ fontSize: 11, padding: '0 6px' }}>
            {source === 'RADAR' ? 'ELINT' : 'COMINT'}
          </Tag>
        </Space>

        <Space size={6} wrap>
          {/* Kaynak Seçimi: Radar vs Muhabere */}
          <Segmented
            size="small"
            value={source}
            onChange={setSource}
            options={[
              { label: '📡 Radar', value: 'RADAR' },
              { label: '📻 Muhabere', value: 'COMINT' }
            ]}
            style={{ backgroundColor: '#1e293b', border: '1px solid #334155' }}
          />

          {/* Kriter Seçimi Dropdown */}
          <Select
            size="small"
            value={criterion}
            onChange={setCriterion}
            style={{ minWidth: 170 }}
            options={source === 'RADAR' ? radarCriteria : comintCriteria}
          />

          {/* Zaman Filtresi */}
          <Select
            size="small"
            value={timeRange}
            onChange={setTimeRange}
            style={{ width: 110 }}
            options={[
              { label: 'Tüm Zamanlar', value: 'ALL' },
              { label: 'Son 24 Saat', value: 'SON_24_SAAT' },
              { label: 'Son 6 Saat', value: 'SON_6_SAAT' },
              { label: 'Son 1 Saat', value: 'SON_1_SAAT' }
            ]}
          />

          {/* Mod Değiştirici: Pasta vs Halka vs Gül */}
          <Segmented
            size="small"
            value={chartMode}
            onChange={setChartMode}
            options={[
              { label: '🥧 Pasta', value: 'PIE' },
              { label: '🍩 Halka', value: 'DONUT' },
              { label: '🌹 Gül', value: 'ROSE' }
            ]}
            style={{ backgroundColor: '#1e293b', border: '1px solid #334155' }}
          />

          {/* Yenile */}
          <Tooltip title="Verileri Yenile">
            <Button
              size="small"
              icon={<ReloadOutlined />}
              onClick={fetchDistribution}
              loading={loading}
              style={{ backgroundColor: '#1e293b', borderColor: '#334155', color: '#cbd5e1' }}
            />
          </Tooltip>

          {/* Büyüt */}
          <Tooltip title="Görünümü Büyüt / Tam Ekran">
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

          {/* Kapat */}
          {onClose && (
            <Tooltip title="Grafik Panelini Kapat">
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

      {/* Alt Bilgi / Kriter Başlığı */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 6, fontSize: 11, color: '#94a3b8' }}>
        <span>
          Aktif Kriter: <span style={{ color: '#38bdf8', fontWeight: 600 }}>{data?.criterionLabel || criterion}</span>
        </span>
        <span style={{ fontStyle: 'italic' }}>
          * Toplam Kayıt: <strong style={{ color: '#f8fafc' }}>{data?.totalCount?.toLocaleString('tr-TR') || 0}</strong>
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
            <Spin tip="Kategorik dağılım hesaplanıyor..." />
          </div>
        )}

        <div
          ref={chartRef}
          style={{
            width: '100%',
            height: '100%',
            minHeight: 320,
            display: (data && data.items && data.items.length > 0) ? 'block' : 'none'
          }}
        />
        {(!data || !data.items || data.items.length === 0) && !loading && (
          <div style={{ height: '100%', display: 'flex', alignItems: 'center', justifyContent: 'center' }}>
            <Empty description={<span style={{ color: '#94a3b8' }}>Seçili kriterde veri bulunamadı</span>} />
          </div>
        )}
      </div>

      {/* Büyütülmüş Modal Görünümü */}
      <Modal
        title={
          <Space>
            <PieChartOutlined style={{ color: '#38bdf8' }} />
            <span style={{ color: '#f8fafc' }}>
              {data?.criterionLabel || 'Taktik Dağılım Analizi'} (Genişletilmiş Analiz Ekranı)
            </span>
            <Tag color={source === 'RADAR' ? 'blue' : 'purple'}>{source}</Tag>
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
        width="80vw"
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
              value={chartMode}
              onChange={setChartMode}
              options={[
                { label: '🥧 Pasta (Klasik)', value: 'PIE' },
                { label: '🍩 Donut Modu', value: 'DONUT' },
                { label: '🌹 Gül (Rose) Modu', value: 'ROSE' }
              ]}
              style={{ backgroundColor: '#1e293b' }}
            />
          </Space>
          <Text type="secondary" style={{ fontSize: 12 }}>
            💡 Dilimlerin üzerine gelerek net adet ve yüzde paylarını inceleyebilirsiniz.
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
          style={{ width: '100%', height: '65vh', minHeight: 450 }}
        />
      </Modal>
    </Card>
  );
}
