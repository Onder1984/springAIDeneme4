import React, { useState, useEffect, useCallback } from 'react';
import {
  Table,
  Input,
  InputNumber,
  Select,
  Tag,
  Button,
  Space,
  Card,
  Typography,
  Tooltip,
  Row,
  Col,
  Badge,
  Popover,
  Checkbox,
  Collapse,
  Divider
} from 'antd';
import {
  SearchOutlined,
  ReloadOutlined,
  EyeOutlined,
  ClearOutlined,
  DatabaseOutlined,
  SettingOutlined,
  FilterOutlined,
  DownOutlined,
  UpOutlined
} from '@ant-design/icons';
import axios from 'axios';
import dayjs from 'dayjs';

const { Text } = Typography;

const ALL_COLUMN_KEYS = [
  { key: 'id', label: 'Hedef ID' },
  { key: 'spotNo', label: 'Spot No' },
  { key: 'kayitTipi', label: 'Kayıt Niteliği' },
  { key: 'teshisKimlik', label: 'Teşhis' },
  { key: 'radarAdi', label: 'Radar & Görev' },
  { key: 'hedefYerBilgisi', label: 'Bölge / Yer Bilgisi' },
  { key: 'hedefMevziBilgisi', label: 'Mevzi / HSS' },
  { key: 'platformOrtami', label: 'Platform' },
  { key: 'frekans', label: 'Frekans (MHz)' },
  { key: 'pri', label: 'PRI (µs)' },
  { key: 'pw', label: 'PW (µs)' },
  { key: 'genlikDbm', label: 'Sinyal Gücü (dBm)' },
  { key: 'taciz', label: 'Taciz' },
  { key: 'etUygulamaDurumu', label: 'ET Durumu' },
  { key: 'sonTespitZamani', label: 'Son Tespit Zamanı' },
  { key: 'action', label: 'İşlem' }
];

