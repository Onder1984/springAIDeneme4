import React, { useState, useEffect } from 'react';
import { Layout, Menu, Space, Tag, Badge, Button, Row, Col, Tooltip } from 'antd';
import {
  AppstoreOutlined,
  DatabaseOutlined,
  RobotOutlined,
  SplitCellsOutlined,
  CheckCircleOutlined,
  SyncOutlined,
  RadarChartOutlined,
  DashboardOutlined
} from '@ant-design/icons';
import EmissionApp from '../apps/emission/EmissionApp';
import CopilotApp from '../apps/copilot/CopilotApp';
import DatabaseMonitorWidget from '../components/DatabaseMonitorWidget';

const { Header, Content } = Layout;

/**
 * 🌐 ORTAK MFE COMPOSER (HOST SHELL / KABUK)
 * Menü üzerinden her iki bağımsız mikroservisin ekranlarını açar:
 * 1. Yayın Servisi Ekranları (Radar & Comint Tabloları)
 * 2. YZ Copilot Ekranı (AI Chat & ECharts Widget'ları)
 * 3. Birleşik Taktik Komuta (Split-Screen / Bölünmüş Ekran Görünümü)
 */
export default function ComposerLayout() {
  const [currentMenu, setCurrentMenu] = useState('split'); // 'emission' | 'copilot' | 'split'
  const [selectedTarget, setSelectedTarget] = useState(null);
  const [emissionSubTab, setEmissionSubTab] = useState('radar');

  // Backend Servis Canlılık Durumu
  const [emissionOnline, setEmissionOnline] = useState(true);
  const [copilotOnline, setCopilotOnline] = useState(true);

  // Periyodik sağlık kontrolü
  useEffect(() => {
    const checkHealth = async () => {
      try {
        const res1 = await fetch('/api/v1/tactical/emissions/all?limit=1');
        setEmissionOnline(res1.ok);
      } catch {
        setEmissionOnline(false);
      }

      try {
        const res2 = await fetch('/api/v1/health');
        setCopilotOnline(res2.ok);
      } catch {
        setCopilotOnline(false);
      }
    };

    checkHealth();
    const interval = setInterval(checkHealth, 15000);
    return () => clearInterval(interval);
  }, []);

  const handleNavigateFromCopilot = (tabKey, filter = '') => {
    setCurrentMenu('emission');
    setEmissionSubTab(tabKey);
  };

  return (
    <Layout style={{ minHeight: '100vh', backgroundColor: '#060a14' }}>
      {/* Üst C2 Composer Header Bar */}
      <Header
        style={{
          display: 'flex',
          alignItems: 'center',
          justifyContent: 'space-between',
          background: 'linear-gradient(90deg, #090e1d 0%, #0f172a 100%)',
          borderBottom: '1px solid #1e293b',
          padding: '0 24px',
          height: 64,
          position: 'sticky',
          top: 0,
          zIndex: 1000
        }}
      >
        {/* Sol Logo & Başlık */}
        <div style={{ display: 'flex', alignItems: 'center', gap: 12 }}>
          <RadarChartOutlined style={{ fontSize: 26, color: '#38bdf8' }} />
          <div>
            <div style={{ color: '#f8fafc', fontWeight: 800, fontSize: 16, letterSpacing: '0.05em' }}>
              TAKTIK C2 MİKRO-FRONTEND COMPOSER
            </div>
            <div style={{ color: '#64748b', fontSize: 11 }}>
              Elektronik Harp Müşterek Komuta Kontrol & YZ Operasyonel Kokpiti
            </div>
          </div>
        </div>

        {/* Orta Navigasyon Menüsü */}
        <Menu
          theme="dark"
          mode="horizontal"
          selectedKeys={[currentMenu]}
          onClick={(e) => setCurrentMenu(e.key)}
          style={{
            background: 'transparent',
            borderBottom: 'none',
            fontSize: 14,
            fontWeight: 500,
            minWidth: 460
          }}
          items={[
            {
              key: 'split',
              icon: <SplitCellsOutlined style={{ fontSize: 16, color: '#38bdf8' }} />,
              label: 'Birleşik Komuta (Split View)'
            },
            {
              key: 'emission',
              icon: <DatabaseOutlined style={{ fontSize: 16, color: '#0ea5e9' }} />,
              label: 'Yayın Servisi (Radar & Muhabere)'
            },
            {
              key: 'copilot',
              icon: <RobotOutlined style={{ fontSize: 16, color: '#a855f7' }} />,
              label: 'AI Copilot & Analitik'
            },
            {
              key: 'db-monitor',
              icon: <DashboardOutlined style={{ fontSize: 16, color: '#06b6d4' }} />,
              label: '⚡ DB & Sorgu İzleme'
            }
          ]}
        />

        {/* Sağ Servis Durum Göstergeleri */}
        <Space size="middle">
          <Tooltip title="ew-emission-service (Port: 8081) - PostgreSQL Veri Katmanı">
            <Tag
              icon={emissionOnline ? <CheckCircleOutlined /> : <SyncOutlined spin />}
              color={emissionOnline ? '#064e3b' : '#7f1d1d'}
              style={{
                borderRadius: 12,
                padding: '2px 10px',
                border: emissionOnline ? '1px solid #059669' : '1px solid #dc2626',
                color: emissionOnline ? '#6ee7b7' : '#fca5a5'
              }}
            >
              📡 Yayın Servisi : 8081
            </Tag>
          </Tooltip>

          <Tooltip title="ew-copilot-service (Port: 8082) - Spring AI & Google Gemini">
            <Tag
              icon={copilotOnline ? <CheckCircleOutlined /> : <SyncOutlined spin />}
              color={copilotOnline ? '#3b0764' : '#7f1d1d'}
              style={{
                borderRadius: 12,
                padding: '2px 10px',
                border: copilotOnline ? '1px solid #7c3aed' : '1px solid #dc2626',
                color: copilotOnline ? '#d8b4fe' : '#fca5a5'
              }}
            >
              🧠 AI Copilot : 8082
            </Tag>
          </Tooltip>
        </Space>
      </Header>

      {/* Ana İçerik Alanı */}
      <Content style={{ padding: '20px 24px', backgroundColor: '#060a14' }}>
        {/* Mod 1: Yayın Servisi Ekranı (Tam Ekran) */}
        {currentMenu === 'emission' && (
          <EmissionApp
            initialSubTab={emissionSubTab}
            onSelectTarget={(id, type, data) => setSelectedTarget({ id, type, data })}
            onAskCopilot={(prompt) => {
              setCurrentMenu('split');
            }}
          />
        )}

        {/* Mod 2: AI Copilot Ekranı (Tam Ekran) */}
        {currentMenu === 'copilot' && (
          <CopilotApp
            onSelectTarget={(id, type) => {
              setSelectedTarget({ id, type });
            }}
            onNavigateTab={handleNavigateFromCopilot}
          />
        )}

        {/* Mod 3: Birleşik Taktik Komuta (Split View) */}
        {currentMenu === 'split' && (
          <Row gutter={[16, 16]}>
            <Col xs={24} xl={13}>
              <EmissionApp
                initialSubTab={emissionSubTab}
                onSelectTarget={(id, type, data) => setSelectedTarget({ id, type, data })}
              />
            </Col>
            <Col xs={24} xl={11}>
              <CopilotApp
                onSelectTarget={(id, type) => {
                  setSelectedTarget({ id, type });
                }}
                onNavigateTab={handleNavigateFromCopilot}
              />
            </Col>
          </Row>
        )}

        {/* Mod 4: Veritabanı ve Sorgu Performans İzleme (Tam Ekran) */}
        {currentMenu === 'db-monitor' && (
          <DatabaseMonitorWidget />
        )}
      </Content>
    </Layout>
  );
}
