// electron.vite.config.ts
import { defineConfig } from "electron-vite";
import vue from "@vitejs/plugin-vue";
var electron_vite_config_default = defineConfig({
  main: {
    build: {
      rollupOptions: {
        external: ["playwright"]
      }
    }
  },
  preload: {},
  renderer: {
    plugins: [vue()]
  }
});
export {
  electron_vite_config_default as default
};
