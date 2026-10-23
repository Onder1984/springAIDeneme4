import React, { useState, useEffect } from 'react';
import { Layout, Space, Tag, Badge, Button, Tooltip, Card } from 'antd';
import {
  RobotOutlined,
  ThunderboltOutlined,
  RadarChartOutlined,
  SafetyCertificateOutlined,
  ReloadOutlined
} from '@ant-design/icons';
import CopilotChat from './components/CopilotChat';
import DetailDrawer from './components/DetailDrawer';

const { Header, Content } = Layout;

export default function App() {
  const [selectedTarget, setSelectedTarget] = useState(null);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [drawerType, setDrawerType] = useState('RADAR');
  const [drawerData, setDrawerData] = useState(null);
  const [drawerLoading, setDrawerLoading] = useState(false);
  const [copilotOnline, setCopilotOnline] = useState(true);

  // AI Copilot Servis Canlılık Kontrolü (Port 8082)
  const checkHealth = async () => {
    try {
      const res = await fetch('/api/v1/health');
      setCopilotOnline(res.ok);
    } catch {
      setCopilotOnline(false);
    }
  };

  useEffect(() => {
    checkHealth();
    const interval = setInterval(checkHealth, 15000);
    return () => clearInterval(interval);
  }, []);

  const handleSelectTarget = async (id, type, preloadedData) => {
    const isRadar = type ? type === 'RADAR' : id.startsWith('EMI');
    const targetType = isRadar ? 'RADAR' : 'COMINT';
    setDrawerType(targetType);
    setDrawerOpen(true);

    if (preloadedData) {
      setDrawerData(preloadedData);
      return;
    }

    setDrawerLoading(true);
    try {
      const url = isRadar
        ? `/api/v1/tactical/emissions/${id}`
        : `/api/v1/tactical/comint/${id}`;
      const res = await fetch(url);
      const data = await res.json();
      setDrawerData(data);
      setSelectedTarget({ id, type: targetType, data });
    } catch (err) {
      console.error('Detay çekilemedi:', err);
    } finally {
      setDrawerLoading(false);
    }
  };

  return (
    <Layout style={{ minHeight: '100vh', backgroundColor: '#090514' }}>
      {/* Üst Askeri AI Başlık Çubuğu */}
      <Header
        style={{
          backgroundColor: '#120a24',
          borderBottom: '1px solid #2e1065',
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
              background: 'linear-gradient(135deg, #7c3aed 0%, #9333ea 100%)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              boxShadow: '0 0 14px rgba(124, 58, 237, 0.5)'
            }}
          >
            <RobotOutlined style={{ color: '#fff', fontSize: 18 }} />
          </div>

          <div>
            <div style={{ fontWeight: 800, fontSize: 16, color: '#f8fafc', letterSpacing: 0.5 }}>
              TAKTIK EH COPILOT KOKPİTİ
            </div>
            <div style={{ fontSize: 11, color: '#a78bfa', marginTop: -2 }}>
              Spring AI 1.x & Google Gemini Destekli Askeri İstihbarat & Karar Destek Ajanı
            </div>
          </div>

          <Tag color="purple" style={{ marginLeft: 8, fontSize: 11 }}>
            🧠 Port: 3002
          </Tag>
        </Space>

        <Space size={14}>
          <Tooltip title={copilotOnline ? 'ew-copilot-service (8082) Aktif' : 'Copilot Servisine Ulaşılamıyor'}>
            <Tag
              color={copilotOnline ? '#2e1065' : '#7f1d1d'}
              style={{
                color: copilotOnline ? '#c084fc' : '#f87171',
                borderColor: copilotOnline ? '#7c3aed' : '#dc2626',
                padding: '3px 10px',
                fontSize: 12
              }}
            >
              <Badge status={copilotOnline ? 'success' : 'error'} style={{ marginRight: 6 }} />
              AI Motoru: {copilotOnline ? 'ONLINE (8082)' : 'OFFLINE'}
            </Tag>
          </Tooltip>

          <Tooltip title="Bağımsız Telemetri & Yayın Konsolunu Ayrı Sekmede Aç">
            <Button
              type="primary"
              size="small"
              icon={<RadarChartOutlined />}
              href="http://localhost:3001"
              target="_blank"
              style={{
                backgroundColor: '#0284c7',
                borderColor: '#0369a1',
                boxShadow: '0 0 8px rgba(2, 132, 199, 0.4)'
              }}
            >
              Telemetri Konsoluna Git (Port 3001)
            </Button>
          </Tooltip>
        </Space>
      </Header>

      {/* Ana İçerik */}
      <Content style={{ padding: '16px 20px', maxWidth: 1750, margin: '0 auto', width: '100%' }}>
        <CopilotChat
          onSelectTarget={handleSelectTarget}
          onNavigateTab={() => {}}
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
