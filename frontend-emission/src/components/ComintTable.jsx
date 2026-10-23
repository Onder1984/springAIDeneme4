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
  Popover,
  Checkbox,
  Badge
} from 'antd';
import {
  SearchOutlined,
  ReloadOutlined,
  EyeOutlined,
  LockOutlined,
  UnlockOutlined,
  ClearOutlined,
  DatabaseOutlined,
  SettingOutlined,
  FilterOutlined,
  SecurityScanOutlined
} from '@ant-design/icons';
import axios from 'axios';
import dayjs from 'dayjs';

const { Text } = Typography;

const ALL_COMINT_COLUMNS = [
  { key: 'id', label: 'Kayıt ID' },
  { key: 'kayitTipi', label: 'Kayıt Niteliği' },
  { key: 'teshisKimlik', label: 'Teşhis' },
  { key: 'cagriAgi', label: 'Çağrı Ağı (Link)' },
  { key: 'lisan', label: 'Lisan' },
  { key: 'tip', label: 'Bant / Tip' },
  { key: 'haberlesmeSekli', label: 'Haberleşme Şekli' },
  { key: 'protokol', label: 'Protokol' },
  { key: 'modulasyon', label: 'Modülasyon' },
  { key: 'frekans', label: 'Frekans (MHz)' },
  { key: 'kriptoluMu', label: 'Kripto Durumu' },
  { key: 'genlikDbm', label: 'Sinyal Gücü (dBm)' },
  { key: 'hedefYerBilgisi', label: 'Bölge / Konum' },
  { key: 'sonTespitZamani', label: 'Son Tespit Zamanı' },
  { key: 'action', label: 'İşlem' }
];

