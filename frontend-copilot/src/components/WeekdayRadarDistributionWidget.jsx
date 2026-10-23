import React, { useState, useEffect, useRef } from 'react';
import {
  Card,
  Button,
  Space,
  Tag,
  Typography,
  Tooltip,
  Modal,
  Spin,
  Empty,
  Row,
  Col,
  Table,
  Segmented,
  Select
} from 'antd';
import {
  RadarChartOutlined,
  FullscreenOutlined,
  CloseOutlined,
  ReloadOutlined,
  FieldTimeOutlined,
  TableOutlined,
  AppstoreOutlined,
  CompassOutlined,
  AimOutlined,
  GlobalOutlined
} from '@ant-design/icons';
import axios from 'axios';
import * as echarts from 'echarts';

const { Text, Title } = Typography;

export default function WeekdayRadarDistributionWidget({
  initialSource = 'TUMU',
  initialRecordType = 'TUMU',
  initialTimeRange = 'ALL',
  onClose
}) {
  const [source, setSource] = useState(initialSource); // 'TUMU' | 'RADAR' | 'COMINT'
  const [recordType, setRecordType] = useState(initialRecordType); // 'TUMU' | 'SADECE_LOB' | 'SADECE_KESTIRIM'
  const [timeRange, setTimeRange] = useState(initialTimeRange); // 'ALL' | 'SON_7_GUN' | 'SON_30_GUN'
  const [viewMode, setViewMode] = useState('CHART'); // 'CHART' | 'TABLE'
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
    if (initialRecordType) setRecordType(initialRecordType);
    if (initialTimeRange) setTimeRange(initialTimeRange);
  }, [initialSource, initialRecordType, initialTimeRange]);

  // Veri Çekme
  const fetchDistribution = async (s = source, r = recordType, t = timeRange) => {
    setLoading(true);
    try {
      const res = await axios.get('/api/v1/tactical/analytics/weekday-radar', {
        params: {
          source: s,
          recordType: r,
          timeRange: t
        }
      });
      setData(res.data);
    } catch (err) {
      console.error('Haftalık LOB radar dağılım verisi çekilemedi:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchDistribution(source, recordType, timeRange);
  }, [source, recordType, timeRange]);

  // ECharts Konfigürasyonu
  const buildChartOption = (dto) => {
    if (!dto || !dto.days || dto.days.length === 0) return {};

    // Sabit adım genişliği ve sabit eksen tavanı:
    // Parametreler (Kaynak, Kayıt Tipi, Zaman Aralığı) seçildikçe adım genişliğinin ve aralıkların
    // dinamik değişmesini engellemek için sabit 50.000 adımlı ölçek kullanılır.
    const STEP_SIZE = 50000;
    const BASE_MAX = 200000;

    let maxValInData = 0;
    if (dto && dto.days) {
      dto.days.forEach((d) => {
        maxValInData = Math.max(
          maxValInData,
          d.radarTotalCount || 0,
          d.comintTotalCount || 0,
          d.radarLobCount || 0,
          d.radarKestirimCount || 0,
          d.comintLobCount || 0,
          d.comintKestirimCount || 0
        );
      });
    }

    const fixedMax = maxValInData > BASE_MAX
      ? Math.ceil(maxValInData / STEP_SIZE) * STEP_SIZE
      : BASE_MAX;
    const splitCount = Math.round(fixedMax / STEP_SIZE);

    const indicators = dto.days.map((d, idx) => ({
      name: d.dayName,
      max: fixedMax,
      axisLabel: {
        show: idx === 0, // Adım sayıları sadece dikey eksende (Pazartesi) temiz ve sabit şekilde görünür
        showMaxLabel: true,
        showMinLabel: false,
        color: '#94a3b8',
        fontSize: 10,
        fontWeight: 600,
        backgroundColor: 'rgba(15, 23, 42, 0.85)',
        padding: [2, 5],
        borderRadius: 3,
        formatter: (val) => {
          if (!val || val === 0) return '';
          return val.toLocaleString('tr-TR');
        }
      }
    }));

    // Kaynak ve Kayıt Tipine göre Seriler oluştur
    const seriesList = [];
    const legendData = [];

    const getVal = (d, src, rec) => {
      if (src === 'RADAR') {
        if (rec === 'SADECE_LOB') return d.radarLobCount;
        if (rec === 'SADECE_KESTIRIM') return d.radarKestirimCount;
        return d.radarTotalCount;
      } else if (src === 'COMINT') {
        if (rec === 'SADECE_LOB') return d.comintLobCount;
        if (rec === 'SADECE_KESTIRIM') return d.comintKestirimCount;
        return d.comintTotalCount;
      } else {
        // TUMU
        if (rec === 'SADECE_LOB') return d.radarLobCount + d.comintLobCount;
        if (rec === 'SADECE_KESTIRIM') return d.radarKestirimCount + d.comintKestirimCount;
        return d.radarTotalCount + d.comintTotalCount;
      }
    };

    if (source === 'TUMU') {
      const radarVals = dto.days.map((d) => getVal(d, 'RADAR', recordType));
      const comintVals = dto.days.map((d) => getVal(d, 'COMINT', recordType));

      legendData.push('Radar (ELINT)', 'Muhabere (COMINT)');

      seriesList.push({
        name: 'Haftalık Dağılım',
        type: 'radar',
        data: [
          {
            value: radarVals,
            name: 'Radar (ELINT)',
            symbol: 'circle',
            symbolSize: 6,
            itemStyle: { color: '#38bdf8' },
            lineStyle: { width: 2.5, color: '#38bdf8' },
            areaStyle: { color: 'rgba(56, 189, 248, 0.28)' }
          },
          {
            value: comintVals,
            name: 'Muhabere (COMINT)',
            symbol: 'diamond',
            symbolSize: 6,
            itemStyle: { color: '#c084fc' },
            lineStyle: { width: 2.5, color: '#c084fc' },
            areaStyle: { color: 'rgba(192, 132, 252, 0.25)' }
          }
        ]
      });
    } else if (source === 'RADAR') {
      if (recordType === 'TUMU') {
        const lobVals = dto.days.map((d) => d.radarLobCount);
        const kesVals = dto.days.map((d) => d.radarKestirimCount);

        legendData.push('Radar Ham LOB', 'Radar Kestirim Elipsi');

        seriesList.push({
          name: 'Haftalık Radar Dağılımı',
          type: 'radar',
          data: [
            {
              value: lobVals,
              name: 'Radar Ham LOB',
              symbol: 'circle',
              symbolSize: 6,
              itemStyle: { color: '#38bdf8' },
              lineStyle: { width: 2, color: '#38bdf8' },
              areaStyle: { color: 'rgba(56, 189, 248, 0.3)' }
            },
            {
              value: kesVals,
              name: 'Radar Kestirim Elipsi',
              symbol: 'rect',
              symbolSize: 6,
              itemStyle: { color: '#0284c7' },
              lineStyle: { width: 2, color: '#0284c7', type: 'dashed' },
              areaStyle: { color: 'rgba(2, 132, 199, 0.2)' }
            }
          ]
        });
      } else {
        const vals = dto.days.map((d) => getVal(d, 'RADAR', recordType));
        const sName = recordType === 'SADECE_LOB' ? 'Radar Ham LOB' : 'Radar Kestirim Elipsi';
        legendData.push(sName);

        seriesList.push({
          name: 'Radar Tespiti',
          type: 'radar',
          data: [
            {
              value: vals,
              name: sName,
              symbol: 'circle',
              symbolSize: 6,
              itemStyle: { color: '#38bdf8' },
              lineStyle: { width: 2.5, color: '#38bdf8' },
              areaStyle: { color: 'rgba(56, 189, 248, 0.3)' }
            }
          ]
        });
      }
    } else {
      // COMINT
      if (recordType === 'TUMU') {
        const lobVals = dto.days.map((d) => d.comintLobCount);
        const kesVals = dto.days.map((d) => d.comintKestirimCount);

        legendData.push('Muhabere Ham LOB', 'Muhabere Kestirim Elipsi');

        seriesList.push({
          name: 'Haftalık Muhabere Dağılımı',
          type: 'radar',
          data: [
            {
              value: lobVals,
              name: 'Muhabere Ham LOB',
              symbol: 'diamond',
              symbolSize: 6,
              itemStyle: { color: '#c084fc' },
              lineStyle: { width: 2, color: '#c084fc' },
              areaStyle: { color: 'rgba(192, 132, 252, 0.3)' }
            },
            {
              value: kesVals,
              name: 'Muhabere Kestirim Elipsi',
              symbol: 'rect',
              symbolSize: 6,
              itemStyle: { color: '#8b5cf6' },
              lineStyle: { width: 2, color: '#8b5cf6', type: 'dashed' },
              areaStyle: { color: 'rgba(139, 92, 246, 0.2)' }
            }
          ]
        });
      } else {
        const vals = dto.days.map((d) => getVal(d, 'COMINT', recordType));
        const sName = recordType === 'SADECE_LOB' ? 'Muhabere Ham LOB' : 'Muhabere Kestirim Elipsi';
        legendData.push(sName);

        seriesList.push({
          name: 'Muhabere Tespiti',
          type: 'radar',
          data: [
            {
              value: vals,
              name: sName,
              symbol: 'diamond',
              symbolSize: 6,
              itemStyle: { color: '#c084fc' },
              lineStyle: { width: 2.5, color: '#c084fc' },
              areaStyle: { color: 'rgba(192, 132, 252, 0.3)' }
            }
          ]
        });
      }
    }

    return {
      backgroundColor: 'transparent',
      tooltip: {
        trigger: 'item',
        backgroundColor: '#0f172a',
        borderColor: '#334155',
        borderWidth: 1,
        textStyle: { color: '#f8fafc', fontSize: 12 },
        formatter: (params) => {
          const dayNames = dto.days.map((d) => d.dayName);
          const vals = params.value || [];
          let html = `<div style="padding: 4px 6px; font-family: Inter, sans-serif;">
            <div style="font-weight: bold; color: ${params.color || '#38bdf8'}; margin-bottom: 6px; border-bottom: 1px solid #334155; padding-bottom: 3px;">
              ${params.seriesName ? params.seriesName + ' - ' : ''}${params.name}
            </div>`;
          for (let i = 0; i < dayNames.length; i++) {
            const v = vals[i] !== undefined ? vals[i].toLocaleString('tr-TR') : 0;
            html += `<div style="display: flex; justify-content: space-between; gap: 16px; font-size: 11px; margin-bottom: 2px;">
              <span style="color: #94a3b8;">${dayNames[i]}:</span>
              <span style="font-weight: 600; color: #f8fafc;">${v}</span>
            </div>`;
          }
          html += '</div>';
          return html;
        }
      },
      legend: {
        show: true,
        data: legendData,
        top: 4,
        textStyle: { color: '#cbd5e1', fontSize: 11 },
        itemGap: 14
      },
      radar: {
        indicator: indicators,
        shape: 'polygon',
        splitNumber: splitCount,
        radius: '68%',
        center: ['50%', '54%'],
        axisLabel: {
          show: false // Sadece indicator[0]'daki eksende gösterilmesi için global kapalı tutulur
        },
        axisName: {
          color: '#38bdf8',
          fontSize: 11,
          fontWeight: 600,
          backgroundColor: '#0f172a',
          borderRadius: 4,
          padding: [2, 6],
          borderWidth: 1,
          borderColor: '#1e293b'
        },
        splitLine: {
          lineStyle: {
            color: [
              'rgba(51, 65, 85, 0.4)',
              'rgba(51, 65, 85, 0.6)',
              'rgba(51, 65, 85, 0.8)',
              '#475569'
            ]
          }
        },
        splitArea: {
          show: true,
          areaStyle: {
            color: ['rgba(15, 23, 42, 0.75)', 'rgba(30, 41, 59, 0.45)']
          }
        },
        axisLine: {
          lineStyle: {
            color: '#334155'
          }
        }
      },
      series: seriesList
    };
  };

  // ECharts Çizimi & Resize
  useEffect(() => {
    if (viewMode !== 'CHART' || !chartRef.current || !data) return;

    if (!chartInstance.current) {
      chartInstance.current = echarts.init(chartRef.current);
    }

    const opt = buildChartOption(data);
    chartInstance.current.setOption(opt, true);

    const handleResize = () => chartInstance.current?.resize();
    window.addEventListener('resize', handleResize);

    const t1 = setTimeout(handleResize, 50);
    const t2 = setTimeout(handleResize, 250);

    return () => {
      window.removeEventListener('resize', handleResize);
      clearTimeout(t1);
      clearTimeout(t2);
    };
  }, [data, viewMode, source, recordType]);

  // Modal ECharts Render Fonksiyonu
  const renderModalChart = (el) => {
    const target = el || modalChartRef.current;
    if (!target || !data) return;
    try {
      let inst = modalChartInstance.current;
      if (!inst) {
        inst = echarts.init(target, 'dark');
        modalChartInstance.current = inst;
      }
      const opt = buildChartOption(data);
      inst.clear();
      inst.setOption(opt, { notMerge: true, replaceMerge: ['series', 'radar', 'legend'] });
      inst.resize();
      setTimeout(() => inst?.resize(), 50);
      setTimeout(() => inst?.resize(), 250);
    } catch (err) {
      console.error('Modal hafta radar grafik render hatası:', err);
    }
  };

  // Modal ECharts Çizimi
  useEffect(() => {
    if (!modalVisible || !data) return;
    renderModalChart();
    const t1 = setTimeout(renderModalChart, 50);
    const t2 = setTimeout(renderModalChart, 200);
    return () => {
      clearTimeout(t1);
      clearTimeout(t2);
    };
  }, [modalVisible, data, source, recordType]);

  // Temizleme
  useEffect(() => {
    return () => {
      chartInstance.current?.dispose();
      chartInstance.current = null;
      modalChartInstance.current?.dispose();
      modalChartInstance.current = null;
    };
  }, []);

  // En yoğun gün hesabı
  let busiestDay = null;
  if (data && data.days) {
    let max = -1;
    for (const d of data.days) {
      if (d.activeCount > max) {
        max = d.activeCount;
        busiestDay = d;
      }
    }
  }

  // Mini Tablo Kolonları
  const tableColumns = [
    {
      title: 'Gün',
      dataIndex: 'dayName',
      key: 'dayName',
      render: (val, r) => (
        <span style={{ fontWeight: 600, color: '#38bdf8' }}>
          {val} <Text type="secondary" style={{ fontSize: 11 }}>({r.shortName})</Text>
        </span>
      )
    },
    {
      title: 'Radar LOB',
      dataIndex: 'radarLobCount',
      key: 'radarLobCount',
      align: 'right',
      render: (v) => <span style={{ color: '#93c5fd' }}>{v?.toLocaleString('tr-TR')}</span>
    },
    {
      title: 'Radar Kestirim',
      dataIndex: 'radarKestirimCount',
      key: 'radarKestirimCount',
      align: 'right',
      render: (v) => <span style={{ color: '#38bdf8' }}>{v?.toLocaleString('tr-TR')}</span>
    },
    {
      title: 'Muhabere LOB',
      dataIndex: 'comintLobCount',
      key: 'comintLobCount',
      align: 'right',
      render: (v) => <span style={{ color: '#e9d5ff' }}>{v?.toLocaleString('tr-TR')}</span>
    },
    {
      title: 'Muhabere Kestirim',
      dataIndex: 'comintKestirimCount',
      key: 'comintKestirimCount',
      align: 'right',
      render: (v) => <span style={{ color: '#c084fc' }}>{v?.toLocaleString('tr-TR')}</span>
    },
    {
      title: 'Toplam Tespit',
      dataIndex: 'activeCount',
      key: 'activeCount',
      align: 'right',
      render: (v, r) => (
        <Tag color="cyan" style={{ fontWeight: 700 }}>
          {(r.radarTotalCount + r.comintTotalCount)?.toLocaleString('tr-TR')}
        </Tag>
      )
    }
  ];

  return (
    <Card
      style={{
        height: '100%',
        backgroundColor: '#0b1120',
        borderColor: '#1e293b',
        borderRadius: 8,
        display: 'flex',
        flexDirection: 'column',
        boxShadow: '0 4px 12px rgba(0,0,0,0.4)',
        position: 'relative'
      }}
      bodyStyle={{
        flex: 1,
        display: 'flex',
        flexDirection: 'column',
        padding: 12,
        minHeight: 0
      }}
    >
      {/* Başlık ve Üst Butonlar */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: 8 }}>
        <Space size={6}>
          <RadarChartOutlined style={{ color: '#38bdf8', fontSize: 16 }} />
          <Title level={5} style={{ margin: 0, color: '#f8fafc', fontSize: 14 }}>
            Haftalık LOB & Kestirim Dağılımı
          </Title>
          <Tag color="blue" style={{ fontSize: 10, lineHeight: '18px', padding: '0 6px' }}>
            Örümcek Ağı / Radar
          </Tag>
          <Tag color="cyan" style={{ fontSize: 10, lineHeight: '18px', padding: '0 6px' }}>
            Sabit Adım: 50.000
          </Tag>
        </Space>

        <Space size={4}>
          <Segmented
            size="small"
            value={viewMode}
            onChange={setViewMode}
            options={[
              { value: 'CHART', icon: <AppstoreOutlined />, label: 'Grafik' },
              { value: 'TABLE', icon: <TableOutlined />, label: 'Tablo' }
            ]}
            style={{ backgroundColor: '#1e293b' }}
          />

          <Tooltip title="Yenile">
            <Button
              size="small"
              type="text"
              icon={<ReloadOutlined style={{ color: '#94a3b8' }} />}
              onClick={() => fetchDistribution()}
            />
          </Tooltip>

          <Tooltip title="Tam Ekran Büyüt">
            <Button
              size="small"
              type="text"
              icon={<FullscreenOutlined style={{ color: '#94a3b8' }} />}
              onClick={() => setModalVisible(true)}
            />
          </Tooltip>

          {onClose && (
            <Tooltip title="Kapat">
              <Button
                size="small"
                type="text"
                icon={<CloseOutlined style={{ color: '#ef4444' }} />}
                onClick={onClose}
              />
            </Tooltip>
          )}
        </Space>
      </div>

      {/* Kontrol ve Filtre Çubuğu */}
      <div style={{
        backgroundColor: '#0f172a',
        padding: '8px 10px',
        borderRadius: 6,
        border: '1px solid #1e293b',
        marginBottom: 8,
        display: 'flex',
        flexDirection: 'column',
        gap: 6
      }}>
        {/* Üst Sıra Filtreler: Kaynak ve Kayıt Tipi */}
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', flexWrap: 'wrap', gap: 6 }}>
          <Space size={6} wrap>
            <Text type="secondary" style={{ fontSize: 11 }}>Kaynak:</Text>
            <Segmented
              size="small"
              value={source}
              onChange={setSource}
              options={[
                { label: '🌐 Tümü', value: 'TUMU' },
                { label: '📡 Radar', value: 'RADAR' },
                { label: '📻 Muhabere', value: 'COMINT' }
              ]}
              style={{ backgroundColor: '#1e293b' }}
            />
          </Space>

          <Space size={6} wrap>
            <Text type="secondary" style={{ fontSize: 11 }}>Kayıt Tipi:</Text>
            <Segmented
              size="small"
              value={recordType}
              onChange={setRecordType}
              options={[
                { label: '🔄 Tümü', value: 'TUMU' },
                { label: '🎯 Sadece LOB', value: 'SADECE_LOB' },
                { label: '⭕ Sadece Kestirim', value: 'SADECE_KESTIRIM' }
              ]}
              style={{ backgroundColor: '#1e293b' }}
            />
          </Space>

          <Space size={6}>
            <FieldTimeOutlined style={{ color: '#94a3b8', fontSize: 12 }} />
            <Select
              size="small"
              value={timeRange}
              onChange={setTimeRange}
              style={{ width: 125 }}
              dropdownStyle={{ backgroundColor: '#0f172a', borderColor: '#334155' }}
              options={[
                { label: '🌐 Tüm Zamanlar', value: 'ALL' },
                { label: '📅 Son 7 Gün', value: 'SON_7_GUN' },
                { label: '📅 Son 30 Gün', value: 'SON_30_GUN' }
              ]}
            />
          </Space>
        </div>
      </div>

      {/* KPI Özet Sayaçları */}
      <Row gutter={6} style={{ marginBottom: 6 }}>
        <Col span={6}>
          <div style={{
            backgroundColor: '#0f172a',
            border: '1px solid #1e293b',
            borderRadius: 6,
            padding: '4px 8px',
            textAlign: 'center'
          }}>
            <Text type="secondary" style={{ fontSize: 10 }}>📡 Radar Tespiti</Text>
            <div style={{ fontSize: 14, fontWeight: 700, color: '#38bdf8' }}>
              {data?.totalRadarCount ? data.totalRadarCount.toLocaleString('tr-TR') : '0'}
            </div>
          </div>
        </Col>
        <Col span={6}>
          <div style={{
            backgroundColor: '#0f172a',
            border: '1px solid #1e293b',
            borderRadius: 6,
            padding: '4px 8px',
            textAlign: 'center'
          }}>
            <Text type="secondary" style={{ fontSize: 10 }}>📻 Muhabere</Text>
            <div style={{ fontSize: 14, fontWeight: 700, color: '#c084fc' }}>
              {data?.totalComintCount ? data.totalComintCount.toLocaleString('tr-TR') : '0'}
            </div>
          </div>
        </Col>
        <Col span={6}>
          <div style={{
            backgroundColor: '#0f172a',
            border: '1px solid #1e293b',
            borderRadius: 6,
            padding: '4px 8px',
            textAlign: 'center'
          }}>
            <Text type="secondary" style={{ fontSize: 10 }}>🎯 Toplam</Text>
            <div style={{ fontSize: 14, fontWeight: 700, color: '#10b981' }}>
              {data?.grandTotal ? data.grandTotal.toLocaleString('tr-TR') : '0'}
            </div>
          </div>
        </Col>
        <Col span={6}>
          <div style={{
            backgroundColor: '#0f172a',
            border: '1px solid #1e293b',
            borderRadius: 6,
            padding: '4px 8px',
            textAlign: 'center'
          }}>
            <Text type="secondary" style={{ fontSize: 10 }}>🔥 En Yoğun Gün</Text>
            <div style={{ fontSize: 13, fontWeight: 700, color: '#f59e0b', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
              {busiestDay ? `${busiestDay.shortName} (${busiestDay.activeCount.toLocaleString('tr-TR')})` : '-'}
            </div>
          </div>
        </Col>
      </Row>

      {/* Grafik veya Tablo Alanı */}
      <div style={{ flex: 1, minHeight: 0, position: 'relative', display: 'flex', flexDirection: 'column' }}>
        {loading && (
          <div style={{
            position: 'absolute',
            top: 0,
            left: 0,
            right: 0,
            bottom: 0,
            backgroundColor: 'rgba(11, 17, 32, 0.65)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 10
          }}>
            <Spin tip="Haftalık tespit dağılımı hesaplanıyor..." />
          </div>
        )}

        {viewMode === 'CHART' ? (
          <div
            ref={chartRef}
            style={{
              width: '100%',
              flex: 1,
              minHeight: 280
            }}
          />
        ) : (
          <div style={{ flex: 1, overflowY: 'auto' }}>
            <Table
              dataSource={data?.days || []}
              columns={tableColumns}
              rowKey="dayOfWeek"
              size="small"
              pagination={false}
              style={{ backgroundColor: '#0f172a' }}
            />
          </div>
        )}
      </div>

      {/* Alt Bilgi İpuçları */}
      <div style={{
        marginTop: 4,
        paddingTop: 4,
        borderTop: '1px solid #1e293b',
        display: 'flex',
        justifyContent: 'space-between',
        fontSize: 10,
        color: '#64748b'
      }}>
        <span>• Sabit adım genişliği: 50.000 (0 - 200.000) • Ağın her bir kolu bir günü temsil eder</span>
        <span>Adım sayıları dikey Pazartesi ekseninde sabit gösterilmektedir</span>
      </div>

      {/* Tam Ekran Projeksiyon Modalı */}
      <Modal
        title={
          <Space>
            <RadarChartOutlined style={{ color: '#38bdf8' }} />
            <span style={{ color: '#f8fafc' }}>
              Haftanın Günlerine Göre LOB ve Kestirim Tespit Dağılımı (Örümcek Ağı Grafiği)
            </span>
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
        footer={null}
        width={920}
        destroyOnClose={false}
        styles={{
          content: { backgroundColor: '#0b1120', border: '1px solid #1e293b' },
          header: { backgroundColor: '#0b1120', borderBottom: '1px solid #1e293b' }
        }}
      >
        <div style={{ padding: '8px 0', height: 540, width: '100%', minHeight: 460 }}>
          <div
            ref={(el) => {
              modalChartRef.current = el;
              if (el && modalVisible && data) {
                renderModalChart(el);
                setTimeout(() => renderModalChart(el), 60);
              }
            }}
            style={{ width: '100%', height: '100%', minHeight: 460 }}
          />
        </div>
      </Modal>
    </Card>
  );
}
