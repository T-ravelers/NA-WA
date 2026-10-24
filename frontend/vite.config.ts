import { fileURLToPath, URL } from 'node:url'

import tailwindcss from '@tailwindcss/vite'
import { defineConfig } from 'vite'
import vue from '@vitejs/plugin-vue'
import { VitePWA } from 'vite-plugin-pwa'

// https://vite.dev/config/
export default defineConfig({
  plugins: [
    vue(),
    tailwindcss(),
    VitePWA({
      registerType: 'autoUpdate',
      // 서비스 워커를 직접 적는다(src/sw.ts). 푸시 처리를 얹으려면 생성된 코드로는 안 된다.
      strategies: 'injectManifest',
      srcDir: 'src',
      filename: 'sw.ts',
      includeAssets: ['favicon.ico', 'pwa-icon.svg', 'apple-touch-icon.png'],
      manifest: {
        id: '/',
        name: 'NA-WA',
        short_name: 'NA-WA',
        description: 'Plan, travel and settle up together',
        lang: 'en',
        start_url: '/',
        scope: '/',
        display: 'standalone',
        background_color: '#171717',
        theme_color: '#171717',
        icons: [
          {
            src: '/pwa-192x192.png',
            sizes: '192x192',
            type: 'image/png',
          },
          {
            src: '/pwa-512x512.png',
            sizes: '512x512',
            type: 'image/png',
            purpose: 'any maskable',
          },
        ],
      },
      // precache 대상. 앱 셸과 정적 자원(js/css/html)만이고 폰트는 넣지 않는다.
      // runtime cache와 index.html 폴백은 src/sw.ts에 코드로 있다.
      injectManifest: {
        globPatterns: ['**/*.{js,css,html}'],
      },
    }),
  ],
  resolve: {
    alias: {
      '@': fileURLToPath(new URL('./src', import.meta.url)),
    },
  },
})
