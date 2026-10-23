import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  server: {
    host: '0.0.0.0',
    port: 3002,
    strictPort: true,
    proxy: {
      // AI Copilot Servisi (Port 8082)
      '/api/v1/tactical/analyze': {
        target: 'http://127.0.0.1:8082',
        changeOrigin: true
      },
      '/api/v1/chat': {
        target: 'http://127.0.0.1:8082',
        changeOrigin: true
      },
      '/api/v1/health': {
        target: 'http://127.0.0.1:8082',
        changeOrigin: true
      },

      // Taktik Analitik ve Telemetri Veri Servisi (Port 8081 - Widget'lar için)
      '/api': {
        target: 'http://127.0.0.1:8081',
        changeOrigin: true
      }
    }
  }
})
