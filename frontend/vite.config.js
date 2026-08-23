import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

/**
 * The dev server proxies /api to Spring Boot, which means the browser only ever talks to
 * one origin during development. No CORS preflight in the common path, and the same
 * relative API base URL works in production behind a reverse proxy.
 *
 * Every environment-specific value comes from VITE_* variables, so no localhost URL is
 * baked into the bundle.
 */
export default defineConfig(({ mode }) => {
  const proxyTarget = process.env.VITE_API_PROXY_TARGET || 'http://localhost:8080'

  return {
    plugins: [react()],
    server: {
      port: 5173,
      strictPort: false,
      proxy: {
        '/api': {
          target: proxyTarget,
          changeOrigin: true,
        },
      },
    },
    build: {
      outDir: 'dist',
      sourcemap: mode !== 'production',
    },
    test: {
      globals: true,
      environment: 'jsdom',
      setupFiles: ['./src/test/setup.js'],
      css: false,
      include: ['src/**/*.{test,spec}.{js,jsx}'],
    },
  }
})
