import React from 'react'
import ReactDOM from 'react-dom/client'
import { ConfigProvider, theme } from 'antd'
import trTR from 'antd/locale/tr_TR'
import App from './App.jsx'
import './index.css'

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <ConfigProvider
      locale={trTR}
      theme={{
        algorithm: theme.darkAlgorithm,
        token: {
          colorPrimary: '#38bdf8',
          colorBgBase: '#0b1120',
          colorBgContainer: '#0f172a',
          colorBorder: '#1e293b',
          colorText: '#f8fafc',
          borderRadius: 6,
          fontFamily: "'Inter', -apple-system, BlinkMacSystemFont, 'Segoe UI', Roboto, sans-serif"
        }
      }}
    >
      <App />
    </ConfigProvider>
  </React.StrictMode>,
)
