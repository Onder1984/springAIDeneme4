import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'
import path from 'path'
import { fileURLToPath } from 'url'

const __dirname = path.dirname(fileURLToPath(import.meta.url))

// https://vite.dev/config/
export default defineConfig({
  plugins: [react()],
  build: {
    outDir: path.resolve(__dirname, 'dist'),
    emptyOutDir: true
  },
  server: {
    host: '0.0.0.0',
    port: 3000,
    strictPort: true,
    proxy: {
      // AI Copilot Servisi (Port 8082)
      '/api/v1/tactical/analyze': {
        target: 'http://127.0.0.1:8082',
        changeOrigin: true
      },
      '/api/v1/analyze': {
        target: 'http://127.0.0.1:8082',
        changeOrigin: true
      },
      '/api/analyze': {
        target: 'http://127.0.0.1:8082',
        changeOrigin: true
      },
      '/api/v1/chat': {
        target: 'http://127.0.0.1:8082',
        changeOrigin: true
      },
      '/api/chat': {
        target: 'http://127.0.0.1:8082',
        changeOrigin: true
      },
      '/api/v1/tactical/chat': {
        target: 'http://127.0.0.1:8082',
        changeOrigin: true
      },
      '/api/copilot': {
        target: 'http://127.0.0.1:8082',
        changeOrigin: true
      },
      '/api/v1/health': {
        target: 'http://127.0.0.1:8082',
        changeOrigin: true
      },

      // Yayın ve Telemetri Veri Servisi (Port 8081)
      '/api': {
        target: 'http://127.0.0.1:8081',
        changeOrigin: true
      }
    }
  }
})
