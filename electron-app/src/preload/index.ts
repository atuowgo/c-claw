import { contextBridge, ipcRenderer } from 'electron'

contextBridge.exposeInMainWorld('electronAPI', {
  getBackendPort: () => ipcRenderer.invoke('get-backend-port'),
  getBridgePort: () => ipcRenderer.invoke('get-bridge-port'),
  platform: process.platform
})