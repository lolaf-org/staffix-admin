import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

const VENDOR_CHUNKS: [string, RegExp][] = [
  ['react', /node_modules\/(react|react-dom|react-router|scheduler)\//],
  ['antd-icons', /node_modules\/@ant-design\/icons/],
  ['antd', /node_modules\/(antd|@ant-design|@rc-component|rc-[^/]+)\//],
  ['charts', /node_modules\/(recharts|d3-[^/]+|victory-vendor|internmap|decimal\.js-light|es-toolkit|immer|redux|@reduxjs|reselect|react-redux|use-sync-external-store|eventemitter3)\//],
  ['vendor', /node_modules\//],
]

export default defineConfig({
  plugins: [react()],
  build: {
    outDir: 'target/classes/static',
    emptyOutDir: true,
    // Ant Design is one ~820 kB vendor chunk, cached across releases; fine for an internal console
    chunkSizeWarningLimit: 1000,
    rolldownOptions: {
      output: {
        codeSplitting: {
          groups: VENDOR_CHUNKS.map(([name, test]) => ({ name, test })),
        },
      },
    },
  },
  server: {
    proxy: {
      '/api': 'http://localhost:8080',
    },
  },
})
