import React, { useState, useEffect, useCallback, useMemo } from 'react';
import {
  Card,
  Row,
  Col,
  Table,
  Tag,
  Button,
  Space,
  Select,
  Switch,
  Input,
  Tooltip,
  Modal,
  Progress,
  Badge,
  Popconfirm,
  Tabs,
  Typography,
  message
} from 'antd';
import {
  DatabaseOutlined,
  ReloadOutlined,
  ClearOutlined,
  ThunderboltOutlined,
  DashboardOutlined,
  SearchOutlined,
  CopyOutlined,
  CheckOutlined,
  WarningOutlined,
  CheckCircleOutlined,
  ClockCircleOutlined,
  TableOutlined,
  CodeOutlined,
  InfoCircleOutlined
} from '@ant-design/icons';

const { Text, Paragraph } = Typography;

/**
 * 🛡️ TAKTIK POSTGRESQL & POSTGIS PERFORMANS VE SORGU İZLEME MONİTÖRÜ
 * pg_stat_statements, pg_stat_user_tables ve pg_stat_user_indexes canlı telemetrisi.
 */
export default function DatabaseMonitorWidget() {
  const [loading, setLoading] = useState(false);
  const [data, setData] = useState(null);
  const [sortBy, setSortBy] = useState('mean_time');
  const [limit, setLimit] = useState(25);
  const [autoRefresh, setAutoRefresh] = useState(false);
  const [searchFilter, setSearchFilter] = useState('');
  const [activeTab, setActiveTab] = useState('queries');
  const [selectedSqlModal, setSelectedSqlModal] = useState(null);
  const [copied, setCopied] = useState(false);

  // Veritabanı metriklerini çek
  const fetchMetrics = useCallback(async () => {
    setLoading(true);
    try {
      const res = await fetch(`/api/v1/tactical/db-monitor/stats?limit=${limit}&sortBy=${sortBy}`);
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const json = await res.json();
      setData(json);
    } catch (err) {
      console.error('DB izleme verisi alınamadı:', err);
      message.error('Veritabanı metrikleri alınamadı: ' + err.message);
    } finally {
      setLoading(false);
    }
  }, [limit, sortBy]);

  // Sayaçları sıfırla
  const handleReset = async () => {
    try {
      const res = await fetch('/api/v1/tactical/db-monitor/reset', { method: 'POST' });
      const json = await res.json();
      if (json.success) {
        message.success('Sorgu performans istatistikleri sıfırlandı.');
        fetchMetrics();
      } else {
        message.warning(json.message || 'Sıfırlama yapılamadı.');
      }
    } catch (err) {
      message.error('Sıfırlama hatası: ' + err.message);
    }
  };

  useEffect(() => {
    fetchMetrics();
  }, [fetchMetrics]);

  // Otomatik yenileme (5 saniyede bir)
  useEffect(() => {
    if (!autoRefresh) return;
    const interval = setInterval(fetchMetrics, 5000);
    return () => clearInterval(interval);
  }, [autoRefresh, fetchMetrics]);

  const summary = data?.summary || {
    totalQueriesTracked: 0,
    totalCalls: 0,
    totalExecTimeMs: 0.0,
    meanExecTimeMs: 0.0,
    slowQueryCount: 0,
    cacheHitPercentage: 100.0,
    databaseName: 'tactical_ew_db_3',
    activeConnections: 1,
    pgStatStatementsAvailable: true
  };

  // Filtrelenmiş sorgular
  const filteredQueries = useMemo(() => {
    if (!data?.topQueries) return [];
    if (!searchFilter.trim()) return data.topQueries;
    const q = searchFilter.toLowerCase();
    return data.topQueries.filter(item =>
      (item.query && item.query.toLowerCase().includes(q)) ||
      (item.queryId && item.queryId.includes(q))
    );
  }, [data?.topQueries, searchFilter]);

  const copyToClipboard = (text) => {
    navigator.clipboard.writeText(text);
    setCopied(true);
    message.success('SQL panoya kopyalandı');
    setTimeout(() => setCopied(false), 2000);
  };

  // 1. Sorgu Tablosu Kolonları
  const queryColumns = [
    {
      title: 'No',
      key: 'index',
      width: 50,
      align: 'center',
      render: (_, __, idx) => <span style={{ color: '#64748b' }}>{idx + 1}</span>
    },
    {
      title: 'Normalize Edilmiş SQL Şablonu',
      dataIndex: 'query',
      key: 'query',
      render: (text, record) => (
        <div
          onClick={() => setSelectedSqlModal(record)}
          style={{
            cursor: 'pointer',
            padding: '6px 10px',
            background: 'rgba(15, 23, 42, 0.7)',
            border: '1px solid #1e293b',
            borderRadius: 4,
            fontFamily: "'JetBrains Mono', 'Roboto Mono', monospace",
            fontSize: 12,
            color: '#e2e8f0',
            maxHeight: 52,
            overflow: 'hidden',
            textOverflow: 'ellipsis',
            display: '-webkit-box',
            WebkitLineClamp: 2,
            WebkitBoxOrient: 'vertical',
            transition: 'border-color 0.2s',
          }}
          onMouseEnter={(e) => (e.currentTarget.style.borderColor = '#38bdf8')}
          onMouseLeave={(e) => (e.currentTarget.style.borderColor = '#1e293b')}
        >
          {text}
        </div>
      )
    },
    {
      title: 'Çağrı (Calls)',
      dataIndex: 'calls',
      key: 'calls',
      width: 110,
      sorter: (a, b) => a.calls - b.calls,
      render: (v) => <Tag color="#0369a1" style={{ fontWeight: 700 }}>{v.toLocaleString()} çağrı</Tag>
    },
    {
      title: 'Ort. Süre (Mean)',
      dataIndex: 'meanExecTimeMs',
      key: 'meanExecTimeMs',
      width: 140,
      sorter: (a, b) => a.meanExecTimeMs - b.meanExecTimeMs,
      render: (v) => {
        let color = '#10b981'; // < 10 ms (Hızlı / Yeşil)
        let label = 'Hızlı';
        if (v >= 50) {
          color = '#ef4444'; // >= 50 ms (Yavaş / Kırmızı)
          label = 'YAVAŞ';
        } else if (v >= 10) {
          color = '#f59e0b'; // 10-50 ms (Orta / Turuncu)
          label = 'Orta';
        }
        return (
          <Space orientation="vertical" size={2}>
            <span style={{ fontWeight: 800, color, fontSize: 13 }}>
              {v.toFixed(2)} ms
            </span>
            <Tag color={color} style={{ fontSize: 10, lineHeight: '16px', padding: '0 4px', margin: 0 }}>
              {label}
            </Tag>
          </Space>
        );
      }
    },
    {
      title: 'Max Süre (Pik)',
      dataIndex: 'maxExecTimeMs',
      key: 'maxExecTimeMs',
      width: 120,
      sorter: (a, b) => a.maxExecTimeMs - b.maxExecTimeMs,
      render: (v) => (
        <span style={{ color: v >= 50 ? '#f87171' : '#94a3b8', fontWeight: 600 }}>
          {v.toFixed(2)} ms
        </span>
      )
    },
    {
      title: 'Toplam Süre',
      dataIndex: 'totalExecTimeMs',
      key: 'totalExecTimeMs',
      width: 120,
      sorter: (a, b) => a.totalExecTimeMs - b.totalExecTimeMs,
      render: (v) => (
        <span style={{ color: '#cbd5e1' }}>
          {v >= 1000 ? `${(v / 1000).toFixed(2)} s` : `${v.toFixed(1)} ms`}
        </span>
      )
    },
    {
      title: 'Satır',
      dataIndex: 'rows',
      key: 'rows',
      width: 90,
      render: (v) => <span style={{ color: '#94a3b8' }}>{v.toLocaleString()}</span>
    },
    {
      title: 'Cache Hit',
      dataIndex: 'cacheHitPercentage',
      key: 'cacheHitPercentage',
      width: 120,
      render: (v) => (
        <Progress
          percent={v}
          size="small"
          status={v >= 90 ? 'success' : v >= 70 ? 'normal' : 'exception'}
          format={(pct) => `%${pct}`}
        />
      )
    }
  ];

  // 2. Tablo İstatistikleri Kolonları
  const tableColumns = [
    {
      title: 'Tablo Adı',
      dataIndex: 'tableName',
      key: 'tableName',
      render: (text) => (
        <Space>
          <TableOutlined style={{ color: '#38bdf8' }} />
          <Text strong style={{ color: '#f8fafc', fontFamily: 'monospace' }}>{text}</Text>
        </Space>
      )
    },
    {
      title: 'İndeks Verimliliği',
      dataIndex: 'indexUsagePercentage',
      key: 'indexUsagePercentage',
      sorter: (a, b) => a.indexUsagePercentage - b.indexUsagePercentage,
      render: (v) => (
        <div style={{ width: 140 }}>
          <Progress
            percent={v}
            size="small"
            strokeColor={v >= 90 ? '#10b981' : v >= 70 ? '#f59e0b' : '#ef4444'}
            format={(pct) => `%${pct}`}
          />
        </div>
      )
    },
    {
      title: 'İndeks Taraması (Idx Scan)',
      dataIndex: 'idxScan',
      key: 'idxScan',
      sorter: (a, b) => a.idxScan - b.idxScan,
      render: (v) => (
        <Tag color="cyan" style={{ fontWeight: 600 }}>{v.toLocaleString()}</Tag>
      )
    },
    {
      title: 'İndeksle Çekilen (Idx Fetch)',
      dataIndex: 'idxTupFetch',
      key: 'idxTupFetch',
      render: (v) => <span style={{ color: '#cbd5e1' }}>{v.toLocaleString()}</span>
    },
    {
      title: 'Tam Tablo Taraması (Seq Scan)',
      dataIndex: 'seqScan',
      key: 'seqScan',
      sorter: (a, b) => a.seqScan - b.seqScan,
      render: (v) => (
        <Tag color={v > 500 ? 'red' : v > 50 ? 'orange' : 'default'}>
          {v.toLocaleString()}
        </Tag>
      )
    },
    {
      title: 'Tablodan Okunan (Seq Read)',
      dataIndex: 'seqTupRead',
      key: 'seqTupRead',
      render: (v) => <span style={{ color: '#94a3b8' }}>{v.toLocaleString()}</span>
    },
    {
      title: 'Kayıt Sayısı (Canlı Satır)',
      dataIndex: 'liveRows',
      key: 'liveRows',
      sorter: (a, b) => a.liveRows - b.liveRows,
      render: (v) => (
        <Text strong style={{ color: '#38bdf8' }}>{v.toLocaleString()} kayıt</Text>
      )
    }
  ];

  // 3. İndeks Detay Kolonları
  const indexColumns = [
    {
      title: 'İndeks Adı',
      dataIndex: 'indexName',
      key: 'indexName',
      render: (text) => (
        <Space>
          <ThunderboltOutlined style={{ color: '#f59e0b' }} />
          <Text copyable style={{ color: '#f8fafc', fontFamily: 'monospace' }}>{text}</Text>
        </Space>
      )
    },
    {
      title: 'Tablo',
      dataIndex: 'tableName',
      key: 'tableName',
      render: (text) => <Tag color="#1e293b">{text}</Tag>
    },
    {
      title: 'Kullanım Sayısı (Idx Scan)',
      dataIndex: 'idxScan',
      key: 'idxScan',
      sorter: (a, b) => a.idxScan - b.idxScan,
      render: (v) => (
        <Tag color={v > 0 ? 'green' : 'default'} style={{ fontWeight: 700 }}>
          {v.toLocaleString()} kez
        </Tag>
      )
    },
    {
      title: 'Taranan Satır (Tup Read)',
      dataIndex: 'idxTupRead',
      key: 'idxTupRead',
      render: (v) => <span style={{ color: '#cbd5e1' }}>{v.toLocaleString()}</span>
    },
    {
      title: 'Çekilen Satır (Tup Fetch)',
      dataIndex: 'idxTupFetch',
      key: 'idxTupFetch',
      render: (v) => <span style={{ color: '#94a3b8' }}>{v.toLocaleString()}</span>
    },
    {
      title: 'Disk Boyutu',
      dataIndex: 'indexSizePretty',
      key: 'indexSizePretty',
      render: (v) => <Tag color="blue">{v}</Tag>
    }
  ];

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {/* Üst Askeri Başlık & Kontrol Paneli */}
      <Card
        size="small"
        style={{
          background: 'linear-gradient(90deg, #0b1329 0%, #0d1e3a 100%)',
          borderColor: '#1e3a8a',
          boxShadow: '0 4px 16px rgba(0, 0, 0, 0.4)'
        }}
      >
        <Row justify="space-between" align="middle" gutter={[12, 12]}>
          <Col>
            <Space size={12} align="center">
              <div
                style={{
                  width: 38,
                  height: 38,
                  borderRadius: 8,
                  background: 'linear-gradient(135deg, #0284c7 0%, #0369a1 100%)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  boxShadow: '0 0 12px rgba(2, 132, 199, 0.5)'
                }}
              >
                <DashboardOutlined style={{ color: '#fff', fontSize: 20 }} />
              </div>
              <div>
                <Space>
                  <span style={{ fontWeight: 800, fontSize: 16, color: '#f8fafc', letterSpacing: 0.5 }}>
                    POSTGRESQL & POSTGIS SORGUSU VE PERFORMANS MONİTÖRÜ
                  </span>
                  <Tag color={summary.pgStatStatementsAvailable ? 'green' : 'red'}>
                    {summary.pgStatStatementsAvailable ? 'pg_stat_statements: AKTİF' : 'Eklenti Kapalı'}
                  </Tag>
                  <Tag color="cyan">Veritabanı: {summary.databaseName}</Tag>
                </Space>
                <div style={{ fontSize: 11, color: '#94a3b8', marginTop: 2 }}>
                  Sıfır kod değişikliğiyle canlı SQL profil çıkarma, indeks verimliliği ve mikro-saniye gecikme teşhisi
                </div>
              </div>
            </Space>
          </Col>

          <Col>
            <Space size={10} wrap>
              {/* Sıralama */}
              <Space orientation="horizontal" size={6}>
                <span style={{ fontSize: 12, color: '#94a3b8' }}>Sırala:</span>
                <Select
                  size="small"
                  value={sortBy}
                  onChange={(v) => setSortBy(v)}
                  style={{ width: 170 }}
                  options={[
                    { value: 'mean_time', label: 'Ortalama Süre (Yavaş)' },
                    { value: 'total_time', label: 'Toplam Tüketilen Süre' },
                    { value: 'calls', label: 'En Çok Çağrılanlar' },
                    { value: 'max_time', label: 'Pik (Max) Süre' }
                  ]}
                />
              </Space>

              {/* Limit */}
              <Select
                size="small"
                value={limit}
                onChange={(v) => setLimit(v)}
                style={{ width: 90 }}
                options={[
                  { value: 10, label: 'Top 10' },
                  { value: 25, label: 'Top 25' },
                  { value: 50, label: 'Top 50' },
                  { value: 100, label: 'Top 100' }
                ]}
              />

              {/* Otomatik Yenile */}
              <Space size={6} style={{ background: '#0f172a', padding: '2px 8px', borderRadius: 4, border: '1px solid #1e293b' }}>
                <span style={{ fontSize: 12, color: '#94a3b8' }}>Oto-Yenile (5s):</span>
                <Switch size="small" checked={autoRefresh} onChange={setAutoRefresh} />
              </Space>

              {/* Yenile Butonu */}
              <Button
                type="primary"
                size="small"
                icon={<ReloadOutlined spin={loading} />}
                onClick={fetchMetrics}
                loading={loading}
                style={{ backgroundColor: '#0284c7', borderColor: '#0284c7' }}
              >
                Canlı Yenile
              </Button>

              {/* Sayaç Sıfırla */}
              <Popconfirm
                title="Sorgu Sayaçlarını Sıfırla"
                description="pg_stat_statements istatistikleri sıfırlansın mı? Bu işlem yeni testler için temiz bir başlangıç sağlar."
                onConfirm={handleReset}
                okText="Evet, Sıfırla"
                cancelText="İptal"
                okButtonProps={{ danger: true }}
              >
                <Button size="small" danger icon={<ClearOutlined />}>
                  İstatistikleri Sıfırla
                </Button>
              </Popconfirm>
            </Space>
          </Col>
        </Row>
      </Card>

      {/* KPI İstatistik Kartları */}
      <Row gutter={[12, 12]}>
        <Col xs={24} sm={12} md={6} lg={4} xl={4}>
          <Card size="small" style={{ background: '#0b1329', borderColor: '#1e293b', borderLeft: '3px solid #38bdf8' }}>
            <div style={{ color: '#94a3b8', fontSize: 11, fontWeight: 600 }}>TOPLAM SORGULAR</div>
            <div style={{ color: '#f8fafc', fontSize: 20, fontWeight: 800, marginTop: 4 }}>
              {summary.totalCalls.toLocaleString()}
            </div>
            <div style={{ color: '#64748b', fontSize: 10 }}>{summary.totalQueriesTracked} tekil şablon</div>
          </Card>
        </Col>

        <Col xs={24} sm={12} md={6} lg={5} xl={5}>
          <Card size="small" style={{ background: '#0b1329', borderColor: '#1e293b', borderLeft: '3px solid #10b981' }}>
            <div style={{ color: '#94a3b8', fontSize: 11, fontWeight: 600 }}>ORTALAMA YÜRÜTME</div>
            <div style={{ color: summary.meanExecTimeMs >= 50 ? '#ef4444' : summary.meanExecTimeMs >= 10 ? '#f59e0b' : '#10b981', fontSize: 20, fontWeight: 800, marginTop: 4 }}>
              {summary.meanExecTimeMs.toFixed(2)} ms
            </div>
            <div style={{ color: '#64748b', fontSize: 10 }}>Tüm sorgu şablonları ortalaması</div>
          </Card>
        </Col>

        <Col xs={24} sm={12} md={6} lg={5} xl={5}>
          <Card size="small" style={{ background: '#0b1329', borderColor: '#1e293b', borderLeft: `3px solid ${summary.slowQueryCount > 0 ? '#ef4444' : '#10b981'}` }}>
            <div style={{ color: '#94a3b8', fontSize: 11, fontWeight: 600 }}>YAVAŞ SORGULAR (&gt; 50ms)</div>
            <div style={{ color: summary.slowQueryCount > 0 ? '#ef4444' : '#10b981', fontSize: 20, fontWeight: 800, marginTop: 4 }}>
              {summary.slowQueryCount} adet
            </div>
            <div style={{ color: summary.slowQueryCount === 0 ? '#10b981' : '#f87171', fontSize: 10 }}>
              {summary.slowQueryCount === 0 ? '✓ Kritik eşik aşımı yok' : '⚠ Optimize edilmeli'}
            </div>
          </Card>
        </Col>

        <Col xs={24} sm={12} md={6} lg={5} xl={5}>
          <Card size="small" style={{ background: '#0b1329', borderColor: '#1e293b', borderLeft: '3px solid #a855f7' }}>
            <div style={{ color: '#94a3b8', fontSize: 11, fontWeight: 600 }}>BUFFER CACHE HIT</div>
            <div style={{ color: '#a855f7', fontSize: 20, fontWeight: 800, marginTop: 4 }}>
              %{summary.cacheHitPercentage.toFixed(1)}
            </div>
            <div style={{ color: '#64748b', fontSize: 10 }}>RAM üzerinden okuma oranı</div>
          </Card>
        </Col>

        <Col xs={24} sm={12} md={6} lg={5} xl={5}>
          <Card size="small" style={{ background: '#0b1329', borderColor: '#1e293b', borderLeft: '3px solid #06b6d4' }}>
            <div style={{ color: '#94a3b8', fontSize: 11, fontWeight: 600 }}>AKTİF DB BAĞLANTISI</div>
            <div style={{ color: '#06b6d4', fontSize: 20, fontWeight: 800, marginTop: 4 }}>
              {summary.activeConnections} Aktif
            </div>
            <div style={{ color: '#64748b', fontSize: 10 }}>HikariCP Connection Pool</div>
          </Card>
        </Col>
      </Row>

      {/* Ana Sekmeli Analiz Tabloları */}
      <Card
        size="small"
        style={{
          background: '#0b1329',
          borderColor: '#1e293b',
          borderRadius: 6
        }}
      >
        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          type="card"
          tabBarExtraContent={
            activeTab === 'queries' ? (
              <Input
                placeholder="SQL şablonlarında filtrele..."
                prefix={<SearchOutlined style={{ color: '#64748b' }} />}
                value={searchFilter}
                onChange={(e) => setSearchFilter(e.target.value)}
                allowClear
                size="small"
                style={{ width: 280, backgroundColor: '#0f172a', borderColor: '#1e293b' }}
              />
            ) : null
          }
          items={[
            {
              key: 'queries',
              label: (
                <Space>
                  <CodeOutlined style={{ color: '#38bdf8' }} />
                  <span>Sorgu Performansı ({filteredQueries.length})</span>
                </Space>
              ),
              children: (
                <div>
                  <Table
                    columns={queryColumns}
                    dataSource={filteredQueries}
                    rowKey="queryId"
                    size="small"
                    loading={loading}
                    pagination={{ pageSize: 10, showSizeChanger: true, pageSizeOptions: ['10', '25', '50'] }}
                    locale={{ emptyText: 'Henüz izlenmiş sorgu bulunamadı. Lütfen "Canlı Yenile" butonuna tıklayınız.' }}
                    style={{ background: 'transparent' }}
                  />
                </div>
              )
            },
            {
              key: 'tables',
              label: (
                <Space>
                  <TableOutlined style={{ color: '#10b981' }} />
                  <span>Tablo Taramaları & İndeks Verimliliği ({data?.tableMetrics?.length || 0})</span>
                </Space>
              ),
              children: (
                <div>
                  <div style={{ marginBottom: 12, padding: '8px 12px', background: 'rgba(16, 185, 129, 0.1)', border: '1px solid rgba(16, 185, 129, 0.3)', borderRadius: 4, color: '#6ee7b7', fontSize: 12 }}>
                    <InfoCircleOutlined style={{ marginRight: 6 }} />
                    <strong>Doktrin Kuralı:</strong> İndeks Verimliliğinin <strong>%95 üzerinde</strong> olması, veritabanının diskteki tam tablo taramalarından (Seq Scan) kaçınarak doğrudan PostGIS GiST ve B-Tree indekslerinden çalıştığını belgeler.
                  </div>
                  <Table
                    columns={tableColumns}
                    dataSource={data?.tableMetrics || []}
                    rowKey="tableName"
                    size="small"
                    loading={loading}
                    pagination={false}
                    style={{ background: 'transparent' }}
                  />
                </div>
              )
            },
            {
              key: 'indexes',
              label: (
                <Space>
                  <ThunderboltOutlined style={{ color: '#f59e0b' }} />
                  <span>İndeks Kullanım İstatistikleri ({data?.indexMetrics?.length || 0})</span>
                </Space>
              ),
              children: (
                <div>
                  <Table
                    columns={indexColumns}
                    dataSource={data?.indexMetrics || []}
                    rowKey="indexName"
                    size="small"
                    loading={loading}
                    pagination={{ pageSize: 10 }}
                    style={{ background: 'transparent' }}
                  />
                </div>
              )
            }
          ]}
        />
      </Card>

      {/* SQL Detay İnceleme Modalı */}
      <Modal
        title={
          <Space>
            <CodeOutlined style={{ color: '#38bdf8' }} />
            <span style={{ color: '#f8fafc' }}>Sorgu Detay Analizi</span>
            <Tag color="#0284c7">ID: {selectedSqlModal?.queryId}</Tag>
          </Space>
        }
        open={!!selectedSqlModal}
        onCancel={() => setSelectedSqlModal(null)}
        width={850}
        footer={[
          <Button
            key="copy"
            icon={copied ? <CheckOutlined /> : <CopyOutlined />}
            onClick={() => selectedSqlModal && copyToClipboard(selectedSqlModal.query)}
            type="primary"
          >
            {copied ? 'Kopyalandı' : 'SQL Şablonunu Kopyala'}
          </Button>,
          <Button key="close" onClick={() => setSelectedSqlModal(null)}>
            Kapat
          </Button>
        ]}
      >
        {selectedSqlModal && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: 14 }}>
            {/* Metrik Rozetleri */}
            <Row gutter={[8, 8]}>
              <Col orientation="horizontal" span={6}>
                <div style={{ background: '#0f172a', padding: '8px 12px', borderRadius: 4, border: '1px solid #1e293b' }}>
                  <div style={{ color: '#64748b', fontSize: 11 }}>ÇALIŞMA ADEDİ</div>
                  <div style={{ color: '#38bdf8', fontSize: 16, fontWeight: 700 }}>{selectedSqlModal.calls.toLocaleString()}</div>
                </div>
              </Col>
              <Col span={6}>
                <div style={{ background: '#0f172a', padding: '8px 12px', borderRadius: 4, border: '1px solid #1e293b' }}>
                  <div style={{ color: '#64748b', fontSize: 11 }}>ORTALAMA SÜRE</div>
                  <div style={{ color: selectedSqlModal.meanExecTimeMs >= 50 ? '#ef4444' : '#10b981', fontSize: 16, fontWeight: 700 }}>
                    {selectedSqlModal.meanExecTimeMs.toFixed(2)} ms
                  </div>
                </div>
              </Col>
              <Col span={6}>
                <div style={{ background: '#0f172a', padding: '8px 12px', borderRadius: 4, border: '1px solid #1e293b' }}>
                  <div style={{ color: '#64748b', fontSize: 11 }}>PİK (MAX) SÜRE</div>
                  <div style={{ color: '#f59e0b', fontSize: 16, fontWeight: 700 }}>{selectedSqlModal.maxExecTimeMs.toFixed(2)} ms</div>
                </div>
              </Col>
              <Col span={6}>
                <div style={{ background: '#0f172a', padding: '8px 12px', borderRadius: 4, border: '1px solid #1e293b' }}>
                  <div style={{ color: '#64748b', fontSize: 11 }}>BUFFER CACHE HIT</div>
                  <div style={{ color: '#a855f7', fontSize: 16, fontWeight: 700 }}>%{selectedSqlModal.cacheHitPercentage}</div>
                </div>
              </Col>
            </Row>

            {/* SQL Kod Alanı */}
            <div>
              <div style={{ fontSize: 12, fontWeight: 600, color: '#94a3b8', marginBottom: 6 }}>
                Normalize Edilmiş SQL:
              </div>
              <pre
                style={{
                  background: '#030712',
                  border: '1px solid #1e293b',
                  borderRadius: 6,
                  padding: 14,
                  color: '#38bdf8',
                  fontFamily: "'JetBrains Mono', 'Roboto Mono', monospace",
                  fontSize: 12,
                  lineHeight: '1.6',
                  maxHeight: 320,
                  overflowY: 'auto',
                  whiteSpace: 'pre-wrap',
                  wordBreak: 'break-word'
                }}
              >
                {selectedSqlModal.query}
              </pre>
            </div>
          </div>
        )}
      </Modal>
    </div>
  );
}
