import React from 'react';
import { Card, Space, Tag } from 'antd';
import {
  RobotOutlined,
  ThunderboltOutlined,
  DotChartOutlined
} from '@ant-design/icons';
import CopilotChat from '../../components/CopilotChat';

/**
 * 🧠 YZ TABANLI SORGULAMA VE GÖRSELLEŞTİRME MİKRO-FRONTEND UYGULAMASI (Port: 8082)
 * Spring AI, Google Gemini, Taktik Korelasyon Doktrini ve ECharts Görselleştirme Kokpiti.
 */
export default function CopilotApp({
  onSelectTarget,
  onNavigateTab
}) {
  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 16 }}>
      {/* Servis Başlık & MFE Rozet Barı */}
      <Card
        size="small"
        style={{
          background: 'linear-gradient(90deg, #130e29 0%, #1a103c 100%)',
          borderColor: '#4c1d95'
        }}
      >
        <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center' }}>
          <Space>
            <RobotOutlined style={{ color: '#a855f7', fontSize: 18 }} />
            <span style={{ color: '#f1f5f9', fontWeight: 600, fontSize: 15 }}>
              AI Taktik Kokpit & Analitik Modülü (Copilot MFE)
            </span>
            <Tag color="#4c1d95" style={{ color: '#d8b4fe', border: '1px solid #7c3aed' }}>
              🧠 ew-copilot-service : 8082
            </Tag>
            <Tag color="purple">Spring AI + Google Gemini</Tag>
            <Tag color="gold">DTO & REST Tabanlı Haberleşme</Tag>
          </Space>
        </div>
      </Card>

      {/* AI Copilot Sohbet & ECharts Widget Paneli */}
      <CopilotChat
        onSelectTarget={onSelectTarget}
        onNavigateTab={onNavigateTab}
      />
    </div>
  );
}