export default function ComintTable({ onSelectComint, initialFilter }) {
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
  const [tipFilter, setTipFilter] = useState(null);
  const [lisanFilter, setLisanFilter] = useState(null);
  const [haberlesmeFilter, setHaberlesmeFilter] = useState(null);
  const [kriptoFilter, setKriptoFilter] = useState(null);

  // Gelişmiş Filtreler
  const [showAdvancedFilters, setShowAdvancedFilters] = useState(false);
  const [cagriAdiFilter, setCagriAdiFilter] = useState('');
  const [karsiCagriAdiFilter, setKarsiCagriAdiFilter] = useState('');
  const [zamanAraligiFilter, setZamanAraligiFilter] = useState(null);
  const [minFrekans, setMinFrekans] = useState(null);
  const [maxFrekans, setMaxFrekans] = useState(null);
  const [minGenlik, setMinGenlik] = useState(null);
  const [maxGenlik, setMaxGenlik] = useState(null);
  const [bolgeFilter, setBolgeFilter] = useState('');
  const [protokolFilter, setProtokolFilter] = useState(null);
  const [modulasyonFilter, setModulasyonFilter] = useState(null);

  // Sütun Yönetimi
  const [visibleColumns, setVisibleColumns] = useState(ALL_COMINT_COLUMNS.map(c => c.key));

  const fetchComints = useCallback(async (
    p = page,
    s = pageSize,
    search = searchText,
    teshis = teshisFilter,
    tip = tipFilter,
    lisan = lisanFilter,
    haberlesme = haberlesmeFilter,
    kripto = kriptoFilter,
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
        tip: tip || undefined,
        lisan: lisan || undefined,
        haberlesme: haberlesme || undefined,
        kripto: kripto !== null ? kripto : undefined,
        kayitTipi: (kayitTipi !== undefined ? kayitTipi : kayitTipiFilter) || undefined,
        cagriAdi: adv.cagriAdiFilter !== undefined ? (adv.cagriAdiFilter || undefined) : (cagriAdiFilter || undefined),
        karsiCagriAdi: adv.karsiCagriAdiFilter !== undefined ? (adv.karsiCagriAdiFilter || undefined) : (karsiCagriAdiFilter || undefined),
        minFrekans: adv.minFrekans ?? minFrekans ?? undefined,
        maxFrekans: adv.maxFrekans ?? maxFrekans ?? undefined,
        minGenlik: adv.minGenlik ?? minGenlik ?? undefined,
        maxGenlik: adv.maxGenlik ?? maxGenlik ?? undefined,
        bolge: adv.bolgeFilter ?? (bolgeFilter || undefined),
        protokol: adv.protokolFilter ?? protokolFilter ?? undefined,
        modulasyon: adv.modulasyonFilter ?? modulasyonFilter ?? undefined,
        zamanAraligi: adv.zamanAraligiFilter !== undefined ? (adv.zamanAraligiFilter || undefined) : (zamanAraligiFilter || undefined),
        sortField: sf || undefined,
        sortOrder: so || undefined
      };
      const res = await axios.get('/api/v1/tactical/comints', { params });
      setData(res.data.content || []);
      setTotal(res.data.totalElements || 0);
      setPage(res.data.page || 1);
    } catch (err) {
      console.error('COMINT kayıtları yüklenemedi:', err);
    } finally {
      setLoading(false);
    }
  }, [
    page, pageSize, searchText, teshisFilter, kayitTipiFilter, tipFilter, lisanFilter, haberlesmeFilter, kriptoFilter,
    cagriAdiFilter, karsiCagriAdiFilter, minFrekans, maxFrekans, minGenlik, maxGenlik, bolgeFilter, protokolFilter,
    modulasyonFilter, zamanAraligiFilter, sortField, sortOrder
  ]);

  useEffect(() => {
    fetchComints(1, pageSize, initialFilter || '');
  }, []);

  const handleSearchChange = (val) => {
    setSearchText(val);
    setPage(1);
    fetchComints(1, pageSize, val, teshisFilter, tipFilter, lisanFilter, haberlesmeFilter, kriptoFilter, sortField, sortOrder, {}, kayitTipiFilter);
  };

  const handleFilterChange = (setter, key) => (val) => {
    setter(val);
    setPage(1);
    const updated = {
      teshis: key === 'teshis' ? val : teshisFilter,
      kayitTipi: key === 'kayitTipi' ? val : kayitTipiFilter,
      tip: key === 'tip' ? val : tipFilter,
      lisan: key === 'lisan' ? val : lisanFilter,
      haberlesme: key === 'haberlesme' ? val : haberlesmeFilter,
      kripto: key === 'kripto' ? val : kriptoFilter,
    };
    fetchComints(1, pageSize, searchText, updated.teshis, updated.tip, updated.lisan, updated.haberlesme, updated.kripto, sortField, sortOrder, {}, updated.kayitTipi);
  };

  const handleApplyAdvancedFilters = () => {
    setPage(1);
    fetchComints(1, pageSize, searchText, teshisFilter, tipFilter, lisanFilter, haberlesmeFilter, kriptoFilter, sortField, sortOrder, {
      cagriAdiFilter, karsiCagriAdiFilter, minFrekans, maxFrekans, minGenlik, maxGenlik, bolgeFilter, protokolFilter, modulasyonFilter, zamanAraligiFilter
    }, kayitTipiFilter);
  };

  const handleResetFilters = () => {
    setSearchText('');
    setTeshisFilter(null);
    setKayitTipiFilter(null);
    setTipFilter(null);
    setLisanFilter(null);
    setHaberlesmeFilter(null);
    setKriptoFilter(null);
    setCagriAdiFilter('');
    setKarsiCagriAdiFilter('');
    setZamanAraligiFilter(null);
    setMinFrekans(null);
    setMaxFrekans(null);
    setMinGenlik(null);
    setMaxGenlik(null);
    setBolgeFilter('');
    setProtokolFilter(null);
    setModulasyonFilter(null);
    setPage(1);
    fetchComints(1, pageSize, '', null, null, null, null, null, sortField, sortOrder, {
      cagriAdiFilter: '', karsiCagriAdiFilter: '', minFrekans: null, maxFrekans: null, minGenlik: null, maxGenlik: null, bolgeFilter: '', protokolFilter: null, modulasyonFilter: null, zamanAraligiFilter: null
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

    fetchComints(newPage, newSize, searchText, teshisFilter, tipFilter, lisanFilter, haberlesmeFilter, kriptoFilter, sf, so, {
      cagriAdiFilter, karsiCagriAdiFilter, minFrekans, maxFrekans, minGenlik, maxGenlik, bolgeFilter, protokolFilter, modulasyonFilter
    }, kayitTipiFilter);
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
      title: 'Kayıt ID',
      dataIndex: 'id',
      key: 'id',
      width: 115,
      sorter: true,
      render: (text) => (
        <Tag color="purple" style={{ cursor: 'pointer', fontWeight: 600 }}>
          {text}
        </Tag>
      )
    },
    {
      title: 'Kayıt Niteliği',
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
      title: 'Çağrı Ağı (Link)',
      dataIndex: 'cagriAdi',
      key: 'cagriAgi',
      width: 230,
      sorter: true,
      filterDropdown: ({ confirm, clearFilters }) => (
        <div style={{ padding: 12, backgroundColor: '#0f172a', border: '1px solid #1e293b', borderRadius: 8, width: 260 }}>
          <div style={{ marginBottom: 8 }}>
            <Text style={{ fontSize: 12, color: '#38bdf8', fontWeight: 600 }}>🎙️ Çağrı Adı:</Text>
            <Input
              placeholder="Örn: VOLGA-01"
              value={cagriAdiFilter}
              onChange={(e) => setCagriAdiFilter(e.target.value)}
              onPressEnter={() => {
                confirm();
                handleApplyAdvancedFilters();
              }}
              style={{ marginTop: 4 }}
              allowClear
            />
          </div>
          <div style={{ marginBottom: 12 }}>
            <Text style={{ fontSize: 12, color: '#c084fc', fontWeight: 600 }}>📻 Karşı Çağrı Adı:</Text>
            <Input
              placeholder="Örn: NATO-TASK-09"
              value={karsiCagriAdiFilter}
              onChange={(e) => setKarsiCagriAdiFilter(e.target.value)}
              onPressEnter={() => {
                confirm();
                handleApplyAdvancedFilters();
              }}
              style={{ marginTop: 4 }}
              allowClear
            />
          </div>
          <Space style={{ display: 'flex', justifyContent: 'space-between' }}>
            <Button
              size="small"
              onClick={() => {
                setCagriAdiFilter('');
                setKarsiCagriAdiFilter('');
                clearFilters && clearFilters();
                confirm();
                fetchComints(1, pageSize, searchText, teshisFilter, tipFilter, lisanFilter, haberlesmeFilter, kriptoFilter, sortField, sortOrder, {
                  cagriAdiFilter: '', karsiCagriAdiFilter: '', minFrekans, maxFrekans, minGenlik, maxGenlik, bolgeFilter, protokolFilter
                }, kayitTipiFilter);
              }}
            >
              Sıfırla
            </Button>
            <Button
              type="primary"
              size="small"
              onClick={() => {
                confirm();
                handleApplyAdvancedFilters();
              }}
            >
              Filtrele
            </Button>
          </Space>
        </div>
      ),
      filterIcon: () => (
        <FilterOutlined style={{ color: (cagriAdiFilter || karsiCagriAdiFilter) ? '#38bdf8' : undefined }} />
      ),
      render: (_, record) => (
        <Space>
          <Tag color="cyan" style={{ fontWeight: 700 }}>{record.cagriAdi || 'Bilinmiyor'}</Tag>
          {record.karsiCagriAdi && (
            <>
              <Text type="secondary">→</Text>
              <Tag color="geekblue" style={{ fontWeight: 700 }}>{record.karsiCagriAdi}</Tag>
            </>
          )}
        </Space>
      )
    },
    {
      title: 'Lisan',
      dataIndex: 'lisan',
      key: 'lisan',
      width: 110,
      sorter: true,
      render: (text) => text ? <Tag color="gold">{text}</Tag> : '-'
    },
    {
      title: 'Bant / Tip',
      dataIndex: 'tip',
      key: 'tip',
      width: 110,
      sorter: true,
      render: (text) => text ? <Tag color="blue">{text}</Tag> : '-'
    },
    {
      title: 'Haberleşme Şekli',
      dataIndex: 'haberlesmeSekli',
      key: 'haberlesmeSekli',
      width: 160,
      sorter: true,
      render: (text) => {
        if (!text) return '-';
        if (text.includes('KRIPTO')) return <Tag color="red" icon={<LockOutlined />}>{text}</Tag>;
        if (text.includes('GAYDA') || text.includes('GURULTU') || text.includes('GÜRÜLTÜ')) return <Tag color="magenta">{text}</Tag>;
        return <Tag color="default">{text}</Tag>;
      }
    },
    {
      title: 'Protokol',
      dataIndex: 'protokol',
      key: 'protokol',
      width: 145,
      sorter: true,
      render: (val) => val ? <Tag color="cyan">{val}</Tag> : '-'
    },
    {
      title: 'Modülasyon',
      dataIndex: 'modulasyon',
      key: 'modulasyon',
      width: 125,
      sorter: true,
      render: (val) => val ? <Tag color="geekblue">{val}</Tag> : '-'
    },
    {
      title: 'Frekans (MHz)',
      dataIndex: 'minFrekansMhz',
      key: 'frekans',
      width: 150,
      sorter: true,
      render: (_, record) => (
        <Text style={{ fontFamily: 'JetBrains Mono, monospace', fontSize: 12 }}>
          {record.minFrekansMhz?.toFixed(1)} - {record.maxFrekansMhz?.toFixed(1)}
        </Text>
      )
    },
    {
      title: 'Kripto Durumu',
      dataIndex: 'haberlesmeSekli',
      key: 'kriptoluMu',
      width: 130,
      sorter: true,
      render: (val) => val === 'KRIPTO' ? (
        <Tag color="error" icon={<LockOutlined />}>KRİPTOLU</Tag>
      ) : (
        <Tag color="default" icon={<UnlockOutlined />}>AÇIK SES</Tag>
      )
    },
    {
      title: 'Sinyal Gücü (dBm)',
      dataIndex: 'genlikDbm',
      key: 'genlikDbm',
      width: 135,
      sorter: true,
      render: (val) => val ? `${val.toFixed(1)} dBm` : '-'
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
      title: 'Son Tespit Zamanı',
      dataIndex: 'sonTespitZamani',
      key: 'sonTespitZamani',
      width: 175,
      sorter: true,
      defaultSortOrder: 'descend',
      render: (val, record) => record.sonTespitZamaniFormatted || (val ? dayjs(val).format('DD.MM.YYYY HH:mm:ss') : '-')
    },
    {
      title: 'İşlem',
      key: 'action',
      width: 85,
      fixed: 'right',
      render: (_, record) => (
        <Button
          type="primary"
          size="small"
          icon={<EyeOutlined />}
          onClick={(e) => {
            e.stopPropagation();
            onSelectComint(record.id);
          }}
        >
          Detay
        </Button>
      )
    }
  ];

  const columns = allColumns.filter(col => visibleColumns.includes(col.key));

  const hasAdvancedFilters = Boolean(
    cagriAdiFilter || karsiCagriAdiFilter || minFrekans || maxFrekans || minGenlik || maxGenlik || bolgeFilter || protokolFilter || modulasyonFilter
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
      {/* Muhabere Listesi Taktik Başlık ve Metrik Çubuğu */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: 14, paddingBottom: 12, borderBottom: '1px solid #1e293b' }}>
        <Space align="center" size={10}>
          <SecurityScanOutlined style={{ fontSize: 20, color: '#c084fc' }} />
          <div>
            <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
              <Text strong style={{ fontSize: 16, color: '#f8fafc', letterSpacing: 0.5, fontFamily: 'Rajdhani, sans-serif' }}>
                MUHABERE (COMINT) TAKTİK YAYIN LİSTESİ
              </Text>
              <Tag color="purple" style={{ fontSize: 10, lineHeight: '16px', margin: 0, fontWeight: 600 }}>30 PARAMETRE</Tag>
            </div>
            <Text type="secondary" style={{ fontSize: 11 }}>
              Telsiz çağrı ağları, frekans spektrumu, kestirim elipsleri ve tekil DF kerterizleri
            </Text>
          </div>
        </Space>
        <div style={{ textAlign: 'right' }}>
          <Text style={{ fontSize: 12, color: '#94a3b8' }}>
            Toplam: <strong style={{ color: '#c084fc', fontSize: 13 }}>{total.toLocaleString()}</strong> aktif muhabere kaydı
          </Text>
        </div>
      </div>

      {/* Filtre ve Arama Çubuğu */}
      <Row gutter={[12, 12]} style={{ marginBottom: 12 }} align="middle">
        <Col xs={24} sm={8} md={5}>
          <Input
            placeholder="Çağrı adı, ID, lisan veya konum ara..."
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
              { label: 'DOST', value: 'DOST' }
            ]}
          />
        </Col>
        <Col xs={12} sm={4} md={2}>
          <Select
            placeholder="Bant"
            value={tipFilter}
            onChange={handleFilterChange(setTipFilter, 'tip')}
            allowClear
            style={{ width: '100%' }}
            options={[
              { label: 'HF', value: 'HF' },
              { label: 'VHF', value: 'VHF' }
            ]}
          />
        </Col>
        <Col xs={12} sm={4} md={2}>
          <Select
            placeholder="Lisan"
            value={lisanFilter}
            onChange={handleFilterChange(setLisanFilter, 'lisan')}
            allowClear
            style={{ width: '100%' }}
            options={[
              { label: 'Türkçe', value: 'Türkçe' },
              { label: 'Rusça', value: 'Rusça' },
              { label: 'Arapça', value: 'Arapça' },
              { label: 'Yunanca', value: 'Yunanca' },
              { label: 'İngilizce', value: 'İngilizce' },
              { label: 'Tanımsız', value: 'Tanımsız' }
            ]}
          />
        </Col>
        <Col xs={12} sm={4} md={3}>
          <Select
            placeholder="Haberleşme Şekli"
            value={haberlesmeFilter}
            onChange={handleFilterChange(setHaberlesmeFilter, 'haberlesme')}
            allowClear
            style={{ width: '100%' }}
            options={[
              { label: 'Açık Ses', value: 'SES' },
              { label: 'Kripto', value: 'KRIPTO' },
              { label: 'Veri', value: 'VERI' },
              { label: 'Darbe Patlama (Burst)', value: 'DARBE_PATLAMA' },
              { label: 'Karıştırma Gayda', value: 'KARISTIRMA_GAYDA' },
              { label: 'Karıştırma Gürültü', value: 'KARISTIRMA_GURULTU' }
            ]}
          />
        </Col>
        <Col xs={12} sm={4} md={2}>
          <Select
            placeholder="Kripto"
            value={kriptoFilter}
            onChange={handleFilterChange(setKriptoFilter, 'kripto')}
            allowClear
            style={{ width: '100%' }}
            options={[
              { label: 'Kriptolu', value: true },
              { label: 'Açık Ses', value: false }
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
              Filtreler {hasAdvancedFilters && <Badge status="processing" color="#c084fc" />}
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
                    {ALL_COMINT_COLUMNS.map((col) => (
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
              <Button icon={<ReloadOutlined />} onClick={() => fetchComints()} loading={loading} />
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
              <Text style={{ fontSize: 12, color: '#38bdf8', fontWeight: 600 }}>🎙️ Çağrı Adı (Callsign):</Text>
              <Input
                placeholder="Örn: VOLGA-01, KARTAL..."
                value={cagriAdiFilter}
                onChange={(e) => setCagriAdiFilter(e.target.value)}
                onPressEnter={handleApplyAdvancedFilters}
                style={{ marginTop: 4 }}
                allowClear
              />
            </Col>
            <Col xs={24} sm={12} md={6}>
              <Text style={{ fontSize: 12, color: '#c084fc', fontWeight: 600 }}>📻 Karşı Çağrı Adı (Hedef İstasyon):</Text>
              <Input
                placeholder="Örn: NATO-TASK-09, HERMES-4..."
                value={karsiCagriAdiFilter}
                onChange={(e) => setKarsiCagriAdiFilter(e.target.value)}
                onPressEnter={handleApplyAdvancedFilters}
                style={{ marginTop: 4 }}
                allowClear
              />
            </Col>
            <Col xs={24} sm={12} md={6}>
              <Text style={{ fontSize: 12, color: '#94a3b8' }}>Bölge / Konum Filtresi:</Text>
              <Input
                placeholder="Örn: Ege, Akdeniz, Şırnak..."
                value={bolgeFilter}
                onChange={(e) => setBolgeFilter(e.target.value)}
                onPressEnter={handleApplyAdvancedFilters}
                style={{ marginTop: 4 }}
                allowClear
              />
            </Col>
            <Col xs={24} sm={12} md={6}>
              <Text style={{ fontSize: 12, color: '#94a3b8' }}>Muhabere Protokolü:</Text>
              <Select
                placeholder="Protokol seçiniz"
                value={protokolFilter}
                onChange={setProtokolFilter}
                allowClear
                style={{ width: '100%', marginTop: 4 }}
                options={[
                  { label: 'DMR Mobil', value: 'DMR_MOBIL' },
                  { label: 'DMR Baz İstasyonu', value: 'DMR_BAZ' },
                  { label: 'DMR Baz / DMR Mobil', value: 'DMR_BAZ_DMR_MOBIL' },
                  { label: 'D-Star Dijital', value: 'D_STAR' },
                  { label: 'STANAG 5065 MSK', value: 'STANAG_5065_MSK' },
                  { label: 'STANAG 5065 FSK', value: 'STANAG_5065_FSK' },
                  { label: 'STANAG 4529', value: 'STANAG_4529' },
                  { label: 'MIL-STD-188-141A (ALE)', value: 'MIL_188_141A' },
                  { label: 'MIL-STD-188-141B (ALE)', value: 'MIL_188_141B' },
                  { label: 'Link-11 (CLEW)', value: 'LINK_11_CLEW' },
                  { label: 'Link-11 (SLEW)', value: 'LINK_11_SLEW' },
                  { label: 'PACTOR-II', value: 'PACTOR_2' },
                  { label: 'PACTOR-III', value: 'PACTOR_3' },
                  { label: 'Clover-2000', value: 'CLOVER_2000' },
                  { label: 'Clover-2500', value: 'CLOVER_2500' },
                  { label: 'Yaesu System Fusion', value: 'FUSION' },
                  { label: 'Belirsiz', value: 'BELIRSIZ' },
                  { label: 'Tanımsız / Standart Telsiz', value: 'TANIMSIZ' }
                ]}
              />
            </Col>
            <Col xs={24} sm={12} md={6}>
              <Text style={{ fontSize: 12, color: '#94a3b8' }}>Muhabere Modülasyonu:</Text>
              <Select
                placeholder="Modülasyon seçiniz"
                value={modulasyonFilter}
                onChange={setModulasyonFilter}
                allowClear
                style={{ width: '100%', marginTop: 4 }}
                options={[
                  { label: 'FM (Frekans Modülasyonu)', value: 'FM' },
                  { label: 'AM (Genlik Modülasyonu)', value: 'AM' },
                  { label: 'USB (Üst Yan Bant)', value: 'USB' },
                  { label: 'LSB (Alt Yan Bant)', value: 'LSB' },
                  { label: 'FSK-16 (16 Seviyeli FSK)', value: 'FSK16' },
                  { label: 'FSK-8 (8 Seviyeli FSK)', value: 'FSK8' },
                  { label: 'CPSK-2 (Sürekli Faz)', value: 'CPSK2' },
                  { label: 'QAM-64 (64-QAM)', value: 'QAM64' },
                  { label: 'QAM-28 (28-QAM)', value: 'QAM28' },
                  { label: 'QAM-128 (128-QAM)', value: 'QAM128' },
                  { label: 'DPSK-4 (4-Faz DPSK)', value: 'DPSK4' },
                  { label: 'DPSK (Diferansiyel Faz)', value: 'DPSK' },
                  { label: 'ASK-8 (8-ASK)', value: 'ASK8' },
                  { label: 'ASK-4 (4-ASK)', value: 'ASK4' },
                  { label: 'Belirsiz', value: 'BELIRSIZ' }
                ]}
              />
            </Col>
            <Col xs={24} sm={12} md={6}>
              <Text style={{ fontSize: 12, color: '#f59e0b', fontWeight: 600 }}>⏱️ Zaman Aralığı (Trafik Penceresi):</Text>
              <Select
                placeholder="Zaman kısıtı seçiniz"
                value={zamanAraligiFilter}
                onChange={(val) => {
                  setZamanAraligiFilter(val);
                }}
                allowClear
                style={{ width: '100%', marginTop: 4 }}
                options={[
                  { label: 'Tüm Zamanlar (Filtresiz)', value: null },
                  { label: '⏱️ Son 1 Saat', value: 'SON_1_SAAT' },
                  { label: '⏱️ Son 6 Saat', value: 'SON_6_SAAT' },
                  { label: '⏱️ Son 12 Saat', value: 'SON_12_SAAT' },
                  { label: '⏱️ Son 24 Saat', value: 'SON_24_SAAT' },
                  { label: '⏱️ Son 48 Saat', value: 'SON_48_SAAT' },
                  { label: '⏱️ Son 7 Gün (1 Hafta)', value: 'SON_1_HAFTA' }
                ]}
              />
            </Col>
            <Col xs={24} sm={12} md={6}>
              <Text style={{ fontSize: 12, color: '#94a3b8' }}>Frekans Aralığı (MHz):</Text>
              <Space style={{ width: '100%', marginTop: 4 }}>
                <InputNumber
                  placeholder="Min MHz"
                  value={minFrekans}
                  onChange={setMinFrekans}
                  onPressEnter={handleApplyAdvancedFilters}
                  style={{ width: '100%' }}
                />
                <Text type="secondary">-</Text>
                <InputNumber
                  placeholder="Max MHz"
                  value={maxFrekans}
                  onChange={setMaxFrekans}
                  onPressEnter={handleApplyAdvancedFilters}
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
                  onPressEnter={handleApplyAdvancedFilters}
                  style={{ width: '100%' }}
                />
                <Text type="secondary">-</Text>
                <InputNumber
                  placeholder="Max dBm"
                  value={maxGenlik}
                  onChange={setMaxGenlik}
                  onPressEnter={handleApplyAdvancedFilters}
                  style={{ width: '100%' }}
                />
              </Space>
            </Col>
            <Col xs={24} sm={24} md={12} style={{ textAlign: 'right', marginTop: 18 }}>
              <Space>
                <Button onClick={handleResetFilters} size="middle">
                  Filtreleri Temizle
                </Button>
                <Button type="primary" onClick={handleApplyAdvancedFilters} size="middle" icon={<FilterOutlined />}>
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
        scroll={{ x: 1850 }}
        pagination={{
          current: page,
          pageSize: pageSize,
          total: total,
          showSizeChanger: true,
          pageSizeOptions: ['10', '15', '25', '50', '100'],
          showTotal: (t) => (
            <span style={{ color: '#94a3b8', fontSize: 12 }}>
              <DatabaseOutlined style={{ marginRight: 4, color: '#c084fc' }} />
              PostgreSQL Sayfalama: Toplam {t} muhabere kaydı
            </span>
          )
        }}
        onChange={handleTableChange}
        onRow={(record) => ({
          onClick: () => onSelectComint(record.id),
          style: { cursor: 'pointer' }
        })}
      />
    </Card>
  );
}

