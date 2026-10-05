import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  plugins: [vue()],
  server: { host: '127.0.0.1', port: 5173, strictPort: true, proxy: { '/api': { target: process.env.API_PROXY_TARGET || 'http://localhost:8080', changeOrigin: true } } },
  build: { sourcemap: false, rollupOptions: { output: { manualChunks: { vendor: ['vue', 'vue-router', 'axios'], ui: ['element-plus'] } } } },
})
