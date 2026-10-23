import React from 'react';
import { Drawer, Tag, Descriptions, Tabs, Table, Badge, Typography, Space, Divider, Alert } from 'antd';
import {
  RadarChartOutlined,
  AlertOutlined,
  ThunderboltOutlined,
  SecurityScanOutlined,
  GlobalOutlined,
  ClockCircleOutlined,
  UserOutlined,
  CheckCircleOutlined,
  CompassOutlined
} from '@ant-design/icons';
import dayjs from 'dayjs';

const { Text, Title } = Typography;

export default function DetailDrawer({ visible, onClose, type, data, loading, onSelectEmission }) {
  if (!visible) return null;

  const isRadar = type === 'RADAR' || (data && data.radarAdi !== undefined);

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

  const getEtColor = (et) => {
    switch (et) {
      case 'UYGULANIYOR': return 'cyan';
      case 'SUSTURDU': return 'purple';
      default: return 'default';
    }
  };

  return (
    <Drawer
      title={
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', width: '100%' }}>
          <Space>
            {isRadar ? <RadarChartOutlined style={{ color: '#38bdf8', fontSize: 20 }} /> : <AlertOutlined style={{ color: '#c084fc', fontSize: 20 }} />}
            <Text strong style={{ fontSize: 16, color: '#f1f5f9' }}>
              {isRadar ? 'RADAR (ELINT) 37 PARAMETRELİ HEDEF DETAYI' : 'MUHABERE (COMINT) 30 PARAMETRELİ YAYIN DETAYI'}
            </Text>
            <Tag color={isRadar ? 'processing' : 'purple'}>{data?.id || 'Yükleniyor...'}</Tag>
          </Space>
          {data?.teshisKimlik && (
            <Tag color={getTeshisColor(data.teshisKimlik)} style={{ fontWeight: 600, fontSize: 13, padding: '2px 10px' }}>
              {data.teshisKimlik}
            </Tag>
          )}
        </div>
      }
      placement="right"
      width={780}
      onClose={onClose}
      open={visible}
      styles={{
        body: { backgroundColor: '#0b1120', color: '#e2e8f0', padding: 20 },
        header: { backgroundColor: '#0f172a', borderBottom: '1px solid #1e293b' }
      }}
    >
      {loading ? (
        <div style={{ textAlign: 'center', padding: '60px 0', color: '#94a3b8' }}>
          Taktik veritabanından teknik detaylar çekiliyor...
        </div>
      ) : !data ? (
        <Alert message="Kayıt bulunamadı veya parametreler yüklenemedi." type="warning" showIcon />
      ) : isRadar ? (
        <div>
          {/* SIGINT Kayıt Niteliği (LOB vs Kestirim) Bildirimi */}
          {data.kayitTipi === 'HAM_LOB' ? (
            data.kestirimId ? (
              <Alert
                message="HAM LOB (YÖN BİLDİRİMİ / STROBE) - KESTİRİME DAHİL EDİLDİ"
                description={
                  <div>
                    Bu kayıt tekil bir sensör kestirimidir (kerteriz: {data.yon != null ? `${data.yon.toFixed(1)}°` : 'Bilinmiyor'}).
                    Çapraz kestirime dahil edilerek{' '}
                    <Tag
                      color="cyan"
                      style={{ cursor: 'pointer', fontWeight: 700 }}
                      onClick={() => onSelectEmission && onSelectEmission(data.kestirimId, 'RADAR')}
                    >
                      {data.kestirimId}
                    </Tag>{' '}
                    hedef hata elipsi oluşturulmuştur.
                  </div>
                }
                type="warning"
                showIcon
                icon={<CompassOutlined />}
                style={{ marginBottom: 16, backgroundColor: 'rgba(245, 158, 11, 0.1)', borderColor: '#f59e0b' }}
              />
            ) : (
              <Alert
                message="KESTİRİM BEKLEYEN BAŞIBOŞ LOB (YÖN BİLDİRİMİ)"
                description={`Bu kayıt tek bir EH sensörü tarafından tespit edilmiş başıboş kerteriz (LOB) yayınıdır (kerteriz: ${data.yon != null ? `${data.yon.toFixed(1)}°` : 'Bilinmiyor'}). Henüz başka sensör LOB'ları ile kesiştirilip elipsli bir kestirim üretilmemiştir. Bu sebeple yayın konumu (enlem/boylam elipsi) boştur.`}
                type="warning"
                showIcon
                icon={<CompassOutlined />}
                style={{ marginBottom: 16, backgroundColor: 'rgba(245, 158, 11, 0.1)', borderColor: '#f59e0b' }}
              />
            )
          ) : (
            data.iliskiliLobIdListesi && data.iliskiliLobIdListesi.length > 0 ? (
              <Alert
                message="ÇAPRAZ KESTİRİM (HATA ELİPSİ) HEDEFİ"
                description={
                  <div>
                    Bu hedef kestirim elipsi, aşağıdaki{' '}
                    <strong>{data.iliskiliLobIdListesi.length} adet sensör LOB (kerteriz) yayınının</strong> kesiştirilmesiyle
                    üretilmiştir:
                    <div style={{ marginTop: 6, display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                      {data.iliskiliLobIdListesi.map((lobId) => (
                        <Tag
                          key={lobId}
                          color="orange"
                          style={{ cursor: 'pointer', fontWeight: 600 }}
                          onClick={() => onSelectEmission && onSelectEmission(lobId, 'RADAR')}
                        >
                          🧭 {lobId}
                        </Tag>
                      ))}
                    </div>
                  </div>
                }
                type="success"
                showIcon
                icon={<RadarChartOutlined />}
                style={{ marginBottom: 16, backgroundColor: 'rgba(16, 185, 129, 0.1)', borderColor: '#10b981' }}
              />
            ) : (
              <Alert
                message="KESTİRİM (HATA ELİPSİ) HEDEFİ"
                description="Hedef konumu ve hata elipsi (CEP) hesaplanmış nihai taktik radar emisyonudur."
                type="info"
                showIcon
                icon={<RadarChartOutlined />}
                style={{ marginBottom: 16, backgroundColor: 'rgba(56, 189, 248, 0.1)', borderColor: '#38bdf8' }}
              />
            )
          )}

          {/* Radar Taciz / ET Durumu Uyarısı */}
          {data.taciz && (
            <Alert
              message="KRİTİK HSS HUDUT TACİZİ MEVCUT"
              description="Bu hedef hudut hattında radar tacizi gerçekleştirmektedir. Angajman ve ET önceliği bulunmaktadır."
              type="error"
              showIcon
              style={{ marginBottom: 16, backgroundColor: 'rgba(239, 68, 68, 0.1)', borderColor: '#ef4444' }}
            />
          )}
          {data.etUygulamaDurumu && data.etUygulamaDurumu !== 'ET_YOK' && (
            <Alert
              message={`ELEKTRONİK TAARRUZ: ${data.etUygulamaDurumu}`}
              description={data.etUygulamaDurumu === 'UYGULANIYOR' ? 'Hedefe aktif karıştırma ve DRFM aldatması icra ediliyor.' : 'Hedef radar yayını susturulmuştur.'}
              type="info"
              showIcon
              style={{ marginBottom: 16, backgroundColor: 'rgba(56, 189, 248, 0.1)', borderColor: '#38bdf8' }}
            />
          )}

          <Tabs defaultActiveKey="1" items={[
            {
              key: '1',
              label: 'Genel & Teşhis Bilgileri',
              children: (
                <Descriptions bordered size="small" column={2} style={{ marginTop: 8 }}>
                  <Descriptions.Item label="Emisyon ID">{data.id}</Descriptions.Item>
                  <Descriptions.Item label="Kayıt Niteliği">
                    {data.kayitTipi === 'HAM_LOB' ? (
                      <Tag color="orange" style={{ fontWeight: 600 }}>HAM LOB (KERTERİZ)</Tag>
                    ) : (
                      <Tag color="cyan" style={{ fontWeight: 600 }}>KESTİRİM (ELİPS)</Tag>
                    )}
                  </Descriptions.Item>
                  <Descriptions.Item label="Radar Adı">{data.radarAdi || '-'}</Descriptions.Item>
                  <Descriptions.Item label="Spot No">
                    {data.spotNo ? (
                      <Tag color="geekblue" style={{ fontWeight: 700, fontFamily: 'monospace' }}>{data.spotNo}</Tag>
                    ) : '-'}
                  </Descriptions.Item>
                  <Descriptions.Item label="Teşhis Kimlik">
                    <Tag color={getTeshisColor(data.teshisKimlik)}>{data.teshisKimlik}</Tag>
                  </Descriptions.Item>
                  <Descriptions.Item label="İlişkili Kestirim / LOB">
                    {data.kayitTipi === 'HAM_LOB' ? (
                      data.kestirimId ? (
                        <Tag
                          color="blue"
                          style={{ cursor: 'pointer', fontWeight: 600 }}
                          onClick={() => onSelectEmission && onSelectEmission(data.kestirimId, 'RADAR')}
                        >
                          Bağlı Kestirim: {data.kestirimId}
                        </Tag>
                      ) : (
                        <Text style={{ color: '#fbbf24', fontStyle: 'italic' }}>Başıboş (Kestirim Bekliyor)</Text>
                      )
                    ) : (
                      data.iliskiliLobIdListesi && data.iliskiliLobIdListesi.length > 0
                        ? `${data.iliskiliLobIdListesi.length} Adet LOB Bağlı`
                        : 'Tekil Kestirim'
                    )}
                  </Descriptions.Item>
                  <Descriptions.Item label="Veri Kaynağı">{data.veriKaynagi || '-'}</Descriptions.Item>
                  <Descriptions.Item label="Radar Görevi">{data.radarGorevi || '-'}</Descriptions.Item>
                  <Descriptions.Item label="HSS / Batarya">{data.hss || '-'}</Descriptions.Item>
                  <Descriptions.Item label="Platform Ortamı">
                    <Tag color="blue">{data.platformOrtami || '-'}</Tag>
                  </Descriptions.Item>
                  <Descriptions.Item label="Platform Tipi">{data.platformTipi || '-'}</Descriptions.Item>
                  <Descriptions.Item label="Sensör Kerterizi (LOB)">
                    {data.yon != null ? `${data.yon.toFixed(1)}°` : '-'}
                  </Descriptions.Item>
                  <Descriptions.Item label="Sensör Konumu">
                    {data.ehUnsuruEnlem != null ? `${data.ehUnsuruEnlem.toFixed(4)}°, ${data.ehUnsuruBoylam?.toFixed(4)}°` : '-'}
                  </Descriptions.Item>
                  <Descriptions.Item label="Mevzi Bilgisi">{data.hedefMevziBilgisi || '-'}</Descriptions.Item>
                  <Descriptions.Item label="Yer / Bölge">{data.hedefYerBilgisi || '-'}</Descriptions.Item>
                  <Descriptions.Item label="Yayın Konumu (Elips)" span={2}>
                    {(data.yayinEnlem ?? data.enlem) != null ? (
                      <Text code>
                        {(data.yayinEnlem ?? data.enlem)?.toFixed(4)}° K, {(data.yayinBoylam ?? data.boylam)?.toFixed(4)}° D
                        {data.yayinSemiMajorMeters != null && (
                          <span style={{ marginLeft: 8, color: '#94a3b8' }}>
                            (CEP: Major {data.yayinSemiMajorMeters.toFixed(0)}m, Minor {data.yayinSemiMinorMeters?.toFixed(0)}m, {data.yayinOrientationDegrees?.toFixed(1)}°)
                          </span>
                        )}
                      </Text>
                    ) : (
                      <span style={{ color: '#f59e0b', fontStyle: 'italic' }}>
                        Yayın Konumu Yok (Ham LOB - Elips Kestirimi Bekliyor)
                      </span>
                    )}
                  </Descriptions.Item>
                  <Descriptions.Item label="İlk Tespit">
                    {data.ilkTespitZamani ? dayjs(data.ilkTespitZamani).format('YYYY-MM-DD HH:mm:ss') : '-'}
                  </Descriptions.Item>
                  <Descriptions.Item label="Son Tespit">
                    {data.sonTespitZamani ? dayjs(data.sonTespitZamani).format('YYYY-MM-DD HH:mm:ss') : '-'}
                  </Descriptions.Item>
                </Descriptions>
              )
            },
            {
              key: '2',
              label: 'RF, PRI & Dalga Şekli',
              children: (() => {
                const frekansTipleri = (data.frekansListesi && data.frekansListesi.length > 0)
                  ? [...new Set(data.frekansListesi.map(f => f.frekansTipi).filter(Boolean))]
                  : (data.modulasyon === 'MULTI_FREQUENCY'
                      ? ['ATLAMALI_HOPPING']
                      : (data.minFrekansMhz != null && data.maxFrekansMhz != null && Math.abs(data.maxFrekansMhz - data.minFrekansMhz) > 50
                          ? ['DEGISKEN_JITTER']
                          : ['SABIT']));

                const priTipleri = (data.priListesi && data.priListesi.length > 0)
                  ? [...new Set(data.priListesi.map(p => p.priTipi).filter(Boolean))]
                  : (data.minPriMicroSec != null && data.maxPriMicroSec != null && Math.abs(data.maxPriMicroSec - data.minPriMicroSec) > 30
                      ? ['KADEMELI']
                      : ['SABIT']);

                const pwTipleri = (data.pwListesi && data.pwListesi.length > 0)
                  ? [...new Set(data.pwListesi.map(p => p.pwTipi).filter(Boolean))]
                  : (data.modulasyon && data.modulasyon !== 'GECERSIZ' && data.modulasyon !== 'YOK_NO_MOP'
                      ? ['MODULASYONLU']
                      : ['SABIT']);

                const atpTipleri = (data.atpListesi && data.atpListesi.length > 0)
                  ? [...new Set(data.atpListesi.map(a => a.atpTipi).filter(Boolean))]
                  : (data.radarGorevi?.includes('Atış')
                      ? ['SEKTOR']
                      : (data.radarAdi?.includes('AESA') || data.radarAdi?.includes('EIRS')
                          ? ['ELEKTRONIK_TARAMA']
                          : ['DAIRESEL']));

                const effectiveFrekansListesi = (data.frekansListesi && data.frekansListesi.length > 0)
                  ? data.frekansListesi
                  : [{ minFrekansMhz: data.minFrekansMhz, maxFrekansMhz: data.maxFrekansMhz, frekansTipi: frekansTipleri[0] }];

                const effectivePriListesi = (data.priListesi && data.priListesi.length > 0)
                  ? data.priListesi
                  : [{ minPriMicroSec: data.minPriMicroSec, maxPriMicroSec: data.maxPriMicroSec, priTipi: priTipleri[0] }];

                const effectivePwListesi = (data.pwListesi && data.pwListesi.length > 0)
                  ? data.pwListesi
                  : [{ minPwMicroSec: data.minPwMicroSec, maxPwMicroSec: data.maxPwMicroSec, pwTipi: pwTipleri[0] }];

                const effectiveAtpListesi = (data.atpListesi && data.atpListesi.length > 0)
                  ? data.atpListesi
                  : [{ atpOrtMicroSec: data.minAtpMicroSec, atpTipi: atpTipleri[0] }];

                return (
                  <div>
                    <Descriptions bordered size="small" column={2} style={{ marginBottom: 16 }}>
                      <Descriptions.Item label="Frekans Aralığı">
                        {data.minFrekansMhz?.toFixed(1)} - {data.maxFrekansMhz?.toFixed(1)} MHz
                      </Descriptions.Item>
                      <Descriptions.Item label="Frekans Tipi">
                        <Space wrap size={[0, 4]}>
                          {frekansTipleri.map(tip => (
                            <Tag color="blue" key={tip}>{tip}</Tag>
                          ))}
                        </Space>
                      </Descriptions.Item>
                      <Descriptions.Item label="PRI Aralığı">
                        {data.minPriMicroSec?.toFixed(0)} - {data.maxPriMicroSec?.toFixed(0)} µs
                      </Descriptions.Item>
                      <Descriptions.Item label="PRI Tipi">
                        <Space wrap size={[0, 4]}>
                          {priTipleri.map(tip => (
                            <Tag color="cyan" key={tip}>{tip}</Tag>
                          ))}
                        </Space>
                      </Descriptions.Item>
                      <Descriptions.Item label="PW (Darbe Genişliği)">
                        {data.minPwMicroSec?.toFixed(2)} - {data.maxPwMicroSec?.toFixed(2)} µs
                      </Descriptions.Item>
                      <Descriptions.Item label="PW Tipi">
                        <Space wrap size={[0, 4]}>
                          {pwTipleri.map(tip => (
                            <Tag color="purple" key={tip}>{tip}</Tag>
                          ))}
                        </Space>
                      </Descriptions.Item>
                      <Descriptions.Item label="ATP (Anten Tarama)">
                        {data.minAtpMicroSec && data.maxAtpMicroSec
                          ? `${(data.minAtpMicroSec / 1000).toFixed(0)} - ${(data.maxAtpMicroSec / 1000).toFixed(0)} ms`
                          : '-'}
                      </Descriptions.Item>
                      <Descriptions.Item label="ATP Tipi">
                        <Space wrap size={[0, 4]}>
                          {atpTipleri.map(tip => (
                            <Tag color="geekblue" key={tip}>{tip}</Tag>
                          ))}
                        </Space>
                      </Descriptions.Item>
                      <Descriptions.Item label="Sinyal Gücü (Genlik)">
                        {data.genlikDbm ? `${data.genlikDbm?.toFixed(1)} dBm` : '-'}
                      </Descriptions.Item>
                      <Descriptions.Item label="Polarizasyon">{data.polarizasyon || '-'}</Descriptions.Item>
                    </Descriptions>

                    <Title level={5} style={{ color: '#94a3b8', fontSize: 13, marginBottom: 8 }}>ÇOKLU FREKANS LİSTESİ</Title>
                    <Table
                      size="small"
                      pagination={false}
                      dataSource={effectiveFrekansListesi.map((f, i) => ({ ...f, key: i, siraNo: i + 1 }))}
                      columns={[
                        { title: '#', dataIndex: 'siraNo', key: 'siraNo', width: 50 },
                        {
                          title: 'Frekans Aralığı',
                          key: 'frekansAraligi',
                          render: (_, r) => `${r.minFrekansMhz?.toFixed(1)} - ${r.maxFrekansMhz?.toFixed(1)} MHz`
                        },
                        {
                          title: 'Frekans Tipi',
                          dataIndex: 'frekansTipi',
                          key: 'frekansTipi',
                          render: (tip) => <Tag color="blue">{tip || 'SABIT'}</Tag>
                        }
                      ]}
                    />

                    <Title level={5} style={{ color: '#94a3b8', fontSize: 13, margin: '16px 0 8px' }}>PRI LİSTESİ</Title>
                    <Table
                      size="small"
                      pagination={false}
                      dataSource={effectivePriListesi.map((p, i) => ({ ...p, key: i, siraNo: i + 1 }))}
                      columns={[
                        { title: '#', dataIndex: 'siraNo', key: 'siraNo', width: 50 },
                        {
                          title: 'PRI Aralığı',
                          key: 'priAraligi',
                          render: (_, r) => `${r.minPriMicroSec?.toFixed(1)} - ${r.maxPriMicroSec?.toFixed(1)} µs`
                        },
                        {
                          title: 'PRI Tipi / Mod',
                          dataIndex: 'priTipi',
                          key: 'priTipi',
                          render: (tip) => <Tag color="cyan">{tip || 'SABIT'}</Tag>
                        }
                      ]}
                    />

                    <Title level={5} style={{ color: '#94a3b8', fontSize: 13, margin: '16px 0 8px' }}>PW (DARBE GENİŞLİĞİ) LİSTESİ</Title>
                    <Table
                      size="small"
                      pagination={false}
                      dataSource={effectivePwListesi.map((p, i) => ({ ...p, key: i, siraNo: i + 1 }))}
                      columns={[
                        { title: '#', dataIndex: 'siraNo', key: 'siraNo', width: 50 },
                        {
                          title: 'PW Aralığı',
                          key: 'pwAraligi',
                          render: (_, r) => `${r.minPwMicroSec?.toFixed(2)} - ${r.maxPwMicroSec?.toFixed(2)} µs`
                        },
                        {
                          title: 'PW Tipi / Modülasyon',
                          dataIndex: 'pwTipi',
                          key: 'pwTipi',
                          render: (tip) => <Tag color="purple">{tip || 'SABIT'}</Tag>
                        }
                      ]}
                    />

                    <Title level={5} style={{ color: '#94a3b8', fontSize: 13, margin: '16px 0 8px' }}>ATP (ANTEN TARAMA PERİYODU) LİSTESİ</Title>
                    <Table
                      size="small"
                      pagination={false}
                      dataSource={effectiveAtpListesi.map((a, i) => ({ ...a, key: i, siraNo: i + 1 }))}
                      columns={[
                        { title: '#', dataIndex: 'siraNo', key: 'siraNo', width: 50 },
                        {
                          title: 'Ortalama Tarama Süresi',
                          key: 'atpOrt',
                          render: (_, r) => `${(r.atpOrtMicroSec / 1000).toFixed(1)} ms (${(r.atpOrtMicroSec / 1_000_000).toFixed(2)} sn)`
                        },
                        {
                          title: 'Tarama Tipi',
                          dataIndex: 'atpTipi',
                          key: 'atpTipi',
                          render: (tip) => <Tag color="geekblue">{tip || 'DAIRESEL'}</Tag>
                        }
                      ]}
                    />
                  </div>
                );
              })()
            },
            {
              key: '3',
              label: 'ET & Operatör Kayıtları',
              children: (
                <Descriptions bordered size="small" column={1}>
                  <Descriptions.Item label="Elektronik Taarruz Durumu">
                    <Tag color={getEtColor(data.etUygulamaDurumu)}>{data.etUygulamaDurumu || 'ET_YOK'}</Tag>
                  </Descriptions.Item>
                  <Descriptions.Item label="Hudut Tacizi">
                    {data.taciz ? <Tag color="error">TACİZ VAR</Tag> : <Tag color="default">YOK</Tag>}
                  </Descriptions.Item>
                  <Descriptions.Item label="Operatör Notu">
                    {data.operatorNotu || 'Kayıtlı not bulunmamaktadır.'}
                  </Descriptions.Item>
                  <Descriptions.Item label="Onaylayan Operatör">
                    <Space><UserOutlined /> {data.onaylayanKullanici || 'SİSTEM'}</Space>
                  </Descriptions.Item>
                  <Descriptions.Item label="Onay Tarihi">
                    {data.onayTarihi ? dayjs(data.onayTarihi).format('YYYY-MM-DD HH:mm:ss') : '-'}
                  </Descriptions.Item>
                </Descriptions>
              )
            }
          ]} />
        </div>
      ) : (
        /* COMINT Detayları */
        <div>
          {/* SIGINT Kayıt Niteliği (LOB vs Kestirim) Bildirimi */}
          {data.kayitTipi === 'HAM_LOB' ? (
            data.kestirimId ? (
              <Alert
                message="HAM LOB (YÖN BİLDİRİMİ / KERTERİZ) - KESTİRİME DAHİL EDİLDİ"
                description={
                  <div>
                    Bu yayın tekil bir sensör kestirimidir (kerteriz: {data.yon != null ? `${data.yon.toFixed(1)}°` : 'Bilinmiyor'}).
                    Çapraz kestirime dahil edilerek{' '}
                    <Tag
                      color="purple"
                      style={{ cursor: 'pointer', fontWeight: 700 }}
                      onClick={() => onSelectEmission && onSelectEmission(data.kestirimId, 'COMINT')}
                    >
                      {data.kestirimId}
                    </Tag>{' '}
                    hedef kestirim elipsine dahil edilmiştir.
                  </div>
                }
                type="warning"
                showIcon
                icon={<CompassOutlined />}
                style={{ marginBottom: 16, backgroundColor: 'rgba(245, 158, 11, 0.1)', borderColor: '#f59e0b' }}
              />
            ) : (
              <Alert
                message="KESTİRİM BEKLEYEN BAŞIBOŞ LOB (YÖN BİLDİRİMİ)"
                description={`Bu yayın tek bir CESM sensörü tarafından tespit edilmiş başıboş kerteriz (LOB) kaydıdır (kerteriz: ${data.yon != null ? `${data.yon.toFixed(1)}°` : 'Bilinmiyor'}). Henüz diğer sensör LOB'ları ile kesiştirilip elipsli bir kestirim üretilmemiştir. Yayın konumu (enlem/boylam elipsi) boştur.`}
                type="warning"
                showIcon
                icon={<CompassOutlined />}
                style={{ marginBottom: 16, backgroundColor: 'rgba(245, 158, 11, 0.1)', borderColor: '#f59e0b' }}
              />
            )
          ) : (
            data.iliskiliLobIdListesi && data.iliskiliLobIdListesi.length > 0 ? (
              <Alert
                message="ÇAPRAZ KESTİRİM (HATA ELİPSİ) YAYINI"
                description={
                  <div>
                    Bu hedef kestirim elipsi, aşağıdaki{' '}
                    <strong>{data.iliskiliLobIdListesi.length} adet sensör LOB (kerteriz) yayınının</strong> kesiştirilmesiyle
                    üretilmiştir:
                    <div style={{ marginTop: 6, display: 'flex', flexWrap: 'wrap', gap: 6 }}>
                      {data.iliskiliLobIdListesi.map((lobId) => (
                        <Tag
                          key={lobId}
                          color="orange"
                          style={{ cursor: 'pointer', fontWeight: 600 }}
                          onClick={() => onSelectEmission && onSelectEmission(lobId, 'COMINT')}
                        >
                          🧭 {lobId}
                        </Tag>
                      ))}
                    </div>
                  </div>
                }
                type="success"
                showIcon
                icon={<AlertOutlined />}
                style={{ marginBottom: 16, backgroundColor: 'rgba(16, 185, 129, 0.1)', borderColor: '#10b981' }}
              />
            ) : (
              <Alert
                message="KESTİRİM (HATA ELİPSİ) YAYINI"
                description="Hedef konumu ve hata elipsi (CEP) hesaplanmış nihai taktik muhabere (COMINT) yayınıdır."
                type="info"
                showIcon
                icon={<AlertOutlined />}
                style={{ marginBottom: 16, backgroundColor: 'rgba(192, 132, 252, 0.1)', borderColor: '#c084fc' }}
              />
            )
          )}

          <Tabs defaultActiveKey="1" items={[
            {
              key: '1',
              label: 'Haberleşme & Çağrı Ağı',
              children: (
                <Descriptions bordered size="small" column={2} style={{ marginTop: 8 }}>
                  <Descriptions.Item label="Kayıt ID">{data.id}</Descriptions.Item>
                  <Descriptions.Item label="Kayıt Niteliği">
                    {data.kayitTipi === 'HAM_LOB' ? (
                      <Tag color="orange" style={{ fontWeight: 600 }}>HAM LOB (KERTERİZ)</Tag>
                    ) : (
                      <Tag color="cyan" style={{ fontWeight: 600 }}>KESTİRİM (ELİPS)</Tag>
                    )}
                  </Descriptions.Item>
                  <Descriptions.Item label="Teşhis">
                    <Tag color={getTeshisColor(data.teshisKimlik)}>{data.teshisKimlik}</Tag>
                  </Descriptions.Item>
                  <Descriptions.Item label="İlişkili Kestirim / LOB">
                    {data.kayitTipi === 'HAM_LOB' ? (
                      data.kestirimId ? (
                        <Tag
                          color="purple"
                          style={{ cursor: 'pointer', fontWeight: 600 }}
                          onClick={() => onSelectEmission && onSelectEmission(data.kestirimId, 'COMINT')}
                        >
                          Bağlı Kestirim: {data.kestirimId}
                        </Tag>
                      ) : (
                        <Text style={{ color: '#fbbf24', fontStyle: 'italic' }}>Başıboş (Kestirim Bekliyor)</Text>
                      )
                    ) : (
                      data.iliskiliLobIdListesi && data.iliskiliLobIdListesi.length > 0
                        ? `${data.iliskiliLobIdListesi.length} Adet LOB Bağlı`
                        : 'Tekil Kestirim'
                    )}
                  </Descriptions.Item>
                  <Descriptions.Item label="Çağrı Adı (Callsign)">
                    <Tag color="cyan" style={{ fontSize: 13, fontWeight: 700 }}>{data.cagriAdi || '-'}</Tag>
                  </Descriptions.Item>
                  <Descriptions.Item label="Karşı Çağrı Adı">
                    <Tag color="geekblue" style={{ fontSize: 13, fontWeight: 700 }}>{data.karsiCagriAdi || '-'}</Tag>
                  </Descriptions.Item>
                  <Descriptions.Item label="Lisan">
                    <Tag color="gold">{data.lisan || '-'}</Tag>
                  </Descriptions.Item>
                  <Descriptions.Item label="Haberleşme Şekli">
                    <Tag color="purple">{data.haberlesmeSekli || '-'}</Tag>
                  </Descriptions.Item>
                  <Descriptions.Item label="Telsiz Tipi">
                    <Tag color="blue">{data.tip || '-'}</Tag>
                  </Descriptions.Item>
                  <Descriptions.Item label="Protokol">{data.protokol || '-'}</Descriptions.Item>
                  <Descriptions.Item label="Modülasyon">{data.modulasyon || '-'}</Descriptions.Item>
                  <Descriptions.Item label="Kripto Durumu">
                    {data.kriptoluMu ? <Tag color="error">KRİPTOLU</Tag> : <Tag color="default">AÇIK SES</Tag>}
                  </Descriptions.Item>
                  <Descriptions.Item label="Sensör Kerterizi (LOB)">
                    {data.yon != null ? `${data.yon.toFixed(1)}°` : '-'}
                  </Descriptions.Item>
                  <Descriptions.Item label="Sensör Konumu">
                    {data.ehUnsuruEnlem != null ? `${data.ehUnsuruEnlem.toFixed(4)}°, ${data.ehUnsuruBoylam?.toFixed(4)}°` : '-'}
                  </Descriptions.Item>
                  <Descriptions.Item label="Bölge / Konum">{data.hedefYerBilgisi || '-'}</Descriptions.Item>
                  <Descriptions.Item label="Mevzi Bilgisi">{data.hedefMevziBilgisi || '-'}</Descriptions.Item>
                  <Descriptions.Item label="Yayın Konumu (Elips)" span={2}>
                    {(data.yayinEnlem ?? data.enlem) != null ? (
                      <Text code>
                        {(data.yayinEnlem ?? data.enlem)?.toFixed(4)}° K, {(data.yayinBoylam ?? data.boylam)?.toFixed(4)}° D
                        {data.yayinSemiMajorMeters != null && (
                          <span style={{ marginLeft: 8, color: '#94a3b8' }}>
                            (CEP: Major {data.yayinSemiMajorMeters.toFixed(0)}m, Minor {data.yayinSemiMinorMeters?.toFixed(0)}m, {data.yayinOrientationDegrees?.toFixed(1)}°)
                          </span>
                        )}
                      </Text>
                    ) : (
                      <span style={{ color: '#f59e0b', fontStyle: 'italic' }}>
                        Yayın Konumu Yok (Ham LOB - Elips Kestirimi Bekliyor)
                      </span>
                    )}
                  </Descriptions.Item>
                  <Descriptions.Item label="İlk Tespit">
                    {data.ilkTespitZamani ? dayjs(data.ilkTespitZamani).format('YYYY-MM-DD HH:mm:ss') : '-'}
                  </Descriptions.Item>
                  <Descriptions.Item label="Son Tespit">
                    {data.sonTespitZamani ? dayjs(data.sonTespitZamani).format('YYYY-MM-DD HH:mm:ss') : '-'}
                  </Descriptions.Item>
                </Descriptions>
              )
            },
            {
              key: '2',
              label: 'Frekans & RF Ölçümleri',
              children: (
                <div>
                  <Descriptions bordered size="small" column={2} style={{ marginBottom: 16 }}>
                    <Descriptions.Item label="Frekans Aralığı">
                      {data.minFrekansMhz} - {data.maxFrekansMhz} MHz
                    </Descriptions.Item>
                    <Descriptions.Item label="Bant">{data.tip || '-'}</Descriptions.Item>
                    <Descriptions.Item label="Sinyal Genliği">
                      {data.genlikDbm ? `${data.genlikDbm} dBm` : '-'}
                    </Descriptions.Item>
                    <Descriptions.Item label="Yayın Süresi">
                      {data.yayinSuresiSn ? `${data.yayinSuresiSn} saniye` : '-'}
                    </Descriptions.Item>
                    <Descriptions.Item label="Kanal Aralığı">
                      {data.kanalAraligiKhz ? `${data.kanalAraligiKhz} kHz` : '-'}
                    </Descriptions.Item>
                    <Descriptions.Item label="Azimut Açısı">
                      {data.azimutAcisi ? `${data.azimutAcisi}°` : '-'}
                    </Descriptions.Item>
                  </Descriptions>

                  <Title level={5} style={{ color: '#94a3b8', fontSize: 13, marginBottom: 8 }}>YAYIN FREKANS KANALLARI</Title>
                  <Table
                    size="small"
                    pagination={false}
                    dataSource={(data.frekansListesi || []).map((f, i) => ({ ...f, key: i }))}
                    columns={[
                      { title: '#', dataIndex: 'siraNo', key: 'siraNo', width: 60 },
                      { title: 'Frekans (MHz)', dataIndex: 'frekansDegeriMhz', key: 'frekansDegeriMhz' },
                      { title: 'Kanal No', dataIndex: 'kanalNo', key: 'kanalNo' },
                      { title: 'Açıklama', dataIndex: 'aciklama', key: 'aciklama' }
                    ]}
                  />
                </div>
              )
            },
            {
              key: '3',
              label: 'İstihbarat & Operatör Notu',
              children: (
                <Descriptions bordered size="small" column={1}>
                  <Descriptions.Item label="İstihbarat / Operatör Notu">
                    {data.operatorNotu || 'Kayıtlı not bulunmamaktadır.'}
                  </Descriptions.Item>
                  <Descriptions.Item label="Onaylayan Analist">
                    <Space><UserOutlined /> {data.onaylayanKullanici || 'SİSTEM'}</Space>
                  </Descriptions.Item>
                  <Descriptions.Item label="Onay Tarihi">
                    {data.onayTarihi ? dayjs(data.onayTarihi).format('YYYY-MM-DD HH:mm:ss') : '-'}
                  </Descriptions.Item>
                </Descriptions>
              )
            }
          ]} />
        </div>
      )}
    </Drawer>
  );
}
