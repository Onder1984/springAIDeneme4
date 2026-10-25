import React, { useState } from 'react';
import { Card, Tabs, Space, Tag, Button, Tooltip } from 'antd';
import {
  RadarChartOutlined,
  SecurityScanOutlined,
  DatabaseOutlined,
  RobotOutlined,
  DashboardOutlined
} from '@ant-design/icons';
import RadarTable from '../../components/RadarTable';
import ComintTable from '../../components/ComintTable';
import DetailDrawer from '../../components/DetailDrawer';
import DatabaseMonitorWidget from '../../components/DatabaseMonitorWidget';

/**
 * 📡 YAYIN VE TELEMETRİ MİKRO-FRONTEND UYGULAMASI (Port: 8081 Veri Sağlayıcıya Bağlı)
 * Radar (ELINT) ve Muhabere (COMINT) yayınlarını, LOB kestirimlerini ve detaylarını yönetir.
 */
export default function EmissionApp({
  onSelectTarget,
  onAskCopilot,
  initialSubTab = 'radar'
}) {
  const [activeTab, setActiveTab] = useState(initialSubTab);
  const [drawerOpen, setDrawerOpen] = useState(false);
  const [drawerType, setDrawerType] = useState('RADAR');
  const [drawerData, setDrawerData] = useState(null);
  const [drawerLoading, setDrawerLoading] = useState(false);

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
      if (onSelectTarget) {
        onSelectTarget(id, type, data);
      }
    } catch (err) {
      console.error('Detay çekme hatası:', err);
    } finally {
      setDrawerLoading(false);
    }
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {/* Servis Başlık & MFE Rozet Barı */}
      <Card
        size="small"
        style={{
          background: 'linear-gradient(90deg, #0b1329 0%, #0d1b38 100%)',
          borderColor: '#1e3a8a'
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Space>
            <DatabaseOutlined style={{ color: '#38bdf8', fontSize: 18 }} />
            <span style={{ color: '#f1f5f9', fontWeight: 600, fontSize: 15 }}>
              Yayın & Telemetri Yönetim Modülü (Emission MFE)
            </span>
            <Tag color="#1e3a8a" style={{ color: '#93c5fd', border: '1px solid #3b82f6' }}>
              📡 ew-emission-service : 8081
            </Tag>
            <Tag color="cyan">PostgreSQL + JPA</Tag>
          </Space>

          {onAskCopilot && (
            <Tooltip title="Bu ekrandaki veriler hakkında yapay zeka copilot ile sohbet et">
              <Button
                type="primary"
                size="small"
                icon={<RobotOutlined />}
                onClick={() => onAskCopilot('Sahadaki radar ve muhabere yayınlarının genel durumu nedir?')}
                style={{ background: '#0284c7', borderColor: '#0284c7' }}
              >
                AI Kokpitte Analiz Et
              </Button>
            </Tooltip>
          )}
        </div>
      </Card>

      {/* Radar ve Muhabere Tablo Sekmeleri */}
      <Tabs
        activeKey={activeTab}
        onChange={setActiveTab}
        type="card"
        size="middle"
        items={[
          {
            key: 'radar',
            label: (
              <span style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '0 8px' }}>
                <RadarChartOutlined style={{ color: '#38bdf8' }} />
                Radar (ELINT) Tablosu & LOB'lar
              </span>
            ),
            children: (
              <RadarTable
                onSelectEmission={(id) => handleOpenDetail(id, 'RADAR')}
              />
            )
          },
          {
            key: 'comint',
            label: (
              <span style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '0 8px' }}>
                <SecurityScanOutlined style={{ color: '#c084fc' }} />
                Muhabere (COMINT) Tablosu & Telsiz Ağları
              </span>
            ),
            children: (
              <ComintTable
                onSelectComint={(id) => handleOpenDetail(id, 'COMINT')}
              />
            )
          },
          {
            key: 'db-monitor',
            label: (
              <span style={{ display: 'flex', alignItems: 'center', gap: 8, padding: '0 8px' }}>
                <DashboardOutlined style={{ color: '#06b6d4' }} />
                ⚡ DB & Sorgu İzleme
              </span>
            ),
            children: <DatabaseMonitorWidget />
          }
        ]}
      />

      {/* Ortak Detay Çekmecesi (Drawer) */}
      <DetailDrawer
        open={drawerOpen}
        onClose={() => setDrawerOpen(false)}
        type={drawerType}
        data={drawerData}
        loading={drawerLoading}
      />
    </div>
  );
}
