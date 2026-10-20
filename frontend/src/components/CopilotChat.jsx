import React, { useState, useRef, useEffect } from 'react';
import { Input, Button, Card, Typography, Space, Tag, Collapse, Spin, Alert, Empty } from 'antd';
import {
  SendOutlined,
  ThunderboltOutlined,
  RadarChartOutlined,
  SecurityScanOutlined,
  CodeOutlined,
  RobotOutlined,
  UserOutlined,
  ArrowRightOutlined,
  FireOutlined,
  PieChartOutlined,
  LineChartOutlined,
  BarChartOutlined
} from '@ant-design/icons';
import axios from 'axios';
import { marked } from 'marked';
import CallsignHeatmapWidget from './CallsignHeatmapWidget';
import TacticalDistributionPieWidget from './TacticalDistributionPieWidget';
import TacticalWaveformTimelineWidget from './TacticalWaveformTimelineWidget';
import RadarSpectrumOccupancyWidget from './RadarSpectrumOccupancyWidget';
import WeekdayRadarDistributionWidget from './WeekdayRadarDistributionWidget';

const { TextArea } = Input;
const { Text } = Typography;

export default function CopilotChat({ onSelectTarget, onNavigateTab }) {
  const [messages, setMessages] = useState([
    {
      role: 'assistant',
      content: `### 🛡️ TAKTİK ELEKTRONİK HARP COPILOT SİSTEMİ DEVREDE
Sahadaki **Radar (ELINT)** ve **Muhabere (COMINT)** hedefleri operasyonel durumsal farkındalık için hazır.

Aşağıdaki hızlı komutları kullanabilir veya doğal dilde arama yapabilirsiniz:
- *'SP048 radarının zaman içindeki frekans, PRI ve mod değişimlerini analiz et'*
- *'VOLGA-01 telsiz ağının zaman içindeki frekans ve sinyal gücü değişimini göster'*
- *'Hangi çağrı adı kiminle ne kadar konuşmuş, en aktif çiftleri ve süreleri analiz et'*
- *'Düşman atış kontrol radarlarını ve HSS mevzilerini listele'*
- *'8500-10000 MHz aralığındaki X-band radarları getir'*
- *'Sahadaki radar yayınlarının polarizasyon ve teşhis dağılımını göster'*`,
      executionLogs: []
    }
  ]);
  const [inputText, setInputText] = useState('');
  const [loading, setLoading] = useState(false);
  const [activeWidget, setActiveWidget] = useState(null); // null | 'CALLSIGN_HEATMAP' | 'PIE_DISTRIBUTION' | 'WAVEFORM_TIMELINE' | 'SPECTRUM_OCCUPANCY' | 'WEEKDAY_RADAR'
  const [distributionConfig, setDistributionConfig] = useState({ source: 'RADAR', criterion: 'TESHIS' });
  const [occupancyConfig, setOccupancyConfig] = useState({ timeRange: 'SON_24_SAAT', identities: ['DUSMAN'] });
  const [timelineConfig, setTimelineConfig] = useState({ source: 'RADAR', targetKey: 'SP048' });
  const [weekdayRadarConfig, setWeekdayRadarConfig] = useState({ source: 'TUMU', recordType: 'TUMU', timeRange: 'ALL' });
  const messagesEndRef = useRef(null);

  const scrollToBottom = () => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
  };

  useEffect(() => {
    scrollToBottom();
  }, [messages, loading]);

  // Sorgu metni ve LLM araç çağrılarına göre uygun dinamik taktiksel widget'ı tespit et
  const detectWidgetForQuery = (prompt, executionLogs = []) => {
    const p = (prompt || '').toLowerCase();

    // Görselleştirme/Grafik talebi içeren açık anahtar kelimeler
    const hasVisualKeyword =
      p.includes('grafik') ||
      p.includes('grafiği') ||
      p.includes('grafigi') ||
      p.includes('çizelge') ||
      p.includes('cizelge') ||
      p.includes('harita') ||
      p.includes('haritası') ||
      p.includes('pasta') ||
      p.includes('pie') ||
      p.includes('donut') ||
      p.includes('örümcek') ||
      p.includes('orumcek') ||
      p.includes('spider') ||
      p.includes('dalga formu') ||
      p.includes('waveform') ||
      p.includes('matris');

    // Operatör açık bir grafik talep etmeksizin sadece net adet / sayı sorduysa grafik açma
    const isCountPrompt =
      !hasVisualKeyword &&
      /\b(ka[çc]|adet|say[ıi]s[ıi]|toplam say[ıi]|tane)\b/i.test(p);

    if (isCountPrompt) {
      return null;
    }

    // 1. Telsiz Çağrı Adı Isı Haritası & İletişim Matrisi Tetikleyicileri
    const isHeatmapPrompt =
      p.includes('ısı haritası') ||
      p.includes('isi haritasi') ||
      p.includes('kim kiminle') ||
      p.includes('kiminle ne kadar') ||
      p.includes('iletişim matrisi') ||
      p.includes('iletisim matrisi') ||
      p.includes('etkileşim matrisi') ||
      p.includes('etkilesim matrisi') ||
      p.includes('çağrı çift') ||
      p.includes('cagri cift') ||
      (p.includes('telsiz ağı') && (p.includes('grafik') || p.includes('matris') || p.includes('harita'))) ||
      (p.includes('haberleşme ağı') && (p.includes('grafik') || p.includes('matris') || p.includes('harita'))) ||
      p.includes('link ilişki') ||
      (p.includes('çağrı') && (p.includes('analiz') || p.includes('görüşme süresi') || p.includes('etkileşim')));

    const isHeatmapTool = executionLogs.some((log) =>
      log.includes('getCallsignPairInteractionStats') ||
      log.includes('queryComintCallsignNetwork') ||
      log.includes('getMostActiveCallsigns')
    );

    if (isHeatmapPrompt || isHeatmapTool) {
      return { type: 'CALLSIGN_HEATMAP' };
    }

    // 2. Dalga Formu & Zamansal Değişim (Waveform Timeline) Tespiti
    // Sırf 'SP040' veya 'spot no' geçti diye grafik AÇILMAZ; kullanıcı dalga formu/zamansal değişim veya grafik istemelidir.
    const isTimelinePrompt =
      p.includes('dalga formu') ||
      p.includes('waveform') ||
      p.includes('zamansal değişim') ||
      p.includes('zamansal degisim') ||
      p.includes('frekans değişimi') ||
      p.includes('frekans degisimi') ||
      p.includes('pri değişimi') ||
      p.includes('pw değişimi') ||
      p.includes('zaman serisi') ||
      p.includes('timeline') ||
      p.includes('intraday') ||
      ((p.includes('frekans') || p.includes('pri') || p.includes('pw')) && (p.includes('grafik') || p.includes('çizelge') || p.includes('cizelge')));

    const timelineToolLog = executionLogs.find((log) => log.includes('getTacticalWaveformTimeline'));

    if (isTimelinePrompt || timelineToolLog) {
      let src = 'RADAR';
      let key = '';

      if (timelineToolLog) {
        const srcMatch = timelineToolLog.match(/source=([A-Za-z]+)/);
        const keyMatch = timelineToolLog.match(/targetKey=([A-Za-z0-9\-_]+)/);
        if (srcMatch) src = srcMatch[1].toUpperCase();
        if (keyMatch) key = keyMatch[1].toUpperCase();
      }

      if (!key) {
        if (p.includes('muhabere') || p.includes('comint') || p.includes('telsiz') || p.includes('çağrı adı') || p.includes('cagri adi') || p.includes('volga') || p.includes('omar')) {
          src = 'COMINT';
        }
        const spotMatch = p.match(/\b(sp\d{1,4})\b/i);
        if (spotMatch) {
          key = spotMatch[1].toUpperCase();
          src = 'RADAR';
        } else {
          const callMatch = p.match(/\b(volga-\d+|omar-\d+|kartal-\d+|shadow-\d+|hermes-\d+)\b/i);
          if (callMatch) {
            key = callMatch[1].toUpperCase();
            src = 'COMINT';
          }
        }
      }

      return { type: 'WAVEFORM_TIMELINE', source: src, targetKey: key || (src === 'RADAR' ? 'SP048' : 'VOLGA-01') };
    }

    // 3. Haftanın Günlerine Göre LOB ve Kestirim Tespit Dağılımı (Örümcek Ağı / Spider Web) Tespiti
    const isWeekdayRadarPrompt =
      p.includes('haftanın günleri') ||
      p.includes('haftanin gunleri') ||
      p.includes('günlere göre') ||
      p.includes('gunlere gore') ||
      p.includes('örümcek ağı') ||
      p.includes('orumcek agi') ||
      p.includes('örümcek agi') ||
      p.includes('spider web') ||
      p.includes('spider chart') ||
      p.includes('haftalık lob') ||
      p.includes('haftalik lob') ||
      p.includes('haftalık kestirim') ||
      p.includes('haftalik kestirim') ||
      ((p.includes('haftalık') || p.includes('haftalik')) && (p.includes('dağılım') || p.includes('dagilim') || p.includes('grafik')));

    const weekdayToolLog = executionLogs.find((log) =>
      log.includes('getWeekdayEmissionDistribution') || log.includes('getWeekdayRadarDistribution') || log.includes('weekday-radar')
    );

    if (isWeekdayRadarPrompt || weekdayToolLog) {
      let src = 'TUMU';
      let rec = 'TUMU';
      let time = 'ALL';

      if (weekdayToolLog) {
        const srcMatch = weekdayToolLog.match(/source=([A-Za-z]+)/);
        const recMatch = weekdayToolLog.match(/recordType=([A-Za-z0-9_]+)/);
        const timeMatch = weekdayToolLog.match(/timeRange=([A-Za-z0-9_]+)/);
        if (srcMatch) src = srcMatch[1].toUpperCase();
        if (recMatch) rec = recMatch[1].toUpperCase();
        if (timeMatch) time = timeMatch[1].toUpperCase();
      } else {
        if (p.includes('radar') && !p.includes('muhabere') && !p.includes('comint')) src = 'RADAR';
        else if ((p.includes('muhabere') || p.includes('comint') || p.includes('telsiz')) && !p.includes('radar')) src = 'COMINT';
        else src = 'TUMU';

        if (p.includes('sadece lob') || (p.includes('lob') && !p.includes('kestirim'))) rec = 'SADECE_LOB';
        else if (p.includes('sadece kestirim') || (p.includes('kestirim') && !p.includes('lob'))) rec = 'SADECE_KESTIRIM';
        else rec = 'TUMU';

        if (p.includes('7 gün') || p.includes('7 gun') || p.includes('bir hafta') || p.includes('son hafta')) time = 'SON_7_GUN';
        else if (p.includes('30 gün') || p.includes('30 gun') || p.includes('ay')) time = 'SON_30_GUN';
      }

      return { type: 'WEEKDAY_RADAR', source: src, recordType: rec, timeRange: time };
    }

    // 4. Radar Frekans Spektrum Doluluk & Yoğunluk Tespiti
    const isOccupancyPrompt =
      p.includes('spektrum doluluk') ||
      p.includes('doluluk oranı') ||
      p.includes('doluluk orani') ||
      p.includes('frekans doluluk') ||
      p.includes('bant doluluk') ||
      (p.includes('spektrum') && (p.includes('grafik') || p.includes('bar') || p.includes('doluluk'))) ||
      p.includes('occupancy');

    const occupancyToolLog = executionLogs.find((log) =>
      log.includes('getRadarSpectrumOccupancy') || log.includes('spectrum-occupancy')
    );

    if (isOccupancyPrompt || occupancyToolLog) {
      let time = 'SON_24_SAAT';
      if (p.includes('1 saat') || p.includes('son saat')) time = 'SON_1_SAAT';
      else if (p.includes('hafta') || p.includes('7 gün')) time = 'SON_1_HAFTA';
      return { type: 'SPECTRUM_OCCUPANCY', timeRange: time };
    }

    // 5. Taktik Kategorik Dağılım / Pasta (Pie & Donut) Grafiği Tetikleyicileri
    const hasExplicitPieWord =
      p.includes('pasta') ||
      p.includes('pie') ||
      p.includes('donut') ||
      p.includes('dağılım') ||
      p.includes('dagilim') ||
      p.includes('oranları') ||
      p.includes('oranlari') ||
      p.includes('yüzdeleri') ||
      p.includes('yuzdeleri');

    const isPiePrompt =
      (p.includes('pasta') || p.includes('pie') || p.includes('donut')) ||
      (hasExplicitPieWord && (
        p.includes('polarizasyon') ||
        p.includes('lisan') ||
        p.includes('haberleşme') ||
        p.includes('modülasyon') ||
        p.includes('modulasyon') ||
        p.includes('görev') ||
        p.includes('gorev') ||
        p.includes('platform') ||
        p.includes('hedef yer') ||
        p.includes('mevzi') ||
        p.includes('frekans bandı') ||
        p.includes('nato bant') ||
        p.includes('teşhis') ||
        p.includes('teshis')
      ));

    const distributionToolLog = executionLogs.find((log) => log.includes('getTacticalDistribution'));

    if (isPiePrompt || distributionToolLog) {
      let src = 'RADAR';
      let crit = 'TESHIS';

      if (distributionToolLog) {
        const srcMatch = distributionToolLog.match(/source=([A-Za-z]+)/);
        const critMatch = distributionToolLog.match(/criterion=([A-Za-z0-9_]+)/);
        if (srcMatch) src = srcMatch[1].toUpperCase();
        if (critMatch) crit = critMatch[1].toUpperCase();
      } else {
        if (p.includes('muhabere') || p.includes('comint') || p.includes('telsiz') || p.includes('lisan') || p.includes('haberleşme') || p.includes('protokol') || p.includes('çağrı') || p.includes('cagri')) {
          src = 'COMINT';
        }

        if (p.includes('spot')) crit = 'SPOT_NO';
        else if (p.includes('çağrı') || p.includes('cagri')) crit = 'CAGRI_ADI';
        else if (p.includes('polarizasyon')) crit = 'POLARIZASYON';
        else if (p.includes('modülasyon') || p.includes('modulasyon')) crit = 'MODULASYON';
        else if (p.includes('lisan') || p.includes('dil')) crit = 'LISAN';
        else if (p.includes('haberleşme')) crit = 'HABERLESME_SEKLI';
        else if (p.includes('görev') || p.includes('gorev')) crit = 'GOREV';
        else if (p.includes('platform')) crit = 'PLATFORM';
        else if (p.includes('mevzi')) crit = 'MEVZI';
        else if (p.includes('yer') || p.includes('sektör') || p.includes('bolge') || p.includes('bölge')) crit = 'HEDEF_YER';
        else if (p.includes('frekans bandı') || p.includes('bant')) crit = src === 'RADAR' ? 'FREKANS_BANDI' : 'BANT';
        else if (p.includes('protokol')) crit = 'PROTOKOL';
        else if (p.includes('teşhis') || p.includes('tehdit') || p.includes('dost') || p.includes('düşman')) crit = 'TESHIS';
      }

      return { type: 'PIE_DISTRIBUTION', source: src, criterion: crit };
    }

    return null;
  };

  const quickPrompts = [
    { label: '📡 Genel Taktik Durumu Özetle', prompt: 'Sahadaki genel taktik durumu, düşman radarlarını ve telsiz muhabere resmini özetle.' }
  ];

  const handleSend = async (customPrompt) => {
    const textToSend = customPrompt || inputText;
    if (!textToSend || !textToSend.trim() || loading) return;

    const userMessage = {
      role: 'user',
      content: textToSend,
      time: new Date().toLocaleTimeString()
    };

    setMessages((prev) => [...prev, userMessage]);
    setInputText('');
    setLoading(true);

    try {
      const res = await axios.post('/api/v1/tactical/analyze', {
        userPrompt: textToSend,
        conversationId: 'c2-tactical-session'
      });

      const logs = res.data?.executionLogs || [];
      const detectedWidget = detectWidgetForQuery(textToSend, logs);

      // İlgili görselleştirici tespit edildiyse paneli aç, alakasız sorgularda (düz tablo vb.) kapatarak tam genişliğe dön
      if (detectedWidget) {
        setActiveWidget(detectedWidget.type);
        if (detectedWidget.type === 'PIE_DISTRIBUTION') {
          setDistributionConfig({
            source: detectedWidget.source || 'RADAR',
            criterion: detectedWidget.criterion || 'TESHIS'
          });
        } else if (detectedWidget.type === 'WAVEFORM_TIMELINE') {
          setTimelineConfig({
            source: detectedWidget.source || 'RADAR',
            targetKey: detectedWidget.targetKey || 'SP048'
          });
        } else if (detectedWidget.type === 'WEEKDAY_RADAR') {
          setWeekdayRadarConfig({
            source: detectedWidget.source || 'TUMU',
            recordType: detectedWidget.recordType || 'TUMU',
            timeRange: detectedWidget.timeRange || 'ALL'
          });
        } else if (detectedWidget.type === 'SPECTRUM_OCCUPANCY') {
          setOccupancyConfig({
            timeRange: detectedWidget.timeRange || 'SON_24_SAAT',
            identities: ['DUSMAN']
          });
        }
      } else {
        setActiveWidget(null);
      }

      const assistantMessage = {
        role: 'assistant',
        content: res.data?.operationalBriefing || 'Yanıt alınamadı.',
        executionLogs: logs,
        relatedWidget: detectedWidget,
        time: new Date().toLocaleTimeString()
      };

      setMessages((prev) => [...prev, assistantMessage]);
    } catch (err) {
      console.error('Sorgu işleme hatası:', err);
      setMessages((prev) => [
        ...prev,
        {
          role: 'assistant',
          content: '❌ **Taktik Analiz Hatası:** Sunucu bağlantısı sağlanamadı veya sorgu zaman aşımına uğradı.',
          executionLogs: ['Hata: ' + (err.message || 'Bilinmeyen hata')],
          isError: true
        }
      ]);
    } finally {
      setLoading(false);
    }
  };

  // Markdown içindeki [EMI-XXXX] ve [COM-XXXX] tıklanabilir yapmak için DOM tıklama delegasyonu
  const handleContentClick = (e) => {
    const target = e.target;
    if (target.dataset && target.dataset.targetId) {
      const id = target.dataset.targetId;
      const type = id.startsWith('EMI') ? 'RADAR' : 'COMINT';
      onSelectTarget(id, type);
    }
  };

  // Markdown HTML formatlama + Target Link Regex enjeksiyonu
  const formatMarkdown = (markdownText) => {
    if (!markdownText) return '';
    // [EMI-1001] -> clickable badge
    let processed = markdownText.replace(
      /\[(EMI-\d+)\]/g,
      `<span class="interactive-target-tag radar" data-target-id="$1" title="Radar Detayını Aç">$1</span>`
    );
    // [COM-2001] -> clickable badge
    processed = processed.replace(
      /\[(COM-\d+)\]/g,
      `<span class="interactive-target-tag comint" data-target-id="$1" title="Muhabere Detayını Aç">$1</span>`
    );

    return marked.parse(processed);
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', height: 'calc(100vh - 190px)' }}>
      {/* Hızlı Komut Çipleri ve Widget Aç/Kapat Barı (Sola Yanaşık) */}
      <div style={{ marginBottom: 12, display: 'flex', justifyContent: 'flex-start', alignItems: 'center', gap: 10, flexWrap: 'wrap' }}>
        {/* Sağ Panel Widget Göster/Gizle Butonları */}
        <Space size={6} wrap>
          <Button
            size="small"
            type={activeWidget === 'CALLSIGN_HEATMAP' ? 'primary' : 'default'}
            ghost={activeWidget === 'CALLSIGN_HEATMAP'}
            icon={<FireOutlined style={{ color: activeWidget === 'CALLSIGN_HEATMAP' ? '#ef4444' : '#94a3b8' }} />}
            onClick={() => setActiveWidget(activeWidget === 'CALLSIGN_HEATMAP' ? null : 'CALLSIGN_HEATMAP')}
            style={{
              borderColor: activeWidget === 'CALLSIGN_HEATMAP' ? '#ef4444' : '#334155',
              color: activeWidget === 'CALLSIGN_HEATMAP' ? '#f87171' : '#cbd5e1',
              borderRadius: 6
            }}
          >
            {activeWidget === 'CALLSIGN_HEATMAP' ? '🔥 Isı Haritası (Açık)' : '📊 Isı Haritası'}
          </Button>

          <Button
            size="small"
            type={activeWidget === 'PIE_DISTRIBUTION' ? 'primary' : 'default'}
            ghost={activeWidget === 'PIE_DISTRIBUTION'}
            icon={<PieChartOutlined style={{ color: activeWidget === 'PIE_DISTRIBUTION' ? '#38bdf8' : '#94a3b8' }} />}
            onClick={() => setActiveWidget(activeWidget === 'PIE_DISTRIBUTION' ? null : 'PIE_DISTRIBUTION')}
            style={{
              borderColor: activeWidget === 'PIE_DISTRIBUTION' ? '#38bdf8' : '#334155',
              color: activeWidget === 'PIE_DISTRIBUTION' ? '#38bdf8' : '#cbd5e1',
              borderRadius: 6
            }}
          >
            {activeWidget === 'PIE_DISTRIBUTION' ? '🥧 Dağılım (Açık)' : '🥧 Dağılım Grafiği'}
          </Button>

          <Button
            size="small"
            type={activeWidget === 'WAVEFORM_TIMELINE' ? 'primary' : 'default'}
            ghost={activeWidget === 'WAVEFORM_TIMELINE'}
            icon={<LineChartOutlined style={{ color: activeWidget === 'WAVEFORM_TIMELINE' ? '#38bdf8' : '#94a3b8' }} />}
            onClick={() => setActiveWidget(activeWidget === 'WAVEFORM_TIMELINE' ? null : 'WAVEFORM_TIMELINE')}
            style={{
              borderColor: activeWidget === 'WAVEFORM_TIMELINE' ? '#38bdf8' : '#334155',
              color: activeWidget === 'WAVEFORM_TIMELINE' ? '#38bdf8' : '#cbd5e1',
              borderRadius: 6
            }}
          >
            {activeWidget === 'WAVEFORM_TIMELINE' ? '📈 Dalga Formu (Açık)' : '📈 Dalga Formu'}
          </Button>

          <Button
            size="small"
            type={activeWidget === 'SPECTRUM_OCCUPANCY' ? 'primary' : 'default'}
            ghost={activeWidget === 'SPECTRUM_OCCUPANCY'}
            icon={<BarChartOutlined style={{ color: activeWidget === 'SPECTRUM_OCCUPANCY' ? '#ef4444' : '#94a3b8' }} />}
            onClick={() => setActiveWidget(activeWidget === 'SPECTRUM_OCCUPANCY' ? null : 'SPECTRUM_OCCUPANCY')}
            style={{
              borderColor: activeWidget === 'SPECTRUM_OCCUPANCY' ? '#ef4444' : '#334155',
              color: activeWidget === 'SPECTRUM_OCCUPANCY' ? '#f87171' : '#cbd5e1',
              borderRadius: 6
            }}
          >
            {activeWidget === 'SPECTRUM_OCCUPANCY' ? '📊 Spektrum Doluluk (Açık)' : '📊 Spektrum Doluluk'}
          </Button>

          <Button
            size="small"
            type={activeWidget === 'WEEKDAY_RADAR' ? 'primary' : 'default'}
            ghost={activeWidget === 'WEEKDAY_RADAR'}
            icon={<RadarChartOutlined style={{ color: activeWidget === 'WEEKDAY_RADAR' ? '#38bdf8' : '#94a3b8' }} />}
            onClick={() => setActiveWidget(activeWidget === 'WEEKDAY_RADAR' ? null : 'WEEKDAY_RADAR')}
            style={{
              borderColor: activeWidget === 'WEEKDAY_RADAR' ? '#38bdf8' : '#334155',
              color: activeWidget === 'WEEKDAY_RADAR' ? '#38bdf8' : '#cbd5e1',
              borderRadius: 6
            }}
          >
            {activeWidget === 'WEEKDAY_RADAR' ? '🕸️ Haftalık LOB (Açık)' : '🕸️ Haftalık LOB Dağılımı'}
          </Button>
        </Space>

        <div style={{ width: 1, height: 20, backgroundColor: '#334155', margin: '0 4px' }} />

        <div style={{ overflowX: 'auto', whiteSpace: 'nowrap', paddingBottom: 2 }}>
          <Space size={8}>
            {quickPrompts.map((qp, idx) => (
              <Button
                key={idx}
                size="small"
                style={{
                  backgroundColor: '#1e293b',
                  borderColor: '#334155',
                  color: '#cbd5e1',
                  borderRadius: 16,
                  fontSize: 12
                }}
                onClick={() => handleSend(qp.prompt)}
                disabled={loading}
              >
                {qp.label}
              </Button>
            ))}
          </Space>
        </div>
      </div>

      {/* Ana Bölünmüş Çalışma Alanı (Sol Chat + Sağ Widget) */}
      <div style={{ display: 'flex', gap: 16, flex: 1, minHeight: 0 }}>
        {/* Sol Kolon: Taktik Sohbet Akışı ve Giriş Çubuğu */}
        <div style={{
          display: 'flex',
          flexDirection: 'column',
          flex: activeWidget ? '1 1 50%' : '1 1 100%',
          minWidth: 0,
          minHeight: 0,
          transition: 'flex 0.25s ease'
        }}>
          {/* Mesaj Listesi */}
          <Card
            style={{
              flex: 1,
              overflowY: 'auto',
              backgroundColor: '#0f172a',
              borderColor: '#1e293b',
              borderRadius: 8,
              marginBottom: 12
            }}
        bodyStyle={{ padding: 16 }}
        onClick={handleContentClick}
      >
        {messages.map((msg, index) => {
          const isUser = msg.role === 'user';
          return (
            <div
              key={index}
              style={{
                display: 'flex',
                justifyContent: isUser ? 'flex-end' : 'flex-start',
                marginBottom: 16
              }}
            >
              <div
                style={{
                  maxWidth: isUser ? '75%' : '90%',
                  display: 'flex',
                  gap: 12,
                  flexDirection: isUser ? 'row-reverse' : 'row'
                }}
              >
                {/* Avatar */}
                <div
                  style={{
                    width: 34,
                    height: 34,
                    borderRadius: '50%',
                    backgroundColor: isUser ? '#0284c7' : '#0369a1',
                    display: 'flex',
                    alignItems: 'center',
                    justifyContent: 'center',
                    color: '#fff',
                    flexShrink: 0
                  }}
                >
                  {isUser ? <UserOutlined /> : <RobotOutlined />}
                </div>

                {/* Bubble */}
                <div
                  style={{
                    backgroundColor: isUser ? '#1e293b' : '#111827',
                    border: `1px solid ${isUser ? '#334155' : '#1f2937'}`,
                    borderRadius: 8,
                    padding: '12px 16px',
                    color: '#e2e8f0',
                    boxShadow: '0 2px 4px rgba(0,0,0,0.2)'
                  }}
                >
                  {isUser ? (
                    <Text style={{ color: '#f1f5f9', whiteSpace: 'pre-wrap' }}>{msg.content}</Text>
                  ) : (
                    <div>
                      <div
                        className="tactical-markdown"
                        dangerouslySetInnerHTML={{ __html: formatMarkdown(msg.content) }}
                      />

                      {/* Tablo ve İlgili Görselleştirici Aksiyon Butonları */}
                      <div style={{ marginTop: 12, display: 'flex', gap: 8, flexWrap: 'wrap' }}>
                        <Button
                          size="small"
                          type="primary"
                          ghost
                          icon={<RadarChartOutlined />}
                          onClick={() => onNavigateTab('radar')}
                          style={{ borderColor: '#38bdf8', color: '#38bdf8' }}
                        >
                          📡 Radar Tablosunda İncele
                        </Button>
                        <Button
                          size="small"
                          type="primary"
                          ghost
                          icon={<SecurityScanOutlined />}
                          onClick={() => onNavigateTab('comint')}
                          style={{ borderColor: '#c084fc', color: '#c084fc' }}
                        >
                          📻 Muhabere Tablosunda İncele
                        </Button>

                        {/* Bu yanıta ait ilişkili taktik ısı haritası butonu */}
                        {(msg.relatedWidget === 'CALLSIGN_HEATMAP' || msg.relatedWidget?.type === 'CALLSIGN_HEATMAP') && (
                          <Button
                            size="small"
                            type="primary"
                            ghost
                            icon={<FireOutlined style={{ color: '#ef4444' }} />}
                            onClick={() => setActiveWidget('CALLSIGN_HEATMAP')}
                            style={{ borderColor: '#ef4444', color: '#f87171' }}
                          >
                            🔥 Isı Haritası Görünümünü Aç
                          </Button>
                        )}

                        {/* Bu yanıta ait ilişkili taktik pasta dağılım butonu */}
                        {msg.relatedWidget?.type === 'PIE_DISTRIBUTION' && (
                          <Button
                            size="small"
                            type="primary"
                            ghost
                            icon={<PieChartOutlined style={{ color: '#38bdf8' }} />}
                            onClick={() => {
                              setDistributionConfig({
                                source: msg.relatedWidget.source || 'RADAR',
                                criterion: msg.relatedWidget.criterion || 'TESHIS'
                              });
                              setActiveWidget('PIE_DISTRIBUTION');
                            }}
                            style={{ borderColor: '#38bdf8', color: '#38bdf8' }}
                          >
                            🥧 Taktik Dağılım Grafiğini Aç
                          </Button>
                        )}

                        {/* Bu yanıta ait ilişkili dalga formu zamansal değişim butonu */}
                        {msg.relatedWidget?.type === 'WAVEFORM_TIMELINE' && (
                          <Button
                            size="small"
                            type="primary"
                            ghost
                            icon={<LineChartOutlined style={{ color: '#38bdf8' }} />}
                            onClick={() => {
                              setTimelineConfig({
                                source: msg.relatedWidget.source || 'RADAR',
                                targetKey: msg.relatedWidget.targetKey || 'SP048'
                              });
                              setActiveWidget('WAVEFORM_TIMELINE');
                            }}
                            style={{ borderColor: '#38bdf8', color: '#38bdf8' }}
                          >
                            📈 Dalga Formu Zaman Çizelgesini Aç
                          </Button>
                        )}

                        {/* Bu yanıta ait ilişkili haftalık LOB örümcek ağı butonu */}
                        {msg.relatedWidget?.type === 'WEEKDAY_RADAR' && (
                          <Button
                            size="small"
                            type="primary"
                            ghost
                            icon={<RadarChartOutlined style={{ color: '#38bdf8' }} />}
                            onClick={() => {
                              setWeekdayRadarConfig({
                                source: msg.relatedWidget.source || 'TUMU',
                                recordType: msg.relatedWidget.recordType || 'TUMU',
                                timeRange: msg.relatedWidget.timeRange || 'ALL'
                              });
                              setActiveWidget('WEEKDAY_RADAR');
                            }}
                            style={{ borderColor: '#38bdf8', color: '#38bdf8' }}
                          >
                            🕸️ Haftalık LOB Örümcek Ağı Grafiğini Aç
                          </Button>
                        )}
                      </div>

                      {/* LLM Telemetri Logları */}
                      {msg.executionLogs && msg.executionLogs.length > 0 && (
                        <Collapse
                          ghost
                          size="small"
                          style={{ marginTop: 12, borderTop: '1px solid #1f2937' }}
                          items={[
                            {
                              key: '1',
                              label: (
                                <Text type="secondary" style={{ fontSize: 11 }}>
                                  <CodeOutlined /> Telemetri Araç Çağrıları ({msg.executionLogs.length})
                                </Text>
                              ),
                              children: (
                                <div style={{
                                  backgroundColor: '#090d16',
                                  padding: '8px 12px',
                                  borderRadius: 4,
                                  fontFamily: 'JetBrains Mono, monospace',
                                  fontSize: 11,
                                  color: '#38bdf8'
                                }}>
                                  {msg.executionLogs.map((log, i) => (
                                    <div key={i} style={{ marginBottom: 4 }}>• {log}</div>
                                  ))}
                                </div>
                              )
                            }
                          ]}
                        />
                      )}
                    </div>
                  )}
                </div>
              </div>
            </div>
          );
        })}

        {loading && (
          <div style={{ display: 'flex', alignItems: 'center', gap: 12, margin: '16px 0' }}>
            <div style={{
              width: 34,
              height: 34,
              borderRadius: '50%',
              backgroundColor: '#0369a1',
              display: 'flex',
              alignItems: 'center',
              justifyContent: 'center',
              color: '#fff'
            }}>
              <RobotOutlined />
            </div>
            <div style={{
              backgroundColor: '#111827',
              border: '1px solid #1f2937',
              borderRadius: 8,
              padding: '10px 16px',
              display: 'flex',
              alignItems: 'center',
              gap: 12
            }}>
              <Spin size="small" />
              <Text type="secondary">Taktik Copilot veritabanı analizini işliyor...</Text>
            </div>
          </div>
        )}
        <div ref={messagesEndRef} />
      </Card>

      {/* Giriş Çubuğu */}
      <div style={{ display: 'flex', gap: 8 }}>
        <TextArea
          value={inputText}
          onChange={(e) => setInputText(e.target.value)}
          placeholder="Komutanım, sahadaki radar emisyonları, telsiz muhabere ağları veya elektronik taarruz hakkında bir soru yazınız..."
          autoSize={{ minRows: 2, maxRows: 4 }}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
              e.preventDefault();
              handleSend();
            }
          }}
          disabled={loading}
          style={{
            backgroundColor: '#0f172a',
            borderColor: '#334155',
            color: '#f8fafc',
            borderRadius: 8
          }}
        />
        <Button
          type="primary"
          icon={<SendOutlined />}
          onClick={() => handleSend()}
          loading={loading}
          style={{
            height: 'auto',
            padding: '0 24px',
            backgroundColor: '#0284c7',
            borderColor: '#0284c7',
            borderRadius: 8
          }}
        >
          Gönder
        </Button>
      </div>
    </div>

    {/* Sağ Sütun: Taktik Dinamik Widget Alanı (Bağlamsal Görselleştirici) */}
    {activeWidget && (
      <div style={{
        flex: '1 1 50%',
        minWidth: 420,
        display: 'flex',
        flexDirection: 'column',
        minHeight: 0,
        transition: 'flex 0.25s ease'
      }}>
        {activeWidget === 'CALLSIGN_HEATMAP' && (
          <CallsignHeatmapWidget onClose={() => setActiveWidget(null)} />
        )}

        {activeWidget === 'PIE_DISTRIBUTION' && (
          <TacticalDistributionPieWidget
            initialSource={distributionConfig.source}
            initialCriterion={distributionConfig.criterion}
            onClose={() => setActiveWidget(null)}
          />
        )}

        {activeWidget === 'WAVEFORM_TIMELINE' && (
          <TacticalWaveformTimelineWidget
            initialSource={timelineConfig.source}
            initialTargetKey={timelineConfig.targetKey}
            onClose={() => setActiveWidget(null)}
          />
        )}

        {activeWidget === 'SPECTRUM_OCCUPANCY' && (
          <RadarSpectrumOccupancyWidget
            initialTimeRange={occupancyConfig.timeRange}
            initialIdentities={occupancyConfig.identities}
            onClose={() => setActiveWidget(null)}
          />
        )}

        {activeWidget === 'WEEKDAY_RADAR' && (
          <WeekdayRadarDistributionWidget
            initialSource={weekdayRadarConfig.source}
            initialRecordType={weekdayRadarConfig.recordType}
            initialTimeRange={weekdayRadarConfig.timeRange}
            onClose={() => setActiveWidget(null)}
          />
        )}
      </div>
    )}
  </div>
</div>
  );
}


