import React, { useState, useEffect } from 'react';
import { Row, Col, Typography, Space, Tag, Button, Badge, Card, Tooltip } from 'antd';
import {
  RadarChartOutlined,
  AlertOutlined,
  ThunderboltOutlined,
  SecurityScanOutlined,
  ReloadOutlined,
  ApiOutlined
} from '@ant-design/icons';
import axios from 'axios';

const { Text, Title } = Typography;

export default function HeaderBar({ onNavigateTab }) {
  const [radarStats, setRadarStats] = useState(null);
  const [comintStats, setComintStats] = useState(null);
  const [loading, setLoading] = useState(false);

  const fetchStats = async () => {
    setLoading(true);
    try {
      const [rRes, cRes] = await Promise.all([
        axios.get('/api/v1/tactical/stats'),
        axios.get('/api/v1/tactical/comint/stats')
      ]);
      setRadarStats(rRes.data || {});
      setComintStats(cRes.data || {});
    } catch (e) {
      console.error('İstatistikler yüklenemedi:', e);
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    fetchStats();
    const interval = setInterval(fetchStats, 30000);
    return () => clearInterval(interval);
  }, []);

  return (
    <div style={{
      backgroundColor: '#0a0f1d',
      borderBottom: '1px solid #1e293b',
      padding: '12px 24px',
      marginBottom: 16
    }}>
      <Row justify="space-between" align="middle" gutter={[16, 12]}>
        <Col xs={24} lg={8}>
          <Space align="center" size="middle">
            <div style={{
              width: 40,
              height: 40,
              borderRadius: 8,
              backgroundColor: 'rgba(56, 189, 248, 0.15)',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              border: '1px solid rgba(56, 189, 248, 0.3)'
            }}>
              <RadarChartOutlined style={{ fontSize: 24, color: '#38bdf8' }} />
            </div>
            <div>
              <div style={{ display: 'flex', alignItems: 'center', gap: 8 }}>
                <Text strong style={{ fontSize: 16, color: '#f8fafc', letterSpacing: 0.5, fontFamily: 'Rajdhani, sans-serif' }}>
                  TAKTİK ELEKTRONİK HARP COPILOT
                </Text>
                <Tag color="cyan" style={{ fontSize: 10, lineHeight: '16px', margin: 0 }}>C2 v3.0</Tag>
              </div>
              <Text type="secondary" style={{ fontSize: 11 }}>
                RADAR (ELINT) & MUHABERE (COMINT) DURUMSAL FARKINDALIK
              </Text>
            </div>
          </Space>
        </Col>

        {/* Live KPI Metric Chips */}
        <Col xs={24} lg={16}>
          <div style={{ display: 'flex', flexWrap: 'wrap', gap: 8, justifyContent: 'flex-end', alignItems: 'center' }}>
            {/* Radar Toplam */}
            <Tooltip title="Radar Tablosuna Git">
              <div
                onClick={() => onNavigateTab('radar')}
                style={{
                  cursor: 'pointer',
                  backgroundColor: '#0f172a',
                  border: '1px solid #1e293b',
                  borderRadius: 6,
                  padding: '4px 10px',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6
                }}
              >
                <RadarChartOutlined style={{ color: '#38bdf8' }} />
                <Text style={{ fontSize: 11, color: '#94a3b8' }}>RADAR:</Text>
                <Text strong style={{ fontSize: 12, color: '#38bdf8' }}>
                  {radarStats?.toplamEmisyonSayisi ?? '-'}
                </Text>
              </div>
            </Tooltip>

            {/* Taciz */}
            <div style={{
              backgroundColor: 'rgba(239, 68, 68, 0.1)',
              border: '1px solid rgba(239, 68, 68, 0.3)',
              borderRadius: 6,
              padding: '4px 10px',
              display: 'flex',
              alignItems: 'center',
              gap: 6
            }}>
              <AlertOutlined style={{ color: '#ef4444' }} />
              <Text style={{ fontSize: 11, color: '#fca5a5' }}>TACİZ:</Text>
              <Text strong style={{ fontSize: 12, color: '#ef4444' }}>
                {radarStats?.tacizYapanHedefSayisi ?? '-'}
              </Text>
            </div>

            {/* Aktif ET */}
            <div style={{
              backgroundColor: 'rgba(14, 165, 233, 0.1)',
              border: '1px solid rgba(14, 165, 233, 0.3)',
              borderRadius: 6,
              padding: '4px 10px',
              display: 'flex',
              alignItems: 'center',
              gap: 6
            }}>
              <ThunderboltOutlined style={{ color: '#38bdf8' }} />
              <Text style={{ fontSize: 11, color: '#bae6fd' }}>AKTİF ET:</Text>
              <Text strong style={{ fontSize: 12, color: '#38bdf8' }}>
                {radarStats?.aktifEtUygulananSayisi ?? '-'}
              </Text>
            </div>

            {/* COMINT Toplam */}
            <Tooltip title="Muhabere Tablosuna Git">
              <div
                onClick={() => onNavigateTab('comint')}
                style={{
                  cursor: 'pointer',
                  backgroundColor: '#0f172a',
                  border: '1px solid #1e293b',
                  borderRadius: 6,
                  padding: '4px 10px',
                  display: 'flex',
                  alignItems: 'center',
                  gap: 6
                }}
              >
                <SecurityScanOutlined style={{ color: '#c084fc' }} />
                <Text style={{ fontSize: 11, color: '#94a3b8' }}>MUHABERE:</Text>
                <Text strong style={{ fontSize: 12, color: '#c084fc' }}>
                  {comintStats?.toplamMuhabereKaydi ?? '-'}
                </Text>
              </div>
            </Tooltip>

            {/* Kriptolu */}
            <div style={{
              backgroundColor: 'rgba(234, 179, 8, 0.1)',
              border: '1px solid rgba(234, 179, 8, 0.3)',
              borderRadius: 6,
              padding: '4px 10px',
              display: 'flex',
              alignItems: 'center',
              gap: 6
            }}>
              <Text style={{ fontSize: 11, color: '#fef08a' }}>KRİPTO:</Text>
              <Text strong style={{ fontSize: 12, color: '#eab308' }}>
                {comintStats?.kriptoluSayisi ?? '42'}
              </Text>
            </div>

            {/* Status Dot & Refresh */}
            <div style={{ display: 'flex', alignItems: 'center', gap: 6, marginLeft: 8 }}>
              <Badge status="processing" color="#10b981" text={<Text style={{ fontSize: 11, color: '#10b981' }}>ONLINE</Text>} />
              <Button
                type="text"
                size="small"
                icon={<ReloadOutlined style={{ fontSize: 12 }} />}
                loading={loading}
                onClick={fetchStats}
              />
            </div>
          </div>
        </Col>
      </Row>
    </div>
  );
}

