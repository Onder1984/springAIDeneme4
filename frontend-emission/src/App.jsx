import React, { useState, useEffect } from 'react';
import { Layout, Tabs, Space, Tag, Badge, Button, Tooltip, Card } from 'antd';
import {
  RadarChartOutlined,
  SecurityScanOutlined,
  DatabaseOutlined,
  ReloadOutlined,
  RobotOutlined,
  ApiOutlined
} from '@ant-design/icons';
import RadarTable from './components/RadarTable';
import ComintTable from './components/ComintTable';
import DetailDrawer from './components/DetailDrawer';

const { Header, Content } = Layout;

export default function App() {
  const [activeTab, setActiveTab] = useState('radar');
  const [selectedTarget, setSelectedTarget] = useState(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [drawerType, setDrawerType] = useState('RADAR');
  const [drawerData, setDrawerData] = useState(null);
  const [drawerLoading, setDrawerLoading] = useState(false);
  const [serviceOnline, setServiceOnline] = useState(true);

  // Servis canlılık kontrolü (Port 8081)
  const checkHealth = async () => {
    try {
      const res = await fetch('/api/v1/tactical/emissions?page=1&size=1');
      setServiceOnline(res.ok);
    } catch {
      setServiceOnline(false);
    }
  };

  useEffect(() => {
    checkHealth();
    const interval = setInterval(checkHealth, 15000);
    return () => clearInterval(interval);
  }, []);

  const handleOpenDetail = async (id, explicitType) => {
    const isRadar = explicitType ? explicitType === 'RADAR' : id.startsWith('EMI');
    const type = isRadar ? 'RADAR' : 'COMINT';
    setDrawerType(type);
    setDrawerOpen(true);
    setDrawerLoading(true);

    try {
      const url = isRadar
        ? `/api/v1/tactical/emissions/${id}`
        : `/api/v1/tactical/comint/${id}`;
      const res = await fetch(url);
      const data = await res.json();
      setDrawerData(data);
      setSelectedTarget({ id, type, data });
    } catch (err) {
      console.error('Detay yükleme hatası:', err);
    } finally {
      setDrawerLoading(false);
    }
  };

  return (
    <Layout style={{ minHeight: '100vh', backgroundColor: '#030712' }}>
      {/* Üst Askeri Başlık Çubuğu */}
      <Header
        style={{
          backgroundColor: '#0b1120',
          borderBottom: '1px solid #1e293b',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center',
          padding: '0 20px',
          height: 60,
          position: 'sticky',
          top: 0,
          zIndex: 100
        }}
      >
        <Space size={12}>
          <div
            style={{
              width: 34,
              height: 34,
              borderRadius: 6,
              background: 'linear-gradient(135deg, #0284c7 0%, #0369a1 100%)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              boxShadow: '0 0 12px rgba(2, 132, 199, 0.4)'
            }}
          >
            <RadarChartOutlined style={{ color: '#fff', fontSize: 18 }} />
          </div>

          <div>
            <div style={{ fontWeight: 800, fontSize: 16, color: '#f8fafc', letterSpacing: 0.5 }}>
              TAKTIK EH TELEMETRİ KONSOLU
            </div>
            <div style={{ fontSize: 11, color: '#64748b', marginTop: -2 }}>
              ELINT (Radar) & COMINT (Muhabere) Telemetri Veri Yönetim Sistemi
            </div>
          </div>

          <Tag color="blue" style={{ marginLeft: 8, fontSize: 11 }}>
            📡 Port: 3001
          </Tag>
        </Space>

        <Space size={14}>
          <Tooltip title={serviceOnline ? 'ew-emission-service (8081) Aktif' : 'Veri Servisine Ulaşılamıyor'}>
            <Tag
              color={serviceOnline ? '#064e3b' : '#7f1d1d'}
              style={{
                color: serviceOnline ? '#34d399' : '#f87171',
                borderColor: serviceOnline ? '#059669' : '#dc2626',
                padding: '3px 10px',
                fontSize: 12
              }}
            >
              <Badge status={serviceOnline ? 'success' : 'error'} style={{ marginRight: 6 }} />
              Veri Servisi: {serviceOnline ? 'ONLINE (8081)' : 'OFFLINE'}
            </Tag>
          </Tooltip>

          <Tooltip title="Bağımsız AI Taktik Copilot Uygulamasını Ayrı Sekmede Aç">
            <Button
              type="primary"
              size="small"
              icon={<RobotOutlined />}
              href="http://localhost:3002"
              target="_blank"
              style={{
                backgroundColor: '#7c3aed',
                borderColor: '#6d28d9',
                boxShadow: '0 0 8px rgba(124, 58, 237, 0.4)'
              }}
            >
              AI Copilot'a Git (Port 3002)
            </Button>
          </Tooltip>
        </Space>
      </Header>

      {/* Ana İçerik */}
      <Content style={{ padding: '16px 20px', maxWidth: 1750, margin: '0 auto', width: '100%' }}>
        <Tabs
          activeKey={activeTab}
          onChange={setActiveTab}
          type="card"
          items={[
            {
              key: 'radar',
              label: (
                <Space>
                  <RadarChartOutlined style={{ color: '#38bdf8' }} />
                  <span>Radar Emisyonları (ELINT)</span>
                </Space>
              ),
              children: (
                <RadarTable onRowSelect={(row) => handleOpenDetail(row.id, 'RADAR')} />
              )
            },
            {
              key: 'comint',
              label: (
                <Space>
                  <SecurityScanOutlined style={{ color: '#c084fc' }} />
                  <span>Muhabere Yayınları (COMINT)</span>
                </Space>
              ),
              children: (
                <ComintTable onRowSelect={(row) => handleOpenDetail(row.id, 'COMINT')} />
              )
            }
          ]}
        />

        {/* Detay Çekmecesi */}
        <DetailDrawer
          open={drawerOpen}
          onClose={() => setDrawerOpen(false)}
          type={drawerType}
          data={drawerData}
          loading={drawerLoading}
        />
      </Content>
    </Layout>
  );
}
