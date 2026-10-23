import React, { useState, useEffect, useRef } from 'react';
import {
  Card,
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
  Col,
  Table,
  Progress,
  Badge,
  Segmented
} from 'antd';
import {
  BarChartOutlined,
  FullscreenOutlined,
  CloseOutlined,
  ReloadOutlined,
  FieldTimeOutlined,
  RadarChartOutlined,
  TableOutlined,
  AppstoreOutlined,
  CheckCircleOutlined
} from '@ant-design/icons';
import axios from 'axios';
import * as echarts from 'echarts';

const { Text, Title } = Typography;
const { CheckableTag } = Tag;

const IDENTITY_CONFIG = {
  DUSMAN: { label: 'DÜŞMAN', color: '#ef4444', gradient: ['#f87171', '#dc2626'] },
  MUHTEMEL_DUSMAN: { label: 'MUHT. DÜŞMAN', color: '#f97316', gradient: ['#fb923c', '#ea580c'] },
  BILINMEYEN: { label: 'BİLİNMEYEN', color: '#eab308', gradient: ['#fde047', '#ca8a04'] },
  DOST: { label: 'DOST', color: '#10b981', gradient: ['#34d399', '#059669'] },
  TARAFSIZ: { label: 'TARAFSIZ', color: '#a855f7', gradient: ['#c084fc', '#9333ea'] }
};

