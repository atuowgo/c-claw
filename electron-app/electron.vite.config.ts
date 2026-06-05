import { defineConfig } from 'electron-vite'
import vue from '@vitejs/plugin-vue'

export default defineConfig({
  main: {
    build: {
      rollupOptions: {
        external: ['playwright', 'active-win']
      }
    }
  },
  preload: {},
  renderer: {
    plugins: [vue()]
  }
})