export default function RadarTable({ onSelectEmission, initialFilter }) {
  const [data, setData] = useState([]);
  const [total, setTotal] = useState(0);
  const [loading, setLoading] = useState(false);
  const [page, setPage] = useState(1);
  const [pageSize, setPageSize] = useState(15);
  const [sortField, setSortField] = useState('sonTespitZamani');
  const [sortOrder, setSortOrder] = useState('descend');

  // Temel Filtreler
  const [searchText, setSearchText] = useState(initialFilter || '');
  const [teshisFilter, setTeshisFilter] = useState(null);
  const [kayitTipiFilter, setKayitTipiFilter] = useState(null);
  const [gorevFilter, setGorevFilter] = useState(null);
  const [tacizFilter, setTacizFilter] = useState(null);
  const [etFilter, setEtFilter] = useState(null);
  const [platformFilter, setPlatformFilter] = useState(null);

  // Gelişmiş Filtreler
  const [showAdvancedFilters, setShowAdvancedFilters] = useState(false);
  const [minFrekans, setMinFrekans] = useState(null);
  const [maxFrekans, setMaxFrekans] = useState(null);
  const [minPri, setMinPri] = useState(null);
  const [maxPri, setMaxPri] = useState(null);
  const [minPw, setMinPw] = useState(null);
  const [maxPw, setMaxPw] = useState(null);
  const [minGenlik, setMinGenlik] = useState(null);
  const [maxGenlik, setMaxGenlik] = useState(null);
  const [bolgeFilter, setBolgeFilter] = useState('');
  const [hssFilter, setHssFilter] = useState(null);

  // Seçilebilir Kolonlar (Column Selector)
  const [visibleColumns, setVisibleColumns] = useState(ALL_COLUMN_KEYS.map(c => c.key));

  const fetchEmissions = useCallback(async (
    p = page,
    s = pageSize,
    search = searchText,
    teshis = teshisFilter,
    gorev = gorevFilter,
    platform = platformFilter,
    taciz = tacizFilter,
    et = etFilter,
    sf = sortField,
    so = sortOrder,
    adv = {},
    kayitTipi = kayitTipiFilter
  ) => {
    setLoading(true);
    try {
      const params = {
        page: p,
        size: s,
        search: search || undefined,
        teshis: teshis || undefined,
        gorev: gorev || undefined,
        platform: platform || undefined,
        taciz: taciz !== null ? taciz : undefined,
        et: et || undefined,
        kayitTipi: (kayitTipi !== undefined ? kayitTipi : kayitTipiFilter) || undefined,
        minFrekans: adv.minFrekans ?? minFrekans ?? undefined,
        maxFrekans: adv.maxFrekans ?? maxFrekans ?? undefined,
        minPri: adv.minPri ?? minPri ?? undefined,
        maxPri: adv.maxPri ?? maxPri ?? undefined,
        minPw: adv.minPw ?? minPw ?? undefined,
        maxPw: adv.maxPw ?? maxPw ?? undefined,
        minGenlik: adv.minGenlik ?? minGenlik ?? undefined,
        maxGenlik: adv.maxGenlik ?? maxGenlik ?? undefined,
        bolge: adv.bolgeFilter ?? (bolgeFilter || undefined),
        hss: adv.hssFilter ?? hssFilter ?? undefined,
        sortField: sf || undefined,
        sortOrder: so || undefined
      };
      const res = await axios.get('/api/v1/tactical/emissions', { params });
      setData(res.data.content || []);
      setTotal(res.data.totalElements || 0);
      setPage(res.data.page || 1);
    } catch (err) {
      console.error('Radar kayıtları yüklenemedi:', err);
    } finally {
      setLoading(false);
    }
  }, [
    page, pageSize, searchText, teshisFilter, kayitTipiFilter, gorevFilter, platformFilter, tacizFilter, etFilter,
    minFrekans, maxFrekans, minPri, maxPri, minPw, maxPw, minGenlik, maxGenlik, bolgeFilter, hssFilter,
    sortField, sortOrder
  ]);

  useEffect(() => {
    fetchEmissions(1, pageSize, initialFilter || '');
  }, []);

  const handleSearchChange = (val) => {
    setSearchText(val);
    setPage(1);
    fetchEmissions(1, pageSize, val, teshisFilter, gorevFilter, platformFilter, tacizFilter, etFilter, sortField, sortOrder, {}, kayitTipiFilter);
  };

  const handleFilterChange = (setter, key) => (val) => {
    setter(val);
    setPage(1);
    const updatedFilters = {
      teshis: key === 'teshis' ? val : teshisFilter,
      kayitTipi: key === 'kayitTipi' ? val : kayitTipiFilter,
      gorev: key === 'gorev' ? val : gorevFilter,
      platform: key === 'platform' ? val : platformFilter,
      taciz: key === 'taciz' ? val : tacizFilter,
      et: key === 'et' ? val : etFilter,
    };
    fetchEmissions(1, pageSize, searchText, updatedFilters.teshis, updatedFilters.gorev, updatedFilters.platform, updatedFilters.taciz, updatedFilters.et, sortField, sortOrder, {}, updatedFilters.kayitTipi);
  };

  const handleApplyAdvancedFilters = () => {
    setPage(1);
    fetchEmissions(1, pageSize, searchText, teshisFilter, gorevFilter, platformFilter, tacizFilter, etFilter, sortField, sortOrder, {
      minFrekans, maxFrekans, minPri, maxPri, minPw, maxPw, minGenlik, maxGenlik, bolgeFilter, hssFilter
    }, kayitTipiFilter);
  };

  const handleResetFilters = () => {
    setSearchText('');
    setTeshisFilter(null);
    setKayitTipiFilter(null);
    setGorevFilter(null);
    setTacizFilter(null);
    setEtFilter(null);
    setPlatformFilter(null);
    setMinFrekans(null);
    setMaxFrekans(null);
    setMinPri(null);
    setMaxPri(null);
    setMinPw(null);
    setMaxPw(null);
    setMinGenlik(null);
    setMaxGenlik(null);
    setBolgeFilter('');
    setHssFilter(null);
    setPage(1);
    fetchEmissions(1, pageSize, '', null, null, null, null, null, sortField, sortOrder, {
      minFrekans: null, maxFrekans: null, minPri: null, maxPri: null, minPw: null, maxPw: null, minGenlik: null, maxGenlik: null, bolgeFilter: '', hssFilter: null
    }, null);
  };

  const handleTableChange = (pagination, filters, sorter) => {
    const newPage = pagination.current;
    const newSize = pagination.pageSize;
    setPage(newPage);
    setPageSize(newSize);

    let sf = sorter.field || 'sonTespitZamani';
    let so = sorter.order || 'descend';
    setSortField(sf);
    setSortOrder(so);

    fetchEmissions(newPage, newSize, searchText, teshisFilter, gorevFilter, platformFilter, tacizFilter, etFilter, sf, so, {}, kayitTipiFilter);
  };

  const getTeshisColor = (teshis) => {
    switch (teshis) {
      case 'DUSMAN':
      case 'DÜŞMAN': return 'error';
      case 'MUHTEMEL_DUSMAN':
      case 'MUHTEMEL_DÜŞMAN': return 'warning';
      case 'DOST': return 'success';
      default: return 'default';
    }
  };

  const allColumns = [
    {
      title: 'Hedef ID',
      dataIndex: 'id',
      key: 'id',
      width: 110,
      sorter: true,
      render: (text) => (
        <Tag color="cyan" style={{ cursor: 'pointer', fontWeight: 600 }}>
          {text}
        </Tag>
      )
    },
    {
      title: 'Nitelik',
      dataIndex: 'kayitTipi',
      key: 'kayitTipi',
      width: 145,
      sorter: true,
      render: (val, record) => {
        const isLob = val === 'HAM_LOB';
        if (isLob) {
          return (
            <div>
              <Tag color="orange" style={{ fontWeight: 600, fontSize: 11 }}>HAM LOB</Tag>
              {record.kestirimId ? (
                <div style={{ fontSize: 10, color: '#38bdf8', marginTop: 2 }}>
                  Bağlı: <Tag color="blue" size="small" style={{ fontSize: 9, padding: '0 4px', margin: 0 }}>{record.kestirimId}</Tag>
                </div>
              ) : (
                <div style={{ fontSize: 10, color: '#fbbf24', marginTop: 2 }}>
                  Başıboş LOB
                </div>
              )}
            </div>
          );
        }
        return (
          <Tag color="cyan" style={{ fontWeight: 600, fontSize: 11 }}>
            KESTİRİM
          </Tag>
        );
      }
    },
    {
      title: 'Teşhis',
      dataIndex: 'teshisKimlik',
      key: 'teshisKimlik',
      width: 120,
      sorter: true,
      render: (text) => <Tag color={getTeshisColor(text)}>{text || 'BELİRSİZ'}</Tag>
    },
    {
      title: 'Spot No',
      dataIndex: 'spotNo',
      key: 'spotNo',
      width: 100,
      sorter: true,
      render: (text) => (
        text ? (
          <Tag color="geekblue" style={{ fontWeight: 700, fontFamily: 'monospace' }}>
            {text}
          </Tag>
        ) : (
          <span style={{ color: '#64748b', fontSize: 11 }}>-</span>
        )
      )
    },
    {
      title: 'Radar & Görev',
      dataIndex: 'radarAdi',
      key: 'radarAdi',
      width: 190,
      sorter: true,
      render: (text, record) => (
        <div>
          <div style={{ display: 'flex', alignItems: 'center', gap: 6 }}>
            <Text strong style={{ color: '#f1f5f9' }}>{text || 'Bilinmiyor'}</Text>
            {record.spotNo && (
              <Tag color="geekblue" size="small" style={{ fontSize: 10, padding: '0 4px', margin: 0, fontWeight: 700 }}>
                {record.spotNo}
              </Tag>
            )}
          </div>
          {record.radarGorevi && (
            <div style={{ fontSize: 11, color: '#94a3b8', marginTop: 2 }}>{record.radarGorevi}</div>
          )}
        </div>
      )
    },
    {
      title: 'Bölge / Konum',
      dataIndex: 'hedefYerBilgisi',
      key: 'hedefYerBilgisi',
      width: 180,
      sorter: true,
      render: (text, record) => {
        if (record.kayitTipi === 'HAM_LOB') {
          return (
            <div>
              <span style={{ color: '#f59e0b', fontSize: 11, fontWeight: 500 }}>
                {record.yon != null ? `🧭 Kerteriz: ${record.yon.toFixed(1)}°` : 'Kerteriz (LOB)'}
              </span>
              {text && <div style={{ color: '#94a3b8', fontSize: 10 }}>{text}</div>}
            </div>
          );
        }
        return text ? <Text style={{ color: '#e2e8f0', fontSize: 12 }}>{text}</Text> : '-';
      }
    },
    {
      title: 'Mevzi / HSS',
      dataIndex: 'hedefMevziBilgisi',
      key: 'hedefMevziBilgisi',
      width: 160,
      sorter: true,
      render: (text, record) => (
        <div>
          <div>{text || '-'}</div>
          {record.hss && <Tag color="volcano" style={{ fontSize: 10, marginTop: 2 }}>{record.hss}</Tag>}
        </div>
      )
    },
    {
      title: 'Platform',
      dataIndex: 'platformOrtami',
      key: 'platformOrtami',
      width: 90,
      sorter: true,
      render: (text) => text ? <Tag color="blue">{text}</Tag> : '-'
    },
    {
      title: 'Frekans (MHz)',
      dataIndex: 'minFrekansMhz',
      key: 'frekans',
      width: 150,
      sorter: true,
      render: (_, record) => (
        <Text style={{ fontFamily: 'JetBrains Mono, monospace', fontSize: 12 }}>
          {record.minFrekansMhz?.toFixed(0)} - {record.maxFrekansMhz?.toFixed(0)}
        </Text>
      )
    },
    {
      title: 'PRI (µs)',
      dataIndex: 'minPriMicroSec',
      key: 'pri',
      width: 130,
      sorter: true,
      render: (_, record) => (
        <Text style={{ fontFamily: 'JetBrains Mono, monospace', fontSize: 11 }}>
          {record.minPriMicroSec?.toFixed(0)} - {record.maxPriMicroSec?.toFixed(0)}
        </Text>
      )
    },
    {
      title: 'PW (µs)',
      dataIndex: 'minPwMicroSec',
      key: 'pw',
      width: 120,
      sorter: true,
      render: (_, record) => (
        <Text style={{ fontFamily: 'JetBrains Mono, monospace', fontSize: 11 }}>
          {record.minPwMicroSec?.toFixed(1)} - {record.maxPwMicroSec?.toFixed(1)}
        </Text>
      )
    },
    {
      title: 'Sinyal Gücü',
      dataIndex: 'genlikDbm',
      key: 'genlikDbm',
      width: 110,
      sorter: true,
      render: (val) => val ? `${val.toFixed(1)} dBm` : '-'
    },
    {
      title: 'Taciz',
      dataIndex: 'taciz',
      key: 'taciz',
      width: 90,
      sorter: true,
      render: (val) => val ? (
        <Badge status="error" text={<Text type="danger" strong>TACİZ</Text>} />
      ) : (
        <Text type="secondary">-</Text>
      )
    },
    {
      title: 'ET Durumu',
      dataIndex: 'etUygulamaDurumu',
      key: 'etUygulamaDurumu',
      width: 120,
      sorter: true,
      render: (val) => {
        if (!val || val === 'ET_YOK' || val === 'UYGULANMIYOR') return <Text type="secondary">YOK</Text>;
        if (val === 'UYGULANIYOR') return <Tag color="cyan">UYGULANIYOR</Tag>;
        if (val === 'SUSTURDU') return <Tag color="purple">SUSTURULDU</Tag>;
        return <Tag>{val}</Tag>;
      }
    },
    {
      title: 'Son Tespit',
      dataIndex: 'sonTespitZamani',
      key: 'sonTespitZamani',
      width: 160,
      sorter: true,
      defaultSortOrder: 'descend',
      render: (val, record) => record.sonTespitZamaniFormatted || (val ? dayjs(val).format('DD.MM.YYYY HH:mm:ss') : '-')
    },
    {
      title: 'İşlem',
      key: 'action',
      width: 80,
      fixed: 'right',
      render: (_, record) => (
        <Button
          type="primary"
          size="small"
          icon={<EyeOutlined />}
          onClick={(e) => {
            e.stopPropagation();
            onSelectEmission(record.id);
          }}
        >
          Detay
        </Button>
      )
    }
  ];

  const columns = allColumns.filter(col => visibleColumns.includes(col.key));

  const hasAdvancedFilters = Boolean(
    minFrekans || maxFrekans || minPri || maxPri || minPw || maxPw || minGenlik || maxGenlik || bolgeFilter || hssFilter
  );

  return (
    <Card
      style={{
        backgroundColor: '#0f172a',
        borderColor: '#1e293b',
        borderRadius: 8
      }}
      bodyStyle={{ padding: 16 }}
    >
      {/* Filtre ve Arama Çubuğu */}
      <Row gutter={[12, 12]} style={{ marginBottom: 12 }} align="middle">
        <Col xs={24} sm={8} md={5}>
          <Input
            placeholder="Radar adı, ID, mevzi veya görev ara..."
            prefix={<SearchOutlined style={{ color: '#64748b' }} />}
            value={searchText}
            onChange={(e) => handleSearchChange(e.target.value)}
            allowClear
          />
        </Col>
        <Col xs={12} sm={4} md={3}>
          <Select
            placeholder="Kayıt Niteliği"
            value={kayitTipiFilter}
            onChange={handleFilterChange(setKayitTipiFilter, 'kayitTipi')}
            allowClear
            style={{ width: '100%' }}
            options={[
              { label: 'KESTİRİM (ELİPSLİ)', value: 'KESTIRIM_ELIPS' },
              { label: 'HAM LOB (KERTERİZ)', value: 'HAM_LOB' }
            ]}
          />
        </Col>
        <Col xs={12} sm={4} md={3}>
          <Select
            placeholder="Teşhis"
            value={teshisFilter}
            onChange={handleFilterChange(setTeshisFilter, 'teshis')}
            allowClear
            style={{ width: '100%' }}
            options={[
              { label: 'DÜŞMAN', value: 'DUSMAN' },
              { label: 'MUHTEMEL DÜŞMAN', value: 'MUHTEMEL_DUSMAN' },
              { label: 'DOST', value: 'DOST' },
              { label: 'BİLİNMEYEN', value: 'BILINMEYEN' }
            ]}
          />
        </Col>
        <Col xs={12} sm={4} md={3}>
          <Select
            placeholder="Radar Görevi"
            value={gorevFilter}
            onChange={handleFilterChange(setGorevFilter, 'gorev')}
            allowClear
            style={{ width: '100%' }}
            options={[
              { label: 'Atış Kontrol', value: 'Atış Kontrol' },
              { label: 'Erken İhbar', value: 'Erken İhbar' },
              { label: 'Hava Savunma', value: 'Hava Savunma' },
              { label: 'Arama', value: 'Arama' },
              { label: 'Hedef Takip', value: 'Hedef Takip' }
            ]}
          />
        </Col>
        <Col xs={12} sm={4} md={2}>
          <Select
            placeholder="Platform"
            value={platformFilter}
            onChange={handleFilterChange(setPlatformFilter, 'platform')}
            allowClear
            style={{ width: '100%' }}
            options={[
              { label: 'KARA', value: 'KARA' },
              { label: 'HAVA', value: 'HAVA' },
              { label: 'DENİZ', value: 'DENIZ' }
            ]}
          />
        </Col>
        <Col xs={12} sm={4} md={2}>
          <Select
            placeholder="Taciz"
            value={tacizFilter}
            onChange={handleFilterChange(setTacizFilter, 'taciz')}
            allowClear
            style={{ width: '100%' }}
            options={[
              { label: 'Taciz Var', value: true },
              { label: 'Taciz Yok', value: false }
            ]}
          />
        </Col>
        <Col xs={12} sm={4} md={2}>
          <Select
            placeholder="ET"
            value={etFilter}
            onChange={handleFilterChange(setEtFilter, 'et')}
            allowClear
            style={{ width: '100%' }}
            options={[
              { label: 'Uygulanıyor', value: 'UYGULANIYOR' },
              { label: 'Susturuldu', value: 'SUSTURDU' },
              { label: 'ET Yok', value: 'ET_YOK' }
            ]}
          />
        </Col>
        <Col xs={24} sm={8} md={4} style={{ textAlign: 'right' }}>
          <Space>
            <Button
              type={showAdvancedFilters ? 'primary' : 'default'}
              icon={<FilterOutlined />}
              onClick={() => setShowAdvancedFilters(!showAdvancedFilters)}
            >
              Filtreler {hasAdvancedFilters && <Badge status="processing" color="#38bdf8" />}
            </Button>
            <Popover
              title="Görünecek Sütunları Seç"
              trigger="click"
              placement="bottomRight"
              content={
                <div style={{ maxHeight: 300, overflowY: 'auto' }}>
                  <Checkbox.Group
                    value={visibleColumns}
                    onChange={(checked) => setVisibleColumns(checked)}
                    style={{ display: 'flex', flexDirection: 'column', gap: 6 }}
                  >
                    {ALL_COLUMN_KEYS.map((col) => (
                      <Checkbox key={col.key} value={col.key}>
                        {col.label}
                      </Checkbox>
                    ))}
                  </Checkbox.Group>
                </div>
              }
            >
              <Tooltip title="Sütunları Yönet">
                <Button icon={<SettingOutlined />} />
              </Tooltip>
            </Popover>
            <Tooltip title="Filtreleri Sıfırla">
              <Button icon={<ClearOutlined />} onClick={handleResetFilters} />
            </Tooltip>
            <Tooltip title="Sunucudan Yenile">
              <Button icon={<ReloadOutlined />} onClick={() => fetchEmissions()} loading={loading} />
            </Tooltip>
          </Space>
        </Col>
      </Row>

      {/* Açılır Gelişmiş Filtre Paneli */}
      {showAdvancedFilters && (
        <Card
          size="small"
          style={{
            backgroundColor: '#0b1329',
            borderColor: '#1e293b',
            marginBottom: 16,
            borderRadius: 6
          }}
        >
          <Row gutter={[16, 12]} align="middle">
            <Col xs={24} sm={12} md={6}>
              <Text style={{ fontSize: 12, color: '#94a3b8' }}>Frekans Aralığı (MHz):</Text>
              <Space style={{ width: '100%', marginTop: 4 }}>
                <InputNumber
                  placeholder="Min MHz"
                  value={minFrekans}
                  onChange={setMinFrekans}
                  style={{ width: '100%' }}
                />
                <Text type="secondary">-</Text>
                <InputNumber
                  placeholder="Max MHz"
                  value={maxFrekans}
                  onChange={setMaxFrekans}
                  style={{ width: '100%' }}
                />
              </Space>
            </Col>
            <Col xs={24} sm={12} md={6}>
              <Text style={{ fontSize: 12, color: '#94a3b8' }}>PRI Aralığı (µs):</Text>
              <Space style={{ width: '100%', marginTop: 4 }}>
                <InputNumber
                  placeholder="Min µs"
                  value={minPri}
                  onChange={setMinPri}
                  style={{ width: '100%' }}
                />
                <Text type="secondary">-</Text>
                <InputNumber
                  placeholder="Max µs"
                  value={maxPri}
                  onChange={setMaxPri}
                  style={{ width: '100%' }}
                />
              </Space>
            </Col>
            <Col xs={24} sm={12} md={6}>
              <Text style={{ fontSize: 12, color: '#94a3b8' }}>PW Aralığı (µs):</Text>
              <Space style={{ width: '100%', marginTop: 4 }}>
                <InputNumber
                  placeholder="Min µs"
                  value={minPw}
                  onChange={setMinPw}
                  style={{ width: '100%' }}
                />
                <Text type="secondary">-</Text>
                <InputNumber
                  placeholder="Max µs"
                  value={maxPw}
                  onChange={setMaxPw}
                  style={{ width: '100%' }}
                />
              </Space>
            </Col>
            <Col xs={24} sm={12} md={6}>
              <Text style={{ fontSize: 12, color: '#94a3b8' }}>Sinyal Gücü (dBm):</Text>
              <Space style={{ width: '100%', marginTop: 4 }}>
                <InputNumber
                  placeholder="Min dBm"
                  value={minGenlik}
                  onChange={setMinGenlik}
                  style={{ width: '100%' }}
                />
                <Text type="secondary">-</Text>
                <InputNumber
                  placeholder="Max dBm"
                  value={maxGenlik}
                  onChange={setMaxGenlik}
                  style={{ width: '100%' }}
                />
              </Space>
            </Col>
            <Col xs={24} sm={12} md={8}>
              <Text style={{ fontSize: 12, color: '#94a3b8' }}>Bölge / Konum Filtresi:</Text>
              <Input
                placeholder="Örn: Ege, Midilli, Akdeniz, Suriye..."
                value={bolgeFilter}
                onChange={(e) => setBolgeFilter(e.target.value)}
                style={{ marginTop: 4 }}
                allowClear
              />
            </Col>
            <Col xs={24} sm={12} md={8}>
              <Text style={{ fontSize: 12, color: '#94a3b8' }}>HSS (Hava Savunma Sistemi):</Text>
              <Select
                placeholder="HSS seçiniz"
                value={hssFilter}
                onChange={setHssFilter}
                allowClear
                style={{ width: '100%', marginTop: 4 }}
                options={[
                  { label: 'S-400', value: 'S-400' },
                  { label: 'S-300', value: 'S-300' },
                  { label: 'Patriot PAC-3', value: 'Patriot' },
                  { label: 'Tor-M2', value: 'Tor-M2' },
                  { label: 'Pantsir-S1', value: 'Pantsir' },
                  { label: 'Hisar-O', value: 'Hisar-O' },
                  { label: 'Kalkan', value: 'Kalkan' }
                ]}
              />
            </Col>
            <Col xs={24} sm={24} md={8} style={{ textAlign: 'right', marginTop: 18 }}>
              <Space>
                <Button onClick={handleResetFilters} size="middle">
                  Temizle
                </Button>
                <Button type="primary" onClick={handleApplyAdvancedFilters} size="middle">
                  Gelişmiş Filtreleri Uygula
                </Button>
              </Space>
            </Col>
          </Row>
        </Card>
      )}

      {/* Sunucu Taraflı Sayfalamalı Ant Design Tablosu */}
      <Table
        dataSource={data.map(item => ({ ...item, key: item.id }))}
        columns={columns}
        loading={loading}
        size="middle"
        scroll={{ x: 1300 }}
        pagination={{
          current: page,
          pageSize: pageSize,
          total: total,
          showSizeChanger: true,
          pageSizeOptions: ['10', '15', '25', '50', '100'],
          showTotal: (t) => (
            <span style={{ color: '#94a3b8', fontSize: 12 }}>
              <DatabaseOutlined style={{ marginRight: 4, color: '#38bdf8' }} />
              PostgreSQL Sayfalama: Toplam {t} radar kaydı
            </span>
          )
        }}
        onChange={handleTableChange}
        onRow={(record) => ({
          onClick: () => onSelectEmission(record.id),
          style: { cursor: 'pointer' }
        })}
      />
    </Card>
  );
}
