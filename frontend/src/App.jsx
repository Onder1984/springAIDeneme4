import React from 'react';
import { ConfigProvider, theme } from 'antd';
import ComposerLayout from './composer/ComposerLayout';

export default function App() {
  return (
    <ConfigProvider
      theme={{
        algorithm: theme.darkAlgorithm,
        token: {
          colorPrimary: '#0284c7',
          colorBgBase: '#060a14',
          colorBgContainer: '#0d1322',
          colorBorder: '#1e293b',
          borderRadius: 6,
          fontFamily: "'Inter', sans-serif"
        }
      }}
    >
      <ComposerLayout />
    </ConfigProvider>
  );
}