export default function RadarSpectrumOccupancyWidget({
  initialTimeRange = 'SON_24_SAAT',
  initialIdentities = ['DUSMAN'],
  onClose
}) {
  const [timeRange, setTimeRange] = useState(initialTimeRange);
  const [selectedIdentities, setSelectedIdentities] = useState(initialIdentities);
  const [binStep, setBinStep] = useState(50);
  const [viewMode, setViewMode] = useState('CHART'); // 'CHART' | 'TABLE'
  const [data, setData] = useState(null);
  const [loading, setLoading] = useState(false);
  const [modalVisible, setModalVisible] = useState(false);

  const chartRef = useRef(null);
  const modalChartRef = useRef(null);
  const chartInstance = useRef(null);
  const modalChartInstance = useRef(null);

  // Veri Çekme
  const fetchOccupancy = async (tRange = timeRange, identities = selectedIdentities, step = binStep) => {
    setLoading(true);
    try {
      const res = await axios.get('/api/v1/tactical/analytics/spectrum-occupancy', {
        params: {
          timeRange: tRange,
          teshisKimlik: identities.length > 0 ? identities.join(',') : 'DUSMAN',
          binStepMhz: step,
          onlyActive: true
        }
      });
      setData(res.data);
    } catch (err) {
      console.error('Spektrum doluluk verisi çekilemedi:', err);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchOccupancy(timeRange, selectedIdentities, binStep);
  }, [timeRange, selectedIdentities, binStep]);

  const handleIdentityToggle = (tag, checked) => {
    setSelectedIdentities(prev => {
      let next;
      if (checked) {
        next = [...prev, tag];
      } else {
        next = prev.filter(t => t !== tag);
        if (next.length === 0) {
          next = ['DUSMAN']; // En az bir seçim kalmalı
        }
      }
      return next;
    });
  };

  // ECharts Grafik Konfigürasyonu
  const buildChartOption = (dto, isModal = false) => {
    if (!dto || !dto.bands || dto.bands.length === 0) {
      return {};
    }

    const categories = dto.bands.map(b => b.bandName);
    const isSingleIdentity = dto.selectedIdentities.length === 1;

    let series = [];
    let legendData = [];

    if (isSingleIdentity) {
      const idKey = dto.selectedIdentities[0];
      const cfg = IDENTITY_CONFIG[idKey] || { label: idKey, color: '#38bdf8', gradient: ['#38bdf8', '#0284c7'] };
      
      const seriesData = dto.bands.map(b => {
        const detail = b.identityDetails ? b.identityDetails[idKey] : null;
        const val = detail ? detail.occupancyPercent : 0.0;
        return {
          value: val,
          bandInfo: b,
          detailInfo: detail
        };
      });

      series.push({
        name: cfg.label,
        type: 'bar',
        barMaxWidth: isModal ? 55 : 38,
        itemStyle: {
          borderRadius: [6, 6, 0, 0],
          color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
            { offset: 0, color: cfg.gradient[0] },
            { offset: 1, color: cfg.gradient[1] }
          ]),
          shadowColor: 'rgba(0, 0, 0, 0.4)',
          shadowBlur: 8
        },
        label: {
          show: true,
          position: 'top',
          formatter: (p) => {
            if (!p || p.value <= 0) return '';
            const cnt = p.data?.detailInfo?.emissionCount;
            const cStr = cnt != null && cnt > 0 ? (cnt >= 1000 ? (cnt / 1000).toFixed(1) + 'k' : cnt) : null;
            return cStr ? `%${p.value}\n(${cStr})` : `%${p.value}`;
          },
          color: '#f8fafc',
          fontSize: isModal ? 11 : 9,
          fontWeight: 'bold',
          lineHeight: 12
        },
        data: seriesData
      });
    } else {
      // Çoklu Teşhis: Grouped Bar Chart
      dto.selectedIdentities.forEach(idKey => {
        const cfg = IDENTITY_CONFIG[idKey] || { label: idKey, color: '#94a3b8', gradient: ['#94a3b8', '#64748b'] };
        legendData.push(cfg.label);

        const seriesData = dto.bands.map(b => {
          const detail = b.identityDetails ? b.identityDetails[idKey] : null;
          const val = detail ? detail.occupancyPercent : 0.0;
          return {
            value: val,
            bandInfo: b,
            detailInfo: detail,
            identityKey: idKey
          };
        });

        series.push({
          name: cfg.label,
          type: 'bar',
          barMaxWidth: isModal ? 28 : 18,
          barGap: '15%',
          itemStyle: {
            borderRadius: [4, 4, 0, 0],
            color: new echarts.graphic.LinearGradient(0, 0, 0, 1, [
              { offset: 0, color: cfg.gradient[0] },
              { offset: 1, color: cfg.gradient[1] }
            ])
          },
          label: {
            show: true,
            position: 'top',
            formatter: (p) => {
              if (!p || p.value <= 0) return '';
              const cnt = p.data?.detailInfo?.emissionCount;
              const cStr = cnt != null && cnt > 0 ? (cnt >= 1000 ? (cnt / 1000).toFixed(1) + 'k' : cnt) : null;
              return cStr ? `%${p.value}\n(${cStr})` : `%${p.value}`;
            },
            color: '#cbd5e1',
            fontSize: 8,
            lineHeight: 10
          },
          data: seriesData
        });
      });
    }

    return {
      backgroundColor: 'transparent',
      tooltip: {
        trigger: 'axis',
        axisPointer: { type: 'shadow' },
        backgroundColor: 'rgba(15, 23, 42, 0.95)',
        borderColor: '#334155',
        borderWidth: 1,
        padding: [10, 14],
        textStyle: { color: '#f8fafc', fontSize: 12 },
        formatter: (params) => {
          if (!params || params.length === 0) return '';
          const first = params[0];
          const band = first.data?.bandInfo;
          let html = `
            <div style="font-weight:bold; font-size:13px; color:#38bdf8; margin-bottom:6px; border-bottom:1px solid #334155; padding-bottom:4px;">
              📡 ${first.name} <span style="font-size:11px; color:#94a3b8; font-weight:normal;">(${band?.bandDescription || ''})</span>
            </div>
            <div style="font-size:11px; color:#94a3b8; margin-bottom:8px;">
              Toplam Bant: <b>${band?.totalBandwidthMhz || 0} MHz</b> | Toplam Yayın: <b>${band?.totalEmissionCount || 0}</b>
            </div>
          `;

          params.forEach(p => {
            const det = p.data?.detailInfo;
            const radars = (det?.topRadars && det.topRadars.length > 0) ? det.topRadars.join(', ') : '-';
            html += `
              <div style="display:flex; justify-content:space-between; align-items:center; margin-top:4px; gap:16px;">
                <span style="display:flex; align-items:center; gap:6px;">
                  <span style="display:inline-block; width:10px; height:10px; border-radius:50%; background:${p.color};"></span>
                  <b>${p.seriesName}:</b>
                </span>
                <span style="color:#f8fafc; font-weight:bold;">
                  %${p.value} (${det?.occupiedMhz || 0} MHz)
                </span>
              </div>
              <div style="font-size:10px; color:#cbd5e1; margin-left:16px; margin-bottom:4px;">
                Radar: <i>${radars}</i> (${det?.emissionCount || 0} temas)
              </div>
            `;
          });
          return html;
        }
      },
      legend: {
        show: !isSingleIdentity,
        data: legendData,
        top: 4,
        right: 16,
        textStyle: { color: '#cbd5e1', fontSize: 11 },
        itemWidth: 12,
        itemHeight: 8
      },
      grid: {
        left: isModal ? '4%' : '6%',
        right: '4%',
        bottom: isModal ? '12%' : '16%',
        top: isSingleIdentity ? '14%' : '20%',
        containLabel: true
      },
      xAxis: {
        type: 'category',
        data: categories,
        axisLine: { lineStyle: { color: '#334155' } },
        axisLabel: {
          color: '#94a3b8',
          fontSize: isModal ? 12 : 10,
          interval: 0,
          rotate: isModal ? 0 : 18
        },
        axisTick: { alignWithLabel: true }
      },
      yAxis: {
        type: 'value',
        name: 'Doluluk Oranı (%)',
        nameTextStyle: { color: '#64748b', fontSize: 11, padding: [0, 0, 4, 0] },
        min: 0,
        max: 100,
        splitLine: {
          lineStyle: { color: 'rgba(51, 65, 85, 0.4)', type: 'dashed' }
        },
        axisLabel: {
          color: '#94a3b8',
          fontSize: 11,
          formatter: '{value}%'
        }
      },
      dataZoom: [
        {
          type: 'slider',
          height: 16,
          bottom: 4,
          borderColor: 'transparent',
          backgroundColor: 'rgba(255,255,255,0.05)',
          fillerColor: 'rgba(56, 189, 248, 0.25)',
          handleStyle: { color: '#38bdf8' },
          textStyle: { color: '#94a3b8', fontSize: 10 }
        },
        { type: 'inside' }
      ],
      series
    };
  };

  // ECharts Render
  useEffect(() => {
    if (!chartRef.current || !data) return;

    if (!chartInstance.current) {
      chartInstance.current = echarts.init(chartRef.current);
    }
    const option = buildChartOption(data, false);
    chartInstance.current.clear();
    chartInstance.current.setOption(option, { notMerge: true, replaceMerge: ['series', 'legend'] });

    chartInstance.current.off('legendselectchanged');
    chartInstance.current.on('legendselectchanged', (p) => {
      const map = {
        'DÜŞMAN': 'DUSMAN',
        'MUHT. DÜŞMAN': 'MUHTEMEL_DUSMAN',
        'BİLİNMEYEN': 'BILINMEYEN',
        'DOST': 'DOST',
        'TARAFSIZ': 'TARAFSIZ'
      };
      const key = map[p.name] || p.name;
      if (key) {
        handleIdentityToggle(key, false);
      }
    });

    const handleResize = () => chartInstance.current?.resize();
    window.addEventListener('resize', handleResize);
    return () => window.removeEventListener('resize', handleResize);
  }, [data, viewMode]);

  // Modal Fullscreen Render Fonksiyonu
  const renderModalChart = (el) => {
    const target = el || modalChartRef.current;
    if (!target || !data) return;
    try {
      let inst = modalChartInstance.current;
      if (!inst) {
        inst = echarts.init(target, 'dark');
        modalChartInstance.current = inst;
      }
      const option = buildChartOption(data, true);
      inst.clear();
      inst.setOption(option, { notMerge: true, replaceMerge: ['series', 'legend'] });
      inst.resize();
      setTimeout(() => inst?.resize(), 50);
      setTimeout(() => inst?.resize(), 250);
    } catch (err) {
      console.error('Modal spektrum doluluk grafik render hatası:', err);
    }
  };

  // Modal Görünürlüğü ve Veri Değişiminde Tetikleme
  useEffect(() => {
    if (!modalVisible || !data) return;
    renderModalChart();
    const t1 = setTimeout(renderModalChart, 50);
    const t2 = setTimeout(renderModalChart, 200);
    return () => {
      clearTimeout(t1);
      clearTimeout(t2);
    };
  }, [modalVisible, data, viewMode]);

  // Taktik Tablo Kolonları
  const tableColumns = [
    {
      title: 'Frekans Aralığı',
      dataIndex: 'bandName',
      key: 'bandName',
      width: 140,
      render: (text, record) => (
        <div>
          <div style={{ color: '#38bdf8', fontWeight: 600 }}>{text}</div>
          <div style={{ fontSize: 10, color: '#94a3b8' }}>{record.bandDescription}</div>
        </div>
      )
    },
    {
      title: 'Toplam Bant',
      dataIndex: 'totalBandwidthMhz',
      key: 'totalBandwidthMhz',
      width: 100,
      render: val => <Text style={{ color: '#cbd5e1' }}>{val} MHz</Text>
    },
    {
      title: 'Spektrum Doluluk Oranı (%)',
      key: 'occupancy',
      render: (_, record) => {
        return (
          <Space direction="vertical" style={{ width: '100%' }} size={4}>
            {selectedIdentities.map(id => {
              const det = record.identityDetails ? record.identityDetails[id] : null;
              const pct = det ? det.occupancyPercent : 0;
              const cfg = IDENTITY_CONFIG[id] || { label: id, color: '#38bdf8' };
              return (
                <div key={id} style={{ display: 'flex', alignItems: 'center', gap: 8, fontSize: 11 }}>
                  <Tag color={cfg.color} style={{ fontSize: 10, margin: 0, padding: '0 4px', lineHeight: '18px' }}>
                    {cfg.label}
                  </Tag>
                  <div style={{ flex: 1 }}>
                    <Progress
                      percent={pct}
                      size="small"
                      strokeColor={cfg.color}
                      trailColor="#1e293b"
                      format={() => `${pct}%`}
                    />
                  </div>
                  <span style={{ fontSize: 10, color: '#94a3b8', width: 60, textAlign: 'right' }}>
                    {det?.occupiedMhz || 0} MHz
                  </span>
                </div>
              );
            })}
          </Space>
        );
      }
    },
    {
      title: 'Yayın / Radarlar',
      key: 'radars',
      width: 180,
      render: (_, record) => {
        const radars = [];
        selectedIdentities.forEach(id => {
          const det = record.identityDetails ? record.identityDetails[id] : null;
          if (det && det.topRadars) {
            det.topRadars.forEach(r => {
              if (!radars.includes(r)) radars.push(r);
            });
          }
        });
        return (
          <div>
            <div style={{ color: '#f8fafc', fontSize: 11, fontWeight: 500 }}>
              {record.totalEmissionCount} temas
            </div>
            <div style={{ fontSize: 10, color: '#94a3b8', whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis' }}>
              {radars.length > 0 ? radars.join(', ') : '-'}
            </div>
          </div>
        );
      }
    }
  ];

  return (
    <Card
      style={{
        background: '#0b1329',
        border: '1px solid #1e293b',
        borderRadius: 8,
        height: '100%',
        display: 'flex',
        flexDirection: 'column',
        boxShadow: '0 4px 20px rgba(0, 0, 0, 0.4)'
      }}
      styles={{
        header: {
          background: 'linear-gradient(90deg, #0f172a 0%, #1e293b 100%)',
          borderBottom: '1px solid #334155',
          padding: '8px 14px'
        },
        body: {
          padding: 12,
          flex: 1,
          display: 'flex',
          flexDirection: 'column',
          overflow: 'hidden'
        }
      }}
      title={
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', gap: 8 }}>
          <Space size={8}>
            <BarChartOutlined style={{ color: '#ef4444', fontSize: 16 }} />
            <div>
              <div style={{ color: '#f8fafc', fontSize: 13, fontWeight: 'bold' }}>
                Radar Spektrum Doluluk & Kullanım Yoğunluğu
              </div>
              <div style={{ color: '#94a3b8', fontSize: 10 }}>
                Sabit Frekans Aralıkları Kesişim & Doluluk Analizi (Union)
              </div>
            </div>
          </Space>
          <Space size={4}>
            <Tooltip title="Yenile">
              <Button
                type="text"
                size="small"
                icon={<ReloadOutlined style={{ color: '#94a3b8' }} />}
                onClick={() => fetchOccupancy(timeRange, selectedIdentities)}
              />
            </Tooltip>
            <Tooltip title="Tam Ekran">
              <Button
                type="text"
                size="small"
                icon={<FullscreenOutlined style={{ color: '#94a3b8' }} />}
                onClick={() => setModalVisible(true)}
              />
            </Tooltip>
            {onClose && (
              <Tooltip title="Kapat">
                <Button
                  type="text"
                  size="small"
                  icon={<CloseOutlined style={{ color: '#94a3b8' }} />}
                  onClick={onClose}
                />
              </Tooltip>
            )}
          </Space>
        </div>
      }
    >
      {/* Filtre ve Kontrol Barı */}
      <div style={{
        background: '#0f172a',
        padding: '8px 10px',
        borderRadius: 6,
        border: '1px solid #1e293b',
        marginBottom: 10
      }}>
        <Row gutter={[8, 8]} align="middle" justify="space-between">
          <Col xs={24} md={14}>
            <Space size={6} wrap align="center">
              <Text style={{ color: '#64748b', fontSize: 11 }}>Teşhis:</Text>
              {Object.keys(IDENTITY_CONFIG).map(idKey => {
                const cfg = IDENTITY_CONFIG[idKey];
                const checked = selectedIdentities.includes(idKey);
                return (
                  <CheckableTag
                    key={idKey}
                    checked={checked}
                    onChange={(val) => handleIdentityToggle(idKey, val)}
                    style={{
                      border: checked ? `1px solid ${cfg.color}` : '1px solid #334155',
                      background: checked ? `${cfg.color}22` : 'transparent',
                      color: checked ? cfg.color : '#94a3b8',
                      fontSize: 10,
                      fontWeight: checked ? 600 : 400,
                      padding: '1px 7px',
                      borderRadius: 4,
                      cursor: 'pointer'
                    }}
                  >
                    {cfg.label}
                  </CheckableTag>
                );
              })}
            </Space>
          </Col>

          <Col xs={24} md={12} style={{ display: 'flex', justifyContent: 'flex-end', gap: 6, flexWrap: 'wrap' }}>
            <Radio.Group
              size="small"
              value={binStep}
              onChange={(e) => setBinStep(e.target.value)}
              buttonStyle="solid"
            >
              <Radio.Button value={50} style={{ fontSize: 10, padding: '0 5px' }}>50 MHz</Radio.Button>
              <Radio.Button value={100} style={{ fontSize: 10, padding: '0 5px' }}>100 MHz</Radio.Button>
              <Radio.Button value={200} style={{ fontSize: 10, padding: '0 5px' }}>200 MHz</Radio.Button>
            </Radio.Group>

            <Radio.Group
              size="small"
              value={timeRange}
              onChange={(e) => setTimeRange(e.target.value)}
              buttonStyle="solid"
            >
              <Radio.Button value="SON_1_SAAT" style={{ fontSize: 10, padding: '0 5px' }}>1s</Radio.Button>
              <Radio.Button value="SON_24_SAAT" style={{ fontSize: 10, padding: '0 5px' }}>1g</Radio.Button>
              <Radio.Button value="SON_1_HAFTA" style={{ fontSize: 10, padding: '0 5px' }}>1h</Radio.Button>
              <Radio.Button value="ALL" style={{ fontSize: 10, padding: '0 5px' }}>Tüm</Radio.Button>
            </Radio.Group>

            <Segmented
              size="small"
              value={viewMode}
              onChange={setViewMode}
              options={[
                { value: 'CHART', icon: <BarChartOutlined /> },
                { value: 'TABLE', icon: <TableOutlined /> }
              ]}
              style={{ background: '#1e293b' }}
            />
          </Col>
        </Row>
      </div>

      {/* Ana Görselleştirme Alanı */}
      <div style={{ flex: 1, position: 'relative', minHeight: 280 }}>
        {loading && (
          <div style={{
            position: 'absolute',
            inset: 0,
            background: 'rgba(11, 19, 41, 0.7)',
            display: 'flex',
            alignItems: 'center',
            justifyContent: 'center',
            zIndex: 10
          }}>
            <Spin tip="Spektrum doluluk oranları hesaplanıyor..." />
          </div>
        )}

        {viewMode === 'CHART' ? (
          <div ref={chartRef} style={{ width: '100%', height: '100%', minHeight: 280 }} />
        ) : (
          <div style={{ height: '100%', overflowY: 'auto' }}>
            <Table
              dataSource={data?.bands || []}
              columns={tableColumns}
              rowKey="bandId"
              pagination={false}
              size="small"
              style={{ background: 'transparent' }}
            />
          </div>
        )}
      </div>

      {/* Alt Bilgi Barı */}
      <div style={{
        marginTop: 6,
        paddingTop: 6,
        borderTop: '1px solid #1e293b',
        display: 'flex',
        alignItems: 'center',
        justifyContent: 'space-between',
        fontSize: 10,
        color: '#64748b'
      }}>
        <span>🕒 Zaman: <b style={{ color: '#cbd5e1' }}>{data?.timeRangeLabel || timeRange}</b> | ⚡ <b style={{ color: '#38bdf8' }}>{(data?.bands || []).reduce((s, b) => s + (b.totalEmissionCount || 0), 0).toLocaleString('tr-TR')}</b> Yayın Teması</span>
        <span>🛡️ Seçili Teşhis: <b style={{ color: '#cbd5e1' }}>{selectedIdentities.join(', ')}</b></span>
      </div>

      {/* Tam Ekran Modalı */}
      <Modal
        title={
          <div style={{ color: '#f8fafc', fontSize: 15, fontWeight: 'bold' }}>
            📡 Radar Frekans Spektrum Doluluk & Kullanım Yoğunluğu (Tam Ekran Detay)
          </div>
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
        width="90vw"
        styles={{
          content: { background: '#0b1329', border: '1px solid #334155' },
          header: { background: '#0f172a', borderBottom: '1px solid #334155' }
        }}
      >
        <div style={{ height: '70vh', width: '100%', minHeight: 480 }}>
          <div
            ref={(el) => {
              modalChartRef.current = el;
              if (el && modalVisible && data) {
                renderModalChart(el);
                setTimeout(() => renderModalChart(el), 60);
              }
            }}
            style={{ width: '100%', height: '100%' }}
          />
        </div>
      </Modal>
    </Card>
  );
}
