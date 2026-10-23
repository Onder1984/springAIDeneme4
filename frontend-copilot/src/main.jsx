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
          colorPrimary: '#a855f7',
          colorBgBase: '#090514',
          colorBgContainer: '#130d24',
          colorBorder: '#2e1065',
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
